import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat conversation deletion is confirmed and recoverable when it fails', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /ElMessageBox\.confirm/)
  assert.match(source, /deletingConversationId/)
  assert.match(source, /conversationActionError/)
  assert.match(source, /failedDeleteConversation/)
  assert.match(source, /class="[^"]*\bconversation-action-status\b[^"]*"/)
  assert.match(source, /删除对话失败/)
  assert.match(source, /重新删除/)
  assert.match(source, /@click\.stop="handleDeleteConversation\(conv\)"/)
  assert.doesNotMatch(source, /@click\.stop="chatStore\.deleteConversation\(conv\.id\)"/)
  assert.match(source, /async function handleDeleteConversation\(conv: Conversation/)
  assert.match(source, /await chatStore\.deleteConversation\(conv\.id\)/)
  assert.match(source, /conversationActionError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '删除对话失败'/)
})
