package ru.my.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Заставка в стиле лаунчера: спутниковые снимки Google Earth View сменяются каждые 40 секунд
 * с плавным переходом и медленным приближением; часы, дата и последняя погода.
 * Ночью (23–7) всё приглушено. Часы понемногу смещаются, чтобы не выжигать экран.
 */
public class ScreensaverDream extends DreamService {

    private static final long SLIDE_MS = 40_000;
    private static final long FADE_MS = 2_500;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable nextSlide = this::showNext;

    private ImageView imgA;
    private ImageView imgB;
    private boolean showingA;
    private List<File> photos;
    private int index;
    private volatile boolean running;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(R.layout.dream);
        imgA = findViewById(R.id.img_a);
        imgB = findViewById(R.id.img_b);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        running = true;
        applyNight();
        showWeather();
        photos = Gallery.cached(this);
        index = photos.isEmpty() ? 0 : random.nextInt(photos.size());
        ui.post(nextSlide);
        // Докачиваем свежие фото в фоне; новые попадут в показ со следующего круга.
        new Thread(() -> {
            if (Gallery.refresh(getApplicationContext()) && running) {
                List<File> fresh = Gallery.cached(getApplicationContext());
                ui.post(() -> photos = fresh);
            }
        }, "gallery").start();
    }

    @Override
    public void onDreamingStopped() {
        running = false;
        ui.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private void showNext() {
        if (!running) return;
        applyNight();
        showWeather();
        nudgeClock();
        if (photos == null || photos.isEmpty()) {
            ui.postDelayed(nextSlide, SLIDE_MS);
            return;
        }
        File file = photos.get(index % photos.size());
        index++;
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath(), o);
            String caption = Gallery.caption(getApplicationContext(), file);
            ui.post(() -> {
                if (running && b != null) {
                    crossfadeTo(b);
                    TextView cap = findViewById(R.id.dream_caption);
                    cap.animate().alpha(0f).setDuration(FADE_MS / 2).withEndAction(() -> {
                        cap.setText(caption);
                        cap.animate().alpha(1f).setDuration(FADE_MS / 2).start();
                    }).start();
                }
                if (running) ui.postDelayed(nextSlide, SLIDE_MS);
            });
        }, "dream-decode").start();
    }

    private void crossfadeTo(Bitmap bitmap) {
        ImageView in = showingA ? imgB : imgA;
        ImageView out = showingA ? imgA : imgB;
        showingA = !showingA;

        in.setImageBitmap(bitmap);
        in.setAlpha(0f);
        // Медленное приближение и сдвиг в случайную сторону.
        float dx = (random.nextFloat() - 0.5f) * 60f * getResources().getDisplayMetrics().density;
        float dy = (random.nextFloat() - 0.5f) * 30f * getResources().getDisplayMetrics().density;
        in.setScaleX(1.0f);
        in.setScaleY(1.0f);
        in.setTranslationX(0);
        in.setTranslationY(0);
        in.animate().alpha(1f).setDuration(FADE_MS).start();
        in.animate().scaleX(1.12f).scaleY(1.12f).translationX(dx).translationY(dy)
                .setDuration(SLIDE_MS + FADE_MS).setInterpolator(null).start();
        out.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            out.setImageDrawable(null); // отпускаем старую картинку
        }).start();
    }

    private void showWeather() {
        String[] w = new Prefs(this).lastWeather();
        View box = findViewById(R.id.dream_weather);
        if (w == null) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.dream_weather_temp)).setText(w[0]);
        ((TextView) findViewById(R.id.dream_weather_desc)).setText(w[1]);
        int icon = getResources().getIdentifier(w[2], "drawable", getPackageName());
        ImageView iv = findViewById(R.id.dream_weather_icon);
        if (icon != 0) {
            iv.setImageResource(icon);
            iv.setVisibility(View.VISIBLE);
        } else {
            iv.setVisibility(View.GONE);
        }
    }

    /** Сдвигаем часы и погоду на несколько dp — защита от выгорания экрана. */
    private void nudgeClock() {
        float d = getResources().getDisplayMetrics().density;
        float x = (random.nextFloat() - 0.5f) * 24 * d;
        float y = (random.nextFloat() - 0.5f) * 16 * d;
        findViewById(R.id.dream_clock_box).animate().translationX(x).translationY(y).setDuration(FADE_MS).start();
        findViewById(R.id.dream_weather).animate().translationX(-x).translationY(y).setDuration(FADE_MS).start();
    }

    private void applyNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 23 || h < 7;
        findViewById(R.id.content).setAlpha(night ? 0.55f : 1f);
        float photo = night ? 0.35f : 1f;
        imgA.setImageAlpha(Math.round(photo * 255));
        imgB.setImageAlpha(Math.round(photo * 255));
    }
}
