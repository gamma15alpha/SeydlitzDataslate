<script setup lang="ts">
import pack from '../../../schemas/examples/dh1-mock.content.json'
import character from '../../../schemas/examples/dh1.character.json'

const { t } = useI18n()
useHead({ title: () => t('demo.title') })
const { setting, setContentLang } = useContentLocale()

const content = pack.content as Dh1Content
const sheet = character.sheet as Dh1Sheet
const CHARACTERISTICS = ['ws', 'bs', 's', 't', 'ag', 'int', 'per', 'wp', 'fel']

function choice(section: string, value?: Choice) {
  if (!value) return {}
  if ('custom' in value) return { custom: value.custom }
  return { entry: findEntry(content, `${section}/${value.id}`), custom: undefined }
}

const characteristics = computed(() => CHARACTERISTICS.map((id) => {
  const c = sheet.characteristics?.[id]
  const value = c?.base == null ? undefined : c.base + 5 * (c.advances ?? 0)
  return { id, value, entry: findEntry(content, `characteristics/${id}`) ?? { id, name: id.toUpperCase() } }
}))

const skills = computed(() => (sheet.skills ?? []).map((s, i) => ({
  key: i,
  entry: findEntry(content, `skills/${s.skill}`) ?? { id: s.skill, name: s.skill },
  specialization: choice('specializations', s.specialization),
  training: s.training ?? 1,
})))

// ПК и планшет от 1024px — справки в перетаскиваемых окнах, ссылка открывает окно рядом;
// сенсорный экран 761–1023px — панель справа, уже — панель снизу; у панелей стек «Назад».
const desktop = useMediaQuery('(min-width: 1024px), (min-width: 761px) and (pointer: fine)')
const WINDOW_WIDTH = 380
const GAP = 12

// Телефон: последняя — на экране, «Назад» снимает её.
const opened = ref<CatalogEntry[]>([])

interface RefWindow { key: number; entry: CatalogEntry; x: number; y: number; z: number }
const windows = ref<RefWindow[]>([])
let nextKey = 0
let topZ = 20

function focus(w: RefWindow) {
  w.z = ++topZ
}

// Уже открытая справка поднимается наверх вместо дубля.
function openWindow(entry: CatalogEntry, from?: RefWindow) {
  const existing = windows.value.find(w => w.entry.id === entry.id)
  if (existing) return focus(existing)
  let x: number
  let y: number
  if (from) {
    const right = from.x + WINDOW_WIDTH + GAP
    x = right + WINDOW_WIDTH <= window.innerWidth ? right : from.x + 32
    y = from.y + (right + WINDOW_WIDTH <= window.innerWidth ? 0 : 32)
  } else {
    const n = windows.value.length
    x = Math.max(GAP, window.innerWidth - WINDOW_WIDTH - 24 - n * 24)
    y = 80 + n * 24
  }
  windows.value.push({ key: nextKey++, entry, x, y: Math.min(y, window.innerHeight - 160), z: ++topZ })
}

const closeWindow = (w: RefWindow) => (windows.value = windows.value.filter(o => o !== w))

const notFound = ref<string | null>(null)

function open(entry: CatalogEntry) {
  if (desktop.value) openWindow(entry)
  else opened.value = [entry]
}

function navigate(ref: string, from?: RefWindow) {
  const entry = findEntry(content, ref)
  notFound.value = entry ? null : ref
  if (!entry) return
  if (from) openWindow(entry, from)
  else opened.value = [...opened.value, entry]
}
</script>

