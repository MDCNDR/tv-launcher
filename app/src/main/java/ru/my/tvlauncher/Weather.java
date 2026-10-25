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

    /** Текущая погода. Неизвестные числа — NaN. */
    static final class Now {
        final double temp;
        final double feels;
        final double max;
        final double min;
        final double wind;   // м/с
        final String description;
        final int icon;      // R.drawable.wx_*

        Now(double temp, double feels, double max, double min, double wind, String description, int icon) {
            this.temp = temp;
            this.feels = feels;
            this.max = max;
            this.min = min;
            this.wind = wind;
            this.description = description;
            this.icon = icon;
        }
    }

    /** Прогноз на один день. Неизвестные числа — NaN, вероятность осадков −1. */
    static final class Day {
        final java.util.Date date;
        final double max;
        final double min;
        final int precipChance;
        final double wind;
        final String description;
        final int icon;

        Day(java.util.Date date, double max, double min, int precipChance, double wind,
            String description, int icon) {
            this.date = date;
            this.max = max;
            this.min = min;
            this.precipChance = precipChance;
            this.wind = wind;
            this.description = description;
            this.icon = icon;
        }
    }

    /** Прогноз на 7 дней (Open-Meteo) или на 3 дня (wttr.in), если первый недоступен. */
    static java.util.List<Day> forecast(double lat, double lon) throws Exception {
        try {
            return forecastOpenMeteo(lat, lon);
        } catch (Exception first) {
            Net.warn("open-meteo forecast failed", first);
            try {
                return forecastWttr(lat, lon);
            } catch (Exception second) {
                Net.warn("wttr.in forecast failed", second);
                second.addSuppressed(first);
                throw second;
            }
        }
    }

    private static java.util.List<Day> forecastOpenMeteo(double lat, double lon) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                        + "precipitation_probability_max,wind_speed_10m_max"
                        + "&forecast_days=7&wind_speed_unit=ms&timezone=auto",
                lat, lon);
        JSONObject daily = new JSONObject(Net.getText(url)).getJSONObject("daily");
        JSONArray time = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray max = daily.optJSONArray("temperature_2m_max");
        JSONArray min = daily.optJSONArray("temperature_2m_min");
        JSONArray pop = daily.optJSONArray("precipitation_probability_max");
        JSONArray wind = daily.optJSONArray("wind_speed_10m_max");
        java.util.List<Day> out = new java.util.ArrayList<>();
        for (int i = 0; i < time.length(); i++) {
            int code = codes.optInt(i, 3);
            out.add(new Day(parseDay(time.getString(i)),
                    at(max, i), at(min, i),
                    pop == null || pop.isNull(i) ? -1 : pop.optInt(i, -1),
                    at(wind, i),
                    describe(code), icon(kindWmo(code), true)));
        }
        return out;
    }

    private static java.util.List<Day> forecastWttr(double lat, double lon) throws Exception {
        String url = String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1&lang=ru", lat, lon);
        JSONArray days = new JSONObject(Net.getText(url)).getJSONArray("weather");
        java.util.List<Day> out = new java.util.ArrayList<>();
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.getJSONObject(i);
            JSONArray hourly = d.optJSONArray("hourly");
            JSONObject noon = hourly != null && hourly.length() > 0
                    ? hourly.getJSONObject(Math.min(4, hourly.length() - 1)) : new JSONObject();
            String desc = "—";
            JSONArray ru = noon.optJSONArray("lang_ru");
            if (ru != null && ru.length() > 0) desc = capitalize(ru.getJSONObject(0).optString("value", "—").trim());
            int pop = -1;
            if (hourly != null) {
                for (int h = 0; h < hourly.length(); h++) {
                    JSONObject x = hourly.getJSONObject(h);
                    pop = Math.max(pop, Math.max(x.optInt("chanceofrain", -1), x.optInt("chanceofsnow", -1)));
                }
            }
            double windKmh = noon.optDouble("windspeedKmph", Double.NaN);
            out.add(new Day(parseDay(d.getString("date")),
                    d.optDouble("maxtempC", Double.NaN), d.optDouble("mintempC", Double.NaN),
                    pop, Double.isNaN(windKmh) ? Double.NaN : windKmh / 3.6,
                    desc, icon(kindWwo(noon.optInt("weatherCode", 116)), true)));
        }
        return out;
    }

    private static double at(JSONArray a, int i) {
        return a == null || a.isNull(i) ? Double.NaN : a.optDouble(i, Double.NaN);
    }

    private static java.util.Date parseDay(String yyyyMmDd) throws java.text.ParseException {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(yyyyMmDd);
    }

    private enum Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

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

    /** Бросает исключение, только если оба источника недоступны. */
    static Now current(double lat, double lon) throws Exception {
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

    private static Now fromOpenMeteo(double lat, double lon) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
                        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1"
                        + "&wind_speed_unit=ms&timezone=auto",
                lat, lon);
        JSONObject json = new JSONObject(Net.getText(url));
        JSONObject cur = json.getJSONObject("current");
        JSONObject daily = json.optJSONObject("daily");
        int code = cur.getInt("weather_code");
        boolean day = cur.optInt("is_day", 1) == 1;
        return new Now(
                cur.getDouble("temperature_2m"),
                cur.optDouble("apparent_temperature", Double.NaN),
                firstOf(daily, "temperature_2m_max"),
                firstOf(daily, "temperature_2m_min"),
                cur.optDouble("wind_speed_10m", Double.NaN),
                describe(code),
                icon(kindWmo(code), day));
    }

    private static Now fromWttr(double lat, double lon) throws Exception {
        String url = String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1&lang=ru", lat, lon);
        JSONObject json = new JSONObject(Net.getText(url));
        JSONObject cur = json.getJSONArray("current_condition").getJSONObject(0);
        String desc = null;
        JSONArray ru = cur.optJSONArray("lang_ru");
        if (ru != null && ru.length() > 0) desc = ru.getJSONObject(0).optString("value", null);
        if (desc == null || desc.trim().isEmpty()) desc = "—";

        double max = Double.NaN, min = Double.NaN;
        JSONArray days = json.optJSONArray("weather");
        if (days != null && days.length() > 0) {
            max = days.getJSONObject(0).optDouble("maxtempC", Double.NaN);
            min = days.getJSONObject(0).optDouble("mintempC", Double.NaN);
        }
        double windKmh = cur.optDouble("windspeedKmph", Double.NaN);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean day = hour >= 7 && hour < 19;
        return new Now(
                cur.getDouble("temp_C"),
                cur.optDouble("FeelsLikeC", Double.NaN),
                max, min,
                Double.isNaN(windKmh) ? Double.NaN : windKmh / 3.6,
                capitalize(desc.trim()),
                icon(kindWwo(cur.optInt("weatherCode", 116)), day));
    }

    private static double firstOf(JSONObject daily, String key) {
        if (daily == null) return Double.NaN;
        JSONArray a = daily.optJSONArray(key);
        return a == null || a.length() == 0 ? Double.NaN : a.optDouble(0, Double.NaN);
    }

    /** «+5°», «−3°», «0°». */
    static String degrees(double t) {
        long r = Math.round(t);
        if (r > 0) return "+" + r + "°";
        if (r < 0) return "−" + (-r) + "°";
        return "0°";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(new Locale("ru")) + s.substring(1);
    }

    private static int icon(Kind k, boolean day) {
        switch (k) {
            case CLEAR: return day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
            case PARTLY: return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
            case FOG: return R.drawable.wx_fog;
            case RAIN: return R.drawable.wx_rain;
            case SNOW: return R.drawable.wx_snow;
            case THUNDER: return R.drawable.wx_thunder;
            default: return R.drawable.wx_cloudy;
        }
    }

    /** Коды WMO (Open-Meteo). */
    private static Kind kindWmo(int c) {
        if (c == 0) return Kind.CLEAR;
        if (c <= 2) return Kind.PARTLY;
        if (c == 3) return Kind.CLOUDY;
        if (c == 45 || c == 48) return Kind.FOG;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return Kind.SNOW;
        if (c >= 95) return Kind.THUNDER;
        if (c >= 51) return Kind.RAIN;
        return Kind.CLOUDY;
    }

    /** Коды WWO (wttr.in). */
    private static Kind kindWwo(int c) {
        if (c == 113) return Kind.CLEAR;
        if (c == 116) return Kind.PARTLY;
        if (c == 119 || c == 122) return Kind.CLOUDY;
        if (c == 143 || c == 248 || c == 260) return Kind.FOG;
        if (c == 200 || (c >= 386 && c <= 395)) return Kind.THUNDER;
        if (c == 179 || c == 227 || c == 230 || (c >= 320 && c <= 338) || c == 350
                || (c >= 368 && c <= 377) || c == 182 || c == 185 || c == 317) return Kind.SNOW;
        return Kind.RAIN;
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
