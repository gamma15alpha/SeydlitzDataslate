<script setup lang="ts">
definePageMeta({ titleKey: 'nav.sheets' })
const { t, locale } = useI18n()
const { text } = useContentLocale()

const dateFormat = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }))

function choiceText(section: string, value?: Choice) {
  if (!value) return undefined
  if ('custom' in value) return value.custom
  const entry = findEntry(demoContent, `${section}/${value.id}`)
  return entry ? text(entry.name) : value.id
}

const cards = computed(() => demoCharacters.map((c) => {
  const sheet = c.sheet as Dh1Sheet
  return {
    id: c.id,
    name: c.name,
    system: SYSTEM_NAMES[c.system] ?? c.system,
    player: sheet.player,
    career: [choiceText('careers', sheet.career), choiceText('ranks', sheet.rank)].filter(Boolean).join(' · '),
    updated: dateFormat.value.format(new Date(c.updatedAt)),
  }
}))
</script>

<template>
  <div class="sheets">
    <div class="actions">
      <button type="button" disabled :title="t('sheets.soon')">{{ t('sheets.create') }}</button>
      <button type="button" disabled :title="t('sheets.soon')">{{ t('sheets.import') }}</button>
      <span class="muted soon">{{ t('sheets.soon') }}</span>
    </div>

    <p v-if="!cards.length" class="panel muted">{{ t('sheets.empty') }}</p>
    <ul v-else class="cards">
      <li v-for="c in cards" :key="c.id">
        <NuxtLink :to="`/sheets/${c.id}`" class="panel card">
          <span class="system">{{ c.system }}</span>
          <span class="name">{{ c.name }}</span>
          <span v-if="c.career">{{ c.career }}</span>
          <span class="muted meta">{{ t('sheets.player', { player: c.player }) }} · {{ t('sheets.updated', { date: c.updated }) }}</span>
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

.actions button:disabled {
  cursor: not-allowed;
}

.soon {
  font-size: var(--text-xs);
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
