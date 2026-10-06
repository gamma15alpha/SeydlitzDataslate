// Сессию, сохранённую без связи, проверяем при её возвращении; 401 обработает session-expiry.
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
