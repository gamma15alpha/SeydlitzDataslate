# Веб-клиент

Nuxt 4 + Vue 3 + TypeScript. Одностраничное приложение без серверного рендеринга: работает офлайн, собирается в статику и отдаётся обратным прокси вместе с API сервера.

## Команды

```
yarn install
yarn dev          # http://localhost:3000; /api проксируется на сервер 127.0.0.1:8090
yarn build        # статика в .output/public
yarn preview
yarn typecheck
yarn test         # vitest: test/*.test.ts
yarn api:types    # после изменения schemas/openapi.yaml
```

Для `yarn dev` нужен запущенный сервер (см. `server/README.md`).

## Устройство

- `app/api/` — клиент API на [openapi-fetch](https://openapi-ts.dev/openapi-fetch/); `schema.d.ts` генерируется из `schemas/openapi.yaml`, руками не править.
- `app/composables/useAuth.ts` — сессия, вход, регистрация, выход. Пользователь кешируется в `localStorage`: офлайн приложение открывается по последней сессии.
- `app/middleware/auth.global.ts` — без входа доступны только страницы с `definePageMeta({ public: true })`.
- `app/plugins/session-expiry.ts` — на 401 посреди работы отправляет на вход с возвратом на текущую страницу.
- Регистрация по ссылке `/register?invite=КОД`; `/sessions` — активные сессии. Ротацию токена браузер обрабатывает сам (cookie).
- `i18n/locales/` — тексты интерфейса, `ru` и `en` ([@nuxtjs/i18n](https://i18n.nuxtjs.org)). Язык — из браузера при первом входе (английский, иначе русский), дальше — из cookie `lang`.
- `public/fonts/` — PT Mono и Forum с текстами лицензий SIL OFL.
- `.yarnrc.yml` — `nodeLinker: node-modules` (у части инструментов экосистемы Nuxt бывают проблемы с режимом Plug'n'Play).

TypeScript закреплён на 6.x: в 7.x нет JS API, на котором работают `openapi-typescript` и `vue-tsc`.

Yarn 4 по умолчанию не запускает скрипты сборки зависимостей (будет предупреждение про esbuild) — сборке это не мешает.
