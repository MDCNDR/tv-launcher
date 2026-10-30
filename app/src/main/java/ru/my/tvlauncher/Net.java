package ru.my.tvlauncher;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Простые HTTP-запросы. Вызывать только из фонового потока. */
final class Net {

    static final String TAG = "TvLauncher";

    private Net() {}

    static String getText(String address) throws IOException {
        return new String(getBytes(address), StandardCharsets.UTF_8);
    }

    static String getText(String address, int connectMs, int readMs) throws IOException {
        return new String(getBytes(address, connectMs, readMs), StandardCharsets.UTF_8);
    }

    static byte[] getBytes(String address) throws IOException {
        return getBytes(address, 15_000, 20_000);
    }

    static byte[] getBytes(String address, int connectMs, int readMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(connectMs);
        conn.setReadTimeout(readMs);
        conn.setInstanceFollowRedirects(true);
        // OpenStreetMap просит понятный User-Agent с адресом проекта.
        conn.setRequestProperty("User-Agent", "TvLauncher/1.3 github.com/MDCNDR/tv-launcher");
        conn.setRequestProperty("Accept", "*/*");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + address);
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
                return buf.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    static void warn(String what, Throwable t) {
        Log.w(TAG, what + ": " + t, t);
    }
}
