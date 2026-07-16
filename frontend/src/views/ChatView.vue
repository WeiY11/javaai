<template>
  <div class="chat-workspace">
    <aside class="conversation-rail workspace-card">
      <div class="rail-section">
        <p class="eyebrow">当前知识库</p>
        <el-select v-model="chatStore.selectedKbId" placeholder="选择知识库" filterable :loading="chatDataLoading">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button
          type="primary"
          class="full-button"
          :loading="creatingConversation"
          :disabled="!chatStore.selectedKbId || creatingConversation || configuredModels.length === 0"
          @click="handleNewConversation"
        >
          新建对话
        </el-button>
      </div>

      <el-input v-model="searchQuery" placeholder="搜索会话" clearable />

      <div v-if="chatDataLoadError" class="chat-data-load-status">
        <div>
          <strong>加载聊天工作区失败</strong>
          <span>{{ chatDataLoadError }}</span>
        </div>
        <el-button text size="small" :loading="chatDataLoading" @click="retryLoadWorkspaceData">
          重新加载
        </el-button>
      </div>

      <div v-if="conversationCreateError" class="conversation-create-status">
        <div>
          <strong>创建对话失败</strong>
          <span>{{ conversationCreateError }}</span>
        </div>
        <el-button
          text
          size="small"
          :disabled="!failedCreateConversation"
          :loading="creatingConversation"
          @click="retryCreateConversation"
        >
          重新创建
        </el-button>
      </div>

      <div v-if="conversationActionError" class="conversation-action-status">
        <div>
          <strong>删除对话失败</strong>
          <span>{{ conversationActionError }}</span>
        </div>
        <el-button
          text
          size="small"
          :disabled="!failedDeleteConversation"
          :loading="deletingConversationId === failedDeleteConversation?.id"
          @click="retryDeleteConversation"
        >
          重新删除
        </el-button>
      </div>

      <div v-if="messageLoadError" class="message-load-status">
        <div>
          <strong>加载消息失败</strong>
          <span>{{ messageLoadError }}</span>
        </div>
        <el-button
          text
          size="small"
          :disabled="!failedMessageConversation"
          :loading="loadingConversationId === failedMessageConversation?.id"
          @click="retrySelectConversation"
        >
          重新加载
        </el-button>
      </div>

      <div class="conversation-list">
        <button
          v-for="conv in filteredConversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: chatStore.currentConversation?.id === conv.id, loading: loadingConversationId === conv.id }"
          type="button"
          :aria-busy="loadingConversationId === conv.id"
          @click="handleSelectConversation(conv)"
        >
          <span class="conversation-title">{{ conv.title || '新的证据问答' }}</span>
          <small>{{ conv.modelProvider || 'deepseek' }}</small>
          <el-button
            text
            type="danger"
            size="small"
            :loading="deletingConversationId === conv.id"
            :disabled="deletingConversationId === conv.id"
            @click.stop="handleDeleteConversation(conv)"
          >
            删除
          </el-button>
        </button>
        <div v-if="filteredConversations.length === 0" class="conversation-empty">
          <strong>暂无会话</strong>
          <span>选定知识库后创建第一轮证据问答。</span>
        </div>
      </div>

      <el-collapse class="model-collapse">
        <el-collapse-item title="模型参数" name="model">
          <el-form label-position="top" size="small">
            <el-form-item label="模型供应商">
              <el-select v-model="modelProvider" :disabled="modelsLoading || !!modelLoadError || configuredModels.length === 0">
                <el-option
                  v-for="model in configuredModels"
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
          <div v-if="modelsLoading" class="model-status-card">
            <strong>正在加载模型接口</strong>
            <span>读取后端可用模型和配置状态。</span>
          </div>
          <div v-else-if="modelLoadError" class="model-status-card status-warning">
            <strong>模型接口加载失败</strong>
            <span>{{ modelLoadError }}</span>
            <el-button text size="small" @click="loadModels">重新加载</el-button>
          </div>
          <div v-else-if="availableModels.length === 0" class="model-status-card">
            <strong>暂无可用模型接口</strong>
            <span>请检查后端模型配置后重新加载。</span>
          </div>
          <div v-for="model in availableModels" :key="model.provider" class="api-card">
            <strong>{{ model.provider }}</strong> ({{ model.model }})
            <div class="api-detail"><span>状态:</span> {{ model.configured ? '已配置' : '未配置' }}</div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </aside>

    <section class="message-workspace workspace-card">
      <div v-if="!chatStore.currentConversation" class="chat-empty-guide">
        <div class="empty-copy">
          <p class="eyebrow">问答起点</p>
          <h2>先把问题放进一条可追溯的证据链</h2>
          <p>选择知识库、确认文档已入库，再创建对话。回答会带引用、切片编号和相关度，方便复核。</p>
        </div>

        <div class="empty-steps">
          <RouterLink class="empty-step" to="/knowledge-bases">
            <span>01</span>
            <strong>选择知识库</strong>
            <small>{{ selectedKbName === '未选择知识库' ? '先创建或选择一个知识库' : selectedKbName }}</small>
          </RouterLink>
          <RouterLink class="empty-step" to="/documents">
            <span>02</span>
            <strong>上传文档</strong>
            <small>让资料完成提取、切片、嵌入和索引。</small>
          </RouterLink>
          <button
            class="empty-step"
            type="button"
            :disabled="!chatStore.selectedKbId || creatingConversation || configuredModels.length === 0"
            :aria-busy="creatingConversation"
            @click="handleNewConversation"
          >
            <span>03</span>
            <strong>创建对话</strong>
            <small>{{ creatingConversation ? '正在创建会话' : chatStore.selectedKbId ? '开始基于证据提问' : '左侧先选知识库' }}</small>
          </button>
        </div>

        <div class="empty-actions">
          <RouterLink class="empty-action secondary-action" to="/knowledge-bases">
            <el-icon><Collection /></el-icon>
            <span>配置知识库</span>
          </RouterLink>
          <RouterLink class="empty-action secondary-action" to="/documents">
            <el-icon><Files /></el-icon>
            <span>上传文档</span>
          </RouterLink>
          <button
            class="empty-action primary-action"
            type="button"
            :disabled="!chatStore.selectedKbId || creatingConversation || configuredModels.length === 0"
            :aria-busy="creatingConversation"
            @click="handleNewConversation"
          >
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ creatingConversation ? '创建中' : '创建对话' }}</span>
          </button>
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
                :disabled="renamingConversation"
                @blur="finishRename"
                @keyup.enter="finishRename"
              />
            </h2>
          </div>
          <div class="message-header-meta">
            <el-tag effect="plain">{{ selectedKbName }}</el-tag>
            <el-tag class="stream-status" :type="streamStatusMeta.type" effect="plain">{{ streamStatusMeta.label }}</el-tag>
          </div>
        </div>

        <div v-if="conversationRenameError" class="conversation-rename-status">
          <div>
            <strong>重命名失败</strong>
            <span>{{ conversationRenameError }}</span>
          </div>
          <el-button
            text
            size="small"
            :disabled="!failedRenameConversation || renamingConversation"
            :loading="renamingConversation"
            @click="retryRenameConversation"
          >
            重新保存
          </el-button>
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
            :disabled="chatStore.isStreaming || chatStore.isLoading"
            @keyup.enter="handleSend"
          />
          <el-button v-if="chatStore.isStreaming" type="danger" @click="handleStop">停止生成</el-button>
          <el-button v-else type="primary" :loading="chatStore.isLoading" @click="handleSend">发送</el-button>
        </div>
        <div v-if="messageSendError" class="message-send-status">
          <div>
            <strong>发送失败</strong>
            <span>{{ messageSendError }}</span>
          </div>
          <el-button
            text
            size="small"
            :disabled="!failedSendPrompt || chatStore.isLoading || chatStore.isStreaming"
            :loading="chatStore.isLoading"
            @click="retrySendMessage"
          >
            重新发送
          </el-button>
        </div>
        <div class="action-bar">
          <el-button
            text
            size="small"
            :loading="exportingConversation && exportingFormat === 'markdown'"
            :disabled="!chatStore.currentConversation || chatStore.messages.length === 0 || exportingConversation"
            @click="handleExport('markdown')"
          >
            导出 Markdown
          </el-button>
          <el-button
            text
            size="small"
            :loading="exportingConversation && exportingFormat === 'json'"
            :disabled="!chatStore.currentConversation || chatStore.messages.length === 0 || exportingConversation"
            @click="handleExport('json')"
          >
            导出 JSON
          </el-button>
        </div>
        <div v-if="conversationExportError" class="conversation-export-status">
          <div>
            <strong>导出失败</strong>
            <span>{{ conversationExportError }}</span>
          </div>
          <el-button
            text
            size="small"
            :disabled="!failedExportConversation || exportingConversation"
            :loading="exportingConversation"
            @click="retryExportConversation"
          >
            重新导出
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

      <div class="citation-summary">
        <div>
          <span>引用</span>
          <strong>{{ latestCitations.length }}</strong>
        </div>
        <div>
          <span>文档</span>
          <strong>{{ uniqueCitationDocuments }}</strong>
        </div>
        <div>
          <span>最高分</span>
          <strong>{{ topCitationScore }}</strong>
        </div>
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
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Collection, Files } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { useChatStore } from '../stores/chat.store'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import { renameConversation, getModels, type AvailableModel } from '../api/chat'
import type { Conversation } from '../types/chat.types'

