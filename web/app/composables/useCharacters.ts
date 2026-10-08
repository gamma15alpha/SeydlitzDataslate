import { copyCharacterFile, exportCharacterFile, newCharacterFile, readCharacterFile } from 'dataslate-core'
import { api } from '~/api/client'

const SAVE_DELAY_MS = 500

// На всё приложение: несохранённая правка переживает смену страницы.
let store: CharacterStore | undefined
let storeUser: string | undefined
// Черновик помнит своё хранилище: после смены учётки он допишется в прежнее.
const drafts = new Map<string, { store: CharacterStore; file: CharacterFile }>()
const timers = new Map<string, ReturnType<typeof setTimeout>>()
const saveFailed = ref(false)
const list = ref<CharacterFile[]>()

// Обмен с сервером (D68).
export type SyncState = 'idle' | 'syncing' | 'offline' | 'error'
const syncState = ref<SyncState>('idle')
const conflicts = ref<SyncConflict[]>([])
// id → счётчик: анкета обновлена с сервера или после конфликта — открытую перечитать.
const reloads = ref<Record<string, number>>({})
// Старый id → новый: анкета сохранена копией (id занят на сервере).
const renamed = ref<Record<string, string>>({})
let syncing: Promise<void> | undefined
let syncAgain = false

function storeFor(userId: string | undefined) {
  if (userId !== storeUser) {
    const old = store
    storeUser = userId
    store = userId ? createCharacterStore(userId) : undefined
    list.value = undefined
    conflicts.value = []
    syncState.value = 'idle'
    if (old) void flushCharacters().then(() => old.close())
  }
  return store
}

async function write(id: string) {
  clearTimeout(timers.get(id))
  timers.delete(id)
  const draft = drafts.get(id)
  drafts.delete(id)
  if (!draft) return
  try {
    await draft.store.put({ ...draft.file, updatedAt: new Date().toISOString() })
    saveFailed.value = false
  } catch (e) {
    console.error('save failed', e)
    saveFailed.value = true
    return
  }
  if (draft.store === store) void requestSync()
}

export function flushCharacters() {
  return Promise.all([...drafts.keys()].map(write))
}

async function reloadList() {
  if (list.value && store) list.value = (await store.list()).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
}

function reload(id: string) {
  reloads.value[id] = (reloads.value[id] ?? 0) + 1
}

// Один обмен за раз; запрос во время обмена — ещё один круг после него.
export function requestSync(): Promise<void> {
  if (!store) return Promise.resolve()
  if (syncing) {
    syncAgain = true
    return syncing
  }
  syncing = (async () => {
    do {
      syncAgain = false
      await runSync(store)
    } while (syncAgain && store)
  })().finally(() => (syncing = undefined))
  return syncing
}

async function runSync(s: CharacterStore | undefined) {
  if (!s) return
  await flushCharacters()
  syncState.value = 'syncing'
  try {
    const r = await syncCharacters(s, api, id => drafts.has(id))
    if (s !== store) return
    conflicts.value = r.conflicts
    Object.assign(renamed.value, r.renamed)
    r.changed.forEach(reload)
    if (r.changed.length || Object.keys(r.renamed).length) await reloadList()
    syncState.value = 'idle'
  } catch (e) {
    if (s !== store) return
    if (!(e instanceof SyncError)) console.error('sync failed', e)
    syncState.value = e instanceof SyncError && e.offline ? 'offline' : 'error'
  }
}

export type ImportResult =
  | { error: string }
  | { conflict: { existing: CharacterFile; incoming: CharacterFile } }
  | { imported: CharacterFile }

export function useCharacters() {
  const auth = useAuth()
  // Раздел анкет — только после входа (auth.global), пользователь есть.
  function current() {
    const s = storeFor(auth.user.value?.id)
    if (!s) throw new Error('no user')
    return s
  }

  async function refresh() {
    const s = current()
    await flushCharacters()
    list.value = (await s.list()).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  }

  async function get(id: string) {
    const s = current()
    await flushCharacters()
    return s.get(id)
  }

  // Копия: правки не должны менять черновик, который уже ждёт записи.
  function scheduleSave(character: CharacterFile) {
    drafts.set(character.id, { store: current(), file: JSON.parse(JSON.stringify(character)) })
    clearTimeout(timers.get(character.id))
    timers.set(character.id, setTimeout(() => write(character.id), SAVE_DELAY_MS))
  }

  async function put(character: CharacterFile) {
    await current().put(character)
    void requestSync()
  }

  async function create(name: string) {
    const user = auth.user.value!
    const character: CharacterFile = JSON.parse(newCharacterFile(name, user.id, user.displayName))
    await put(character)
    return character
  }

  async function remove(id: string) {
    drafts.delete(id)
    clearTimeout(timers.get(id))
    await current().remove(id)
    void requestSync()
  }

  async function importText(text: string): Promise<ImportResult> {
    const r = readCharacterFile(text)
    if (r.json == null) return { error: r.error! }
    const incoming: CharacterFile = JSON.parse(r.json)
    const existing = await get(incoming.id)
    if (existing) return { conflict: { existing, incoming } }
    await put(incoming)
    return { imported: incoming }
  }

  async function importAsCopy(incoming: CharacterFile) {
    const copy: CharacterFile = JSON.parse(copyCharacterFile(JSON.stringify(incoming))!)
    await put(copy)
    return copy
  }

  // Конфликт с сервером: mine — своя поверх серверной, server — серверная, both — своя копией. Возвращает копию.
  async function resolveConflict(conflict: SyncConflict, how: 'mine' | 'server' | 'both') {
    const s = current()
    await flushCharacters()
    let copy: CharacterFile | undefined
    if (how === 'mine') await keepMine(s, conflict)
    else if (how === 'server') await takeServer(s, conflict)
    else copy = await keepBoth(s, conflict)
    conflicts.value = conflicts.value.filter(c => c.id !== conflict.id)
    if (how !== 'mine') reload(conflict.id)
    await reloadList()
    void requestSync()
    return copy
  }

  async function exportFile(id: string) {
    const character = await get(id)
    const text = character && exportCharacterFile(JSON.stringify(character))
    if (!text) return
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `${character.name.replace(/[\\/:*?"<>|]+/g, '_').trim() || 'character'}.character.json`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  return {
    list, saveFailed, syncState, conflicts, reloads, renamed,
    refresh, get, scheduleSave, create, remove, importText, replace: put, importAsCopy, resolveConflict, exportFile,
    sync: () => (current(), requestSync()),
  }
}
