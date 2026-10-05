<script setup lang="ts">
const props = defineProps<{ entry: CatalogEntry; canGoBack: boolean; draggable?: boolean; dragging?: boolean }>()
const emit = defineEmits<{ close: []; back: []; navigate: [ref: string]; grab: [e: PointerEvent] }>()
const { t } = useI18n()
const { lang } = useContentLocale()
const showOriginal = ref(false)
watch(() => props.entry, () => (showOriginal.value = false))

const main = computed(() => (props.entry.description ? pickText(props.entry.description, lang.value) : ''))
const others = computed(() => (props.entry.description ? allTexts(props.entry.description).filter(([, text]) => text !== main.value) : []))

// Тянуть — только за пустое место полосы: название можно выделить и скопировать.
function onHeaderPointerDown(e: PointerEvent) {
  if (props.draggable && !(e.target as HTMLElement).closest('.title')) emit('grab', e)
}

function onClick(e: MouseEvent) {
  const link = (e.target as HTMLElement).closest('a[data-ref]')
  if (!link) return
  e.preventDefault()
  emit('navigate', link.getAttribute('data-ref')!)
}
</script>

<template>
  <aside class="article" @click="onClick">
    <header :class="{ draggable, dragging }" @pointerdown="onHeaderPointerDown">
      <button v-if="canGoBack" type="button" @click="emit('back')">{{ t('demo.back') }}</button>
      <span class="title"><ContentTerm :entry="{ ...entry, description: undefined }" /></span>
      <button type="button" class="close" :aria-label="t('demo.close')" @click="emit('close')">×</button>
    </header>
    <div class="body">
      <!-- eslint-disable-next-line vue/no-v-html -- renderMarkdown не пропускает сырой HTML -->
      <div class="markdown" v-html="renderMarkdown(main)" />
      <template v-if="others.length">
        <button type="button" @click="showOriginal = !showOriginal">{{ t('demo.original') }}</button>
        <div v-for="[l, text] in showOriginal ? others : []" :key="l" class="markdown original">
          <span class="muted">{{ l.toUpperCase() }}</span>
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div v-html="renderMarkdown(text)" />
        </div>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.article {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--phosphor-dim);
  background: var(--screen);
  overflow: hidden;
}

/* Полоса заголовка, как у окна: в режиме окна за неё перетаскивают. */
header {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  min-height: 36px;
  padding-left: 12px;
  background: rgba(79, 168, 69, 0.22);
  border-bottom: 1px solid var(--phosphor-dim);
}

header.draggable {
  touch-action: none;
}

header.dragging {
  background: rgba(79, 168, 69, 0.4);
}

.title {
  cursor: text;
}

@media (pointer: coarse) {
  header {
    min-height: 44px;
  }
}

.close {
  align-self: stretch;
  margin-left: auto;
  padding: 0 14px;
  border: 0;
  font-size: 18px;
  line-height: 1;
}

.close:hover {
  background: #b3261e;
  color: #fff;
}

.body {
  display: grid;
  gap: 12px;
  align-content: start;
  min-height: 0; /* иначе во flex-колонке тело не сжимается и не прокручивается */
  padding: 16px;
  overflow-y: auto;
}

.original {
  padding-left: 12px;
  border-left: 2px solid var(--phosphor-dim);
}

.markdown :deep(.table-scroll) {
  max-width: 100%;
  overflow-x: auto;
}

.markdown :deep(table) {
  border-collapse: collapse;
}

.markdown :deep(th),
.markdown :deep(td) {
  padding: 4px 10px;
  border: 1px solid var(--phosphor-dim);
}

.markdown :deep(h2) {
  margin: 0 0 8px;
  font-family: var(--font-inscription);
  font-weight: 400;
}

.markdown :deep(.missing-image) {
  color: var(--phosphor-dim);
}
</style>
