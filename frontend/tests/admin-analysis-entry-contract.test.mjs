import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const shellView = new URL('../src/components/AppShell.vue', import.meta.url)
const dashboardView = new URL('../src/views/DashboardView.vue', import.meta.url)

test('analysis entry points are hidden from non-administrators', async () => {
  const [shellSource, dashboardSource] = await Promise.all([
    readFile(shellView, 'utf8'),
    readFile(dashboardView, 'utf8')
  ])

  assert.match(shellSource, /visibleShellModuleGroups/)
  assert.match(shellSource, /authStore\.user\?\.systemRole === 'ADMIN'/)
  assert.match(shellSource, /v-for="group in visibleShellModuleGroups"/)
  assert.match(shellSource, /path: '\/analysis'[\s\S]*adminOnly: true/)
  assert.match(dashboardSource, /authStore\.user\?\.systemRole === 'ADMIN'/)
  assert.match(dashboardSource, /to: '\/analysis'[\s\S]*adminOnly: true/)
})
