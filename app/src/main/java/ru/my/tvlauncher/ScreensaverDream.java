package ru.my.tvlauncher;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/**
 * Заставка: статичный фон в стиле macOS, по центру дата, часы и погода.
 * Раз в минуту блок смещается на несколько точек (защита от выгорания),
 * ночью (23–7) всё приглушено. Никаких загрузок из сети.
 */
public class ScreensaverDream extends DreamService {

    private static final long TICK_MS = 60_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            applyNight();
            showWeather();
            nudge();
            ui.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(R.layout.dream);
        ((ImageView) findViewById(R.id.dream_bg)).setImageBitmap(Wallpaper.get(getResources()));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        ui.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        ui.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private void showWeather() {
        String[] w = new Prefs(this).lastWeather();
        View box = findViewById(R.id.dream_weather);
        if (w == null) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.dream_weather_text)).setText(w[0] + "  ·  " + w[1]);
        int icon = getResources().getIdentifier(w[2], "drawable", getPackageName());
        ImageView iv = findViewById(R.id.dream_weather_icon);
        iv.setVisibility(icon != 0 ? View.VISIBLE : View.GONE);
        if (icon != 0) iv.setImageResource(icon);
    }

    private void nudge() {
        float d = getResources().getDisplayMetrics().density;
        float x = (random.nextFloat() - 0.5f) * 40 * d;
        float y = (random.nextFloat() - 0.5f) * 24 * d;
        findViewById(R.id.dream_center).animate().translationX(x).translationY(y).setDuration(2000).start();
    }

    private void applyNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 23 || h < 7;
        findViewById(R.id.dream_center).setAlpha(night ? 0.55f : 1f);
        findViewById(R.id.dream_bg).setAlpha(night ? 0.35f : 1f);
    }
}
