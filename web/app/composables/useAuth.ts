import type { components } from '~/api/schema'
import { api } from '~/api/client'

export type User = components['schemas']['User']
type ApiError = components['schemas']['Error']

// Офлайн пускаем по последнему известному пользователю; выкидывает только 401 от сервера.
const CACHE_KEY = 'dataslate:user'
const NO_CONNECTION = 'Нет связи с сервером'
export const REVOKED_NOTICE = 'Сессия завершена: вход с этой сессией выполнен с другого устройства. Войдите снова и проверьте активные сессии.'

let restoring: Promise<void> | undefined

export function useAuth() {
  const user = useState<User | null>('auth:user', () => null)
  const offline = useState('auth:offline', () => false)
  const ready = useState('auth:ready', () => false)
  // Сообщение для экрана входа (например, сессию отозвали).
  const notice = useState<string | null>('auth:notice', () => null)

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
      const { data, error, response } = await api.GET('/api/me')
      offline.value = false
      if (data) setUser(data)
      else if (response.status === 401) {
        setUser(null)
        if (error?.code === 'session_revoked') notice.value = REVOKED_NOTICE
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

  async function submit(call: () => Promise<{ data?: { user: User }; error?: ApiError; response: Response }>) {
    try {
      const { data, error, response } = await call()
      offline.value = false
      if (data) {
        setUser(data.user)
        notice.value = null
        return null
      }
      return errorMessage(error, response)
    } catch {
      return NO_CONNECTION
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
      return NO_CONNECTION
    }
    setUser(null)
    await navigateTo('/login')
    return null
  }

  return { user, offline, ready, notice, restore, login, register, logout, forget: () => setUser(null) }
}

const messages: Record<string, string> = {
  invalid_credentials: 'Неверный логин или пароль',
  invalid_invite: 'Инвайт недействителен, истёк или уже использован',
  login_taken: 'Логин уже занят',
  invalid_login: 'Логин: от 3 до 32 символов — латиница, цифры, «_», «.», «-»',
  invalid_password: 'Пароль: от 8 до 128 символов',
  invalid_display_name: 'Имя — не длиннее 64 символов',
}

function errorMessage(error: ApiError | undefined, response: Response) {
  const code = error?.code ?? ''
  if (code === 'too_many_attempts' || code === 'rate_limited') {
    return `Слишком много попыток. Повторите через ${retryAfter(response)}`
  }
  return messages[code] ?? `Ошибка сервера ${response.status}, код запроса ${response.headers.get('X-Request-ID') ?? '—'}`
}

function retryAfter(response: Response) {
  const seconds = Number(response.headers.get('Retry-After')) || 60
  return seconds < 60 ? `${seconds} с` : `${Math.ceil(seconds / 60)} мин`
}

// Только пути своего приложения: иначе ?redirect= уводит на чужой сайт.
export function safeRedirect(target: unknown) {
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//') ? target : '/'
}
