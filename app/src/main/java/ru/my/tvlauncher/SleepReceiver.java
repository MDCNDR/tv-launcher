package ru.my.tvlauncher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Таймер сна: предупреждение за минуту и перевод приставки в сон. */
public class SleepReceiver extends BroadcastReceiver {

    static final String ACTION_WARN = "ru.my.tvlauncher.SLEEP_WARN";
    static final String ACTION_SLEEP = "ru.my.tvlauncher.SLEEP_NOW";

    private static final long WARN_BEFORE_MS = 60_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        if (ACTION_WARN.equals(intent.getAction())) {
            if (prefs.sleepAt() > 0) {
                Toast.makeText(context, R.string.sleep_warn, Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!ACTION_SLEEP.equals(intent.getAction())) return;
        prefs.setSleepAt(0);
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                // KEYCODE_SLEEP: приставка засыпает, по HDMI-CEC обычно выключается и телевизор.
                Root.run("input keyevent 223", 15_000);
            } finally {
                result.finish();
            }
        }, "sleep").start();
    }

    /** Ставит таймер на minutes минут; 0 — отменяет. */
    static void schedule(Context c, int minutes) {
        Context app = c.getApplicationContext();
        AlarmManager am = app.getSystemService(AlarmManager.class);
        PendingIntent warn = pending(app, ACTION_WARN, 1);
        PendingIntent sleep = pending(app, ACTION_SLEEP, 2);
        am.cancel(warn);
        am.cancel(sleep);
        Prefs prefs = new Prefs(app);
        if (minutes <= 0) {
            prefs.setSleepAt(0);
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        prefs.setSleepAt(at);
        if (minutes > 1) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - WARN_BEFORE_MS, warn);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, sleep);
    }

    private static PendingIntent pending(Context c, String action, int code) {
        Intent i = new Intent(c, SleepReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
