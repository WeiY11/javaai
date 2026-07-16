import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const documentView = new URL('../src/views/DocumentView.vue', import.meta.url)

test('document upload shows progress and a recoverable failure state', async () => {
  const source = await readFile(documentView, 'utf8')

  assert.match(source, /uploadingDocument/)
  assert.match(source, /uploadActionError/)
  assert.match(source, /class="[^"]*\bdocument-upload-status\b[^"]*"/)
  assert.match(source, /上传文档失败/)
  assert.match(source, /重新加载文档/)
  assert.match(source, /v-loading="uploadingDocument"/)
  assert.match(source, /:disabled="!selectedKbId \|\| uploadingDocument"/)
  assert.match(source, /uploadActionError\.value = ''/)
  assert.match(source, /uploadActionError\.value = e\.response\?\.data\?\.message \|\| '上传文档失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '上传文档失败'\)/)
  assert.match(source, /finally\s*{\s*uploadingDocument\.value = false\s*}/s)
})
