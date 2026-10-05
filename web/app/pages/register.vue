<script setup lang="ts">
definePageMeta({ public: true, layout: 'auth' })
const { t } = useI18n()
useHead({ title: () => t('auth.registerTitle') })

const auth = useAuth()
const route = useRoute()
// Ссылка-приглашение: /register?invite=КОД
const form = reactive({
  invite: typeof route.query.invite === 'string' ? route.query.invite : '',
  login: '',
  displayName: '',
  password: '',
  repeat: '',
})
const error = ref<string | null>(null)
const busy = ref(false)

async function submit() {
  if (form.password !== form.repeat) {
    error.value = t('auth.passwordsMismatch')
    return
  }
  busy.value = true
  error.value = await auth.register({
    invite: form.invite,
    login: form.login.trim(),
    password: form.password,
    displayName: form.displayName.trim() || undefined,
  })
  busy.value = false
  if (!error.value) await navigateTo('/')
}
</script>

<template>
  <AuthPanel :title="t('auth.registerTitle')" :submit-label="t('auth.registerSubmit')" :error="error" :busy="busy" @submit="submit">
    <label>
      {{ t('auth.invite') }}
      <input v-model="form.invite" autocomplete="off" spellcheck="false" required>
    </label>
    <label>
      {{ t('auth.login') }}
      <input
        v-model="form.login" name="username" autocomplete="username" required
        minlength="3" maxlength="32" pattern="[A-Za-z0-9_.\-]+"
        :title="t('auth.loginHint')"
      >
    </label>
    <label>
      {{ t('auth.displayName') }} <span class="muted">{{ t('auth.displayNameHint') }}</span>
      <input v-model="form.displayName" name="nickname" autocomplete="nickname" maxlength="64">
    </label>
    <label>
      {{ t('auth.password') }}
      <PasswordInput v-model="form.password" autocomplete="new-password" required minlength="8" maxlength="128" />
    </label>
    <label>
      {{ t('auth.passwordRepeat') }}
      <PasswordInput v-model="form.repeat" autocomplete="new-password" required />
    </label>
    <template #footer>
      {{ t('auth.haveAccount') }} <NuxtLink to="/login">{{ t('auth.loginSubmit') }}</NuxtLink>
    </template>
  </AuthPanel>
</template>
