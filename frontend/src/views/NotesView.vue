<template>
  <div class="notes-page">
    <section class="workspace-card note-workbench">
      <div class="toolbar">
        <div>
          <h2 class="section-title">笔记工作台</h2>
          <p class="section-subtitle">围绕文档或切片沉淀批注、标签和研究判断。</p>
        </div>
        <el-tag :type="noteReadiness.type" effect="plain">{{ noteReadiness.label }}</el-tag>
      </div>

      <div class="note-workbench-layout">
        <div class="note-scope-card">
          <span>当前范围</span>
          <strong>{{ noteScopeLabel }}</strong>
          <p>{{ noteScopeDetail }}</p>
        </div>

        <div class="note-readiness-list">
          <div v-for="item in noteReadiness.items" :key="item.label" class="note-readiness-item">
            <span class="status-dot" :class="{ muted: !item.ready }"></span>
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.detail }}</small>
            </div>
          </div>
        </div>

        <div class="note-workbench-actions">
          <el-button type="primary" :disabled="!canCreate" @click="openCreate">沉淀判断</el-button>
          <el-button :disabled="!canQueryNotes" @click="loadNotes">回到证据</el-button>
        </div>
      </div>

      <div v-if="workspaceLoading || workspaceLoadError" class="note-workspace-load-status">
        <span>{{ workspaceLoading ? '正在加载笔记工作台...' : workspaceLoadError }}</span>
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

    <section class="workspace-card note-controls">
      <div>
        <h2 class="section-title">科研笔记</h2>
        <p class="section-subtitle">定位资料后再新增笔记，避免笔记脱离原文证据。</p>
      </div>
      <div class="note-filter">
        <el-radio-group v-model="queryMode">
          <el-radio-button label="document">按文档</el-radio-button>
          <el-radio-button label="chunk">按切片</el-radio-button>
        </el-radio-group>
        <el-select v-if="queryMode === 'document'" v-model="selectedKbId" placeholder="知识库" @change="loadDocuments">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-select
          v-if="queryMode === 'document'"
          v-model="documentId"
          filterable
          placeholder="选择文档"
          @change="loadNotes"
        >
          <el-option v-for="doc in documents" :key="doc.id" :label="doc.fileName" :value="doc.id" />
        </el-select>
        <el-input-number v-else v-model="chunkId" :min="1" placeholder="切片 ID" @change="loadNotes" />
        <el-button type="primary" :disabled="!canCreate" @click="openCreate">新增笔记</el-button>
      </div>
    </section>

    <section class="note-summary-strip">
      <div>
        <span>范围</span>
        <strong>{{ noteScopeLabel }}</strong>
      </div>
      <div>
        <span>笔记</span>
        <strong>{{ notes.length }}</strong>
      </div>
      <div>
        <span>标签</span>
        <strong>{{ noteTagCount }}</strong>
      </div>
      <div>
        <span>引用摘录</span>
        <strong>{{ quotedNoteCount }}</strong>
      </div>
    </section>

    <section class="notes-grid">
      <div v-if="notesLoadError" class="workspace-card note-load-status">
        <div>
          <strong>笔记加载失败</strong>
          <span>{{ notesLoadError }}</span>
        </div>
        <el-button type="primary" plain :disabled="!canQueryNotes" @click="loadNotes">重新加载</el-button>
      </div>
      <div v-if="noteActionError" class="workspace-card note-action-status">
        <div>
          <strong>笔记操作失败</strong>
          <span>{{ noteActionError }}</span>
        </div>
        <el-button type="primary" plain :disabled="!canQueryNotes" :loading="loading" @click="loadNotes">重新加载笔记</el-button>
      </div>
      <article v-for="note in notes" :key="note.id" class="note-card workspace-card">
        <div class="note-card-header">
          <div class="note-meta">
            <el-tag size="small" :color="note.color || undefined" effect="plain">Note #{{ note.id }}</el-tag>
            <span>{{ note.updatedAt || note.createdAt || '未记录时间' }}</span>
          </div>
          <small>{{ noteTargetLabel(note) }}</small>
        </div>
        <blockquote v-if="note.quote">
          <span>证据摘录</span>
          {{ note.quote }}
        </blockquote>
        <p class="note-content">{{ note.content }}</p>
        <div class="note-tags">
          <el-tag v-for="tag in splitTags(note.tags)" :key="tag" size="small" type="info">{{ tag }}</el-tag>
          <span v-if="splitTags(note.tags).length === 0" class="tag-placeholder">未加标签</span>
        </div>
        <div class="note-actions">
          <el-button size="small" @click="openEdit(note)">编辑</el-button>
          <el-button
            size="small"
            type="danger"
            :disabled="noteSaving || deletingNoteId === note.id"
            :loading="deletingNoteId === note.id"
            @click="removeNote(note)"
          >
            删除
          </el-button>
        </div>
      </article>
      <div v-if="!loading && !notesLoadError && notes.length === 0" class="workspace-card note-empty-guide">
        <div>
          <h3>{{ emptyGuideTitle }}</h3>
          <p>{{ emptyGuideDetail }}</p>
        </div>
        <el-button type="primary" :disabled="!canCreate" @click="openCreate">新增第一条笔记</el-button>
      </div>
    </section>

    <el-dialog v-model="showDialog" :title="editingNote?.id ? '编辑笔记' : '新增笔记'" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item label="引用原文">
          <el-input v-model="form.quote" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="笔记内容">
          <el-input v-model="form.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="form.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="noteSaving" @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="noteSaving" :disabled="noteSaving" @click="submitNote">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import type { Document } from '../types/document.types'
