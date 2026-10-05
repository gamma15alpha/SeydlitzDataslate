<script setup lang="ts">
// Корпус датаслейта вокруг экрана (D10–D12): табличка, лампы, кнопки разделов (D61), ручка PHOSPHOR,
// кнопка SCAN, ЖК-статус, ползунок языка LANG. Стили — assets/css/casing.css; приложение — в слоте, на экране.
const { t, locale, locales, setLocale } = useI18n()
const { phosphor, scanlines, busy, fault, setPhosphor, toggleScanlines } = useCasing()
const { state: link } = useServerLink()
const auth = useAuth()
const { section } = useNavigation()

const index = computed(() => PHOSPHORS.findIndex(p => p.name === phosphor.value))
const position = computed(() => PHOSPHORS[index.value]!)

const clamp = (i: number, count: number) => Math.min(count - 1, Math.max(0, i))

// Стрелки, Home/End — у ручки и ползунка одинаково; дальше крайних положений не идут (упоры).
const KEY_STEPS: Record<string, (i: number, count: number) => number> = {
  ArrowRight: i => i + 1,
  ArrowUp: i => i + 1,
  ArrowLeft: i => i - 1,
  ArrowDown: i => i - 1,
  Home: () => 0,
  End: (_, count) => count - 1,
}

function keyStep(e: KeyboardEvent, current: number, count: number): number | null {
  const step = KEY_STEPS[e.key]
  if (!step) return null
  e.preventDefault()
  return clamp(step(current, count), count)
}

// Клик по ручке — по кругу, колесо — до упора.
function turnTo(next: number) {
  setPhosphor(PHOSPHORS[clamp(next, PHOSPHORS.length)]!.name)
}

function onKnobClick() {
  setPhosphor(PHOSPHORS[(index.value + 1) % PHOSPHORS.length]!.name)
}

function onKnobWheel(e: WheelEvent) {
  turnTo(index.value + (e.deltaY > 0 ? 1 : -1))
}

function onKnobKey(e: KeyboardEvent) {
  const next = keyStep(e, index.value, PHOSPHORS.length)
  if (next !== null) turnTo(next)
}

// Ползунок LANG: два положения, ручка ездит по прорези. Нажатие — в другое положение,
// свайп — ручка идёт за пальцем (или мышью), отпустили — язык в сторону свайпа.
const langIndex = computed(() => Math.max(0, locales.value.findIndex(l => l.code === locale.value)))

async function setLang(i: number) {
  const code = locales.value[i]?.code
  if (code && code !== locale.value) await setLocale(code)
}

const LANG_TRAVEL = 22 // ход ручки, px — как в casing.css
const SWIPE_THRESHOLD = 6 // меньше — это нажатие, а не свайп
// Положение ручки во время свайпа (0…1); null — ручка стоит у текущего языка.
const dragPos = ref<number | null>(null)
let drag: { startX: number; startPos: number; dx: number; swiped: boolean } | null = null
// После свайпа мышью браузер присылает ещё и click — его не считаем нажатием.
let swallowClick = false

function onLangPointerDown(e: PointerEvent) {
  if (e.button !== 0) return
  swallowClick = false
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  drag = { startX: e.clientX, startPos: langIndex.value, dx: 0, swiped: false }
}

function onLangPointerMove(e: PointerEvent) {
  if (!drag) return
  drag.dx = e.clientX - drag.startX
  if (!drag.swiped && Math.abs(drag.dx) < SWIPE_THRESHOLD) return
  drag.swiped = true
  dragPos.value = Math.min(1, Math.max(0, drag.startPos + drag.dx / LANG_TRAVEL))
}

async function onLangPointerUp() {
  if (!drag) return
  const { swiped, dx, startPos } = drag
  drag = null
  if (!swiped) return // нажатие — обработает click
  swallowClick = true
  // Решает направление: увели и вернули к началу — язык прежний.
  const target = Math.abs(dx) < SWIPE_THRESHOLD ? startPos : dx > 0 ? 1 : 0
  dragPos.value = target
  await setLang(target)
  dragPos.value = null
}

function onLangPointerCancel() {
  drag = null
  dragPos.value = null
}

function onLangClick() {
  if (swallowClick) swallowClick = false
  else void setLang(1 - langIndex.value)
}

