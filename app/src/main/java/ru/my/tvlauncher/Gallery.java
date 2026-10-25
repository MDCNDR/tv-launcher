package ru.my.tvlauncher;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Снимки Earth View для заставки. Держим на устройстве до 30 штук, раз в 6 часов
 * докачиваем 6 новых случайных и удаляем самые старые. Не из UI-потока.
 */
final class Gallery {

    private static final int KEEP = 30;
    private static final int ADD_PER_REFRESH = 6;
    private static final long REFRESH_MS = 6 * 60 * 60 * 1000L;

    private Gallery() {}

    /** Что уже скачано, в случайном порядке. */
    static List<File> cached(Context c) {
        File[] files = EarthView.dir(c).listFiles((d, name) -> name.endsWith(".jpg"));
        List<File> out = new ArrayList<>();
        if (files != null) out.addAll(Arrays.asList(files));
        java.util.Collections.shuffle(out);
        return out;
    }

    /** Подпись к снимку по имени файла («1234.jpg» → «Чили, Tamarugal»). */
    static String caption(Context c, File f) {
        String id = f.getName().replace(".jpg", "");
        EarthView.Item item = EarthView.byId(c, id);
        return item == null ? "" : item.caption();
    }

    /** Докачивает новые снимки. Возвращает true, если что-то добавилось. */
    static boolean refresh(Context c) {
        File stamp = new File(EarthView.dir(c), ".updated");
        boolean empty = cached(c).size() < ADD_PER_REFRESH;
        if (!empty && stamp.exists() && System.currentTimeMillis() - stamp.lastModified() < REFRESH_MS) {
            return false;
        }
        cleanupOldBing(c);
        List<EarthView.Item> all = EarthView.all(c);
        if (all.isEmpty()) return false;
        Random random = new Random();
        int added = 0;
        for (int tries = 0; added < ADD_PER_REFRESH && tries < ADD_PER_REFRESH * 3; tries++) {
            EarthView.Item item = all.get(random.nextInt(all.size()));
            if (EarthView.file(c, item).exists()) continue;
            try {
                EarthView.fetch(c, item);
                added++;
            } catch (Exception e) {
                Net.warn("earth view download", e);
                if (added == 0 && tries >= 2) break; // сети нет — не мучаемся
            }
        }
        prune(c);
        try {
            //noinspection ResultOfMethodCallIgnored
            stamp.createNewFile();
            //noinspection ResultOfMethodCallIgnored
            stamp.setLastModified(System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        return added > 0;
    }

    /** Оставляем KEEP самых новых файлов. */
    private static void prune(Context c) {
        File[] files = EarthView.dir(c).listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null || files.length <= KEEP) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    /** Удаляем снимки Bing от прошлых версий. */
    private static void cleanupOldBing(Context c) {
        File old = new File(c.getFilesDir(), "gallery");
        File[] files = old.listFiles();
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
        //noinspection ResultOfMethodCallIgnored
        old.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), "wallpaper.jpg").delete();
    }
}
