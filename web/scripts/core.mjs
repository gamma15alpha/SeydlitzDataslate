// Собирает ядро правил (shared, Kotlin/JS) для алиаса dataslate-core; аргументы — в Gradle (например, --continuous).
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../..', import.meta.url))
const gradlew = join(root, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')
const { status } = spawnSync(`"${gradlew}"`, [':shared:jsProductionLibraryDistribution', '-q', ...process.argv.slice(2)], {
  cwd: root,
  stdio: 'inherit',
  shell: true,
})
process.exit(status ?? 1)
