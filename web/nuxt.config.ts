// Веб-клиент Seydlitz Dataslate.
// SPA без серверного рендеринга: приложение работает офлайн (данные — в IndexedDB),
// собирается в статику (`yarn build` → .output/public) и отдаётся обратным прокси вместе с API.
export default defineNuxtConfig({
  compatibilityDate: '2026-10-05',
  ssr: false,
  devtools: { enabled: true },
  css: ['~/assets/css/main.css'],
  // В разработке API — Go-сервер (server/); в проде /api проксирует тот же обратный прокси.
  nitro: {
    devProxy: { '/api': { target: 'http://127.0.0.1:8090/api', changeOrigin: true } },
  },
  app: {
    head: {
      htmlAttrs: { lang: 'ru' },
      title: 'Seydlitz Dataslate',
      meta: [{ name: 'viewport', content: 'width=device-width, initial-scale=1, viewport-fit=cover' }],
    },
  },
})
