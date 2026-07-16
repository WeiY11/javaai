import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const citationView = new URL('../src/views/CitationView.vue', import.meta.url)

test('citation page shows progress and a recoverable state when citation export fails', async () => {
  const source = await readFile(citationView, 'utf8')

  assert.match(source, /exporting/)
  assert.match(source, /exportError/)
  assert.match(source, /class="[^"]*\bcitation-export-status\b[^"]*"/)
  assert.match(source, /引用生成失败/)
  assert.match(source, /重新生成/)
  assert.match(source, /:loading="exporting"/)
  assert.match(source, /@click="handleExport"/)
  assert.match(source, /catch \(e: any\)/)
  assert.match(source, /exportError\.value/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '引用生成失败'\)/)
})
