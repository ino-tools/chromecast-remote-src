package com.ino.remotehome;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** アプリ内からのソフト更新。新しい版を確認 → ダウンロード → インストール確認画面。 */
public class UpdateActivity extends Activity {

    private LinearLayout list;
    private TextView status;
    private TextView action;
    private Net.Latest latest;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(10, 14, 24));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 24));
        sv.addView(list);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText(L.t("ソフトの更新", "Software update"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        list.addView(title);

        TextView cur = new TextView(this);
        cur.setText(L.t("いまの版：", "Installed: ") + Net.versionName(this));
        cur.setTextColor(Color.rgb(160, 170, 190));
        cur.setTextSize(14);
        cur.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        list.addView(cur);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setPadding(0, 0, 0, Ui.dp(this, 14));
        list.addView(status);

        action = Ui.row(this, "");
        list.addView(action);

        TextView page = Ui.row(this, L.t("ダウンロードページを開く（うまくいかないとき）", "Open download page (if this fails)"));
        page.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.RELEASES_PAGE)));
                } catch (Exception ignored) {
                }
            }
        });
        list.addView(page);

        check();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void check() {
        if (busy) return;
        busy = true;
        status.setText(L.t("新しい版を確認しています…", "Checking for updates…"));
        action.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Net.Latest l = Net.fetchLatest();
                if (l != null) Net.store(getApplicationContext(), l);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        latest = l;
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void refresh() {
        if (busy) return;
        action.setVisibility(View.VISIBLE);
        if (latest == null) {
            status.setText(L.t("確認できませんでした。通信を確かめて、もう一度試してください。",
                    "Could not check. Check your connection and try again."));
            setAction(L.t("もう一度確認する", "Check again"), new Runnable() {
                @Override
                public void run() {
                    check();
                }
            });
            return;
        }
        if (latest.versionCode <= Net.versionCode(this)) {
            status.setText(L.t("最新の版です。", "You have the latest version."));
            setAction(L.t("もう一度確認する", "Check again"), new Runnable() {
                @Override
                public void run() {
                    check();
                }
            });
            return;
        }
        status.setText(L.t("新しい版があります：", "New version: ") + latest.versionName);
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText(status.getText() + "\n" + L.t(
                    "更新するには、このアプリに「提供元不明のアプリのインストール」を許可する必要があります。",
                    "To update, allow this app to install unknown apps."));
            setAction(L.t("許可の設定を開く", "Open the permission setting"), new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {
                    }
                }
            });
            return;
        }
        setAction(L.t("更新する", "Update now"), new Runnable() {
            @Override
            public void run() {
                startDownload();
            }
        });
    }

    private void setAction(String label, final Runnable r) {
        action.setText(label);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        action.requestFocus();
    }

    private void startDownload() {
        if (busy || latest == null) return;
        busy = true;
        action.setVisibility(View.GONE);
        status.setText(L.t("ダウンロードしています… 0%", "Downloading… 0%"));
        final Net.Latest l = latest;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File f = Net.download(getApplicationContext(), l, new Net.Progress() {
                    @Override
                    public void onProgress(final int p) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                status.setText(L.t("ダウンロードしています… ", "Downloading… ") + p + "%");
                            }
                        });
                    }
                });
                boolean ok = false;
                if (f != null) {
                    ok = install(f);
                }
                final boolean fok = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (fok) {
                            status.setText(L.t("インストールの確認画面で「更新」を選んでください。",
                                    "Choose \"Update\" on the install prompt."));
                            action.setVisibility(View.GONE);
                        } else {
                            status.setText(L.t("更新に失敗しました。もう一度試すか、ダウンロードページから入れてください。",
                                    "Update failed. Try again or use the download page."));
                            refresh();
                        }
                    }
                });
            }
        }).start();
    }

    /** PackageInstaller でインストールを始める（結果は InstallReceiver に届く）。 */
    private boolean install(File f) {
        try {
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(sp);
            PackageInstaller.Session s = pi.openSession(id);
            OutputStream out = s.openWrite("app", 0, f.length());
            InputStream in = new FileInputStream(f);
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
            s.fsync(out);
            in.close();
            out.close();
            Intent i = new Intent(this, InstallReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent p = PendingIntent.getBroadcast(this, id, i, flags);
            s.commit(p.getIntentSender());
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
