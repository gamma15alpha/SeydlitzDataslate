# Seydlitz Dataslate

Автоматизированный лист персонажа для Dark Heresy 1st Edition

| Папка | Стек | Подробнее |
|---|---|---|
| `web/` | Nuxt 4, Vue 3, TypeScript, Yarn 4 | [web/README.md](web/README.md) |
| `android/` | Kotlin, Jetpack Compose | [android/README.md](android/README.md) |
| `server/` | Go, chi, PostgreSQL | [server/README.md](server/README.md) |
| `schemas/` | OpenAPI 3.1, JSON Schema | [schemas/openapi.yaml](schemas/openapi.yaml) |

## Контент

Игровой контент не поставляется

## Подготовка машины

| Что | Версия | Для чего |
|---|---|---|
| Node.js | 20+ | веб |
| Yarn | любой; версия проекта закреплена в `web/package.json` | веб |
| JDK | **21** должен быть установлен; Gradle сам запускает на нём демон (`gradle/gradle-daemon-jvm.properties`), даже если по умолчанию в системе Java новее | Android |
| Android SDK | платформа 37 (Gradle скачает сам при принятых лицензиях) | Android; удобнее всего через Android Studio |
| Go | из `server/go.mod` | сервер |
| Docker | любой с `docker compose` | PostgreSQL для сервера при разработке |

## Быстрый старт

```
cd web && yarn install && yarn dev                                        # http://localhost:3000
cd android && ./gradlew assembleDebug
cd server && docker compose up -d && DATABASE_URL=postgres://dataslate:dataslate@127.0.0.1:5432/dataslate go run ./cmd/server  # http://127.0.0.1:8090/api/health
```

## Лицензия

Код — [MIT](LICENSE). Шрифты PT Mono и Forum — SIL Open Font License (тексты лицензий лежат рядом со шрифтами).
