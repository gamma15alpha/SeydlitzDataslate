# Android-клиент

Kotlin + Jetpack Compose (Material 3).

## Сборка

Нужен **JDK 21** — на более новых версиях Java Gradle и Android Gradle Plugin могут не запуститься.

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew installDebug     # установить на подключённое устройство
```

В Android Studio: открыть папку `android/`, JDK для Gradle — встроенный (JetBrains Runtime) или 21.

Путь к Android SDK — в `local.properties` (`sdk.dir=…`, файл в `.gitignore`; Android Studio создаёт его сама) или в переменной `ANDROID_HOME`.

## Устройство

| | |
|---|---|
| Версии | `gradle/libs.versions.toml`: Android Gradle Plugin 9.4, Kotlin 2.4, Compose BOM 2026.09; Gradle 9.8 — через wrapper |
| SDK | `compileSdk = 37` (требуют новые библиотеки AndroidX), `targetSdk = 36`, `minSdk = 26` |
| Идентификатор | `space.seydlitz.dataslate`; отладочная сборка — `space.seydlitz.dataslate.debug` («Dataslate (debug)»), ставится рядом с рабочей |
| Шрифты | `res/font/` — PT Mono и Forum; лицензии — `assets/licenses/` |

`applicationId` после первой раздачи APK не меняется: другой идентификатор — это для Android другое приложение, данные не переедут.
