# Веб-клиент

Nuxt 4, Vue 3, TypeScript. SPA без SSR и PWA, собирается в статику. Правила — ядро на Kotlin из [`shared/`](../shared/README.md), для его сборки нужен JDK 21.

```
yarn install
yarn dev
yarn build
yarn typecheck
yarn test
yarn core:watch
yarn api:types
```

- `yarn dev`, `build`, `typecheck`, `test` сначала собирают ядро (`yarn core`).
- `yarn core:watch` — в отдельном терминале при правке `shared/`: пересобирает ядро, `yarn dev` подхватывает.
- `yarn dev` — http://localhost:3000, `/api` проксируется на сервер `127.0.0.1:8090`. Service worker в dev не работает: `/sw.js` снимает оставшийся от прод-сборки и чистит его кеш (`server/dev/sw.ts`), вкладка перезагружается сама.
- `yarn build` — статика в `.output/public`.
- `yarn api:types` — после изменения `schemas/openapi.yaml`; `app/api/schema.d.ts` руками не править.
- TypeScript закреплён на 6.x: `openapi-typescript` и `vue-tsc` не работают с 7.x.