const chatStore = useChatStore()
const kbStore = useKnowledgeBaseStore()
const availableModels = ref<AvailableModel[]>([])
const configuredModels = computed(() => availableModels.value.filter(model => model.configured))
const modelsLoading = ref(false)
const modelLoadError = ref('')
const chatDataLoading = ref(false)
const chatDataLoadError = ref('')
const inputText = ref('')
const messageSendError = ref('')
const failedSendPrompt = ref('')
const modelProvider = ref('deepseek')
const messagesRef = ref<HTMLElement>()
const searchQuery = ref('')
const renaming = ref(false)
const renameTitle = ref('')
const renamingConversation = ref(false)
const conversationRenameError = ref('')
const failedRenameConversation = ref<{ id: number; title: string } | null>(null)
const exportingConversation = ref(false)
const exportingFormat = ref<'markdown' | 'json' | null>(null)
const conversationExportError = ref('')
const failedExportConversation = ref<{ id: number; format: 'markdown' | 'json' } | null>(null)
const temperature = ref(0.7)
const topP = ref(1)
const maxTokens = ref(2048)
const modelName = ref('deepseek-v4-pro')
const thinking = ref(true)
const reasoningEffort = ref('medium')
const deletingConversationId = ref<number | null>(null)
const conversationActionError = ref('')
const failedDeleteConversation = ref<Conversation | null>(null)
const loadingConversationId = ref<number | null>(null)
const messageLoadError = ref('')
const failedMessageConversation = ref<Conversation | null>(null)
const creatingConversation = ref(false)
const conversationCreateError = ref('')
const failedCreateConversation = ref<{ knowledgeBaseId: number; modelProvider: string } | null>(null)

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

  await loadWorkspaceData()
  await loadModels()
})

