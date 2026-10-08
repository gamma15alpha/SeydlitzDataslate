<script setup lang="ts">
definePageMeta({ titleKey: 'nav.sheet' })
const { t } = useI18n()
const route = useRoute()
const id = String(route.params.id)
const { get, scheduleSave, remove, exportFile, saveFailed } = useCharacters()

// undefined — грузится, null — нет такой анкеты.
const character = ref<CharacterFile | null>()
onMounted(async () => (character.value = (await get(id)) ?? null))
// Загрузка (из undefined) — не правка.
watch(() => character.value && JSON.stringify(character.value), (json, old) => {
  if (json && old) scheduleSave(character.value!)
})
onBeforeUnmount(flushCharacters)
usePageTitle(() => character.value?.name)

const confirmDelete = ref(false)
async function deleteSheet() {
  await remove(id)
  await navigateTo('/sheets')
}
</script>

<template>
  <div v-if="character" class="sheet">
    <p v-if="saveFailed" class="panel warning" role="alert">{{ t('sheet.saveFailed') }}</p>
    <Dh1SheetView v-model="character" />
    <div class="actions">
      <button type="button" @click="exportFile(id)">{{ t('sheet.export') }}</button>
      <button v-if="!confirmDelete" type="button" @click="confirmDelete = true">{{ t('sheet.delete') }}</button>
    </div>
    <div v-if="confirmDelete" class="panel confirm" role="alertdialog">
      <p class="warning">{{ t('sheet.deleteConfirm', { name: character.name }) }}</p>
      <div class="actions">
        <button type="button" @click="deleteSheet">{{ t('sheet.deleteYes') }}</button>
        <button type="button" @click="confirmDelete = false">{{ t('sheets.cancel') }}</button>
      </div>
    </div>
  </div>
  <div v-else class="page">
    <p class="panel muted">{{ t(character === undefined ? 'sheets.loading' : 'sheets.notFound') }}</p>
    <NuxtLink v-if="character === null" to="/sheets">{{ t('sheets.toList') }}</NuxtLink>
  </div>
</template>

<style scoped>
.sheet {
  display: grid;
  gap: 12px;
  align-content: start;
  max-width: 1100px;
  margin: 0 auto;
  padding-bottom: 48px;
}

.sheet > .panel,
.sheet > .actions {
  margin: 0 16px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.confirm {
  display: grid;
  gap: 12px;
}

.page {
  gap: 12px;
}

.panel {
  margin: 0;
}
</style>
