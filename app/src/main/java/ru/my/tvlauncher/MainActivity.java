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
    /** Чуть меньше тика, чтобы проверка шла на каждом тике (раз в 5 минут). */
    private static final long UPDATE_TTL = 4 * MINUTE;
    private static final long VPN_WAIT_MS = 12_000;
    /** Не чаще одной проверки обновлений за столько мс при нажатиях «Домой». */
    private static final long HOME_UPDATE_DEBOUNCE_MS = 15_000;

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

    private ImageView sleepBtn;
    private ImageView netBtn;
    private boolean pinging;
    private View content;

    /** Приложение, которое ждёт переключения VPN перед запуском. */
    private String pendingLaunch;
    private boolean pendingWant;
    private long pendingDeadline;
    private final Runnable pendingCheck = this::checkPendingLaunch;

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

        content = findViewById(R.id.content);
        sleepBtn = findViewById(R.id.btn_sleep);
        sleepBtn.setOnClickListener(v -> showSleepDialog());
        findViewById(R.id.btn_clean).setOnClickListener(v -> cleanMemory());
        netBtn = findViewById(R.id.btn_net);
        netBtn.setOnClickListener(v -> showSpeedTest());

        // Подпись под круглыми кнопками: показывает, что сейчас в фокусе.
        topHint = findViewById(R.id.top_hint);
        for (int id : TOP_BUTTONS) {
            findViewById(id).setOnFocusChangeListener((v, has) -> {
                if (has) {
                    if (v == sleepBtn) renderSleep();
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
        renderSleep();
        applyNightMode();
        checkPing();
        checkUpdateOnHome(); // вернулись на главный экран (в т.ч. кнопкой «Домой» из приложения)
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
        checkUpdateOnHome();
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
            TextView tile = Tiles.make(this, favRow, e.label);
            tile.setTag(pkg);
            showVpnMarker(tile, prefs.vpnRule(pkg));
            tile.setOnClickListener(v -> launch(pkg));
            tile.setOnLongClickListener(v -> {
                showTileMenu(pkg, e.label);
                return true;
            });
            favRow.addView(tile);
            if (pkg.equals(focusPkg)) toFocus = tile;
        }

        View add = Tiles.make(this, favRow, "+   " + getString(R.string.add_apps));
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
        boolean happ = Vpn.isHappInstalled(this);
        String[] items = {
                getString(R.string.move_left),
                getString(R.string.move_right),
                getString(R.string.remove),
                getString(R.string.app_info),
                getString(R.string.vpn_rule_menu, vpnRuleName(prefs.vpnRule(pkg))),
        };
        if (!happ) items = java.util.Arrays.copyOf(items, 4);
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
                        case 4:
                            showVpnRuleDialog(pkg, label);
                            return;
                    }
                    buildFavorites();
                })
                .show();
    }

    /** Запуск с плитки: сначала переключаем VPN по правилу приложения, если нужно. */
    private void launch(String pkg) {
        int rule = prefs.vpnRule(pkg);
        if (rule != Prefs.VPN_ANY && Vpn.isHappInstalled(this)) {
            boolean want = rule == Prefs.VPN_ON;
            if (vpn.isActive() != want && vpn.request(want)) {
                Toast.makeText(this, want ? R.string.vpn_switching_on : R.string.vpn_switching_off,
                        Toast.LENGTH_SHORT).show();
                pendingLaunch = pkg;
                pendingWant = want;
                pendingDeadline = System.currentTimeMillis() + 8_000;
                ui.removeCallbacks(pendingCheck);
                ui.postDelayed(pendingCheck, 300);
                return;
            }
        }
        startApp(pkg);
    }

    /** Ждём, пока VPN переключится и лаунчер снова окажется на экране, затем запускаем. */
    private void checkPendingLaunch() {
        if (pendingLaunch == null) return;
        long now = System.currentTimeMillis();
        boolean switched = vpn.isActive() == pendingWant;
        boolean timedOut = now > pendingDeadline;
        if (resumed && (switched || timedOut)) {
            String pkg = pendingLaunch;
            pendingLaunch = null;
            if (!switched) Toast.makeText(this, R.string.vpn_switch_failed, Toast.LENGTH_SHORT).show();
            startApp(pkg);
            return;
        }
        if (now > pendingDeadline + 15_000) { // так и не вернулись на экран — отменяем
            pendingLaunch = null;
            return;
        }
        ui.postDelayed(pendingCheck, 300);
    }

    private void showVpnRuleDialog(String pkg, CharSequence label) {
        String[] names = {
                getString(R.string.vpn_rule_any),
                getString(R.string.vpn_rule_on),
                getString(R.string.vpn_rule_off),
        };
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(getString(R.string.vpn_rule_title) + " · " + label)
                .setSingleChoiceItems(names, prefs.vpnRule(pkg), (d, which) -> {
                    prefs.setVpnRule(pkg, which);
                    focusPkg = pkg;
                    d.dismiss();
                    buildFavorites();
                })
                .show();
    }

    private String vpnRuleName(int rule) {
        switch (rule) {
            case Prefs.VPN_ON: return getString(R.string.vpn_rule_on).toLowerCase(java.util.Locale.ROOT);
            case Prefs.VPN_OFF: return getString(R.string.vpn_rule_off).toLowerCase(java.util.Locale.ROOT);
            default: return getString(R.string.vpn_rule_any).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Маленький щит справа от названия: зелёный — с VPN, серый — без VPN. */
    private void showVpnMarker(TextView tile, int rule) {
        int res = rule == Prefs.VPN_ON ? R.drawable.ic_vpn_on
                : rule == Prefs.VPN_OFF ? R.drawable.ic_vpn_off : 0;
        if (res == 0) {
            tile.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        android.graphics.drawable.Drawable d = getDrawable(res);
        int size = Tiles.dp(this, 16);
        d.setBounds(0, 0, size, size);
        tile.setCompoundDrawablePadding(Tiles.dp(this, 10));
        tile.setCompoundDrawablesRelative(null, null, d, null);
    }

    private void startApp(String pkg) {
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
        checkPing();
        renderSleep();
        applyNightMode();
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
        prefs.saveWeather(Weather.degrees(w.temp), w.description + " · " + city,
                getResources().getResourceEntryName(w.icon));

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
                if (first) wallpaper.animate().alpha(isNight() ? 0.3f : 1f).setDuration(600).start();
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

    private static final int[] TOP_BUTTONS = {
            R.id.btn_vpn, R.id.btn_net, R.id.btn_sleep, R.id.btn_clean,
            R.id.btn_wifi, R.id.btn_bluetooth, R.id.btn_settings,
    };

    private boolean isTopButtonFocused() {
        View f = getCurrentFocus();
        if (f == null) return false;
        for (int id : TOP_BUTTONS) if (f.getId() == id) return true;
        return false;
    }

    // ---------- Обновление лаунчера ----------

    /** «Домой» проверяет обновления сразу, без ожидания 5-минутного тика. */
    private void checkUpdateOnHome() {
        if (updating) return;
        if (System.currentTimeMillis() - updateCheckedAt > HOME_UPDATE_DEBOUNCE_MS) checkUpdate();
    }

    private void checkUpdate() {
        updateCheckedAt = System.currentTimeMillis();
        io.execute(() -> {
            Updater.Info info;
            try {
                info = Updater.check(getApplicationContext());
            } catch (Exception e) {
                Net.warn("update check failed", e);
                return; // повторим на следующем тике
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

    // ---------- Таймер сна ----------

    private static final int[] SLEEP_MINUTES = {15, 30, 45, 60, 90, 120};

    private void showSleepDialog() {
        boolean active = prefs.sleepAt() > 0;
        String[] items = new String[SLEEP_MINUTES.length + (active ? 1 : 0)];
        int k = 0;
        if (active) items[k++] = getString(R.string.sleep_off);
        for (int m : SLEEP_MINUTES) items[k++] = m < 60 ? m + " мин" : (m % 60 == 0 ? m / 60 + " ч" : m / 60 + " ч " + m % 60 + " мин");
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.sleep_title)
                .setItems(items, (d, which) -> {
                    if (active && which == 0) {
                        SleepReceiver.schedule(this, 0);
                        Toast.makeText(this, R.string.sleep_cancelled, Toast.LENGTH_SHORT).show();
                        renderSleep();
                        return;
                    }
                    int minutes = SLEEP_MINUTES[which - (active ? 1 : 0)];
                    // Сразу проверяем root, чтобы запрос Magisk появился сейчас, а не ночью.
                    Toast.makeText(this, R.string.priv_checking, Toast.LENGTH_LONG).show();
                    new Thread(() -> {
                        Privileged.Result access = Privileged.run(this, "true");
                        ui.post(() -> {
                            if (access != Privileged.Result.OK) {
                                Toast.makeText(this, Privileged.message(access), Toast.LENGTH_LONG).show();
                                return;
                            }
                            SleepReceiver.schedule(this, minutes);
                            Toast.makeText(this, getString(R.string.sleep_set, minutes), Toast.LENGTH_SHORT).show();
                            renderSleep();
                        });
                    }, "sleep-check").start();
                })
                .show();
    }

    private void renderSleep() {
        long at = prefs.sleepAt();
        if (at > 0) {
            int left = (int) Math.max(1, Math.round((at - System.currentTimeMillis()) / 60_000.0));
            sleepBtn.setImageResource(R.drawable.ic_sleep_on);
            sleepBtn.setImageTintList(null);
            sleepBtn.setContentDescription(getString(R.string.sleep_in, left));
        } else {
            sleepBtn.setImageResource(R.drawable.ic_sleep);
            sleepBtn.setImageTintList(getColorStateList(R.color.chip_text));
            sleepBtn.setContentDescription(getString(R.string.sleep));
        }
        if (sleepBtn.isFocused()) topHint.setText(sleepBtn.getContentDescription());
    }

    // ---------- Ночной режим ----------

    /** С 23:00 до 7:00 фото и текст приглушены. */
    private boolean isNight() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 23 || h < 7;
    }

    private void applyNightMode() {
        boolean night = isNight();
        content.setAlpha(night ? 0.6f : 1f);
        if (wallpaper.getDrawable() != null) wallpaper.setAlpha(night ? 0.3f : 1f);
    }

    // ---------- Очистка памяти ----------

    private boolean cleaning;

    private void cleanMemory() {
        if (cleaning) return;
        cleaning = true;
        Toast.makeText(this, R.string.cleaning, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            long[] r;
            try {
                r = Cleaner.clean(this);
            } catch (InterruptedException e) {
                r = new long[]{-1, Privileged.Result.FAILED.ordinal()};
            }
            final long[] res = r;
            ui.post(() -> {
                cleaning = false;
                if (res[0] < 0) {
                    Privileged.Result why = Privileged.Result.values()[(int) res[1]];
                    Toast.makeText(this, Privileged.message(why), Toast.LENGTH_LONG).show();
                    return;
                }
                long freed = res[1] - res[0];
                String msg = freed >= 20L * 1024 * 1024
                        ? getString(R.string.cleaned, mb(freed), mb(res[1]))
                        : getString(R.string.cleaned_nothing, mb(res[1]));
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        }, "clean").start();
    }

    private static String mb(long bytes) {
        if (bytes < 0) return "?";
        long mb = bytes / (1024 * 1024);
        return mb >= 1024 ? String.format(java.util.Locale.ROOT, "%.1f ГБ", mb / 1024.0).replace('.', ',') : mb + " МБ";
    }

    // ---------- Индикатор интернета ----------

    private static final int GOOD_MS = 80;
    private static final int OK_MS = 200;

    /** Отдельный поток: пинг не должен ждать погоду и обновления в очереди io. */
    private void checkPing() {
        if (pinging) return;
        pinging = true;
        new Thread(() -> {
            int ms = NetCheck.ping();
            ui.post(() -> {
                pinging = false;
                renderNet(ms);
            });
        }, "ping").start();
    }

    private void renderNet(int ms) {
        int color;
        String text;
        boolean viaVpn = vpn != null && vpn.isActive();
        if (ms < 0) {
            color = 0xFFFF6B6B;
            text = getString(R.string.net_down);
        } else {
            color = ms <= GOOD_MS ? 0xFF4CC26B : ms <= OK_MS ? 0xFFFFC857 : 0xFFFF6B6B;
            text = getString(viaVpn ? R.string.net_ping_vpn : R.string.net_ping, ms);
        }
        netBtn.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        netBtn.setContentDescription(text);
        if (netBtn.isFocused()) topHint.setText(text);
    }

    private void showSpeedTest() {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.speed_title)
                .setMessage(getString(R.string.speed_running, ""))
                .setPositiveButton(R.string.speed_again, null)
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.setOnShowListener(d -> {
            View again = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            again.setOnClickListener(v -> runSpeedTest(dialog));
            runSpeedTest(dialog);
        });
        dialog.show();
    }

    private boolean speedRunning;

    private void runSpeedTest(AlertDialog dialog) {
        if (speedRunning) return;
        speedRunning = true;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.setMessage(getString(R.string.speed_running, ""));
        boolean viaVpn = vpn.isActive();
        new Thread(() -> {
            int ping = NetCheck.ping();
            double mbps = NetCheck.download(p -> ui.post(() -> {
                if (dialog.isShowing()) dialog.setMessage(getString(R.string.speed_running, speed(p)));
            }));
            ui.post(() -> {
                speedRunning = false;
                renderNet(ping);
                if (!dialog.isShowing()) return;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                if (mbps < 0) {
                    dialog.setMessage(getString(R.string.speed_failed));
                    return;
                }
                dialog.setMessage(getString(R.string.speed_result, speed(mbps),
                        ping < 0 ? "—" : ping + " мс",
                        viaVpn ? getString(R.string.speed_via_vpn) : ""));
            });
        }, "speedtest").start();
    }

    private static String speed(double mbps) {
        if (mbps <= 0) return "";
        return (mbps >= 100 ? String.valueOf(Math.round(mbps))
                : String.format(java.util.Locale.ROOT, "%.1f", mbps).replace('.', ',')) + " Мбит/с";
    }
}
