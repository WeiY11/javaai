import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const routerFile = new URL('../src/router/index.ts', import.meta.url)
const loginView = new URL('../src/views/LoginView.vue', import.meta.url)

test('router preserves intended authenticated route when sending users to login', async () => {
  const source = await readFile(routerFile, 'utf8')

  assert.match(source, /query:\s*\{\s*redirect:\s*to\.fullPath\s*\}/)
  assert.match(source, /next\(\{\s*path:\s*['"]\/login['"]/)
  assert.doesNotMatch(source, /next\(['"]\/login['"]\)/)
})

test('login page returns to a safe internal redirect after authentication', async () => {
  const source = await readFile(loginView, 'utf8')

  assert.match(source, /useRoute/)
  assert.match(source, /safeRedirectTarget/)
  assert.match(source, /route\.query\.redirect/)
  assert.match(source, /redirect\.startsWith\(['"]\/['"]\)/)
  assert.match(source, /!redirect\.startsWith\(['"]\/\/['"]\)/)
  assert.match(source, /router\.push\(safeRedirectTarget\.value\)/)
})
