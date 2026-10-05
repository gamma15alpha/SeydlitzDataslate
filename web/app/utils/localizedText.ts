// localizedText из schemas/content.schema.json: строка (русский оригинал) или переводы по тегу BCP 47.
export type LocalizedText = string | Record<string, string>

const ORIGINAL = 'ru'

// Правило из схемы: язык → его основной язык (en-GB → en) → ru → en → первый по порядку.
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

// Ag · Ловкость · Agility: английский дубль — только если язык контента не английский и название другое.
export function termParts(entry: CatalogEntry, lang: string) {
  const name = pickText(entry.name, lang)
  const english = lang.split('-')[0] === 'en' ? undefined : pickText(entry.name, 'en')
  return {
    abbreviation: entry.abbreviation ? pickText(entry.abbreviation, lang) : undefined,
    name,
    english: english && english !== name ? english : undefined,
  }
}

// Все варианты текста — для сверки с оригиналом без смены языка; оригинал первым.
export function allTexts(text: LocalizedText): [lang: string, text: string][] {
  if (typeof text === 'string') return [[ORIGINAL, text]]
  return Object.entries(text).sort(([a], [b]) => Number(b === ORIGINAL) - Number(a === ORIGINAL))
}
