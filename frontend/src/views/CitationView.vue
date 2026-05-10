<template>
  <div class="citation-page">
    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">引用导出</h2>
          <p class="section-subtitle">按知识库选择文档，一键导出 BibTeX 或 APA。</p>
        </div>
        <el-select v-model="selectedKbId" placeholder="选择知识库" filterable class="kb-select" @change="loadDocuments">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>

      <el-table :data="documents" v-loading="loading" @selection-change="handleSelection">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="fileName" label="文档" min-width="240" show-overflow-tooltip />
        <el-table-column prop="fileFormat" label="格式" width="100" />
        <el-table-column prop="chunkCount" label="切片" width="100" />
        <el-table-column prop="createdAt" label="上传时间" min-width="180" />
      </el-table>

      <div class="citation-actions">
        <el-radio-group v-model="format">
          <el-radio-button label="bibtex">BibTeX</el-radio-button>
          <el-radio-button label="apa">APA</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :disabled="selectedDocumentIds.length === 0" @click="handleExport">导出引用</el-button>
      </div>
    </section>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">导出结果</h2>
          <p class="section-subtitle">后端会根据论文元数据生成引用文本。</p>
        </div>
        <el-button :disabled="!exportText" @click="copyResult">复制</el-button>
      </div>
      <pre class="code-output">{{ exportText || '请选择文档并导出引用。' }}</pre>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
const selectedDocumentIds = ref<number[]>([])
const format = ref<CitationFormat>('bibtex')
const exportText = ref('')

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

function handleSelection(rows: Document[]) {
  selectedDocumentIds.value = rows.map(row => row.id)
}

async function handleExport() {
  exportText.value = await citationApi.exportCitations(selectedDocumentIds.value, format.value)
  ElMessage.success('引用已生成')
}

async function copyResult() {
  await navigator.clipboard.writeText(exportText.value)
  ElMessage.success('已复制到剪贴板')
}

onMounted(async () => {
  await kbStore.loadKnowledgeBases()
  selectedKbId.value = kbStore.knowledgeBases[0]?.id
  await loadDocuments()
})
</script>

<style scoped>
.citation-page {
  display: grid;
  gap: 16px;
}

.kb-select {
  width: 280px;
}

.citation-actions {
  display: flex;
  gap: 12px;
  margin-top: 14px;
  align-items: center;
}

@media (max-width: 760px) {
  .toolbar,
  .citation-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .kb-select {
    width: 100%;
  }
}
</style>
