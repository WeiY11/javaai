<template>
  <div class="analysis-page">
    <section class="workspace-card analysis-command-center">
      <div class="toolbar">
        <div>
          <h2 class="section-title">分析工作流</h2>
          <p class="section-subtitle">先确认目录、文件和模型，再启动批处理。结果完成后直接进入报告导出。</p>
        </div>
        <el-tag :type="analysisReadiness.type" effect="plain">{{ analysisReadiness.label }}</el-tag>
      </div>

      <div class="command-layout">
        <div class="command-summary">
          <div>
            <span>准备分析</span>
            <strong>{{ selectedFileSummary }}</strong>
          </div>
          <div>
            <span>当前目录</span>
            <strong>{{ currentDir || '工作区根目录' }}</strong>
          </div>
          <div>
            <span>模型</span>
            <strong>{{ providerLabel }}</strong>
          </div>
          <div>
            <span>结果导出</span>
            <strong>{{ exportSelectionLabel }}</strong>
          </div>
        </div>

        <div class="analysis-steps">
          <div v-for="item in analysisReadiness.items" :key="item.label" class="analysis-step">
            <span class="status-dot" :class="{ muted: !item.ready }"></span>
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.detail }}</small>
            </div>
          </div>
        </div>

        <div class="command-actions">
          <el-select v-model="provider" class="provider-select" aria-label="分析模型">
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="智谱 GLM-4" value="zhipu" />
            <el-option label="通义千问" value="qianwen" />
            <el-option label="OpenAI" value="openai" />
          </el-select>
          <el-button
            type="primary"
            :disabled="selectedFiles.length === 0 || startingAnalysis"
            :loading="startingAnalysis && failedStartMode !== 'directory'"
            @click="startSelectedBatch"
          >
            启动批处理
          </el-button>
          <el-button
            :disabled="selectableFileCount === 0 || startingAnalysis"
            :loading="startingAnalysis && failedStartMode === 'directory'"
            @click="startDirectoryBatch"
          >
            分析当前目录
          </el-button>
        </div>
      </div>

      <div v-if="analysisStartError" class="analysis-start-status">
        <div>
          <strong>启动分析失败</strong>
          <span>{{ analysisStartError }}</span>
        </div>
        <el-button type="primary" plain :loading="startingAnalysis" @click="retryAnalysisStart">重新启动</el-button>
      </div>
    </section>

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

        <div class="file-browser-meta">
          <span>{{ fileBrowserSummary }}</span>
          <span>{{ selectedFileSummary }}</span>
        </div>

        <div v-if="filesLoadError" class="analysis-file-load-status">
          <div>
            <strong>文件列表加载失败</strong>
            <span>{{ filesLoadError }}</span>
          </div>
          <el-button type="primary" plain @click="loadFiles(currentDir)">重新加载</el-button>
        </div>

        <el-table
          :data="files"
          v-loading="filesLoading"
          @selection-change="handleFileSelection"
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
          <template #empty>
            <div v-if="!filesLoadError" class="analysis-empty-guide">
              <strong>当前目录没有可分析文件</strong>
              <span>输入包含 PDF、Word、Markdown、表格或文本的目录，或者回到上级目录重新选择。</span>
            </div>
          </template>
        </el-table>
      </section>

      <section class="workspace-card progress-card">
        <div class="toolbar compact-toolbar">
          <div>
            <h2 class="section-title">任务进度</h2>
            <p class="section-subtitle">批量分析会异步执行，可在完成后查看结果并导出报告。</p>
          </div>
          <el-tag :type="progressTagType" effect="plain">{{ progressLabel }}</el-tag>
        </div>
        <el-progress :percentage="progressPercent" :status="progressStatus" />
        <dl class="progress-detail">
          <div><dt>任务 ID</dt><dd>{{ taskId || '尚未开始' }}</dd></div>
          <div><dt>完成度</dt><dd>{{ progressCompletedLabel }}</dd></div>
          <div><dt>当前文件</dt><dd>{{ progress?.currentFile || '-' }}</dd></div>
        </dl>
        <div v-if="progressPollError" class="analysis-progress-status">
          <div>
            <strong>进度同步失败</strong>
            <span>{{ progressPollError }}</span>
          </div>
          <el-button type="primary" plain :disabled="!taskId" @click="retryProgressPolling">重新同步进度</el-button>
        </div>
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

      <div v-if="analysisResults.length" class="analysis-result-summary">
        <div>
          <span>可导出结果</span>
          <strong>{{ analysisResults.length }}</strong>
        </div>
        <div>
          <span>模型来源</span>
          <strong>{{ resultProviderCount }}</strong>
        </div>
        <div>
          <span>已选择</span>
          <strong>{{ exportSelectionLabel }}</strong>
        </div>
      </div>

      <div v-if="resultsLoadError" class="analysis-load-status">
        <div>
          <strong>分析结果加载失败</strong>
          <span>{{ resultsLoadError }}</span>
        </div>
        <el-button type="primary" plain @click="loadResults">重新加载</el-button>
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
        <template #empty>
          <div v-if="!resultsLoadError" class="analysis-empty-guide">
            <strong>暂无分析结果</strong>
            <span>完成一次批处理后，结果会在这里汇总，并支持 Markdown 或 PDF 导出。</span>
          </div>
        </template>
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
const filesLoadError = ref('')
const provider = ref('deepseek')
const taskId = ref('')
const progress = ref<BatchProgress | null>(null)
const pollTimer = ref<number | undefined>()
const analysisResults = ref<AnalysisResult[]>([])
const resultsLoading = ref(false)
const resultsLoadError = ref('')
const selectedResultIds = ref<string[]>([])
const startingAnalysis = ref(false)
const analysisStartError = ref('')
const failedStartMode = ref<'selected' | 'directory' | ''>('')
const progressPollError = ref('')

