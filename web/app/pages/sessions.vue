<script setup lang="ts">
import type { components } from '~/api/schema'
import { api } from '~/api/client'

type SessionInfo = components['schemas']['SessionInfo']
useHead({ title: 'Сессии — Seydlitz Dataslate' })

const sessions = ref<SessionInfo[]>([])
const error = ref<string | null>(null)
const busy = ref(false)
const dateFormat = new Intl.DateTimeFormat('ru', { dateStyle: 'medium', timeStyle: 'short' })

async function load() {
  try {
    const { data } = await api.GET('/api/sessions')
    if (data) sessions.value = data
    error.value = data ? null : 'Не удалось загрузить сессии'
  } catch {
    error.value = 'Нет связи с сервером'
  }
}

async function run(action: () => Promise<{ response: Response }>) {
  busy.value = true
  try {
    const { response } = await action()
    if (!response.ok && response.status !== 404) error.value = `Ошибка сервера ${response.status}`
  } catch {
    error.value = 'Нет связи с сервером'
  }
  busy.value = false
  await load()
}

const end = (id: string) => run(() => api.DELETE('/api/sessions/{id}', { params: { path: { id } } }))
const endOthers = () => run(() => api.DELETE('/api/sessions'))

function device(ua: string) {
  if (ua.startsWith('SeydlitzDataslate-Android')) return 'Android-приложение'
  const os = [['Android', 'Android'], ['iPhone', 'iPhone'], ['Windows', 'Windows'], ['Mac OS', 'macOS'], ['Linux', 'Linux']]
    .find(([key]) => ua.includes(key!))?.[1]
  const browser = [['Edg/', 'Edge'], ['Firefox/', 'Firefox'], ['Chrome/', 'Chrome'], ['Safari/', 'Safari']]
    .find(([key]) => ua.includes(key!))?.[1]
  return [browser, os].filter(Boolean).join(', ') || ua || 'Неизвестное устройство'
}

onMounted(load)
</script>

<template>
  <main class="screen">
    <h1>Сессии</h1>
    <ul>
      <li v-for="s in sessions" :key="s.id">
        <div>
          {{ device(s.userAgent) }}
          <span v-if="s.current" class="muted">— это устройство</span>
        </div>
        <div class="muted">
          вход {{ dateFormat.format(new Date(s.createdAt)) }} · активность {{ dateFormat.format(new Date(s.lastUsedAt)) }}
        </div>
        <button v-if="!s.current" type="button" :disabled="busy" @click="end(s.id)">Завершить</button>
      </li>
    </ul>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button v-if="sessions.length > 1" type="button" :disabled="busy" @click="endOthers">Выйти на всех остальных устройствах</button>
    <NuxtLink to="/">Назад</NuxtLink>
  </main>
</template>

<style scoped>
.screen {
  gap: 16px;
}

h1 {
  margin: 0;
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
  padding: 12px;
  border: 1px solid var(--phosphor-dim);
}

li button {
  justify-self: start;
}
</style>
