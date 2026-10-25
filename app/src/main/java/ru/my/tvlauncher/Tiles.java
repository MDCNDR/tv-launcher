package ru.my.tvlauncher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/** Плитка приложения: баннер или иконка, подпись, увеличение при фокусе. */
final class Tiles {

    private Tiles() {}

    static View make(Context c, ViewGroup parent, Drawable image, boolean isBanner, CharSequence label) {
        View tile = LayoutInflater.from(c).inflate(R.layout.tile, parent, false);
        ImageView iv = tile.findViewById(R.id.image);
        TextView tv = tile.findViewById(R.id.label);

        ((View) iv.getParent()).setClipToOutline(true);
        iv.setImageDrawable(image);
        if (isBanner) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            int p = dp(c, 16);
            iv.setPadding(p, p, p, p);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        tv.setText(label);
        tile.setContentDescription(label);

        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.1f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? dp(c, 8) : 0).setDuration(140).start();
        });
        return tile;
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
