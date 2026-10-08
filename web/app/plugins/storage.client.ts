// Хранилище без persist браузер вправе очистить при нехватке места (D22).
export default defineNuxtPlugin(() => {
  navigator.storage?.persist?.()
  // На телефоне вкладку в фоне выгружают без pagehide.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushCharacters()
  })
  window.addEventListener('pagehide', () => flushCharacters())
})
