# Seydlitz Dataslate

Автоматизированный лист персонажа для Dark Heresy 1st Edition. Игровой контент не поставляется.

| Папка | Стек |
|---|---|
| [`shared/`](shared/README.md) | Ядро правил: Kotlin Multiplatform (JVM для Android, JS для веба) |
| [`web/`](web/README.md) | Nuxt 4, Vue 3, TypeScript; Node 20+, Yarn, JDK 21 (ядро) |
| [`android/`](android/README.md) | Kotlin, Jetpack Compose; JDK 21, Android SDK 37 |
| [`server/`](server/README.md) | Go, chi, PostgreSQL; Docker для разработки |
| [`schemas/`](schemas/openapi.yaml) | OpenAPI 3.1, JSON Schema |

## Лицензия

Код — [MIT](LICENSE). Шрифты PT Mono и Forum — SIL Open Font License, тексты лицензий лежат рядом со шрифтами.
