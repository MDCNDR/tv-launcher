package ru.my.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Фон главного экрана: «фото дня» Bing, обновляется раз в сутки и хранится на устройстве. */
final class Wallpaper {

    static final class Result {
        final Bitmap bitmap;
        final String caption;

        Result(Bitmap bitmap, String caption) {
            this.bitmap = bitmap;
            this.caption = caption;
        }
    }

    private static final String META =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=ru-RU";

    private Wallpaper() {}

    /** Вызывать не из UI-потока. Если сети нет, отдаёт вчерашнюю картинку. */
    static Result load(Context c) throws Exception {
        SharedPreferences sp = c.getSharedPreferences("wallpaper", Context.MODE_PRIVATE);
        File img = new File(c.getFilesDir(), "wallpaper.jpg");
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());

        if (!img.exists() || !today.equals(sp.getString("day", null))) {
            try {
                JSONObject meta = new JSONObject(Net.getText(META))
                        .getJSONArray("images").getJSONObject(0);
                String url = "https://www.bing.com" + meta.getString("urlbase") + "_1920x1080.jpg";
                byte[] data = Net.getBytes(url);
                File tmp = new File(c.getFilesDir(), "wallpaper.tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(data);
                }
                if (!tmp.renameTo(img)) throw new IllegalStateException("rename failed");
                sp.edit()
                        .putString("day", today)
                        .putString("caption", cleanCaption(meta.optString("copyright", "")))
                        .apply();
            } catch (Exception e) {
                Net.warn("wallpaper download failed", e);
                if (!img.exists()) throw e;
            }
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(img.getAbsolutePath(), opts);
        if (bitmap == null) {
            img.delete();
            throw new IllegalStateException("cannot decode wallpaper");
        }
        return new Result(bitmap, sp.getString("caption", ""));
    }

    /** «Озеро Байкал, Россия (© Автор/Getty Images)» → «Озеро Байкал, Россия». */
    private static String cleanCaption(String s) {
        int i = s.indexOf(" (©");
        return (i > 0 ? s.substring(0, i) : s).trim();
    }
}
