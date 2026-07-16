import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const chatView = new URL('../src/views/ChatView.vue', import.meta.url)

test('model selector only offers configured providers', async () => {
  const source = await readFile(chatView, 'utf8')

  assert.match(source, /const configuredModels = computed\(\(\) => availableModels\.value\.filter\(model => model\.configured\)\)/)
  assert.match(source, /v-for="model in configuredModels"/)
  assert.match(source, /:disabled="modelsLoading \|\| !!modelLoadError \|\| configuredModels\.length === 0"/)
  assert.match(source, /configuredModels\.value\.find\(m => m\.provider === modelProvider\.value\)/)
})
