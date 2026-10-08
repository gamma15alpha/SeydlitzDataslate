# Ядро правил

Kotlin Multiplatform: модели контента и анкеты, формат файлов, правила. Android подключает JVM-вариант (`:shared`), веб — сборку Kotlin/JS по алиасу `dataslate-core`. Сборка Gradle — из корня репозитория.

```
./gradlew :shared:allTests
./gradlew :shared:jsProductionLibraryDistribution
./gradlew :shared:jsProductionLibraryDistribution --continuous
```

- `commonMain` — общий код, `commonTest` — тесты, идут и на JVM, и в Node.
- `jsMain` — фасад для веба (`@JsExport`): классы с `Array` вместо `List`, модели ядра наружу не отдаются. Результат — `build/dist/js/productionLibrary` (`.mjs` и `.d.mts`).
- Код Kotlin в бандле веба не вытряхивается: объём задаёт то, что экспортировано.
- `kotlin-js-store/` в корне — lock-файл зависимостей Kotlin/JS, коммитится.
