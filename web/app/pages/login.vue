<script setup lang="ts">
definePageMeta({ public: true, layout: 'auth' })
const { t } = useI18n()
useHead({ title: () => t('auth.loginTitle') })

const auth = useAuth()
const route = useRoute()
const form = reactive({ login: '', password: '' })
const error = ref<string | null>(null)
const busy = ref(false)

async function submit() {
  busy.value = true
  error.value = await auth.login(form.login.trim(), form.password)
  busy.value = false
  if (!error.value) await navigateTo(safeRedirect(route.query.redirect))
}
</script>

<template>
  <AuthPanel :title="t('auth.loginTitle')" :submit-label="t('auth.loginSubmit')" :error="error" :busy="busy" @submit="submit">
    <p v-if="auth.notice.value" class="warning" role="status">{{ t(auth.notice.value) }}</p>
    <label>
      {{ t('auth.login') }}
      <input v-model="form.login" name="username" autocomplete="username" required autofocus>
    </label>
    <label>
      {{ t('auth.password') }}
      <PasswordInput v-model="form.password" name="password" autocomplete="current-password" required />
    </label>
    <template #footer>
      {{ t('auth.loginFooter') }}
    </template>
  </AuthPanel>
</template>
