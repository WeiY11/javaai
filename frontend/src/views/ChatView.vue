<template>
  <div class="chat-workspace">
    <aside class="conversation-rail workspace-card">
      <div class="rail-section">
        <p class="eyebrow">当前知识库</p>
        <el-select v-model="chatStore.selectedKbId" placeholder="选择知识库" filterable>
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button type="primary" class="full-button" @click="handleNewConversation">新建对话</el-button>
      </div>

      <el-input v-model="searchQuery" placeholder="搜索会话" clearable />

      <div class="conversation-list">
        <button
          v-for="conv in filteredConversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: chatStore.currentConversation?.id === conv.id }"
          type="button"
          @click="chatStore.selectConversation(conv)"
        >
          <span class="conversation-title">{{ conv.title || '新的证据问答' }}</span>
          <small>{{ conv.modelProvider || 'deepseek' }}</small>
          <el-button text type="danger" size="small" @click.stop="chatStore.deleteConversation(conv.id)">删除</el-button>
        </button>
      </div>

      <el-collapse class="model-collapse">
        <el-collapse-item title="模型参数" name="model">
          <el-form label-position="top" size="small">
            <el-form-item label="模型供应商">
              <el-select v-model="modelProvider">
                <el-option 
                  v-for="model in availableModels" 
                  :key="model.provider" 
                  :label="model.provider + ' (' + model.model + ')'" 
                  :value="model.provider" 
                />
              </el-select>
            </el-form-item>
            
            <template v-if="modelProvider.toLowerCase() === 'deepseek'">
              <el-form-item label="模型版本">
                <el-select v-model="modelName" placeholder="请选择模型版本">
                  <el-option label="deepseek-chat (V3)" value="deepseek-chat" />
                  <el-option label="deepseek-reasoner (R1)" value="deepseek-reasoner" />
                  <el-option label="deepseek-v4-pro" value="deepseek-v4-pro" />
                  <el-option label="deepseek-v4-flash" value="deepseek-v4-flash" />
                </el-select>
              </el-form-item>
              
              <el-form-item label="启用思维链 (Thinking)">
                <el-switch v-model="thinking" />
              </el-form-item>
              
              <el-form-item v-if="thinking && modelName?.includes('pro')" label="推理强度 (Reasoning Effort)">
                <el-select v-model="reasoningEffort" placeholder="选择推理强度">
                  <el-option label="Low" value="low" />
                  <el-option label="Medium" value="medium" />
                  <el-option label="High" value="high" />
                </el-select>
              </el-form-item>
            </template>
            
            <el-form-item label="Temperature">
              <el-slider v-model="temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="Top-P">
              <el-slider v-model="topP" :min="0" :max="1" :step="0.05" show-input />
            </el-form-item>
            <el-form-item label="最大 Token">
              <el-input-number v-model="maxTokens" :min="64" :max="8192" :step="64" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
        <el-collapse-item title="系统已接入接口" name="api">
          <div v-if="availableModels.length === 0" class="text-muted text-sm">正在加载接口信息...</div>
          <div v-for="model in availableModels" :key="model.provider" class="api-card">
            <strong>{{ model.provider }}</strong> ({{ model.model }})
            <div class="api-detail"><span>URL:</span> {{ model.baseUrl || '默认' }}</div>
            <div class="api-detail"><span>KEY:</span> {{ model.apiKey }}</div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </aside>

    <section class="message-workspace workspace-card">
      <div v-if="!chatStore.currentConversation" class="empty-panel">
        <div>
          <h2>选择知识库并创建对话</h2>
          <p>回答会附带引用来源，方便回到文档切片验证证据。</p>
        </div>
      </div>
      <template v-else>
        <div class="message-header">
          <div>
            <p class="eyebrow">当前会话</p>
            <h2>
              <span v-if="!renaming" @dblclick="startRename">{{ chatStore.currentConversation.title || '新的证据问答' }}</span>
              <el-input
                v-else
                v-model="renameTitle"
                size="small"
                @blur="finishRename"
                @keyup.enter="finishRename"
              />
            </h2>
          </div>
          <el-tag effect="plain">{{ selectedKbName }}</el-tag>
        </div>

        <div ref="messagesRef" class="messages">
          <article v-for="msg in chatStore.messages" :key="msg.id" class="message" :class="msg.role">
            <div class="message-role">{{ msg.role === 'user' ? '你' : 'EviMind' }}</div>
            <div class="message-content">
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)"></div>
              <div v-else>{{ msg.content }}</div>
            </div>
          </article>
          <div v-if="chatStore.isLoading" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>

        <div class="input-area">
          <el-input
            v-model="inputText"
            placeholder="输入问题，EviMind 会基于知识库证据回答"
            :disabled="chatStore.isStreaming"
            @keyup.enter="handleSend"
          />
          <el-button v-if="chatStore.isStreaming" type="danger" @click="handleStop">停止生成</el-button>
          <el-button v-else type="primary" :loading="chatStore.isLoading" @click="handleSend">发送</el-button>
        </div>
        <div class="action-bar">
          <el-button text size="small" @click="handleExport('markdown')" :disabled="!chatStore.currentConversation || chatStore.messages.length === 0">
            导出 Markdown
          </el-button>
          <el-button text size="small" @click="handleExport('json')" :disabled="!chatStore.currentConversation || chatStore.messages.length === 0">
            导出 JSON
          </el-button>
        </div>
      </template>
    </section>

    <aside class="context-panel evidence-panel">
      <div class="toolbar">
        <div>
          <p class="eyebrow">Evidence</p>
          <h2 class="section-title">引用与证据</h2>
        </div>
        <el-tag type="success" effect="plain">{{ latestCitations.length }} 条</el-tag>
      </div>

      <div v-if="latestCitations.length === 0" class="evidence-empty">
        <p>当前回答还没有引用。发送问题后，相关文档、切片编号和相关度会显示在这里。</p>
      </div>
      <div v-else class="citation-list">
        <div v-for="(citation, index) in latestCitations" :key="`${citation.documentId}-${index}`" class="citation-card">
          <div>
            <strong>{{ citation.fileName || `文档 #${citation.documentId}` }}</strong>
            <span>切片 {{ citation.chunkIndex }}</span>
          </div>
          <el-tag size="small" type="info">score {{ citation.score?.toFixed?.(3) || citation.score }}</el-tag>
        </div>
      </div>

      <el-divider />

      <div class="quick-notes">
        <h3>研究工作流</h3>
        <p>从回答中的引用进入文档切片，在科研笔记中沉淀批注，再从引用导出页生成 BibTeX/APA。</p>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { useChatStore } from '../stores/chat.store'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import { renameConversation, getModels } from '../api/chat'

