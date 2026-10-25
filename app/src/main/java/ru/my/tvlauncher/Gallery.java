package ru.my.tvlauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Фото Bing за последние 8 дней для заставки. Хранятся в files/gallery. Не из UI-потока. */
final class Gallery {

    private static final String META =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=ru-RU";
    private static final long REFRESH_MS = 12 * 60 * 60 * 1000L;

    private Gallery() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "gallery");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    /** Что уже скачано (новые первыми). Плюс фото дня с главного экрана, если галерея пуста. */
    static List<File> cached(Context c) {
        File[] files = dir(c).listFiles((d, name) -> name.endsWith(".jpg"));
        List<File> out = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
            out.addAll(Arrays.asList(files));
        }
        if (out.isEmpty()) {
            File wp = new File(c.getFilesDir(), "wallpaper.jpg");
            if (wp.exists()) out.add(wp);
        }
        return out;
    }

    /** Докачивает недостающие фото и удаляет старше недели. Возвращает true, если что-то добавилось. */
    static boolean refresh(Context c) {
        File stamp = new File(dir(c), ".updated");
        if (stamp.exists() && System.currentTimeMillis() - stamp.lastModified() < REFRESH_MS) return false;
        boolean added = false;
        try {
            JSONArray images = new JSONObject(Net.getText(META)).getJSONArray("images");
            Set<String> keep = new HashSet<>();
            for (int i = 0; i < images.length(); i++) {
                JSONObject im = images.getJSONObject(i);
                String name = im.optString("startdate", "img" + i) + ".jpg";
                keep.add(name);
                File f = new File(dir(c), name);
                if (f.exists()) continue;
                byte[] data = Net.getBytes("https://www.bing.com" + im.getString("urlbase") + "_1920x1080.jpg");
                File tmp = new File(dir(c), name + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(data);
                }
                added |= tmp.renameTo(f);
            }
            File[] all = dir(c).listFiles((d, name) -> name.endsWith(".jpg"));
            if (all != null) for (File f : all) if (!keep.contains(f.getName())) f.delete();
            new FileOutputStream(stamp).close();
            //noinspection ResultOfMethodCallIgnored
            stamp.setLastModified(System.currentTimeMillis());
        } catch (Exception e) {
            Net.warn("gallery refresh failed", e);
        }
        return added;
    }
}
