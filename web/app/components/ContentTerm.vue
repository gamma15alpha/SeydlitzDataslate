<script setup lang="ts">
const props = defineProps<{ entry?: CatalogEntry; custom?: string }>()
const emit = defineEmits<{ open: [entry: CatalogEntry] }>()
const { lang } = useContentLocale()
const parts = computed(() => (props.entry ? termParts(props.entry, lang.value) : undefined))
</script>

<template>
  <span v-if="custom" class="custom">{{ custom }}</span>
  <component
    :is="entry?.description ? 'button' : 'span'"
    v-else-if="parts"
    class="term" :type="entry?.description ? 'button' : undefined"
    @click="entry?.description && emit('open', entry)"
  >
    <span v-if="parts.abbreviation" class="abbr">{{ parts.abbreviation }}</span>
    {{ parts.name }}
    <span v-if="parts.english" class="muted">{{ parts.english }}</span>
  </component>
  <span v-else class="muted">—</span>
</template>

<style scoped>
.term {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0 0.5em;
  align-items: baseline;
  padding: 0;
  border: 0;
  text-transform: none;
  letter-spacing: normal;
  text-align: left;
}

button.term {
  text-decoration: underline dotted;
  text-underline-offset: 3px;
}

button.term:hover {
  background: transparent;
  color: #fff;
}

.abbr {
  min-width: 2.5em;
  color: var(--amber);
}

.custom {
  font-style: italic;
}
</style>
