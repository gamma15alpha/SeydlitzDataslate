// Настройки устройства в localStorage. Он может быть недоступен (приватный режим) — тогда выбор просто не запоминается.
export function readSetting(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeSetting(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // не запоминаем
  }
}
