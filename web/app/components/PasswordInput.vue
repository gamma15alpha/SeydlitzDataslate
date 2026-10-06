<script setup lang="ts">
// Атрибуты — на <input>, а не на обёртку.
defineOptions({ inheritAttrs: false })
const model = defineModel<string>({ required: true })
const { t } = useI18n()
const visible = ref(false)
</script>

<template>
  <span class="password-input">
    <input v-model="model" v-bind="$attrs" :type="visible ? 'text' : 'password'" spellcheck="false" autocapitalize="off">
    <button
      type="button" class="toggle" :aria-pressed="visible"
      :aria-label="visible ? t('auth.hidePassword') : t('auth.showPassword')"
      @click="visible = !visible"
    >
      {{ visible ? t('auth.hide') : t('auth.show') }}
    </button>
  </span>
</template>

<style scoped>
.password-input {
  position: relative;
  display: grid;
}

input {
  padding-right: 6.5em;
}

.toggle {
  position: absolute;
  top: 1px;
  right: 1px;
  bottom: 1px;
  min-width: 5.5em;
  padding: 0 10px;
  border: 0;
  border-left: 1px solid var(--phosphor-faint);
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  color: var(--phosphor-secondary);
}

.toggle[aria-pressed='true'] {
  color: var(--phosphor);
}

.toggle:hover:not(:disabled) {
  border-color: var(--phosphor-faint);
  color: var(--phosphor);
}

@media (pointer: coarse) {
  .toggle {
    min-height: 0;
    min-width: 44px;
  }
}
</style>
