import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

test('knowledge graph page explains graph readiness before rendering the canvas', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/KnowledgeGraphView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bkg-workbench\b[^"]*"/)
  assert.match(source, /图谱工作台/)
  assert.match(source, /graphReadiness/)
  assert.match(source, /graphScopeSummary/)
  assert.match(source, /graphDensityLabel/)
  assert.match(source, /选择知识库/)
  assert.match(source, /抽取实体/)
  assert.match(source, /探索关系/)
})

test('knowledge graph page summarizes exploration state and guides empty graph recovery', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/KnowledgeGraphView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bkg-summary-strip\b[^"]*"/)
  assert.match(source, /topHubSummary/)
  assert.match(source, /pathSearchSummary/)
  assert.match(source, /class="[^"]*\bkg-empty-guide\b[^"]*"/)
  assert.match(source, /emptyGraphGuide/)
  assert.match(source, /pathResultLabel/)
  assert.match(source, /关系探索/)
})
