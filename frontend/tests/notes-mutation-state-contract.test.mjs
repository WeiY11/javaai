import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const notesView = new URL('../src/views/NotesView.vue', import.meta.url)

test('notes mutations show progress and recover when save or delete fails', async () => {
  const source = await readFile(notesView, 'utf8')

  assert.match(source, /noteSaving/)
  assert.match(source, /noteActionError/)
  assert.match(source, /deletingNoteId/)
  assert.match(source, /class="[^"]*\bnote-action-status\b[^"]*"/)
  assert.match(source, /笔记操作失败/)
  assert.match(source, /重新加载笔记/)
  assert.match(source, /:loading="noteSaving"/)
  assert.match(source, /:disabled="noteSaving"/)
  assert.match(source, /:loading="deletingNoteId === note\.id"/)
  assert.match(source, /noteActionError\.value = ''/)
  assert.match(source, /noteActionError\.value = e\.response\?\.data\?\.message \|\| '笔记操作失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '笔记操作失败'\)/)
})
