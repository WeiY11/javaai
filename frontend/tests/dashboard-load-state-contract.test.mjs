import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardView = new URL('../src/views/DashboardView.vue', import.meta.url)

test('dashboard exposes loading, failure, and retry states for workspace data', async () => {
  const source = await readFile(dashboardView, 'utf8')

  assert.match(source, /dashboardLoading/)
  assert.match(source, /dashboardLoadError/)
  assert.match(source, /loadDashboardData/)
  assert.match(source, /class="dashboard-load-status"/)
  assert.match(source, /数据加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /v-loading="dashboardLoading"/)
  assert.match(source, /@click="loadDashboardData"/)
  assert.doesNotMatch(source, /onMounted\(async \(\) => \{\s*await Promise\.all/)
})