const chatStore = useChatStore()
const kbStore = useKnowledgeBaseStore()
const availableModels = ref<any[]>([])
const inputText = ref('')
const modelProvider = ref('deepseek')
const messagesRef = ref<HTMLElement>()
const searchQuery = ref('')
const renaming = ref(false)
const renameTitle = ref('')
const temperature = ref(0.7)
const topP = ref(1)
const maxTokens = ref(2048)
const modelName = ref('deepseek-v4-pro')
const thinking = ref(true)
const reasoningEffort = ref('medium')

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

// MutationObserver：流式输出时自动滚动到底部
let scrollObserver: MutationObserver | null = null

onMounted(async () => {
  // 自动滚动 observer
  scrollObserver = new MutationObserver(() => {
    if (chatStore.isStreaming && messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
  if (messagesRef.value) {
    scrollObserver.observe(messagesRef.value, { childList: true, subtree: true, characterData: true })
  }

  // 加载数据
  chatStore.loadConversations()
  kbStore.loadKnowledgeBases()
  try {
    const res = await getModels()
    availableModels.value = res || []
    if (availableModels.value.length > 0 && !availableModels.value.find(m => m.provider === modelProvider.value)) {
      modelProvider.value = availableModels.value[0].provider
    }
  } catch (e) {
    console.error('Failed to load models', e)
  }
})

onUnmounted(() => {
  scrollObserver?.disconnect()
})

const filteredConversations = computed(() => {
  if (!searchQuery.value) return chatStore.conversations
  const q = searchQuery.value.toLowerCase()
  return chatStore.conversations.filter(c => (c.title || '').toLowerCase().includes(q))
})

const selectedKbName = computed(() => {
  const id = chatStore.currentConversation?.knowledgeBaseId || chatStore.selectedKbId
  return kbStore.knowledgeBases.find(kb => kb.id === id)?.name || '未选择知识库'
})

const latestCitations = computed(() => {
  const assistantMessages = chatStore.messages.filter(msg => msg.role === 'assistant' && msg.citations?.length)
  return assistantMessages.at(-1)?.citations || []
})

function renderMarkdown(content: string): string {
  if (!content) return '<span class="streaming-cursor">生成中...</span>'
  return DOMPurify.sanitize(md.render(content))
}

async function handleNewConversation() {
  if (!chatStore.selectedKbId) {
    ElMessage.warning('请先选择知识库')
    return
  }
  await chatStore.createConversation(chatStore.selectedKbId, modelProvider.value)
}

async function handleSend() {
  if (!inputText.value.trim()) return
  if (!chatStore.currentConversation) {
    ElMessage.warning('请先创建或选择会话')
    return
  }
  const text = inputText.value.trim()
  inputText.value = ''
  try {
    await chatStore.sendMessage(text, {
      temperature: temperature.value,
      topP: topP.value,
      maxTokens: maxTokens.value,
      modelName: modelProvider.value.toLowerCase() === 'deepseek' && modelName.value ? modelName.value : undefined,
      thinking: modelProvider.value.toLowerCase() === 'deepseek' ? thinking.value : undefined,
      reasoningEffort: modelProvider.value.toLowerCase() === 'deepseek' && thinking.value ? reasoningEffort.value : undefined
    })
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败')
  }
  await nextTick()
  messagesRef.value?.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
}

function startRename() {
  renameTitle.value = chatStore.currentConversation?.title || ''
  renaming.value = true
}

async function finishRename() {
  if (!chatStore.currentConversation) return
  const title = renameTitle.value.trim()
  if (title) {
    await renameConversation(chatStore.currentConversation.id, title)
    await chatStore.loadConversations()
    chatStore.currentConversation.title = title
  }
  renaming.value = false
}

/**
 * 中止当前流式生成。
 * 面试点：AbortController 中止 SSE 流的用户控制。
 */
function handleStop() {
  chatStore.stopGenerating()
}

/**
 * 导出当前对话为 Markdown 或 JSON 文件并下载。
 */
async function handleExport(format: 'markdown' | 'json') {
  try {
    const content = await chatStore.exportCurrentConversation(format)
    const ext = format === 'json' ? 'json' : 'md'
    const mime = format === 'json' ? 'application/json' : 'text/markdown'
    const blob = new Blob([content], { type: mime })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${chatStore.currentConversation?.title || 'conversation'}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  }
}
</script>

<style scoped>
.chat-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 320px;
  gap: 16px;
  height: calc(100vh - 112px);
}

.conversation-rail,
.message-workspace,
.evidence-panel {
  min-height: 0;
  overflow: hidden;
}

.conversation-rail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rail-section {
  display: grid;
  gap: 10px;
}

.full-button {
  width: 100%;
}

.conversation-list {
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: 8px;
}

.conversation-item {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  color: var(--text);
  padding: 10px;
  text-align: left;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
  cursor: pointer;
}

.conversation-item.active {
  border-color: var(--primary);
  background: rgba(37, 99, 235, 0.08);
}

.conversation-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}

.conversation-item small {
  color: var(--text-muted);
}

.api-card {
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  margin-bottom: 8px;
  font-size: 13px;
}

.api-card:last-child {
  margin-bottom: 0;
}

.api-detail {
  margin-top: 4px;
  color: var(--text-muted);
  word-break: break-all;
}

.api-detail span {
  color: var(--text);
  font-weight: 600;
  margin-right: 4px;
}

.model-collapse {
  margin-top: auto;
}

.message-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 12px;
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--border-soft);
  padding-bottom: 12px;
}

