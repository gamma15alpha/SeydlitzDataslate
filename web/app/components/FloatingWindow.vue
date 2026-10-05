<script setup lang="ts">
const props = defineProps<{ x: number; y: number; z: number }>()
const emit = defineEmits<{ move: [x: number, y: number]; focus: [] }>()
const MIN_WIDTH = 260
const MIN_HEIGHT = 160
const width = ref(380)
const height = ref(Math.min(480, Math.round(window.innerHeight * 0.7)))
const dragging = ref(false)

// Указатель (мышь или палец) захватывается элементом, пока не отпустят. Пока тянут — класс на <html>:
// курсор на всей странице не мигает, текст под окном не выделяется.
function track(e: PointerEvent, cursorClass: string, onMove: (ev: PointerEvent) => void, onEnd?: () => void) {
  e.preventDefault()
  const handle = e.currentTarget as HTMLElement
  handle.setPointerCapture(e.pointerId)
  document.documentElement.classList.add(cursorClass)
  const stop = () => {
    document.documentElement.classList.remove(cursorClass)
    handle.removeEventListener('pointermove', onMove)
    handle.removeEventListener('pointerup', stop)
    handle.removeEventListener('pointercancel', stop)
    onEnd?.()
  }
  handle.addEventListener('pointermove', onMove)
  handle.addEventListener('pointerup', stop)
  handle.addEventListener('pointercancel', stop)
}

// Перетаскивание за заголовок: содержимое вызывает grab на pointerdown. Заголовок не уходит за край экрана.
function grab(e: PointerEvent) {
  if ((e.target as HTMLElement).closest('button, a, input, select')) return
  const dx = e.clientX - props.x
  const dy = e.clientY - props.y
  dragging.value = true
  track(e, 'window-dragging', ev => emit('move',
    Math.min(Math.max(ev.clientX - dx, 48 - width.value), window.innerWidth - 48),
    Math.min(Math.max(ev.clientY - dy, 0), window.innerHeight - 48),
  ), () => (dragging.value = false))
}

// Свой уголок вместо CSS resize: тот не работает пальцем.
function resize(e: PointerEvent) {
  const startX = e.clientX
  const startY = e.clientY
  const startW = width.value
  const startH = height.value
  track(e, 'window-resizing', (ev) => {
    width.value = Math.min(Math.max(startW + ev.clientX - startX, MIN_WIDTH), window.innerWidth - props.x)
    height.value = Math.min(Math.max(startH + ev.clientY - startY, MIN_HEIGHT), window.innerHeight - props.y)
  })
}
</script>

<template>
  <div
    class="floating-window"
    :style="{ left: `${x}px`, top: `${y}px`, zIndex: z, width: `${width}px`, height: `${height}px` }"
    @pointerdown="emit('focus')"
  >
    <slot :grab="grab" :dragging="dragging" />
    <div class="resize-grip" aria-hidden="true" @pointerdown="resize" />
  </div>
</template>

<style scoped>
.floating-window {
  position: fixed;
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.85), 0 0 12px var(--phosphor-glow);
}

/* Уголок: видимый треугольник 12px, зона нажатия 24px (на сенсорном экране — 44px). */
.resize-grip {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 24px;
  height: 24px;
  cursor: nwse-resize;
  touch-action: none;
  background: linear-gradient(135deg, transparent 50%, var(--phosphor-dim) 50%) no-repeat right 2px bottom 2px / 12px 12px;
}

@media (pointer: coarse) {
  .resize-grip {
    width: 44px;
    height: 44px;
    background-size: 16px 16px;
  }
}
</style>
