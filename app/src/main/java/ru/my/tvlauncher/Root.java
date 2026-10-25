package ru.my.tvlauncher;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Команды через su — только если root на устройстве действительно установлен. Не из UI-потока. */
final class Root {

    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/su/bin/su",
    };

    private Root() {}

    /** Есть ли su вообще (без запуска, чтобы не вызывать лишних запросов). */
    static boolean present() {
        for (String p : SU_PATHS) if (new File(p).exists()) return true;
        return false;
    }

    static boolean run(String command, long timeoutMs) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                Net.warn("su timeout: " + command, new IllegalStateException("timeout"));
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            Net.warn("su failed: " + command, e);
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
