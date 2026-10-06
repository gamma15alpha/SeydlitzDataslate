<script setup lang="ts">
// Шестерня — символ #cog из DataslateCasing.
defineProps<{ name: IconName }>()
</script>

<template>
  <svg class="app-icon" viewBox="0 0 24 24" aria-hidden="true">
    <use v-if="name === 'settings'" href="#cog" x="2" y="2" width="20" height="20" />
    <g v-else fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="square">
      <template v-if="name === 'sheet'">
        <path d="M6 3h9l3 3v15H6z" />
        <path d="M9 10h6M9 14h6M9 18h4" />
      </template>
      <template v-else-if="name === 'book'">
        <path d="M4 5h6a2 2 0 0 1 2 2v13a2 2 0 0 0-2-2H4z" />
        <path d="M20 5h-6a2 2 0 0 0-2 2v13a2 2 0 0 1 2-2h6z" />
      </template>
      <template v-else-if="name === 'user'">
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1" />
      </template>
      <path v-else-if="name === 'back'" d="M15 5l-7 7 7 7" />
    </g>
  </svg>
</template>

<style scoped>
.app-icon {
  display: block;
  width: 20px;
  height: 20px;
  flex: none;
  /* свечение люминофора: у SVG нет text-shadow */
  filter: drop-shadow(0 0 3px var(--phosphor-glow));
}
</style>
