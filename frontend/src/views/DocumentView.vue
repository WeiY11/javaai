<template>
  <div class="doc-container">
    <h3>文档管理</h3>
    <el-upload :auto-upload="false" :on-change="handleFileChange" accept=".pdf,.docx,.xlsx,.csv,.json,.md,.txt">
      <el-button type="primary" size="small">选择文件上传</el-button>
    </el-upload>
    <el-table :data="documents" style="margin-top:12px" v-loading="loading" size="small">
      <el-table-column prop="fileName" label="文件名" />
      <el-table-column prop="fileFormat" label="格式" width="80" />
      <el-table-column prop="fileSize" label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="ingestionStatus" label="入库状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.ingestionStatus)">{{ row.ingestionStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="切片数" width="80" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.ingestionStatus === 'FAILED'" size="small" @click="retryIngestion(row.id)">重试</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { Document } from '../types/document.types'
import * as docApi from '../api/document'
import { ElMessage } from 'element-plus'

const props = defineProps<{ knowledgeBaseId: number }>()
const documents = ref<Document[]>([])
const loading = ref(false)

async function loadDocuments() {
  loading.value = true
  try {
    const res = await docApi.listDocuments(props.knowledgeBaseId)
    documents.value = res.records
  } finally {
    loading.value = false
  }
}

async function handleFileChange(file: any) {
  try {
    await docApi.uploadDocument(file.raw, props.knowledgeBaseId)
    ElMessage.success('上传成功，正在入库...')
    await loadDocuments()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  }
}

async function handleDelete(id: number) {
  await docApi.deleteDocument(id)
  await loadDocuments()
}

async function retryIngestion(id: number) {
  await docApi.retryIngestion(id)
  ElMessage.info('重试入库中...')
  await loadDocuments()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1048576).toFixed(1) + 'MB'
}

function statusType(status: string): string {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'warning'
}

onMounted(loadDocuments)
</script>

<style scoped>
.doc-container { padding: 12px; }
</style>
