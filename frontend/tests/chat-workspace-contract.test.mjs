import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat page gives first-time users a guided workflow before a conversation exists', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /class="chat-empty-guide"/)
  assert.match(source, /选择知识库/)
  assert.match(source, /上传文档/)
  assert.match(source, /创建对话/)
  assert.match(source, /to="\/knowledge-bases"/)
  assert.match(source, /to="\/documents"/)
})

test('chat page exposes stream progress and citation context without waiting for raw messages', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /streamStatusMeta/)
  assert.match(source, /class="stream-status"/)
  assert.match(source, /检索证据/)
  assert.match(source, /生成回答/)
  assert.match(source, /回答完成/)
  assert.match(source, /class="citation-summary"/)
})
