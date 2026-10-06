<script setup lang="ts">
definePageMeta({ titleKey: 'nav.profile' })
const { t } = useI18n()
const auth = useAuth()
const { available } = useServerLink()
const error = ref<string | null>(null)

async function logout() {
  error.value = await auth.logout()
}
</script>

<template>
  <div class="page">
    <section class="panel">
      <p class="name">{{ auth.user.value?.displayName }}</p>
      <p class="muted">
        {{ auth.user.value?.login }}<template v-if="auth.user.value?.isAdmin"> · {{ t('profile.admin') }}</template>
      </p>
    </section>

    <nav class="links">
      <NuxtLink to="/profile/login">{{ t('nav.changeLogin') }} ›</NuxtLink>
      <NuxtLink to="/profile/password">{{ t('nav.changePassword') }} ›</NuxtLink>
      <NuxtLink to="/profile/sessions">{{ t('nav.sessions') }} ›</NuxtLink>
      <NuxtLink v-if="auth.user.value?.isAdmin" to="/profile/invites">{{ t('nav.invites') }} ›</NuxtLink>
    </nav>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <ServerNotice />
    <button type="button" class="alert" :disabled="!available" @click="logout">{{ t('auth.logout') }}</button>
  </div>
</template>

<style scoped>
.page {
  gap: 16px;
}

.panel,
.links {
  width: min(420px, 100%);
  box-sizing: border-box;
}

p {
  margin: 0;
}

.name {
  margin-bottom: 4px;
  font-family: var(--font-inscription);
  font-size: var(--text-l);
  letter-spacing: 2px;
}

.links {
  display: grid;
}

.links a {
  padding: 10px 0;
  border-bottom: 1px solid var(--panel-border);
  text-decoration: none;
}
</style>
