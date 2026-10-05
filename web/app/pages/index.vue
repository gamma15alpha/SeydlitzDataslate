<script setup lang="ts">
const { t } = useI18n()
const auth = useAuth()
const error = ref<string | null>(null)

async function logout() {
  error.value = await auth.logout()
}
</script>

<template>
  <LocaleSwitch />
  <main class="screen">
    <h1>{{ t('app.title') }}</h1>
    <p>{{ auth.user.value?.displayName }} <span class="muted">({{ auth.user.value?.login }})</span></p>
    <p v-if="auth.offline.value" class="warning">{{ t('auth.offline') }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <NuxtLink to="/demo">{{ t('demo.link') }}</NuxtLink>
    <NuxtLink to="/sessions">{{ t('sessions.link') }}</NuxtLink>
    <button type="button" @click="logout">{{ t('auth.logout') }}</button>
  </main>
</template>

<style scoped>
.screen {
  gap: 12px;
  text-align: center;
}

h1 {
  margin: 0 0 12px;
}

p {
  margin: 0;
}
</style>
