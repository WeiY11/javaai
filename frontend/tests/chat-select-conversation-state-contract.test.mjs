import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)
const chatStore = new URL('../src/stores/chat.store.ts', import.meta.url)

test('chat conversation selection shows loading and recovers when message loading fails', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /loadingConversationId/)
  assert.match(source, /messageLoadError/)
  assert.match(source, /failedMessageConversation/)
  assert.match(source, /class="[^"]*\bmessage-load-status\b[^"]*"/)
  assert.match(source, /\u52a0\u8f7d\u6d88\u606f\u5931\u8d25/)
  assert.match(source, /\u91cd\u65b0\u52a0\u8f7d/)
  assert.match(source, /@click="handleSelectConversation\(conv\)"/)
  assert.doesNotMatch(source, /@click="chatStore\.selectConversation\(conv\)"/)
  assert.match(source, /:aria-busy="loadingConversationId === conv\.id"/)
  assert.match(source, /if \(loadingConversationId\.value\) return/)
  assert.match(source, /messageLoadError\.value = ''/)
  assert.match(source, /failedMessageConversation\.value = conv/)
  assert.match(source, /await chatStore\.selectConversation\(conv\)/)
  assert.match(source, /messageLoadError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '\u52a0\u8f7d\u6d88\u606f\u5931\u8d25'/)
  assert.match(source, /finally\s*{\s*loadingConversationId\.value = null\s*}/s)
  assert.match(source, /async function retrySelectConversation\(\)/)
})

test('chat store only switches current conversation after messages load', async () => {
  const source = await readFile(chatStore, 'utf8')

  assert.match(
    source,
    /async function selectConversation\(conv: Conversation\) {\s*const loadedMessages = await chatApi\.getMessages\(conv\.id\)\s*currentConversation\.value = conv\s*selectedKbId\.value = conv\.knowledgeBaseId\s*messages\.value = loadedMessages\s*}/s
  )
})
