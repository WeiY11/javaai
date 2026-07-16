import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const styleFile = new URL('../src/style.css', import.meta.url)

function ruleBody(source, selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = source.match(new RegExp(`${escapedSelector}\\s*\\{([^}]*)\\}`))
  assert.ok(match, `missing CSS rule: ${selector}`)
  return match[1]
}

test('global style does not depend on remote font delivery', async () => {
  const source = await readFile(styleFile, 'utf8')

  assert.doesNotMatch(source, /@import\s+url\(['"]?https?:\/\//)
  assert.doesNotMatch(source, /fonts\.googleapis\.com/)
  assert.doesNotMatch(source, /['"](?:Inter|Outfit)['"]/)
  assert.match(source, /--font-sans:\s*system-ui,\s*-apple-system/)
  assert.match(source, /--font-display:\s*var\(--font-sans\)/)
})

test('global workbench chrome avoids layout-shifting hover motion', async () => {
  const source = await readFile(styleFile, 'utf8')

  assert.doesNotMatch(ruleBody(source, '.workspace-card:hover, .metric-card:hover'), /transform:\s*translateY/)
  assert.doesNotMatch(ruleBody(source, '.el-button--primary:hover'), /transform:\s*translateY/)
  assert.doesNotMatch(source, /letter-spacing:\s*-/)
  assert.match(source, /transition:\s*border-color 0\.2s ease,\s*box-shadow 0\.2s ease/)
  assert.match(source, /--shadow-glow:\s*none/)
})
