package ru.my.tvlauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

/** Плитка приложения: название в «таблетке», увеличение при фокусе. */
final class Tiles {

    private Tiles() {}

    static TextView make(Context c, ViewGroup parent, CharSequence label) {
        TextView tile = (TextView) LayoutInflater.from(c).inflate(R.layout.tile, parent, false);
        tile.setText(label);
        tile.setContentDescription(label);
        tile.setOnFocusChangeListener((v, hasFocus) -> {
            // Как фокус на Apple TV: плитка приподнимается и чуть увеличивается.
            float s = hasFocus ? 1.1f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? dp(c, 12) : 0)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(180).start();
        });
        return tile;
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
