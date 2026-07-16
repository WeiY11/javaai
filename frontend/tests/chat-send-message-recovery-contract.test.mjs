import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat send failure preserves the prompt and offers a retry path', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /messageSendError/)
  assert.match(source, /failedSendPrompt/)
  assert.match(source, /class="[^"]*\bmessage-send-status\b[^"]*"/)
  assert.match(source, /\u53d1\u9001\u5931\u8d25/)
  assert.match(source, /\u91cd\u65b0\u53d1\u9001/)
  assert.match(source, /:disabled="chatStore\.isStreaming \|\| chatStore\.isLoading"/)
  assert.match(source, /if \(chatStore\.isLoading \|\| chatStore\.isStreaming\) return/)
  assert.match(source, /messageSendError\.value = ''/)
  assert.match(source, /failedSendPrompt\.value = ''/)
  assert.match(source, /failedSendPrompt\.value = text/)
  assert.match(source, /chatStore\.streamStatus === 'failed'/)
  assert.match(source, /messageSendError\.value = '\u53d1\u9001\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5'/)
  assert.match(source, /inputText\.value = text/)
  assert.match(source, /async function retrySendMessage\(\)/)
  assert.match(source, /const prompt = failedSendPrompt\.value/)
  assert.match(source, /await handleSend\(\)/)
})
