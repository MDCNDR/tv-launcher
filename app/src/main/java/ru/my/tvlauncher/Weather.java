package ru.my.tvlauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Погода с Open-Meteo: бесплатно, без ключа и регистрации. Вызывать не из UI-потока. */
final class Weather {

    static final class Place {
        final String name;
        final double lat;
        final double lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private Weather() {}

    /** Ищет город по названию. Возвращает null, если не найден. */
    static Place geocode(String query) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&name="
                + URLEncoder.encode(query.trim(), "UTF-8");
        JSONObject json = new JSONObject(get(url));
        JSONArray results = json.optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        return new Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"));
    }

    /** Строка вида «+5° · Пасмурно». */
    static String current(double lat, double lon) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code&timezone=auto",
                lat, lon);
        JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
        long t = Math.round(cur.getDouble("temperature_2m"));
        String temp = (t > 0 ? "+" : "") + t + "°";
        return temp + " · " + describe(cur.getInt("weather_code"));
    }

    /** Коды погоды WMO → короткое описание. */
    static String describe(int code) {
        if (code == 0) return "Ясно";
        if (code <= 2) return "Малооблачно";
        if (code == 3) return "Пасмурно";
        if (code == 45 || code == 48) return "Туман";
        if (code >= 51 && code <= 57) return "Морось";
        if (code >= 61 && code <= 67) return "Дождь";
        if (code >= 71 && code <= 77) return "Снег";
        if (code >= 80 && code <= 82) return "Ливень";
        if (code == 85 || code == 86) return "Снегопад";
        if (code >= 95) return "Гроза";
        return "—";
    }

    private static String get(String address) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
                return buf.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            conn.disconnect();
        }
    }
}
