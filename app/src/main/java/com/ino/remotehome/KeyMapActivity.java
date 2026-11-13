package com.ino.remotehome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * リモコンのボタンの割り当て。
 * 働きの行を選んで決定 → リモコンの割り当てたいボタンを押す、で登録する。
 * ファイヤースティックなど、ボタンが違うリモコン用。
 */
public class KeyMapActivity extends Activity {

    private LinearLayout list;
    private TextView hint;
    /** いま割り当て待ちの働き。なければ null。 */
    private String waiting = null;
    private final List<TextView> rows = new ArrayList<TextView>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(10, 14, 24));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(24), dp(20), dp(24), dp(24));
        sv.addView(list);
        setContentView(sv);
        build();
    }

    private void build() {
        list.removeAllViews();
        rows.clear();

        TextView title = new TextView(this);
        title.setText(L.t("リモコンの割り当て", "Remote button mapping"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        list.addView(title);

        hint = new TextView(this);
        hint.setTextColor(Color.rgb(160, 170, 190));
        hint.setTextSize(14);
        hint.setPadding(0, dp(6), 0, dp(14));
        hint.setText(L.t(
                "変えたい働きを選んで決定を押し、続けてリモコンの割り当てたいボタンを押します。やめるときは、同じ行をもう一度タップするか、戻るキーを押してください。",
                "Select a function and press OK, then press the remote button to assign. To cancel, tap the row again or press Back."));
        list.addView(hint);

        for (final String f : Prefs.FUNCS) {
            TextView t = row(Prefs.funcName(f) + "\n" + Prefs.keyNames(Prefs.codes(this, f)));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (f.equals(waiting)) {
                        waiting = null;
                    } else {
                        waiting = f;
                    }
                    refreshHint();
                }
            });
            list.addView(t);
            rows.add(t);
        }

        TextView clear = row(L.t("このボタンの割り当てを外す（モード切替だけ）", "Remove the mode-switch button"));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.clear(KeyMapActivity.this, "toggle");
                waiting = null;
                build();
            }
        });
        list.addView(clear);

        TextView reset = row(L.t("初期値に戻す", "Reset to defaults"));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.resetMap(KeyMapActivity.this);
                waiting = null;
                build();
            }
        });
        list.addView(reset);

        if (!rows.isEmpty()) {
            rows.get(0).requestFocus();
        }
    }

    private void refreshHint() {
        if (waiting == null) {
            hint.setTextColor(Color.rgb(160, 170, 190));
            hint.setText(L.t("変えたい働きを選んで決定を押してください。", "Select a function and press OK."));
        } else {
            hint.setTextColor(Color.rgb(255, 200, 61));
            hint.setText(L.t("「" + Prefs.funcName(waiting) + "」にするボタンを、リモコンで押してください。",
                    "Press the remote button for \"" + Prefs.funcName(waiting) + "\"."));
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (waiting == null) {
            return super.dispatchKeyEvent(e);
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                waiting = null;
                refreshHint();
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_HOME || k == KeyEvent.KEYCODE_UNKNOWN) {
                return true;
            }
            Prefs.assign(this, waiting, k);
            waiting = null;
            build();
        }
        return true;
    }

    private TextView row(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(bg(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setBackground(bg(has));
            }
        });
        return t;
    }

    private GradientDrawable bg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(focused ? Color.rgb(46, 60, 92) : Color.rgb(28, 36, 56));
        g.setStroke(dp(3), focused ? Color.rgb(125, 211, 252) : Color.TRANSPARENT);
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
