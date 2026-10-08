<script setup lang="ts">
// Конфликт синхронизации (D68): анкету изменили и здесь, и на другом устройстве.
const props = defineProps<{ conflict: SyncConflict }>()
const emit = defineEmits<{ resolved: [copy?: CharacterFile] }>()
const { t, locale } = useI18n()
const { resolveConflict } = useCharacters()

const dateFormat = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }))
const name = computed(() => (props.conflict.local ?? props.conflict.server)?.name)
const describe = (c: CharacterFile | null) => (c ? t('sync.changed', { date: dateFormat.value.format(new Date(c.updatedAt)) }) : t('sync.deleted'))

const busy = ref(false)
async function resolve(how: 'mine' | 'server' | 'both') {
  busy.value = true
  try {
    emit('resolved', await resolveConflict(props.conflict, how))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="panel conflict" role="alertdialog">
    <p class="warning">{{ t('sync.conflict', { name }) }}</p>
    <p class="muted">{{ t('sync.local', { state: describe(conflict.local) }) }}</p>
    <p class="muted">{{ t('sync.server', { state: describe(conflict.server) }) }}</p>
    <div class="actions">
      <button type="button" :disabled="busy" @click="resolve('mine')">{{ t('sync.keepMine') }}</button>
      <button type="button" :disabled="busy" @click="resolve('server')">{{ t('sync.takeServer') }}</button>
      <button v-if="conflict.local && conflict.server" type="button" :disabled="busy" @click="resolve('both')">
        {{ t('sync.keepBoth') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.conflict {
  display: grid;
  gap: 6px;
  margin: 0;
}

.conflict p {
  margin: 0;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