.message-header h2 {
  margin: 0;
  color: var(--text);
  font-size: 20px;
}

.messages {
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.message {
  max-width: 82%;
}

.message.user {
  margin-left: auto;
}

.message-role {
  margin-bottom: 5px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.message.user .message-role {
  text-align: right;
}

.message-content {
  padding: 13px 15px;
  border-radius: var(--radius);
  background: var(--surface-muted);
  color: var(--text);
  line-height: 1.7;
  word-break: break-word;
}

.message.user .message-content {
  background: var(--primary);
  color: white;
}

.message-content :deep(pre) {
  overflow: auto;
  padding: 12px;
  border-radius: var(--radius-sm);
  background: #0f172a;
  color: #e2e8f0;
}

.input-area {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  border-top: 1px solid var(--border-soft);
  padding-top: 12px;
}

.action-bar {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 4px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-soft);
  animation: bounce 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

.evidence-panel {
  padding: 18px;
  overflow: auto;
}

.evidence-empty,
.quick-notes {
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.7;
}

.citation-list {
  display: grid;
  gap: 10px;
}

.citation-card {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  display: grid;
  gap: 8px;
}

.citation-card strong,
.citation-card span {
  display: block;
}

.citation-card span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

@media (max-width: 1280px) {
  .chat-workspace {
    grid-template-columns: 270px minmax(0, 1fr);
    height: auto;
  }
  .evidence-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 820px) {
  .chat-workspace {
    grid-template-columns: 1fr;
  }
  .message {
    max-width: 100%;
  }
}
</style>
