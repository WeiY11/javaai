import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const knowledgeBaseView = new URL('../src/views/KnowledgeBaseView.vue', import.meta.url)

test('knowledge-base member management shows loading and retry state when members fail to load', async () => {
  const source = await readFile(knowledgeBaseView, 'utf8')

  assert.match(source, /membersLoading/)
  assert.match(source, /memberActionError/)
  assert.match(source, /currentManageKb/)
  assert.match(source, /class="[^"]*\bmember-action-status\b[^"]*"/)
  assert.match(source, /成员加载失败/)
  assert.match(source, /重新加载成员/)
  assert.match(source, /v-loading="membersLoading"/)
  assert.match(source, /async function loadMembers/)
  assert.match(source, /showMembersDialog\.value = true/)
  assert.match(source, /await loadMembers\(kb\.id\)/)
  assert.match(source, /memberActionError\.value = e\.response\?\.data\?\.message \|\| '成员加载失败'/)
  assert.match(source, /ElMessage\.error\(e\.response\?\.data\?\.message \|\| '成员加载失败'\)/)
})
