import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const knowledgeBaseView = new URL('../src/views/KnowledgeBaseView.vue', import.meta.url)

test('knowledge-base page exposes readiness before management details', async () => {
  const source = await readFile(knowledgeBaseView, 'utf8')

  assert.match(source, /class="[^"]*\bkb-command-center\b[^"]*"/)
  assert.match(source, /selectedKbReadiness/)
  assert.match(source, /readinessChecklist/)
  assert.match(source, /证据阈值/)
  assert.match(source, /切片策略/)
  assert.match(source, /to="\/documents"/)
  assert.match(source, /to="\/chat"/)
})

test('knowledge-base page guides empty setup and summarizes retrieval results', async () => {
  const source = await readFile(knowledgeBaseView, 'utf8')

  assert.match(source, /class="kb-empty-guide"/)
  assert.match(source, /创建第一个知识库/)
  assert.match(source, /class="search-result-summary"/)
  assert.match(source, /searchSourceCount/)
  assert.match(source, /topSearchScore/)
  assert.match(source, /命中文档/)
})
