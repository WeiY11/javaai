<template>
  <div class="doc-container">
    <h3>文档管理</h3>
    <el-upload :auto-upload="false" :on-change="handleFileChange" accept=".pdf,.docx,.xlsx,.csv,.json,.md,.txt"
               drag>
      <el-icon><Plus /></el-icon>
      <div>拖拽或点击上传文件</div>
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
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="openChunks(row)">查看切片</el-button>
          <el-button v-if="row.ingestionStatus === 'FAILED'" size="small" @click="retryIngestion(row.id)">重试</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Chunk Viewer Drawer -->
    <el-drawer v-model="showChunks" title="文档切片" :size="500">
      <div v-loading="chunksLoading">
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-item">
          <el-tag size="small" type="info">切片 #{{ chunk.chunkIndex }}</el-tag>
          <p>{{ chunk.content }}</p>
        </div>
        <el-empty v-if="!chunksLoading && chunks.length === 0" description="暂无切片" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { Document } from '../types/document.types'
import type { DocumentChunk } from '../types/chunk.types'
import * as docApi from '../api/document'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const props = defineProps<{ knowledgeBaseId: number }>()
const documents = ref<Document[]>([])
const loading = ref(false)
const showChunks = ref(false)
const chunks = ref<DocumentChunk[]>([])
const chunksLoading = ref(false)

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

async function openChunks(doc: Document) {
  showChunks.value = true
  chunksLoading.value = true
  try {
    chunks.value = await docApi.getDocumentChunks(doc.id)
  } catch {
    chunks.value = []
  } finally {
    chunksLoading.value = false
  }
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
.chunk-item {
  padding: 12px;
  margin: 8px 0;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}
.chunk-item p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
