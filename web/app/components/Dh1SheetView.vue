<script setup lang="ts">
import { Dh1Session } from 'dataslate-core'

// Правки — прямо в объекте анкеты; сохраняет страница.
const character = defineModel<CharacterFile>({ required: true })
const sheet = computed(() => character.value.sheet)
const { t } = useI18n()
const { user } = useAuth()
const player = computed(() => authorName(character.value, user.value))
const mine = computed(() => !!user.value && character.value.author?.id === user.value.id)

const content = demoContent
const CHARACTERISTICS = ['ws', 'bs', 's', 't', 'ag', 'int', 'per', 'wp', 'fel']

function choice(section: string, value?: Choice) {
  if (!value) return {}
  if ('custom' in value) return { custom: value.custom }
  return { entry: findEntry(content, `${section}/${value.id}`), custom: undefined }
}

// Ранги — выбранной карьеры; карьера своя или не выбрана — всех.
const rankEntries = computed(() => {
  const career = sheet.value.career
  const careers = career && 'id' in career ? content.careers?.filter(c => c.id === career.id) : content.careers
  return careers?.flatMap(c => c.ranks ?? []) ?? []
})

watch(() => sheet.value.career, () => {
  const rank = sheet.value.rank
  if (rank && 'id' in rank && !rankEntries.value.some(r => r.id === rank.id)) delete sheet.value.rank
})

const session = new Dh1Session(JSON.stringify({ system: 'dh1', content }))
const evaluation = computed(() => session.evaluate(JSON.stringify(character.value), 'ru'))

const characteristics = computed(() => CHARACTERISTICS.map((id) => {
  const stat = evaluation.value.characteristics.find(s => s.id === id)
  const sources = stat?.contributions.map(c => `${c.source}: ${c.value}`).join(', ')
  return { id, value: stat?.value ?? undefined, sources, entry: findEntry(content, `characteristics/${id}`) ?? { id, name: id.toUpperCase() } }
}))

const skills = computed(() => (sheet.value.skills ?? []).map((s, i) => ({
  key: i,
  entry: findEntry(content, `skills/${s.skill}`) ?? { id: s.skill, name: s.skill },
  specialization: choice('specializations', s.specialization),
  training: s.training ?? 1,
})))

// Справки: окна на широком экране или с мышью, иначе панель со стеком «Назад».
const { size: screen } = useScreen()
const finePointer = useMediaQuery('(pointer: fine)')
const desktop = computed(() => screen.width >= 760 || (screen.width >= 500 && finePointer.value))
const WINDOW_WIDTH = 380
const GAP = 12

const opened = ref<CatalogEntry[]>([])

interface RefWindow { key: number; entry: CatalogEntry; x: number; y: number; z: number }
const windows = ref<RefWindow[]>([])
let nextKey = 0
let topZ = 20

function focus(w: RefWindow) {
  w.z = ++topZ
}

function openWindow(entry: CatalogEntry, from?: RefWindow) {
  const existing = windows.value.find(w => w.entry.id === entry.id)
  if (existing) return focus(existing)
  let x: number
  let y: number
  if (from) {
    const right = from.x + WINDOW_WIDTH + GAP
    x = right + WINDOW_WIDTH <= screen.width ? right : from.x + 32
    y = from.y + (right + WINDOW_WIDTH <= screen.width ? 0 : 32)
  } else {
    const n = windows.value.length
    x = Math.max(GAP, screen.width - WINDOW_WIDTH - 24 - n * 24)
    y = 80 + n * 24
  }
  windows.value.push({ key: nextKey++, entry, x, y: Math.min(y, screen.height - 160), z: ++topZ })
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
  <div class="demo" :class="{ 'with-article': !desktop && opened.length }">
    <div>
      <div class="header">
        <label>{{ t('sheet.name') }}<input v-model="character.name" required></label>
        <div class="field">
          <span>{{ t('demo.player') }}</span>
          <NuxtLink v-if="mine" to="/profile">{{ player }}</NuxtLink>
          <span v-else :class="{ muted: !player }">{{ player ?? '—' }}</span>
        </div>
        <ChoiceField v-model="sheet.homeworld" :label="t('demo.homeworld')" :entries="content.homeworlds ?? []" @open="open" />
        <ChoiceField v-model="sheet.career" :label="t('demo.career')" :entries="content.careers ?? []" @open="open" />
        <ChoiceField v-model="sheet.rank" :label="t('demo.rank')" :entries="rankEntries" @open="open" />
      </div>

      <h3>{{ t('demo.characteristics') }}</h3>
      <table>
        <tr v-for="c in characteristics" :key="c.id">
          <td><ContentTerm :entry="c.entry" @open="open" /></td>
          <td class="value" :title="c.sources">{{ c.value ?? '—' }}</td>
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
    </div>

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
  padding: 16px 16px 48px;
}

.demo.with-article {
  grid-template-columns: 1fr minmax(300px, 420px);
}

.demo.with-article > aside {
  position: sticky;
  top: 16px;
  max-height: calc(100cqh - 32px);
}

h1,
h2,
h3 {
  margin: 0 0 12px;
}

.header {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px 16px;
  margin-bottom: 24px;
}

.header label,
.header .field {
  display: grid;
  gap: 6px;
  align-content: start;
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

@container screen (max-width: 499px) {
  .demo.with-article {
    grid-template-columns: 1fr;
  }

  .demo.with-article > aside {
    position: fixed;
    inset: auto 0 0;
    z-index: 10;
    max-height: 75cqh;
    border-width: 1px 0 0;
    box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.8);
  }
}
</style>
