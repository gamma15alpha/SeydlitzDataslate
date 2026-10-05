<script setup lang="ts">
defineProps<{ title: string; error: string | null; busy: boolean; submitLabel: string }>()
defineEmits<{ submit: [] }>()
</script>

<template>
  <main class="page">
    <form class="panel" @submit.prevent="$emit('submit')">
      <h1>{{ title }}</h1>
      <slot />
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="busy">{{ busy ? '…' : submitLabel }}</button>
      <p class="muted"><slot name="footer" /></p>
    </form>
  </main>
</template>

<style scoped>
.panel {
  display: grid;
  gap: 14px;
  width: min(360px, 100%);
  box-sizing: border-box;
}

h1 {
  margin: 0 0 8px;
  text-align: center;
}
</style>
