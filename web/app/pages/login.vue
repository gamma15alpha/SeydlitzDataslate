<script setup lang="ts">
definePageMeta({ public: true })
useHead({ title: 'Вход — Seydlitz Dataslate' })

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
  <AuthPanel title="Вход" submit-label="Войти" :error="error" :busy="busy" @submit="submit">
    <p v-if="auth.notice.value" class="warning" role="status">{{ auth.notice.value }}</p>
    <label>
      Логин
      <input v-model="form.login" name="username" autocomplete="username" required autofocus>
    </label>
    <label>
      Пароль
      <input v-model="form.password" type="password" name="password" autocomplete="current-password" required>
    </label>
    <template #footer>
      Нет аккаунта? Регистрация — по инвайту от администратора.
    </template>
  </AuthPanel>
</template>
