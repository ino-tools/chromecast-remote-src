package com.ino.remotehome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** リモコン（キーボード・マウスも）から届いた入力を、そのまま表示する確認画面。 */
public class KeyTestActivity extends Activity {

    private TextView big;
    private TextView log;
    private ScrollView scroll;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private int count = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 14, 24));
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(L.t("リモコンのキー確認", "Remote key check"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView lead = new TextView(this);
        lead.setText(L.t(
                "リモコンのボタンを押すと、届いたキーを表示します。戻るキーを2回続けて押すと、この画面を閉じます。",
                "Press buttons on the remote to see the keys received. Press Back twice quickly to close."));
        lead.setTextColor(Color.rgb(160, 170, 190));
        lead.setTextSize(14);
        lead.setPadding(0, dp(6), 0, dp(14));
        root.addView(lead);

        big = new TextView(this);
        big.setText("—");
        big.setTextColor(Color.rgb(125, 211, 252));
        big.setTextSize(34);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        big.setGravity(Gravity.CENTER);
        big.setPadding(0, dp(10), 0, dp(10));
        root.addView(big);

        scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(Color.rgb(200, 210, 225));
        log.setTextSize(12);
        log.setTypeface(Typeface.MONOSPACE);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private long lastBack = 0;

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            boolean twice = now - lastBack < 900;
            lastBack = now;
            show(e);
            if (twice) {
                finish();
            }
            return true;
        }
        show(e);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
            return true;
        }
        append("MOTION " + MotionEvent.actionToString(e.getActionMasked())
                + " src=0x" + Integer.toHexString(e.getSource())
                + " x=" + (int) e.getX() + " y=" + (int) e.getY()
                + " dev=" + deviceName(e.getDevice()));
        return true;
    }

    private void show(KeyEvent e) {
        String act = e.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "UP  ";
        String name = KeyEvent.keyCodeToString(e.getKeyCode()).replace("KEYCODE_", "");
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            big.setText(name);
        }
        append(act + " " + name + " (" + e.getKeyCode() + ")"
                + " scan=" + e.getScanCode()
                + (e.getRepeatCount() > 0 ? " rep=" + e.getRepeatCount() : "")
                + " src=0x" + Integer.toHexString(e.getSource())
                + " dev=" + deviceName(e.getDevice()));
    }

    private static String deviceName(InputDevice d) {
        return d == null ? "?" : d.getName();
    }

    private void append(String s) {
        count++;
        CharSequence old = log.getText();
        String t = fmt.format(new Date()) + " " + s + "\n" + old;
        if (t.length() > 6000) {
            t = t.substring(0, 6000);
        }
        log.setText(t);
        scroll.scrollTo(0, 0);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
