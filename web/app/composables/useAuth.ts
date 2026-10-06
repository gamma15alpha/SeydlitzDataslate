import type { components } from '~/api/schema'
import { api } from '~/api/client'

export type User = components['schemas']['User']
type ApiError = components['schemas']['Error']
type Translate = (key: string, params?: Record<string, unknown>) => string

const CACHE_KEY = 'dataslate:user'
const RESTORE_TIMEOUT_MS = 5000

let restoring: Promise<void> | undefined
let refreshing: Promise<void> | undefined

export function useAuth() {
  const user = useState<User | null>('auth:user', () => null)
  const offline = useState('auth:offline', () => false)
  const ready = useState('auth:ready', () => false)
  const notice = useState<string | null>('auth:notice', () => null)
  const t: Translate = useNuxtApp().$i18n.t

  function setUser(u: User | null) {
    user.value = u
    if (u) localStorage.setItem(CACHE_KEY, JSON.stringify(u))
    else localStorage.removeItem(CACHE_KEY)
  }

  function useCached() {
    const cached = localStorage.getItem(CACHE_KEY)
    user.value = cached ? (JSON.parse(cached) as User) : null
  }

  async function load() {
    try {
      // Middleware ждёт его до первой страницы: без таймаута зависший сервер оставит пустой экран.
      const { data, error, response } = await api.GET('/api/me', { signal: AbortSignal.timeout(RESTORE_TIMEOUT_MS) })
      offline.value = false
      if (data) setUser(data)
      else if (response.status === 401) {
        setUser(null)
        if (error?.code === 'session_revoked') notice.value = 'auth.revoked'
      }
      else useCached() // сбой сервера — не повод разлогинивать
    } catch {
      offline.value = true
      useCached()
    }
    ready.value = true
  }

  function restore() {
    return (restoring ??= load())
  }

  function refresh() {
    return (refreshing ??= load().finally(() => (refreshing = undefined)))
  }

  async function submit(call: () => Promise<{ data?: { user: User }; error?: ApiError; response: Response }>) {
    try {
      const { data, error, response } = await call()
      offline.value = false
      if (data) {
        setUser(data.user)
        notice.value = null
        return null
      }
      return errorMessage(t, error, response)
    } catch {
      return t('errors.noConnection')
    }
  }

  function login(login: string, password: string) {
    return submit(() => api.POST('/api/auth/login', { body: { login, password } }))
  }

  function register(body: { invite: string; login: string; password: string; displayName?: string }) {
    return submit(() => api.POST('/api/auth/register', { body }))
  }

  // Офлайн выйти нельзя: HttpOnly-cookie из JS не удалить, и сессия вернулась бы при связи.
  async function logout() {
    try {
      await api.POST('/api/auth/logout')
    } catch {
      return t('errors.noConnection')
    }
    setUser(null)
    await navigateTo('/login')
    return null
  }

  return { user, offline, ready, notice, restore, refresh, login, register, logout, updateUser: setUser, forget: () => setUser(null) }
}

const knownCodes = new Set([
  'invalid_credentials', 'invalid_invite', 'login_taken', 'invalid_login', 'invalid_password', 'invalid_display_name',
  'wrong_password', 'invalid_expiry',
])

export function errorMessage(t: Translate, error: ApiError | undefined, response: Response) {
  const code = error?.code ?? ''
  if (code === 'too_many_attempts' || code === 'rate_limited') {
    const seconds = Number(response.headers.get('Retry-After')) || 60
    const time = seconds < 60 ? t('errors.seconds', { n: seconds }) : t('errors.minutes', { n: Math.ceil(seconds / 60) })
    return t('errors.tooMany', { time })
  }
  if (knownCodes.has(code)) return t(`errors.${code}`)
  return t('errors.serverWithId', { status: response.status, id: response.headers.get('X-Request-ID') ?? '—' })
}

// Только пути своего приложения: иначе ?redirect= уводит на чужой сайт.
export function safeRedirect(target: unknown) {
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//') ? target : '/'
}
