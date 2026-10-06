// schemas/content.schema.json
export type LocalizedText = string | Record<string, string>

const ORIGINAL = 'ru'

// Порядок — из схемы, Android выбирает так же.
export function pickText(text: LocalizedText, lang: string): string {
  if (typeof text === 'string') return text
  const base = lang.split('-')[0]!
  return text[lang] ?? text[base] ?? text[ORIGINAL] ?? text.en ?? Object.values(text)[0] ?? ''
}

export interface CatalogEntry {
  id: string
  name: LocalizedText
  abbreviation?: LocalizedText
  description?: LocalizedText
}

export function termParts(entry: CatalogEntry, lang: string) {
  const name = pickText(entry.name, lang)
  const english = lang.split('-')[0] === 'en' ? undefined : pickText(entry.name, 'en')
  return {
    abbreviation: entry.abbreviation ? pickText(entry.abbreviation, lang) : undefined,
    name,
    english: english && english !== name ? english : undefined,
  }
}

export function allTexts(text: LocalizedText): [lang: string, text: string][] {
  if (typeof text === 'string') return [[ORIGINAL, text]]
  return Object.entries(text).sort(([a], [b]) => Number(b === ORIGINAL) - Number(a === ORIGINAL))
}
