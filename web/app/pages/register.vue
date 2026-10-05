<script setup lang="ts">
definePageMeta({ public: true })
useHead({ title: 'Регистрация — Seydlitz Dataslate' })

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
    error.value = 'Пароли не совпадают'
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
  <AuthPanel title="Регистрация" submit-label="Зарегистрироваться" :error="error" :busy="busy" @submit="submit">
    <label>
      Инвайт
      <input v-model="form.invite" autocomplete="off" spellcheck="false" required>
    </label>
    <label>
      Логин
      <input
        v-model="form.login" name="username" autocomplete="username" required
        minlength="3" maxlength="32" pattern="[A-Za-z0-9_.\-]+"
        title="Латиница, цифры, «_», «.», «-»"
      >
    </label>
    <label>
      Имя <span class="muted">— необязательно, по умолчанию логин</span>
      <input v-model="form.displayName" name="nickname" autocomplete="nickname" maxlength="64">
    </label>
    <label>
      Пароль
      <input v-model="form.password" type="password" autocomplete="new-password" required minlength="8" maxlength="128">
    </label>
    <label>
      Пароль ещё раз
      <input v-model="form.repeat" type="password" autocomplete="new-password" required>
    </label>
    <template #footer>
      Уже есть аккаунт? <NuxtLink to="/login">Войти</NuxtLink>
    </template>
  </AuthPanel>
</template>
