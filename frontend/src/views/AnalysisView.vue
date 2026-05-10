<template>
  <div class="analysis-page">
    <div class="analysis-grid">
      <section class="workspace-card">
        <div class="toolbar">
          <div>
            <h2 class="section-title">文件浏览</h2>
            <p class="section-subtitle">选择可分析文件或直接分析当前目录。</p>
          </div>
          <el-button @click="loadFiles(currentDir)">刷新</el-button>
        </div>

        <div class="path-bar">
          <el-input v-model="currentDir" placeholder="目录路径，例如 data" @keyup.enter="loadFiles(currentDir)" />
          <el-button @click="goUp">上级</el-button>
        </div>

        <el-table
          :data="files"
          v-loading="filesLoading"
          @selection-change="selectedFiles = $event"
        >
          <el-table-column type="selection" width="44" :selectable="isSelectableFile" />
          <el-table-column prop="name" label="名称" min-width="220">
            <template #default="{ row }">
              <el-button v-if="row.isDir" text type="primary" @click="loadFiles(row.path)">📁 {{ row.name }}</el-button>
              <span v-else>{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="类型" width="110" />
          <el-table-column label="大小" width="110">
            <template #default="{ row }">{{ row.isDir ? '-' : formatSize(row.size) }}</template>
          </el-table-column>
        </el-table>

        <div class="analysis-actions">
          <el-select v-model="provider" class="provider-select">
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="智谱 GLM-4" value="zhipu" />
            <el-option label="通义千问" value="qianwen" />
            <el-option label="OpenAI" value="openai" />
          </el-select>
          <el-button type="primary" :disabled="selectedFiles.length === 0" @click="startSelectedBatch">分析选中文件</el-button>
          <el-button @click="startDirectoryBatch">分析当前目录</el-button>
        </div>
      </section>

      <section class="workspace-card progress-card">
        <h2 class="section-title">任务进度</h2>
        <p class="section-subtitle">批量分析会异步执行，可在完成后查看结果并导出报告。</p>
        <el-progress :percentage="progressPercent" :status="progressStatus" />
        <dl class="progress-detail">
          <div><dt>任务 ID</dt><dd>{{ taskId || '尚未开始' }}</dd></div>
          <div><dt>状态</dt><dd>{{ progress?.status || 'IDLE' }}</dd></div>
          <div><dt>当前文件</dt><dd>{{ progress?.currentFile || '-' }}</dd></div>
        </dl>
      </section>
    </div>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">分析结果</h2>
          <p class="section-subtitle">选择结果后可导出 Markdown 或 PDF 报告。</p>
        </div>
        <div class="export-actions">
          <el-button :disabled="selectedResultIds.length === 0" tag="a" :href="markdownUrl" target="_blank">导出 Markdown</el-button>
          <el-button :disabled="selectedResultIds.length === 0" tag="a" :href="pdfUrl" target="_blank">导出 PDF</el-button>
        </div>
      </div>

      <el-table :data="analysisResults" v-loading="resultsLoading" @selection-change="handleResultSelection">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="provider" label="模型" width="110" />
        <el-table-column prop="fileCategory" label="类型" width="110" />
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="摘要" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { AnalysisResult, BatchProgress, FileItem } from '../types/analysis.types'
import * as analysisApi from '../api/analysis'

const currentDir = ref('')
const files = ref<FileItem[]>([])
const selectedFiles = ref<FileItem[]>([])
const filesLoading = ref(false)
const provider = ref('deepseek')
const taskId = ref('')
const progress = ref<BatchProgress | null>(null)
const pollTimer = ref<number | undefined>()
const analysisResults = ref<AnalysisResult[]>([])
const resultsLoading = ref(false)
const selectedResultIds = ref<string[]>([])

const progressPercent = computed(() => {
  if (!progress.value?.total) return 0
  return Math.round((progress.value.completed / progress.value.total) * 100)
})

const progressStatus = computed(() => {
  if (progress.value?.status === 'FAILED') return 'exception'
  if (progress.value?.status === 'COMPLETED') return 'success'
  return undefined
})

const markdownUrl = computed(() => analysisApi.markdownExportUrl(selectedResultIds.value))
const pdfUrl = computed(() => analysisApi.pdfExportUrl(selectedResultIds.value))

async function loadFiles(dir = '') {
  filesLoading.value = true
  try {
    const res = await analysisApi.listFiles(dir)
    currentDir.value = res.currentDir || dir
    files.value = res.items
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载文件失败')
  } finally {
    filesLoading.value = false
  }
}

function goUp() {
  const parts = currentDir.value.split('/').filter(Boolean)
  parts.pop()
  loadFiles(parts.join('/'))
}

function isSelectableFile(row: FileItem): boolean {
  return !row.isDir
}

async function startSelectedBatch() {
  const paths = selectedFiles.value.filter(file => !file.isDir).map(file => file.path)
  if (!paths.length) return
  const res = await analysisApi.startBatchAnalysis({ paths, provider: provider.value })
  startPolling(res.taskId)
}

async function startDirectoryBatch() {
  const res = await analysisApi.startDirectoryAnalysis(currentDir.value, provider.value)
  startPolling(res.taskId)
}

function startPolling(id: string) {
  taskId.value = id
  window.clearInterval(pollTimer.value)
  pollTimer.value = window.setInterval(refreshProgress, 1600)
  refreshProgress()
}

async function refreshProgress() {
  if (!taskId.value) return
  progress.value = await analysisApi.getBatchProgress(taskId.value)
  if (progress.value.status === 'COMPLETED' || progress.value.status === 'FAILED') {
    window.clearInterval(pollTimer.value)
    await analysisApi.getBatchResult(taskId.value)
    await loadResults()
  }
}

async function loadResults() {
  resultsLoading.value = true
  try {
    const page = await analysisApi.listAnalysisResults()
    analysisResults.value = page.records || page.content || []
  } finally {
    resultsLoading.value = false
  }
}

function handleResultSelection(rows: AnalysisResult[]) {
  selectedResultIds.value = rows.map(row => row.id)
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

onMounted(() => {
  loadFiles()
  loadResults()
})

onUnmounted(() => {
  window.clearInterval(pollTimer.value)
})
</script>

<style scoped>
.analysis-page {
  display: grid;
  gap: 16px;
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.7fr);
  gap: 16px;
}

.path-bar,
.analysis-actions,
.export-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.analysis-actions {
  margin: 14px 0 0;
}

.provider-select {
  width: 160px;
}

.progress-card {
  display: grid;
  align-content: start;
  gap: 16px;
}

.progress-detail {
  display: grid;
  gap: 12px;
  margin: 0;
}

.progress-detail div {
  padding: 12px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
}

.progress-detail dt {
  color: var(--text-muted);
  font-size: 12px;
}

.progress-detail dd {
  margin: 4px 0 0;
  word-break: break-all;
}

@media (max-width: 980px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
  .path-bar,
  .analysis-actions,
  .export-actions {
    flex-direction: column;
  }
  .provider-select {
    width: 100%;
  }
}
</style>
