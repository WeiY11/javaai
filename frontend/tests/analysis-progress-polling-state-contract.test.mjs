import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const analysisView = new URL('../src/views/AnalysisView.vue', import.meta.url)

test('analysis progress polling exposes a recoverable failure state when task sync fails', async () => {
  const source = await readFile(analysisView, 'utf8')

  assert.match(source, /progressPollError/)
  assert.match(source, /class="[^"]*\banalysis-progress-status\b[^"]*"/)
  assert.match(source, /进度同步失败/)
  assert.match(source, /@click="retryProgressPolling"/)
  assert.match(source, /async function retryProgressPolling\(\)/)
  assert.match(source, /function startPolling\(id: string\)[\s\S]*progressPollError\.value = ''/)
  assert.match(source, /async function refreshProgress\(\)[\s\S]*try\s*{[\s\S]*analysisApi\.getBatchProgress\(taskId\.value\)/)
  assert.match(source, /catch \(e: any\)[\s\S]*progressPollError\.value = e\.response\?\.data\?\.message \|\| '同步分析进度失败'/)
  assert.match(source, /catch \(e: any\)[\s\S]*window\.clearInterval\(pollTimer\.value\)/)
  assert.match(source, /ElMessage\.error\(progressPollError\.value\)/)
})
