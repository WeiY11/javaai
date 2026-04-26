import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Conversation, ChatMessage } from '../types/chat.types'
import * as chatApi from '../api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversation = ref<Conversation | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)

  async function loadConversations() {
    conversations.value = await chatApi.listConversations()
  }

  async function selectConversation(conv: Conversation) {
    currentConversation.value = conv
    messages.value = await chatApi.getMessages(conv.id)
  }

  async function createConversation(knowledgeBaseId?: number) {
    const conv = await chatApi.createConversation(knowledgeBaseId)
    conversations.value.unshift(conv)
    currentConversation.value = conv
    messages.value = []
    return conv
  }

  async function sendMessage(content: string) {
    if (!currentConversation.value) return
    messages.value.push({
      id: Date.now(),
      conversationId: currentConversation.value.id,
      role: 'user',
      content,
      createdAt: new Date().toISOString()
    })

    isLoading.value = true
    try {
      const assistant = await chatApi.addMessage(
        currentConversation.value.id, 'assistant', ''
      )
      messages.value.push(assistant)
    } finally {
      isLoading.value = false
    }
  }

  async function deleteConversation(id: number) {
    await chatApi.deleteConversation(id)
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (currentConversation.value?.id === id) {
      currentConversation.value = null
      messages.value = []
    }
  }

  return {
    conversations, currentConversation, messages, isLoading,
    loadConversations, selectConversation, createConversation, sendMessage, deleteConversation
  }
})
