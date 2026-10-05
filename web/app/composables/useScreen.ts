// Экран корпуса: приложение живёт внутри него. У .screen — container-type: size, поэтому position: fixed
// внутри отсчитывается от экрана, а не от окна браузера; размеры для расчётов — отсюда, а не window.inner*.
const element = shallowRef<HTMLElement | null>(null)
const size = reactive({ width: 0, height: 0 })
let observer: ResizeObserver | undefined

export function registerScreen(el: HTMLElement | null) {
  if (el === element.value) return
  observer?.disconnect()
  element.value = el
  if (!el) return
  // Сразу, а не по первому колбэку наблюдателя: страница уже читает размер при открытии.
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
