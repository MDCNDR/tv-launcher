# Ускорение Xiaomi TV Stick (Google TV). Запуск из папки с adb:
#   powershell -ExecutionPolicy Bypass -File .\optimize.ps1
# Всё обратимо: отключённое включается командой  .\adb shell pm enable <пакет>

$adb = Join-Path $PSScriptRoot "adb.exe"
$ip  = "192.168.1.118:5555"

Write-Host "Подключаюсь к приставке $ip..." -ForegroundColor Cyan
& $adb connect $ip | Out-Host

# 1. Анимации в 2 раза быстрее (0 — совсем без анимаций)
Write-Host "`n[1/5] Анимации x0.5" -ForegroundColor Cyan
& $adb shell settings put global window_animation_scale 0.5
& $adb shell settings put global transition_animation_scale 0.5
& $adb shell settings put global animator_duration_scale 0.5

# 2. «Заморозка» фоновых приложений: свернутые приложения не тратят процессор
Write-Host "[2/5] Заморозка фоновых приложений" -ForegroundColor Cyan
& $adb shell settings put global cached_apps_freezer enabled

# 3. Отключаем лишние сервисы Xiaomi и Google
Write-Host "[3/5] Отключаю лишние сервисы" -ForegroundColor Cyan
$pkgs = @(
  # --- Google TV ---
  "com.google.android.apps.tv.launcherx",          # стандартный лаунчер Google TV (у нас свой) — много памяти
  "com.google.android.apps.tv.dreamx",             # заставка Google с рекомендациями
  "com.google.android.feedback",                   # отзывы в Google
  "com.android.tv.feedbackconsent",
  "com.google.android.tts",                        # синтез речи (нужен только диктору)
  "com.google.android.marvin.talkback",            # экранный диктор
  "com.google.android.syncadapters.calendar",      # синхронизация календаря
  "com.android.printspooler",                      # печать
  "com.android.adservices.api",                    # рекламные сервисы Android
  "com.android.ondevicepersonalization.services",  # персонализация рекламы
  "com.android.federatedcompute.services",         # сбор данных для обучения моделей
  "com.netflix.tokenmanager",                      # служебный пакет Netflix
  # --- Xiaomi ---
  "com.xiaomi.statistic",                          # статистика Xiaomi
  "com.miui.tv.analytics",                         # аналитика
  "com.xm.webcontent",                             # рекламный веб-контент
  "com.mitv.tvhome.oemtab",                        # вкладка Xiaomi в Google TV
  "com.xiaomi.mimusic2",                           # Mi Music
  "com.xiaomi.mitv.mediaexplorer",                 # файловый плеер Xiaomi
  "com.mitv.videoplayer",                          # видеоплеер Xiaomi
  "com.xiaomi.wfdsinkhelperservice",               # Miracast (трансляция экрана с телефона)
  "com.xiaomo.tv.milegal"                          # юридические тексты Xiaomi
)
foreach ($p in $pkgs) {
  $r = & $adb shell pm disable-user --user 0 $p 2>&1
  if ("$r" -match "disabled") { Write-Host "  выключен: $p" -ForegroundColor Green }
  else { Write-Host "  пропущен: $p ($r)" -ForegroundColor DarkGray }
}

# 4. Чистим кэши приложений во внутренней памяти
Write-Host "[4/5] Чищу кэши приложений" -ForegroundColor Cyan
& $adb shell pm trim-caches 999G
& $adb shell am kill-all

# 5. Оптимизация приложений (компиляция) — быстрее запуск. Может занять 5–15 минут.
Write-Host "[5/5] Оптимизирую приложения (5-15 минут, не выключайте приставку)..." -ForegroundColor Cyan
& $adb shell cmd package bg-dexopt-job | Out-Null

Write-Host "`nГотово. Свободно во внутренней памяти:" -ForegroundColor Green
& $adb shell df -h /data | Out-Host
Write-Host "Остался один шаг вручную: Настройки > Система > Для разработчиков >"
Write-Host "«Лимит фоновых процессов» > «Не более 2 процессов» (сбрасывается после перезагрузки)."
