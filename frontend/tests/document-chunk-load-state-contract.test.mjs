import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const documentView = new URL('../src/views/DocumentView.vue', import.meta.url)

test('document chunk drawer shows a recoverable state when chunk loading fails', async () => {
  const source = await readFile(documentView, 'utf8')

  assert.match(source, /chunkLoadError/)
  assert.match(source, /activeChunkDocument/)
  assert.match(source, /class="[^"]*\bchunk-load-status\b[^"]*"/)
  assert.match(source, /@click="reloadChunks"/)
  assert.match(source, /async function reloadChunks\(\)/)
  assert.match(source, /if \(activeChunkDocument\.value\) await openChunks\(activeChunkDocument\.value\)/)
  assert.match(source, /async function openChunks\(doc: Document\)[\s\S]*chunkLoadError\.value = ''/)
  assert.match(source, /chunks\.value = await docApi\.getDocumentChunks\(doc\.id\)/)
  assert.match(source, /catch \(e: any\)[\s\S]*chunkLoadError\.value = e\.response\?\.data\?\.message \|\| '加载文档切片失败'/)
  assert.match(source, /ElMessage\.error\(chunkLoadError\.value\)/)
})
