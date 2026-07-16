import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const knowledgeBaseView = new URL('../src/views/KnowledgeBaseView.vue', import.meta.url)

test('knowledge-base member mutations show progress and a recoverable state when they fail', async () => {
  const source = await readFile(knowledgeBaseView, 'utf8')

  assert.match(source, /memberMutationLoading/)
  assert.match(source, /memberMutationError/)
  assert.match(source, /removingMemberId/)
  assert.match(source, /class="[^"]*\bmember-mutation-status\b[^"]*"/)
  assert.match(source, /成员操作失败/)
  assert.match(source, /重新加载成员/)
  assert.match(source, /:loading="memberMutationLoading"/)
  assert.match(source, /:loading="removingMemberId === row\.userId"/)
  assert.match(source, /memberMutationError\.value = ''/)
  assert.match(source, /memberMutationError\.value = e\.response\?\.data\?\.message \|\| '成员操作失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '成员操作失败'\)/)
})
