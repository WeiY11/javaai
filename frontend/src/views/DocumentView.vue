<template>
  <div class="document-page">
    <div class="metric-grid">
      <div class="metric-card"><span>文档数</span><strong>{{ documents.length }}</strong></div>
      <div class="metric-card"><span>已完成</span><strong>{{ completedCount }}</strong></div>
      <div class="metric-card"><span>切片总数</span><strong>{{ totalChunks }}</strong></div>
      <div class="metric-card"><span>当前知识库</span><strong>{{ selectedKbName }}</strong></div>
    </div>

    <section class="workspace-card ingestion-pipeline">
      <div class="toolbar">
        <div>
          <h2 class="section-title">入库流程</h2>
          <p class="section-subtitle">上传后按阶段推进，完成索引后才适合进入问答。</p>
        </div>
        <el-tag :type="failedCount ? 'danger' : processingCount ? 'warning' : 'success'" effect="plain">
          {{ failedCount ? `${failedCount} 个失败` : processingCount ? `${processingCount} 个处理中` : '队列稳定' }}
        </el-tag>
      </div>
      <div class="pipeline-grid">
        <div v-for="stage in pipelineStages" :key="stage.label" class="pipeline-stage" :class="{ active: stage.count > 0 }">
          <span>{{ stage.index }}</span>
          <strong>{{ stage.label }}</strong>
          <small>{{ stage.detail }}</small>
          <em>{{ stage.count }} 个</em>
        </div>
      </div>
    </section>

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

      <div v-if="kbStore.knowledgeBases.length === 0" class="no-kb-guide">
        <div>
          <strong>先创建知识库</strong>
          <span>文档必须归属到知识库后才能切片、索引和进入问答。</span>
        </div>
        <RouterLink class="guide-link" to="/knowledge-bases">创建知识库</RouterLink>
      </div>

      <div v-if="workspaceLoading || workspaceLoadError" class="document-workspace-load-status">
        <div>
          <strong>{{ workspaceLoading ? '正在加载文档工作台' : '文档工作台加载失败' }}</strong>
          <span>{{ workspaceLoading ? '正在同步知识库和文档列表...' : workspaceLoadError }}</span>
        </div>
        <el-button
          v-if="workspaceLoadError"
          type="primary"
          plain
          :loading="workspaceLoading"
          @click="loadWorkspaceData"
        >
          重新加载
        </el-button>
      </div>

      <div v-if="documentLoadError" class="document-load-status">
        <div>
          <strong>文档列表加载失败</strong>
          <span>{{ documentLoadError }}</span>
        </div>
        <el-button type="primary" plain @click="loadDocuments">重新加载</el-button>
      </div>

      <div v-if="failedCount > 0" class="recovery-banner">
        <el-icon><Warning /></el-icon>
        <div>
          <strong>{{ failedCount }} 个文档入库失败</strong>
          <span>先重试失败任务；如果仍失败，再查看文件格式或切片配置。</span>
        </div>
        <el-button
          type="danger"
          plain
          :loading="retryingFailedDocuments"
          :disabled="retryingFailedDocuments"
          @click="retryFailedDocuments"
        >
          <el-icon><RefreshRight /></el-icon>
          <span>重新提交失败任务</span>
        </el-button>
      </div>

      <div v-if="ingestionActionError" class="ingestion-recovery-status">
        <div>
          <strong>入库重试失败</strong>
          <span>{{ ingestionActionError }}</span>
        </div>
        <el-button
          type="danger"
          plain
          :loading="retryingFailedDocuments"
          :disabled="retryingFailedDocuments || failedCount === 0"
          @click="retryFailedDocuments"
        >
          重新提交失败任务
        </el-button>
      </div>

      <div v-if="documentActionError" class="document-action-status">
        <div>
          <strong>文档操作失败</strong>
          <span>{{ documentActionError }}</span>
        </div>
        <el-button type="primary" plain :loading="loading" @click="loadDocuments">重新加载文档</el-button>
      </div>

      <div v-if="uploadActionError" class="document-upload-status">
        <div>
          <strong>上传文档失败</strong>
          <span>{{ uploadActionError }}</span>
        </div>
        <el-button type="primary" plain :loading="loading" @click="loadDocuments">重新加载文档</el-button>
      </div>

      <el-upload
        class="upload-zone"
        v-loading="uploadingDocument"
        :auto-upload="false"
        :show-file-list="false"
        :disabled="!selectedKbId || uploadingDocument"
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
            <el-button
              v-if="row.ingestionStatus === 'FAILED'"
              size="small"
              :disabled="deletingDocumentId === row.id || retryingDocumentId === row.id"
              :loading="retryingDocumentId === row.id"
              @click="retryIngestion(row.id)"
            >
              重试
            </el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="deletingDocumentId === row.id || retryingDocumentId === row.id"
              :loading="deletingDocumentId === row.id"
              @click="handleDelete(row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="document-empty-guide">
            <el-icon><UploadFilled /></el-icon>
            <strong>{{ selectedKbId ? '上传第一份文档' : '先选择知识库' }}</strong>
            <span>
              {{ selectedKbId ? '完成入库后，问答页才能引用这些切片。' : '没有知识库时不能上传文档。' }}
            </span>
            <RouterLink v-if="!selectedKbId" to="/knowledge-bases">去创建知识库</RouterLink>
          </div>
        </template>
      </el-table>
    </section>

    <el-drawer v-model="showChunks" title="文档切片" size="560px">
      <div v-loading="chunksLoading" class="chunk-list">
        <div v-if="chunkLoadError" class="chunk-load-status">
          <div>
            <strong>文档切片加载失败</strong>
            <span>{{ chunkLoadError }}</span>
          </div>
          <el-button type="primary" plain :loading="chunksLoading" @click="reloadChunks">重新加载切片</el-button>
        </div>
        <article v-for="chunk in chunks" :key="chunk.id" class="chunk-item">
          <el-tag size="small" type="info">切片 #{{ chunk.chunkIndex }}</el-tag>
          <p>{{ chunk.content }}</p>
        </article>
        <el-empty v-if="!chunksLoading && !chunkLoadError && chunks.length === 0" description="暂无切片" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight, UploadFilled, Warning } from '@element-plus/icons-vue'