import type { ResearchNote } from '../types/research.types'
import * as docApi from '../api/document'
import * as notesApi from '../api/notes'

const kbStore = useKnowledgeBaseStore()
const queryMode = ref<'document' | 'chunk'>('document')
const selectedKbId = ref<number>()
const documentId = ref<number>()
const chunkId = ref<number>()
const documents = ref<Document[]>([])
const notes = ref<ResearchNote[]>([])
const loading = ref(false)
const workspaceLoading = ref(false)
const workspaceLoadError = ref('')
const notesLoadError = ref('')
const noteSaving = ref(false)
const noteActionError = ref('')
const deletingNoteId = ref<number | null>(null)
const showDialog = ref(false)
const editingNote = ref<ResearchNote | null>(null)

const form = reactive<ResearchNote>({
  documentId: undefined,
  chunkId: undefined,
  quote: '',
  content: '',
  tags: '',
  color: '#ccfbf1'
})

const canCreate = computed(() => queryMode.value === 'document' ? !!documentId.value : !!chunkId.value)
const canQueryNotes = computed(() => queryMode.value === 'document' ? !!documentId.value : !!chunkId.value)

const selectedKnowledgeBase = computed(() => {
  return kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value) || null
})

const selectedDocument = computed(() => {
  return documents.value.find(doc => doc.id === documentId.value) || null
})

const noteScopeLabel = computed(() => {
  if (queryMode.value === 'chunk') {
    return chunkId.value ? `切片 #${chunkId.value}` : '未选择切片'
  }
  if (selectedDocument.value) return selectedDocument.value.fileName
  if (selectedKnowledgeBase.value) return `${selectedKnowledgeBase.value.name} · 未选文档`
  return '未选择知识库'
})

const noteScopeDetail = computed(() => {
  if (queryMode.value === 'chunk') {
    return chunkId.value ? '当前按切片收集证据批注。' : '输入切片 ID 后可查看或新增批注。'
  }
  if (!selectedKnowledgeBase.value) return '先选择知识库和文档，再创建第一条研究笔记。'
  if (!selectedDocument.value) return '当前知识库还没有可选文档，先完成文档入库。'
  return `当前文档已有 ${notes.value.length} 条笔记，可继续补充判断或标签。`
})

const noteTagCount = computed(() => {
  const tags = new Set<string>()
  notes.value.forEach(note => {
    splitTags(note.tags).forEach(tag => tags.add(tag))
  })
  return tags.size
})

const quotedNoteCount = computed(() => notes.value.filter(note => !!note.quote?.trim()).length)

