// Приложение живёт на экране корпуса: размеры — отсюда, а не window.inner*.
const element = shallowRef<HTMLElement | null>(null)
const size = reactive({ width: 0, height: 0 })
let observer: ResizeObserver | undefined

export function registerScreen(el: HTMLElement | null) {
  if (el === element.value) return
  observer?.disconnect()
  element.value = el
  if (!el) return
  // Страница читает размер сразу, не дожидаясь наблюдателя.
  size.width = el.clientWidth
  size.height = el.clientHeight
  observer = new ResizeObserver(() => {
    size.width = el.clientWidth
    size.height = el.clientHeight
  })
  observer.observe(el)
}

export function useScreen() {
  return { element, size: readonly(size) }
}
