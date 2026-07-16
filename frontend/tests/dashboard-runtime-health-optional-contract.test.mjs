import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardView = new URL('../src/views/DashboardView.vue', import.meta.url)
const healthApi = new URL('../src/api/health.ts', import.meta.url)

test('dashboard treats disabled optional runtime components as informational', async () => {
  const [dashboardSource, healthSource] = await Promise.all([
    readFile(dashboardView, 'utf8'),
    readFile(healthApi, 'utf8')
  ])

  assert.match(healthSource, /required\?: boolean/)
  assert.match(dashboardSource, /function isRequiredComponent\(component: RuntimeHealthComponent\)/)
  assert.match(dashboardSource, /component\.required !== false/)
  assert.match(dashboardSource, /degradedRequiredComponentCount/)
  assert.match(dashboardSource, /optionalComponentCount/)
  assert.match(dashboardSource, /component-optional-badge/)
  assert.match(
    dashboardSource,
    /componentActionHint\(name, component\.status, component\.action, component\.required\)/
  )
  assert.match(
    dashboardSource,
    /function componentActionHint\(name: string, status\?: string, action\?: string, required\?: boolean\)/
  )
  assert.match(dashboardSource, /if \(required === false\) return ''/)
})
