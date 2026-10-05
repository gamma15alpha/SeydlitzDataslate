// Связь с сервером для лампы VOX: опрос /api/health раз в 30 с, пока вкладка видна, и сразу — при возврате
// на вкладку, появлении сети и сбое запроса к API. Опрос идёт мимо клиента API: ACT не мигает на каждый пульс.
//   up      — сервер ответил «ok»;
//   down    — сеть есть, а сервер не отвечает, отвечает ошибкой или прокси не достучался (502–504);
//   offline — у браузера нет сети;
//   unknown — ещё не проверяли.
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
    const response = await fetch('/api/health', { cache: 'no-store', signal: AbortSignal.timeout(TIMEOUT_MS) })
    const body = response.ok ? await response.json().catch(() => null) : null
    state.value = body?.status === 'ok' ? 'up' : 'down'
  } catch {
    state.value = navigator.onLine ? 'down' : 'offline'
  }
}

// Параллельные поводы проверить сливаются в один запрос.
export function checkServer() {
  return (inflight ??= probe().finally(() => (inflight = undefined)))
}

// Ответ на обычный запрос к API — тоже сведения о связи: 502–504 — прокси не достучался до сервера.
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

export function useServerLink() {
  return { state: readonly(state), check: checkServer }
}
