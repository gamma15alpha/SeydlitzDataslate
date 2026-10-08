import 'fake-indexeddb/auto'
import { afterEach, describe, expect, it } from 'vitest'
import { newCharacterFile } from 'dataslate-core'
import { createCharacterStore, parseStoredCharacter } from '../app/utils/characterStore'
import { authorName, type CharacterFile } from '../app/utils/dh1Content'

let n = 0
let store = createCharacterStore('user', `test-${n}`)
afterEach(async () => {
  await store.close()
  store = createCharacterStore('user', `test-${++n}`)
})

const create = (name: string): CharacterFile => JSON.parse(newCharacterFile(name, '01a10fdd-b783-7016-adf1-bdcf1a01711e', 'Сейдлиц'))

describe('characterStore', () => {
  it('сохраняет и читает анкеты', async () => {
    const a = create('Вэйн')
    const b = create('Сарейн')
    await store.put(a)
    await store.put(b)
    expect((await store.list()).map(c => c.name).sort()).toEqual(['Вэйн', 'Сарейн'])
    expect(await store.get(a.id)).toEqual(a)
  })

  it('правка заменяет документ, ревизия растёт', async () => {
    const a = create('Вэйн')
    await store.put(a)
    await store.put({ ...a, sheet: { homeworld: { custom: 'Улей' } } })
    expect((await store.get(a.id))?.sheet.homeworld).toEqual({ custom: 'Улей' })
    expect(await store.list()).toHaveLength(1)
  })

  it('удаление: не бывшая на сервере исчезает сразу, бывшая — надгробие до отправки', async () => {
    const a = create('Вэйн')
    await store.put(a)
    await store.remove(a.id)
    expect(await store.document(a.id)).toBeUndefined()

    await store.put(a)
    await store.markSent(a.id, 1, 1)
    await store.remove(a.id)
    expect(await store.list()).toEqual([])
    expect(await store.get(a.id)).toBeUndefined()
    expect(await store.pending()).toMatchObject([{ key: a.id, deleted: true, serverRevision: 1 }])
    await store.markSent(a.id, 2, 2)
    expect(await store.document(a.id)).toBeUndefined()
  })

  it('правка во время отправки остаётся неотправленной', async () => {
    const a = create('Вэйн')
    await store.put(a)
    await store.put({ ...a, name: 'Вэйн II' })
    await store.markSent(a.id, 1, 5)
    expect(await store.document(a.id)).toMatchObject({ dirty: true, serverRevision: 5 })
    await store.markSent(a.id, 2, 6)
    expect(await store.pending()).toEqual([])
  })

  it('версия с сервера не затирает неотправленное без force', async () => {
    const a = create('Вэйн')
    await store.put(a)
    expect(await store.applyRemote(a.id, 3, { ...a, name: 'С сервера' })).toBe(false)
    expect((await store.get(a.id))?.name).toBe('Вэйн')
    expect(await store.applyRemote(a.id, 3, { ...a, name: 'С сервера' }, true)).toBe(true)
    expect(await store.document(a.id)).toMatchObject({ dirty: false, serverRevision: 3 })
    expect(await store.applyRemote(a.id, 4, null)).toBe(true)
    expect(await store.document(a.id)).toBeUndefined()
  })

  it('курсор и перенос анкет из общей базы до D68', async () => {
    expect(await store.cursor()).toBe(0)
    await store.setCursor(7)
    expect(await store.cursor()).toBe(7)

    const legacy = createCharacterStore('x', 'dataslate')
    await legacy.put(create('Старая'))
    await legacy.close()
    const mine = createCharacterStore('me', `test-legacy-${n}`)
    expect((await mine.list()).map(c => c.name)).toEqual(['Старая'])
    expect(await mine.pending()).toHaveLength(1)
    expect((await indexedDB.databases()).some(d => d.name === 'dataslate')).toBe(false)
    await mine.close()
  })

  it('старый лист мигрирует при чтении', () => {
    const old = { ...create('Вэйн'), sheetVersion: 3, sheet: { career: 'Писарь' } }
    expect(parseStoredCharacter(JSON.stringify(old))?.sheet.career).toEqual({ custom: 'Писарь' })
  })

  it('игрок — автор: своя анкета с текущим именем учётки, чужая — с сохранённым', () => {
    const a = create('Вэйн')
    expect(a.author).toEqual({ id: '01a10fdd-b783-7016-adf1-bdcf1a01711e', name: 'Сейдлиц' })
    expect(authorName(a, { id: a.author!.id, displayName: 'Новое имя' })).toBe('Новое имя')
    expect(authorName(a, { id: 'other', displayName: 'Другой' })).toBe('Сейдлиц')
    expect(authorName({ ...a, author: undefined }, null)).toBeUndefined()
  })
})
