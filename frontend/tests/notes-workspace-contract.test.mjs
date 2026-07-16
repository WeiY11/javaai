import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

test('notes page exposes a research-note workspace before the note cards', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/NotesView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bnote-workbench\b[^"]*"/)
  assert.match(source, /笔记工作台/)
  assert.match(source, /noteScopeLabel/)
  assert.match(source, /noteReadiness/)
  assert.match(source, /定位资料/)
  assert.match(source, /沉淀判断/)
  assert.match(source, /回到证据/)
})

test('notes page summarizes note coverage and gives a guided empty state', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/NotesView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bnote-summary-strip\b[^"]*"/)
  assert.match(source, /noteTagCount/)
  assert.match(source, /quotedNoteCount/)
  assert.match(source, /class="[^"]*\bnote-empty-guide\b[^"]*"/)
  assert.match(source, /emptyGuideTitle/)
  assert.match(source, /emptyGuideDetail/)
  assert.match(source, /先选择知识库和文档/)
})
