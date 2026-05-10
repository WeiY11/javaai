<template>
  <div class="kb-page">
    <div class="metric-grid">
      <div class="metric-card"><span>知识库</span><strong>{{ kbStore.knowledgeBases.length }}</strong></div>
      <div class="metric-card"><span>已启用</span><strong>{{ activeCount }}</strong></div>
      <div class="metric-card"><span>平均阈值</span><strong>{{ averageThreshold }}</strong></div>
      <div class="metric-card"><span>当前选择</span><strong>{{ kbStore.currentKb?.name || '无' }}</strong></div>
    </div>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">知识库管理</h2>
          <p class="section-subtitle">配置切片策略、证据阈值和团队成员权限。</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">创建知识库</el-button>
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
            <el-button size="small" @click="kbStore.selectKb(row)">选择</el-button>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" @click="openMembersDialog(row)">成员</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
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
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showMembersDialog" title="成员管理" width="620px">
      <div class="member-toolbar">
        <el-input-number v-model="newMemberId" placeholder="用户 ID" :min="1" />
        <el-select v-model="newMemberRole">
          <el-option label="成员" value="MEMBER" />
          <el-option label="所有者" value="OWNER" />
        </el-select>
        <el-button type="primary" :disabled="!newMemberId" @click="handleAddMember">添加</el-button>
      </div>
      <el-table :data="members" size="small">
        <el-table-column prop="userId" label="用户 ID" />
        <el-table-column prop="role" label="角色" />
        <el-table-column prop="joinedAt" label="加入时间" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleRemoveMember(row.userId)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import * as kbApi from '../api/knowledge-base'
import type { KbMember, KnowledgeBase } from '../types/knowledge-base.types'

const kbStore = useKnowledgeBaseStore()
const loading = ref(false)
const showFormDialog = ref(false)
const showMembersDialog = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const members = ref<KbMember[]>([])
const currentManageKbId = ref<number | null>(null)
const newMemberId = ref<number | null>(null)
const newMemberRole = ref<'MEMBER' | 'OWNER'>('MEMBER')

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
  showFormDialog.value = true
}

async function submitForm() {
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
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('删除后相关文档和对话可能不可用，确认删除？', '删除知识库', { type: 'warning' })
  await kbStore.deleteKb(id)
  ElMessage.success('已删除知识库')
}

async function openMembersDialog(kb: KnowledgeBase) {
  currentManageKbId.value = kb.id
  members.value = await kbApi.listMembers(kb.id)
  showMembersDialog.value = true
}

async function handleAddMember() {
  if (!currentManageKbId.value || !newMemberId.value) return
  await kbApi.addMember(currentManageKbId.value, newMemberId.value, newMemberRole.value)
  members.value = await kbApi.listMembers(currentManageKbId.value)
  newMemberId.value = null
  ElMessage.success('成员已添加')
}

async function handleRemoveMember(userId: number) {
  if (!currentManageKbId.value) return
  await kbApi.removeMember(currentManageKbId.value, userId)
  members.value = await kbApi.listMembers(currentManageKbId.value)
  ElMessage.success('成员已移除')
}

onMounted(async () => {
  loading.value = true
  try {
    await kbStore.loadKnowledgeBases()
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

@media (max-width: 760px) {
  .member-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
