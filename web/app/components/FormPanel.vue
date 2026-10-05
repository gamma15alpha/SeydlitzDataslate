<script setup lang="ts">
// Форма в панели: поля — в слоте, под ними ошибка или сообщение об успехе и кнопка отправки.
defineProps<{ submitLabel: string; busy: boolean; error: string | null; success?: string | null }>()
defineEmits<{ submit: [] }>()
</script>

<template>
  <form class="panel form-panel" @submit.prevent="$emit('submit')">
    <slot />
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="success" class="success" role="status">{{ success }}</p>
    <button type="submit" :disabled="busy">{{ busy ? '…' : submitLabel }}</button>
  </form>
</template>

<style scoped>
.form-panel {
  display: grid;
  gap: 14px;
  width: min(420px, 100%);
  box-sizing: border-box;
}

button {
  justify-self: start;
}

.success {
  margin: 0;
}
</style>
