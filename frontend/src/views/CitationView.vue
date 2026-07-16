<template>
  <div class="citation-page">
    <section class="workspace-card citation-workbench">
      <div class="toolbar">
        <div>
          <h2 class="section-title">引用工作台</h2>
          <p class="section-subtitle">先确定知识库和文档范围，再生成可复制的 BibTeX 或 APA 引用。</p>
        </div>
        <el-tag :type="citationReadiness.type" effect="plain">{{ citationReadiness.label }}</el-tag>
      </div>

      <div class="citation-workbench-layout">
        <div class="citation-scope-card">
          <span>当前范围</span>
          <strong>{{ citationScopeSummary }}</strong>
          <p>{{ selectedDocumentSummary }}</p>
        </div>

        <div class="citation-readiness-list">
          <div v-for="item in citationReadiness.items" :key="item.label" class="citation-readiness-item">
            <span class="status-dot" :class="{ muted: !item.ready }"></span>
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.detail }}</small>
            </div>
          </div>
        </div>

        <div class="citation-workbench-actions">
          <el-radio-group v-model="format">
            <el-radio-button label="bibtex">BibTeX</el-radio-button>
            <el-radio-button label="apa">APA</el-radio-button>
          </el-radio-group>
          <el-button
            type="primary"
            :disabled="selectedDocumentIds.length === 0 || exporting"
            :loading="exporting"
            @click="handleExport"
          >
            生成引用
          </el-button>
        </div>
      </div>

      <div v-if="workspaceLoading || workspaceLoadError" class="citation-workspace-load-status">
        <span>{{ workspaceLoading ? '正在加载引用工作台...' : workspaceLoadError }}</span>
        <el-button
          v-if="workspaceLoadError"
          size="small"
          :loading="workspaceLoading"
          @click="loadWorkspaceData"
        >
          重试
        </el-button>
      </div>
    </section>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">引用导出</h2>
          <p class="section-subtitle">按知识库选择文档，批量生成 {{ exportFormatLabel }} 引用文本。</p>
        </div>
        <el-select v-model="selectedKbId" placeholder="选择知识库" filterable class="kb-select" @change="loadDocuments">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>

      <div class="citation-document-summary">
        <div>
          <span>知识库</span>
          <strong>{{ selectedKnowledgeBase?.name || '未选择' }}</strong>
        </div>
        <div>
          <span>可导出文档</span>
          <strong>{{ documents.length }}</strong>
        </div>
        <div>
          <span>已选择</span>
          <strong>{{ selectedDocumentSummary }}</strong>
        </div>
        <div>
          <span>格式</span>
          <strong>{{ exportFormatLabel }}</strong>
        </div>
      </div>

      <div v-if="documentsLoadError" class="citation-load-status">
        <div>
          <strong>引用文档加载失败</strong>
          <span>{{ documentsLoadError }}</span>
        </div>
        <el-button type="primary" plain @click="loadDocuments">重新加载</el-button>
      </div>

      <el-table :data="documents" v-loading="loading" @selection-change="handleSelection">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="fileName" label="文档" min-width="240" show-overflow-tooltip />
        <el-table-column prop="fileFormat" label="格式" width="100" />
        <el-table-column prop="chunkCount" label="切片" width="100" />
        <el-table-column prop="createdAt" label="上传时间" min-width="180" />
        <template #empty>
          <div v-if="!documentsLoadError" class="citation-empty-guide">
            <strong>{{ emptyDocumentGuide }}</strong>
            <span>需要先完成文档入库，引用导出才有稳定的来源元数据。</span>
          </div>
        </template>
      </el-table>

      <div class="citation-actions">
        <el-radio-group v-model="format">
          <el-radio-button label="bibtex">BibTeX</el-radio-button>
          <el-radio-button label="apa">APA</el-radio-button>
        </el-radio-group>
        <el-button
          type="primary"
          :disabled="selectedDocumentIds.length === 0 || exporting"
          :loading="exporting"
          @click="handleExport"
        >
          导出引用
        </el-button>
      </div>
    </section>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">导出结果</h2>
          <p class="section-subtitle">后端会根据论文元数据生成引用文本。</p>
        </div>
        <el-button :disabled="!exportText || exporting" @click="copyResult">复制</el-button>
      </div>
      <div v-if="exportError" class="citation-export-status">
        <div>
          <strong>引用生成失败</strong>
          <span>{{ exportError }}</span>
        </div>
        <el-button
          type="primary"
          plain
          :disabled="selectedDocumentIds.length === 0 || exporting"
          :loading="exporting"
          @click="handleExport"
        >
          重新生成
        </el-button>
      </div>
      <div class="citation-output-summary">
        <div>
          <span>结果状态</span>
          <strong>{{ citationOutputStateLabel }}</strong>
        </div>
        <div>
          <span>格式</span>
          <strong>{{ exportFormatLabel }}</strong>
        </div>
        <div>
          <span>行数</span>
          <strong>{{ citationLineCount }}</strong>
        </div>
      </div>
      <pre class="code-output">{{ exportText || '请选择文档并导出引用。' }}</pre>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import type { Document } from '../types/document.types'
