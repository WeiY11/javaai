import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Conversation, ChatMessage, Citation } from '../types/chat.types'
import * as chatApi from '../api/chat'

export interface ModelParams {
  temperature?: number
  topP?: number
  maxTokens?: number
  modelName?: string
  thinking?: boolean
  reasoningEffort?: string
}

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversation = ref<Conversation | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const isStreaming = ref(false)
  const selectedKbId = ref<number | undefined>(undefined)

  // AbortController 用于中止 SSE 流
  let currentAbortController: AbortController | null = null

  async function loadConversations() {
    conversations.value = await chatApi.listConversations()
  }

  async function selectConversation(conv: Conversation) {
    currentConversation.value = conv
    selectedKbId.value = conv.knowledgeBaseId
    messages.value = await chatApi.getMessages(conv.id)
  }

  async function createConversation(knowledgeBaseId?: number, modelProvider = 'deepseek') {
    const conv = await chatApi.createConversation(knowledgeBaseId, modelProvider)
    conversations.value.unshift(conv)
    currentConversation.value = conv
    selectedKbId.value = knowledgeBaseId
    messages.value = []
    return conv
  }

  async function sendMessage(content: string, params?: ModelParams) {
    if (!currentConversation.value) return

    if (!selectedKbId.value && !currentConversation.value.knowledgeBaseId) {
      throw new Error('请先选择知识库')
    }

    messages.value.push({
      id: Date.now(),
      conversationId: currentConversation.value.id,
      role: 'user',
      content,
      createdAt: new Date().toISOString()
    })

    const assistantMsg: ChatMessage = {
      id: Date.now() + 1,
      conversationId: currentConversation.value.id,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString()
    }
    messages.value.push(assistantMsg)

    isLoading.value = true
    isStreaming.value = true

    // 创建 AbortController 用于中止流
    currentAbortController = new AbortController()
    const signal = currentAbortController.signal

    try {
      await chatApi.streamMessage(currentConversation.value.id, content, {
        onToken(text: string) {
          assistantMsg.content += text
        },
        onCitations(citations: Citation[]) {
          assistantMsg.citations = citations
        },
        onDone(_messageId: number) {
          // message saved by server
        },
        onError(error: string) {
          if (!signal.aborted) {
            assistantMsg.content = `错误: ${error}`
          }
        }
      }, params, signal)
      loadConversations()
    } catch (e: any) {
      if (!signal.aborted) {
        assistantMsg.content = `错误: ${e.message || '请求失败'}`
      }
    } finally {
      isLoading.value = false
      isStreaming.value = false
      currentAbortController = null
    }
  }

  /**
   * 中止当前正在流式生成的回答。
   * 面试点：AbortController 控制 SSE 流的生命周期。
   */
  function stopGenerating() {
    if (currentAbortController) {
      currentAbortController.abort()
      currentAbortController = null
      isStreaming.value = false
      isLoading.value = false
    }
  }

  /**
   * 导出当前对话为 Markdown 或 JSON。
   */
  async function exportCurrentConversation(format: 'markdown' | 'json' = 'markdown'): Promise<string> {
    if (!currentConversation.value) {
      throw new Error('请先选择会话')
    }
    return chatApi.exportConversation(currentConversation.value.id, format)
  }

  async function deleteConversation(id: number) {
    await chatApi.deleteConversation(id)
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (currentConversation.value?.id === id) {
      currentConversation.value = null
      messages.value = []
      selectedKbId.value = undefined
    }
  }

  return {
    conversations, currentConversation, messages, isLoading, isStreaming, selectedKbId,
    loadConversations, selectConversation, createConversation,
    sendMessage, stopGenerating, exportCurrentConversation, deleteConversation
  }
})
