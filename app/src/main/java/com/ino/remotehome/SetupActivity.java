package com.ino.remotehome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * かんたん設定：リモコンをつなぐ → キーを確かめる → ユーザー補助をオン → 使い方。
 * 各ステップが済んだかは自動で調べて、チェックを付ける。
 */
public class SetupActivity extends Activity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(10, 14, 24));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 32));
        sv.addView(list);
        setContentView(sv);
        getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("setup_seen", true).apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    // ---------------------------------------------------------------- 状態

    private String remoteNames() {
        StringBuilder sb = new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int src = d.getSources();
            boolean key = (src & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                    || (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
            if (!key || d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_NONE) continue;
            String n = d.getName();
            // 本体のボタンなどは除く
            String low = n.toLowerCase();
            if (low.contains("gpio") || low.contains("pmic") || low.contains("fpc") || low.contains("uinput")
                    || low.contains("hall") || low.contains("power") || low.contains("keypad")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(n);
        }
        return sb.toString();
    }

    private boolean serviceEnabled() {
        String v = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return v != null && v.contains(getPackageName() + "/");
    }

    // ---------------------------------------------------------------- 画面

    private void build() {
        list.removeAllViews();

        TextView title = new TextView(this);
        title.setText(L.t("かんたん設定", "Quick setup"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        list.addView(title);

        TextView lead = text(L.t(
                "上から順に進めてください。済んだ手順には ✓ が付きます。設定を終えて戻ると、自動で更新されます。",
                "Go from top to bottom. Finished steps get a ✓. This page updates when you come back."), 14,
                Color.rgb(160, 170, 190));
        lead.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        list.addView(lead);

        View first = null;

        // 1. リモコンをつなぐ
        String names = remoteNames();
        header(1, L.t("リモコンをつなぐ", "Connect the remote"), !names.isEmpty());
        list.addView(text(names.isEmpty()
                ? L.t("リモコンが見つかりません。", "No remote found.")
                : L.t("見つかった機器：", "Found: ") + names, 14, names.isEmpty() ? Color.rgb(255, 200, 61) : Color.rgb(120, 220, 160)));
        list.addView(text(L.t(
                "・Chromecast のリモコン：「戻る」と「ホーム」を同時に3秒ほど長押し（ランプが点滅したらペアリング待ち）。\n"
                        + "・そのほかのリモコン：説明書のペアリング（接続待ち）の操作をする。\n"
                        + "・下のボタンで Bluetooth の設定を開き、リモコンを選んで接続。",
                "- Chromecast remote: hold Back + Home together for about 3 seconds (the light blinks when it is ready to pair).\n"
                        + "- Other remotes: follow the pairing steps in the manual.\n"
                        + "- Open Bluetooth settings below and choose the remote."), 13, Color.rgb(200, 210, 225)));
        TextView bt = Ui.row(this, L.t("Bluetooth の設定を開く", "Open Bluetooth settings"));
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
        });
        list.addView(bt);
        if (first == null) first = bt;

        // 2. キーを確かめる
        header(2, L.t("ボタンが届くか確かめる", "Check the buttons"), false);
        list.addView(text(L.t(
                "リモコンのボタンを押して、画面に名前が出るか確認します。上下左右・決定が出れば大丈夫です。出ないボタンがあるリモコンは、次の「割り当て」で別のボタンを使えます。",
                "Press the remote buttons and see their names appear. If arrows and OK show up, you are fine. If some buttons do not, assign other buttons below."),
                13, Color.rgb(200, 210, 225)));
        TextView kt = Ui.row(this, L.t("リモコンのキー確認を開く", "Open the key check"));
        kt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SetupActivity.this, KeyTestActivity.class));
            }
        });
        list.addView(kt);
        TextView km = Ui.row(this, L.t("ボタンの割り当てを変える（必要なときだけ）", "Change button assignments (only if needed)"));
        km.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SetupActivity.this, KeyMapActivity.class));
            }
        });
        list.addView(km);

        // 3. ユーザー補助
        boolean en = serviceEnabled();
        header(3, L.t("「リモコンカーソル」をオンにする", "Turn on \"Remote cursor\""), en);
        list.addView(text(en
                ? L.t("オンになっています。他のアプリをリモコンで操作できます。", "It is on. You can control other apps with the remote.")
                : L.t(
                "他のアプリをリモコンで操作するために必要です。\n"
                        + "① 下のボタンで「ユーザー補助」を開く\n"
                        + "② 「ダウンロードしたアプリ」または「インストール済みのアプリ」の中の「リモコンカーソル」を選ぶ\n"
                        + "③ オンにして、確認で「許可」\n"
                        + "※ 「制限された設定」と出てオンにできないときは、下の「アプリの情報を開く」→右上の︙→「制限付き設定を許可」をしてから、もう一度。",
                "Needed to control other apps with the remote.\n"
                        + "1) Open Accessibility with the button below\n"
                        + "2) Choose \"Remote cursor\" under downloaded / installed apps\n"
                        + "3) Turn it on and allow\n"
                        + "* If it says \"Restricted setting\", open App info below, tap the ⋮ menu, choose \"Allow restricted settings\", then try again."),
                13, en ? Color.rgb(120, 220, 160) : Color.rgb(200, 210, 225)));
        TextView ac = Ui.row(this, L.t("ユーザー補助の設定を開く", "Open Accessibility settings"));
        ac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        list.addView(ac);
        if (!en) {
            TextView info = Ui.row(this, L.t("アプリの情報を開く（制限付き設定の許可）", "Open App info (allow restricted settings)"));
            info.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                }
            });
            list.addView(info);
        }

        // 4. アプリ内更新（任意）
        boolean canInstall = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        header(4, L.t("アプリ内の更新を使えるようにする（おすすめ）", "Allow in-app updates (recommended)"), canInstall);
        list.addView(text(canInstall
                ? L.t("許可されています。「ソフトの更新」から新しい版を入れられます。", "Allowed. You can install new versions from \"Update\".")
                : L.t("「提供元不明のアプリのインストール」をこのアプリに許可すると、アプリの中から更新できます。",
                "Allow \"Install unknown apps\" for this app to update from inside it."),
                13, canInstall ? Color.rgb(120, 220, 160) : Color.rgb(200, 210, 225)));
        if (!canInstall) {
            TextView ins = Ui.row(this, L.t("許可の設定を開く", "Open the permission setting"));
            ins.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())));
                }
            });
            list.addView(ins);
        }

        // 5. 使い方
        header(5, L.t("使い方", "How to use"), false);
        list.addView(text(L.t(
                "・ホームで動画アプリを選んで決定。\n"
                        + "・アプリの中では、上下左右でボタンからボタンへ選択（水色の枠）が移り、決定で押します。端まで行くと自動でスクロール。\n"
                        + "・決定を長押しすると「フォーカス → カーソル → スクロール」と切り替わります。フォーカスで選べない画面は、カーソルを使ってください。\n"
                        + "・戻る・ホームは、リモコンのボタンがそのまま使えます。\n"
                        + "・「カーソル操作」をオフにすると、リモコンはふつうの動きに戻ります（ホームの「リモコンカーソル」の行）。",
                "- Pick a video app on the home screen and press OK.\n"
                        + "- Inside apps, the arrows move the selection (blue frame) between buttons, and OK presses it. At the edge it scrolls by itself.\n"
                        + "- Long-press OK to switch Focus -> Cursor -> Scroll. Use Cursor where Focus cannot select.\n"
                        + "- Back and Home work as usual.\n"
                        + "- Turn off \"Cursor\" on the home screen to return the remote to normal."),
                13, Color.rgb(200, 210, 225)));

        TextView done = Ui.row(this, L.t("ホームに戻る", "Back to home"));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        list.addView(done);

        if (first != null) first.requestFocus();
    }

    private void header(int n, String s, boolean ok) {
        TextView t = new TextView(this);
        t.setText((ok ? "✓ " : "") + n + ". " + s);
        t.setTextColor(ok ? Color.rgb(120, 220, 160) : Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        list.addView(t);
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 0, 0, Ui.dp(this, 8));
        return t;
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
