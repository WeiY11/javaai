import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const documentView = new URL('../src/views/DocumentView.vue', import.meta.url)

test('document row actions show progress and recover when retry or delete fails', async () => {
  const source = await readFile(documentView, 'utf8')

  assert.match(source, /documentActionError/)
  assert.match(source, /deletingDocumentId/)
  assert.match(source, /retryingDocumentId/)
  assert.match(source, /class="[^"]*\bdocument-action-status\b[^"]*"/)
  assert.match(source, /文档操作失败/)
  assert.match(source, /重新加载文档/)
  assert.match(source, /:loading="retryingDocumentId === row\.id"/)
  assert.match(source, /:loading="deletingDocumentId === row\.id"/)
  assert.match(source, /documentActionError\.value = ''/)
  assert.match(source, /documentActionError\.value = e\.response\?\.data\?\.message \|\| '文档操作失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '文档操作失败'\)/)
})
