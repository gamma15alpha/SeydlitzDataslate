// Только в dev (nuxt.config): заменяет SW, оставшийся от прод-сборки на этом же адресе, — иначе он отдаёт старую сборку из кеша.
export default defineEventHandler((event) => {
  setResponseHeader(event, 'Content-Type', 'text/javascript')
  setResponseHeader(event, 'Cache-Control', 'no-store')
  return `self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (e) => e.waitUntil((async () => {
  await self.registration.unregister()
  for (const key of await caches.keys()) await caches.delete(key)
  for (const client of await self.clients.matchAll({ type: 'window' })) client.navigate(client.url)
})()))
`
})
