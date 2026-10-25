package ru.my.tvlauncher;

import android.app.Activity;
import android.os.Bundle;
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
            TextView tile = Tiles.make(this, grid, e.label);
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) tile.getLayoutParams();
            lp.width = Tiles.dp(this, 150);
            lp.setMargins(Tiles.dp(this, 6), Tiles.dp(this, 6), Tiles.dp(this, 6), Tiles.dp(this, 6));
            tile.setBackgroundResource(R.drawable.chip_bg);
            render(tile, e);
            tile.setOnClickListener(v -> {
                prefs.toggleFavorite(e.pkg);
                render(tile, e);
            });
            grid.addView(tile);
        }
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private void render(TextView tile, Apps.Entry e) {
        boolean fav = prefs.isFavorite(e.pkg);
        tile.setText(fav ? MARK + e.label : e.label);
        tile.setAlpha(fav ? 1f : 0.6f);
    }
}
