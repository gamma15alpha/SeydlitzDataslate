<script setup lang="ts">
import { api } from '~/api/client'

definePageMeta({ titleKey: 'nav.changeLogin' })
const { t } = useI18n()
const auth = useAuth()

const form = reactive({ login: auth.user.value?.login ?? '', password: '' })
const busy = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

async function submit() {
  busy.value = true
  error.value = success.value = null
  try {
    const { data, error: apiError, response } = await api.PUT('/api/me/login', { body: { login: form.login.trim(), password: form.password } })
    if (data) {
      auth.updateUser(data)
      form.password = ''
      success.value = t('profile.loginChanged', { login: data.login })
    } else {
      error.value = errorMessage(t, apiError, response)
    }
  } catch {
    error.value = t('errors.noConnection')
  }
  busy.value = false
}
</script>

<template>
  <div class="page">
    <FormPanel :submit-label="t('profile.changeLoginSubmit')" :busy="busy" :error="error" :success="success" @submit="submit">
      <label>
        {{ t('profile.newLogin') }}
        <input
          v-model="form.login" name="username" autocomplete="username" required
          minlength="3" maxlength="32" pattern="[A-Za-z0-9_.\-]+" :title="t('auth.loginHint')"
        >
      </label>
      <label>
        {{ t('profile.currentPassword') }}
        <PasswordInput v-model="form.password" name="password" autocomplete="current-password" required />
      </label>
      <p class="muted hint">{{ t('profile.changeLoginHint') }}</p>
    </FormPanel>
  </div>
</template>

<style scoped>
.hint {
  margin: 0;
  font-size: var(--text-xs);
}
</style>
