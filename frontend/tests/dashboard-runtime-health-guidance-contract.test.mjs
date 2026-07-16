import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardView = new URL('../src/views/DashboardView.vue', import.meta.url)

test('dashboard gives actionable remediation for degraded runtime components', async () => {
  const source = await readFile(dashboardView, 'utf8')

  assert.match(source, /componentActionHint/)
  assert.match(source, /处理建议/)
  assert.match(source, /NOT_CONFIGURED/)
  assert.match(source, /ES_URIS/)
  assert.match(source, /MINIO_ENDPOINT/)
  assert.match(source, /POSTGRES_HOST/)
  assert.match(source, /componentActionHint\(name, component\.status, component\.action, component\.required\)/)
  assert.match(
    source,
    /v-if="componentActionHint\(name, component\.status, component\.action, component\.required\)"/
  )
  assert.match(
    source,
    /function componentActionHint\(name: string, status\?: string, action\?: string, required\?: boolean\)/
  )
  assert.match(source, /if \(action\) return action/)
})
