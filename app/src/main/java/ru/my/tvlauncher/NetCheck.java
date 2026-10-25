package ru.my.tvlauncher;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;

/** Пинг (время TCP-соединения) и замер скорости загрузки. Не из UI-потока. */
final class NetCheck {

    private static final String[] PING_HOSTS = {"ya.ru", "google.com", "cloudflare.com"};
    private static final String[] SPEED_URLS = {
            "https://speed.cloudflare.com/__down?bytes=100000000",
            "https://speedtest.selectel.ru/100MB",
    };

    interface Progress {
        void onProgress(double mbps);
    }

    private NetCheck() {}

    /** Медиана трёх соединений, мс; −1 — интернета нет. */
    static int ping() {
        for (String host : PING_HOSTS) {
            try {
                InetAddress addr = InetAddress.getByName(host);
                long[] t = new long[3];
                for (int i = 0; i < t.length; i++) {
                    long start = System.nanoTime();
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(addr, 443), 3000);
                    }
                    t[i] = (System.nanoTime() - start) / 1_000_000;
                }
                Arrays.sort(t);
                return (int) Math.max(1, t[1]);
            } catch (Exception e) {
                Net.warn("ping " + host, e);
            }
        }
        return -1;
    }

    /** Скорость загрузки, Мбит/с; ~10 секунд. −1 — ни один сервер не ответил. */
    static double download(Progress progress) {
        for (String url : SPEED_URLS) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) TvLauncher");
                if (conn.getResponseCode() != 200) throw new java.io.IOException("HTTP " + conn.getResponseCode());
                byte[] buf = new byte[65536];
                long bytes = 0;
                long start = System.nanoTime();
                long lastReport = start;
                try (InputStream in = conn.getInputStream()) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        bytes += n;
                        long now = System.nanoTime();
                        if (now - lastReport > 500_000_000L) {
                            progress.onProgress(mbps(bytes, now - start));
                            lastReport = now;
                        }
                        if (now - start > 10_000_000_000L) break;
                    }
                }
                long elapsed = System.nanoTime() - start;
                if (bytes < 256 * 1024) throw new java.io.IOException("too little data: " + bytes);
                return mbps(bytes, elapsed);
            } catch (Exception e) {
                Net.warn("speed " + url, e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return -1;
    }

    private static double mbps(long bytes, long nanos) {
        return bytes * 8.0 / 1_000_000.0 / (nanos / 1e9);
    }
}