<template>
  <LocaleSwitch />
  <div class="demo" :class="{ 'with-article': !desktop && opened.length }">
    <main>
      <h1>{{ t('demo.title') }}</h1>
      <label class="content-lang">
        {{ t('demo.contentLang') }}
        <select :value="setting" @change="setContentLang(($event.target as HTMLSelectElement).value as ContentLang)">
          <option value="ui">{{ t('demo.contentLangUi') }}</option>
          <option value="ru">Русский</option>
          <option value="en">English</option>
        </select>
      </label>

      <h2>{{ character.name }}</h2>
      <dl>
        <dt>{{ t('demo.player') }}</dt>
        <dd>{{ sheet.player }}</dd>
        <dt>{{ t('demo.homeworld') }}</dt>
        <dd><ContentTerm v-bind="choice('homeworlds', sheet.homeworld)" @open="open" /></dd>
        <dt>{{ t('demo.career') }}</dt>
        <dd><ContentTerm v-bind="choice('careers', sheet.career)" @open="open" /></dd>
        <dt>{{ t('demo.rank') }}</dt>
        <dd><ContentTerm v-bind="choice('ranks', sheet.rank)" @open="open" /></dd>
      </dl>

      <h3>{{ t('demo.characteristics') }}</h3>
      <table>
        <tr v-for="c in characteristics" :key="c.id">
          <td><ContentTerm :entry="c.entry" @open="open" /></td>
          <td class="value">{{ c.value ?? '—' }}</td>
        </tr>
      </table>

      <h3>{{ t('demo.skills') }}</h3>
      <ul>
        <li v-for="s in skills" :key="s.key">
          <ContentTerm :entry="s.entry" @open="open" />
          <template v-if="s.specialization.entry || s.specialization.custom">
            (<ContentTerm v-bind="s.specialization" @open="open" />)
          </template>
          <span class="muted">{{ s.training > 1 ? t('demo.training', { n: 10 * (s.training - 1) }) : t('demo.trained') }}</span>
        </li>
      </ul>
      <p v-if="notFound" class="warning">{{ t('demo.notFound', { ref: notFound }) }}</p>
    </main>

    <ContentArticle
      v-if="!desktop && opened.length"
      :entry="opened.at(-1)!"
      :can-go-back="opened.length > 1"
      @back="opened = opened.slice(0, -1)"
      @close="opened = []"
      @navigate="navigate"
    />
  </div>

  <template v-if="desktop">
    <FloatingWindow
      v-for="w in windows" :key="w.key"
      :x="w.x" :y="w.y" :z="w.z"
      @move="(x, y) => { w.x = x; w.y = y }"
      @focus="focus(w)"
    >
      <template #default="{ grab, dragging }">
        <ContentArticle
          :entry="w.entry" :can-go-back="false" draggable :dragging="dragging"
          @grab="grab"
          @close="closeWindow(w)"
          @navigate="ref => navigate(ref, w)"
        />
      </template>
    </FloatingWindow>
  </template>
</template>

<style scoped>
.demo {
  display: grid;
  gap: 24px;
  max-width: 1100px;
  margin: 0 auto;
  padding:
    calc(max(12px, env(safe-area-inset-top)) + 56px) max(16px, env(safe-area-inset-right))
    max(48px, env(safe-area-inset-bottom)) max(16px, env(safe-area-inset-left));
}

.demo.with-article {
  grid-template-columns: 1fr minmax(300px, 420px);
}

.demo.with-article > aside {
  position: sticky;
  top: 16px;
  max-height: calc(100dvh - 32px);
}

h1,
h2,
h3 {
  margin: 0 0 12px;
}

.content-lang {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 24px;
}

select {
  font: inherit;
  color: inherit;
  background: var(--screen);
  border: 1px solid var(--phosphor-dim);
  padding: 4px 8px;
}

dl {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 6px 16px;
}

dt {
  color: var(--phosphor-dim);
}

dd {
  margin: 0;
}

table {
  border-collapse: collapse;
  margin-bottom: 24px;
}

td {
  padding: 4px 16px 4px 0;
}

.value {
  text-align: right;
}

ul {
  display: grid;
  gap: 6px;
  padding: 0;
  list-style: none;
}

/* Телефон: описание — панель снизу поверх анкеты. */
@media (max-width: 760px) {
  .demo.with-article {
    grid-template-columns: 1fr;
  }

  .demo.with-article > aside {
    position: fixed;
    inset: auto 0 0;
    z-index: 10;
    max-height: 75dvh;
    padding-bottom: max(16px, env(safe-area-inset-bottom));
    border-width: 1px 0 0;
    box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.8);
  }
}
</style>
