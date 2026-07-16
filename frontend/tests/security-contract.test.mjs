import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)
const chatApi = new URL('../src/api/chat.ts', import.meta.url)

test('model configuration UI never renders sensitive provider settings', async () => {
  const [viewSource, apiSource] = await Promise.all([
    readFile(chatView, 'utf8'),
    readFile(chatApi, 'utf8')
  ])

  assert.doesNotMatch(viewSource, /\b(?:apiKey|baseUrl)\b/)
  assert.match(apiSource, /Promise<AvailableModel\[\]>/)
  assert.doesNotMatch(apiSource, /\b(?:apiKey|baseUrl)\??\s*:/)
})
