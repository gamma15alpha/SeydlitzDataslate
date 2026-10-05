// Веб-клиент Seydlitz Dataslate.
// SPA без серверного рендеринга: приложение работает офлайн (данные — в IndexedDB),
// собирается в статику (`yarn build` → .output/public) и отдаётся обратным прокси вместе с API.
import { fileURLToPath } from 'node:url'

export default defineNuxtConfig({
  compatibilityDate: '2026-10-05',
  ssr: false,
  devtools: { enabled: true },
  modules: ['@nuxtjs/i18n'],
  css: ['~/assets/css/casing.css', '~/assets/css/main.css'],
  // В разработке API — Go-сервер (server/); в проде /api проксирует тот же обратный прокси.
  nitro: {
    devProxy: { '/api': { target: 'http://127.0.0.1:8090/api', changeOrigin: true } },
  },
  // Демо-анкета импортирует примеры из ../schemas/examples.
  vite: {
    server: { fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] } },
  },
  // Язык — из браузера при первом входе (английский, иначе русский), дальше — из cookie.
  i18n: {
    strategy: 'no_prefix',
    defaultLocale: 'ru',
    locales: [
      { code: 'ru', language: 'ru', name: 'Русский', file: 'ru.json' },
      { code: 'en', language: 'en', name: 'English', file: 'en.json' },
    ],
    detectBrowserLanguage: { useCookie: true, cookieKey: 'lang', fallbackLocale: 'ru' },
  },
  app: {
    head: {
      meta: [{ name: 'viewport', content: 'width=device-width, initial-scale=1, viewport-fit=cover' }],
    },
  },
})
