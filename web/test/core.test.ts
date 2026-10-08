import { describe, expect, it } from 'vitest'
import { Dh1Session, pickText, rollDice, testD100, JsModifier } from '../../shared/build/dist/js/productionLibrary/dataslate-core.mjs'
import pack from '../../schemas/examples/dh1-mock.content.json?raw'
import character from '../../schemas/examples/dh1.character.json?raw'
import { pickText as pickTextTs } from '../app/utils/localizedText'

describe('ядро на Kotlin/JS', () => {
  const session = new Dh1Session(pack)

  it('считает анкету из примеров', () => {
    const r = session.evaluate(character, 'ru')
    const int = r.characteristics.find((c) => c.id === 'int')!
    expect(int.value).toBe(40)
    expect(int.contributions.map((c) => [c.source, c.value])).toEqual([['base', 35], ['advances', 5]])
    expect(r.bonuses.find((b) => b.id === 'int')!.value).toBe(4)
    expect(r.characteristics.find((c) => c.id === 'per')!.label).toBe('Per')
    expect(r.skills.map((s) => s.label)).toContain('Знание (Ереси сектора)')
    expect(r.diagnostics).toEqual([])
  })

  it('временный модификатор виден как источник', () => {
    const r = session.evaluate(character, 'en', [new JsModifier('fear', 'int', -10)])
    expect(r.characteristics.find((c) => c.id === 'int')!.value).toBe(30)
  })

  it('pickText совпадает с реализацией на TS', () => {
    const cases: [unknown, string][] = [
      ['Ловкость', 'en'],
      [{ ru: 'Ловкость', en: 'Agility', 'en-GB': 'UK' }, 'en-GB'],
      [{ ru: 'Ловкость', en: 'Agility' }, 'en-US'],
      [{ en: 'Agility', fr: 'Agilité' }, 'de'],
      [{ fr: 'Agilité' }, 'ru'],
    ]
    for (const [text, lang] of cases) {
      expect(pickText(JSON.stringify(text), lang)).toBe(pickTextTs(text as never, lang))
    }
  })

  it('броски: то же зерно — те же кости, что на JVM', () => {
    expect(rollDice('3d10', 42)!.dice).toEqual([4, 1, 2])
    expect(rollDice('2d')).toBeNull()
    const t = testD100(45, 12)
    expect([t.success, t.degrees]).toEqual([true, 3])
  })
})
