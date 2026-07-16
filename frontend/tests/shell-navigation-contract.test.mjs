import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const shellView = new URL('../src/components/AppShell.vue', import.meta.url)

test('application shell groups navigation by evidence workflow stage', async () => {
  const source = await readFile(shellView, 'utf8')

  assert.match(source, /shellModuleGroups/)
  assert.match(source, /class="[^"]*\brail-group\b[^"]*"/)
  assert.match(source, /class="[^"]*\bnav-group-title\b[^"]*"/)
  assert.match(source, /准备资料/)
  assert.match(source, /证据问答/)
  assert.match(source, /成果输出/)
  assert.match(source, /v-for="group in visibleShellModuleGroups"/)
  assert.match(source, /v-for="item in group\.items"/)
})

test('application shell exposes current module context in the top bar', async () => {
  const source = await readFile(shellView, 'utf8')

  assert.match(source, /class="[^"]*\bshell-context-strip\b[^"]*"/)
  assert.match(source, /activeModuleGroup/)
  assert.match(source, /activeWorkflowIndex/)
  assert.match(source, /activeWorkflowLabel/)
  assert.match(source, /routeContextItems/)
  assert.match(source, /当前阶段/)
  assert.match(source, /下一步/)
})
