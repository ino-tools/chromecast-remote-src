package com.ino.remotehome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 改善要望を送る。送り先は Google フォーム（アカウント不要）。 */
public class FeedbackActivity extends Activity {

    private EditText edit;
    private TextView send;
    private boolean sending = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(10, 14, 24));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 24));
        sv.addView(list);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText(L.t("改善要望を送る", "Send feedback"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        list.addView(title);

        TextView lead = new TextView(this);
        lead.setText(Net.formReady()
                ? L.t("使いにくいところ、ほしい機能などを書いてください。送るのは、書いた文章と、アプリの版・端末の種類だけです。名前やメールアドレスは送りません。",
                        "Tell us what is hard to use or what you would like. Only your text, the app version and the device model are sent. No name or email.")
                : L.t("要望の受付は準備中です。", "Feedback is not available yet."));
        lead.setTextColor(Color.rgb(160, 170, 190));
        lead.setTextSize(14);
        lead.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        list.addView(lead);

        edit = new EditText(this);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.rgb(120, 130, 150));
        edit.setHint(L.t("ここに書く", "Write here"));
        edit.setMinLines(5);
        edit.setGravity(Gravity.TOP);
        edit.setBackground(Ui.bg(this, false));
        edit.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        edit.setEnabled(Net.formReady());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 12);
        list.addView(edit, lp);

        send = Ui.row(this, L.t("送る", "Send"));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        list.addView(send);
    }

    private void send() {
        if (sending) return;
        if (!Net.formReady()) {
            Toast.makeText(this, L.t("要望の受付は準備中です", "Feedback is not available yet"), Toast.LENGTH_LONG).show();
            return;
        }
        final String body = edit.getText().toString().trim();
        if (body.length() < 3) {
            Toast.makeText(this, L.t("内容を書いてください", "Please write something"), Toast.LENGTH_SHORT).show();
            return;
        }
        sending = true;
        send.setText(L.t("送っています…", "Sending…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = Net.sendFeedback(getApplicationContext(), body, "feedback");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sending = false;
                        send.setText(L.t("送る", "Send"));
                        if (ok) {
                            Toast.makeText(FeedbackActivity.this, L.t("送りました。ありがとうございます", "Sent. Thank you"),
                                    Toast.LENGTH_LONG).show();
                            edit.setText("");
                            finish();
                        } else {
                            Toast.makeText(FeedbackActivity.this, L.t("送れませんでした。通信を確かめてください", "Could not send. Check your connection"),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }
}
