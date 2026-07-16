import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

async function exists(relativePath) {
  try {
    await access(path.join(sourceRoot, relativePath))
    return true
  } catch {
    return false
  }
}

test('authenticated root opens a dashboard before task-specific tools', async () => {
  const routerSource = await readFile(path.join(sourceRoot, 'router/index.ts'), 'utf8')
  const shellSource = await readFile(path.join(sourceRoot, 'components/AppShell.vue'), 'utf8')

  assert.match(routerSource, /path:\s*['"]\/['"][\s\S]*name:\s*['"]Dashboard['"]/)
  assert.match(routerSource, /component:\s*\(\)\s*=>\s*import\(['"]\.\.\/views\/DashboardView\.vue['"]\)/)
  assert.match(routerSource, /path:\s*['"]\/chat['"][\s\S]*name:\s*['"]Chat['"]/)
  assert.match(shellSource, /path:\s*['"]\/['"][^}]*label:\s*['"]工作台['"]/)
  assert.match(shellSource, /path:\s*['"]\/chat['"][^}]*label:\s*['"]智能问答['"]/)
})

test('frontend no longer carries starter template artifacts', async () => {
  assert.equal(await exists('components/HelloWorld.vue'), false)
  assert.equal(await exists('assets/vite.svg'), false)
  assert.equal(await exists('assets/vue.svg'), false)
})
