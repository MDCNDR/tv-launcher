package ru.my.tvlauncher;

import java.util.concurrent.TimeUnit;

/** Команды через su (Magisk). Первый вызов показывает на экране запрос Magisk. Не из UI-потока. */
final class Root {

    private Root() {}

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

    /** Проверяет доступ (и при первом вызове вызывает запрос Magisk — ждём ответа до 30 с). */
    static boolean available() {
        return run("id", 30_000);
    }
}
