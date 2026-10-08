// schemas/dh1-content.schema.json
export interface Dh1Content {
  characteristics?: CatalogEntry[]
  rules?: CatalogEntry[]
  items?: CatalogEntry[]
  homeworlds?: CatalogEntry[]
  careers?: (CatalogEntry & { ranks?: CatalogEntry[] })[]
  skills?: (CatalogEntry & {
    characteristic: string
    kind: 'basic' | 'advanced'
    specializations?: boolean
    specializationOptions?: CatalogEntry[]
  })[]
}

// schemas/dh1-sheet.schema.json, sheetVersion 4
export type Choice = { id: string } | { custom: string }

// player из старых файлов ядро сохраняет, клиент не трогает: игрок — author.
export interface Dh1Sheet {
  homeworld?: Choice
  career?: Choice
  rank?: Choice
  characteristics?: Record<string, { base?: number | null; advances?: number; unnatural?: number }>
  modifiers?: { target: string; value: number; reason?: string }[]
  skills?: { skill: string; specialization?: Choice; training?: number }[]
}

// schemas/character.schema.json; проверка и миграции — в ядре (readCharacterFile).
export interface CharacterFile {
  format: 'seydlitz.character'
  formatVersion: number
  id: string
  system: 'dh1'
  sheetVersion: number
  name: string
  createdAt: string
  updatedAt: string
  // id — учётка; name — имя на момент создания.
  author?: { id: string; name: string }
  sheet: Dh1Sheet
}

// Своя анкета — текущее имя учётки (могли сменить), чужая — сохранённое.
export function authorName(character: CharacterFile, me?: { id: string; displayName: string } | null) {
  const author = character.author
  if (!author) return undefined
  return author.id === me?.id ? me.displayName : author.name
}

export function findEntry(content: Dh1Content, ref: string): CatalogEntry | undefined {
  const [section, id] = ref.split('/')
  const byId = (list?: CatalogEntry[]) => list?.find(e => e.id === id)
  switch (section) {
    case 'ranks':
      return content.careers?.map(c => byId(c.ranks)).find(Boolean)
    case 'specializations':
      return content.skills?.map(s => byId(s.specializationOptions)).find(Boolean)
    case 'characteristics':
    case 'rules':
    case 'items':
    case 'homeworlds':
    case 'careers':
    case 'skills':
      return byId(content[section])
    default:
      return undefined
  }
}
