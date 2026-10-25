package ru.my.tvlauncher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Спутниковые снимки Google Earth View (~1500 мест). Список встроен в лаунчер
 * (assets/earthview.tsv: id, страна, регион), сами снимки качаются с gstatic.com
 * по мере надобности и хранятся в files/earth.
 */
final class EarthView {

    static final class Item {
        final String id;
        final String country;
        final String region;

        Item(String id, String country, String region) {
            this.id = id;
            this.country = country;
            this.region = region;
        }

        /** «Чили, Tamarugal · Google Earth» — страна по-русски, регион как есть. */
        String caption() {
            String c = russianCountry(country);
            String place = region.isEmpty() ? c : c + ", " + region;
            return place.isEmpty() ? "Google Earth" : place + " · Google Earth";
        }
    }

    private static final String URL = "https://www.gstatic.com/prettyearth/assets/full/%s.jpg";

    private static List<Item> list;
    private static Map<String, String> countryCodes;

    private EarthView() {}

    static synchronized List<Item> all(Context c) {
        if (list != null) return list;
        List<Item> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getAssets().open("earthview.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length >= 1 && !p[0].isEmpty()) {
                    out.add(new Item(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : ""));
                }
            }
        } catch (Exception e) {
            Net.warn("earthview list", e);
        }
        list = Collections.unmodifiableList(out);
        return list;
    }

    static Item byId(Context c, String id) {
        for (Item i : all(c)) if (i.id.equals(id)) return i;
        return null;
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "earth");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static File file(Context c, Item item) {
        return new File(dir(c), item.id + ".jpg");
    }

    /** Скачивает снимок, если его ещё нет. Не из UI-потока. */
    static File fetch(Context c, Item item) throws Exception {
        File f = file(c, item);
        if (f.exists() && f.length() > 0) return f;
        byte[] data = Net.getBytes(String.format(Locale.ROOT, URL, item.id));
        File tmp = new File(dir(c), item.id + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) throw new IllegalStateException("rename failed");
        return f;
    }

    /** Снимок дня: одинаковый весь день, каждый день новый, без повторов ~4 года. */
    static Item ofTheDay(Context c, long epochDay) {
        List<Item> all = all(c);
        if (all.isEmpty()) return null;
        int n = all.size();
        // Шаг, взаимно простой с n, перемешивает порядок, но обходит все снимки.
        int step = 7919 % n;
        while (gcd(step, n) != 1) step++;
        int idx = (int) Math.floorMod(epochDay * step, (long) n);  // 0..n-1
        return all.get(idx);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /** Английское название страны → русское (через встроенные в Android названия). */
    static synchronized String russianCountry(String english) {
        if (english == null || english.isEmpty()) return "";
        if (countryCodes == null) {
            countryCodes = new HashMap<>();
            for (String code : Locale.getISOCountries()) {
                countryCodes.put(norm(new Locale("", code).getDisplayCountry(Locale.ENGLISH)), code);
            }
            // Названия из Earth View, которые расходятся с названиями в Android.
            countryCodes.put(norm("The Bahamas"), "BS");
            countryCodes.put(norm("Myanmar (Burma)"), "MM");
            countryCodes.put(norm("Côte d'Ivoire"), "CI");
            countryCodes.put(norm("Democratic Republic of the Congo"), "CD");
            countryCodes.put(norm("Republic of the Congo"), "CG");
            countryCodes.put(norm("Macedonia (FYROM)"), "MK");
            countryCodes.put(norm("Czech Republic"), "CZ");
            countryCodes.put(norm("Russia"), "RU");
            countryCodes.put(norm("South Korea"), "KR");
            countryCodes.put(norm("North Korea"), "KP");
            countryCodes.put(norm("Vietnam"), "VN");
            countryCodes.put(norm("Laos"), "LA");
            countryCodes.put(norm("Iran"), "IR");
            countryCodes.put(norm("Syria"), "SY");
            countryCodes.put(norm("Bolivia"), "BO");
            countryCodes.put(norm("Venezuela"), "VE");
            countryCodes.put(norm("Tanzania"), "TZ");
            countryCodes.put(norm("Turkey"), "TR");
            countryCodes.put(norm("Antigua and Barbuda"), "AG");
            countryCodes.put(norm("Hong Kong"), "HK");
            countryCodes.put(norm("Falkland Islands (Islas Malvinas)"), "FK");
            countryCodes.put(norm("Heard Island and McDonald Islands"), "HM");
            countryCodes.put(norm("Republic of the Union of Myanmar"), "MM");
            countryCodes.put(norm("West Sahara"), "EH");
        }
        String direct = PLACES.get(norm(english));
        if (direct != null) return direct;
        String code = countryCodes.get(norm(english));
        if (code == null) return english;
        String ru = new Locale("", code).getDisplayCountry(new Locale("ru"));
        return ru.isEmpty() ? english : ru;
    }

    /** Места, которые не являются странами в справочнике Android. */
    private static final Map<String, String> PLACES = new HashMap<>();
    static {
        PLACES.put(norm("Antarctica"), "Антарктида");
        PLACES.put(norm("Crimea"), "Крым");
        PLACES.put(norm("South Pacific"), "Южная часть Тихого океана");
        PLACES.put(norm("Diomede Ilands"), "Острова Диомида");
        PLACES.put(norm("The Minquiers"), "Менкье");
    }

    private static String norm(String s) {
        String x = s.toLowerCase(Locale.ROOT).trim();
        if (x.startsWith("the ")) x = x.substring(4);
        return x.replaceAll("[^a-z]", "");
    }
}
