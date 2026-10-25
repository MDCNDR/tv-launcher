package ru.my.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** Диалог «Город для погоды»: ищет город и сохраняет его координаты. */
final class CityDialog {

    private CityDialog() {}

    static void show(Activity a, Prefs prefs, Runnable onChanged) {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(prefs.city());
        input.setSelectAllOnFocus(true);
        int pad = Tiles.dp(a, 20);

        LinearLayout box = new LinearLayout(a);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(R.string.city_title)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> find(a, prefs, input.getText().toString(), onChanged))
                .setNegativeButton(R.string.cancel, null)
                .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.dismiss();
                find(a, prefs, input.getText().toString(), onChanged);
                return true;
            }
            return false;
        });
        dialog.show();
    }

    private static void find(Activity a, Prefs prefs, String query, Runnable onChanged) {
        if (query.trim().isEmpty()) return;
        new Thread(() -> {
            Weather.Place place = null;
            try {
                place = Weather.geocode(query);
            } catch (Exception e) {
                Net.warn("geocode failed", e);
            }
            final Weather.Place found = place;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (found == null) {
                    Toast.makeText(a, R.string.city_not_found, Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.setCity(found.name, found.lat, found.lon);
                onChanged.run();
            });
        }, "geocode").start();
    }
}
