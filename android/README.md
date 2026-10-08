# Android-клиент

Kotlin, Jetpack Compose, Ktor Client, kotlinx.serialization; модели и правила — из [`shared/`](../shared/README.md). Нужен JDK 21: демон Gradle закреплён на нём (`gradle/gradle-daemon-jvm.properties`), сам Gradle его не скачивает.

Сборка Gradle — из корня репозитория (в Android Studio открывать корень):

```
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
```

- Адрес сервера — свойство `dataslate.apiUrl`. Debug по умолчанию ходит на `http://10.0.2.2:8090/` (компьютер из эмулятора), release без адреса не собирается. Телефон по USB: `adb reverse tcp:8090 tcp:8090` и `-Pdataslate.apiUrl=http://127.0.0.1:8090/`.
- Android SDK — `local.properties` в корне (`sdk.dir`) или `ANDROID_HOME`.
- `applicationId` (`space.seydlitz.dataslate`) после первой раздачи APK не менять: данные не переедут.
