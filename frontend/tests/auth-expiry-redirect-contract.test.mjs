import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const requestFile = new URL('../src/utils/request.ts', import.meta.url)

test('expired sessions redirect to login with the current workspace route preserved', async () => {
  const source = await readFile(requestFile, 'utf8')

  assert.match(source, /loginRedirectUrl/)
  assert.match(source, /window\.location\.pathname/)
  assert.match(source, /window\.location\.search/)
  assert.match(source, /window\.location\.hash/)
  assert.match(source, /encodeURIComponent\(currentPath\)/)
  assert.match(source, /redirect=/)
  assert.match(source, /window\.location\.href\s*=\s*loginRedirectUrl/)
  assert.doesNotMatch(source, /window\.location\.href\s*=\s*['"]\/login['"]/)
})
