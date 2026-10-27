package ru.my.tvlauncher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Прогноз на неделю. Открывается по нажатию на погоду на главном экране. */
public class ForecastActivity extends Activity {

    private static final Locale RU = new Locale("ru");

    private Prefs prefs;
    private TextView title;
    private TextView status;
    private TextView details;
    private LinearLayout days;
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forecast);
        prefs = new Prefs(this);
        title = findViewById(R.id.title);
        status = findViewById(R.id.status);
        details = findViewById(R.id.day_details);
        days = findViewById(R.id.days);
        findViewById(R.id.btn_city).setOnClickListener(v -> CityDialog.show(this, prefs, this::load));
        loadBackground();
        load();
    }

    private void load() {
        final int gen = ++generation;
        final String city = prefs.city();
        final double lat = prefs.lat();
        final double lon = prefs.lon();
        title.setText(getString(R.string.forecast_title, city));
        status.setText(R.string.weather_loading);
        status.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Weather.Day> list;
            try {
                list = Weather.forecast(lat, lon);
            } catch (Exception e) {
                list = null;
            }
            final List<Weather.Day> result = list;
            runOnUiThread(() -> {
                if (gen != generation || isDestroyed()) return;
                if (result == null || result.isEmpty()) {
                    status.setText(R.string.forecast_error);
                    return;
                }
                status.setVisibility(View.GONE);
                show(result);
            });
        }, "forecast").start();
    }

    private void show(List<Weather.Day> list) {
        days.removeAllViews();
        details.setText("");
        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM", RU);
        for (int i = 0; i < list.size(); i++) {
            Weather.Day d = list.get(i);
            View card = inflater.inflate(R.layout.forecast_day, days, false);
            if (i == list.size() - 1) {
                ((LinearLayout.LayoutParams) card.getLayoutParams()).setMarginEnd(0);
            }
            ((TextView) card.findViewById(R.id.day_name)).setText(dayName(d, "EEE"));
            ((TextView) card.findViewById(R.id.day_date)).setText(dateFmt.format(d.date).replace(".", ""));
            ((ImageView) card.findViewById(R.id.day_icon)).setImageResource(d.icon);
            ((TextView) card.findViewById(R.id.day_max)).setText(
                    Double.isNaN(d.max) ? "—" : Weather.degrees(d.max));
            ((TextView) card.findViewById(R.id.day_min)).setText(
                    Double.isNaN(d.min) ? "" : Weather.degrees(d.min));
            TextView pop = card.findViewById(R.id.day_pop);
            if (d.precipChance >= 10) {
                pop.setText("осадки " + d.precipChance + "%");
            } else {
                pop.setVisibility(View.INVISIBLE);
                pop.setText("—");
            }
            card.setOnFocusChangeListener((v, has) -> {
                float s = has ? 1.06f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(140).start();
                if (has) details.setText(describe(d));
            });
            days.addView(card);
        }
        if (days.getChildCount() > 0) days.getChildAt(0).requestFocus();
    }

    private String dayName(Weather.Day d) {
        return dayName(d, "EEEE");
    }

    /** «Сегодня», «Завтра», дальше день недели в формате pattern («Вт» или «Вторник»). */
    private String dayName(Weather.Day d, String pattern) {
        if (DateUtils.isToday(d.date.getTime())) return "Сегодня";
        if (DateUtils.isToday(d.date.getTime() - DateUtils.DAY_IN_MILLIS)) return "Завтра";
        String s = new SimpleDateFormat(pattern, RU).format(d.date).replace(".", "");
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(RU) + s.substring(1);
    }

    private String describe(Weather.Day d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d.date);
        StringBuilder sb = new StringBuilder();
        sb.append(dayName(d)).append(", ")
                .append(new SimpleDateFormat("d MMMM", RU).format(d.date)).append(" — ")
                .append(d.description.toLowerCase(RU));
        if (!Double.isNaN(d.max) && !Double.isNaN(d.min)) {
            sb.append("\nДнём ").append(Weather.degrees(d.max))
                    .append(", ночью ").append(Weather.degrees(d.min));
        }
        if (d.precipChance >= 0) sb.append(" · вероятность осадков ").append(d.precipChance).append('%');
        if (!Double.isNaN(d.wind)) sb.append(" · ветер до ").append(Math.round(d.wind)).append(" м/с");
        return sb.toString();
    }

    /** Фон — тот же статичный рисунок, что на главном экране. */
    private void loadBackground() {
        ((ImageView) findViewById(R.id.wallpaper)).setImageBitmap(Wallpaper.get(getResources()));
    }
}
