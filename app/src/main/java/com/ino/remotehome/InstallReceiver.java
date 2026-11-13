package com.ino.remotehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** インストールの結果を受け取る。確認画面が必要なときは開く。 */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (Exception e) {
                    Toast.makeText(c, L.t("確認画面を開けませんでした", "Could not open the install prompt"),
                            Toast.LENGTH_LONG).show();
                }
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, L.t("更新できませんでした：", "Update failed: ") + (m == null ? "" : m),
                    Toast.LENGTH_LONG).show();
        }
    }
}
