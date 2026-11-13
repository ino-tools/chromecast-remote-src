package com.ino.remotehome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * リモコンの上下左右・決定を、画面のカーソル操作に変える。
 * 読むのはリモコンのキーと「いま前面にあるアプリの名前」だけ。画面の中身は読まない。記録・通信はしない。
 *
 * 操作：
 *  - 上下左右：カーソルを動かす（押し続けると速くなる）
 *  - 決定（短く）：カーソルの位置をタップ
 *  - 決定（長く）：カーソルモード ⇔ スクロールモード を切り替え
 *  - スクロールモード：上下左右でその方向へ画面をスクロール（指でなぞる動き）
 *  - 戻る・ホーム・音量などは触らず、そのまま通す
 */
public class CursorService extends AccessibilityService {

    private static final long LONG_MS = 650;
    private static final long HIDE_MS = 5000;

    private WindowManager wm;
    private CursorView view;
    private final Handler h = new Handler(Looper.getMainLooper());

    private String fgPkg = "";
    private boolean scrollMode = false;
    private float cx = -1, cy = -1;
    private boolean okDown = false;
    private boolean okLong = false;

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
                scrollMode = !scrollMode;
                if (view != null) {
                    view.setScroll(scrollMode);
                    view.setShow(true);
                }
                h.removeCallbacks(hideRun);
                h.postDelayed(hideRun, HIDE_MS);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        view = new CursorView(this);
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
        fgPkg = s;
    }

    @Override
    public void onInterrupt() {
    }

    private boolean active() {
        return view != null
                && Prefs.cursorOn(this)
                && !fgPkg.equals(getPackageName());
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (!active()) {
            return false;
        }
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER
                || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean dir = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!ok && !dir) {
            return false;
        }
        initPos();

        if (dir) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                onDirection(k, e.getRepeatCount());
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
                show();
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            h.removeCallbacks(longRun);
            boolean wasLong = okLong;
            okDown = false;
            okLong = false;
            if (!wasLong) {
                tap(cx, cy);
            }
        }
        return true;
    }

    private void initPos() {
        if (cx < 0 || cy < 0) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            cx = dm.widthPixels / 2f;
            cy = dm.heightPixels / 2f;
            if (view != null) {
                view.setPos(cx, cy);
            }
        }
    }

    private void onDirection(int k, int repeat) {
        show();
        float w = view.getWidth() > 0 ? view.getWidth() : getResources().getDisplayMetrics().widthPixels;
        float hh = view.getHeight() > 0 ? view.getHeight() : getResources().getDisplayMetrics().heightPixels;

        if (scrollMode) {
            // 長押しの連続は少しずつ間引く
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
            view.setScroll(scrollMode);
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
        // 始点も画面内へ寄せ、距離を保つ
        float x1 = Math.max(1, Math.min(w - 1, x));
        float y1 = Math.max(1, Math.min(hh - 1, y));
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, 260);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    /** 画面全体にかぶせて、カーソルだけを描く（タッチは通す）。 */
    private static final class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x = -1, y = -1;
        private boolean show = false;
        private boolean scroll = false;
        private final float d;

        CursorView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(2.5f * d);
            text.setTextSize(16 * d);
            text.setColor(Color.WHITE);
            text.setShadowLayer(4 * d, 0, 0, Color.BLACK);
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

        void setScroll(boolean s) {
            scroll = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!show || x < 0) {
                return;
            }
            int col = scroll ? Color.rgb(255, 200, 61) : Color.rgb(125, 211, 252);
            fill.setColor(col);
            fill.setAlpha(150);
            edge.setColor(Color.WHITE);
            float r = 13 * d;
            c.drawCircle(x, y, r, fill);
            c.drawCircle(x, y, r, edge);
            if (scroll) {
                // 上下左右の小さな矢じり
                float a = r + 8 * d;
                Path p = new Path();
                p.moveTo(x, y - a - 6 * d); p.lineTo(x - 5 * d, y - a); p.lineTo(x + 5 * d, y - a); p.close();
                p.moveTo(x, y + a + 6 * d); p.lineTo(x - 5 * d, y + a); p.lineTo(x + 5 * d, y + a); p.close();
                p.moveTo(x - a - 6 * d, y); p.lineTo(x - a, y - 5 * d); p.lineTo(x - a, y + 5 * d); p.close();
                p.moveTo(x + a + 6 * d, y); p.lineTo(x + a, y - 5 * d); p.lineTo(x + a, y + 5 * d); p.close();
                fill.setAlpha(230);
                c.drawPath(p, fill);
            }
            String label = scroll ? L.t("スクロール", "Scroll") : L.t("カーソル", "Cursor");
            c.drawText(label, x + r + 6 * d, y - r, text);
        }
    }
}
