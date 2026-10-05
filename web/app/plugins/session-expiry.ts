import { api } from '~/api/client'

// Сессия истекла или отозвана посреди работы — на вход, с возвратом на текущую страницу.
export default defineNuxtPlugin(() => {
  const auth = useAuth()
  const router = useRouter()
  api.use({
    async onResponse({ request, response }) {
      if (response.status !== 401 || !auth.ready.value || !auth.user.value) return
      if (new URL(request.url).pathname.startsWith('/api/auth/')) return
      const body = await response.clone().json().catch(() => null)
      if (body?.code === 'session_revoked') auth.notice.value = REVOKED_NOTICE
      auth.forget()
      void navigateTo({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    },
  })
})
