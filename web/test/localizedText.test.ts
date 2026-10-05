import { describe, expect, it } from 'vitest'
import { allTexts, pickText, termParts } from '../app/utils/localizedText'

describe('pickText', () => {
  const text = { ru: 'Ловкость', en: 'Agility', 'en-GB': 'Agility (UK)' }

  it('строка — оригинал, на любом языке', () => {
    expect(pickText('Ловкость', 'en')).toBe('Ловкость')
  })

  it('точный язык, затем основной', () => {
    expect(pickText(text, 'en-GB')).toBe('Agility (UK)')
    expect(pickText(text, 'en-US')).toBe('Agility')
  })

  it('нет языка — ru, затем en, затем первый', () => {
    expect(pickText(text, 'de')).toBe('Ловкость')
    expect(pickText({ en: 'Agility', fr: 'Agilité' }, 'de')).toBe('Agility')
    expect(pickText({ fr: 'Agilité' }, 'ru')).toBe('Agilité')
  })
})

describe('termParts', () => {
  const ag = { id: 'ag', abbreviation: 'Ag', name: { ru: 'Ловкость', en: 'Agility' } }

  it('русский контент — с английским дублем', () => {
    expect(termParts(ag, 'ru')).toEqual({ abbreviation: 'Ag', name: 'Ловкость', english: 'Agility' })
  })

  it('английский контент — без дубля', () => {
    expect(termParts(ag, 'en-GB')).toEqual({ abbreviation: 'Ag', name: 'Agility', english: undefined })
    // Даже если британское название отличается от общего английского.
    expect(termParts({ id: 'x', name: { ru: 'Броня', en: 'Armor', 'en-GB': 'Armour' } }, 'en-GB').english).toBeUndefined()
  })

  it('нет английского названия — нет дубля', () => {
    expect(termParts({ id: 'x', name: 'Улей Примус' }, 'ru').english).toBeUndefined()
  })
})

describe('allTexts', () => {
  it('оригинал первым', () => {
    expect(allTexts({ en: 'A', ru: 'Б' })).toEqual([['ru', 'Б'], ['en', 'A']])
    expect(allTexts('Б')).toEqual([['ru', 'Б']])
  })
})