onUnmounted(() => {
  scrollObserver?.disconnect()
})

async function loadWorkspaceData() {
  if (chatDataLoading.value) return
  chatDataLoading.value = true
  chatDataLoadError.value = ''
  try {
    await Promise.all([
      chatStore.loadConversations(),
      kbStore.loadKnowledgeBases()
    ])
  } catch (e: any) {
    chatDataLoadError.value = e.response?.data?.message || e.message || '加载聊天工作区失败'
    ElMessage.error(chatDataLoadError.value)
  } finally {
    chatDataLoading.value = false
  }
}

async function retryLoadWorkspaceData() {
  await loadWorkspaceData()
}

async function loadModels() {
  modelsLoading.value = true
  modelLoadError.value = ''
  try {
    const res = await getModels()
    availableModels.value = res || []
    if (configuredModels.value.length > 0 && !configuredModels.value.find(m => m.provider === modelProvider.value)) {
      modelProvider.value = configuredModels.value[0].provider
    }
  } catch (e: any) {
    availableModels.value = []
    modelLoadError.value = e.response?.data?.message || e.message || '无法读取模型接口状态'
  } finally {
    modelsLoading.value = false
  }
}

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

const streamStatusMeta = computed(() => {
  const labels = {
    idle: { label: '待提问', type: 'info' },
    retrieving: { label: '检索证据', type: 'warning' },
    generating: { label: '生成回答', type: 'warning' },
    completed: { label: '回答完成', type: 'success' },
    aborted: { label: '已停止', type: 'info' },
    failed: { label: '生成失败', type: 'danger' }
  } as const
  return labels[chatStore.streamStatus]
})