const noteReadiness = computed(() => {
  const hasKnowledgeBase = queryMode.value === 'chunk' || !!selectedKnowledgeBase.value
  const hasScope = canQueryNotes.value
  const hasNotes = notes.value.length > 0
  const items = [
    {
      label: '定位资料',
      detail: hasKnowledgeBase ? noteScopeLabel.value : '先选择知识库和文档',
      ready: hasKnowledgeBase
    },
    {
      label: '沉淀判断',
      detail: hasScope ? '可以新增批注、结论和标签' : '需要先绑定文档或切片',
      ready: hasScope
    },
    {
      label: '回到证据',
      detail: hasNotes ? `${quotedNoteCount.value} 条笔记包含原文摘录` : '暂无可回溯的笔记证据',
      ready: hasNotes
    }
  ]

  if (!hasKnowledgeBase) return { label: '待选择资料', type: 'info' as const, items }
  if (!hasScope) return { label: '待定位范围', type: 'warning' as const, items }
  if (!hasNotes) return { label: '待沉淀', type: 'warning' as const, items }
  return { label: '可复盘', type: 'success' as const, items }
})

const emptyGuideTitle = computed(() => {
  if (!canQueryNotes.value) return '先选择知识库和文档'
  return '暂无笔记'
})

const emptyGuideDetail = computed(() => {
  if (!canQueryNotes.value) return '先选择知识库和文档，或切换到切片模式输入切片 ID。'
  if (queryMode.value === 'chunk') return '当前切片还没有批注，可以先摘录证据，再写研究判断。'
  return '当前文档还没有批注，可以先摘录关键句，再写判断和标签。'
})

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
    documentId.value = undefined
    notes.value = []
    workspaceLoadError.value = e.response?.data?.message || '加载笔记工作台失败'
    ElMessage.error(workspaceLoadError.value)
  } finally {
    workspaceLoading.value = false
  }
}

async function loadDocuments() {
  if (!selectedKbId.value) {
    documents.value = []
    documentId.value = undefined
    notes.value = []
    return
  }
  loading.value = true
  notesLoadError.value = ''
  try {
    const res = await docApi.listDocuments(selectedKbId.value, 1, 100)
    documents.value = res.records
    documentId.value = documents.value[0]?.id
    await loadNotes()
  } catch (e: any) {
    documents.value = []
    documentId.value = undefined
    notes.value = []
    notesLoadError.value = e.response?.data?.message || '加载笔记文档失败'
    ElMessage.error(notesLoadError.value)
  } finally {
    loading.value = false
  }
}

async function loadNotes() {
  loading.value = true
  notesLoadError.value = ''
  try {
    if (queryMode.value === 'document' && documentId.value) {
      notes.value = await notesApi.listNotes({ documentId: documentId.value })
    } else if (queryMode.value === 'chunk' && chunkId.value) {
      notes.value = await notesApi.listNotes({ chunkId: chunkId.value })
    } else {
      notes.value = []
    }
  } catch (e: any) {
    notes.value = []
    notesLoadError.value = e.response?.data?.message || '加载笔记失败'
    ElMessage.error(e.response?.data?.message || '加载笔记失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingNote.value = null
  noteActionError.value = ''
  Object.assign(form, {
    documentId: queryMode.value === 'document' ? documentId.value : undefined,
    chunkId: queryMode.value === 'chunk' ? chunkId.value : undefined,
    quote: '',
    content: '',
    tags: '',
    color: '#ccfbf1'
  })
  showDialog.value = true
}

function openEdit(note: ResearchNote) {
  editingNote.value = note
  noteActionError.value = ''
  Object.assign(form, note)
  showDialog.value = true
}

async function submitNote() {
  if (!form.content.trim()) {
    ElMessage.warning('请填写笔记内容')
    return
  }
  if (noteSaving.value) return
  noteSaving.value = true
  noteActionError.value = ''
  try {
    if (editingNote.value?.id) {
      await notesApi.updateNote(editingNote.value.id, form)
    } else {
      await notesApi.createNote(form)
    }
    showDialog.value = false
    await loadNotes()
    ElMessage.success('笔记已保存')
  } catch (e: any) {
    noteActionError.value = e.response?.data?.message || '笔记操作失败'
    ElMessage.error(e.response?.data?.message || '笔记操作失败')
  } finally {
    noteSaving.value = false
  }
}

async function removeNote(note: ResearchNote) {
  if (!note.id) return
  try {
    await ElMessageBox.confirm('确认删除该笔记？', '删除笔记', { type: 'warning' })
    noteActionError.value = ''
    deletingNoteId.value = note.id
    await notesApi.deleteNote(note.id)
    await loadNotes()
    ElMessage.success('笔记已删除')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    noteActionError.value = e.response?.data?.message || '笔记操作失败'
    ElMessage.error(e.response?.data?.message || '笔记操作失败')
  } finally {
    deletingNoteId.value = null
  }
}

function splitTags(tags?: string): string[] {
  return (tags || '').split(',').map(tag => tag.trim()).filter(Boolean)
}

function noteTargetLabel(note: ResearchNote): string {
  if (note.chunkId) return `切片 #${note.chunkId}`
  if (note.documentId) return `文档 #${note.documentId}`
  return '未绑定来源'
}

onMounted(() => {
  loadWorkspaceData()
})
</script>

<style scoped>
.notes-page {
  display: grid;
  gap: 16px;
}

.note-workbench {
  display: grid;
  gap: 16px;
}

.note-workbench-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.9fr) minmax(180px, 0.45fr);
  gap: 14px;
  align-items: stretch;
}

