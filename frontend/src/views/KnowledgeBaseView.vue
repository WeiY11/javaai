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
          <el-table-column prop="status" label="状态" width="80" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="kbStore.selectKb(row)">选择</el-button>
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

    <el-dialog v-model="showCreateDialog" title="创建知识库">
      <el-form :model="createForm">
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" /></el-form-item>
        <el-form-item label="切片策略">
          <el-select v-model="createForm.chunkStrategy">
            <el-option label="段落" value="PARAGRAPH" />
            <el-option label="固定长度" value="FIXED_LENGTH" />
            <el-option label="语义" value="SEMANTIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="切片大小"><el-input-number v-model="createForm.chunkSize" :min="100" :max="2000" /></el-form-item>
        <el-form-item label="重叠大小"><el-input-number v-model="createForm.chunkOverlap" :min="0" :max="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import DocumentView from './DocumentView.vue'
import { ElMessage } from 'element-plus'

const kbStore = useKnowledgeBaseStore()
const loading = ref(false)
const showCreateDialog = ref(false)
const createForm = reactive({
  name: '', description: '', chunkStrategy: 'PARAGRAPH' as const,
  chunkSize: 500, chunkOverlap: 100, groupId: 1
})

async function handleCreate() {
  try {
    await kbStore.createKb({ ...createForm, chunkStrategy: createForm.chunkStrategy })
    showCreateDialog.value = false
    ElMessage.success('创建成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  }
}

onMounted(() => {
  kbStore.loadKnowledgeBases(1)
})
</script>

<style scoped>
.kb-container { padding: 24px; }
</style>
