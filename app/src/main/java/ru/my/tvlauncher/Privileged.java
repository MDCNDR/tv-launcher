package ru.my.tvlauncher;

import android.content.Context;

import java.io.File;
import java.net.ConnectException;

import dadb.AdbKeyPair;
import dadb.AdbShellResponse;
import dadb.Dadb;

/**
 * Команды с правами shell или root — для очистки памяти и таймера сна.
 * 1) root (su), если он действительно есть на устройстве;
 * 2) иначе — подключение к отладке по сети на самой приставке (127.0.0.1:5555),
 *    как это делает adb с компьютера. При первом подключении на экране появится
 *    системный запрос «Разрешить отладку?» — его нужно подтвердить один раз.
 * Вызывать не из UI-потока.
 */
final class Privileged {

    enum Result {
        OK,
        /** Отладка по сети выключена — подключиться не к чему. */
        ADB_OFF,
        /** Запрос «Разрешить отладку?» отклонён или не подтверждён вовремя. */
        NOT_ALLOWED,
        /** Команда выполнилась с ошибкой. */
        FAILED,
    }

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;
    /** Сколько ждём, пока человек подтвердит запрос на экране. */
    private static final int AUTH_WAIT_MS = 60_000;

    private Privileged() {}

    static synchronized Result run(Context c, String command) {
        if (Root.present()) {
            if (Root.run(command, 30_000)) return Result.OK;
            // root есть, но не дали — пробуем через отладку
        }
        return viaAdb(c.getApplicationContext(), command);
    }

    private static Result viaAdb(Context c, String command) {
        AdbKeyPair keys;
        try {
            keys = keys(c);
        } catch (Exception e) {
            Net.warn("adb keys", e);
            return Result.FAILED;
        }
        try (Dadb adb = Dadb.Companion.create(HOST, PORT, keys, 3_000, AUTH_WAIT_MS)) {
            AdbShellResponse r = adb.shell(command);
            if (r.getExitCode() != 0) {
                Net.warn("adb shell exit " + r.getExitCode() + ": " + command,
                        new IllegalStateException(r.getAllOutput()));
                return Result.FAILED;
            }
            return Result.OK;
        } catch (Exception e) {
            // Kotlin-библиотека не объявляет исключения, поэтому разбираем причину сами.
            Net.warn("adb failed", e);
            if (has(e, ConnectException.class)) return Result.ADB_OFF;
            if (has(e, java.net.SocketTimeoutException.class)) return Result.NOT_ALLOWED;
            // Обрыв во время авторизации обычно значит «Отклонить».
            return has(e, java.io.IOException.class) ? Result.NOT_ALLOWED : Result.FAILED;
        }
    }

    private static boolean has(Throwable t, Class<? extends Throwable> type) {
        for (Throwable x = t; x != null; x = x.getCause()) if (type.isInstance(x)) return true;
        return false;
    }

    /** Ключ лаунчера для отладки: создаётся один раз и хранится в files/adb. */
    private static AdbKeyPair keys(Context c) {
        File dir = new File(c.getFilesDir(), "adb");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File priv = new File(dir, "adbkey");
        File pub = new File(dir, "adbkey.pub");
        if (!priv.exists() || !pub.exists()) AdbKeyPair.Companion.generate(priv, pub);
        return AdbKeyPair.Companion.read(priv, pub);
    }

    /** Текст для пользователя по результату. */
    static int message(Result r) {
        switch (r) {
            case ADB_OFF: return R.string.priv_adb_off;
            case NOT_ALLOWED: return R.string.priv_not_allowed;
            default: return R.string.priv_failed;
        }
    }
}
