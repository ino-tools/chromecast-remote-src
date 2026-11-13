package com.ino.remotehome;

/**
 * 外部とのつながりの設定（ここだけ）。
 * 自動反映タスクはこのファイルを変更しない。
 */
final class AppConfig {
    private AppConfig() {}

    /** 改善要望の Googleフォーム（/forms/d/e/<ID>/ の ID）。空なら要望送信は「準備中」表示 */
    static final String FORM_ID = "";
    /** フォームの「要望」欄の entry 番号（entry.<数字>） */
    static final String FORM_ENTRY = "";

    /** 新しい版の確認先（このリポジトリの最新の Release） */
    static final String LATEST_API =
            "https://api.github.com/repos/ino-tools/chromecast-remote-src/releases/latest";
    /** ダウンロードしてよい APK の置き場所（これ以外は取らない） */
    static final String DOWNLOAD_PREFIX =
            "https://github.com/ino-tools/chromecast-remote-src/releases/download/";
    /** うまくいかないときに開くページ */
    static final String RELEASES_PAGE = "https://github.com/ino-tools/chromecast-remote-src/releases";
}
