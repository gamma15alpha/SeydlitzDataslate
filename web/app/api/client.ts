import createClient from 'openapi-fetch'
import type { paths } from './schema'

const TIMEOUT_MS = 15_000

// Сессия — HttpOnly-cookie, её шлёт браузер. Таймаут — чтобы зависший сервер не держал кнопку бесконечно.
export const api = createClient<paths>({
  baseUrl: '',
  credentials: 'same-origin',
  fetch: request => fetch(request, { signal: AbortSignal.any([request.signal, AbortSignal.timeout(TIMEOUT_MS)]) }),
})
