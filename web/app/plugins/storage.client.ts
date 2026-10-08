// Изменения с других устройств — опросом, пока нет уведомлений (Centrifuge, D68).
const POLL_MS = 60_000

// Хранилище без persist браузер вправе очистить при нехватке места (D22).
export default defineNuxtPlugin(() => {
  navigator.storage?.persist?.()
  const auth = useAuth()
  const { state: link } = useServerLink()
  // Здесь, а не в обработчиках: вне setup контекста Nuxt нет.
  const characters = useCharacters()
  const sync = () => {
    if (auth.user.value) void characters.sync()
  }

  // На телефоне вкладку в фоне выгружают без pagehide.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushCharacters()
    else sync()
  })
  window.addEventListener('pagehide', () => flushCharacters())
  window.addEventListener('online', sync)
  watch(() => auth.user.value?.id, sync)
  watch(() => link.value === 'up', up => up && sync())
  setInterval(() => document.visibilityState === 'visible' && sync(), POLL_MS)
})
