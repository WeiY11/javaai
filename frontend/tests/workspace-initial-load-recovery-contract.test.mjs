import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

async function readView(name) {
  return readFile(path.join(sourceRoot, 'views', name), 'utf8')
}

test('document workspace can recover when initial knowledge-base loading fails', async () => {
  const source = await readView('DocumentView.vue')

  assert.match(source, /workspaceLoading/)
  assert.match(source, /workspaceLoadError/)
  assert.match(source, /class="[^"]*\bdocument-workspace-load-status\b[^"]*"/)
  assert.match(source, /@click="loadWorkspaceData"/)
  assert.match(source, /async function loadWorkspaceData\(\)/)
  assert.match(source, /await kbStore\.loadKnowledgeBases\(\)[\s\S]*if \(selectedKbId\.value\) await loadDocuments\(\)/)
  assert.match(source, /workspaceLoadError\.value = e\.response\?\.data\?\.message \|\| '加载文档工作台失败'/)
  assert.match(source, /onMounted\(\(\) => \{[\s\S]*loadWorkspaceData\(\)/)
  assert.doesNotMatch(source, /onMounted\(async \(\) => \{[\s\S]*await kbStore\.loadKnowledgeBases\(\)/)
})

test('citation workspace can recover when initial knowledge-base loading fails', async () => {
  const source = await readView('CitationView.vue')

  assert.match(source, /workspaceLoading/)
  assert.match(source, /workspaceLoadError/)
  assert.match(source, /class="[^"]*\bcitation-workspace-load-status\b[^"]*"/)
  assert.match(source, /@click="loadWorkspaceData"/)
  assert.match(source, /async function loadWorkspaceData\(\)/)
  assert.match(source, /await kbStore\.loadKnowledgeBases\(\)[\s\S]*if \(selectedKbId\.value\) await loadDocuments\(\)/)
  assert.match(source, /workspaceLoadError\.value = e\.response\?\.data\?\.message \|\| '加载引用工作台失败'/)
  assert.match(source, /onMounted\(\(\) => \{[\s\S]*loadWorkspaceData\(\)/)
  assert.doesNotMatch(source, /onMounted\(async \(\) => \{[\s\S]*await kbStore\.loadKnowledgeBases\(\)/)
})

test('notes workspace can recover when initial knowledge-base or document loading fails', async () => {
  const source = await readView('NotesView.vue')

  assert.match(source, /workspaceLoading/)
  assert.match(source, /workspaceLoadError/)
  assert.match(source, /class="[^"]*\bnote-workspace-load-status\b[^"]*"/)
  assert.match(source, /@click="loadWorkspaceData"/)
  assert.match(source, /async function loadWorkspaceData\(\)/)
  assert.match(source, /await kbStore\.loadKnowledgeBases\(\)[\s\S]*if \(selectedKbId\.value\) await loadDocuments\(\)/)
  assert.match(source, /workspaceLoadError\.value = e\.response\?\.data\?\.message \|\| '加载笔记工作台失败'/)
  assert.match(source, /async function loadDocuments\(\)[\s\S]*try \{[\s\S]*await docApi\.listDocuments/)
  assert.match(source, /notesLoadError\.value = e\.response\?\.data\?\.message \|\| '加载笔记文档失败'/)
  assert.match(source, /onMounted\(\(\) => \{[\s\S]*loadWorkspaceData\(\)/)
  assert.doesNotMatch(source, /onMounted\(async \(\) => \{[\s\S]*await kbStore\.loadKnowledgeBases\(\)/)
})
