import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const analysisView = new URL('../src/views/AnalysisView.vue', import.meta.url)

test('analysis page shows a recoverable state when the file browser cannot be loaded', async () => {
  const source = await readFile(analysisView, 'utf8')

  assert.match(source, /filesLoadError/)
  assert.match(source, /class="[^"]*\banalysis-file-load-status\b[^"]*"/)
  assert.match(source, /文件列表加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /@click="loadFiles\(currentDir\)"/)
  assert.match(source, /filesLoadError\.value/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '加载文件失败'\)/)
  assert.match(source, /!filesLoadError/)
})
