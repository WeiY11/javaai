import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const analysisView = new URL('../src/views/AnalysisView.vue', import.meta.url)

test('analysis batch start shows progress and a recoverable failure state', async () => {
  const source = await readFile(analysisView, 'utf8')

  assert.match(source, /startingAnalysis/)
  assert.match(source, /analysisStartError/)
  assert.match(source, /failedStartMode/)
  assert.match(source, /class="[^"]*\banalysis-start-status\b[^"]*"/)
  assert.match(source, /启动分析失败/)
  assert.match(source, /重新启动/)
  assert.match(source, /:loading="startingAnalysis && failedStartMode !== 'directory'"/)
  assert.match(source, /:loading="startingAnalysis && failedStartMode === 'directory'"/)
  assert.match(source, /analysisStartError\.value = ''/)
  assert.match(source, /analysisStartError\.value = e\.response\?\.data\?\.message \|\| '启动分析失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '启动分析失败'\)/)
  assert.match(source, /finally\s*{\s*startingAnalysis\.value = false\s*}/s)
})
