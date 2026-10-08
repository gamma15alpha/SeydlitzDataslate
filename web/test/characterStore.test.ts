import 'fake-indexeddb/auto'
import { afterEach, describe, expect, it } from 'vitest'
import { newCharacterFile } from 'dataslate-core'
import { createCharacterStore, parseStoredCharacter } from '../app/utils/characterStore'
import { authorName, type CharacterFile } from '../app/utils/dh1Content'

let n = 0
let store = createCharacterStore(`test-${n}`)
afterEach(async () => {
  await store.close()
  store = createCharacterStore(`test-${++n}`)
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

  it('удаление — надгробие: анкеты нет ни в списке, ни по id', async () => {
    const a = create('Вэйн')
    await store.put(a)
    await store.remove(a.id)
    expect(await store.list()).toEqual([])
    expect(await store.get(a.id)).toBeUndefined()
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
