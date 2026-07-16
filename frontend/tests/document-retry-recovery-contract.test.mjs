import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const documentView = new URL('../src/views/DocumentView.vue', import.meta.url)

test('document page shows progress and a recoverable state when failed ingestion retry fails', async () => {
  const source = await readFile(documentView, 'utf8')

  assert.match(source, /retryingFailedDocuments/)
  assert.match(source, /ingestionActionError/)
  assert.match(source, /class="[^"]*\bingestion-recovery-status\b[^"]*"/)
  assert.match(source, /入库重试失败/)
  assert.match(source, /重新提交失败任务/)
  assert.match(source, /:loading="retryingFailedDocuments"/)
  assert.match(source, /@click="retryFailedDocuments"/)
  assert.match(source, /catch \(e: any\)/)
  assert.match(source, /ingestionActionError\.value = e\.response\?\.data\?\.message \|\| '入库重试失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '入库重试失败'\)/)
})
