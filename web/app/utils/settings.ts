// В приватном режиме localStorage может быть недоступен.
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
