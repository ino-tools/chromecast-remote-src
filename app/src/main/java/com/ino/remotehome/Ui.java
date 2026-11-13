package com.ino.remotehome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** リモコンで選べる行（フォーカスすると枠が光る）を作る小道具。 */
final class Ui {
    private Ui() {}

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable bg(Context c, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 12));
        g.setColor(focused ? Color.rgb(46, 60, 92) : Color.rgb(28, 36, 56));
        g.setStroke(dp(c, 3), focused ? Color.rgb(125, 211, 252) : Color.TRANSPARENT);
        return g;
    }

    static TextView row(final Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(bg(c, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 10);
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setBackground(bg(c, has));
            }
        });
        return t;
    }
}
