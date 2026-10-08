import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { readCharacterFile } from 'dataslate-core'
import type { CharacterFile } from './dh1Content'

// D25: документы по (collection, key); revision и надгробия — под синхронизацию.
export interface StoredDocument {
  collection: string
  key: string
  json: string
  revision: number
  updatedAt: string
  deleted: boolean
}

interface DataslateDb extends DBSchema {
  documents: { key: [string, string]; value: StoredDocument; indexes: { collection: string } }
}

const CHARACTERS = 'characters'

// Через ядро: лист старой версии мигрирует при чтении.
export function parseStoredCharacter(json: string): CharacterFile | undefined {
  const r = readCharacterFile(json)
  return r.json == null ? undefined : JSON.parse(r.json)
}

export function createCharacterStore(name = 'dataslate') {
  let db: Promise<IDBPDatabase<DataslateDb>> | undefined
  const open = () => (db ??= openDB<DataslateDb>(name, 1, {
    upgrade(db) {
      db.createObjectStore('documents', { keyPath: ['collection', 'key'] }).createIndex('collection', 'collection')
    },
  }))

  return {
    async list(): Promise<CharacterFile[]> {
      const docs = await (await open()).getAllFromIndex('documents', 'collection', CHARACTERS)
      return docs.filter(d => !d.deleted).map(d => parseStoredCharacter(d.json)).filter(c => c !== undefined)
    },

    async get(id: string): Promise<CharacterFile | undefined> {
      const doc = await (await open()).get('documents', [CHARACTERS, id])
      return doc && !doc.deleted ? parseStoredCharacter(doc.json) : undefined
    },

    async put(file: CharacterFile) {
      const tx = (await open()).transaction('documents', 'readwrite')
      const existing = await tx.store.get([CHARACTERS, file.id])
      await tx.store.put({
        collection: CHARACTERS,
        key: file.id,
        json: JSON.stringify(file),
        revision: (existing?.revision ?? 0) + 1,
        updatedAt: file.updatedAt,
        deleted: false,
      })
      await tx.done
    },

    async remove(id: string) {
      const tx = (await open()).transaction('documents', 'readwrite')
      const existing = await tx.store.get([CHARACTERS, id])
      if (existing && !existing.deleted) {
        await tx.store.put({ ...existing, json: '', revision: existing.revision + 1, updatedAt: new Date().toISOString(), deleted: true })
      }
      await tx.done
    },

    async close() {
      if (db) (await db).close()
      db = undefined
    },
  }
}

export type CharacterStore = ReturnType<typeof createCharacterStore>
