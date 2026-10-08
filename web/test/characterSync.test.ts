import 'fake-indexeddb/auto'
import { afterEach, describe, expect, it } from 'vitest'
import createClient from 'openapi-fetch'
import { newCharacterFile } from 'dataslate-core'
import type { paths } from '../app/api/schema'
import { createCharacterStore, type CharacterStore } from '../app/utils/characterStore'
import { keepBoth, keepMine, sameCharacter, SyncError, syncCharacters, takeServer } from '../app/utils/characterSync'
import type { CharacterFile } from '../app/utils/dh1Content'

// Сервер в памяти с поведением server/internal/character: ревизии, If-Match, надгробия, курсор.
class FakeServer {
  seq = 0
  rows = new Map<string, { revision: number; seq: number; data: unknown | null }>()
  // id анкет другого пользователя
  foreign = new Set<string>()
  offline = false
  // Обработать запрос, но «потерять» ответ.
  dropNextResponse = false

  stored(id: string, withData: boolean) {
    const row = this.rows.get(id)!
    return { id, revision: row.revision, deleted: row.data === null, updatedAt: '2026-10-08T10:00:00Z', ...(withData && row.data ? { character: row.data } : {}) }
  }

  fetch = async (request: Request) => {
    if (this.offline) throw new TypeError('Failed to fetch')
    const url = new URL(request.url)
    const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
    if (request.method === 'GET') {
      const since = Number(url.searchParams.get('since') ?? 0)
      const changes = [...this.rows.entries()]
        .filter(([, r]) => r.seq > since && (since > 0 || r.data !== null))
        .sort(([, a], [, b]) => a.seq - b.seq)
        .map(([id]) => this.stored(id, true))
      return json(200, { cursor: this.seq, changes })
    }
    const id = url.pathname.split('/').pop()!
    if (this.foreign.has(id)) return json(409, { code: 'id_taken', error: '' })
    const ifMatch = request.headers.get('If-Match')
    const base = ifMatch ? Number(ifMatch.replaceAll('"', '')) : 0
    const data = request.method === 'PUT' ? await request.json() : null
    const row = this.rows.get(id)
    if (!row) {
      if (base || !data) return json(404, { code: 'not_found', error: '' })
      this.rows.set(id, { revision: 1, seq: ++this.seq, data })
    } else if (row.revision !== base) {
      return json(412, this.stored(id, true))
    } else if (!(row.data === null && data === null)) {
      this.rows.set(id, { revision: row.revision + 1, seq: ++this.seq, data })
    }
    if (this.dropNextResponse) {
      this.dropNextResponse = false
      throw new TypeError('connection reset')
    }
    return json(base ? 200 : 201, this.stored(id, false))
  }
}

let server: FakeServer
let n = 0
const stores: CharacterStore[] = []
const device = () => {
  const s = createCharacterStore('user', `sync-${n++}`)
  stores.push(s)
  return s
}
const api = () => createClient<paths>({ baseUrl: 'http://test', fetch: server.fetch })
const sync = (store: CharacterStore, busy: (id: string) => boolean = () => false) => syncCharacters(store, api(), busy)
const create = (name: string): CharacterFile => JSON.parse(newCharacterFile(name, '01a10fdd-b783-7016-adf1-bdcf1a01711e', 'Сейдлиц'))

server = new FakeServer()
afterEach(async () => {
  server = new FakeServer()
  for (const s of stores.splice(0)) await s.close()
})

