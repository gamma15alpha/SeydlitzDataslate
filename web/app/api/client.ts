import createClient from 'openapi-fetch'
import type { paths } from './schema'

// Типы — из schemas/openapi.yaml: yarn api:types. Сессия — в HttpOnly-cookie, её шлёт браузер.
export const api = createClient<paths>({ baseUrl: '', credentials: 'same-origin' })