.note-scope-card {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  min-width: 0;
}

.note-scope-card span,
.note-summary-strip span {
  color: var(--text-muted);
  font-size: 12px;
}

.note-scope-card strong,
.note-summary-strip strong {
  color: var(--text);
  word-break: break-word;
}

.note-scope-card p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.note-readiness-list {
  display: grid;
  gap: 8px;
}

.note-readiness-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}

.note-readiness-item:last-child {
  border-bottom: 0;
}

.note-readiness-item strong,
.note-readiness-item small {
  display: block;
}

.note-readiness-item small {
  margin-top: 2px;
  color: var(--text-muted);
}

.status-dot.muted {
  background: var(--border-strong);
  box-shadow: none;
}

.note-workbench-actions {
  display: grid;
  gap: 10px;
  align-content: start;
}

.note-workspace-load-status {
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

.note-controls {
  display: grid;
  gap: 14px;
}

.note-filter {
  display: grid;
  grid-template-columns: auto minmax(160px, 240px) minmax(220px, 1fr) auto;
  gap: 10px;
}

.note-summary-strip {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) repeat(3, minmax(120px, 0.35fr));
  gap: 12px;
}

.note-summary-strip div {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: var(--radius-sm);
  background: var(--surface);
  border: 1px solid var(--border);
  min-width: 0;
}

.notes-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.note-load-status,
.note-action-status {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}

.note-load-status strong,
.note-load-status span,
.note-action-status strong,
.note-action-status span {
  display: block;
}

.note-load-status strong,
.note-action-status strong {
  color: var(--text);
}

.note-load-status span,
.note-action-status span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.note-card {
  display: grid;
  gap: 12px;
}

.note-card-header {
  display: grid;
  gap: 6px;
}

.note-card-header small {
  color: var(--text-muted);
}

.note-meta,
.note-actions,
.note-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.note-meta {
  justify-content: space-between;
  color: var(--text-muted);
  font-size: 12px;
}

.note-card blockquote {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid var(--evidence);
  background: var(--surface-muted);
  color: var(--text-muted);
  line-height: 1.6;
}

.note-card blockquote span {
  display: block;
  margin-bottom: 4px;
  color: var(--text);
  font-weight: 700;
}

.note-content {
  margin: 0;
  color: var(--text);
  line-height: 1.7;
}

.tag-placeholder {
  color: var(--text-muted);
  font-size: 12px;
}

.note-empty-guide {
  grid-column: 1 / -1;
  display: grid;
  justify-items: center;
  gap: 12px;
  text-align: center;
  padding: 34px 18px;
}

.note-empty-guide h3,
.note-empty-guide p {
  margin: 0;
}

.note-empty-guide p {
  color: var(--text-muted);
}

@media (max-width: 1100px) {
  .note-workbench-layout,
  .note-summary-strip,
  .notes-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .note-workbench-layout,
  .note-summary-strip,
  .note-filter,
  .notes-grid {
    grid-template-columns: 1fr;
  }
  .note-workspace-load-status,
  .note-load-status,
  .note-action-status {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
