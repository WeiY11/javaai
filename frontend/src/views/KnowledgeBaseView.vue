<template>
  <div class="kb-page">
    <div class="metric-grid">
      <div class="metric-card"><span>知识库</span><strong>{{ kbStore.knowledgeBases.length }}</strong></div>
      <div class="metric-card"><span>已启用</span><strong>{{ activeCount }}</strong></div>
      <div class="metric-card"><span>平均阈值</span><strong>{{ averageThreshold }}</strong></div>
      <div class="metric-card"><span>当前选择</span><strong>{{ kbStore.currentKb?.name || '无' }}</strong></div>
    </div>

    <section class="workspace-card kb-command-center">
      <div class="toolbar">
        <div>
          <h2 class="section-title">知识库就绪度</h2>
          <p class="section-subtitle">先确认策略和阈值，再上传资料、检索证据或进入问答。</p>
        </div>
        <el-tag :type="selectedKbReadiness.type" effect="plain">{{ selectedKbReadiness.label }}</el-tag>
      </div>

      <div v-if="selectedKb" class="readiness-layout">
        <div class="selected-kb-panel">
          <p class="eyebrow">当前知识库</p>
          <h3>{{ selectedKb.name }}</h3>
          <p>{{ selectedKb.description || '暂无描述。建议补充资料范围、适用场景和排除范围。' }}</p>
          <div class="policy-grid">
            <div>
              <span>切片策略</span>
              <strong>{{ chunkStrategyLabel(selectedKb.chunkStrategy) }}</strong>
            </div>
            <div>
              <span>证据阈值</span>
              <strong>{{ Math.round(selectedKb.evidenceThreshold * 100) }}%</strong>
            </div>
            <div>
              <span>切片大小</span>
              <strong>{{ selectedKb.chunkSize }}</strong>
            </div>
          </div>
        </div>

        <div class="readiness-checklist">
          <div v-for="item in readinessChecklist" :key="item.label" class="readiness-item" :class="{ ready: item.ready }">
            <span class="status-dot" :class="{ muted: !item.ready }"></span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.detail }}</small>
          </div>
        </div>

        <div class="kb-next-actions">
          <RouterLink class="kb-action primary-action" to="/documents">上传文档</RouterLink>
          <RouterLink class="kb-action" to="/chat">开始问答</RouterLink>
          <button class="kb-action" type="button" @click="focusSearch">检索验证</button>
        </div>
      </div>

      <div v-else class="kb-empty-guide">
        <div>
          <strong>创建第一个知识库</strong>
          <span>知识库是文档、检索和问答的边界。先建库，再上传资料。</span>
        </div>
        <el-button type="primary" @click="openCreateDialog">创建知识库</el-button>
      </div>
    </section>

    <section class="workspace-card search-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">知识库检索</h2>
          <p class="section-subtitle">直接召回知识片段，查看分数、来源和切片位置。</p>
        </div>
        <el-tag v-if="kbStore.currentKb" type="success">{{ kbStore.currentKb.name }}</el-tag>
      </div>

      <div class="search-form">
        <el-select v-model="searchForm.knowledgeBaseId" filterable placeholder="选择知识库" @change="handleSearchKbChange">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-input
          v-model="searchForm.query"
          clearable
          placeholder="输入关键词或问题"
          @keyup.enter="handleSearch"
        />
        <el-input-number v-model="searchForm.topK" :min="1" :max="20" controls-position="right" />
        <el-switch v-model="searchForm.rerank" active-text="精排" />
        <el-button type="primary" :disabled="!canSearch" :loading="searching" @click="handleSearch">检索</el-button>
      </div>

      <div v-if="searchError" class="search-error-status">
        <div>
          <strong>检索失败</strong>
          <span>{{ searchError }}</span>
        </div>
        <el-button type="primary" plain :disabled="!canSearch" :loading="searching" @click="handleSearch">重新检索</el-button>
      </div>

      <div v-if="searchResults.length" class="search-result-summary">
        <div><span>命中片段</span><strong>{{ searchResults.length }}</strong></div>
        <div><span>命中文档</span><strong>{{ searchSourceCount }}</strong></div>
        <div><span>最高分</span><strong>{{ topSearchScore }}</strong></div>
      </div>

      <div v-if="searchResults.length" class="search-results">
        <article v-for="result in searchResults" :key="result.chunkId" class="search-result">
          <div class="result-head">
            <strong>文档 {{ result.documentId }} · 切片 #{{ result.chunkIndex }}</strong>
            <span>{{ scoreLabel(result.score) }} · {{ result.source }}</span>
          </div>
          <p>{{ result.content }}</p>
        </article>
      </div>
      <el-empty v-else-if="hasSearched && !searching && !searchError" description="暂无检索结果" />
    </section>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">知识库管理</h2>
          <p class="section-subtitle">配置切片策略、证据阈值和团队成员权限。</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">创建知识库</el-button>
      </div>

      <div v-if="knowledgeBaseActionError" class="kb-action-status">
        <div>
          <strong>删除知识库失败</strong>
          <span>{{ knowledgeBaseActionError }}</span>
        </div>
        <el-button
          type="danger"
          plain
          :disabled="!failedDeleteKnowledgeBase || deletingKnowledgeBaseId === failedDeleteKnowledgeBase?.id"
          :loading="deletingKnowledgeBaseId === failedDeleteKnowledgeBase?.id"
          @click="retryDeleteKnowledgeBase"
        >
          重新删除
        </el-button>
      </div>

      <el-table :data="kbStore.knowledgeBases" v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="chunkStrategy" label="切片策略" width="130" />
        <el-table-column prop="chunkSize" label="切片大小" width="110" />
        <el-table-column label="证据阈值" width="130">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.evidenceThreshold * 100)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status || 'ACTIVE' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="selectKb(row)">选择</el-button>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" @click="openMembersDialog(row)">成员</el-button>
            <el-button
              size="small"
              type="danger"
              :loading="deletingKnowledgeBaseId === row.id"
              :disabled="deletingKnowledgeBaseId === row.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="kb-empty-guide">
            <div>
              <strong>创建第一个知识库</strong>
              <span>建库后才能配置切片策略、证据阈值和成员权限。</span>
            </div>
            <el-button type="primary" @click="openCreateDialog">创建知识库</el-button>
          </div>
        </template>
      </el-table>
    </section>

    <el-dialog v-model="showFormDialog" :title="formMode === 'create' ? '创建知识库' : '编辑知识库'" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item v-if="formMode === 'create'" label="切片策略">
          <el-select v-model="form.chunkStrategy">
            <el-option label="段落切片" value="PARAGRAPH" />
            <el-option label="固定长度" value="FIXED_LENGTH" />
            <el-option label="语义切片" value="SEMANTIC" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formMode === 'create'" label="切片大小">
          <el-input-number v-model="form.chunkSize" :min="100" :max="2000" />
        </el-form-item>
        <el-form-item v-if="formMode === 'create'" label="重叠大小">
          <el-input-number v-model="form.chunkOverlap" :min="0" :max="500" />
        </el-form-item>
        <el-form-item label="证据阈值">
          <el-slider v-model="form.evidenceThreshold" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
      </el-form>

      <div v-if="formActionError" class="form-action-status">
        <div>
          <strong>保存知识库失败</strong>
          <span>{{ formActionError }}</span>
        </div>
        <el-button type="primary" plain :loading="formSaving" @click="submitForm">重新保存</el-button>
      </div>

      <template #footer>
        <el-button :disabled="formSaving" @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" :loading="formSaving" :disabled="formSaving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showMembersDialog" :title="currentManageKb ? currentManageKb.name + ' 成员管理' : '成员管理'" width="620px">
      <div class="member-toolbar">
        <el-input-number v-model="newMemberId" placeholder="用户 ID" :min="1" />
        <el-select v-model="newMemberRole">
          <el-option label="成员" value="MEMBER" />
          <el-option label="所有者" value="OWNER" />
        </el-select>
        <el-button
          type="primary"
          :disabled="!newMemberId || membersLoading || memberMutationLoading || !!memberActionError"
          :loading="memberMutationLoading"
          @click="handleAddMember"
        >
          添加
        </el-button>
      </div>

      <div v-if="memberActionError" class="member-action-status">
        <div>
          <strong>成员加载失败</strong>
          <span>{{ memberActionError }}</span>
        </div>
        <el-button type="primary" plain :loading="membersLoading" @click="loadMembers()">重新加载成员</el-button>
      </div>

      <div v-if="memberMutationError" class="member-mutation-status">
        <div>
          <strong>成员操作失败</strong>
          <span>{{ memberMutationError }}</span>
        </div>
        <el-button type="primary" plain :loading="membersLoading" @click="loadMembers()">重新加载成员</el-button>
      </div>

      <el-table :data="members" size="small" v-loading="membersLoading">
        <el-table-column prop="userId" label="用户 ID" />
        <el-table-column prop="role" label="角色" />
        <el-table-column prop="joinedAt" label="加入时间" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              size="small"
              type="danger"
              :disabled="membersLoading || memberMutationLoading"
              :loading="removingMemberId === row.userId"
              @click="handleRemoveMember(row.userId)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import * as kbApi from '../api/knowledge-base'
