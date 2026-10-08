import { copyCharacterFile, exportCharacterFile, newCharacterFile, readCharacterFile } from 'dataslate-core'

const SAVE_DELAY_MS = 500

// На всё приложение: несохранённая правка переживает смену страницы.
const store = createCharacterStore()
const drafts = new Map<string, CharacterFile>()
const timers = new Map<string, ReturnType<typeof setTimeout>>()
const saveFailed = ref(false)

async function write(id: string) {
  clearTimeout(timers.get(id))
  timers.delete(id)
  const draft = drafts.get(id)
  drafts.delete(id)
  if (!draft) return
  try {
    await store.put({ ...draft, updatedAt: new Date().toISOString() })
    saveFailed.value = false
  } catch (e) {
    console.error('save failed', e)
    saveFailed.value = true
  }
}

export function flushCharacters() {
  return Promise.all([...drafts.keys()].map(write))
}

export type ImportResult =
  | { error: string }
  | { conflict: { existing: CharacterFile; incoming: CharacterFile } }
  | { imported: CharacterFile }

export function useCharacters() {
  const list = useState<CharacterFile[] | undefined>('characters', () => undefined)
  const auth = useAuth()

  async function refresh() {
    await flushCharacters()
    list.value = (await store.list()).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  }

  async function get(id: string) {
    await flushCharacters()
    return store.get(id)
  }

  // Копия: правки не должны менять черновик, который уже ждёт записи.
  function scheduleSave(character: CharacterFile) {
    drafts.set(character.id, JSON.parse(JSON.stringify(character)))
    clearTimeout(timers.get(character.id))
    timers.set(character.id, setTimeout(() => write(character.id), SAVE_DELAY_MS))
  }

  // Раздел анкет — только после входа (auth.global), пользователь есть.
  async function create(name: string) {
    const user = auth.user.value!
    const character: CharacterFile = JSON.parse(newCharacterFile(name, user.id, user.displayName))
    await store.put(character)
    return character
  }

  async function remove(id: string) {
    drafts.delete(id)
    clearTimeout(timers.get(id))
    await store.remove(id)
  }

  async function importText(text: string): Promise<ImportResult> {
    const r = readCharacterFile(text)
    if (r.json == null) return { error: r.error! }
    const incoming: CharacterFile = JSON.parse(r.json)
    const existing = await get(incoming.id)
    if (existing) return { conflict: { existing, incoming } }
    await store.put(incoming)
    return { imported: incoming }
  }

  async function replace(incoming: CharacterFile) {
    await store.put(incoming)
  }

  async function importAsCopy(incoming: CharacterFile) {
    const copy: CharacterFile = JSON.parse(copyCharacterFile(JSON.stringify(incoming))!)
    await store.put(copy)
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

  return { list, saveFailed, refresh, get, scheduleSave, create, remove, importText, replace, importAsCopy, exportFile }
}
