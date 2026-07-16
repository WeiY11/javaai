import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('chat model panel reports loading, failure, and retry states instead of silent console errors', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /modelsLoading/)
  assert.match(source, /modelLoadError/)
  assert.match(source, /loadModels/)
  assert.match(source, /class="model-status-card"/)
  assert.match(source, /模型接口加载失败/)
  assert.match(source, /重新加载/)
  assert.doesNotMatch(source, /console\.error\(['"]Failed to load models/)
})
