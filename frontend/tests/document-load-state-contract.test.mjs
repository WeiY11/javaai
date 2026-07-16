import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const documentView = new URL('../src/views/DocumentView.vue', import.meta.url)

test('document page shows a recoverable state when the document list fails to load', async () => {
  const source = await readFile(documentView, 'utf8')

  assert.match(source, /documentLoadError/)
  assert.match(source, /class="document-load-status"/)
  assert.match(source, /文档列表加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /@click="loadDocuments"/)
  assert.match(source, /catch \(e: any\)/)
  assert.match(source, /documentLoadError\.value/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '加载文档失败'\)/)
})