import type { KbMember, KnowledgeBase, KnowledgeBaseSearchResult } from '../types/knowledge-base.types'

const kbStore = useKnowledgeBaseStore()
const loading = ref(false)
const searching = ref(false)
const hasSearched = ref(false)
const showFormDialog = ref(false)
const showMembersDialog = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSaving = ref(false)
const formActionError = ref('')
const members = ref<KbMember[]>([])
const membersLoading = ref(false)
const memberActionError = ref('')
const memberMutationLoading = ref(false)
const memberMutationError = ref('')
const removingMemberId = ref<number | null>(null)
const searchResults = ref<KnowledgeBaseSearchResult[]>([])
const searchError = ref('')
const deletingKnowledgeBaseId = ref<number | null>(null)
const knowledgeBaseActionError = ref('')
const failedDeleteKnowledgeBase = ref<KnowledgeBase | null>(null)
const currentManageKbId = ref<number | null>(null)
const currentManageKb = ref<KnowledgeBase | null>(null)
const newMemberId = ref<number | null>(null)
const newMemberRole = ref<'MEMBER' | 'OWNER'>('MEMBER')

const searchForm = reactive({
  knowledgeBaseId: null as number | null,
  query: '',
  topK: 8,
  rerank: true
})

const form = reactive({
  id: 0,
  name: '',
  description: '',
  chunkStrategy: 'PARAGRAPH' as KnowledgeBase['chunkStrategy'],
  chunkSize: 500,
  chunkOverlap: 100,
  evidenceThreshold: 0.5
})

