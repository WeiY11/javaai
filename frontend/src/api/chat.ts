import type { Conversation, ChatMessage, Citation } from '../types/chat.types'
import { get, post, put, del, rootGet } from '../utils/request'

export async function listConversations(): Promise<Conversation[]> {
  return get('/conversations')
}

export async function getModels(): Promise<any[]> {
  return rootGet('/models')
}

export async function createConversation(knowledgeBaseId?: number, modelProvider = 'deepseek'): Promise<Conversation> {
  const params = new URLSearchParams()
  if (knowledgeBaseId) params.append('knowledgeBaseId', knowledgeBaseId.toString())
  if (modelProvider) params.append('modelProvider', modelProvider)
  return post(`/conversations?${params.toString()}`)
}

export async function getMessages(conversationId: number): Promise<ChatMessage[]> {
  return get(`/conversations/${conversationId}/messages`)
}

export async function addMessage(conversationId: number, role: string, content: string): Promise<ChatMessage> {
  const params = new URLSearchParams()
  params.append('role', role)
  params.append('content', content)
  return post(`/conversations/${conversationId}/messages?${params.toString()}`)
}

export async function deleteConversation(conversationId: number): Promise<void> {
  return del(`/conversations/${conversationId}`)
}

export async function renameConversation(conversationId: number, title: string): Promise<Conversation> {
  return put(`/conversations/${conversationId}/rename`, { title })
}

export async function exportConversation(conversationId: number, format = 'markdown'): Promise<string> {
  return get(`/conversations/${conversationId}/export`, { format })
}

export interface StreamCallbacks {
  onToken: (text: string) => void
  onCitations: (citations: Citation[]) => void
  onDone: (messageId: number) => void
  onError: (error: string) => void
}

export async function streamMessage(
  conversationId: number,
  content: string,
  callbacks: StreamCallbacks,
  params?: { temperature?: number; topP?: number; maxTokens?: number }
): Promise<void> {
  const token = localStorage.getItem('accessToken')
  const response = await fetch(`/api/v1/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : ''
    },
    body: JSON.stringify({ content, ...params })
  })

  if (!response.ok) {
    const errorText = await response.text()
    callbacks.onError(`HTTP ${response.status}: ${errorText}`)
    return
  }

  const reader = response.body?.getReader()
  if (!reader) {
    callbacks.onError('Stream not supported')
    return
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) continue

      let data = trimmed
      if (trimmed.startsWith('data:')) {
        data = trimmed.substring(5).trim()
      }

      if (!data) continue

      try {
        const event = JSON.parse(data)
        switch (event.type) {
          case 'token':
            callbacks.onToken(event.text || '')
            break
          case 'citations':
            callbacks.onCitations(event.citations || [])
            break
          case 'done':
            callbacks.onDone(event.messageId)
            break
          case 'error':
            callbacks.onError(event.message || 'Unknown error')
            break
        }
      } catch {
        // Skip non-JSON data lines
      }
    }
  }
}
