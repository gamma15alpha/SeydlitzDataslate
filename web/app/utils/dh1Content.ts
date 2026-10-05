// Содержимое пакета DH1 — schemas/dh1-content.schema.json.
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

// Лист DH1 (sheetVersion 4) — schemas/dh1-sheet.schema.json.
export type Choice = { id: string } | { custom: string }

export interface Dh1Sheet {
  player?: string
  homeworld?: Choice
  career?: Choice
  rank?: Choice
  characteristics?: Record<string, { base?: number | null; advances?: number; unnatural?: number }>
  skills?: { skill: string; specialization?: Choice; training?: number }[]
}

// Ссылка dataslate:<раздел>/<id>; ранги ищутся во всех карьерах, специализации — во всех навыках.
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
