import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const analysisView = new URL('../src/views/AnalysisView.vue', import.meta.url)

test('analysis page shows a recoverable state when result history cannot be loaded', async () => {
  const source = await readFile(analysisView, 'utf8')

  assert.match(source, /resultsLoadError/)
  assert.match(source, /class="[^"]*\banalysis-load-status\b[^"]*"/)
  assert.match(source, /分析结果加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /@click="loadResults"/)
  assert.match(source, /catch \(e: any\)/)
  assert.match(source, /resultsLoadError\.value/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '加载分析结果失败'\)/)
  assert.match(source, /!resultsLoadError/)
})
