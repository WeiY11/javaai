<template>
  <div class="chat-container">
    <div class="sidebar">
      <h3>会话列表</h3>
      <div style="margin-bottom:12px">
        <el-select v-model="chatStore.selectedKbId" placeholder="选择知识库" size="small" style="width:100%;margin-bottom:8px">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button type="primary" size="small" @click="handleNewConversation" style="width:100%">
          新建会话
        </el-button>
      </div>
      <div v-for="conv in chatStore.conversations" :key="conv.id"
           class="conv-item" :class="{ active: chatStore.currentConversation?.id === conv.id }"
           @click="chatStore.selectConversation(conv)">
        <span>{{ conv.title || '新会话' }}</span>
        <el-button size="small" type="danger" text @click.stop="chatStore.deleteConversation(conv.id)">×</el-button>
      </div>
      <div style="margin-top:16px">
        <el-select v-model="modelProvider" size="small" placeholder="选择模型">
          <el-option label="DeepSeek" value="deepseek" />
          <el-option label="智谱GLM-4" value="zhipu" />
          <el-option label="千问" value="qianwen" />
          <el-option label="OpenAI" value="openai" />
        </el-select>
      </div>
    </div>
    <div class="chat-main">
      <div v-if="!chatStore.currentConversation" class="empty-state">
        <el-empty description="新建或选择一个会话开始对话" />
      </div>
      <template v-else>
        <div class="messages" ref="messagesRef">
          <div v-for="msg in chatStore.messages" :key="msg.id"
               class="message" :class="msg.role">
            <div class="message-content">
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)"></div>
              <div v-else>{{ msg.content }}</div>
            </div>
            <div v-if="msg.citations?.length" class="citations">
              <el-collapse>
                <el-collapse-item title="引用来源 ({{ msg.citations.length }}条)">
                  <div v-for="(c, i) in msg.citations" :key="i" class="citation-item">
                    <span>{{ c.fileName || '文档#' + c.documentId }}</span>
                    <span>切片{{ c.chunkIndex }}</span>
                    <el-tag size="small">评分: {{ c.score.toFixed(3) }}</el-tag>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
          <div v-if="chatStore.isLoading" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
        <div class="input-area">
          <el-input v-model="inputText" placeholder="输入消息..." @keyup.enter="handleSend"
                    :disabled="chatStore.isLoading" />
          <el-button type="primary" @click="handleSend" :loading="chatStore.isLoading">发送</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useChatStore } from '../stores/chat.store'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'

const chatStore = useChatStore()
const kbStore = useKnowledgeBaseStore()
const inputText = ref('')
const modelProvider = ref('deepseek')
const messagesRef = ref<HTMLElement>()

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

function renderMarkdown(content: string): string {
  if (!content) return '<span class="streaming-cursor">▊</span>'
  return md.render(content)
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
    ElMessage.warning('请先选择知识库并新建会话')
    return
  }
  const text = inputText.value
  inputText.value = ''
  try {
    await chatStore.sendMessage(text)
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败')
  }
  await nextTick()
  scrollToBottom()
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

onMounted(() => {
  chatStore.loadConversations()
  kbStore.loadKnowledgeBases()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  height: calc(100vh - 60px);
}
.sidebar {
  width: 260px;
  padding: 16px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  background: #fafafa;
}
.conv-item {
  padding: 8px 12px;
  margin: 4px 0;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.conv-item:hover { background: #f0f2f5; }
.conv-item.active { background: #e6f0ff; }
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
.message {
  margin-bottom: 16px;
  max-width: 80%;
}
.message.user {
  margin-left: auto;
}
.message.user .message-content {
  background: #409eff;
  color: white;
  border-radius: 12px 12px 0 12px;
}
.message.assistant .message-content {
  background: #f4f4f5;
  border-radius: 12px 12px 12px 0;
}
.message-content {
  padding: 12px 16px;
  word-break: break-word;
}
.message-content :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}
.message-content :deep(code) {
  font-family: 'Fira Code', monospace;
  font-size: 13px;
}
.citations {
  margin-top: 8px;
  font-size: 12px;
}
.citation-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px 0;
}
.input-area {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 8px;
}
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px;
}
.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #bbb;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}
.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
