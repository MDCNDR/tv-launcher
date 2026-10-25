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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final long WEATHER_REFRESH_MS = 30 * 60 * 1000L;
    private static final long WEATHER_RETRY_MS = 2 * 60 * 1000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable weatherTick = this::loadWeather;

    private Prefs prefs;
    private LinearLayout favRow;
    private TextView weather;
    /** Пакет плитки, на которую вернуть фокус после перестройки строки. */
    private String focusPkg;
    private boolean resumed;
    private long weatherLoadedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        favRow = findViewById(R.id.fav_row);
        weather = findViewById(R.id.weather);

        findViewById(R.id.btn_wifi).setOnClickListener(v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        findViewById(R.id.btn_bluetooth).setOnClickListener(v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));
        weather.setOnClickListener(v -> askCity());
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        buildFavorites();
        ui.removeCallbacks(weatherTick);
        long age = System.currentTimeMillis() - weatherLoadedAt;
        ui.postDelayed(weatherTick, Math.max(0, WEATHER_REFRESH_MS - age));
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(weatherTick);
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

    // ---------- Погода ----------

    private void loadWeather() {
        final String city = prefs.city();
        final double lat = prefs.lat();
        final double lon = prefs.lon();
        io.execute(() -> {
            String text;
            boolean ok;
            try {
                text = Weather.current(lat, lon) + " · " + city;
                ok = true;
            } catch (Exception ex) {
                text = getString(R.string.weather_error);
                ok = false;
            }
            final String shown = text;
            final boolean success = ok;
            final long next = ok ? WEATHER_REFRESH_MS : WEATHER_RETRY_MS;
            ui.post(() -> {
                weather.setText(shown);
                weatherLoadedAt = success ? System.currentTimeMillis() : 0;
                ui.removeCallbacks(weatherTick);
                if (resumed) ui.postDelayed(weatherTick, next);
            });
        });
    }

    private void askCity() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(prefs.city());
        input.setSelectAllOnFocus(true);
        int pad = Tiles.dp(this, 20);

        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.city_title)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> findCity(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.dismiss();
                findCity(input.getText().toString());
                return true;
            }
            return false;
        });
        dialog.show();
    }

    private void findCity(String query) {
        if (query.trim().isEmpty()) return;
        weather.setText(R.string.weather_loading);
        io.execute(() -> {
            Weather.Place place = null;
            try {
                place = Weather.geocode(query);
            } catch (Exception ignored) {
            }
            final Weather.Place found = place;
            ui.post(() -> {
                if (found == null) {
                    Toast.makeText(this, R.string.city_not_found, Toast.LENGTH_SHORT).show();
                } else {
                    prefs.setCity(found.name, found.lat, found.lon);
                }
                ui.removeCallbacks(weatherTick);
                loadWeather();
            });
        });
    }
}
