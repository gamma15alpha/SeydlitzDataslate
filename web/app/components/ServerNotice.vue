<script setup lang="ts">
const { t } = useI18n()
const { available } = useServerLink()
</script>

<template>
  <p v-if="!available" class="warning" role="status">{{ t('errors.needServer') }}</p>
</template>
