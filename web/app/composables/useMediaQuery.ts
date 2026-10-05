export function useMediaQuery(query: string) {
  const list = window.matchMedia(query)
  const matches = ref(list.matches)
  const update = (e: MediaQueryListEvent) => (matches.value = e.matches)
  list.addEventListener('change', update)
  onScopeDispose(() => list.removeEventListener('change', update))
  return matches
}
