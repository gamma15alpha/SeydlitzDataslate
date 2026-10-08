<script setup lang="ts">
definePageMeta({ titleKey: 'nav.sheet' })
const { t } = useI18n()
const route = useRoute()
const id = String(route.params.id)
const { get, scheduleSave, remove, exportFile, saveFailed, conflicts, reloads, renamed } = useCharacters()

// undefined — грузится, null — нет такой анкеты.
const character = ref<CharacterFile | null>()
// Загрузка — не правка: смену из load() watch ниже пропускает.
let loading = false
async function load() {
  const loaded = (await get(id)) ?? null
  if (JSON.stringify(loaded) === JSON.stringify(character.value ?? null)) return
  loading = true
  character.value = loaded
}
onMounted(load)
watch(() => character.value && JSON.stringify(character.value), (json, old) => {
  if (json && old && !loading) scheduleSave(character.value!)
  loading = false
})
onBeforeUnmount(flushCharacters)

// Обновлена с сервера или после выбора в конфликте.
watch(() => reloads.value[id], load)
const conflict = computed(() => conflicts.value.find(c => c.id === id))
async function resolved(copy?: CharacterFile) {
  if (copy) await navigateTo(`/sheets/${copy.id}`)
}
watch(() => renamed.value[id], to => to && navigateTo(`/sheets/${to}`, { replace: true }), { immediate: true })
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
    <SyncConflict v-if="conflict" :conflict="conflict" @resolved="resolved" />
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
.sheet > .conflict,
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
