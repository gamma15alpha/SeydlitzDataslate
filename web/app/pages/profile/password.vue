<script setup lang="ts">
import { api } from '~/api/client'

definePageMeta({ titleKey: 'nav.changePassword' })
const { t } = useI18n()
const auth = useAuth()

const form = reactive({ current: '', password: '', repeat: '' })
const busy = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

async function submit() {
  error.value = success.value = null
  if (form.password !== form.repeat) {
    error.value = t('auth.passwordsMismatch')
    return
  }
  busy.value = true
  try {
    const { error: apiError, response } = await api.PUT('/api/me/password', {
      body: { currentPassword: form.current, newPassword: form.password },
    })
    if (response.ok) {
      Object.assign(form, { current: '', password: '', repeat: '' })
      success.value = t('profile.passwordChanged')
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
    <FormPanel :submit-label="t('profile.changePasswordSubmit')" :busy="busy" :error="error" :success="success" @submit="submit">
      <!-- Для менеджера паролей: чей это пароль -->
      <input type="text" name="username" autocomplete="username" :value="auth.user.value?.login" hidden readonly>
      <label>
        {{ t('profile.currentPassword') }}
        <PasswordInput v-model="form.current" autocomplete="current-password" required />
      </label>
      <label>
        {{ t('profile.newPassword') }}
        <PasswordInput v-model="form.password" autocomplete="new-password" required minlength="8" maxlength="128" />
      </label>
      <label>
        {{ t('auth.passwordRepeat') }}
        <PasswordInput v-model="form.repeat" autocomplete="new-password" required />
      </label>
      <p class="muted hint">{{ t('profile.changePasswordHint') }}</p>
    </FormPanel>
  </div>
</template>

<style scoped>
.hint {
  margin: 0;
  font-size: var(--text-xs);
}
</style>
