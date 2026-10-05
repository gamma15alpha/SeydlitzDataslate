<script setup lang="ts">
definePageMeta({ titleKey: 'nav.sheet' })
const { t } = useI18n()
const route = useRoute()
const character = computed(() => findDemoCharacter(String(route.params.id)))
// В строке приложения — имя персонажа, а не безликое «Анкета».
usePageTitle(() => character.value?.name)
</script>

<template>
  <Dh1SheetView v-if="character" :character="character" />
  <div v-else class="page">
    <p class="panel muted">{{ t('sheets.notFound') }}</p>
    <NuxtLink to="/sheets">{{ t('sheets.toList') }}</NuxtLink>
  </div>
</template>

<style scoped>
.page {
  gap: 12px;
}

.panel {
  margin: 0;
}
</style>
