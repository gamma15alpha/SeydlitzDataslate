<!-- Вход и регистрация: только форма по центру экрана, без разделов и строки приложения -->
<template>
  <div class="auth-layout">
    <slot />
  </div>
</template>

<style scoped>
.auth-layout {
  height: 100%;
  overflow: auto;
}
</style>