describe('characterSync', () => {
  it('правка доходит до другого устройства', async () => {
    const [a, b] = [device(), device()]
    const sheet = create('Вэйн')
    await a.put(sheet)
    await sync(a)
    expect(await a.pending()).toEqual([])

    expect(await sync(b)).toMatchObject({ changed: [sheet.id], conflicts: [] })
    await b.put({ ...sheet, name: 'Вэйн II' })
    await sync(b)

    expect((await sync(a)).changed).toEqual([sheet.id])
    expect((await a.get(sheet.id))?.name).toBe('Вэйн II')
    // Своя запись, вернувшаяся в списке изменений, — не изменение.
    expect((await sync(b)).changed).toEqual([])
  })

  it('конфликт: оставить свою, взять с сервера, сохранить обе', async () => {
    const [a, b] = [device(), device()]
    const sheet = create('Вэйн')
    await a.put(sheet)
    await sync(a)
    await sync(b)
    await a.put({ ...sheet, name: 'A' })
    await b.put({ ...sheet, name: 'B' })
    await sync(a)

    const [conflict] = (await sync(b)).conflicts
    expect(conflict).toMatchObject({ id: sheet.id, local: { name: 'B' }, server: { name: 'A' }, serverRevision: 2 })
    // Неотправленное при загрузке не затёрто.
    expect((await b.get(sheet.id))?.name).toBe('B')

    await keepMine(b, conflict!)
    expect((await sync(b)).conflicts).toEqual([])
    await sync(a)
    expect((await a.get(sheet.id))?.name).toBe('B')

    await a.put({ ...sheet, name: 'A2' })
    await b.put({ ...sheet, name: 'B2' })
    await sync(a)
    await takeServer(b, (await sync(b)).conflicts[0]!)
    expect((await b.get(sheet.id))?.name).toBe('A2')
    expect(await b.pending()).toEqual([])

    await a.put({ ...sheet, name: 'A3' })
    await b.put({ ...sheet, name: 'B3' })
    await sync(a)
    const copy = await keepBoth(b, (await sync(b)).conflicts[0]!)
    await sync(b)
    await sync(a)
    expect((await a.list()).map(c => c.name).sort()).toEqual(['A3', 'B3'])
    expect(copy.id).not.toBe(sheet.id)
  })

  it('удаление доходит до других; удалённая там, изменённая здесь — конфликт', async () => {
    const [a, b] = [device(), device()]
    const sheet = create('Вэйн')
    await a.put(sheet)
    await sync(a)
    await sync(b)

    await a.remove(sheet.id)
    await b.put({ ...sheet, name: 'Правка' })
    await sync(a)
    expect(await a.document(sheet.id)).toBeUndefined()

    const [conflict] = (await sync(b)).conflicts
    expect(conflict).toMatchObject({ local: { name: 'Правка' }, server: null })
    await keepMine(b, conflict!)
    await sync(b)
    await sync(a)
    expect((await a.get(sheet.id))?.name).toBe('Правка')

    await b.remove(sheet.id)
    await sync(b)
    expect((await sync(a)).changed).toEqual([sheet.id])
    expect(await a.list()).toEqual([])
  })

  it('потерянный ответ на запись — не конфликт', async () => {
    const a = device()
    const sheet = create('Вэйн')
    await a.put(sheet)
    server.dropNextResponse = true
    await expect(sync(a)).rejects.toMatchObject({ offline: true })
    expect(await a.pending()).toHaveLength(1)
    expect(await sync(a)).toMatchObject({ conflicts: [] })
    expect(await a.pending()).toEqual([])
  })

  it('id занят другим пользователем — своя копией под новым id', async () => {
    const a = device()
    const sheet = create('Вэйн')
    server.foreign.add(sheet.id)
    await a.put(sheet)
    const { renamed } = await sync(a)
    const copyId = renamed[sheet.id]!
    expect(copyId).toBeTruthy()
    expect(await a.document(sheet.id)).toBeUndefined()
    expect((await a.get(copyId))?.name).toBe('Вэйн')
    expect(server.rows.has(copyId)).toBe(true)
  })

  it('сервер без анкеты (база пересоздана) — создаём заново', async () => {
    const a = device()
    const sheet = create('Вэйн')
    await a.put(sheet)
    await sync(a)
    server.rows.clear()
    await a.put({ ...sheet, name: 'После сброса' })
    await sync(a)
    expect(server.rows.get(sheet.id)).toMatchObject({ revision: 1, data: { name: 'После сброса' } })
  })

  it('без связи — ошибка offline, изменения ждут', async () => {
    const a = device()
    await a.put(create('Вэйн'))
    server.offline = true
    const error = await sync(a).catch(e => e)
    expect(error).toBeInstanceOf(SyncError)
    expect(error.offline).toBe(true)
    expect(await a.pending()).toHaveLength(1)
  })

  it('открытую правку (черновик) загрузка не трогает', async () => {
    const [a, b] = [device(), device()]
    const sheet = create('Вэйн')
    await a.put(sheet)
    await sync(a)
    await sync(b)
    await a.put({ ...sheet, name: 'A' })
    await sync(a)
    expect((await sync(b, id => id === sheet.id)).changed).toEqual([])
    expect((await b.get(sheet.id))?.name).toBe('Вэйн')
  })

  it('сравнение не зависит от порядка ключей (jsonb)', () => {
    expect(sameCharacter({ a: 1, b: { c: [1, { d: 2, e: 3 }] } }, { b: { c: [1, { e: 3, d: 2 }] }, a: 1 })).toBe(true)
    expect(sameCharacter({ a: 1 }, { a: 2 })).toBe(false)
    expect(sameCharacter(null, null)).toBe(true)
  })
})