const uniqueCitationDocuments = computed(() => new Set(latestCitations.value.map(citation => citation.documentId)).size)

const topCitationScore = computed(() => {
  if (latestCitations.value.length === 0) return '-'
  const topScore = Math.max(...latestCitations.value.map(citation => Number(citation.score) || 0))
  return topScore.toFixed(3)
})

function renderMarkdown(content: string): string {
  if (!content) return '<span class="streaming-cursor">生成中...</span>'
  return DOMPurify.sanitize(md.render(content))
}

async function handleNewConversation() {
  if (creatingConversation.value) return
  if (!chatStore.selectedKbId) {
    ElMessage.warning('请先选择知识库')
    return
  }
  const knowledgeBaseId = chatStore.selectedKbId
  creatingConversation.value = true
  conversationCreateError.value = ''
  failedCreateConversation.value = null
  try {
    await chatStore.createConversation(knowledgeBaseId, modelProvider.value)
  } catch (e: any) {
    failedCreateConversation.value = { knowledgeBaseId, modelProvider: modelProvider.value }
    conversationCreateError.value = e.response?.data?.message || e.message || '创建对话失败'
    ElMessage.error(conversationCreateError.value)
  } finally {
    creatingConversation.value = false
  }
}

async function retryCreateConversation() {
  if (!failedCreateConversation.value || creatingConversation.value) return
  const failedIntent = failedCreateConversation.value
  chatStore.selectedKbId = failedIntent.knowledgeBaseId
  modelProvider.value = failedIntent.modelProvider
  await handleNewConversation()
}

async function handleDeleteConversation(conv: Conversation, shouldConfirm = true) {
  if (deletingConversationId.value) return
  try {
    if (shouldConfirm) {
      await ElMessageBox.confirm(
        `删除「${conv.title || '新的证据问答'}」后，当前消息记录将从列表移除。确认删除？`,
        '删除对话',
        {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消'
        }
      )
    }
    conversationActionError.value = ''
    deletingConversationId.value = conv.id
    await chatStore.deleteConversation(conv.id)
    if (failedDeleteConversation.value?.id === conv.id) failedDeleteConversation.value = null
    ElMessage.success('对话已删除')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    failedDeleteConversation.value = conv
    conversationActionError.value = e.response?.data?.message || e.message || '删除对话失败'
    ElMessage.error(e.response?.data?.message || e.message || '删除对话失败')
  } finally {
    deletingConversationId.value = null
  }
}

async function retryDeleteConversation() {
  if (!failedDeleteConversation.value) return
  await handleDeleteConversation(failedDeleteConversation.value, false)
}

