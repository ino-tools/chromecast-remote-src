package com.ino.remotehome;

import android.content.Context;
import android.content.SharedPreferences;

/** 端末内だけに保存する設定。 */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("p", Context.MODE_PRIVATE);
    }

    static boolean cursorOn(Context c) {
        return sp(c).getBoolean("cursor_on", true);
    }

    static void setCursorOn(Context c, boolean v) {
        sp(c).edit().putBoolean("cursor_on", v).apply();
    }
}
