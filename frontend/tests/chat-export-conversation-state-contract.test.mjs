import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat conversation export shows progress and a recoverable failure state', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /exportingConversation/)
  assert.match(source, /exportingFormat/)
  assert.match(source, /conversationExportError/)
  assert.match(source, /failedExportConversation/)
  assert.match(source, /class="[^"]*\bconversation-export-status\b[^"]*"/)
  assert.match(source, /\u5bfc\u51fa\u5931\u8d25/)
  assert.match(source, /\u91cd\u65b0\u5bfc\u51fa/)
  assert.match(source, /:loading="exportingConversation && exportingFormat === 'markdown'"/)
  assert.match(source, /:loading="exportingConversation && exportingFormat === 'json'"/)
  assert.match(source, /:disabled="!chatStore\.currentConversation \|\| chatStore\.messages\.length === 0 \|\| exportingConversation"/)
  assert.match(source, /if \(exportingConversation\.value\) return/)
  assert.match(source, /conversationExportError\.value = ''/)
  assert.match(source, /failedExportConversation\.value = { id: conversation\.id, format }/)
  assert.match(source, /conversationExportError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '\u5bfc\u51fa\u5931\u8d25'/)
  assert.match(source, /finally\s*{\s*exportingConversation\.value = false\s*exportingFormat\.value = null\s*}/s)
  assert.match(source, /async function retryExportConversation\(\)/)
})
