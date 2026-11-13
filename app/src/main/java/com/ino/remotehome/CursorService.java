package com.ino.remotehome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * リモコンで他のアプリを操作する（Chromecast のような動き）。
 *
 * モード（決定の長押しで切り替え）：
 *  - フォーカス（初期）：上下左右でボタンからボタンへ選択が移り、決定で押す。端に来ると自動でスクロール。
 *  - カーソル：マウスのようにカーソルを動かし、決定でタップ。
 *  - スクロール：上下左右でその方向へスクロール。
 *
 * フォーカスモードでは、画面にあるボタンの位置と種類（押せるか・スクロールできるか）を読む。
 * 文字や入力内容は使わず、保存も通信もしない。割り当てていないボタン（戻る・ホーム・音量）はそのまま通す。
 */
public class CursorService extends AccessibilityService {

    private static final int FOCUS = 0, CURSOR = 1, SCROLL = 2;
    private static final long LONG_MS = 650;
    private static final long HIDE_MS = 6000;

    private WindowManager wm;
    private OverlayView view;
    private final Handler h = new Handler(Looper.getMainLooper());

    private String fgPkg = "";
    private int mode = FOCUS;
    private float cx = -1, cy = -1;
    private boolean okDown = false;
    private boolean okLong = false;

    /** フォーカスモードで選んでいる場所。 */
    private final Rect focusRect = new Rect();
    private boolean hasFocus = false;
    private int pendingDir = 0;

    private final Runnable hideRun = new Runnable() {
        @Override
        public void run() {
            if (view != null) {
                view.setShow(false);
            }
        }
    };

    private final Runnable longRun = new Runnable() {
        @Override
        public void run() {
            if (okDown) {
                okLong = true;
                nextMode();
            }
        }
    };

