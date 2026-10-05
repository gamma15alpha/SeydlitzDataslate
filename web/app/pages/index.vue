<script setup lang="ts">
const auth = useAuth()
const error = ref<string | null>(null)

async function logout() {
  error.value = await auth.logout()
}
</script>

<template>
  <main class="screen">
    <h1>Seydlitz Dataslate</h1>
    <p>{{ auth.user.value?.displayName }} <span class="muted">({{ auth.user.value?.login }})</span></p>
    <p v-if="auth.offline.value" class="warning">Нет связи с сервером — работа офлайн</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <NuxtLink to="/sessions">Активные сессии</NuxtLink>
    <button type="button" @click="logout">Выйти</button>
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
