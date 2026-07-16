import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat conversation rename shows progress and stays recoverable when it fails', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /renamingConversation/)
  assert.match(source, /conversationRenameError/)
  assert.match(source, /failedRenameConversation/)
  assert.match(source, /class="[^"]*\bconversation-rename-status\b[^"]*"/)
  assert.match(source, /\u91cd\u547d\u540d\u5931\u8d25/)
  assert.match(source, /\u91cd\u65b0\u4fdd\u5b58/)
  assert.match(source, /:disabled="renamingConversation"/)
  assert.match(source, /:loading="renamingConversation"/)
  assert.match(source, /if \(renamingConversation\.value\) return/)
  assert.match(source, /conversationRenameError\.value = ''/)
  assert.match(source, /failedRenameConversation\.value = { id: conversation\.id, title }/)
  assert.match(source, /await renameConversation\(conversation\.id, title\)/)
  assert.match(source, /conversationRenameError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '\u91cd\u547d\u540d\u5931\u8d25'/)
  assert.match(source, /renaming\.value = true/s)
  assert.match(source, /finally\s*{\s*renamingConversation\.value = false\s*}/s)
  assert.match(source, /async function retryRenameConversation\(\)/)
})