const activeCount = computed(() => kbStore.knowledgeBases.filter(kb => (kb.status || 'ACTIVE') === 'ACTIVE').length)
const averageThreshold = computed(() => {
  if (!kbStore.knowledgeBases.length) return '0%'
  const total = kbStore.knowledgeBases.reduce((sum, kb) => sum + (kb.evidenceThreshold || 0), 0)
  return `${Math.round((total / kbStore.knowledgeBases.length) * 100)}%`
})
const selectedKb = computed(() =>
  kbStore.currentKb
  || kbStore.knowledgeBases.find(kb => kb.id === searchForm.knowledgeBaseId)
  || kbStore.knowledgeBases[0]
  || null
)
const canSearch = computed(() => Boolean(searchForm.knowledgeBaseId && searchForm.query.trim()))
const selectedKbReadiness = computed(() => {
  if (!selectedKb.value) return { label: '待创建', type: 'info' as const }
  const missing = readinessChecklist.value.filter(item => !item.ready).length
  if (missing > 0) return { label: `${missing} 项待完善`, type: 'warning' as const }
  return { label: '可用于问答', type: 'success' as const }
})
const readinessChecklist = computed(() => {
  const kb = selectedKb.value
  return [
    {
      label: '知识库已启用',
      ready: Boolean(kb && (kb.status || 'ACTIVE') === 'ACTIVE'),
      detail: kb ? '当前知识库可被检索和问答流程使用。' : '先创建知识库。'
    },
    {
      label: '切片策略',
      ready: Boolean(kb?.chunkStrategy && kb.chunkSize > 0),
      detail: kb ? `${chunkStrategyLabel(kb.chunkStrategy)}，大小 ${kb.chunkSize}，重叠 ${kb.chunkOverlap}` : '创建时选择切片策略。'
    },
    {
      label: '证据阈值',
      ready: Boolean(kb && kb.evidenceThreshold > 0),
      detail: kb ? `当前阈值 ${Math.round(kb.evidenceThreshold * 100)}%，会影响召回片段进入答案。` : '建议设置证据阈值。'
    }
  ]
})
const searchSourceCount = computed(() => new Set(searchResults.value.map(result => result.documentId)).size)
const topSearchScore = computed(() => {
  if (searchResults.value.length === 0) return '-'
  const topScore = Math.max(...searchResults.value.map(result => Number(result.score) || 0))
  return scoreLabel(topScore)
})

