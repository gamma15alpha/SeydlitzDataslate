import pack from '../../../schemas/examples/dh1-mock.content.json'

// Мок-пакет контента — пока нет каталога (срез 4); анкеты — из хранилища (D66).
export const demoContent = pack.content as Dh1Content

export const SYSTEM_NAMES: Record<string, string> = { dh1: 'Dark Heresy 1e' }
