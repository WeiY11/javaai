<template>
  <div class="chat-container">
    <div class="sidebar">
      <h3>会话列表</h3>
      <el-button type="primary" size="small" @click="handleNewConversation" style="width:100%;margin-bottom:12px">
        新建会话
      </el-button>
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
      <div class="messages" ref="messagesRef">
        <div v-for="msg in chatStore.messages" :key="msg.id"
             class="message" :class="msg.role">
          <div class="message-content">
            <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)"></div>
            <div v-else>{{ msg.content }}</div>
          </div>
          <div v-if="msg.citations?.length" class="citations">
            <el-collapse>
              <el-collapse-item title="引用来源">
                <div v-for="(c, i) in msg.citations" :key="i">
                  文档#{{ c.documentId }} 切片{{ c.chunkIndex }} (评分: {{ c.score.toFixed(3) }})
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
      <div class="input-area">
        <el-input v-model="inputText" placeholder="输入消息..." @keyup.enter="handleSend"
                  :disabled="chatStore.isLoading" />
        <el-button type="primary" @click="handleSend" :loading="chatStore.isLoading">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useChatStore } from '../stores/chat.store'
import MarkdownIt from 'markdown-it'

const chatStore = useChatStore()
const inputText = ref('')
const modelProvider = ref('deepseek')
const messagesRef = ref<HTMLElement>()

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

function renderMarkdown(content: string): string {
  return md.render(content)
}

async function handleNewConversation() {
  await chatStore.createConversation()
}

async function handleSend() {
  if (!inputText.value.trim()) return
  const text = inputText.value
  inputText.value = ''
  await chatStore.sendMessage(text)
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
.citations {
  margin-top: 8px;
  font-size: 12px;
}
.input-area {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 8px;
}
</style>