import type { Document } from '../types/document.types'
import type { DocumentChunk } from '../types/chunk.types'
import * as docApi from '../api/document'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'

type IngestionStatus = Document['ingestionStatus']

const props = defineProps<{ knowledgeBaseId?: number }>()
const kbStore = useKnowledgeBaseStore()
const selectedKbId = ref<number | undefined>(props.knowledgeBaseId)
const documents = ref<Document[]>([])
const chunks = ref<DocumentChunk[]>([])
const loading = ref(false)
const workspaceLoading = ref(false)
const workspaceLoadError = ref('')
const documentLoadError = ref('')
const retryingFailedDocuments = ref(false)
const ingestionActionError = ref('')
const documentActionError = ref('')
const deletingDocumentId = ref<number | null>(null)
const retryingDocumentId = ref<number | null>(null)
const uploadingDocument = ref(false)
const uploadActionError = ref('')
const showChunks = ref(false)
const chunksLoading = ref(false)
const chunkLoadError = ref('')
const activeChunkDocument = ref<Document | null>(null)

const completedCount = computed(() => documents.value.filter(doc => doc.ingestionStatus === 'COMPLETED').length)
const totalChunks = computed(() => documents.value.reduce((sum, doc) => sum + (doc.chunkCount || 0), 0))
const selectedKbName = computed(() => kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value)?.name || '未选择')
const failedCount = computed(() => documents.value.filter(doc => doc.ingestionStatus === 'FAILED').length)
const processingStatuses: IngestionStatus[] = ['PENDING', 'EXTRACTING', 'CLEANING', 'CHUNKING', 'EMBEDDING', 'INDEXING']
const processingCount = computed(() => documents.value.filter(doc => processingStatuses.includes(doc.ingestionStatus)).length)
const pipelineStages = computed(() => [
  {
    index: '01',
    label: '提取文本',
    detail: '解析 PDF、Office、Markdown 等文件内容。',
    count: countByStatus(['PENDING', 'EXTRACTING'])
  },
  {
    index: '02',
    label: '清洗切片',
    detail: '清理正文并按知识库策略生成切片。',
    count: countByStatus(['CLEANING', 'CHUNKING'])
  },
  {
    index: '03',
    label: '嵌入索引',
    detail: '写入向量、关键词和检索索引。',
    count: countByStatus(['EMBEDDING', 'INDEXING'])
  },
  {
    index: '04',
    label: '可问答',
    detail: '完成后可在问答页作为证据引用。',
    count: completedCount.value
  }
])

