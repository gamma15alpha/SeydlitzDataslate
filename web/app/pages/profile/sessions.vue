<script setup lang="ts">
import type { components } from '~/api/schema'
import { api } from '~/api/client'

type SessionInfo = components['schemas']['SessionInfo']
definePageMeta({ titleKey: 'nav.sessions' })
const { t, locale } = useI18n()

const sessions = ref<SessionInfo[]>([])
const error = ref<string | null>(null)
const busy = ref(false)
const dateFormat = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }))

async function load(actionError: string | null = null) {
  try {
    const { data } = await api.GET('/api/sessions')
    if (data) sessions.value = data
    error.value = actionError ?? (data ? null : t('sessions.loadFailed'))
  } catch {
    error.value = t('errors.noConnection')
  }
}

async function run(action: () => Promise<{ response: Response }>) {
  busy.value = true
  let actionError: string | null = null
  try {
    const { response } = await action()
    if (!response.ok && response.status !== 404) actionError = t('errors.server', { status: response.status })
  } catch {
    actionError = t('errors.noConnection')
  }
  busy.value = false
  await load(actionError)
}

const end = (id: string) => run(() => api.DELETE('/api/sessions/{id}', { params: { path: { id } } }))
const endOthers = () => run(() => api.DELETE('/api/sessions'))

function device(ua: string) {
  if (ua.startsWith('SeydlitzDataslate-Android')) return t('sessions.androidApp')
  const os = [['Android', 'Android'], ['iPhone', 'iPhone'], ['Windows', 'Windows'], ['Mac OS', 'macOS'], ['Linux', 'Linux']]
    .find(([key]) => ua.includes(key!))?.[1]
  const browser = [['Edg/', 'Edge'], ['Firefox/', 'Firefox'], ['Chrome/', 'Chrome'], ['Safari/', 'Safari']]
    .find(([key]) => ua.includes(key!))?.[1]
  return [browser, os].filter(Boolean).join(', ') || ua || t('sessions.unknownDevice')
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <ul>
      <li v-for="s in sessions" :key="s.id" class="panel">
        <div>
          {{ device(s.userAgent) }}
          <span v-if="s.current" class="muted">{{ t('sessions.thisDevice') }}</span>
        </div>
        <div class="muted">
          {{ t('sessions.dates', { created: dateFormat.format(new Date(s.createdAt)), used: dateFormat.format(new Date(s.lastUsedAt)) }) }}
        </div>
        <button v-if="!s.current" type="button" :disabled="busy" @click="end(s.id)">{{ t('sessions.end') }}</button>
      </li>
    </ul>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button v-if="sessions.length > 1" type="button" :disabled="busy" @click="endOthers">{{ t('sessions.endOthers') }}</button>
  </div>
</template>

<style scoped>
.page {
  gap: 16px;
}

ul {
  display: grid;
  gap: 16px;
  width: min(480px, 100%);
  margin: 0;
  padding: 0;
  list-style: none;
}

li {
  display: grid;
  gap: 6px;
}

li button {
  justify-self: start;
}
</style>