import type { CitationFormat } from '../types/research.types'
import * as docApi from '../api/document'
import * as citationApi from '../api/citations'

const kbStore = useKnowledgeBaseStore()
const selectedKbId = ref<number>()
const documents = ref<Document[]>([])
const loading = ref(false)
const workspaceLoading = ref(false)
const workspaceLoadError = ref('')
const documentsLoadError = ref('')
const selectedDocumentIds = ref<number[]>([])
const format = ref<CitationFormat>('bibtex')
const exportText = ref('')
const exporting = ref(false)
const exportError = ref('')

const selectedKnowledgeBase = computed(() => {
  return kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value) || null
})

const exportFormatLabel = computed(() => format.value === 'apa' ? 'APA' : 'BibTeX')

const selectedDocumentSummary = computed(() => {
  if (!selectedDocumentIds.value.length) return '尚未选择文档'
  const selected = documents.value.filter(document => selectedDocumentIds.value.includes(document.id))
  const chunks = selected.reduce((sum, document) => sum + (document.chunkCount || 0), 0)
  return `${selectedDocumentIds.value.length} 篇文档 · ${chunks} 个切片`
})

const citationScopeSummary = computed(() => {
  if (!selectedKnowledgeBase.value) return '未选择知识库'
  return `${selectedKnowledgeBase.value.name} · ${documents.value.length} 篇文档`
})

const citationHasOutput = computed(() => exportText.value.trim().length > 0)

const citationLineCount = computed(() => {
  if (!citationHasOutput.value) return 0
  return exportText.value.trim().split(/\r?\n/).filter(Boolean).length
})

const citationOutputStateLabel = computed(() => {
  if (exporting.value) return '生成中'
  if (exportError.value) return '生成失败'
  return citationHasOutput.value ? '可复制结果' : '未生成'
})

const emptyDocumentGuide = computed(() => {
  if (!selectedKnowledgeBase.value) return '先选择知识库'
  return '当前知识库暂无可导出文档'
})

const citationReadiness = computed(() => {
  const hasKnowledgeBase = !!selectedKnowledgeBase.value
  const hasSelection = selectedDocumentIds.value.length > 0
  const hasOutput = citationHasOutput.value
  const items = [
    {
      label: '选择知识库',
      detail: hasKnowledgeBase ? citationScopeSummary.value : '先选择知识库',
      ready: hasKnowledgeBase
    },
    {
      label: '选择文档',
      detail: hasSelection ? selectedDocumentSummary.value : '勾选需要导出的文档',
      ready: hasSelection
    },
    {
      label: '生成引用',
      detail: hasOutput ? `${exportFormatLabel.value} 已生成 ${citationLineCount.value} 行` : `${exportFormatLabel.value} 待生成`,
      ready: hasOutput
    }
  ]

  if (!hasKnowledgeBase) return { label: '待选择知识库', type: 'info' as const, items }
  if (!hasSelection) return { label: '待选择文档', type: 'warning' as const, items }
  if (!hasOutput) return { label: '可生成', type: 'success' as const, items }
  return { label: '可复制', type: 'success' as const, items }
})

