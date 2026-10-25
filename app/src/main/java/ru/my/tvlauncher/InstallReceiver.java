package ru.my.tvlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/** Результат установки обновления от системного установщика. */
public class InstallReceiver extends BroadcastReceiver {

    @SuppressWarnings("deprecation")
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Система просит подтвердить установку — показываем её окно.
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Exception e) {
                    Log.w(Net.TAG, "cannot show install confirmation", e);
                }
            }
            return;
        }
        if (status != PackageInstaller.STATUS_SUCCESS) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Log.w(Net.TAG, "update failed: " + status + " " + msg);
            Toast.makeText(context,
                    context.getString(R.string.update_failed) + (msg == null ? "" : ": " + msg),
                    Toast.LENGTH_LONG).show();
        }
    }
}