function resetForm() {
  Object.assign(form, {
    id: 0,
    name: '',
    description: '',
    chunkStrategy: 'PARAGRAPH',
    chunkSize: 500,
    chunkOverlap: 100,
    evidenceThreshold: 0.5
  })
}

function openCreateDialog() {
  resetForm()
  formMode.value = 'create'
  formActionError.value = ''
  showFormDialog.value = true
}

function openEditDialog(kb: KnowledgeBase) {
  Object.assign(form, {
    id: kb.id,
    name: kb.name,
    description: kb.description || '',
    chunkStrategy: kb.chunkStrategy,
    chunkSize: kb.chunkSize,
    chunkOverlap: kb.chunkOverlap,
    evidenceThreshold: kb.evidenceThreshold
  })
  formMode.value = 'edit'
  formActionError.value = ''
  showFormDialog.value = true
}

function selectKb(kb: KnowledgeBase) {
  kbStore.selectKb(kb)
  searchForm.knowledgeBaseId = kb.id
}

function focusSearch() {
  if (selectedKb.value) {
    searchForm.knowledgeBaseId = selectedKb.value.id
    kbStore.selectKb(selectedKb.value)
  }
}

function handleSearchKbChange(kbId: number) {
  const kb = kbStore.knowledgeBases.find(item => item.id === kbId)
  if (kb) kbStore.selectKb(kb)
  searchError.value = ''
}

async function handleSearch() {
  if (!canSearch.value || !searchForm.knowledgeBaseId) return
  searching.value = true
  hasSearched.value = true
  searchError.value = ''
  try {
    searchResults.value = await kbApi.searchKnowledgeBase(searchForm.knowledgeBaseId, {
      query: searchForm.query.trim(),
      topK: searchForm.topK,
      rerank: searchForm.rerank
    })
  } catch (e: any) {
    searchResults.value = []
    searchError.value = e.response?.data?.message || '检索失败'
    ElMessage.error(e.response?.data?.message || '检索失败')
  } finally {
    searching.value = false
  }
}

function scoreLabel(score: number) {
  const normalized = Math.max(0, Math.min(score, 1))
  return `${Math.round(normalized * 100)}%`
}

function chunkStrategyLabel(strategy: KnowledgeBase['chunkStrategy']): string {
  const labels: Record<KnowledgeBase['chunkStrategy'], string> = {
    FIXED_LENGTH: '固定长度',
    PARAGRAPH: '段落切片',
    SEMANTIC: '语义切片'
  }
  return labels[strategy] || strategy
}

async function submitForm() {
  if (formSaving.value) return
  formSaving.value = true
  formActionError.value = ''
  try {
    if (formMode.value === 'create') {
      await kbStore.createKb({ ...form })
    } else {
      await kbApi.updateKnowledgeBase(form.id, {
        name: form.name,
        description: form.description,
        evidenceThreshold: form.evidenceThreshold
      })
      await kbStore.loadKnowledgeBases()
    }
    showFormDialog.value = false
    ElMessage.success('知识库已保存')
  } catch (e: any) {
    formActionError.value = e.response?.data?.message || '保存知识库失败'
    ElMessage.error(e.response?.data?.message || '保存知识库失败')
  } finally {
    formSaving.value = false
  }
}

