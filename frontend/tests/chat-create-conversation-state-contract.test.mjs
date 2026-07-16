import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat conversation creation shows progress and a recoverable failure state', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /creatingConversation/)
  assert.match(source, /conversationCreateError/)
  assert.match(source, /failedCreateConversation/)
  assert.match(source, /class="[^"]*\bconversation-create-status\b[^"]*"/)
  assert.match(source, /\u521b\u5efa\u5bf9\u8bdd\u5931\u8d25/)
  assert.match(source, /\u91cd\u65b0\u521b\u5efa/)
  assert.match(source, /:loading="creatingConversation"/)
  assert.match(source, /:disabled="!chatStore\.selectedKbId \|\| creatingConversation \|\| configuredModels\.length === 0"/)
  assert.match(source, /if \(creatingConversation\.value\) return/)
  assert.match(source, /conversationCreateError\.value = ''/)
  assert.match(source, /failedCreateConversation\.value = { knowledgeBaseId, modelProvider: modelProvider\.value }/)
  assert.match(source, /await chatStore\.createConversation\(knowledgeBaseId, modelProvider\.value\)/)
  assert.match(source, /conversationCreateError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '\u521b\u5efa\u5bf9\u8bdd\u5931\u8d25'/)
  assert.match(source, /finally\s*{\s*creatingConversation\.value = false\s*}/s)
  assert.match(source, /async function retryCreateConversation\(\)/)
})
