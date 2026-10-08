import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

// Алиас — как в nuxt.config.ts.
export default defineConfig({
  resolve: {
    alias: {
      'dataslate-core': fileURLToPath(new URL('../shared/build/dist/js/productionLibrary/dataslate-core.mjs', import.meta.url)),
    },
  },
})
