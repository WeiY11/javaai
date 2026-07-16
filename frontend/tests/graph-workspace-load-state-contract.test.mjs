import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

test('knowledge graph workspace can recover when initial data loading fails', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/KnowledgeGraphView.vue'), 'utf8')

  assert.match(source, /workspaceLoading/)
  assert.match(source, /workspaceLoadError/)
  assert.match(source, /class="[^"]*\bgraph-load-status\b[^"]*"/)
  assert.match(source, /@click="loadWorkspaceData"/)
  assert.match(source, /async function loadWorkspaceData\(\)/)
  assert.match(source, /await kbStore\.loadKnowledgeBases\(\)[\s\S]*if \(selectedKbId\.value\) await loadGraph\(\)/)
  assert.match(source, /workspaceLoadError\.value = e\.response\?\.data\?\.message \|\| '加载图谱工作台失败'/)
  assert.match(source, /onMounted\(\(\) => \{[\s\S]*loadWorkspaceData\(\)[\s\S]*window\.addEventListener\('resize', handleResize\)/)
  assert.doesNotMatch(source, /onMounted\(async \(\) => \{[\s\S]*await kbStore\.loadKnowledgeBases\(\)/)
})

test('academic graph workspace can recover when initial data loading fails', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/AcademicGraphView.vue'), 'utf8')

  assert.match(source, /workspaceLoading/)
  assert.match(source, /workspaceLoadError/)
  assert.match(source, /class="[^"]*\bacademic-load-status\b[^"]*"/)
  assert.match(source, /@click="loadWorkspaceData"/)
  assert.match(source, /async function loadWorkspaceData\(\)/)
  assert.match(source, /await kbStore\.loadKnowledgeBases\(\)[\s\S]*if \(selectedKbId\.value\) await loadGraph\(\)/)
  assert.match(source, /workspaceLoadError\.value = e\.response\?\.data\?\.message \|\| '加载学术图谱工作台失败'/)
  assert.match(source, /onMounted\(\(\) => \{[\s\S]*loadWorkspaceData\(\)[\s\S]*window\.addEventListener\('resize', handleResize\)/)
  assert.doesNotMatch(source, /onMounted\(async \(\) => \{[\s\S]*await kbStore\.loadKnowledgeBases\(\)/)
})
