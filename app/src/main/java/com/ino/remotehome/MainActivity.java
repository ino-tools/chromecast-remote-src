package com.ino.remotehome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * テレビ風のホーム画面。
 * 上に「いま選んでいるもの」の大きな表示、その下にカードの行（お気に入り・動画アプリ・道具・すべてのアプリ）。
 * リモコンの上下左右で選び、決定で開く。決定を長押しするとお気に入りに入る／外れる。
 * 選択の移動は Android の標準のフォーカス移動に任せる。
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

    private static final int BG = Color.rgb(9, 12, 20);
    private static final int ACCENT = Color.rgb(125, 211, 252);

    private LinearLayout content;
    private View firstFocus;

    // 上の大きな表示
    private LinearLayout hero;
    private ImageView heroIcon;
    private TextView heroTitle;
    private TextView heroSub;
    private int heroTint = ACCENT;

    private final Map<String, Integer> tints = new HashMap<String, Integer>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(18), 0, dp(40));
        sv.addView(content);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getSharedPreferences("p", MODE_PRIVATE).getBoolean("setup_seen", false) && !serviceEnabled()) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        build();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean found = Net.checkDaily(getApplicationContext());
                if (found) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            build();
                        }
                    });
                }
            }
        }).start();
    }

    // ---------------------------------------------------------------- 組み立て

    private void build() {
        content.removeAllViews();
        firstFocus = null;

        addTopBar();

        boolean en = serviceEnabled();
        if (Net.hasUpdate(this)) {
            addBanner(L.t("新しい版があります：", "Update available: ") + Net.latestName(this)
                    + L.t("　→ 決定で更新", "  → press OK to update"), UpdateActivity.class);
        }
        if (!en) {
            addBanner(L.t("リモコンで他のアプリを操作するには設定が必要です　→ 決定で「かんたん設定」",
                    "Setup is needed to control other apps with the remote  → press OK"), SetupActivity.class);
        }

        addHero();

        PackageManager pm = getPackageManager();
        List<Item> all = loadLauncherApps(pm);

        // お気に入り
        List<Item> favs = new ArrayList<Item>();
        for (String p : Prefs.favorites(this)) {
            for (Item it : all) {
                if (it.pkg.equals(p)) favs.add(it);
            }
        }
        if (!favs.isEmpty()) {
            addRowTitle(L.t("お気に入り", "Favorites"));
            addRow(favs, true);
        }

        // 動画アプリ
        List<Item> videos = new ArrayList<Item>();
        for (String p : VIDEO_PKGS) {
            for (Item it : all) {
                if (it.pkg.equals(p)) videos.add(it);
            }
        }
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

        // 道具・設定
        addRowTitle(L.t("リモコンと設定", "Remote & settings"));
        addRow(tools(en), true);

        // すべてのアプリ
        addRowTitle(L.t("すべてのアプリ", "All apps"));
        addRow(all, false);

        if (firstFocus != null) {
            firstFocus.requestFocus();
        } else {
            showHero(null);
        }
    }

    private void addTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, dp(32), dp(6));

        TextView title = new TextView(this);
        title.setText(L.t("リモコンホーム", "Remote Home"));
        title.setTextColor(Color.rgb(200, 210, 225));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView clock = new TextView(this);
        clock.setTextColor(Color.rgb(200, 210, 225));
        clock.setTextSize(16);
        clock.setText(new SimpleDateFormat("M/d (E)  HH:mm", Locale.getDefault()).format(new Date()));
        bar.addView(clock);
        content.addView(bar);
    }

    private void addBanner(String text, final Class<?> target) {
        TextView ub = Ui.row(this, text);
        ub.setTextColor(Color.rgb(255, 200, 61));
        ub.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = dp(8);
        lp.rightMargin = dp(32);
        ub.setLayoutParams(lp);
        ub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, target));
            }
        });
        content.addView(ub);
        if (firstFocus == null) firstFocus = ub;
    }

    /** 上の大きな表示。選んでいるカードに合わせて変わる。 */
    private void addHero() {
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(22), dp(16), dp(22), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(132));
        lp.rightMargin = dp(32);
        lp.bottomMargin = dp(4);
        hero.setLayoutParams(lp);

        heroIcon = new ImageView(this);
        hero.addView(heroIcon, new LinearLayout.LayoutParams(dp(84), dp(84)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = dp(20);

        heroTitle = new TextView(this);
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTextSize(26);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitle.setMaxLines(1);
        col.addView(heroTitle);

        heroSub = new TextView(this);
        heroSub.setTextColor(Color.rgb(200, 210, 225));
        heroSub.setTextSize(14);
        heroSub.setPadding(0, dp(4), 0, 0);
        col.addView(heroSub);
        hero.addView(col, cl);
        content.addView(hero);
        paintHero();
    }

    private void paintHero() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{mix(heroTint, BG, 0.55f), BG});
        g.setCornerRadius(dp(18));
        hero.setBackground(g);
    }

    private void showHero(Item it) {
        if (heroTitle == null) return;
        if (it == null) {
            heroTitle.setText(L.t("リモコンホーム", "Remote Home"));
            heroSub.setText(L.t("上下左右で選んで、決定で開きます。", "Use the arrows to choose and OK to open."));
            heroIcon.setImageDrawable(null);
            heroTint = ACCENT;
        } else {
            heroTitle.setText(it.label.replace("\n", " "));
            heroSub.setText(it.desc != null ? it.desc
                    : L.t("決定で開く　・　決定を長押しでお気に入りに入れる／外す", "OK to open  ·  hold OK to add / remove favorite"));
            if (it.icon != null) {
                heroIcon.setImageDrawable(it.icon);
            } else {
                heroIcon.setImageResource(it.res != 0 ? it.res : android.R.drawable.ic_menu_manage);
            }
            heroTint = tintOf(it);
        }
        paintHero();
    }

    private List<Item> tools(boolean en) {
        List<Item> t = new ArrayList<Item>();
        t.add(Item.tool(L.t("かんたん設定", "Quick setup"), android.R.drawable.ic_menu_manage,
                L.t("リモコンをつなぐ手順を図で案内します。", "Step-by-step setup with diagrams."),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, SetupActivity.class));
                    }
                }));
        t.add(Item.tool(L.t("リモコンのキー確認", "Key check"), android.R.drawable.ic_menu_view,
                L.t("リモコンのボタンを押すと、届いたキーを表示します。", "Shows which key each remote button sends."),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, KeyTestActivity.class));
                    }
                }));
        t.add(Item.tool(L.t("リモコンの割り当て", "Button mapping"), android.R.drawable.ic_menu_preferences,
                L.t("上下左右・決定などに使うボタンを変えます。", "Choose which buttons are used for arrows and OK."),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, KeyMapActivity.class));
                    }
                }));
        boolean on = Prefs.cursorOn(this);
        t.add(Item.tool(L.t("リモコン操作：", "Remote control: ") + (on ? L.t("オン", "ON") : L.t("オフ", "OFF")),
                android.R.drawable.ic_menu_directions,
                L.t("オフにすると、他のアプリではリモコンがふつうの動きに戻ります。", "When off, the remote behaves normally in other apps."),
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setCursorOn(MainActivity.this, !Prefs.cursorOn(MainActivity.this));
                        build();
                    }
                }));
        t.add(Item.tool(L.t("ユーザー補助：", "Accessibility: ") + (en ? L.t("有効", "on") : L.t("要設定", "set up")),
                android.R.drawable.ic_menu_info_details,
                en ? L.t("他のアプリをリモコンで操作できる状態です。", "Other apps can be controlled with the remote.")
                        : L.t("「リモコンカーソル」をオンにしてください。", "Turn on \"Remote cursor\"."),
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    }
                }));
        t.add(Item.tool(L.t("ソフトの更新", "Update"), android.R.drawable.ic_menu_upload,
                L.t("いまの版：", "Installed: ") + Net.versionName(this),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, UpdateActivity.class));
                    }
                }));
        t.add(Item.tool(L.t("改善要望を送る", "Send feedback"), android.R.drawable.ic_menu_send,
                L.t("使いにくい点や、ほしい機能を送れます。", "Tell us what to improve."),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, FeedbackActivity.class));
                    }
                }));
        return t;
    }

    private boolean serviceEnabled() {
        String v = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return v != null && v.contains(getPackageName() + "/");
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
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                continue;
            }
            Item it = new Item();
            it.pkg = pkg;
            it.label = String.valueOf(ri.loadLabel(pm));
            it.icon = ri.loadIcon(pm);
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

    // ---------------------------------------------------------------- 行とカード

    private void addRowTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(4));
        content.addView(t);
    }

    private void addRow(List<Item> items, boolean big) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(6), dp(12), dp(32), dp(12));
        row.setClipChildren(false);
        for (Item it : items) {
            View card = makeCard(it, big);
            row.addView(card);
            if (firstFocus == null) {
                firstFocus = card;
            }
        }
        hs.addView(row);
        content.addView(hs);
    }

    private View makeCard(final Item it, boolean big) {
        int w = big ? 176 : 124;
        int h = big ? 100 : 76;
        final FrameLayout card = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(h));
        lp.rightMargin = dp(12);
        card.setLayoutParams(lp);
        card.setBackground(cardBg(it, false));
        card.setFocusable(true);
        card.setClickable(true);
        card.setLongClickable(it.pkg.length() > 0);

        ImageView iv = new ImageView(this);
        if (it.icon != null) {
            iv.setImageDrawable(it.icon);
        } else {
            iv.setImageResource(it.res != 0 ? it.res : android.R.drawable.ic_menu_manage);
        }
        int is = big ? 44 : 34;
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(is), dp(is), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        ip.topMargin = dp(big ? 12 : 8);
        card.addView(iv, ip);

        TextView tv = new TextView(this);
        tv.setText(it.label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(big ? 13 : 11);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tv.setPadding(dp(6), 0, dp(6), dp(big ? 8 : 5));
        card.addView(tv, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        if (it.pkg.length() > 0 && Prefs.favorites(this).contains(it.pkg)) {
            TextView star = new TextView(this);
            star.setText("★");
            star.setTextColor(Color.rgb(255, 200, 61));
            star.setTextSize(14);
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            sp.topMargin = dp(4);
            sp.rightMargin = dp(8);
            card.addView(star, sp);
        }

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setBackground(cardBg(it, has));
                float s = has ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(120).start();
                v.setElevation(has ? dp(8) : 0);
                if (has) {
                    v.bringToFront();
                    showHero(it);
                }
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(it);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (it.pkg.length() == 0) {
                    return false;
                }
                boolean added = Prefs.toggleFavorite(MainActivity.this, it.pkg);
                Toast.makeText(MainActivity.this,
                        added ? L.t("お気に入りに入れました", "Added to favorites")
                                : L.t("お気に入りから外しました", "Removed from favorites"),
                        Toast.LENGTH_SHORT).show();
                build();
                return true;
            }
        });
        return card;
    }

    private Drawable cardBg(Item it, boolean focused) {
        int tint = tintOf(it);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{mix(tint, Color.rgb(20, 26, 42), 0.35f), Color.rgb(20, 26, 42)});
        g.setCornerRadius(dp(14));
        g.setStroke(dp(3), focused ? Color.WHITE : Color.TRANSPARENT);
        return g;
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

    // ---------------------------------------------------------------- 色

    /** アイコンのおもな色（カードの背景に薄く使う）。 */
    private int tintOf(Item it) {
        if (it.pkg.length() == 0 || it.icon == null) {
            return ACCENT;
        }
        Integer c = tints.get(it.pkg);
        if (c != null) {
            return c;
        }
        int res = ACCENT;
        try {
            Bitmap bm = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            it.icon.setBounds(0, 0, 16, 16);
            it.icon.draw(cv);
            long r = 0, g = 0, b = 0, wsum = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int p = bm.getPixel(x, y);
                    if (Color.alpha(p) < 200) continue;
                    float[] hsv = new float[3];
                    Color.colorToHSV(p, hsv);
                    // 色のついた部分を重く（白・黒・灰色は軽く）
                    long wgt = 1 + (long) (hsv[1] * hsv[2] * 20);
                    r += Color.red(p) * wgt;
                    g += Color.green(p) * wgt;
                    b += Color.blue(p) * wgt;
                    wsum += wgt;
                }
            }
            if (wsum > 0) {
                res = Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (b / wsum));
            }
        } catch (Exception ignored) {
        }
        tints.put(it.pkg, res);
        return res;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (Color.red(a) * t + Color.red(b) * (1 - t));
        int g = (int) (Color.green(a) * t + Color.green(b) * (1 - t));
        int bl = (int) (Color.blue(a) * t + Color.blue(b) * (1 - t));
        return Color.rgb(r, g, bl);
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
        String desc;
        Drawable icon;
        int res;
        Intent launch;
        Runnable action;

        static Item tool(String label, int res, String desc, Runnable action) {
            Item it = new Item();
            it.label = label;
            it.res = res;
            it.desc = desc;
            it.action = action;
            return it;
        }
    }
}