async function handleDelete(kb: KnowledgeBase, shouldConfirm = true) {
  if (deletingKnowledgeBaseId.value) return
  try {
    if (shouldConfirm) {
      await ElMessageBox.confirm(
        `删除「${kb.name}」后相关文档和对话可能不可用，确认删除？`,
        '删除知识库',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      )
    }
    knowledgeBaseActionError.value = ''
    deletingKnowledgeBaseId.value = kb.id
    await kbStore.deleteKb(kb.id)
    if (searchForm.knowledgeBaseId === kb.id) {
      searchForm.knowledgeBaseId = null
      searchResults.value = []
      hasSearched.value = false
    }
    if (failedDeleteKnowledgeBase.value?.id === kb.id) failedDeleteKnowledgeBase.value = null
    ElMessage.success('已删除知识库')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    failedDeleteKnowledgeBase.value = kb
    knowledgeBaseActionError.value = e.response?.data?.message || e.message || '删除知识库失败'
    ElMessage.error(e.response?.data?.message || e.message || '删除知识库失败')
  } finally {
    deletingKnowledgeBaseId.value = null
  }
}

async function retryDeleteKnowledgeBase() {
  if (!failedDeleteKnowledgeBase.value) return
  await handleDelete(failedDeleteKnowledgeBase.value, false)
}

async function openMembersDialog(kb: KnowledgeBase) {
  currentManageKbId.value = kb.id
  currentManageKb.value = kb
  members.value = []
  memberActionError.value = ''
  memberMutationError.value = ''
  showMembersDialog.value = true
  await loadMembers(kb.id)
}

async function loadMembers(kbId = currentManageKbId.value) {
  if (!kbId) return
  membersLoading.value = true
  memberActionError.value = ''
  try {
    members.value = await kbApi.listMembers(kbId)
  } catch (e: any) {
    members.value = []
    memberActionError.value = e.response?.data?.message || '成员加载失败'
    ElMessage.error(e.response?.data?.message || '成员加载失败')
  } finally {
    membersLoading.value = false
  }
}

async function handleAddMember() {
  if (!currentManageKbId.value || !newMemberId.value) return
  memberMutationLoading.value = true
  memberMutationError.value = ''
  try {
    await kbApi.addMember(currentManageKbId.value, newMemberId.value, newMemberRole.value)
    await loadMembers(currentManageKbId.value)
    newMemberId.value = null
    ElMessage.success('成员已添加')
  } catch (e: any) {
    memberMutationError.value = e.response?.data?.message || '成员操作失败'
    ElMessage.error(e.response?.data?.message || '成员操作失败')
  } finally {
    memberMutationLoading.value = false
  }
}

