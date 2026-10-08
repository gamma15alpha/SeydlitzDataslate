import { deleteDB, openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { readCharacterFile } from 'dataslate-core'
import type { CharacterFile } from './dh1Content'

// D25: документы по (collection, key); надгробия и ревизии — для синхронизации (D68).
export interface StoredDocument {
  collection: string
  key: string
  json: string
  // Локальный счётчик правок: отправленная версия ещё та же, что в хранилище?
  revision: number
  updatedAt: string
  deleted: boolean
  // Ревизия сервера, на которой основана локальная версия; 0 — на сервере нет.
  serverRevision: number
  // Есть изменения, не отправленные на сервер.
  dirty: boolean
}

interface DataslateDb extends DBSchema {
  documents: { key: [string, string]; value: StoredDocument; indexes: { collection: string } }
  meta: { key: string; value: unknown }
}

const CHARACTERS = 'characters'
const CURSOR = 'characters.cursor'
// До D68 база была одна на браузер; её анкеты достаются первой вошедшей учётке.
const LEGACY_DB = 'dataslate'

// Через ядро: лист старой версии мигрирует при чтении.
export function parseStoredCharacter(json: string): CharacterFile | undefined {
  const r = readCharacterFile(json)
  return r.json == null ? undefined : JSON.parse(r.json)
}

function openStore(name: string) {
  return openDB<DataslateDb>(name, 2, {
    async upgrade(db, oldVersion, _newVersion, tx) {
      if (oldVersion < 1) {
        db.createObjectStore('documents', { keyPath: ['collection', 'key'] }).createIndex('collection', 'collection')
      }
      if (oldVersion < 2) {
        db.createObjectStore('meta')
        for (let cursor = await tx.objectStore('documents').openCursor(); cursor; cursor = await cursor.continue()) {
          if (cursor.value.deleted) await cursor.delete()
          else await cursor.update({ ...cursor.value, serverRevision: 0, dirty: true })
        }
      }
    },
  })
}

async function adoptLegacy(db: IDBPDatabase<DataslateDb>) {
  if (!(await indexedDB.databases?.())?.some(d => d.name === LEGACY_DB)) return
  const legacy = await openStore(LEGACY_DB)
  const docs = await legacy.getAll('documents')
  legacy.close()
  const tx = db.transaction('documents', 'readwrite')
  for (const doc of docs) {
    if (!(await tx.store.get([doc.collection, doc.key]))) await tx.store.put(doc)
  }
  await tx.done
  await deleteDB(LEGACY_DB)
}

// Своя база на учётку: на общем устройстве анкеты разных людей не смешиваются и не уходят на чужой аккаунт.
export function createCharacterStore(userId: string, name = `dataslate:${userId}`) {
  let db: Promise<IDBPDatabase<DataslateDb>> | undefined
  const open = () => (db ??= openStore(name).then(async (d) => {
    if (name !== LEGACY_DB) await adoptLegacy(d).catch(e => console.error('legacy sheets', e))
    return d
  }))

  async function update(id: string, change: (doc: StoredDocument | undefined) => StoredDocument | null | undefined) {
    const tx = (await open()).transaction('documents', 'readwrite')
    const next = change(await tx.store.get([CHARACTERS, id]))
    if (next === null) await tx.store.delete([CHARACTERS, id])
    else if (next) await tx.store.put(next)
    await tx.done
    return next
  }

  return {
    async list(): Promise<CharacterFile[]> {
      const docs = await (await open()).getAllFromIndex('documents', 'collection', CHARACTERS)
      return docs.filter(d => !d.deleted).map(d => parseStoredCharacter(d.json)).filter(c => c !== undefined)
    },

    async get(id: string): Promise<CharacterFile | undefined> {
      const doc = await (await open()).get('documents', [CHARACTERS, id])
      return doc && !doc.deleted ? parseStoredCharacter(doc.json) : undefined
    },

    async document(id: string) {
      return (await open()).get('documents', [CHARACTERS, id])
    },

    // Не отправленные на сервер, в том числе удаления.
    async pending() {
      const docs = await (await open()).getAllFromIndex('documents', 'collection', CHARACTERS)
      return docs.filter(d => d.dirty)
    },

    // Правка на устройстве.
    async put(file: CharacterFile) {
      await update(file.id, existing => ({
        collection: CHARACTERS,
        key: file.id,
        json: JSON.stringify(file),
        revision: (existing?.revision ?? 0) + 1,
        updatedAt: file.updatedAt,
        deleted: false,
        serverRevision: existing?.serverRevision ?? 0,
        dirty: true,
      }))
    },

    // Удаление на устройстве: надгробие, пока не дойдёт до сервера; не было на сервере — сразу.
    async remove(id: string) {
      await update(id, (existing) => {
        if (!existing || existing.deleted) return undefined
        if (!existing.serverRevision) return null
        return { ...existing, json: '', revision: existing.revision + 1, updatedAt: new Date().toISOString(), deleted: true, dirty: true }
      })
    },

    // Сервер принял версию с локальной ревизией sent. Правки, сделанные пока шёл запрос, остаются неотправленными.
    async markSent(id: string, sent: number, serverRevision: number) {
      await update(id, (doc) => {
        if (!doc) return undefined
        const dirty = doc.revision !== sent
        if (doc.deleted && !dirty) return null
        return { ...doc, serverRevision, dirty }
      })
    },

    // Версия с сервера; null — удалена. Без force не трогает неотправленное — это конфликт, его решает пользователь.
    async applyRemote(id: string, serverRevision: number, file: CharacterFile | null, force = false) {
      const applied = await update(id, (doc) => {
        if (doc?.dirty && !force) return undefined
        if (!file) return doc ? null : undefined
        return {
          collection: CHARACTERS,
          key: id,
          json: JSON.stringify(file),
          revision: (doc?.revision ?? 0) + 1,
          updatedAt: file.updatedAt,
          deleted: false,
          serverRevision,
          dirty: false,
        }
      })
      return applied !== undefined
    },

    // «Оставить мою»: правка теперь основана на версии сервера и уйдёт поверх неё.
    async rebase(id: string, serverRevision: number) {
      await update(id, doc => doc && { ...doc, serverRevision, dirty: true })
    },

    // Без следа и без отправки: id занят на сервере (анкета уже сохранена копией) или удаление не нужно передавать.
    async forget(id: string) {
      await update(id, doc => (doc ? null : undefined))
    },

    async cursor(): Promise<number> {
      return ((await (await open()).get('meta', CURSOR)) as number | undefined) ?? 0
    },

    async setCursor(cursor: number) {
      await (await open()).put('meta', cursor, CURSOR)
    },

    async close() {
      if (db) (await db).close()
      db = undefined
    },
  }
}

export type CharacterStore = ReturnType<typeof createCharacterStore>
