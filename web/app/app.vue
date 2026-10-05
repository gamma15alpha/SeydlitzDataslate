<script setup lang="ts">
const { locale, t } = useI18n()
useHead({ htmlAttrs: { lang: locale }, titleTemplate: page => (page ? t('app.pageTitle', { page }) : t('app.title')) })
</script>

<template>
  <DataslateCasing>
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </DataslateCasing>
</template>
