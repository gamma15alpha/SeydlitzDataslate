# Веб-клиент

Nuxt 4 + Vue 3 + TypeScript. Одностраничное приложение без серверного рендеринга: работает офлайн, собирается в статику и отдаётся обратным прокси вместе с API сервера.

## Команды

```
yarn install      # зависимости (Yarn 4, версия закреплена в package.json)
yarn dev          # разработка: http://localhost:3000
yarn build        # статика в .output/public (nuxt generate)
yarn preview      # просмотр собранной версии
```

## Устройство

- `app/` — исходники (srcDir Nuxt 4): `app.vue`, `assets/css/main.css` — шрифты и палитра когитатора.
- `public/fonts/` — PT Mono и Forum с текстами лицензий SIL OFL.
- `nuxt.config.ts` — `ssr: false`, язык страницы `ru`.
- `.yarnrc.yml` — `nodeLinker: node-modules` (у части инструментов экосистемы Nuxt бывают проблемы с режимом Plug'n'Play).

Yarn 4 по умолчанию не запускает скрипты сборки зависимостей (будет предупреждение про esbuild) — сборке это не мешает.
