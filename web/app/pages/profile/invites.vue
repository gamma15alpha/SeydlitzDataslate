<script setup lang="ts">
import type { components } from '~/api/schema'
import { api } from '~/api/client'

// Приглашения — только администратору (сервер проверяет сам; здесь — чтобы не показывать пустую форму).
definePageMeta({ titleKey: 'nav.invites' })
type Invite = components['schemas']['Invite']
const { t, locale } = useI18n()
const auth = useAuth()
const isAdmin = computed(() => auth.user.value?.isAdmin ?? false)

const EXPIRY_DAYS = [1, 3, 7, 14, 30, 90]
const expiresInDays = ref(7)
const invites = ref<Invite[]>([])
// Код показывается один раз — в ответе на создание; сервер хранит только хеш.
const created = ref<{ code: string; link: string } | null>(null)
const copied = ref<'code' | 'link' | null>(null)
const busy = ref(false)
const error = ref<string | null>(null)
const dateFormat = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }))
const format = (iso: string) => dateFormat.value.format(new Date(iso))
// «1 день / 3 дня / 7 дней»: форма числа — по правилам языка (Intl), тексты форм — в i18n.
const plural = computed(() => new Intl.PluralRules(locale.value))
const days = (n: number) => t(`invites.days.${plural.value.select(n)}`, { n })
const expiryOptions = computed(() => EXPIRY_DAYS.map(d => ({ value: d, label: days(d) })))

type Status = 'used' | 'expired' | 'active'
function status(i: Invite): Status {
  if (i.usedAt) return 'used'
  return new Date(i.expiresAt) <= new Date() ? 'expired' : 'active'
}

async function call<T>(request: () => Promise<{ data?: T; error?: components['schemas']['Error']; response: Response }>) {
  try {
    const { data, error: apiError, response } = await request()
    if (!response.ok) error.value = errorMessage(t, apiError, response)
    return response.ok ? (data ?? null) : null
  } catch {
    error.value = t('errors.noConnection')
    return null
  }
}

async function load() {
  const list = await call(() => api.GET('/api/invites'))
  if (list) invites.value = list
}

async function create() {
  busy.value = true
  error.value = null
  copied.value = null
  const invite = await call(() => api.POST('/api/invites', { body: { expiresInDays: expiresInDays.value } }))
  if (invite?.code) {
    created.value = { code: invite.code, link: `${location.origin}/register?invite=${encodeURIComponent(invite.code)}` }
    await load()
  }
  busy.value = false
}

async function revoke(id: string) {
  busy.value = true
  error.value = null
  await call(() => api.DELETE('/api/invites/{id}', { params: { path: { id } } }))
  await load()
  busy.value = false
}

async function copy(what: 'code' | 'link') {
  if (!created.value) return
  try {
    await navigator.clipboard.writeText(created.value[what])
    copied.value = what
  } catch {
    error.value = t('invites.copyFailed')
  }
}

onMounted(() => {
  if (isAdmin.value) void load()
})
</script>

<template>
  <div class="page">
    <p v-if="!isAdmin" class="panel muted">{{ t('invites.adminOnly') }}</p>
    <template v-else>
      <form class="panel create" @submit.prevent="create">
        <AppSelect v-model="expiresInDays" :label="t('invites.expiresIn')" :options="expiryOptions" />
        <button type="submit" :disabled="busy">{{ t('invites.create') }}</button>

        <div v-if="created" class="created" role="status">
          <p class="warning">{{ t('invites.showOnce') }}</p>
          <div class="secret">
            <span class="muted">{{ t('invites.code') }}</span>
            <code>{{ created.code }}</code>
            <button type="button" @click="copy('code')">{{ copied === 'code' ? t('invites.copied') : t('invites.copy') }}</button>
          </div>
          <div class="secret">
            <span class="muted">{{ t('invites.link') }}</span>
            <code>{{ created.link }}</code>
            <button type="button" @click="copy('link')">{{ copied === 'link' ? t('invites.copied') : t('invites.copy') }}</button>
          </div>
        </div>
      </form>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <ul class="list">
        <li v-if="!invites.length" class="muted">{{ t('invites.none') }}</li>
        <li v-for="i in invites" :key="i.id" class="panel invite">
          <div>
            <span :class="['status', status(i)]">{{ t(`invites.status.${status(i)}`) }}</span>
            <template v-if="i.usedAt">
              · {{ i.usedBy ?? t('invites.deletedUser') }}, {{ format(i.usedAt) }}
            </template>
          </div>
          <div class="muted dates">
            {{ t('invites.dates', { created: format(i.createdAt), expires: format(i.expiresAt) }) }}
          </div>
          <button v-if="!i.usedAt" type="button" class="alert" :disabled="busy" @click="revoke(i.id)">{{ t('invites.revoke') }}</button>
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.page {
  gap: 16px;
}

.panel,
.list {
  width: min(560px, 100%);
  box-sizing: border-box;
}

p {
  margin: 0;
}

.create {
  display: grid;
  gap: 14px;
}

.create button[type='submit'] {
  justify-self: start;
}

.created {
  display: grid;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--panel-border);
}

/* Код и ссылка: перенос по любому месту — длинная ссылка не растягивает панель */
.secret {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  align-items: center;
}

.secret .muted {
  grid-column: 1 / -1;
  font-size: var(--text-xs);
}

code {
  overflow-wrap: anywhere;
  font-family: var(--font-mono);
  color: var(--phosphor);
}

.list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.invite {
  display: grid;
  gap: 6px;
}

.invite button {
  justify-self: start;
}

.dates {
  font-size: var(--text-xs);
}

.status.active {
  color: var(--phosphor);
}

.status.used,
.status.expired {
  color: var(--phosphor-secondary);
}
</style>
