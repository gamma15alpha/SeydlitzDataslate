<script setup lang="ts">
definePageMeta({ titleKey: 'nav.sheets' })
const { t, locale } = useI18n()
const { text } = useContentLocale()

const dateFormat = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }))
const dateTimeFormat = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }))

function choiceText(section: string, value?: Choice) {
  if (!value) return undefined
  if ('custom' in value) return value.custom
  const entry = findEntry(demoContent, `${section}/${value.id}`)
  return entry ? text(entry.name) : value.id
}

const { list, refresh, create, importText, replace, importAsCopy } = useCharacters()
const { user } = useAuth()
onMounted(refresh)

const cards = computed(() => list.value?.map((c) => {
  const sheet = c.sheet
  return {
    id: c.id,
    name: c.name,
    system: SYSTEM_NAMES[c.system] ?? c.system,
    player: authorName(c, user.value),
    career: [choiceText('careers', sheet.career), choiceText('ranks', sheet.rank)].filter(Boolean).join(' · '),
    updated: dateFormat.value.format(new Date(c.updatedAt)),
  }
}))

async function createSheet() {
  const character = await create(t('sheets.newName'))
  await navigateTo(`/sheets/${character.id}`)
}

const fileInput = ref<HTMLInputElement>()
const notice = ref<{ text: string; error?: boolean }>()
const conflict = ref<{ existing: CharacterFile; incoming: CharacterFile }>()

async function onFile() {
  const file = fileInput.value?.files?.[0]
  fileInput.value!.value = ''
  if (!file) return
  conflict.value = undefined
  const r = await importText(await file.text())
  if ('error' in r) notice.value = { text: t(`sheets.importError.${r.error}`), error: true }
  else if ('conflict' in r) {
    notice.value = undefined
    conflict.value = r.conflict
  } else await imported(r.imported)
}

async function imported(character: CharacterFile) {
  conflict.value = undefined
  notice.value = { text: t('sheets.imported', { name: character.name }) }
  await refresh()
}

async function resolve(how: 'replace' | 'copy') {
  const incoming = conflict.value!.incoming
  if (how === 'replace') {
    await replace(incoming)
    await imported(incoming)
  } else await imported(await importAsCopy(incoming))
}
</script>

<template>
  <div class="sheets">
    <div class="actions">
      <button type="button" @click="createSheet">{{ t('sheets.create') }}</button>
      <button type="button" @click="fileInput?.click()">{{ t('sheets.import') }}</button>
      <input ref="fileInput" type="file" accept=".json,application/json" hidden @change="onFile">
    </div>

    <p v-if="notice" class="panel" :class="{ warning: notice.error }" role="status">{{ notice.text }}</p>
    <div v-if="conflict" class="panel conflict" role="alertdialog">
      <p>{{ t('sheets.conflict', { name: conflict.existing.name }) }}</p>
      <p class="muted">{{ t('sheets.conflictExisting', { date: dateTimeFormat.format(new Date(conflict.existing.updatedAt)) }) }}</p>
      <p class="muted">{{ t('sheets.conflictIncoming', { date: dateTimeFormat.format(new Date(conflict.incoming.updatedAt)) }) }}</p>
      <div class="actions">
        <button type="button" @click="resolve('replace')">{{ t('sheets.replace') }}</button>
        <button type="button" @click="resolve('copy')">{{ t('sheets.keepBoth') }}</button>
        <button type="button" @click="conflict = undefined">{{ t('sheets.cancel') }}</button>
      </div>
    </div>

    <p v-if="!cards" class="panel muted">{{ t('sheets.loading') }}</p>
    <p v-else-if="!cards.length" class="panel muted">{{ t('sheets.empty') }}</p>
    <ul v-else class="cards">
      <li v-for="c in cards" :key="c.id">
        <NuxtLink :to="`/sheets/${c.id}`" class="panel card">
          <span class="system">{{ c.system }}</span>
          <span class="name">{{ c.name }}</span>
          <span v-if="c.career">{{ c.career }}</span>
          <span class="muted meta"><template v-if="c.player">{{ t('sheets.player', { player: c.player }) }} · </template>{{ t('sheets.updated', { date: c.updated }) }}</span>
        </NuxtLink>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.sheets {
  display: grid;
  gap: 16px;
  align-content: start;
  max-width: 1100px;
  margin: 0 auto;
  padding: 16px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.sheets > p.panel {
  margin: 0;
}

.conflict {
  display: grid;
  gap: 6px;
}

.conflict p {
  margin: 0;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.card {
  display: grid;
  gap: 6px;
  height: 100%;
  box-sizing: border-box;
  text-decoration: none;
}

.card:hover,
.card:focus-visible {
  border-color: var(--phosphor-dim);
  color: var(--phosphor);
  outline: none;
}

.system {
  justify-self: start;
  padding: 1px 6px;
  border: 1px solid var(--panel-border);
  font-size: var(--text-label);
  letter-spacing: 0.08em;
  color: var(--phosphor-secondary);
}

.name {
  font-family: var(--font-inscription);
  font-size: var(--text-l);
  letter-spacing: 2px;
}

.meta {
  font-size: var(--text-xs);
}
</style>
