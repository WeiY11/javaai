import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

test('analysis page explains the batch workflow before users start a run', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/AnalysisView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\banalysis-command-center\b[^"]*"/)
  assert.match(source, /分析工作流/)
  assert.match(source, /selectedFileSummary/)
  assert.match(source, /analysisReadiness/)
  assert.match(source, /准备分析/)
  assert.match(source, /选择文件/)
  assert.match(source, /启动批处理/)
})

test('analysis page gives empty-state and result summaries instead of bare tables', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/AnalysisView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\banalysis-empty-guide\b[^"]*"/)
  assert.match(source, /当前目录没有可分析文件/)
  assert.match(source, /class="[^"]*\banalysis-result-summary\b[^"]*"/)
  assert.match(source, /resultProviderCount/)
  assert.match(source, /exportSelectionLabel/)
  assert.match(source, /可导出结果/)
})