const providerOptions: Record<string, string> = {
  deepseek: 'DeepSeek',
  zhipu: '智谱 GLM-4',
  qianwen: '通义千问',
  openai: 'OpenAI'
}

const selectableFileCount = computed(() => files.value.filter(file => !file.isDir).length)

const selectedFileSummary = computed(() => {
  if (!selectedFiles.value.length) return '尚未选择文件'
  const totalSize = selectedFiles.value.reduce((sum, file) => sum + (file.size || 0), 0)
  return `${selectedFiles.value.length} 个文件 · ${formatSize(totalSize)}`
})

const fileBrowserSummary = computed(() => {
  const directoryCount = files.value.filter(file => file.isDir).length
  return `${selectableFileCount.value} 个可分析文件 · ${directoryCount} 个目录`
})

const providerLabel = computed(() => providerOptions[provider.value] || provider.value)

const exportSelectionLabel = computed(() => {
  if (!selectedResultIds.value.length) return '未选择结果'
  return `${selectedResultIds.value.length} 项待导出`
})

const resultProviderCount = computed(() => {
  return new Set(analysisResults.value.map(result => result.provider).filter(Boolean)).size
})

const analysisReadiness = computed(() => {
  const hasFiles = selectableFileCount.value > 0
  const hasSelection = selectedFiles.value.length > 0
  const items = [
    {
      label: '准备分析',
      detail: hasFiles ? fileBrowserSummary.value : '先定位到包含文档的目录',
      ready: hasFiles
    },
    {
      label: '选择文件',
      detail: hasSelection ? selectedFileSummary.value : '勾选文件或分析整个目录',
      ready: hasSelection
    },
    {
      label: '启动批处理',
      detail: taskId.value ? `当前任务 ${taskId.value}` : `${providerLabel.value} 已选定`,
      ready: hasSelection || !!taskId.value
    }
  ]

  if (taskId.value && progress.value?.status === 'RUNNING') {
    return { label: '分析中', type: 'warning' as const, items }
  }
  if (!hasFiles) {
    return { label: '等待文件', type: 'info' as const, items }
  }
  if (!hasSelection) {
    return { label: '等待选择', type: 'warning' as const, items }
  }
  return { label: '可以启动', type: 'success' as const, items }
})

const progressPercent = computed(() => {
  if (!progress.value?.total) return 0
  return Math.round((progress.value.completed / progress.value.total) * 100)
})

const progressStatus = computed(() => {
  if (progress.value?.status === 'FAILED') return 'exception'
  if (progress.value?.status === 'COMPLETED') return 'success'
  return undefined
})

const progressLabel = computed(() => {
  if (!progress.value) return '未开始'
  if (progress.value.status === 'COMPLETED') return '已完成'
  if (progress.value.status === 'FAILED') return '失败'
  if (progress.value.status === 'RUNNING') return '运行中'
  return progress.value.status || '等待中'
})

const progressTagType = computed(() => {
  if (progress.value?.status === 'COMPLETED') return 'success'
  if (progress.value?.status === 'FAILED') return 'danger'
  if (progress.value?.status === 'RUNNING') return 'warning'
  return 'info'
})

const progressCompletedLabel = computed(() => {
  if (!progress.value?.total) return '0 / 0'
  return `${progress.value.completed} / ${progress.value.total}`
})

const markdownUrl = computed(() => analysisApi.markdownExportUrl(selectedResultIds.value))
const pdfUrl = computed(() => analysisApi.pdfExportUrl(selectedResultIds.value))