watch(() => props.knowledgeBaseId, (id) => {
  if (id) {
    selectedKbId.value = id
    loadDocuments()
  }
})

async function loadWorkspaceData() {
  workspaceLoading.value = true
  workspaceLoadError.value = ''
  try {
    await kbStore.loadKnowledgeBases()
    if (!selectedKbId.value || !kbStore.knowledgeBases.some(kb => kb.id === selectedKbId.value)) {
      const routeKb = kbStore.knowledgeBases.find(kb => kb.id === props.knowledgeBaseId)
      selectedKbId.value = routeKb?.id ?? kbStore.currentKb?.id ?? kbStore.knowledgeBases[0]?.id
    }
    if (selectedKbId.value) await loadDocuments()
  } catch (e: any) {
    documents.value = []
    workspaceLoadError.value = e.response?.data?.message || '加载文档工作台失败'
    ElMessage.error(workspaceLoadError.value)
  } finally {
    workspaceLoading.value = false
  }
}

async function loadDocuments() {
  if (!selectedKbId.value) {
    documents.value = []
    documentLoadError.value = ''
    return
  }
  loading.value = true
  documentLoadError.value = ''
  try {
    const res = await docApi.listDocuments(selectedKbId.value, 1, 100)
    documents.value = res.records
  } catch (e: any) {
    documents.value = []
    documentLoadError.value = e.response?.data?.message || '加载文档失败'
    ElMessage.error(e.response?.data?.message || '加载文档失败')
  } finally {
    loading.value = false
  }
}

async function handleFileChange(file: any) {
  if (!selectedKbId.value || !file.raw) {
    ElMessage.warning('请先选择知识库')
    return
  }
  uploadingDocument.value = true
  uploadActionError.value = ''
  try {
    await docApi.uploadDocument(file.raw, selectedKbId.value)
    ElMessage.success('上传成功，正在入库')
    await loadDocuments()
  } catch (e: any) {
    uploadActionError.value = e.response?.data?.message || '上传文档失败'
    ElMessage.error(e.response?.data?.message || '上传文档失败')
  } finally {
    uploadingDocument.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该文档？', '删除文档', { type: 'warning' })
    documentActionError.value = ''
    deletingDocumentId.value = id
    await docApi.deleteDocument(id)
    await loadDocuments()
    ElMessage.success('文档已删除')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    documentActionError.value = e.response?.data?.message || '文档操作失败'
    ElMessage.error(e.response?.data?.message || '文档操作失败')
  } finally {
    deletingDocumentId.value = null
  }
}

async function retryIngestion(id: number) {
  retryingDocumentId.value = id
  documentActionError.value = ''
  try {
    await docApi.retryIngestion(id)
    ElMessage.info('已重新提交入库任务')
    await loadDocuments()
  } catch (e: any) {
    documentActionError.value = e.response?.data?.message || '文档操作失败'
    ElMessage.error(e.response?.data?.message || '文档操作失败')
  } finally {
    retryingDocumentId.value = null
  }
}

async function retryFailedDocuments() {
  const failedDocuments = documents.value.filter(doc => doc.ingestionStatus === 'FAILED')
  if (failedDocuments.length === 0) return
  retryingFailedDocuments.value = true
  ingestionActionError.value = ''
  try {
    await Promise.all(failedDocuments.map(doc => docApi.retryIngestion(doc.id)))
    ElMessage.success('已重新提交失败任务')
    await loadDocuments()
  } catch (e: any) {
    ingestionActionError.value = e.response?.data?.message || '入库重试失败'
    ElMessage.error(e.response?.data?.message || '入库重试失败')
  } finally {
    retryingFailedDocuments.value = false
  }
}

