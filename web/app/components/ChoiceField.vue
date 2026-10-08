<script setup lang="ts">
// choice из dh1-sheet.schema.json: запись каталога или своё значение (custom не бывает пустым).
const model = defineModel<Choice | undefined>()
const props = defineProps<{ label: string; entries: CatalogEntry[] }>()
defineEmits<{ open: [entry: CatalogEntry] }>()
const { t } = useI18n()
const { text } = useContentLocale()

const CUSTOM = '*'
const NONE = ''
const modeOf = (c?: Choice) => (!c ? NONE : 'custom' in c ? CUSTOM : c.id)

// Режим отдельно от модели: «своё» с пустым текстом — режим есть, значения нет.
const mode = ref(modeOf(model.value))
const custom = ref(model.value && 'custom' in model.value ? model.value.custom : '')

watch(model, (c) => {
  if (c || mode.value !== CUSTOM) mode.value = modeOf(c)
  if (c && 'custom' in c) custom.value = c.custom
})

watch([mode, custom], ([m, value]) => {
  model.value = m === NONE ? undefined : m === CUSTOM ? (value.trim() ? { custom: value } : undefined) : { id: m }
})

const entry = computed(() => props.entries.find(e => e.id === mode.value))

// Неизвестный id (пакет без этой записи) — показываем как есть, не теряем.
const options = computed(() => [
  { value: NONE, label: t('sheet.none') },
  ...props.entries.map(e => ({ value: e.id, label: text(e.name) })),
  ...(mode.value !== NONE && mode.value !== CUSTOM && !entry.value ? [{ value: mode.value, label: mode.value }] : []),
  { value: CUSTOM, label: t('sheet.custom') },
])
</script>

<template>
  <div class="choice">
    <AppSelect v-model="mode" :label="label" :options="options" />
    <input v-if="mode === CUSTOM" v-model="custom" :aria-label="`${label}: ${t('sheet.custom')}`">
    <ContentTerm v-else-if="entry" :entry="entry" @open="$emit('open', $event)" />
  </div>
</template>

<style scoped>
.choice {
  display: grid;
  gap: 6px;
  align-content: start;
}
</style>
