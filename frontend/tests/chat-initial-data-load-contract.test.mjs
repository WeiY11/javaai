import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat workspace initial data load is visible and recoverable when it fails', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /chatDataLoading/)
  assert.match(source, /chatDataLoadError/)
  assert.match(source, /class="[^"]*\bchat-data-load-status\b[^"]*"/)
  assert.match(source, /\u52a0\u8f7d\u804a\u5929\u5de5\u4f5c\u533a\u5931\u8d25/)
  assert.match(source, /\u91cd\u65b0\u52a0\u8f7d/)
  assert.match(source, /:loading="chatDataLoading"/)
  assert.match(source, /async function loadWorkspaceData\(\)/)
  assert.match(source, /if \(chatDataLoading\.value\) return/)
  assert.match(source, /chatDataLoadError\.value = ''/)
  assert.match(source, /await Promise\.all\(\[\s*chatStore\.loadConversations\(\),\s*kbStore\.loadKnowledgeBases\(\)\s*\]\)/s)
  assert.match(source, /chatDataLoadError\.value = e\.response\?\.data\?\.message \|\| e\.message \|\| '\u52a0\u8f7d\u804a\u5929\u5de5\u4f5c\u533a\u5931\u8d25'/)
  assert.match(source, /finally\s*{\s*chatDataLoading\.value = false\s*}/s)
  assert.match(source, /async function retryLoadWorkspaceData\(\)/)
  assert.match(source, /await loadWorkspaceData\(\)/)
  assert.doesNotMatch(source, /onMounted\(async \(\) =>[\s\S]*?chatStore\.loadConversations\(\)\s*[\r\n]+\s*kbStore\.loadKnowledgeBases\(\)/)
})
