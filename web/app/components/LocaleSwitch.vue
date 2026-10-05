<script setup lang="ts">
const { locale, locales, setLocale } = useI18n()
</script>

<template>
  <nav class="locale-switch">
    <button
      v-for="l in locales" :key="l.code" type="button"
      :aria-pressed="l.code === locale" :disabled="l.code === locale"
      @click="setLocale(l.code)"
    >
      {{ l.code.toUpperCase() }}
    </button>
  </nav>
</template>

<style scoped>
.locale-switch {
  position: fixed;
  top: max(12px, env(safe-area-inset-top));
  right: max(12px, env(safe-area-inset-right));
  z-index: 5;
  display: flex;
  gap: 4px;
}

button {
  padding: 4px 8px;
  font-size: 12px;
}

button:disabled {
  border-color: var(--phosphor);
  opacity: 1;
  cursor: default;
}
</style>
