import type { Conversation, ChatMessage } from '../types/chat.types'
import { get, del } from '../utils/request'
import request from '../utils/request'

export async function listConversations(): Promise<Conversation[]> {
  return get('/conversations')
}

export async function createConversation(knowledgeBaseId?: number, modelProvider = 'deepseek'): Promise<Conversation> {
  const res = await request.post('/api/v1/conversations', null, { params: { knowledgeBaseId, modelProvider } })
  return res.data.data
}

export async function getMessages(conversationId: number): Promise<ChatMessage[]> {
  return get(`/conversations/${conversationId}/messages`)
}

export async function addMessage(conversationId: number, role: string, content: string): Promise<ChatMessage> {
  const res = await request.post(`/api/v1/conversations/${conversationId}/messages`, null, { params: { role, content } })
  return res.data.data
}

export async function deleteConversation(conversationId: number): Promise<void> {
  return del(`/conversations/${conversationId}`)
}