async function handleSelectConversation(conv: Conversation) {
  if (loadingConversationId.value) return
  loadingConversationId.value = conv.id
  messageLoadError.value = ''
  try {
    await chatStore.selectConversation(conv)
    if (failedMessageConversation.value?.id === conv.id) failedMessageConversation.value = null
  } catch (e: any) {
    failedMessageConversation.value = conv
    messageLoadError.value = e.response?.data?.message || e.message || '加载消息失败'
    ElMessage.error(messageLoadError.value)
  } finally {
    loadingConversationId.value = null
  }
}

async function retrySelectConversation() {
  if (!failedMessageConversation.value) return
  await handleSelectConversation(failedMessageConversation.value)
}

async function handleSend() {
  if (chatStore.isLoading || chatStore.isStreaming) return
  if (!inputText.value.trim()) return
  if (!chatStore.currentConversation) {
    ElMessage.warning('请先创建或选择会话')
    return
  }
  const text = inputText.value.trim()
  messageSendError.value = ''
  failedSendPrompt.value = ''
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
    if (chatStore.streamStatus === 'failed') {
      failedSendPrompt.value = text
      messageSendError.value = '发送失败，请重试'
      inputText.value = text
      ElMessage.error(messageSendError.value)
      return
    }
  } catch (e: any) {
    failedSendPrompt.value = text
    messageSendError.value = e.response?.data?.message || e.message || '发送失败，请重试'
    inputText.value = text
    ElMessage.error(messageSendError.value)
  }
  await nextTick()
  messagesRef.value?.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
}

async function retrySendMessage() {
  if (!failedSendPrompt.value || chatStore.isLoading || chatStore.isStreaming) return
  const prompt = failedSendPrompt.value
  inputText.value = prompt
  await handleSend()
}

function startRename() {
  if (renamingConversation.value) return
  renameTitle.value = chatStore.currentConversation?.title || ''
  conversationRenameError.value = ''
  renaming.value = true
}

async function finishRename() {
  if (renamingConversation.value) return
  const conversation = chatStore.currentConversation
  if (!conversation) return
  const title = renameTitle.value.trim()
  if (!title || title === (conversation.title || '')) {
    renaming.value = false
    conversationRenameError.value = ''
    return
  }
  renamingConversation.value = true
  conversationRenameError.value = ''
  failedRenameConversation.value = null
  try {
    await renameConversation(conversation.id, title)
    await chatStore.loadConversations()
    conversation.title = title
    renaming.value = false
  } catch (e: any) {
    failedRenameConversation.value = { id: conversation.id, title }
    conversationRenameError.value = e.response?.data?.message || e.message || '重命名失败'
    renaming.value = true
    ElMessage.error(conversationRenameError.value)
  } finally {
    renamingConversation.value = false
  }
}

async function retryRenameConversation() {
  if (!failedRenameConversation.value || renamingConversation.value) return
  const failedIntent = failedRenameConversation.value
  if (!chatStore.currentConversation || chatStore.currentConversation.id !== failedIntent.id) {
    conversationRenameError.value = '请先回到原会话后再重试'
    return
  }
  renameTitle.value = failedIntent.title
  renaming.value = true
  await finishRename()
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
  if (exportingConversation.value) return
  const conversation = chatStore.currentConversation
  if (!conversation) {
    ElMessage.warning('请先选择会话')
    return
  }
  exportingConversation.value = true
  exportingFormat.value = format
  conversationExportError.value = ''
  failedExportConversation.value = null
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
    failedExportConversation.value = { id: conversation.id, format }
    conversationExportError.value = e.response?.data?.message || e.message || '导出失败'
    ElMessage.error(conversationExportError.value)
  } finally {
    exportingConversation.value = false
    exportingFormat.value = null
  }
}

