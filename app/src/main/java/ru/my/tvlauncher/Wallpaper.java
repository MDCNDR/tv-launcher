package ru.my.tvlauncher;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/**
 * Статичный фон: натюрморт «банан и два яйца» (свой рисунок, встроен в лаунчер — без интернета).
 * Декодируем в половинном разрешении и в 16-битном цвете: на плавных градиентах
 * разницы не видно, а в памяти ~1 МБ вместо ~8 МБ.
 */
final class Wallpaper {

    private static Bitmap cached;

    private Wallpaper() {}

    static synchronized Bitmap get(Resources res) {
        if (cached != null && !cached.isRecycled()) return cached;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        cached = BitmapFactory.decodeResource(res, R.drawable.wallpaper_main, o);
        return cached;
    }

    /** Удаляем файлы прошлых версий: снимки Earth View/Bing и скачанное обновление. */
    static void cleanupOldFiles(Context c) {
        deleteTree(new File(c.getFilesDir(), "earth"));
        deleteTree(new File(c.getFilesDir(), "gallery"));
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), "wallpaper.jpg").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(c.getCacheDir(), "update.apk").delete();
        c.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
