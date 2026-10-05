<script setup lang="ts" generic="T extends string | number">
// Выпадающий список в стиле терминала вместо <select>: меню системного рисует браузер или ОС, вне палитры экрана.
// Доступность — шаблон WAI-ARIA «select-only combobox»: фокус остаётся на кнопке, выбранный пункт — aria-activedescendant.
// Клавиатура: стрелки, Home/End, Enter/пробел, Esc, Tab, первая буква пункта.
// Подпись — пропсом label, а не обёрткой <label>: клик по пункту внутри label снова нажал бы кнопку и открыл список.
const model = defineModel<T>({ required: true })
const props = defineProps<{ options: { value: T; label: string }[]; label: string }>()

const id = useId()
const root = ref<HTMLElement>()
const button = ref<HTMLButtonElement>()
const list = ref<HTMLElement>()
const open = ref(false)
const active = ref(0)
// Места под кнопкой до края экрана мало — список раскрывается вверх.
const dropUp = ref(false)
const { element: screen } = useScreen()

const selected = computed(() => props.options.findIndex(o => o.value === model.value))
const optionId = (i: number) => `${id}-option-${i}`

async function show() {
  open.value = true
  active.value = Math.max(0, selected.value)
  await nextTick()
  const box = root.value!.getBoundingClientRect()
  const bounds = screen.value?.getBoundingClientRect() ?? { top: 0, bottom: window.innerHeight }
  const below = bounds.bottom - box.bottom
  dropUp.value = list.value!.offsetHeight > below && box.top - bounds.top > below
  scrollToActive()
}

function hide(refocus = true) {
  open.value = false
  if (refocus) button.value?.focus()
}

function choose(i: number) {
  const option = props.options[i]
  if (option) model.value = option.value
  hide()
}

function scrollToActive() {
  list.value?.querySelector(`#${CSS.escape(optionId(active.value))}`)?.scrollIntoView({ block: 'nearest' })
}

function move(to: number) {
  active.value = Math.min(props.options.length - 1, Math.max(0, to))
  scrollToActive()
}

// Первая буква — следующий пункт на неё, по кругу от текущего.
function jumpTo(char: string) {
  const n = props.options.length
  const lower = char.toLowerCase()
  for (let step = 1; step <= n; step++) {
    const i = (active.value + step) % n
    if (props.options[i]!.label.toLowerCase().startsWith(lower)) return move(i)
  }
}

function onKeydown(e: KeyboardEvent) {
  if (!open.value) {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) {
      e.preventDefault()
      void show()
    }
    return
  }
  switch (e.key) {
    case 'ArrowDown': move(active.value + 1); break
    case 'ArrowUp': move(active.value - 1); break
    case 'Home': move(0); break
    case 'End': move(props.options.length - 1); break
    case 'Enter':
    case ' ': choose(active.value); break
    case 'Escape': hide(); break
    case 'Tab': hide(false); return // фокус уходит дальше сам
    default:
      if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) jumpTo(e.key)
      return
  }
  e.preventDefault()
}

// Клик или касание мимо — закрыть, фокус не забирать.
function onOutside(e: PointerEvent) {
  if (!root.value?.contains(e.target as Node)) hide(false)
}
watch(open, (value) => {
  if (value) document.addEventListener('pointerdown', onOutside)
  else document.removeEventListener('pointerdown', onOutside)
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutside))
</script>

<template>
  <div class="field">
    <!-- Подпись, как у <label>: нажатие открывает список -->
    <span :id="`${id}-label`" class="label" @click="button?.focus(); show()">{{ label }}</span>
    <span ref="root" class="app-select" :class="{ open }">
      <button
        ref="button" type="button" class="trigger" role="combobox" :aria-labelledby="`${id}-label`"
        aria-haspopup="listbox" :aria-expanded="open" :aria-controls="`${id}-list`"
        :aria-activedescendant="open ? optionId(active) : undefined"
        @click="open ? hide() : show()" @keydown="onKeydown"
      >
        <span class="value">{{ options[selected]?.label }}</span>
        <span class="chevron" aria-hidden="true" />
      </button>
      <ul
        v-show="open" :id="`${id}-list`" ref="list" class="list" :class="{ up: dropUp }"
        role="listbox" tabindex="-1" :aria-labelledby="`${id}-label`"
      >
        <li
          v-for="(o, i) in options" :id="optionId(i)" :key="o.value"
          role="option" :aria-selected="i === selected" :class="{ active: i === active }"
          @pointerenter="active = i" @click="choose(i)"
        >
          {{ o.label }}
        </li>
      </ul>
    </span>
  </div>
</template>

<style scoped>
.field {
  display: grid;
  gap: 4px;
}

.label {
  cursor: default;
}

.app-select {
  position: relative;
  display: grid;
}

/* Открытый список — поверх соседних панелей */
.app-select.open {
  z-index: 10;
}

/* Кнопка выглядит как поле ввода: те же фон, рамка и нижняя черта */
.trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  text-align: left;
  background: var(--screen);
  border: 1px solid var(--phosphor-faint);
  border-bottom-color: var(--phosphor-dim);
  border-radius: 3px;
  cursor: pointer;
}

.trigger:hover:not(:disabled) {
  background: var(--screen);
  border-color: var(--phosphor-dim);
}

.trigger:focus-visible,
.open .trigger {
  outline: none;
  border-color: var(--phosphor-dim);
  border-bottom: 2px solid var(--phosphor);
  padding-bottom: 5px;
}

.value {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* Уголок-указатель; у открытого — повёрнут */
.chevron {
  flex: none;
  width: 7px;
  height: 7px;
  margin-top: -4px;
  border-right: 1.5px solid currentColor;
  border-bottom: 1.5px solid currentColor;
  transform: rotate(45deg);
  transition: transform 120ms;
}

.open .chevron {
  margin-top: 4px;
  transform: rotate(-135deg);
}

.list {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 100%;
  max-height: 240px;
  overflow-y: auto;
  margin: 0;
  padding: 4px 0;
  box-sizing: border-box;
  list-style: none;
  background: var(--screen);
  border: 1px solid var(--phosphor-dim);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.8), 0 0 10px var(--phosphor-glow);
  scrollbar-color: var(--phosphor-dim) transparent;
}

.list.up {
  top: auto;
  bottom: calc(100% + 4px);
}

li {
  padding: 6px 12px 6px 26px;
  white-space: nowrap;
  cursor: pointer;
  color: var(--phosphor-secondary);
}

/* Подсвеченный пункт — под указателем или стрелками */
li.active {
  background: var(--phosphor-faint);
  color: var(--phosphor);
}

/* Выбранный — с маркером, как курсор терминала */
li[aria-selected='true'] {
  position: relative;
  color: var(--phosphor);
}

li[aria-selected='true']::before {
  content: '>';
  position: absolute;
  left: 10px;
}

@media (pointer: coarse) {
  .trigger {
    min-height: 44px;
  }

  li {
    display: flex;
    align-items: center;
    min-height: 44px;
    padding-block: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chevron {
    transition: none;
  }
}
</style>
