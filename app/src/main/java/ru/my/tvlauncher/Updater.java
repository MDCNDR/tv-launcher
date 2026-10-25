package ru.my.tvlauncher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Самообновление: GitHub Actions после каждой сборки выкладывает в релиз «latest»
 * файлы tv-launcher.apk и version.json. Здесь мы их проверяем, скачиваем и ставим.
 * Сетевые методы вызывать не из UI-потока.
 */
final class Updater {

    private static final String BASE = "https://github.com/MDCNDR/tv-launcher/releases/download/latest/";

    static final class Info {
        final long versionCode;
        final String versionName;

        Info(long versionCode, String versionName) {
            this.versionCode = versionCode;
            this.versionName = versionName;
        }
    }

    private Updater() {}

    static long installedVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /** Новая версия или null, если обновлять нечего. */
    static Info check(Context c) throws Exception {
        JSONObject j = new JSONObject(Net.getText(BASE + "version.json?t=" + System.currentTimeMillis()));
        Info info = new Info(j.getLong("versionCode"), j.optString("versionName", ""));
        return info.versionCode > installedVersion(c) ? info : null;
    }

    static File download(Context c) throws IOException {
        File apk = new File(c.getCacheDir(), "update.apk");
        byte[] data = Net.getBytes(BASE + "tv-launcher.apk");
        try (OutputStream out = new FileOutputStream(apk)) {
            out.write(data);
        }
        return apk;
    }

    /** Передаёт APK системному установщику; результат придёт в {@link InstallReceiver}. */
    static void install(Context c, File apk) throws IOException {
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent done = new Intent(c, InstallReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, id, done,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pi.getIntentSender());
        } catch (IOException | RuntimeException e) {
            installer.abandonSession(id);
            throw e;
        }
    }
}
