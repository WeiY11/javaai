import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardView = new URL('../src/views/DashboardView.vue', import.meta.url)

test('dashboard limits detailed runtime health to administrators', async () => {
  const source = await readFile(dashboardView, 'utf8')

  assert.match(source, /<div v-if="isAdmin" class="workspace-card health-panel"/)
  assert.match(
    source,
    /watch\(\s*isAdmin,\s*canViewRuntimeHealth => \{\s+if \(canViewRuntimeHealth && !runtimeHealth\.value && !runtimeHealthLoading\.value\) \{\s+void loadRuntimeHealth\(\)\s+\}\s+\},\s*\{ immediate: true \}\s*\)/
  )
  assert.match(source, /onMounted\(\(\) => \{\s+void loadDashboardData\(\)\s+\}\)/)
})
