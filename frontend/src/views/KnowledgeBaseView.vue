<template>
  <div class="kb-container">
    <h2>知识库管理</h2>
    <el-row :gutter="16">
      <el-col :span="16">
        <el-button type="primary" @click="showCreateDialog = true">创建知识库</el-button>
        <el-table :data="kbStore.knowledgeBases" style="margin-top:16px" v-loading="loading">
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="chunkStrategy" label="切片策略" width="120" />
          <el-table-column prop="chunkSize" label="切片大小" width="100" />
          <el-table-column prop="evidenceThreshold" label="证据阈值" width="100" />
          <el-table-column prop="status" label="状态" width="80" />
          <el-table-column label="操作" width="250">
            <template #default="{ row }">
              <el-button size="small" @click="kbStore.selectKb(row)">选择</el-button>
              <el-button size="small" type="warning" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" @click="openMembersDialog(row)">成员</el-button>
              <el-button size="small" type="danger" @click="kbStore.deleteKb(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="8">
        <DocumentView v-if="kbStore.currentKb" :knowledge-base-id="kbStore.currentKb.id" />
        <el-empty v-else description="请选择知识库" />
      </el-col>
    </el-row>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建知识库">
      <el-form :model="createForm">
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" /></el-form-item>
        <el-form-item label="切片策略">
          <el-select v-model="createForm.chunkStrategy">
            <el-option label="段落 (按段落边界切分)" value="PARAGRAPH" />
            <el-option label="固定长度 (按字符数切分)" value="FIXED_LENGTH" />
            <el-option label="语义 (按句子边界切分)" value="SEMANTIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="切片大小"><el-input-number v-model="createForm.chunkSize" :min="100" :max="2000" /></el-form-item>
        <el-form-item label="重叠大小"><el-input-number v-model="createForm.chunkOverlap" :min="0" :max="500" /></el-form-item>
        <el-form-item label="证据阈值">
          <el-slider v-model="createForm.evidenceThreshold" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- Edit Dialog -->
    <el-dialog v-model="showEditDialog" title="编辑知识库">
      <el-form :model="editForm">
        <el-form-item label="名称"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" /></el-form-item>
        <el-form-item label="证据阈值">
          <el-slider v-model="editForm.evidenceThreshold" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- Members Dialog -->
    <el-dialog v-model="showMembersDialog" title="成员管理">
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-input-number v-model="newMemberId" placeholder="用户ID" :min="1" size="small" style="width:120px" />
        <el-select v-model="newMemberRole" size="small" style="width:100px">
          <el-option label="成员" value="MEMBER" />
          <el-option label="所有者" value="OWNER" />
        </el-select>
        <el-button type="primary" size="small" @click="handleAddMember" :disabled="!newMemberId">添加</el-button>
      </div>
      <el-table :data="members" size="small">
        <el-table-column prop="userId" label="用户ID" />
        <el-table-column prop="role" label="角色" />
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
import { ref, reactive, onMounted } from 'vue'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import * as kbApi from '../api/knowledge-base'
import type { KnowledgeBase, KbMember } from '../types/knowledge-base.types'
import DocumentView from './DocumentView.vue'
import { ElMessage } from 'element-plus'

const kbStore = useKnowledgeBaseStore()
const loading = ref(false)
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const showMembersDialog = ref(false)
const members = ref<KbMember[]>([])
const currentManageKbId = ref<number | null>(null)
const newMemberId = ref<number | null>(null)
const newMemberRole = ref('MEMBER')

const createForm = reactive({
  name: '', description: '', chunkStrategy: 'PARAGRAPH' as string,
  chunkSize: 500, chunkOverlap: 100, evidenceThreshold: 0.5
})

const editForm = reactive({
  id: 0, name: '', description: '', evidenceThreshold: 0.5
})

async function handleCreate() {
  try {
    await kbStore.createKb({ ...createForm, chunkStrategy: createForm.chunkStrategy as any })
    showCreateDialog.value = false
    ElMessage.success('创建成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  }
}

function openEditDialog(kb: KnowledgeBase) {
  editForm.id = kb.id
  editForm.name = kb.name
  editForm.description = kb.description || ''
  editForm.evidenceThreshold = kb.evidenceThreshold
  showEditDialog.value = true
}

async function handleEdit() {
  try {
    await kbApi.updateKnowledgeBase(editForm.id, {
      name: editForm.name,
      description: editForm.description,
      evidenceThreshold: editForm.evidenceThreshold
    })
    showEditDialog.value = false
    await kbStore.loadKnowledgeBases()
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function openMembersDialog(kb: KnowledgeBase) {
  currentManageKbId.value = kb.id
  try {
    members.value = await kbApi.listMembers(kb.id)
  } catch {
    members.value = []
  }
  showMembersDialog.value = true
}

async function handleAddMember() {
  if (!currentManageKbId.value || !newMemberId.value) return
  try {
    await kbApi.addMember(currentManageKbId.value, newMemberId.value, newMemberRole.value)
    members.value = await kbApi.listMembers(currentManageKbId.value)
    newMemberId.value = null
    ElMessage.success('添加成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '添加失败')
  }
}

async function handleRemoveMember(userId: number) {
  if (!currentManageKbId.value) return
  try {
    await kbApi.removeMember(currentManageKbId.value, userId)
    members.value = await kbApi.listMembers(currentManageKbId.value)
    ElMessage.success('移除成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '移除失败')
  }
}

onMounted(() => {
  kbStore.loadKnowledgeBases()
})
</script>

<style scoped>
.kb-container { padding: 24px; }
</style>