async function retryExportConversation() {
  if (!failedExportConversation.value || exportingConversation.value) return
  const failedIntent = failedExportConversation.value
  if (!chatStore.currentConversation || chatStore.currentConversation.id !== failedIntent.id) {
    conversationExportError.value = '请先回到原会话后再重试'
    return
  }
  await handleExport(failedIntent.format)
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

.conversation-item.loading {
  border-color: var(--el-color-warning-light-5);
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

.conversation-empty {
  padding: 14px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  background: var(--surface-soft);
}

.conversation-empty strong,
.conversation-empty span {
  display: block;
}

.conversation-empty strong {
  color: var(--text);
}

.conversation-empty span {
  margin-top: 4px;
  font-size: 13px;
}

.chat-data-load-status,
.conversation-action-status,
.conversation-create-status,
.conversation-export-status,
.conversation-rename-status,
.message-load-status,
.message-send-status {
  padding: 12px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  display: grid;
  gap: 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.chat-data-load-status strong,
.chat-data-load-status span,
.conversation-action-status strong,
.conversation-action-status span,
.conversation-create-status strong,
.conversation-create-status span,
.conversation-export-status strong,
.conversation-export-status span,
.conversation-rename-status strong,
.conversation-rename-status span,
.message-load-status strong,
.message-load-status span,
.message-send-status strong,
.message-send-status span {
  display: block;
}

.chat-data-load-status strong,
.conversation-action-status strong,
.conversation-create-status strong,
.conversation-export-status strong,
.conversation-rename-status strong,
.message-load-status strong,
.message-send-status strong {
  color: var(--text);
}

.chat-data-load-status span,
.conversation-action-status span,
.conversation-create-status span,
.conversation-export-status span,
.conversation-rename-status span,
.message-load-status span,
.message-send-status span {
  margin-top: 4px;
  line-height: 1.5;
}

.chat-data-load-status .el-button,
.conversation-action-status .el-button,
.conversation-create-status .el-button,
.conversation-export-status .el-button,
.conversation-rename-status .el-button,
.message-load-status .el-button,
.message-send-status .el-button {
  justify-self: start;
  padding-left: 0;
}

.model-status-card {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  color: var(--text-muted);
  font-size: 13px;
}

.model-status-card strong {
  color: var(--text);
}

.model-status-card .el-button {
  justify-self: start;
  padding-left: 0;
}

.model-status-card.status-warning {
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
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

.chat-empty-guide {
  min-height: 100%;
  display: grid;
  align-content: center;
  gap: 22px;
  padding: 24px;
}

.empty-copy {
  max-width: 720px;
}

.empty-copy h2 {
  margin: 0;
  color: var(--text);
  font-size: 30px;
  line-height: 1.25;
}

.empty-copy p:not(.eyebrow) {
  margin: 12px 0 0;
  color: var(--text-muted);
  line-height: 1.8;
}

.empty-steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.empty-step {
  min-height: 130px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  color: var(--text);
  text-align: left;
  text-decoration: none;
}

.empty-step:not(:disabled):hover {
  border-color: var(--primary);
  transform: translateY(-2px);
}

.empty-step:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.empty-step span {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  color: var(--primary);
  background: var(--primary-soft);
  font-weight: 800;
}

.empty-step strong,
.empty-step small {
  display: block;
}

.empty-step strong {
  margin-top: 14px;
}

.empty-step small {
  margin-top: 6px;
  color: var(--text-muted);
  line-height: 1.5;
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.empty-action {
  min-height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  color: var(--text);
  background: var(--surface-muted);
  font-weight: 700;
  text-decoration: none;
}

.primary-action {
  border: 0;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-strong));
}

.empty-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
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

.message-header-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
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

.citation-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 16px 0;
}

.citation-summary div {
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.citation-summary span,
.citation-summary strong {
  display: block;
}

.citation-summary span {
  color: var(--text-muted);
  font-size: 12px;
}

.citation-summary strong {
  margin-top: 4px;
  color: var(--text);
  font-size: 18px;
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
  .empty-steps {
    grid-template-columns: 1fr;
  }
  .message {
    max-width: 100%;
  }
}
</style>
