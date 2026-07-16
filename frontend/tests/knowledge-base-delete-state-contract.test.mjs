import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const knowledgeBaseView = new URL('../src/views/KnowledgeBaseView.vue', import.meta.url)

test('knowledge-base deletion shows progress and a recoverable state when it fails', async () => {
  const source = await readFile(knowledgeBaseView, 'utf8')

  assert.match(source, /deletingKnowledgeBaseId/)
  assert.match(source, /knowledgeBaseActionError/)
  assert.match(source, /failedDeleteKnowledgeBase/)
  assert.match(source, /class="[^"]*\bkb-action-status\b[^"]*"/)
  assert.match(source, /删除知识库失败/)
  assert.match(source, /重新删除/)
  assert.match(source, /:loading="deletingKnowledgeBaseId === row\.id"/)
  assert.match(source, /@click="handleDelete\(row\)"/)
  assert.doesNotMatch(source, /@click="handleDelete\(row\.id\)"/)
  assert.match(source, /async function handleDelete\(kb: KnowledgeBase/)
  assert.match(source, /await kbStore\.deleteKb\(kb\.id\)/)
  assert.match(source, /knowledgeBaseActionError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '删除知识库失败'/)
})