async function loadDocuments() {
  if (!selectedKbId.value) {
    documents.value = []
    selectedDocumentIds.value = []
    documentsLoadError.value = ''
    return
  }
  loading.value = true
  documentsLoadError.value = ''
  try {
    const res = await docApi.listDocuments(selectedKbId.value, 1, 100)
    documents.value = res.records
    selectedDocumentIds.value = []
  } catch (e: any) {
    documents.value = []
    selectedDocumentIds.value = []
    documentsLoadError.value = e.response?.data?.message || '加载引用文档失败'
    ElMessage.error(e.response?.data?.message || '加载引用文档失败')
  } finally {
    loading.value = false
  }
}

async function loadWorkspaceData() {
  workspaceLoading.value = true
  workspaceLoadError.value = ''
  try {
    await kbStore.loadKnowledgeBases()
    if (!selectedKbId.value || !kbStore.knowledgeBases.some(kb => kb.id === selectedKbId.value)) {
      selectedKbId.value = kbStore.currentKb?.id ?? kbStore.knowledgeBases[0]?.id
    }
    if (selectedKbId.value) await loadDocuments()
  } catch (e: any) {
    documents.value = []
    selectedDocumentIds.value = []
    workspaceLoadError.value = e.response?.data?.message || '加载引用工作台失败'
    ElMessage.error(workspaceLoadError.value)
  } finally {
    workspaceLoading.value = false
  }
}

function handleSelection(rows: Document[]) {
  selectedDocumentIds.value = rows.map(row => row.id)
}

async function handleExport() {
  exportError.value = ''
  exportText.value = ''
  exporting.value = true
  try {
    exportText.value = await citationApi.exportCitations(selectedDocumentIds.value, format.value)
    ElMessage.success('引用已生成')
  } catch (e: any) {
    exportError.value = e.response?.data?.message || '引用生成失败'
    ElMessage.error(e.response?.data?.message || '引用生成失败')
  } finally {
    exporting.value = false
  }
}

async function copyResult() {
  await navigator.clipboard.writeText(exportText.value)
  ElMessage.success('已复制到剪贴板')
}

onMounted(() => {
  loadWorkspaceData()
})
</script>

<style scoped>
.citation-page {
  display: grid;
  gap: 16px;
}

.citation-workbench {
  display: grid;
  gap: 16px;
}

.citation-workbench-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.85fr) minmax(200px, 0.5fr);
  gap: 14px;
}

.citation-scope-card {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  min-width: 0;
}

.citation-scope-card span,
.citation-document-summary span,
.citation-output-summary span {
  color: var(--text-muted);
  font-size: 12px;
}

.citation-scope-card strong,
.citation-document-summary strong,
.citation-output-summary strong {
  color: var(--text);
  word-break: break-word;
}

.citation-scope-card p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.citation-readiness-list {
  display: grid;
  gap: 8px;
}

.citation-readiness-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}

.citation-readiness-item:last-child {
  border-bottom: 0;
}

.citation-readiness-item strong,
.citation-readiness-item small {
  display: block;
}

.citation-readiness-item small {
  margin-top: 2px;
  color: var(--text-muted);
}

.status-dot.muted {
  background: var(--border-strong);
  box-shadow: none;
}

.citation-workbench-actions {
  display: grid;
  gap: 10px;
  align-content: start;
}

.citation-workspace-load-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  color: var(--text-muted);
  font-size: 13px;
}

.kb-select {
  width: 280px;
}

.citation-document-summary,
.citation-output-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 0 0 14px;
}

.citation-output-summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.citation-document-summary div,
.citation-output-summary div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 12px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
}

.citation-actions {
  display: flex;
  gap: 12px;
  margin-top: 14px;
  align-items: center;
}

.citation-load-status,
.citation-export-status {
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

.citation-load-status strong,
.citation-load-status span,
.citation-export-status strong,
.citation-export-status span {
  display: block;
}

.citation-load-status strong,
.citation-export-status strong {
  color: var(--text);
}

.citation-load-status span,
.citation-export-status span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.citation-empty-guide {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 30px 16px;
  color: var(--text-muted);
  text-align: center;
}

.citation-empty-guide strong {
  color: var(--text);
}

@media (max-width: 760px) {
  .citation-workbench-layout,
  .citation-document-summary,
  .citation-output-summary {
    grid-template-columns: 1fr;
  }
  .toolbar,
  .citation-actions,
  .citation-workspace-load-status,
  .citation-load-status,
  .citation-export-status {
    align-items: stretch;
    flex-direction: column;
  }
  .kb-select {
    width: 100%;
  }
}
</style>
