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
            float s = hasFocus ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? dp(c, 8) : 0).setDuration(140).start();
        });
        return tile;
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
