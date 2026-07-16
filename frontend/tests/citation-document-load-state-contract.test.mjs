import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const citationView = new URL('../src/views/CitationView.vue', import.meta.url)

test('citation page shows a recoverable state when citation documents cannot be loaded', async () => {
  const source = await readFile(citationView, 'utf8')

  assert.match(source, /documentsLoadError/)
  assert.match(source, /class="[^"]*\bcitation-load-status\b[^"]*"/)
  assert.match(source, /引用文档加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /@click="loadDocuments"/)
  assert.match(source, /catch \(e: any\)/)
  assert.match(source, /documentsLoadError\.value/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '加载引用文档失败'\)/)
  assert.match(source, /!documentsLoadError/)
})
