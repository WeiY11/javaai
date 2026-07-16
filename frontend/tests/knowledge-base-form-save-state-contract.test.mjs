import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const knowledgeBaseView = new URL('../src/views/KnowledgeBaseView.vue', import.meta.url)

test('knowledge-base form save shows progress and a recoverable failure state', async () => {
  const source = await readFile(knowledgeBaseView, 'utf8')

  assert.match(source, /formSaving/)
  assert.match(source, /formActionError/)
  assert.match(source, /class="[^"]*\bform-action-status\b[^"]*"/)
  assert.match(source, /保存知识库失败/)
  assert.match(source, /重新保存/)
  assert.match(source, /:loading="formSaving"/)
  assert.match(source, /:disabled="formSaving"/)
  assert.match(source, /formActionError\.value = ''/)
  assert.match(source, /formActionError\.value = e\.response\?\.data\?\.message \|\| '保存知识库失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '保存知识库失败'\)/)
  assert.match(source, /finally\s*{\s*formSaving\.value = false\s*}/s)
})
