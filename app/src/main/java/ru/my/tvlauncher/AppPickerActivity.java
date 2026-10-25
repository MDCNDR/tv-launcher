package ru.my.tvlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import java.util.List;

/** Список всех приложений: ОК добавляет на главный экран или убирает с него. */
public class AppPickerActivity extends Activity {

    private static final String MARK = "✓  ";

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);
        prefs = new Prefs(this);

        GridLayout grid = findViewById(R.id.grid);
        List<Apps.Entry> apps = Apps.all(this);
        for (Apps.Entry e : apps) {
            View tile = Tiles.make(this, grid, e.image, e.isBanner, e.label);
            TextView label = tile.findViewById(R.id.label);
            render(tile, label, e);
            tile.setOnClickListener(v -> {
                prefs.toggleFavorite(e.pkg);
                render(tile, label, e);
            });
            grid.addView(tile);
        }
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private void render(View tile, TextView label, Apps.Entry e) {
        boolean fav = prefs.isFavorite(e.pkg);
        label.setText(fav ? MARK + e.label : e.label);
        tile.findViewById(R.id.image).setAlpha(fav ? 1f : 0.45f);
    }
}
