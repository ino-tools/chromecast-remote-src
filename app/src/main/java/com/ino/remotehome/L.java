package com.ino.remotehome;

import java.util.Locale;

/** 画面の文字は必ず L.t("日本語", "English") で書く。 */
final class L {
    private L() {}

    static String t(String ja, String en) {
        return Locale.getDefault().getLanguage().equals("ja") ? ja : en;
    }
}
