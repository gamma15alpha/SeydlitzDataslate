<script setup lang="ts">
definePageMeta({ titleKey: 'nav.settings' })
const { t } = useI18n()
const { screenTabs, setScreenTabs } = useNavigation()
const { setting: contentLang, setContentLang } = useContentLocale()
// На самих языках: узнают и без знания текущего.
const contentLangOptions = computed<{ value: ContentLang; label: string }[]>(() => [
  { value: 'ui', label: t('demo.contentLangUi') },
  { value: 'ru', label: 'Русский' },
  { value: 'en', label: 'English' },
])
</script>

<template>
  <div class="page">
    <section class="panel">
      <h2>{{ t('settings.screen') }}</h2>
      <label class="check">
        <input type="checkbox" :checked="screenTabs" @change="setScreenTabs(($event.target as HTMLInputElement).checked)">
        {{ t('settings.screenTabs') }}
      </label>
      <p class="muted hint">{{ t('settings.screenTabsHint') }}</p>
    </section>

    <section class="panel">
      <h2>{{ t('settings.content') }}</h2>
      <AppSelect
        :model-value="contentLang" :label="t('demo.contentLang')" :options="contentLangOptions"
        @update:model-value="setContentLang"
      />
    </section>
  </div>
</template>

<style scoped>
.page {
  gap: 16px;
}

.panel {
  display: grid;
  gap: 10px;
  width: min(480px, 100%);
  box-sizing: border-box;
}

h2 {
  margin: 0;
}

.hint {
  margin: 0;
  font-size: var(--text-xs);
}
</style>
