package ru.my.tvlauncher;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import org.json.JSONObject;

import java.util.Locale;

/** Официальные курсы ЦБ РФ (зеркало cbr-xml-daily.ru). Вызывать не из UI-потока. */
final class Rates {

    private static final String URL = "https://www.cbr-xml-daily.ru/daily_json.js";
    private static final String[][] CURRENCIES = {
            {"USD", "$"},
            {"EUR", "€"},
            {"CNY", "¥"},
    };
    private static final int UP = Color.parseColor("#7BD88F");
    private static final int DOWN = Color.parseColor("#FF7A7A");

    private Rates() {}

    /** Строка вида «$ 81,23 ▲  € 94,10 ▼  ¥ 11,32 ▲» с цветными стрелками. */
    static CharSequence load() throws Exception {
        JSONObject valute = new JSONObject(Net.getText(URL)).getJSONObject("Valute");
        Locale ru = new Locale("ru");
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String[] c : CURRENCIES) {
            JSONObject v = valute.optJSONObject(c[0]);
            if (v == null) continue;
            int nominal = Math.max(1, v.optInt("Nominal", 1));
            double now = v.getDouble("Value") / nominal;
            double prev = v.optDouble("Previous", now) / nominal;

            if (sb.length() > 0) sb.append("     ");
            sb.append(c[1]).append(' ').append(String.format(ru, "%.2f", now));
            if (Math.abs(now - prev) >= 0.005) {
                boolean up = now > prev;
                int start = sb.length();
                sb.append(up ? " ▲" : " ▼");
                sb.setSpan(new ForegroundColorSpan(up ? UP : DOWN), start, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if (sb.length() == 0) throw new IllegalStateException("no currencies in response");
        return sb;
    }
}
