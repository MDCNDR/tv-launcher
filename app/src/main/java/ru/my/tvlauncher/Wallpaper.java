package ru.my.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/** Фон главного экрана: снимок дня Google Earth View (каждый день новый). */
final class Wallpaper {

    static final class Result {
        final Bitmap bitmap;
        final String caption;

        Result(Bitmap bitmap, String caption) {
            this.bitmap = bitmap;
            this.caption = caption;
        }
    }

    private Wallpaper() {}

    /** Вызывать не из UI-потока. Если сети нет, отдаёт последний скачанный снимок. */
    static Result load(Context c) throws Exception {
        long now = System.currentTimeMillis();
        long today = (now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L;
        EarthView.Item item = EarthView.ofTheDay(c, today);
        File img = null;
        String caption = "";
        if (item != null) {
            try {
                img = EarthView.fetch(c, item);
                caption = item.caption();
                c.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit()
                        .putString("last_id", item.id).apply();
            } catch (Exception e) {
                Net.warn("earth view of the day failed", e);
            }
        }
        if (img == null) { // нет сети — берём вчерашний
            String last = c.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).getString("last_id", null);
            EarthView.Item prev = last == null ? null : EarthView.byId(c, last);
            if (prev != null && EarthView.file(c, prev).exists()) {
                img = EarthView.file(c, prev);
                caption = prev.caption();
            }
        }
        if (img == null) throw new IllegalStateException("no wallpaper yet");

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(img.getAbsolutePath(), opts);
        if (bitmap == null) {
            //noinspection ResultOfMethodCallIgnored
            img.delete();
            throw new IllegalStateException("cannot decode wallpaper");
        }
        return new Result(bitmap, caption);
    }
}
