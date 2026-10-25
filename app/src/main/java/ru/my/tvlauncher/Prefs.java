package ru.my.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Настройки: избранные приложения и город для погоды. */
final class Prefs {

    /** Что показать на главном экране при первом запуске (если установлено). */
    private static final String[] DEFAULT_FAVORITES = {
            "com.teamsmart.videomanager.tv",   // SmartTube
            "ru.kinopoisk.tv",                 // Кинопоиск
            "ru.astroapps.hdrezka",            // HDrezka
            "mobi.zona",                       // Zona
            "com.lazycatsoftware.lmd",         // LazyMedia Deluxe
            "io.gh.reisxd.tizentube.cobalt",   // TizenTube
    };

    private static final String KEY_FAVS = "favorites";
    private static final String KEY_CITY = "city";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";

    private final SharedPreferences sp;
    private final Context context;

    Prefs(Context c) {
        context = c.getApplicationContext();
        sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    List<String> favorites() {
        String raw = sp.getString(KEY_FAVS, null);
        if (raw == null) {
            List<String> defaults = new ArrayList<>();
            for (String pkg : DEFAULT_FAVORITES) {
                if (Apps.isInstalled(context, pkg)) defaults.add(pkg);
            }
            saveFavorites(defaults);
            return defaults;
        }
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    void saveFavorites(List<String> favs) {
        sp.edit().putString(KEY_FAVS, TextUtils.join(",", favs)).apply();
    }

    boolean isFavorite(String pkg) {
        return favorites().contains(pkg);
    }

    void toggleFavorite(String pkg) {
        List<String> favs = favorites();
        if (!favs.remove(pkg)) favs.add(pkg);
        saveFavorites(favs);
    }

    void removeFavorite(String pkg) {
        List<String> favs = favorites();
        favs.remove(pkg);
        saveFavorites(favs);
    }

    /** Сдвигает приложение на delta позиций (−1 влево, +1 вправо). */
    void move(String pkg, int delta) {
        List<String> favs = favorites();
        int from = favs.indexOf(pkg);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= favs.size()) return;
        favs.remove(from);
        favs.add(to, pkg);
        saveFavorites(favs);
    }

    // ---------- VPN по приложениям ----------

    static final int VPN_ANY = 0;   // не трогать
    static final int VPN_ON = 1;    // включать перед запуском
    static final int VPN_OFF = 2;   // выключать перед запуском

    int vpnRule(String pkg) {
        return sp.getInt("vpn_rule_" + pkg, VPN_ANY);
    }

    void setVpnRule(String pkg, int rule) {
        if (rule == VPN_ANY) sp.edit().remove("vpn_rule_" + pkg).apply();
        else sp.edit().putInt("vpn_rule_" + pkg, rule).apply();
    }

    // ---------- Таймер сна ----------

    /** Время засыпания (System.currentTimeMillis) или 0, если таймер не задан. */
    long sleepAt() {
        long t = sp.getLong("sleep_at", 0);
        return t > System.currentTimeMillis() ? t : 0;
    }

    void setSleepAt(long t) {
        sp.edit().putLong("sleep_at", t).apply();
    }

    String city() {
        return sp.getString(KEY_CITY, "Москва");
    }

    double lat() {
        return Double.longBitsToDouble(sp.getLong(KEY_LAT, Double.doubleToLongBits(55.7558)));
    }

    double lon() {
        return Double.longBitsToDouble(sp.getLong(KEY_LON, Double.doubleToLongBits(37.6173)));
    }

    void setCity(String name, double lat, double lon) {
        sp.edit()
                .putString(KEY_CITY, name)
                .putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon))
                .apply();
    }
}
