# Веб-клиент

Nuxt 4, Vue 3, TypeScript. SPA без SSR и PWA, собирается в статику.

```
yarn install
yarn dev
yarn build
yarn typecheck
yarn test
yarn api:types
```

- `yarn dev` — http://localhost:3000, `/api` проксируется на сервер `127.0.0.1:8090`.
- `yarn build` — статика в `.output/public`.
- `yarn api:types` — после изменения `schemas/openapi.yaml`; `app/api/schema.d.ts` руками не править.
- TypeScript закреплён на 6.x: `openapi-typescript` и `vue-tsc` не работают с 7.x.
