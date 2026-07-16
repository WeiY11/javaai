import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const documentView = new URL('../src/views/DocumentView.vue', import.meta.url)

test('document page explains the ingestion pipeline instead of showing only an upload box', async () => {
  const source = await readFile(documentView, 'utf8')

  assert.match(source, /class="[^"]*\bingestion-pipeline\b[^"]*"/)
  assert.match(source, /pipelineStages/)
  assert.match(source, /提取文本/)
  assert.match(source, /清洗切片/)
  assert.match(source, /嵌入索引/)
  assert.match(source, /可问答/)
})

test('document page guides missing setup, empty libraries, and failed ingestion recovery', async () => {
  const source = await readFile(documentView, 'utf8')

  assert.match(source, /class="no-kb-guide"/)
  assert.match(source, /to="\/knowledge-bases"/)
  assert.match(source, /class="document-empty-guide"/)
  assert.match(source, /上传第一份文档/)
  assert.match(source, /class="recovery-banner"/)
  assert.match(source, /failedCount/)
  assert.match(source, /processingCount/)
})
