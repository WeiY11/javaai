import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardView = new URL('../src/views/DashboardView.vue', import.meta.url)

test('dashboard surfaces backend runtime health with degraded component detail and retry', async () => {
  const source = await readFile(dashboardView, 'utf8')

  assert.match(source, /getRuntimeHealth/)
  assert.match(source, /runtimeHealth/)
  assert.match(source, /runtimeHealthError/)
  assert.match(source, /class="[^"]*\bruntime-health-status\b[^"]*"/)
  assert.match(source, /运行健康/)
  assert.match(source, /后端状态/)
  assert.match(source, /组件状态/)
  assert.match(source, /@click="loadRuntimeHealth"/)
  assert.match(source, /async function loadRuntimeHealth\(\)/)
  assert.match(source, /runtimeHealth\.value = await getRuntimeHealth\(\)/)
  assert.match(source, /runtimeHealthError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '运行健康检查失败'/)
})
