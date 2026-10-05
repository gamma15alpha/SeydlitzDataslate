// Связь вернулась, а приложение работает офлайн по сохранённой сессии — проверяем её на сервере.
// Сессия жива — пропадает пометка «работа офлайн»; истекла или отозвана — 401, и session-expiry отправит на вход.
export default defineNuxtPlugin(() => {
  const auth = useAuth()
  const { state: link } = useServerLink()
  watch(
    () => link.value === 'up' && auth.offline.value && auth.ready.value,
    (stale) => {
      if (stale) void auth.refresh()
    },
  )
})
