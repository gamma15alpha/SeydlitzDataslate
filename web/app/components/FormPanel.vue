<script setup lang="ts">
defineProps<{ submitLabel: string; busy: boolean; error: string | null; success?: string | null }>()
defineEmits<{ submit: [] }>()
const { available } = useServerLink()
</script>

<template>
  <form class="panel form-panel" @submit.prevent="$emit('submit')">
    <slot />
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="success" class="success" role="status">{{ success }}</p>
    <ServerNotice />
    <button type="submit" :disabled="busy || !available">{{ busy ? '…' : submitLabel }}</button>
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
