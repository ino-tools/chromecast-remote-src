package com.ino.remotehome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Google TV 風のホーム画面（試作）。
 * リモコンの上下左右で選び、決定で起動する。ボタンの動きは Android の標準のフォーカス移動に任せる。
 */
public class MainActivity extends Activity {

    /** 動画アプリとして先に出すもの（入っているものだけ表示）。 */
    private static final String[] VIDEO_PKGS = {
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "jp.unext.mediaplayer",
            "com.disney.disneyplus",
            "tv.abema",
            "jp.co.tver.android",
            "jp.happyon.android",
            "com.dmm.app.video.android",
            "jp.nicovideo.android",
            "com.hulu.plus",
            "jp.co.nhk.nhkplus",
            "com.google.android.apps.youtube.music",
    };

    private static final int BG = Color.rgb(10, 14, 24);
    private static final int TILE = Color.rgb(28, 36, 56);
    private static final int ACCENT = Color.rgb(125, 211, 252);

    private LinearLayout content;
    private TextView clock;
    private View firstFocus;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(24), 0, dp(40));
        sv.addView(content);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        content.removeAllViews();
        firstFocus = null;

        clock = new TextView(this);
        clock.setTextColor(Color.rgb(200, 210, 225));
        clock.setTextSize(18);
        clock.setText(new SimpleDateFormat("M/d (E) HH:mm", Locale.getDefault()).format(new Date()));
        clock.setGravity(Gravity.END);
        clock.setPadding(0, 0, dp(32), dp(8));
        content.addView(clock);

        PackageManager pm = getPackageManager();
        List<Item> all = loadLauncherApps(pm);

        List<Item> videos = new ArrayList<Item>();
        for (String p : VIDEO_PKGS) {
            for (Item it : all) {
                if (it.pkg.equals(p)) {
                    videos.add(it);
                }
            }
        }

        // 1段目：動画アプリ（なければ空の案内）
        addRowTitle(L.t("動画アプリ", "Video apps"));
        if (videos.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(L.t("入っている動画アプリが見つかりません。下の「すべてのアプリ」から開けます。",
                    "No known video apps found. Open any app from \"All apps\" below."));
            t.setTextColor(Color.rgb(160, 170, 190));
            t.setTextSize(14);
            t.setPadding(0, 0, 0, dp(12));
            content.addView(t);
        } else {
            addRow(videos, true);
        }

        // 2段目：道具（キー確認）
        addRowTitle(L.t("道具", "Tools"));
        List<Item> tools = new ArrayList<Item>();
        tools.add(Item.tool(L.t("リモコンの\nキー確認", "Remote\nkey check"), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, KeyTestActivity.class));
            }
        }));
        addRow(tools, false);

        // 設定の行：カーソル操作
        addRowTitle(L.t("リモコンカーソル", "Remote cursor"));
        List<Item> cur = new ArrayList<Item>();
        boolean on = Prefs.cursorOn(this);
        boolean en = serviceEnabled();
        cur.add(Item.tool(L.t("カーソル操作\n", "Cursor\n") + (on ? L.t("オン", "ON") : L.t("オフ", "OFF")),
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setCursorOn(MainActivity.this, !Prefs.cursorOn(MainActivity.this));
                        build();
                    }
                }));
        cur.add(Item.tool(L.t("ユーザー補助\n", "Accessibility\n") + (en ? L.t("有効", "enabled") : L.t("要設定", "set up")),
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    }
                }));
        cur.add(Item.tool(L.t("リモコンの\n割り当て", "Remote\nbuttons"), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, KeyMapActivity.class));
            }
        }));
        addRow(cur, false);
        addHelp(en);

        // 3段目：すべてのアプリ
        addRowTitle(L.t("すべてのアプリ", "All apps"));
        addRow(all, false);

        if (firstFocus != null) {
            firstFocus.requestFocus();
        }
    }

    private boolean serviceEnabled() {
        String v = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return v != null && v.contains(getPackageName() + "/");
    }

    private void addHelp(boolean enabled) {
        TextView t = new TextView(this);
        t.setText(enabled
                ? L.t("他のアプリを開くと、リモコンの上下左右でカーソルが動きます。決定＝タップ、決定を長押し＝スクロールモードと切り替え。",
                        "In other apps, the arrow keys move a cursor. OK = tap, long-press OK = switch to scroll mode.")
                : L.t("「ユーザー補助」で「リモコンカーソル」をオンにすると、他のアプリもリモコンで操作できます。",
                        "Turn on \"Remote cursor\" in Accessibility to control other apps with the remote."));
        t.setTextColor(Color.rgb(160, 170, 190));
        t.setTextSize(13);
        t.setPadding(0, 0, dp(32), dp(8));
        content.addView(t);
    }

    private List<Item> loadLauncherApps(PackageManager pm) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
        List<Item> out = new ArrayList<Item>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) {
                continue;
            }
            Item it = new Item();
            it.pkg = pkg;
            it.label = String.valueOf(ri.loadLabel(pm));
            it.icon = ri.loadIcon(pm);
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                continue;
            }
            it.launch = launch;
            out.add(it);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private void addRowTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(8));
        content.addView(t);
    }

    private void addRow(List<Item> items, boolean big) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(10), dp(32), dp(10));
        row.setClipChildren(false);
        for (Item it : items) {
            View tile = makeTile(it, big);
            row.addView(tile);
            if (firstFocus == null) {
                firstFocus = tile;
            }
        }
        hs.addView(row);
        content.addView(hs);
    }

    private View makeTile(final Item it, boolean big) {
        int w = big ? 150 : 110;
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(8), dp(12), dp(8), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), -2);
        lp.rightMargin = dp(12);
        tile.setLayoutParams(lp);
        tile.setBackground(bg(false));
        tile.setFocusable(true);
        tile.setClickable(true);

        ImageView iv = new ImageView(this);
        if (it.icon != null) {
            iv.setImageDrawable(it.icon);
        } else {
            iv.setImageResource(android.R.drawable.ic_menu_manage);
        }
        int is = big ? 72 : 52;
        tile.addView(iv, new LinearLayout.LayoutParams(dp(is), dp(is)));

        TextView tv = new TextView(this);
        tv.setText(it.label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(big ? 14 : 12);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tv.setPadding(0, dp(8), 0, 0);
        tile.addView(tv, new LinearLayout.LayoutParams(-1, -2));

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setBackground(bg(has));
                float s = has ? 1.12f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(120).start();
                v.setElevation(has ? dp(8) : 0);
                if (has) {
                    v.bringToFront();
                }
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(it);
            }
        });
        return tile;
    }

    private void open(Item it) {
        if (it.action != null) {
            it.action.run();
            return;
        }
        try {
            startActivity(it.launch);
        } catch (Exception e) {
            // 開けなかったときは何もしない
        }
    }

    private Drawable bg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(focused ? Color.rgb(46, 60, 92) : TILE);
        if (focused) {
            g.setStroke(dp(3), ACCENT);
        } else {
            g.setStroke(dp(3), Color.TRANSPARENT);
        }
        return g;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        // ホームでは「戻る」で閉じない（リモコンの誤操作で消えないように）
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class Item {
        String pkg = "";
        String label = "";
        Drawable icon;
        Intent launch;
        Runnable action;

        static Item tool(String label, Runnable action) {
            Item it = new Item();
            it.label = label;
            it.action = action;
            return it;
        }
    }
}
