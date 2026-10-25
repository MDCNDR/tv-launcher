package ru.my.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final long MINUTE = 60 * 1000L;
    /** Как часто проверять, не пора ли обновить данные (и повторить после ошибки). */
    private static final long TICK_MS = 5 * MINUTE;
    private static final long WEATHER_TTL = 30 * MINUTE;
    private static final long RATES_TTL = 3 * 60 * MINUTE;
    private static final long WALLPAPER_TTL = 60 * MINUTE;
    private static final long UPDATE_TTL = 6 * 60 * MINUTE;
    private static final long VPN_WAIT_MS = 12_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            refreshStale();
            if (resumed) ui.postDelayed(this, TICK_MS);
        }
    };

    private Prefs prefs;
    private LinearLayout favRow;
    private View weatherBlock;
    private ImageView weatherIcon;
    private TextView weatherTemp;
    private TextView weatherDesc;
    private TextView weatherDetails;
    private TextView rates;
    private ImageView wallpaper;
    private TextView wallpaperCaption;
    /** Пакет плитки, на которую вернуть фокус после перестройки строки. */
    private String focusPkg;
    private boolean resumed;
    private long weatherLoadedAt;
    private long ratesLoadedAt;
    private long wallpaperLoadedAt;
    private long updateCheckedAt;
    private String weatherCity;

    private Vpn vpn;
    private ImageView vpnBtn;
    private TextView topHint;
    /** Ожидаемое состояние после нажатия, пока Happ подключается/отключается. */
    private Boolean vpnTarget;
    private final Runnable vpnTimeout = () -> {
        vpnTarget = null;
        renderVpn(vpn.isActive());
    };

    private TextView updateBtn;
    private Updater.Info update;
    private boolean updating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        favRow = findViewById(R.id.fav_row);
        weatherBlock = findViewById(R.id.weather_block);
        weatherIcon = findViewById(R.id.weather_icon);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherDesc = findViewById(R.id.weather_desc);
        weatherDetails = findViewById(R.id.weather_details);
        rates = findViewById(R.id.rates);
        wallpaper = findViewById(R.id.wallpaper);
        wallpaperCaption = findViewById(R.id.wallpaper_caption);

        findViewById(R.id.btn_wifi).setOnClickListener(v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        findViewById(R.id.btn_bluetooth).setOnClickListener(v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));
        weatherBlock.setOnClickListener(v -> startActivity(new Intent(this, ForecastActivity.class)));
        weatherBlock.setOnLongClickListener(v -> {
            CityDialog.show(this, prefs, () -> {
                weatherLoadedAt = 0;
                loadWeather();
            });
            return true;
        });

        vpn = new Vpn(this);
        vpnBtn = findViewById(R.id.btn_vpn);
        vpnBtn.setOnClickListener(v -> toggleVpn());

        // Подпись под круглыми кнопками: показывает, что сейчас в фокусе.
        topHint = findViewById(R.id.top_hint);
        for (int id : new int[]{R.id.btn_vpn, R.id.btn_wifi, R.id.btn_bluetooth, R.id.btn_settings}) {
            findViewById(id).setOnFocusChangeListener((v, has) -> {
                if (has) {
                    topHint.setText(v.getContentDescription());
                    topHint.setVisibility(View.VISIBLE);
                } else if (!isTopButtonFocused()) {
                    topHint.setVisibility(View.INVISIBLE);
                }
            });
        }

        updateBtn = findViewById(R.id.btn_update);
        updateBtn.setOnClickListener(v -> installUpdate());
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        buildFavorites();
        if (!prefs.city().equals(weatherCity)) weatherLoadedAt = 0;
        if (Vpn.isHappInstalled(this)) {
            vpnBtn.setVisibility(View.VISIBLE);
            vpn.start(this::renderVpn);
        } else {
            vpnBtn.setVisibility(View.GONE);
        }
        ui.removeCallbacks(refreshTick);
        ui.post(refreshTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(refreshTick);
        vpn.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Кнопка «Домой» на главном экране: вернуть фокус на первое приложение.
        if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Это главный экран: «Назад» никуда не ведёт.
    }

    // ---------- Избранные приложения ----------

    private void buildFavorites() {
        View focused = getCurrentFocus();
        boolean focusInRow = focused != null && focused.getParent() == favRow;
        if (focusInRow && focusPkg == null && focused.getTag() instanceof String) {
            focusPkg = (String) focused.getTag();
        }

        favRow.removeAllViews();
        View toFocus = null;
        List<String> favs = prefs.favorites();
        for (String pkg : favs) {
            Apps.Entry e = Apps.forPackage(this, pkg);
            if (e == null) continue; // приложение удалили — просто не показываем
            View tile = Tiles.make(this, favRow, e.image, e.isBanner, e.label);
            tile.setTag(pkg);
            tile.setOnClickListener(v -> launch(pkg));
            tile.setOnLongClickListener(v -> {
                showTileMenu(pkg, e.label);
                return true;
            });
            favRow.addView(tile);
            if (pkg.equals(focusPkg)) toFocus = tile;
        }

        View add = Tiles.make(this, favRow, getDrawable(R.drawable.ic_add), false, getString(R.string.add_apps));
        add.setOnClickListener(v -> startActivity(new Intent(this, AppPickerActivity.class)));
        favRow.addView(add);

        if (toFocus == null && (focused == null || focusInRow)) toFocus = favRow.getChildAt(0);
        focusPkg = null;
        if (toFocus != null) {
            View target = toFocus;
            favRow.post(target::requestFocus);
        }
    }

    private void showTileMenu(String pkg, CharSequence label) {
        String[] items = {
                getString(R.string.move_left),
                getString(R.string.move_right),
                getString(R.string.remove),
                getString(R.string.app_info),
        };
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(label)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            prefs.move(pkg, -1);
                            focusPkg = pkg;
                            break;
                        case 1:
                            prefs.move(pkg, +1);
                            focusPkg = pkg;
                            break;
                        case 2:
                            prefs.removeFavorite(pkg);
                            break;
                        case 3:
                            openAppInfo(pkg);
                            return;
                    }
                    buildFavorites();
                })
                .show();
    }

    private void launch(String pkg) {
        Intent i = Apps.launchIntent(getPackageManager(), pkg);
        if (i == null) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkg, null)));
        } catch (ActivityNotFoundException ex) {
            openSettings(Settings.ACTION_SETTINGS);
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ex) {
            if (!Settings.ACTION_SETTINGS.equals(action)) {
                openSettings(Settings.ACTION_SETTINGS);
            } else {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------- Погода, курсы, фон ----------

    /** Обновляет то, что устарело. Неудачные загрузки повторятся на следующем тике. */
    private void refreshStale() {
        long now = System.currentTimeMillis();
        if (now - weatherLoadedAt > WEATHER_TTL) loadWeather();
        if (now - ratesLoadedAt > RATES_TTL) loadRates();
        if (now - wallpaperLoadedAt > WALLPAPER_TTL) loadWallpaper();
        if (now - updateCheckedAt > UPDATE_TTL) checkUpdate();
    }

    private void loadWeather() {
        final String city = prefs.city();
        final double lat = prefs.lat();
        final double lon = prefs.lon();
        io.execute(() -> {
            Weather.Now now;
            try {
                now = Weather.current(lat, lon);
            } catch (Exception ex) {
                now = null;
            }
            final Weather.Now w = now;
            ui.post(() -> {
                if (w != null) {
                    showWeather(w, city);
                    weatherCity = city;
                    weatherLoadedAt = System.currentTimeMillis();
                } else if (weatherLoadedAt == 0) {
                    weatherIcon.setVisibility(View.INVISIBLE);
                    weatherTemp.setText("—");
                    weatherDesc.setText(R.string.weather_error);
                    weatherDetails.setText(city);
                }
            });
        });
    }

    private void showWeather(Weather.Now w, String city) {
        weatherIcon.setImageResource(w.icon);
        weatherIcon.setVisibility(View.VISIBLE);
        weatherTemp.setText(Weather.degrees(w.temp));
        weatherDesc.setText(w.description + " · " + city);

        StringBuilder d = new StringBuilder();
        if (!Double.isNaN(w.feels)) d.append("Ощущается ").append(Weather.degrees(w.feels));
        if (!Double.isNaN(w.max) && !Double.isNaN(w.min)) {
            if (d.length() > 0) d.append("\n");
            d.append("↑ ").append(Weather.degrees(w.max)).append("   ↓ ").append(Weather.degrees(w.min));
        }
        if (!Double.isNaN(w.wind)) {
            d.append(d.length() > 0 ? "   ·   " : "").append("ветер ").append(Math.round(w.wind)).append(" м/с");
        }
        weatherDetails.setText(d);
        weatherDetails.setVisibility(d.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void loadRates() {
        io.execute(() -> {
            CharSequence text;
            try {
                text = Rates.load();
            } catch (Exception ex) {
                Net.warn("rates failed", ex);
                text = null;
            }
            final CharSequence shown = text;
            ui.post(() -> {
                if (shown == null) return;
                rates.setText(shown);
                ratesLoadedAt = System.currentTimeMillis();
            });
        });
    }

    private void loadWallpaper() {
        io.execute(() -> {
            Wallpaper.Result result;
            try {
                result = Wallpaper.load(getApplicationContext());
            } catch (Exception ex) {
                Net.warn("wallpaper failed", ex);
                result = null;
            }
            final Wallpaper.Result r = result;
            ui.post(() -> {
                if (r == null) return;
                boolean first = wallpaper.getDrawable() == null;
                wallpaper.setImageBitmap(r.bitmap);
                wallpaperCaption.setText(r.caption);
                if (first) wallpaper.animate().alpha(1f).setDuration(600).start();
                wallpaperLoadedAt = System.currentTimeMillis();
            });
        });
    }


    // ---------- VPN (Happ) ----------

    private void toggleVpn() {
        if (vpnTarget != null) return; // уже ждём ответа
        boolean enable = !vpn.isActive();
        if (!vpn.request(enable)) {
            Toast.makeText(this, R.string.vpn_no_response, Toast.LENGTH_LONG).show();
            return;
        }
        vpnTarget = enable;
        renderVpn(vpn.isActive());
        ui.removeCallbacks(vpnTimeout);
        ui.postDelayed(vpnTimeout, VPN_WAIT_MS);
    }

    private void renderVpn(boolean active) {
        if (vpnTarget != null && vpnTarget == active) {
            vpnTarget = null;
            ui.removeCallbacks(vpnTimeout);
        }
        int icon;
        int label;
        if (vpnTarget != null) {
            icon = R.drawable.ic_vpn_wait;
            label = R.string.vpn_wait;
        } else if (active) {
            icon = R.drawable.ic_vpn_on;
            label = R.string.vpn_on;
        } else {
            icon = R.drawable.ic_vpn_off;
            label = R.string.vpn_off;
        }
        vpnBtn.setImageResource(icon);
        vpnBtn.setContentDescription(getString(label));
        if (vpnBtn.isFocused()) topHint.setText(getString(label));
    }

    private boolean isTopButtonFocused() {
        View f = getCurrentFocus();
        if (f == null) return false;
        int id = f.getId();
        return id == R.id.btn_vpn || id == R.id.btn_wifi || id == R.id.btn_bluetooth || id == R.id.btn_settings;
    }

    // ---------- Обновление лаунчера ----------

    private void checkUpdate() {
        updateCheckedAt = System.currentTimeMillis();
        io.execute(() -> {
            Updater.Info info;
            try {
                info = Updater.check(getApplicationContext());
            } catch (Exception e) {
                Net.warn("update check failed", e);
                ui.post(() -> updateCheckedAt = System.currentTimeMillis() - UPDATE_TTL + 30 * MINUTE);
                return;
            }
            final Updater.Info found = info;
            ui.post(() -> {
                update = found;
                if (!updating) renderUpdate();
            });
        });
    }

    private void renderUpdate() {
        if (update == null) {
            updateBtn.setVisibility(View.GONE);
            return;
        }
        updateBtn.setVisibility(View.VISIBLE);
        updateBtn.setText(getString(R.string.update_to, update.versionName));
    }

    private void installUpdate() {
        if (update == null || updating) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                // На части TV-прошивок этого экрана нет — разрешение выдаётся через adb.
            }
            Toast.makeText(this, R.string.update_need_permission, Toast.LENGTH_LONG).show();
            return;
        }
        updating = true;
        updateBtn.setText(R.string.update_downloading);
        io.execute(() -> {
            try {
                java.io.File apk = Updater.download(getApplicationContext());
                ui.post(() -> updateBtn.setText(R.string.update_installing));
                Updater.install(getApplicationContext(), apk);
                // Если установку отменили, через минуту кнопка снова станет активной.
                ui.postDelayed(() -> {
                    updating = false;
                    renderUpdate();
                }, 60_000);
            } catch (Exception e) {
                Net.warn("update install failed", e);
                ui.post(() -> {
                    updating = false;
                    renderUpdate();
                    Toast.makeText(this, getString(R.string.update_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