function onLangKey(e: KeyboardEvent) {
  const next = keyStep(e, langIndex.value, locales.value.length)
  if (next !== null) void setLang(next)
}

// VOX: горит — сервер на связи, мигает красным — сеть есть, а сервер не отвечает, погашена — нет сети.
const status = computed(() => {
  if (fault.value) return t('casing.fault')
  switch (link.value) {
    case 'up': return t('casing.ready')
    case 'down': return t('casing.serverDown')
    case 'offline': return t('casing.offline')
    default: return t('casing.connecting')
  }
})
</script>

<template>
  <!-- Общие SVG-символы: шестерня Механикус -->
  <svg width="0" height="0" style="position: absolute" aria-hidden="true">
    <defs>
      <mask id="cog-hole">
        <rect width="100" height="100" fill="white" />
        <circle cx="50" cy="50" r="19" fill="black" />
      </mask>
      <symbol id="cog" viewBox="0 0 100 100">
        <g fill="currentColor" mask="url(#cog-hole)">
          <circle cx="50" cy="50" r="33" />
          <rect
            v-for="a in 12" :key="a"
            x="43" y="3" width="14" height="18" rx="2" :transform="`rotate(${(a - 1) * 30} 50 50)`"
          />
        </g>
      </symbol>
    </defs>
  </svg>

  <div class="housing">
    <!-- Верхняя панель: табличка, клеймо, сигнальные лампы, динамик -->
    <header class="deck deck-top">
      <span class="screw" style="--r: 18deg" aria-hidden="true" />

      <div class="nameplate">
        <span class="nameplate-emblem" aria-hidden="true">
          <svg viewBox="0 0 100 100"><use href="#cog" /></svg>
          <span class="nameplate-eye" />
        </span>
        <span class="nameplate-title">Seydlitz Dataslate</span>
      </div>

      <span class="deck-spacer" />

      <span class="serial">STC&nbsp;7&#8209;IV/41</span>

      <div class="lamps">
        <div class="lamp-unit">
          <span class="lamp on" :class="{ fault }" style="--lamp: #7dff6a" aria-hidden="true" />
          <span class="stencil" aria-hidden="true">PWR</span>
        </div>
        <div class="lamp-unit">
          <span
            class="lamp" :class="{ on: link === 'up' || link === 'down', alarm: link === 'down' }"
            style="--lamp: #ffb03a" aria-hidden="true"
          />
          <span class="stencil" aria-hidden="true">VOX</span>
        </div>
        <div class="lamp-unit">
          <span class="lamp" :class="{ on: busy, blink: busy }" style="--lamp: #ff5a3c" aria-hidden="true" />
          <span class="stencil" aria-hidden="true">ACT</span>
        </div>
      </div>

      <span class="grille grille-top" aria-hidden="true" />
      <span class="screw" style="--r: -34deg" aria-hidden="true" />
    </header>

    <!-- Левая стойка: клавиши разделов, кабельные трубы и вентиляция -->
    <aside class="column column-left">
      <span class="screw" style="--r: 62deg" aria-hidden="true" />
      <!-- Те же разделы, что вкладки на экране; без входа — неактивны -->
      <nav class="section-keys" :aria-label="t('nav.sections')">
        <div v-for="s in SECTIONS" :key="s.key" class="control">
          <button
            class="section-key" :class="{ active: section?.key === s.key }" type="button"
            :disabled="!auth.user.value" :aria-label="t(s.titleKey)"
            :aria-current="section?.key === s.key ? 'page' : undefined"
            @click="navigateTo(s.path)"
          >
            <span class="section-key-led" aria-hidden="true" />
          </button>
          <span class="stencil" aria-hidden="true">{{ s.stencil }}</span>
        </div>
      </nav>
      <div class="conduits" aria-hidden="true">
        <span class="conduit" />
        <span class="conduit conduit-thin" />
        <span class="clamp" style="top: 14%" />
        <span class="clamp" style="top: 58%" />
      </div>
      <span class="vents" aria-hidden="true" />
      <span class="screw" style="--r: -8deg" aria-hidden="true" />
    </aside>

    <!-- Экран в рамке -->
    <div class="bezel">
      <span class="screw bezel-screw tl" style="--r: 40deg" aria-hidden="true" />
      <span class="screw bezel-screw tr" style="--r: -12deg" aria-hidden="true" />
      <span class="screw bezel-screw bl" style="--r: 75deg" aria-hidden="true" />
      <span class="screw bezel-screw br" style="--r: 5deg" aria-hidden="true" />

      <div class="screen-well">
        <div :ref="el => registerScreen(el as HTMLElement | null)" class="screen">
          <div class="screen-content">
            <slot />
          </div>
        </div>
        <div class="scanlines" aria-hidden="true" />
        <div class="glass" aria-hidden="true" />
      </div>
    </div>

    <!-- Правая стойка: органы управления и печать чистоты -->
    <aside class="column column-right">
      <span class="screw" style="--r: -50deg" aria-hidden="true" />

      <!-- Переключатель люминофора: метки шкалы и ручка-указатель -->
      <div class="control">
        <div class="phosphor-selector">
          <button
            v-for="p in PHOSPHORS" :key="p.name"
            class="phosphor-mark" :class="{ active: p.name === phosphor }" type="button" tabindex="-1" aria-hidden="true"
            :style="{ '--angle': `${p.angle}deg`, '--mark': p.color }"
            @click="setPhosphor(p.name)"
          >
            {{ p.mark }}
          </button>
          <div
            class="knob" role="slider" tabindex="0"
            :style="{ '--angle': `${position.angle}deg` }"
            :aria-label="t('casing.phosphor')" aria-valuemin="0" :aria-valuemax="PHOSPHORS.length - 1"
            :aria-valuenow="index" :aria-valuetext="position.label"
            @click="onKnobClick" @wheel.prevent="onKnobWheel" @keydown="onKnobKey"
          />
        </div>
        <span class="stencil" aria-hidden="true">PHOSPHOR</span>
      </div>

      <div class="control">
        <button class="push-button" type="button" :aria-pressed="scanlines" :aria-label="t('casing.scanlines')" @click="toggleScanlines">
          <span class="push-button-led" aria-hidden="true" />
        </button>
        <span class="stencil" aria-hidden="true">SCAN</span>
      </div>

      <div class="seal" aria-hidden="true">
        <span class="seal-ribbon r1" />
        <span class="seal-ribbon r2" />
        <span class="seal-wax">
          <svg viewBox="0 0 100 100"><use href="#cog" /></svg>
        </span>
      </div>

      <span class="screw" style="--r: 27deg" aria-hidden="true" />
    </aside>

    <!-- Нижняя панель: разметка, разъёмы, индикатор статуса -->
    <footer class="deck deck-bottom">
      <span class="screw" style="--r: 81deg" aria-hidden="true" />
      <span class="hazard" aria-hidden="true" />
      <span class="ports" aria-hidden="true">
        <span class="port" />
        <span class="port" />
        <span class="port port-round" />
      </span>
      <span class="deck-spacer" />
      <div class="lcd">
        <span class="lcd-text" :class="{ fail: fault || link === 'down' }" role="status">{{ status }}</span>
      </div>
      <span class="deck-spacer" />

      <!-- Ползунок языка: метки по краям прорези, ручка у выбранной -->
      <div class="control">
        <div class="lang-selector">
          <button class="lang-mark" :class="{ active: langIndex === 0 }" type="button" tabindex="-1" aria-hidden="true" @click="setLang(0)">
            {{ locales[0]?.code.toUpperCase() }}
          </button>
          <div
            class="slide-switch" :class="{ dragging: dragPos !== null }" role="slider" tabindex="0"
            :style="{ '--pos': dragPos ?? langIndex }"
            :aria-label="t('app.language')" aria-valuemin="0" :aria-valuemax="locales.length - 1"
            :aria-valuenow="langIndex" :aria-valuetext="locales[langIndex]?.name"
            @click="onLangClick" @keydown="onLangKey"
            @pointerdown="onLangPointerDown" @pointermove="onLangPointerMove"
            @pointerup="onLangPointerUp" @pointercancel="onLangPointerCancel"
          >
            <span class="slide-knob" />
          </div>
          <button class="lang-mark" :class="{ active: langIndex === 1 }" type="button" tabindex="-1" aria-hidden="true" @click="setLang(1)">
            {{ locales[1]?.code.toUpperCase() }}
          </button>
        </div>
        <span class="stencil" aria-hidden="true">LANG</span>
      </div>

      <span class="grille grille-bottom" aria-hidden="true" />
      <span class="screw" style="--r: -22deg" aria-hidden="true" />
    </footer>
  </div>
</template>
