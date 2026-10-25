package ru.my.tvlauncher;

import java.io.BufferedReader;
import java.io.FileReader;

/** Очистка памяти: закрывает фоновые процессы (am kill-all с правами shell/root). Не из UI-потока. */
final class Cleaner {

    private Cleaner() {}

    /** Сколько памяти доступно (байт), по /proc/meminfo. −1, если не удалось прочитать. */
    static long available() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    String kb = line.replaceAll("[^0-9]", "");
                    return Long.parseLong(kb) * 1024;
                }
            }
        } catch (Exception e) {
            Net.warn("meminfo", e);
        }
        return -1;
    }

    /**
     * Закрывает всё, что висит в фоне. Приложения на переднем плане и активные
     * сервисы (например, VPN Happ) не трогаются — их am kill-all не закрывает.
     * Возвращает {доступно до, доступно после}; при ошибке — {-1, причина (ordinal)}.
     */
    static long[] clean(android.content.Context c) throws InterruptedException {
        long before = available();
        Privileged.Result r = Privileged.run(c, "am kill-all");
        if (r != Privileged.Result.OK) return new long[]{-1, r.ordinal()};
        Thread.sleep(1500); // системе нужно время, чтобы забрать память
        return new long[]{before, available()};
    }
}
