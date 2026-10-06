export type LinkState = 'unknown' | 'up' | 'down' | 'offline'

const POLL_MS = 30_000
const TIMEOUT_MS = 5000

const state = ref<LinkState>('unknown')
let inflight: Promise<void> | undefined
let started = false

async function probe() {
  if (!navigator.onLine) {
    state.value = 'offline'
    return
  }
  try {
    // Мимо клиента API: ACT не мигает на каждый опрос.
    const response = await fetch('/api/health', { cache: 'no-store', signal: AbortSignal.timeout(TIMEOUT_MS) })
    const body = response.ok ? await response.json().catch(() => null) : null
    state.value = body?.status === 'ok' ? 'up' : 'down'
  } catch {
    state.value = navigator.onLine ? 'down' : 'offline'
  }
}

export function checkServer() {
  return (inflight ??= probe().finally(() => (inflight = undefined)))
}

// 502–504 — прокси не достучался до сервера.
export function reportApiResponse(status: number) {
  if (status >= 502 && status <= 504) void checkServer()
  else state.value = 'up'
}

export function startServerLink() {
  if (started) return
  started = true
  void checkServer()
  setInterval(() => {
    if (document.visibilityState === 'visible') void checkServer()
  }, POLL_MS)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') void checkServer()
  })
  window.addEventListener('online', () => void checkServer())
  window.addEventListener('offline', () => (state.value = 'offline'))
}

// unknown — доступен: кнопки не мигают при запуске.
const available = computed(() => state.value !== 'down' && state.value !== 'offline')

export function useServerLink() {
  return { state: readonly(state), available, check: checkServer }
}
