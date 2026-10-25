package ru.my.tvlauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Locale;

/**
 * Погода: основной источник Open-Meteo, запасной — wttr.in.
 * Оба бесплатные и без ключа. Вызывать не из UI-потока.
 */
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
        JSONObject json = new JSONObject(Net.getText(url));
        JSONArray results = json.optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        return new Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"));
    }

    /** Строка вида «+5° · Пасмурно». Бросает исключение, если оба источника недоступны. */
    static String current(double lat, double lon) throws Exception {
        try {
            return fromOpenMeteo(lat, lon);
        } catch (Exception first) {
            Net.warn("open-meteo failed", first);
            try {
                return fromWttr(lat, lon);
            } catch (Exception second) {
                Net.warn("wttr.in failed", second);
                second.addSuppressed(first);
                throw second;
            }
        }
    }

    private static String fromOpenMeteo(double lat, double lon) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code&timezone=auto",
                lat, lon);
        JSONObject cur = new JSONObject(Net.getText(url)).getJSONObject("current");
        return format(cur.getDouble("temperature_2m"), describe(cur.getInt("weather_code")));
    }

    private static String fromWttr(double lat, double lon) throws Exception {
        String url = String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1&lang=ru", lat, lon);
        JSONObject cur = new JSONObject(Net.getText(url))
                .getJSONArray("current_condition").getJSONObject(0);
        String desc = null;
        JSONArray ru = cur.optJSONArray("lang_ru");
        if (ru != null && ru.length() > 0) desc = ru.getJSONObject(0).optString("value", null);
        if (desc == null || desc.trim().isEmpty()) desc = "—";
        return format(cur.getDouble("temp_C"), capitalize(desc.trim()));
    }

    private static String format(double temperature, String description) {
        long t = Math.round(temperature);
        return (t > 0 ? "+" : "") + t + "° · " + description;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(new Locale("ru")) + s.substring(1);
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
}
