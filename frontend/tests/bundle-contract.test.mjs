import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async entry => {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) return sourceFiles(target)
    return /\.(?:ts|tsx|vue)$/.test(entry.name) ? [target] : []
  }))
  return nested.flat()
}

test('frontend uses modular ECharts imports instead of the full bundle', async () => {
  const files = await sourceFiles(sourceRoot)
  const offenders = []

  for (const file of files) {
    const source = await readFile(file, 'utf8')
    if (/from\s+['"]echarts['"]/.test(source)) {
      offenders.push(path.relative(sourceRoot, file))
    }
  }

  assert.deepEqual(offenders, [])
})

test('Element Plus registers only the components used by templates', async () => {
  const files = await sourceFiles(sourceRoot)
  const mainSource = await readFile(path.join(sourceRoot, 'main.ts'), 'utf8')
  const vueSources = await Promise.all(
    files.filter(file => file.endsWith('.vue')).map(file => readFile(file, 'utf8'))
  )
  const templateSource = vueSources.join('\n')
  const usedTags = new Set([...templateSource.matchAll(/<el-([a-z0-9-]+)/g)].map(match => match[1]))
  const componentName = tag => `El${tag.split('-').map(part => part[0].toUpperCase() + part.slice(1)).join('')}`
  const requiredPlugins = [...usedTags].map(componentName)
  if (/\bv-loading\b/.test(templateSource)) requiredPlugins.push('ElLoading')

  assert.doesNotMatch(
    mainSource,
    /import\s+ElementPlus\s+from\s+['"]element-plus['"]/
  )
  for (const plugin of requiredPlugins) {
    assert.match(mainSource, new RegExp(`\\b${plugin}\\b`), `${plugin} must be imported`)
    assert.match(mainSource, new RegExp(`app\\.use\\(${plugin}\\)`), `${plugin} must be registered`)
  }
})
