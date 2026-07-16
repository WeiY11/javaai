import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const loginView = new URL('../src/views/LoginView.vue', import.meta.url)

test('login page presents an operational auth entry instead of a marketing splash', async () => {
  const source = await readFile(loginView, 'utf8')

  assert.match(source, /class="[^"]*\bauth-entry-panel\b[^"]*"/)
  assert.match(source, /账号入口/)
  assert.match(source, /authModeSummary/)
  assert.match(source, /credentialHint/)
  assert.match(source, /后端认证/)
  assert.match(source, /工作台入口/)
  assert.doesNotMatch(source, /glow-orb/)
  assert.doesNotMatch(source, /顶级知识大脑|知识宇宙/)
})

test('login page exposes credential readiness and mode-safe submit behavior', async () => {
  const source = await readFile(loginView, 'utf8')

  assert.match(source, /authChecklist/)
  assert.match(source, /submitLabel/)
  assert.match(source, /canSubmit/)
  assert.match(source, /class="[^"]*\bauth-error-hint\b[^"]*"/)
  assert.match(source, /:disabled="!canSubmit"/)
  assert.match(source, /注册并进入/)
  assert.match(source, /登录工作台/)
})
