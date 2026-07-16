import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const notesView = new URL('../src/views/NotesView.vue', import.meta.url)

test('notes page shows a recoverable state when notes cannot be loaded', async () => {
  const source = await readFile(notesView, 'utf8')

  assert.match(source, /notesLoadError/)
  assert.match(source, /class="[^"]*\bnote-load-status\b[^"]*"/)
  assert.match(source, /笔记加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /@click="loadNotes"/)
  assert.match(source, /catch \(e: any\)/)
  assert.match(source, /notesLoadError\.value/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '加载笔记失败'\)/)
  assert.match(source, /!notesLoadError/)
})