async function handleRemoveMember(userId: number) {
  if (!currentManageKbId.value) return
  removingMemberId.value = userId
  memberMutationError.value = ''
  try {
    await kbApi.removeMember(currentManageKbId.value, userId)
    await loadMembers(currentManageKbId.value)
    ElMessage.success('成员已移除')
  } catch (e: any) {
    memberMutationError.value = e.response?.data?.message || '成员操作失败'
    ElMessage.error(e.response?.data?.message || '成员操作失败')
  } finally {
    removingMemberId.value = null
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await kbStore.loadKnowledgeBases()
    if (kbStore.currentKb) {
      searchForm.knowledgeBaseId = kbStore.currentKb.id
    } else if (kbStore.knowledgeBases[0]) {
      selectKb(kbStore.knowledgeBases[0])
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.kb-page {
  display: grid;
  gap: 16px;
}

.member-toolbar {
  display: grid;
  grid-template-columns: 160px 140px auto;
  gap: 10px;
  margin-bottom: 14px;
}

.member-action-status,
.member-mutation-status {
  margin: 0 0 14px;
  padding: 14px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.member-action-status strong,
.member-action-status span,
.member-mutation-status strong,
.member-mutation-status span {
  display: block;
}

.member-action-status strong,
.member-mutation-status strong {
  color: var(--text);
}

.member-action-status span,
.member-mutation-status span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.kb-command-center {
  display: grid;
  gap: 18px;
}

.readiness-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr) 180px;
  gap: 14px;
  align-items: stretch;
}

.selected-kb-panel,
.readiness-item,
.kb-empty-guide,
.search-result-summary div {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.selected-kb-panel {
  padding: 16px;
}

.selected-kb-panel h3 {
  margin: 0;
  color: var(--text);
  font-size: 22px;
}

.selected-kb-panel p:not(.eyebrow) {
  margin: 8px 0 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.policy-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.policy-grid div {
  padding: 10px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
}

.policy-grid span,
.policy-grid strong {
  display: block;
}

.policy-grid span,
.readiness-item small,
.search-result-summary span {
  color: var(--text-muted);
  font-size: 12px;
}

.policy-grid strong,
.readiness-item strong,
.search-result-summary strong {
  margin-top: 4px;
  color: var(--text);
}

.readiness-checklist {
  display: grid;
  gap: 10px;
}

.readiness-item {
  padding: 12px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 4px 8px;
  align-content: start;
}

.readiness-item small {
  grid-column: 2;
  line-height: 1.5;
}

.readiness-item.ready {
  border-color: rgba(16, 185, 129, 0.35);
}

.status-dot.muted {
  background: var(--text-soft);
}

.kb-next-actions {
  display: grid;
  gap: 10px;
  align-content: start;
}

.kb-action {
  min-height: 40px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  color: var(--text);
  display: grid;
  place-items: center;
  font-weight: 700;
  text-decoration: none;
}

.kb-action.primary-action {
  border: 0;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-strong));
}

.kb-empty-guide {
  min-height: 160px;
  padding: 20px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  text-align: center;
}

.kb-empty-guide strong,
.kb-empty-guide span {
  display: block;
}

.kb-empty-guide strong {
  color: var(--text);
  font-size: 18px;
}

.kb-empty-guide span {
  margin-top: 6px;
  color: var(--text-muted);
}

.search-card {
  display: grid;
  gap: 18px;
}

.search-form {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(240px, 1fr) 120px 92px auto;
  gap: 12px;
  align-items: center;
}

.search-result-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.search-error-status {
  padding: 14px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.search-error-status strong,
.search-error-status span {
  display: block;
}

.search-error-status strong {
  color: var(--text);
}

.search-error-status span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.kb-action-status {
  margin: 0 0 14px;
  padding: 14px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.form-action-status {
  margin: 0 0 14px;
  padding: 14px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kb-action-status strong,
.kb-action-status span,
.form-action-status strong,
.form-action-status span {
  display: block;
}

.kb-action-status strong,
.form-action-status strong {
  color: var(--text);
}

.kb-action-status span,
.form-action-status span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.search-result-summary div {
  padding: 12px;
}

.search-result-summary span,
.search-result-summary strong {
  display: block;
}

.search-results {
  display: grid;
  gap: 10px;
}

.search-result {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-solid);
  padding: 14px;
}

.result-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text);
  font-size: 13px;
}

.result-head span {
  color: var(--text-muted);
  white-space: nowrap;
}

.search-result p {
  margin: 10px 0 0;
  color: var(--text-muted);
  line-height: 1.7;
  word-break: break-word;
}

@media (max-width: 760px) {
  .readiness-layout,
  .policy-grid,
  .search-result-summary {
    grid-template-columns: 1fr;
  }

  .search-form {
    grid-template-columns: 1fr;
  }

  .result-head {
    display: grid;
  }

  .search-error-status {
    align-items: stretch;
    flex-direction: column;
  }

  .kb-action-status,
  .form-action-status {
    align-items: stretch;
    flex-direction: column;
  }

  .result-head span {
    white-space: normal;
  }

  .member-toolbar {
    grid-template-columns: 1fr;
  }

  .member-action-status,
  .member-mutation-status {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
