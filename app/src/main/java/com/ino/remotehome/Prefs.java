package com.ino.remotehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** 端末内だけに保存する設定。 */
final class Prefs {
    private Prefs() {}

    /** リモコンのボタンに割り当てられる働き。 */
    static final String[] FUNCS = {"up", "down", "left", "right", "ok", "toggle"};

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("p", Context.MODE_PRIVATE);
    }

    static boolean cursorOn(Context c) {
        return sp(c).getBoolean("cursor_on", true);
    }

    static void setCursorOn(Context c, boolean v) {
        sp(c).edit().putBoolean("cursor_on", v).apply();
    }

    static String funcName(String f) {
        if (f.equals("up")) return L.t("上", "Up");
        if (f.equals("down")) return L.t("下", "Down");
        if (f.equals("left")) return L.t("左", "Left");
        if (f.equals("right")) return L.t("右", "Right");
        if (f.equals("ok")) return L.t("決定（タップ）", "OK (tap)");
        return L.t("モード切替（カーソル⇔スクロール）", "Switch mode (cursor/scroll)");
    }

    /** 初期の割り当て。ファイヤースティックやゲームパッド型の一般的なリモコンも入れてある。 */
    static int[] defaults(String f) {
        if (f.equals("up")) return new int[]{KeyEvent.KEYCODE_DPAD_UP};
        if (f.equals("down")) return new int[]{KeyEvent.KEYCODE_DPAD_DOWN};
        if (f.equals("left")) return new int[]{KeyEvent.KEYCODE_DPAD_LEFT};
        if (f.equals("right")) return new int[]{KeyEvent.KEYCODE_DPAD_RIGHT};
        if (f.equals("ok")) {
            return new int[]{KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BUTTON_SELECT};
        }
        return new int[0];
    }

    static List<Integer> codes(Context c, String f) {
        String s = sp(c).getString("map_" + f, null);
        List<Integer> out = new ArrayList<Integer>();
        if (s == null) {
            for (int k : defaults(f)) out.add(k);
            return out;
        }
        for (String p : s.split(",")) {
            if (p.length() > 0) {
                try {
                    out.add(Integer.parseInt(p));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static void save(Context c, String f, List<Integer> l) {
        StringBuilder b = new StringBuilder();
        for (int k : l) {
            if (b.length() > 0) b.append(',');
            b.append(k);
        }
        sp(c).edit().putString("map_" + f, b.toString()).apply();
    }

    /** 押されたキーがどの働きか。割り当てがなければ null（そのまま通す）。 */
    static String funcOf(Context c, int code) {
        for (String f : FUNCS) {
            if (codes(c, f).contains(code)) return f;
        }
        return null;
    }

    /** このボタンを f だけに割り当てる（ほかの働きからは外す）。 */
    static void assign(Context c, String f, int code) {
        for (String g : FUNCS) {
            List<Integer> l = codes(c, g);
            if (g.equals(f)) {
                l = new ArrayList<Integer>();
                l.add(code);
                save(c, g, l);
            } else if (l.remove(Integer.valueOf(code))) {
                save(c, g, l);
            }
        }
    }

    static void clear(Context c, String f) {
        save(c, f, new ArrayList<Integer>());
    }

    static void resetMap(Context c) {
        SharedPreferences.Editor e = sp(c).edit();
        for (String f : FUNCS) e.remove("map_" + f);
        e.apply();
    }

    static String keyNames(List<Integer> l) {
        if (l.isEmpty()) return L.t("（なし）", "(none)");
        StringBuilder b = new StringBuilder();
        for (int k : l) {
            if (b.length() > 0) b.append(" / ");
            b.append(KeyEvent.keyCodeToString(k).replace("KEYCODE_", ""));
        }
        return b.toString();
    }
}
