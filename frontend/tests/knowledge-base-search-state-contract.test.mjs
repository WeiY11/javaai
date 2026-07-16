import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const knowledgeBaseView = new URL('../src/views/KnowledgeBaseView.vue', import.meta.url)

test('knowledge-base search shows a recoverable state when retrieval fails', async () => {
  const source = await readFile(knowledgeBaseView, 'utf8')

  assert.match(source, /searchError/)
  assert.match(source, /class="[^"]*\bsearch-error-status\b[^"]*"/)
  assert.match(source, /检索失败/)
  assert.match(source, /重新检索/)
  assert.match(source, /v-else-if="hasSearched && !searching && !searchError"/)
  assert.match(source, /searchError\.value = ''/)
  assert.match(source, /searchError\.value = e\.response\?\.data\?\.message \|\| '检索失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '检索失败'\)/)
})
