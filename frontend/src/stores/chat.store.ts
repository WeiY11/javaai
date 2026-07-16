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

export type StreamStatus = 'idle' | 'retrieving' | 'generating' | 'completed' | 'aborted' | 'failed'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversation = ref<Conversation | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const isStreaming = ref(false)
  const streamStatus = ref<StreamStatus>('idle')
  const selectedKbId = ref<number | undefined>(undefined)

  let currentAbortController: AbortController | null = null

  async function loadConversations() {
    conversations.value = await chatApi.listConversations()
  }

  async function selectConversation(conv: Conversation) {
    const loadedMessages = await chatApi.getMessages(conv.id)
    currentConversation.value = conv
    selectedKbId.value = conv.knowledgeBaseId
    messages.value = loadedMessages
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
      throw new Error('Please select a knowledge base first')
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
    streamStatus.value = 'retrieving'

    currentAbortController = new AbortController()
    const signal = currentAbortController.signal

    try {
      await chatApi.streamMessage(currentConversation.value.id, content, {
        onToken(text: string) {
          streamStatus.value = 'generating'
          assistantMsg.content += text
        },
        onCitations(citations: Citation[]) {
          assistantMsg.citations = citations
        },
        onDone(_messageId: number) {
          if (!signal.aborted) {
            streamStatus.value = 'completed'
          }
        },
        onError(error: string) {
          if (!signal.aborted) {
            streamStatus.value = 'failed'
            assistantMsg.content = `Error: ${error}`
          }
        }
      }, params, signal)
      if (!signal.aborted) {
        await loadConversations()
      }
    } catch (e: any) {
      if (!signal.aborted) {
        streamStatus.value = 'failed'
        assistantMsg.content = `Error: ${e.message || 'Request failed'}`
      }
    } finally {
      isLoading.value = false
      isStreaming.value = false
      if (signal.aborted) {
        streamStatus.value = 'aborted'
      }
      currentAbortController = null
    }
  }

  function stopGenerating() {
    if (currentAbortController) {
      streamStatus.value = 'aborted'
      currentAbortController.abort()
      currentAbortController = null
      isStreaming.value = false
      isLoading.value = false
    }
  }

  async function exportCurrentConversation(format: 'markdown' | 'json' = 'markdown'): Promise<string> {
    if (!currentConversation.value) {
      throw new Error('Please select a conversation first')
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
      streamStatus.value = 'idle'
    }
  }

  return {
    conversations, currentConversation, messages, isLoading, isStreaming, streamStatus, selectedKbId,
    loadConversations, selectConversation, createConversation,
    sendMessage, stopGenerating, exportCurrentConversation, deleteConversation
  }
})
