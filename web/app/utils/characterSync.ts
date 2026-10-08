import type { Client } from 'openapi-fetch'
import { copyCharacterFile } from 'dataslate-core'
import type { components, paths } from '../api/schema'
import type { CharacterStore, StoredDocument } from './characterStore'
import type { CharacterFile } from './dh1Content'

// null — удалена (на устройстве или на сервере).
export interface SyncConflict {
  id: string
  local: CharacterFile | null
  server: CharacterFile | null
  serverRevision: number
}

export interface SyncResult {
  // Обновлены с сервера: открытую анкету — перечитать.
  changed: string[]
  conflicts: SyncConflict[]
  // id занят другим пользователем — анкета сохранена копией под новым id.
  renamed: Record<string, string>
}

export class SyncError extends Error {
  constructor(readonly offline: boolean, message: string) {
    super(message)
  }
}

type Api = Client<paths>
type StoredCharacter = components['schemas']['StoredCharacter']

// Ключи по порядку: сервер хранит jsonb и возвращает их в своём.
function canonical(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(k => [k, canonical((value as Record<string, unknown>)[k])]))
  }
  return value
}

export function sameCharacter(a: unknown, b: unknown) {
  return JSON.stringify(canonical(a)) === JSON.stringify(canonical(b))
}

async function call<T extends { response: Response }>(request: Promise<T>): Promise<T> {
  try {
    return await request
  } catch (e) {
    throw new SyncError(true, String(e))
  }
}

function failed(response: Response): never {
  throw new SyncError(false, `HTTP ${response.status} ${response.headers.get('X-Request-ID') ?? ''}`.trim())
}

/**
 * Обмен анкетами (D68): сначала изменения с сервера, потом свои.
 * Неотправленное с сервера не перезаписывается — расхождение всплывёт при отправке (412) как конфликт.
 * busy(id) — правка ещё не дошла до хранилища (черновик автосохранения): её тоже не трогаем.
 */
export async function syncCharacters(store: CharacterStore, api: Api, busy: (id: string) => boolean): Promise<SyncResult> {
  const result: SyncResult = { changed: [], conflicts: [], renamed: {} }

  const pulled = await call(api.GET('/api/characters', { params: { query: { since: await store.cursor() } } }))
  if (!pulled.data) failed(pulled.response)
  for (const c of pulled.data.changes) {
    const doc = await store.document(c.id)
    if (doc && doc.serverRevision === c.revision) continue // своя же запись
    if (busy(c.id)) continue
    const file = c.deleted ? null : (c.character as CharacterFile)
    if (await store.applyRemote(c.id, c.revision, file)) result.changed.push(c.id)
  }
  await store.setCursor(pulled.data.cursor)

  for (const doc of await store.pending()) await push(store, api, doc, result)
  return result
}

async function push(store: CharacterStore, api: Api, doc: StoredDocument, result: SyncResult): Promise<void> {
  const id = doc.key
  const path = { params: { path: { id } } }
  const ifMatch = { 'If-Match': `"${doc.serverRevision}"` }
  const { data, error, response } = doc.deleted
    ? await call(api.DELETE('/api/characters/{id}', { ...path, headers: ifMatch }))
    : await call(api.PUT('/api/characters/{id}', {
        ...path,
        headers: doc.serverRevision ? ifMatch : { 'If-None-Match': '*' },
        body: JSON.parse(doc.json),
      }))

  if (data) return store.markSent(id, doc.revision, data.revision)
  switch (response.status) {
    case 412: {
      const server = error as StoredCharacter
      const serverFile = server.deleted ? null : (server.character as CharacterFile)
      const local = doc.deleted ? null : (JSON.parse(doc.json) as CharacterFile)
      // Совпадает — значит, это наша же запись, ответ на которую потерялся.
      if (sameCharacter(local, serverFile)) return store.markSent(id, doc.revision, server.revision)
      result.conflicts.push({ id, local, server: serverFile, serverRevision: server.revision })
      return
    }
    case 404:
      // На сервере нет (база пересоздана): удалять нечего, остальное — создать заново.
      if (doc.deleted) return store.forget(id)
      await store.rebase(id, 0)
      return push(store, api, { ...doc, serverRevision: 0 }, result)
    case 409: {
      // id занят другим пользователем (один файл импортировали двое): своя — под новым id.
      if (doc.deleted) return store.forget(id)
      const copy: CharacterFile = JSON.parse(copyCharacterFile(doc.json)!)
      await store.put(copy)
      await store.forget(id)
      result.renamed[id] = copy.id
      const moved = await store.document(copy.id)
      return moved && push(store, api, moved, result)
    }
    default:
      failed(response)
  }
}

// Решения конфликта; отправит следующий обмен.
export async function keepMine(store: CharacterStore, c: SyncConflict) {
  await store.rebase(c.id, c.serverRevision)
}

export async function takeServer(store: CharacterStore, c: SyncConflict) {
  await store.applyRemote(c.id, c.serverRevision, c.server, true)
}

// Только когда обе версии есть: своя — копией под новым id, у этого id — версия сервера.
export async function keepBoth(store: CharacterStore, c: SyncConflict) {
  if (!c.local || !c.server) throw new Error('keepBoth needs both versions')
  const copy: CharacterFile = JSON.parse(copyCharacterFile(JSON.stringify(c.local))!)
  await store.put(copy)
  await store.applyRemote(c.id, c.serverRevision, c.server, true)
  return copy
}
