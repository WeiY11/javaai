<template>
  <div class="notes-page">
    <section class="workspace-card note-controls">
      <div>
        <h2 class="section-title">科研笔记</h2>
        <p class="section-subtitle">围绕文档或切片沉淀批注、标签和研究判断。</p>
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

    <section class="notes-grid">
      <article v-for="note in notes" :key="note.id" class="note-card workspace-card">
        <div class="note-meta">
          <el-tag size="small" :color="note.color || undefined" effect="plain">Note #{{ note.id }}</el-tag>
          <span>{{ note.updatedAt || note.createdAt || '未记录时间' }}</span>
        </div>
        <blockquote v-if="note.quote">{{ note.quote }}</blockquote>
        <p>{{ note.content }}</p>
        <div class="note-tags">
          <el-tag v-for="tag in splitTags(note.tags)" :key="tag" size="small" type="info">{{ tag }}</el-tag>
        </div>
        <div class="note-actions">
          <el-button size="small" @click="openEdit(note)">编辑</el-button>
          <el-button size="small" type="danger" @click="removeNote(note)">删除</el-button>
        </div>
      </article>
      <div v-if="!loading && notes.length === 0" class="workspace-card empty-panel">
        <div>
          <h3>暂无笔记</h3>
          <p>选择文档或切片后创建第一条研究批注。</p>
        </div>
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
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submitNote">保存</el-button>
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

async function loadDocuments() {
  if (!selectedKbId.value) return
  const res = await docApi.listDocuments(selectedKbId.value, 1, 100)
  documents.value = res.records
  documentId.value = documents.value[0]?.id
  await loadNotes()
}

async function loadNotes() {
  loading.value = true
  try {
    if (queryMode.value === 'document' && documentId.value) {
      notes.value = await notesApi.listNotes({ documentId: documentId.value })
    } else if (queryMode.value === 'chunk' && chunkId.value) {
      notes.value = await notesApi.listNotes({ chunkId: chunkId.value })
    } else {
      notes.value = []
    }
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingNote.value = null
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
  Object.assign(form, note)
  showDialog.value = true
}

async function submitNote() {
  if (!form.content.trim()) {
    ElMessage.warning('请填写笔记内容')
    return
  }
  if (editingNote.value?.id) {
    await notesApi.updateNote(editingNote.value.id, form)
  } else {
    await notesApi.createNote(form)
  }
  showDialog.value = false
  await loadNotes()
  ElMessage.success('笔记已保存')
}

async function removeNote(note: ResearchNote) {
  if (!note.id) return
  await ElMessageBox.confirm('确认删除该笔记？', '删除笔记', { type: 'warning' })
  await notesApi.deleteNote(note.id)
  await loadNotes()
}

function splitTags(tags?: string): string[] {
  return (tags || '').split(',').map(tag => tag.trim()).filter(Boolean)
}

onMounted(async () => {
  await kbStore.loadKnowledgeBases()
  selectedKbId.value = kbStore.knowledgeBases[0]?.id
  await loadDocuments()
})
</script>

<style scoped>
.notes-page {
  display: grid;
  gap: 16px;
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

.notes-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.note-card {
  display: grid;
  gap: 12px;
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
}

.note-card p {
  margin: 0;
  color: var(--text);
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .notes-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .note-filter,
  .notes-grid {
    grid-template-columns: 1fr;
  }
}
</style>
