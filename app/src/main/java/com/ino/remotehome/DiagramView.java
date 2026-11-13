package com.ino.remotehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** かんたん設定の図解。画像ファイルは使わず、すべてその場で描く。 */
public class DiagramView extends View {

    static final int PAIR_CC = 0;     // Chromecast のリモコンのペアリング
    static final int PAIR_FIRE = 1;   // Fire TV のリモコンのペアリング
    static final int FLOW = 2;        // つながり方
    static final int A11Y = 3;        // ユーザー補助の手順
    static final int USE = 4;         // 使い方（ボタンの役目）
    static final int MODES = 5;       // 3つのモード

    private static final int BG = Color.rgb(20, 27, 44);
    private static final int BODY = Color.rgb(58, 66, 84);
    private static final int BTN = Color.rgb(98, 108, 130);
    private static final int HL = Color.rgb(255, 200, 61);
    private static final int CYAN = Color.rgb(125, 211, 252);
    private static final int TXT = Color.rgb(225, 232, 245);
    private static final int SUB = Color.rgb(160, 170, 190);

    private final int kind;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);

    DiagramView(Context c, int kind) {
        super(c);
        this.kind = kind;
        d = c.getResources().getDisplayMetrics().density;
        st.setStyle(Paint.Style.STROKE);
        t.setColor(TXT);
    }

    /** 描画の高さ（dp）。 */
    private int heightDp() {
        switch (kind) {
            case PAIR_CC:
            case PAIR_FIRE:
                return 250;
            case FLOW:
                return 130;
            case A11Y:
                return 230;
            case USE:
                return 270;
            default:
                return 170;
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (heightDp() * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(BG);
        c.drawRoundRect(new RectF(0, 0, w, h), 14 * d, 14 * d, p);
        switch (kind) {
            case PAIR_CC:
                pair(c, w, h, false);
                break;
            case PAIR_FIRE:
                pair(c, w, h, true);
                break;
            case FLOW:
                flow(c, w, h);
                break;
            case A11Y:
                a11y(c, w, h);
                break;
            case USE:
                use(c, w, h);
                break;
            default:
                modes(c, w, h);
        }
    }

    // ------------------------------------------------------------ 部品

    private void text(Canvas c, String s, float x, float y, float sizeDp, int color, boolean bold, Paint.Align al) {
        t.setTextSize(sizeDp * d);
        t.setColor(color);
        t.setFakeBoldText(bold);
        t.setTextAlign(al);
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; i++) {
            c.drawText(lines[i], x, y + i * sizeDp * 1.35f * d, t);
        }
    }

    private void box(Canvas c, RectF r, int fill, int stroke) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawRoundRect(r, 10 * d, 10 * d, p);
        if (stroke != 0) {
            st.setColor(stroke);
            st.setStrokeWidth(2 * d);
            c.drawRoundRect(r, 10 * d, 10 * d, st);
        }
    }

    private void arrowRight(Canvas c, float x1, float x2, float y, int color) {
        st.setColor(color);
        st.setStrokeWidth(2.5f * d);
        c.drawLine(x1, y, x2, y, st);
        Path a = new Path();
        a.moveTo(x2, y);
        a.lineTo(x2 - 7 * d, y - 5 * d);
        a.lineTo(x2 - 7 * d, y + 5 * d);
        a.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(a, p);
    }

    private void arrowDown(Canvas c, float x, float y1, float y2, int color) {
        st.setColor(color);
        st.setStrokeWidth(2.5f * d);
        c.drawLine(x, y1, x, y2, st);
        Path a = new Path();
        a.moveTo(x, y2);
        a.lineTo(x - 5 * d, y2 - 7 * d);
        a.lineTo(x + 5 * d, y2 - 7 * d);
        a.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(a, p);
    }

    private void circle(Canvas c, float x, float y, float r, int fill, boolean hl) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawCircle(x, y, r, p);
        if (hl) {
            st.setColor(HL);
            st.setStrokeWidth(3 * d);
            c.drawCircle(x, y, r + 4 * d, st);
        }
    }

    /** リモコンの絵。位置は呼び出し側が受け取る。 */
    private float[] remote(Canvas c, float x, float y, float w, float h, boolean fire,
                           boolean hlBack, boolean hlHome, boolean hlOk, boolean hlArrows) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(BODY);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), w * 0.42f, w * 0.42f, p);
        float cx = x + w / 2f;
        // 上段
        if (fire) {
            circle(c, cx, y + h * 0.09f, w * 0.1f, BTN, false);
        } else {
            circle(c, x + w * 0.27f, y + h * 0.09f, w * 0.07f, Color.rgb(150, 80, 80), false);
            circle(c, x + w * 0.73f, y + h * 0.09f, w * 0.07f, BTN, false);
        }
        // 十字キー
        float dy = y + h * 0.29f, dr = w * 0.36f;
        circle(c, cx, dy, dr, Color.rgb(78, 87, 108), false);
        if (hlArrows) {
            st.setColor(HL);
            st.setStrokeWidth(3 * d);
            c.drawCircle(cx, dy, dr + 4 * d, st);
        }
        p.setColor(Color.rgb(190, 200, 220));
        float ar = w * 0.05f;
        tri(c, cx, dy - dr * 0.72f, ar, 0);
        tri(c, cx, dy + dr * 0.72f, ar, 2);
        tri(c, cx - dr * 0.72f, dy, ar, 3);
        tri(c, cx + dr * 0.72f, dy, ar, 1);
        circle(c, cx, dy, dr * 0.42f, BTN, hlOk);
        // 戻る・ホーム
        float by = y + h * 0.52f;
        float br = w * 0.115f;
        float bx = fire ? x + w * 0.2f : x + w * 0.27f;
        float hx = fire ? cx : x + w * 0.73f;
        circle(c, bx, by, br, BTN, hlBack);
        circle(c, hx, by, br, BTN, hlHome);
        // 戻るの記号（←）、ホームの記号（家）
        st.setColor(Color.rgb(230, 235, 245));
        st.setStrokeWidth(2 * d);
        c.drawLine(bx + br * 0.4f, by, bx - br * 0.4f, by, st);
        c.drawLine(bx - br * 0.4f, by, bx - br * 0.05f, by - br * 0.35f, st);
        c.drawLine(bx - br * 0.4f, by, bx - br * 0.05f, by + br * 0.35f, st);
        Path house = new Path();
        house.moveTo(hx - br * 0.45f, by + br * 0.05f);
        house.lineTo(hx, by - br * 0.4f);
        house.lineTo(hx + br * 0.45f, by + br * 0.05f);
        c.drawPath(house, st);
        c.drawRect(hx - br * 0.3f, by + br * 0.05f, hx + br * 0.3f, by + br * 0.45f, st);
        if (fire) {
            float mx = x + w * 0.8f;
            circle(c, mx, by, br, BTN, false);
            st.setColor(Color.rgb(230, 235, 245));
            for (int i = -1; i <= 1; i++) {
                c.drawLine(mx - br * 0.4f, by + i * br * 0.35f, mx + br * 0.4f, by + i * br * 0.35f, st);
            }
        }
        // 下段（飾り）
        p.setColor(BTN);
        if (fire) {
            for (int i = 0; i < 3; i++) {
                c.drawCircle(x + w * (0.22f + 0.28f * i), y + h * 0.74f, w * 0.085f, p);
            }
            for (int i = 0; i < 2; i++) {
                c.drawCircle(x + w * (0.3f + 0.4f * i), y + h * 0.9f, w * 0.085f, p);
            }
        } else {
            c.drawRoundRect(new RectF(x + w * 0.12f, y + h * 0.72f, x + w * 0.47f, y + h * 0.79f), 5 * d, 5 * d, p);
            c.drawRoundRect(new RectF(x + w * 0.53f, y + h * 0.72f, x + w * 0.88f, y + h * 0.79f), 5 * d, 5 * d, p);
            for (int i = 0; i < 3; i++) {
                c.drawCircle(x + w * (0.22f + 0.28f * i), y + h * 0.9f, w * 0.08f, p);
            }
        }
        return new float[]{bx, by, hx, by, cx, dy};
    }

    private void tri(Canvas c, float x, float y, float r, int dir) {
        Path a = new Path();
        if (dir == 0) {
            a.moveTo(x, y - r); a.lineTo(x - r, y + r); a.lineTo(x + r, y + r);
        } else if (dir == 2) {
            a.moveTo(x, y + r); a.lineTo(x - r, y - r); a.lineTo(x + r, y - r);
        } else if (dir == 3) {
            a.moveTo(x - r, y); a.lineTo(x + r, y - r); a.lineTo(x + r, y + r);
        } else {
            a.moveTo(x + r, y); a.lineTo(x - r, y - r); a.lineTo(x - r, y + r);
        }
        a.close();
        c.drawPath(a, p);
    }

    // ------------------------------------------------------------ 各図

    private void pair(Canvas c, float w, float h, boolean fire) {
        float rw = 78 * d, rh = h - 28 * d;
        float rx = 22 * d, ry = 14 * d;
        float[] pos = remote(c, rx, ry, rw, rh, fire, !fire, true, false, false);
        float tx = rx + rw + 22 * d;
        float ty = 40 * d;
        if (fire) {
            text(c, "Fire TV のリモコン", tx, ty, 15, HL, true, Paint.Align.LEFT);
            text(c, "「ホーム」ボタンを\n10秒ほど長押し", tx, ty + 30 * d, 15, TXT, true, Paint.Align.LEFT);
            text(c, "ランプが点滅したら、\nペアリング待ちです。", tx, ty + 86 * d, 13, SUB, false, Paint.Align.LEFT);
            // 矢印：ホーム → 10秒
            badge(c, pos[2], pos[3] + 26 * d, "10秒");
        } else {
            text(c, "Chromecast のリモコン", tx, ty, 15, HL, true, Paint.Align.LEFT);
            text(c, "「戻る」と「ホーム」を\n同時に3秒ほど長押し", tx, ty + 30 * d, 15, TXT, true, Paint.Align.LEFT);
            text(c, "ランプが点滅したら、\nペアリング待ちです。", tx, ty + 86 * d, 13, SUB, false, Paint.Align.LEFT);
            badge(c, (pos[0] + pos[2]) / 2f, pos[3] + 26 * d, "同時 3秒");
        }
        text(c, "つなぐ側（P3・スマホ）の\nBluetooth 設定の一覧に出てきます。", tx, ty + 130 * d, 12, SUB, false, Paint.Align.LEFT);
    }

    private void badge(Canvas c, float x, float y, String s) {
        t.setTextSize(12 * d);
        float tw = t.measureText(s);
        RectF r = new RectF(x - tw / 2f - 8 * d, y - 12 * d, x + tw / 2f + 8 * d, y + 8 * d);
        p.setStyle(Paint.Style.FILL);
        p.setColor(HL);
        c.drawRoundRect(r, 10 * d, 10 * d, p);
        text(c, s, x, y + 3 * d, 12, Color.rgb(30, 30, 30), true, Paint.Align.CENTER);
    }

    private void flow(Canvas c, float w, float h) {
        float bw = (w - 2 * 16 * d - 2 * 34 * d) / 3f;
        float by = 22 * d, bh = h - 54 * d;
        float x1 = 16 * d, x2 = x1 + bw + 34 * d, x3 = x2 + bw + 34 * d;
        box(c, new RectF(x1, by, x1 + bw, by + bh), Color.rgb(34, 44, 68), CYAN);
        box(c, new RectF(x2, by, x2 + bw, by + bh), Color.rgb(34, 44, 68), HL);
        box(c, new RectF(x3, by, x3 + bw, by + bh), Color.rgb(34, 44, 68), CYAN);
        text(c, "リモコン", x1 + bw / 2f, by + bh / 2f - 2 * d, 14, TXT, true, Paint.Align.CENTER);
        text(c, "Chromecast\nFire TV など", x1 + bw / 2f, by + bh / 2f + 18 * d, 10, SUB, false, Paint.Align.CENTER);
        text(c, "P3・スマホ", x2 + bw / 2f, by + bh / 2f - 10 * d, 13, TXT, true, Paint.Align.CENTER);
        text(c, "リモコンホーム\n（このアプリ）", x2 + bw / 2f, by + bh / 2f + 10 * d, 10, HL, false, Paint.Align.CENTER);
        text(c, "動画アプリ", x3 + bw / 2f, by + bh / 2f - 2 * d, 13, TXT, true, Paint.Align.CENTER);
        text(c, "Netflix など", x3 + bw / 2f, by + bh / 2f + 18 * d, 10, SUB, false, Paint.Align.CENTER);
        float ay = by + bh / 2f;
        arrowRight(c, x1 + bw + 4 * d, x2 - 4 * d, ay, CYAN);
        arrowRight(c, x2 + bw + 4 * d, x3 - 4 * d, ay, CYAN);
        text(c, "Bluetooth", x1 + bw + 17 * d, by - 6 * d, 10, SUB, false, Paint.Align.CENTER);
        text(c, "操作", x2 + bw + 17 * d, by - 6 * d, 10, SUB, false, Paint.Align.CENTER);
    }

    private void a11y(Canvas c, float w, float h) {
        String[] s = {"設定を開く", "「ユーザー補助」を選ぶ", "「リモコンカーソル」を選ぶ", "オンにして「許可」"};
        float bw = w - 2 * 28 * d;
        float bh = 36 * d, gap = 16 * d;
        float y = 16 * d;
        for (int i = 0; i < 4; i++) {
            RectF r = new RectF(28 * d, y, 28 * d + bw, y + bh);
            box(c, r, Color.rgb(34, 44, 68), i == 3 ? HL : CYAN);
            circle(c, 28 * d + 18 * d, y + bh / 2f, 11 * d, i == 3 ? HL : CYAN, false);
            text(c, String.valueOf(i + 1), 28 * d + 18 * d, y + bh / 2f + 5 * d, 13, Color.rgb(20, 20, 30), true, Paint.Align.CENTER);
            text(c, s[i], 28 * d + 40 * d, y + bh / 2f + 5 * d, 14, TXT, true, Paint.Align.LEFT);
            if (i < 3) {
                arrowDown(c, w / 2f, y + bh + 1 * d, y + bh + gap - 1 * d, SUB);
            }
            y += bh + gap;
        }
    }

    private void use(Canvas c, float w, float h) {
        float rw = 74 * d, rh = h - 28 * d;
        float rx = 22 * d, ry = 14 * d;
        float[] pos = remote(c, rx, ry, rw, rh, false, true, true, true, true);
        float tx = rx + rw + 24 * d;
        // 十字キー
        callout(c, tx, ry + rh * 0.08f, pos[4] + rw * 0.4f, pos[5] - rw * 0.2f, "上下左右", "ボタンからボタンへ\n選択が移る");
        // 決定
        callout(c, tx, ry + rh * 0.34f, pos[4] + rw * 0.17f, pos[5], "決定", "押す（長押しで\nモード切替）");
        // 戻る
        callout(c, tx, ry + rh * 0.64f, pos[2] + rw * 0.12f, pos[3], "戻る・ホーム", "そのまま使える");
    }

    private void callout(Canvas c, float tx, float ty, float fx, float fy, String head, String body) {
        st.setColor(HL);
        st.setStrokeWidth(1.5f * d);
        c.drawLine(fx, fy, tx - 6 * d, ty + 8 * d, st);
        text(c, head, tx, ty + 12 * d, 14, HL, true, Paint.Align.LEFT);
        text(c, body, tx, ty + 30 * d, 12, TXT, false, Paint.Align.LEFT);
    }

    private void modes(Canvas c, float w, float h) {
        String[] n = {"フォーカス", "カーソル", "スクロール"};
        String[] b = {"ボタンへ選択が\n移る（初期）", "マウスのように\n動かして押す", "上下左右へ\n画面を動かす"};
        int[] col = {CYAN, CYAN, HL};
        float bw = (w - 2 * 14 * d - 2 * 22 * d) / 3f;
        float by = 20 * d, bh = h - 70 * d;
        for (int i = 0; i < 3; i++) {
            float x = 14 * d + i * (bw + 22 * d);
            box(c, new RectF(x, by, x + bw, by + bh), Color.rgb(34, 44, 68), col[i]);
            text(c, n[i], x + bw / 2f, by + 24 * d, 13, TXT, true, Paint.Align.CENTER);
            text(c, b[i], x + bw / 2f, by + 46 * d, 10, SUB, false, Paint.Align.CENTER);
            if (i < 2) {
                arrowRight(c, x + bw + 3 * d, x + bw + 19 * d, by + bh / 2f, SUB);
            }
        }
        // 戻りの矢印
        st.setColor(SUB);
        st.setStrokeWidth(2 * d);
        float yb = by + bh + 14 * d;
        c.drawLine(14 * d + 2 * (bw + 22 * d) + bw / 2f, by + bh + 2 * d, 14 * d + 2 * (bw + 22 * d) + bw / 2f, yb, st);
        c.drawLine(14 * d + bw / 2f, yb, 14 * d + 2 * (bw + 22 * d) + bw / 2f, yb, st);
        arrowUp(c, 14 * d + bw / 2f, yb, by + bh + 2 * d);
        text(c, "決定を長押しで切り替え", w / 2f, yb + 18 * d, 12, HL, true, Paint.Align.CENTER);
    }

    private void arrowUp(Canvas c, float x, float y1, float y2) {
        st.setColor(SUB);
        st.setStrokeWidth(2 * d);
        c.drawLine(x, y1, x, y2, st);
        Path a = new Path();
        a.moveTo(x, y2);
        a.lineTo(x - 5 * d, y2 + 7 * d);
        a.lineTo(x + 5 * d, y2 + 7 * d);
        a.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(SUB);
        c.drawPath(a, p);
    }
}
