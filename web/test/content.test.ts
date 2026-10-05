import { describe, expect, it } from 'vitest'
import pack from '../../schemas/examples/dh1-mock.content.json'
import { findEntry, type Dh1Content } from '../app/utils/dh1Content'
import { renderMarkdown } from '../app/utils/markdown'

const content = pack.content as Dh1Content

describe('findEntry', () => {
  it('разделы, вложенные ранги и специализации', () => {
    expect(findEntry(content, 'characteristics/per')?.id).toBe('per')
    expect(findEntry(content, 'ranks/test-scribe-2')?.id).toBe('test-scribe-2')
    expect(findEntry(content, 'specializations/test-lore-archives')?.id).toBe('test-lore-archives')
  })

  it('нет записи или раздела — undefined', () => {
    expect(findEntry(content, 'skills/missing')).toBeUndefined()
    expect(findEntry(content, 'secrets/test-notice')).toBeUndefined()
  })

  it('все ссылки dataslate: в мок-пакете ведут на существующие записи', () => {
    const refs = [...JSON.stringify(pack).matchAll(/dataslate:([a-z]+\/[a-z0-9-]+)/g)].map(m => m[1]!)
    expect(refs.length).toBeGreaterThan(0)
    for (const ref of refs) expect(findEntry(content, ref), ref).toBeDefined()
  })
})

describe('renderMarkdown', () => {
  it('внутренние ссылки — data-ref, внешние — в новой вкладке', () => {
    const html = renderMarkdown('[Ловкость](dataslate:characteristics/ag) [сайт](https://example.org)')
    expect(html).toContain('<a href="#" data-ref="characteristics/ag">Ловкость</a>')
    expect(html).toContain('target="_blank" rel="noopener noreferrer"')
  })

  it('сырой HTML и javascript: не проходят', () => {
    const html = renderMarkdown('<script>alert(1)</script> [x](javascript:alert(1)) <img src=x onerror=alert(1)>')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('<img')
    expect(html).not.toContain('href="javascript')
  })

  it('картинки пакета — через asset, без него — подпись', () => {
    expect(renderMarkdown('![Схема](assets/a.png)', p => `/packs/dh1/${p}`)).toContain('src="/packs/dh1/assets/a.png" alt="Схема"')
    expect(renderMarkdown('![Схема](assets/a.png)')).toContain('<span class="missing-image">[Схема]</span>')
  })

  it('таблицы GFM — в прокручиваемой обёртке', () => {
    expect(renderMarkdown('| a | b |\n|---|---|\n| 1 | 2 |')).toMatch(/^<div class="table-scroll"><table>[\s\S]*<\/table><\/div>\n$/)
  })
})
