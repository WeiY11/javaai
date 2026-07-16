import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

test('academic graph page explains citation readiness before rendering the network', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/AcademicGraphView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bacademic-workbench\b[^"]*"/)
  assert.match(source, /学术图谱工作台/)
  assert.match(source, /academicReadiness/)
  assert.match(source, /academicScopeSummary/)
  assert.match(source, /citationCoverageSummary/)
  assert.match(source, /选择知识库/)
  assert.match(source, /提取引用/)
  assert.match(source, /生成综述/)
})

test('academic graph page summarizes citation coverage and review generation state', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/AcademicGraphView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bacademic-summary-strip\b[^"]*"/)
  assert.match(source, /mostCitedSummary/)
  assert.match(source, /reviewStatusSummary/)
  assert.match(source, /reviewEvidenceSummary/)
  assert.match(source, /class="[^"]*\bcitation-empty-guide\b[^"]*"/)
  assert.match(source, /emptyCitationGuide/)
  assert.match(source, /class="[^"]*\breview-readiness-panel\b[^"]*"/)
  assert.match(source, /reviewInputGuide/)
})
