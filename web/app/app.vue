<!-- Каркас: веб-клиент на Nuxt. Корпус датаслейта и лист персонажа переносятся срезами. -->
<template>
  <main class="placeholder">
    <h1>Seydlitz Dataslate</h1>
    <p>Каркас веб-клиента: Nuxt + TypeScript.</p>
  </main>
</template>

<style scoped>
.placeholder {
  display: grid;
  place-content: center;
  min-height: 100dvh;
  text-align: center;
  text-shadow: 0 0 6px var(--phosphor-glow);
}

h1 {
  margin: 0 0 12px;
  font-family: var(--font-inscription);
  font-weight: 400;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

p {
  margin: 0;
  color: var(--phosphor-dim);
}
</style>
