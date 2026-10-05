# Android-клиент

Kotlin + Jetpack Compose (Material 3). Сеть — Ktor Client (движок OkHttp), JSON — kotlinx.serialization.

## Сборка

Нужен **JDK 21** — на более новых версиях Java Gradle и Android Gradle Plugin могут не запуститься.

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew installDebug     # установить на подключённое устройство
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest
```

Адрес сервера — gradle-свойство `dataslate.apiUrl` (`-Pdataslate.apiUrl=…` или `gradle.properties`). Debug по умолчанию ходит на `http://10.0.2.2:8090/` — компьютер разработчика из эмулятора; HTTP без TLS разрешён только в debug и только к нему. На телефоне по USB: `adb reverse tcp:8090 tcp:8090` и `-Pdataslate.apiUrl=http://127.0.0.1:8090/`. Release без адреса не собирается.

В Android Studio: открыть папку `android/`, JDK для Gradle — встроенный (JetBrains Runtime) или 21.

Путь к Android SDK — в `local.properties` (`sdk.dir=…`, файл в `.gitignore`; Android Studio создаёт его сама) или в переменной `ANDROID_HOME`.

## Устройство

| | |
|---|---|
| Версии | `gradle/libs.versions.toml`: Android Gradle Plugin 9.1 (максимум, который поддерживает Android-плагин IntelliJ IDEA), Kotlin 2.4, Compose BOM 2026.09; Gradle 9.8 — через wrapper |
| SDK | `compileSdk = 37` (требуют новые библиотеки AndroidX), `targetSdk = 36`, `minSdk = 26` |
| Идентификатор | `space.seydlitz.dataslate`; отладочная сборка — `space.seydlitz.dataslate.debug` («Dataslate (debug)»), ставится рядом с рабочей |
| Шрифты | `res/font/` — PT Mono и Forum; лицензии — `assets/licenses/` |

`applicationId` после первой раздачи APK не меняется: другой идентификатор — это для Android другое приложение, данные не переедут.

## Языки

Тексты — `res/values/strings.xml` (русский, по умолчанию) и `res/values-en/strings.xml`; `StringsTest` падает, если перевод неполный. Язык — системный; на Android 13+ его можно выбрать для приложения в настройках системы.

## Авторизация

- `api/` — клиент API; модели — по `schemas/openapi.yaml`. HTTP-ответ с ошибкой — значение `ApiResult.Failure` с кодом из тела, исключение сети — `ApiResult.Offline`.
- `auth/` — сессия: токен в DataStore, зашифрован ключом Android Keystore. Офлайн приложение открывается по сохранённой сессии; разлогинивает только `401`. Выход работает и без связи — токен удаляется локально.
- Ротация: новый токен из заголовка `X-Session-Token` сохраняется сразу. Отозванная сессия (`session_revoked`) — выход с сообщением на экране входа.