    private final Runnable afterScroll = new Runnable() {
        @Override
        public void run() {
            if (mode == FOCUS && hasFocus && pendingDir != 0) {
                List<Cand> cs = collect();
                Cand c = pick(cs, pendingDir, focusRect);
                if (c != null) {
                    setFocus(c.r);
                }
            }
            pendingDir = 0;
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        view = new OverlayView(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(view, lp);
        } catch (Exception e) {
            view = null;
        }
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (wm != null && view != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
        }
        view = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence p = e.getPackageName();
        if (p == null) {
            return;
        }
        String s = p.toString();
        if (s.equals("com.android.systemui") || s.contains("inputmethod") || s.equals("android")) {
            return;
        }
        if (!s.equals(fgPkg)) {
            fgPkg = s;
        }
        // 画面が切り替わったら、選んでいた場所は無効
        hasFocus = false;
        if (view != null) {
            view.setRect(null);
        }
    }

    @Override
    public void onInterrupt() {
    }

    private boolean active() {
        return view != null
                && Prefs.cursorOn(this)
                && !fgPkg.equals(getPackageName());
    }

    // ---------------------------------------------------------------- キー

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (!active()) {
            return false;
        }
        String fn = Prefs.funcOf(this, e.getKeyCode());
        if (fn == null) {
            return false;
        }
        initPos();

        if (fn.equals("toggle")) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                nextMode();
            }
            return true;
        }

        if (!fn.equals("ok")) {
            int k = fn.equals("up") ? KeyEvent.KEYCODE_DPAD_UP
                    : fn.equals("down") ? KeyEvent.KEYCODE_DPAD_DOWN
                    : fn.equals("left") ? KeyEvent.KEYCODE_DPAD_LEFT
                    : KeyEvent.KEYCODE_DPAD_RIGHT;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (mode == FOCUS) {
                    onFocusDirection(k, e.getRepeatCount());
                } else {
                    onDirection(k, e.getRepeatCount());
                }
            }
            return true;
        }

        // 決定
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                okDown = true;
                okLong = false;
                h.removeCallbacks(longRun);
                h.postDelayed(longRun, LONG_MS);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            h.removeCallbacks(longRun);
            boolean wasLong = okLong;
            okDown = false;
            okLong = false;
            if (!wasLong) {
                onOk();
            }
        }
        return true;
    }

    private void nextMode() {
        mode = (mode + 1) % 3;
        hasFocus = false;
        if (view != null) {
            view.setRect(null);
            view.setMode(mode);
            view.setMsg(modeName(mode));
        }
        show();
    }

    private static String modeName(int m) {
        if (m == FOCUS) return L.t("フォーカス操作", "Focus mode");
        if (m == CURSOR) return L.t("カーソル操作", "Cursor mode");
        return L.t("スクロール操作", "Scroll mode");
    }

    private void onOk() {
        if (mode == FOCUS) {
            if (!hasFocus) {
                selectFirst();
                return;
            }
            // 選んでいる場所を押す
            List<Cand> cs = collect();
            Cand best = null;
            long bestOv = -1;
            for (Cand c : cs) {
                Rect i = new Rect();
                if (i.setIntersect(c.r, focusRect)) {
                    long ov = (long) i.width() * i.height();
                    if (ov > bestOv) {
                        bestOv = ov;
                        best = c;
                    }
                }
            }
            boolean done = false;
            if (best != null) {
                done = best.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            if (!done) {
                tap(focusRect.exactCenterX(), focusRect.exactCenterY());
            }
            hasFocus = false;
            if (view != null) {
                view.setRect(null);
            }
            return;
        }
        show();
        tap(cx, cy);
    }

    // ---------------------------------------------------------------- フォーカス

    private static final class Cand {
        AccessibilityNodeInfo node;
        Rect r;
    }

    private int minSize() {
        return (int) (20 * getResources().getDisplayMetrics().density);
    }

    private int[] screenSize() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = view != null && view.getWidth() > 0 ? view.getWidth() : dm.widthPixels;
        int hh = view != null && view.getHeight() > 0 ? view.getHeight() : dm.heightPixels;
        return new int[]{w, hh};
    }

    /** 画面にある、押せる場所を集める。 */
    private List<Cand> collect() {
        List<Cand> out = new ArrayList<Cand>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return out;
        }
        int[] sz = screenSize();
        long area = (long) sz[0] * sz[1];
        int[] count = new int[]{0};
        walk(root, area, out, count);
        // 中に別の候補を含む大きな候補は外す（細かいボタンを優先）
        List<Cand> res = new ArrayList<Cand>();
        for (Cand a : out) {
            boolean containsOther = false;
            for (Cand b : out) {
                if (a != b && a.r.contains(b.r) && !a.r.equals(b.r)) {
                    containsOther = true;
                    break;
                }
            }
            if (!containsOther) {
                boolean dup = false;
                for (Cand b : res) {
                    if (b.r.equals(a.r)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) res.add(a);
            }
        }
        return res;
    }

    private void walk(AccessibilityNodeInfo n, long screenArea, List<Cand> out, int[] count) {
        if (n == null || count[0]++ > 2500) {
            return;
        }
        if (!n.isVisibleToUser()) {
            return;
        }
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        boolean usable = (n.isClickable() || n.isEditable()) && n.isEnabled();
        if (usable && r.width() >= minSize() && r.height() >= minSize()
                && (long) r.width() * r.height() < screenArea * 0.85) {
            Cand c = new Cand();
            c.node = n;
            c.r = r;
            out.add(c);
        }
        int cc = n.getChildCount();
        for (int i = 0; i < cc; i++) {
            walk(n.getChild(i), screenArea, out, count);
        }
    }

    /** 向きに合う、いちばん近い候補。 */
    private Cand pick(List<Cand> cs, int dir, Rect from) {
        Cand best = null;
        long bestScore = Long.MAX_VALUE;
        for (Cand c : cs) {
            Rect r = c.r;
            if (r.equals(from)) {
                continue;
            }
            long major, minor;
            if (dir == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!(from.top < r.top || from.bottom <= r.top) || !(from.bottom < r.bottom)) continue;
                major = Math.max(1, r.top - from.bottom);
                minor = Math.abs(r.centerX() - from.centerX());
            } else if (dir == KeyEvent.KEYCODE_DPAD_UP) {
                if (!(from.bottom > r.bottom || from.top >= r.bottom) || !(from.top > r.top)) continue;
                major = Math.max(1, from.top - r.bottom);
                minor = Math.abs(r.centerX() - from.centerX());
            } else if (dir == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (!(from.left < r.left || from.right <= r.left) || !(from.right < r.right)) continue;
                major = Math.max(1, r.left - from.right);
                minor = Math.abs(r.centerY() - from.centerY());
            } else {
                if (!(from.right > r.right || from.left >= r.right) || !(from.left > r.left)) continue;
                major = Math.max(1, from.left - r.right);
                minor = Math.abs(r.centerY() - from.centerY());
            }
            long score = 13 * major * major + minor * minor;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    private void setFocus(Rect r) {
        focusRect.set(r);
        hasFocus = true;
        if (view != null) {
            view.setMode(FOCUS);
            view.setRect(r);
        }
        show();
    }

    private void selectFirst() {
        List<Cand> cs = collect();
        if (cs.isEmpty()) {
            if (view != null) {
                view.setMsg(L.t("押せる場所が見つかりません。決定を長押しでカーソルに切り替え",
                        "Nothing to select. Long-press OK to switch to cursor"));
            }
            show();
            return;
        }
        int[] sz = screenSize();
        float mx = sz[0] / 2f, my = sz[1] / 2f;
        Cand best = null;
        double bd = Double.MAX_VALUE;
        for (Cand c : cs) {
            double dx = c.r.exactCenterX() - mx, dy = c.r.exactCenterY() - my;
            double d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        setFocus(best.r);
    }

    private void onFocusDirection(int dir, int repeat) {
        if (repeat > 0 && repeat % 3 != 0) {
            return;
        }
        if (!hasFocus) {
            selectFirst();
            return;
        }
        List<Cand> cs = collect();
        Cand c = pick(cs, dir, focusRect);
        if (c != null) {
            setFocus(c.r);
            return;
        }
        // その方向に候補がない → スクロールしてみる
        if (scrollNode(dir)) {
            pendingDir = dir;
            h.removeCallbacks(afterScroll);
            h.postDelayed(afterScroll, 380);
        }
        show();
    }

    /** 選んでいる場所を含む、スクロールできる部分を探して、その方向へ動かす。 */
    private boolean scrollNode(int dir) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        List<AccessibilityNodeInfo> sc = new ArrayList<AccessibilityNodeInfo>();
        findScrollable(root, sc, new int[]{0});
        AccessibilityNodeInfo best = null;
        long bestArea = Long.MAX_VALUE;
        for (AccessibilityNodeInfo n : sc) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (!r.contains((int) focusRect.exactCenterX(), (int) focusRect.exactCenterY())) {
                continue;
            }
            long a = (long) r.width() * r.height();
            // 向きに合う細長さを優先（横の並びは横に長いもの）
            boolean horiz = dir == KeyEvent.KEYCODE_DPAD_LEFT || dir == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (horiz != (r.width() >= r.height())) {
                a = a * 4;
            }
            if (a < bestArea) {
                bestArea = a;
                best = n;
            }
        }
        boolean fwd = dir == KeyEvent.KEYCODE_DPAD_DOWN || dir == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (best != null) {
            int act = fwd ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if (best.performAction(act)) {
                return true;
            }
        }
        // 見つからない・動かせないときは、指でなぞる動きで試す
        int[] sz = screenSize();
        float d = Math.min(sz[0], sz[1]) * 0.35f;
        float dx = 0, dy = 0;
        if (dir == KeyEvent.KEYCODE_DPAD_DOWN) dy = -d;
        if (dir == KeyEvent.KEYCODE_DPAD_UP) dy = d;
        if (dir == KeyEvent.KEYCODE_DPAD_RIGHT) dx = -d;
        if (dir == KeyEvent.KEYCODE_DPAD_LEFT) dx = d;
        swipe(focusRect.exactCenterX(), focusRect.exactCenterY(), dx, dy, sz[0], sz[1]);
        return true;
    }

    private void findScrollable(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int[] count) {
        if (n == null || count[0]++ > 2500 || !n.isVisibleToUser()) {
            return;
        }
        if (n.isScrollable()) {
            out.add(n);
        }
        int cc = n.getChildCount();
        for (int i = 0; i < cc; i++) {
            findScrollable(n.getChild(i), out, count);
        }
    }

    // ---------------------------------------------------------------- カーソル・スクロール

    private void initPos() {
        if (cx < 0 || cy < 0) {
            int[] sz = screenSize();
            cx = sz[0] / 2f;
            cy = sz[1] / 2f;
            if (view != null) {
                view.setPos(cx, cy);
            }
        }
    }

    private void onDirection(int k, int repeat) {
        show();
        int[] sz = screenSize();
        float w = sz[0], hh = sz[1];

        if (mode == SCROLL) {
            if (repeat > 0 && repeat % 4 != 0) {
                return;
            }
            float dx = 0, dy = 0;
            float d = Math.min(w, hh) * 0.35f;
            // 「下」を押す＝下の内容を見たい＝指は上へなぞる
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) dy = -d;
            if (k == KeyEvent.KEYCODE_DPAD_UP) dy = d;
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) dx = -d;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) dx = d;
            swipe(cx, cy, dx, dy, w, hh);
            return;
        }

        float base = 22f * getResources().getDisplayMetrics().density;
        float step = base * (1f + Math.min(repeat, 24) / 4f);
        if (k == KeyEvent.KEYCODE_DPAD_UP) cy -= step;
        if (k == KeyEvent.KEYCODE_DPAD_DOWN) cy += step;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) cx -= step;
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT) cx += step;
        cx = Math.max(0, Math.min(w - 1, cx));
        cy = Math.max(0, Math.min(hh - 1, cy));
        view.setPos(cx, cy);
    }

    private void show() {
        if (view != null) {
            view.setMode(mode);
            view.setShow(true);
        }
        h.removeCallbacks(hideRun);
        h.postDelayed(hideRun, HIDE_MS);
    }

    private void tap(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 60);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    private void swipe(float x, float y, float dx, float dy, float w, float hh) {
        float x2 = Math.max(1, Math.min(w - 1, x + dx));
        float y2 = Math.max(1, Math.min(hh - 1, y + dy));
        float x1 = Math.max(1, Math.min(w - 1, x));
        float y1 = Math.max(1, Math.min(hh - 1, y));
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 260);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    // ---------------------------------------------------------------- 画面にかぶせる表示

    /** 画面全体にかぶせて、選択の枠・カーソル・モード名を描く（タッチは通す）。 */
    private static final class OverlayView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tbg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x = -1, y = -1;
        private boolean show = false;
        private int mode = FOCUS;
        private Rect rect = null;
        private String msg = null;
        private final float d;
        private final Runnable clearMsg = new Runnable() {
            @Override
            public void run() {
                msg = null;
                invalidate();
            }
        };

        OverlayView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(2.5f * d);
            text.setTextSize(16 * d);
            text.setColor(Color.WHITE);
            tbg.setColor(Color.argb(170, 0, 0, 0));
        }

        void setPos(float nx, float ny) {
            x = nx;
            y = ny;
            invalidate();
        }

        void setShow(boolean s) {
            show = s;
            invalidate();
        }

        void setMode(int m) {
            mode = m;
            invalidate();
        }

        void setRect(Rect r) {
            rect = r == null ? null : new Rect(r);
            invalidate();
        }

        void setMsg(String m) {
            msg = m;
            removeCallbacks(clearMsg);
            postDelayed(clearMsg, 2500);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (msg != null) {
                float tw = text.measureText(msg);
                float px = 14 * d;
                float left = (getWidth() - tw) / 2f;
                c.drawRoundRect(new RectF(left - px, 24 * d, left + tw + px, 24 * d + 40 * d), 10 * d, 10 * d, tbg);
                c.drawText(msg, left, 24 * d + 27 * d, text);
            }
            if (!show) {
                return;
            }
            if (mode == FOCUS) {
                if (rect != null) {
                    float pad = 3 * d;
                    RectF rf = new RectF(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad);
                    fill.setColor(Color.argb(45, 125, 211, 252));
                    c.drawRoundRect(rf, 10 * d, 10 * d, fill);
                    edge.setColor(Color.rgb(125, 211, 252));
                    edge.setStrokeWidth(4 * d);
                    c.drawRoundRect(rf, 10 * d, 10 * d, edge);
                }
                return;
            }
            if (x < 0) {
                return;
            }
            boolean scroll = mode == SCROLL;
            int col = scroll ? Color.rgb(255, 200, 61) : Color.rgb(125, 211, 252);
            fill.setColor(col);
            fill.setAlpha(150);
            edge.setColor(Color.WHITE);
            edge.setStrokeWidth(2.5f * d);
            float r = 13 * d;
            c.drawCircle(x, y, r, fill);
            c.drawCircle(x, y, r, edge);
            if (scroll) {
                float a = r + 8 * d;
                Path p = new Path();
                p.moveTo(x, y - a - 6 * d); p.lineTo(x - 5 * d, y - a); p.lineTo(x + 5 * d, y - a); p.close();
                p.moveTo(x, y + a + 6 * d); p.lineTo(x - 5 * d, y + a); p.lineTo(x + 5 * d, y + a); p.close();
                p.moveTo(x - a - 6 * d, y); p.lineTo(x - a, y - 5 * d); p.lineTo(x - a, y + 5 * d); p.close();
                p.moveTo(x + a + 6 * d, y); p.lineTo(x + a, y - 5 * d); p.lineTo(x + a, y + 5 * d); p.close();
                fill.setAlpha(230);
                c.drawPath(p, fill);
            }
        }
    }
}