async function loadFiles(dir = '') {
  filesLoading.value = true
  filesLoadError.value = ''
  try {
    const res = await analysisApi.listFiles(dir)
    currentDir.value = res.currentDir || dir
    files.value = res.items
  } catch (e: any) {
    files.value = []
    selectedFiles.value = []
    filesLoadError.value = e.response?.data?.message || '加载文件失败'
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

function handleFileSelection(rows: FileItem[]) {
  selectedFiles.value = rows.filter(file => !file.isDir)
}

async function startSelectedBatch() {
  if (startingAnalysis.value) return
  const paths = selectedFiles.value.filter(file => !file.isDir).map(file => file.path)
  if (!paths.length) return
  startingAnalysis.value = true
  analysisStartError.value = ''
  failedStartMode.value = 'selected'
  try {
    const res = await analysisApi.startBatchAnalysis({ paths, provider: provider.value })
    startPolling(res.taskId)
  } catch (e: any) {
    analysisStartError.value = e.response?.data?.message || '启动分析失败'
    ElMessage.error(e.response?.data?.message || '启动分析失败')
  } finally {
    startingAnalysis.value = false
  }
}

async function startDirectoryBatch() {
  if (startingAnalysis.value) return
  startingAnalysis.value = true
  analysisStartError.value = ''
  failedStartMode.value = 'directory'
  try {
    const res = await analysisApi.startDirectoryAnalysis(currentDir.value, provider.value)
    startPolling(res.taskId)
  } catch (e: any) {
    analysisStartError.value = e.response?.data?.message || '启动分析失败'
    ElMessage.error(e.response?.data?.message || '启动分析失败')
  } finally {
    startingAnalysis.value = false
  }
}

async function retryAnalysisStart() {
  if (failedStartMode.value === 'directory') {
    await startDirectoryBatch()
    return
  }
  await startSelectedBatch()
}

function startPolling(id: string) {
  taskId.value = id
  analysisStartError.value = ''
  progressPollError.value = ''
  window.clearInterval(pollTimer.value)
  pollTimer.value = window.setInterval(refreshProgress, 1600)
  refreshProgress()
}

async function refreshProgress() {
  if (!taskId.value) return
  try {
    progressPollError.value = ''
    progress.value = await analysisApi.getBatchProgress(taskId.value)
    if (progress.value.status === 'COMPLETED' || progress.value.status === 'FAILED') {
      window.clearInterval(pollTimer.value)
      await analysisApi.getBatchResult(taskId.value)
      await loadResults()
    }
  } catch (e: any) {
    progressPollError.value = e.response?.data?.message || '同步分析进度失败'
    window.clearInterval(pollTimer.value)
    ElMessage.error(progressPollError.value)
  }
}

async function retryProgressPolling() {
  if (!taskId.value) return
  startPolling(taskId.value)
}

async function loadResults() {
  resultsLoading.value = true
  resultsLoadError.value = ''
  try {
    const page = await analysisApi.listAnalysisResults()
    analysisResults.value = page.records || page.content || []
  } catch (e: any) {
    analysisResults.value = []
    selectedResultIds.value = []
    resultsLoadError.value = e.response?.data?.message || '加载分析结果失败'
    ElMessage.error(e.response?.data?.message || '加载分析结果失败')
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

.analysis-command-center {
  display: grid;
  gap: 16px;
}

.command-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(240px, 0.8fr) minmax(200px, 0.55fr);
  gap: 14px;
  align-items: stretch;
}

.command-summary,
.analysis-steps,
.command-actions,
.analysis-result-summary {
  display: grid;
  gap: 10px;
}

.command-summary {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.command-summary div,
.analysis-result-summary div {
  padding: 12px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  min-width: 0;
}

.command-summary span,
.analysis-result-summary span {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
  margin-bottom: 4px;
}

.command-summary strong,
.analysis-result-summary strong {
  display: block;
  color: var(--text);
  font-size: 15px;
  word-break: break-word;
}

.analysis-step {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}

.analysis-step:last-child {
  border-bottom: 0;
}

.analysis-step strong,
.analysis-step small {
  display: block;
}

.analysis-step small {
  margin-top: 2px;
  color: var(--text-muted);
}

.status-dot.muted {
  background: var(--border-strong);
  box-shadow: none;
}

.command-actions {
  align-content: start;
}

.path-bar,
.export-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.file-browser-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 13px;
  flex-wrap: wrap;
}

.provider-select {
  width: 100%;
}

.progress-card {
  display: grid;
  align-content: start;
  gap: 16px;
}

.compact-toolbar {
  align-items: start;
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

.analysis-start-status,
.analysis-file-load-status,
.analysis-progress-status,
.analysis-load-status {
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

.analysis-start-status strong,
.analysis-start-status span,
.analysis-file-load-status strong,
.analysis-file-load-status span,
.analysis-progress-status strong,
.analysis-progress-status span,
.analysis-load-status strong,
.analysis-load-status span {
  display: block;
}

.analysis-start-status strong,
.analysis-file-load-status strong,
.analysis-progress-status strong,
.analysis-load-status strong {
  color: var(--text);
}

.analysis-start-status span,
.analysis-file-load-status span,
.analysis-progress-status span,
.analysis-load-status span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.analysis-empty-guide {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 28px 16px;
  color: var(--text-muted);
  text-align: center;
}

.analysis-empty-guide strong {
  color: var(--text);
}

.analysis-result-summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0 0 14px;
}

@media (max-width: 980px) {
  .command-layout,
  .analysis-grid {
    grid-template-columns: 1fr;
  }
  .path-bar,
  .export-actions,
  .analysis-start-status,
  .analysis-file-load-status,
  .analysis-progress-status,
  .analysis-load-status {
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .command-summary,
  .analysis-result-summary {
    grid-template-columns: 1fr;
  }
}
</style>
