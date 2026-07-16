import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appShell = new URL('../src/components/AppShell.vue', import.meta.url)
const authStore = new URL('../src/stores/auth.store.ts', import.meta.url)

test('auth store reports whether current-user restoration succeeded', async () => {
  const source = await readFile(authStore, 'utf8')

  assert.match(source, /async function fetchUser\(\): Promise<boolean>/)
  assert.match(source, /return true/)
  assert.match(source, /return false/)
})

test('application shell redirects failed session restoration back through login with route context', async () => {
  const source = await readFile(appShell, 'utf8')

  assert.match(source, /async function restoreSession/)
  assert.match(source, /await authStore\.fetchUser\(\)/)
  assert.match(source, /router\.replace\(\{\s*path:\s*['"]\/login['"],\s*query:\s*\{\s*redirect:\s*route\.fullPath\s*\}/)
  assert.match(source, /void restoreSession\(\)/)
  assert.doesNotMatch(source, /if \(authStore\.isLoggedIn\) authStore\.fetchUser\(\)/)
})
