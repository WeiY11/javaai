<template>
  <div class="document-page">
    <div class="metric-grid">
      <div class="metric-card"><span>文档数</span><strong>{{ documents.length }}</strong></div>
      <div class="metric-card"><span>已完成</span><strong>{{ completedCount }}</strong></div>
      <div class="metric-card"><span>切片总数</span><strong>{{ totalChunks }}</strong></div>
      <div class="metric-card"><span>当前知识库</span><strong>{{ selectedKbName }}</strong></div>
    </div>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">文档入库</h2>
          <p class="section-subtitle">上传资料后自动提取、清洗、切片并建立检索索引。</p>
        </div>
        <el-select v-model="selectedKbId" placeholder="选择知识库" filterable class="kb-select" @change="loadDocuments">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>

      <el-upload
        class="upload-zone"
        :auto-upload="false"
        :show-file-list="false"
        :disabled="!selectedKbId"
        :on-change="handleFileChange"
        accept=".pdf,.docx,.xlsx,.csv,.json,.md,.txt,.pptx,.epub"
        drag
      >
        <div class="upload-copy">
          <strong>拖拽或点击上传文档</strong>
          <span>支持 PDF、Word、Excel、CSV、JSON、Markdown、TXT、PPT、EPUB</span>
        </div>
      </el-upload>

      <el-table :data="documents" v-loading="loading" style="margin-top: 16px">
        <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileFormat" label="格式" width="90" />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="入库状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.ingestionStatus)">{{ statusLabel(row.ingestionStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片" width="90" />
        <el-table-column prop="createdAt" label="上传时间" min-width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openChunks(row)">切片</el-button>
            <el-button v-if="row.ingestionStatus === 'FAILED'" size="small" @click="retryIngestion(row.id)">重试</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="showChunks" title="文档切片" size="560px">
      <div v-loading="chunksLoading" class="chunk-list">
        <article v-for="chunk in chunks" :key="chunk.id" class="chunk-item">
          <el-tag size="small" type="info">切片 #{{ chunk.chunkIndex }}</el-tag>
          <p>{{ chunk.content }}</p>
        </article>
        <el-empty v-if="!chunksLoading && chunks.length === 0" description="暂无切片" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Document } from '../types/document.types'
import type { DocumentChunk } from '../types/chunk.types'
import * as docApi from '../api/document'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'

const props = defineProps<{ knowledgeBaseId?: number }>()
const kbStore = useKnowledgeBaseStore()
const selectedKbId = ref<number | undefined>(props.knowledgeBaseId)
const documents = ref<Document[]>([])
const chunks = ref<DocumentChunk[]>([])
const loading = ref(false)
const showChunks = ref(false)
const chunksLoading = ref(false)

const completedCount = computed(() => documents.value.filter(doc => doc.ingestionStatus === 'COMPLETED').length)
const totalChunks = computed(() => documents.value.reduce((sum, doc) => sum + (doc.chunkCount || 0), 0))
const selectedKbName = computed(() => kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value)?.name || '未选择')

watch(() => props.knowledgeBaseId, (id) => {
  if (id) {
    selectedKbId.value = id
    loadDocuments()
  }
})

async function loadDocuments() {
  if (!selectedKbId.value) {
    documents.value = []
    return
  }
  loading.value = true
  try {
    const res = await docApi.listDocuments(selectedKbId.value, 1, 100)
    documents.value = res.records
  } finally {
    loading.value = false
  }
}

async function handleFileChange(file: any) {
  if (!selectedKbId.value || !file.raw) {
    ElMessage.warning('请先选择知识库')
    return
  }
  try {
    await docApi.uploadDocument(file.raw, selectedKbId.value)
    ElMessage.success('上传成功，正在入库')
    await loadDocuments()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确认删除该文档？', '删除文档', { type: 'warning' })
  await docApi.deleteDocument(id)
  await loadDocuments()
}

async function retryIngestion(id: number) {
  await docApi.retryIngestion(id)
  ElMessage.info('已重新提交入库任务')
  await loadDocuments()
}

async function openChunks(doc: Document) {
  showChunks.value = true
  chunksLoading.value = true
  try {
    chunks.value = await docApi.getDocumentChunks(doc.id)
  } finally {
    chunksLoading.value = false
  }
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'warning'
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    PENDING: '等待中',
    EXTRACTING: '提取中',
    CLEANING: '清洗中',
    CHUNKING: '切片中',
    EMBEDDING: '嵌入中',
    INDEXING: '索引中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return labels[status] || status
}

onMounted(async () => {
  await kbStore.loadKnowledgeBases()
  if (!selectedKbId.value) selectedKbId.value = kbStore.knowledgeBases[0]?.id
  await loadDocuments()
})
</script>

<style scoped>
.document-page {
  display: grid;
  gap: 16px;
}

.kb-select {
  width: 260px;
}

.upload-zone {
  width: 100%;
}

.upload-copy {
  display: grid;
  gap: 6px;
  color: var(--text);
}

.upload-copy span {
  color: var(--text-muted);
  font-size: 13px;
}

.chunk-list {
  display: grid;
  gap: 10px;
}

.chunk-item {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.chunk-item p {
  margin: 10px 0 0;
  color: var(--text);
  line-height: 1.7;
  white-space: pre-wrap;
}

@media (max-width: 760px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .kb-select {
    width: 100%;
  }
}
</style>
