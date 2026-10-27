# ADB-клиент (dadb) и его зависимости: сохраняем как есть, чтобы не сломать протокол.
-keep class dadb.** { *; }
-keep class okio.** { *; }
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn kotlin.**

# Иконки погоды ищутся по имени (заставка) — не переименовывать ресурсы.
-keepclassmembers class **.R$drawable { public static <fields>; }
