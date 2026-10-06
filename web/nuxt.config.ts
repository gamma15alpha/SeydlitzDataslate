import { fileURLToPath } from 'node:url'

export default defineNuxtConfig({
  compatibilityDate: '2026-10-05',
  ssr: false,
  devtools: { enabled: true },
  modules: ['@nuxtjs/i18n', '@vite-pwa/nuxt'],
  css: ['~/assets/css/casing.css', '~/assets/css/main.css'],
  nitro: {
    devProxy: { '/api': { target: 'http://127.0.0.1:8090/api', changeOrigin: true } },
  },
  // Демо-анкета импортирует примеры из ../schemas/examples.
  vite: {
    server: { fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] } },
  },
  i18n: {
    strategy: 'no_prefix',
    defaultLocale: 'ru',
    locales: [
      { code: 'ru', language: 'ru', name: 'Русский', file: 'ru.json' },
      { code: 'en', language: 'en', name: 'English', file: 'en.json' },
    ],
    detectBrowserLanguage: { useCookie: true, cookieKey: 'lang', fallbackLocale: 'ru' },
  },
  pwa: {
    registerType: 'prompt',
    manifest: {
      name: 'Seydlitz Dataslate',
      short_name: 'Dataslate',
      lang: 'ru',
      display: 'standalone',
      background_color: '#030603',
      theme_color: '#030603',
    },
    pwaAssets: { config: false, preset: 'minimal-2023', image: 'public/icon.svg' },
    workbox: {
      globPatterns: ['**/*.{js,css,html,svg,png,ico,ttf}'],
      // Иначе переход на /api/… получил бы index.html из кеша.
      navigateFallbackDenylist: [/^\/api\//],
    },
    client: { periodicSyncForUpdates: 3600 },
  },
  app: {
    head: {
      meta: [{ name: 'viewport', content: 'width=device-width, initial-scale=1, viewport-fit=cover' }],
    },
  },
})