async function openChunks(doc: Document) {
  showChunks.value = true
  activeChunkDocument.value = doc
  chunksLoading.value = true
  chunkLoadError.value = ''
  try {
    chunks.value = await docApi.getDocumentChunks(doc.id)
  } catch (e: any) {
    chunks.value = []
    chunkLoadError.value = e.response?.data?.message || '加载文档切片失败'
    ElMessage.error(chunkLoadError.value)
  } finally {
    chunksLoading.value = false
  }
}

async function reloadChunks() {
  if (activeChunkDocument.value) await openChunks(activeChunkDocument.value)
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

function countByStatus(statuses: IngestionStatus[]): number {
  return documents.value.filter(doc => statuses.includes(doc.ingestionStatus)).length
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

onMounted(() => {
  loadWorkspaceData()
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

.ingestion-pipeline {
  display: grid;
  gap: 16px;
}

.pipeline-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.pipeline-stage {
  min-height: 136px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  display: grid;
  align-content: start;
  gap: 8px;
}

.pipeline-stage.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.pipeline-stage span {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--primary);
  background: var(--surface-solid);
  font-weight: 800;
}

.pipeline-stage strong {
  color: var(--text);
}

.pipeline-stage small {
  color: var(--text-muted);
  line-height: 1.5;
}

.pipeline-stage em {
  margin-top: 4px;
  color: var(--text);
  font-style: normal;
  font-weight: 700;
}

.no-kb-guide,
.document-workspace-load-status,
.document-load-status,
.document-action-status,
.document-upload-status,
.ingestion-recovery-status,
.recovery-banner {
  margin: 16px 0;
  padding: 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-soft);
  display: flex;
  align-items: center;
  gap: 12px;
}

.no-kb-guide {
  justify-content: space-between;
}

.document-workspace-load-status,
.document-load-status,
.document-action-status,
.document-upload-status {
  justify-content: space-between;
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}

.ingestion-recovery-status {
  justify-content: space-between;
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}

.recovery-banner {
  border-color: rgba(225, 29, 72, 0.3);
}

.recovery-banner .el-icon {
  color: var(--danger);
  font-size: 22px;
}

.no-kb-guide strong,
.no-kb-guide span,
.document-workspace-load-status strong,
.document-workspace-load-status span,
.document-load-status strong,
.document-load-status span,
.document-action-status strong,
.document-action-status span,
.document-upload-status strong,
.document-upload-status span,
.ingestion-recovery-status strong,
.ingestion-recovery-status span,
.recovery-banner strong,
.recovery-banner span {
  display: block;
}

.no-kb-guide strong,
.document-workspace-load-status strong,
.document-load-status strong,
.document-action-status strong,
.document-upload-status strong,
.ingestion-recovery-status strong,
.recovery-banner strong {
  color: var(--text);
}

.no-kb-guide span,
.document-workspace-load-status span,
.document-load-status span,
.document-action-status span,
.document-upload-status span,
.ingestion-recovery-status span,
.recovery-banner span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.guide-link,
.document-empty-guide a {
  color: var(--primary);
  font-weight: 700;
  text-decoration: none;
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

.document-empty-guide {
  min-height: 180px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--text-muted);
}

.document-empty-guide .el-icon {
  color: var(--primary);
  font-size: 30px;
}

.document-empty-guide strong {
  color: var(--text);
  font-size: 16px;
}

.document-empty-guide span {
  max-width: 360px;
  line-height: 1.6;
}

.chunk-list {
  display: grid;
  gap: 10px;
}

.chunk-load-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  color: var(--text-muted);
  font-size: 13px;
}

.chunk-load-status strong,
.chunk-load-status span {
  display: block;
}

.chunk-load-status strong {
  color: var(--text);
}

.chunk-load-status span {
  margin-top: 4px;
  line-height: 1.5;
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
  .pipeline-grid {
    grid-template-columns: 1fr;
  }
  .no-kb-guide,
  .document-workspace-load-status,
  .document-load-status,
  .ingestion-recovery-status,
  .recovery-banner,
  .chunk-load-status {
    align-items: stretch;
    flex-direction: column;
  }
  .kb-select {
    width: 100%;
  }
}
</style>
