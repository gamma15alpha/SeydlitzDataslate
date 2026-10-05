<script setup lang="ts">
// Раздел «Справочник» — каталог контента; пока заглушка каркаса.
definePageMeta({ titleKey: 'nav.reference' })
const { t } = useI18n()
</script>

<template>
  <div class="page">
    <p class="panel muted">{{ t('reference.placeholder') }}</p>
  </div>
</template>

<style scoped>
.panel {
  max-width: 48ch;
  margin: 0;
}
</style>
