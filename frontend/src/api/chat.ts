import type { Conversation, ChatMessage, Citation } from '../types/chat.types'
import { get, post, put, del, rootGet, authenticatedFetch } from '../utils/request'

export interface AvailableModel {
  provider: string
  model: string
  configured: boolean
}

export async function listConversations(): Promise<Conversation[]> {
  return get('/conversations')
}

export async function getModels(): Promise<AvailableModel[]> {
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

export interface StreamOptions {
  temperature?: number
  topP?: number
  maxTokens?: number
  modelName?: string
  thinking?: boolean
  reasoningEffort?: string
}

interface ParsedSseEvent {
  id?: string
  event?: string
  data: string
}

export async function streamMessage(
  conversationId: number,
  content: string,
  callbacks: StreamCallbacks,
  params?: StreamOptions,
  signal?: AbortSignal
): Promise<void> {
  const response = await authenticatedFetch(`/api/v1/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, ...params }),
    signal
  })

  if (!response.ok) {
    if (signal?.aborted) return
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
  const seenEventIds = new Set<string>()
  let buffer = ''
  let doneReceived = false

  const handleBlock = (block: string) => {
    const parsed = parseSseBlock(block)
    if (!parsed || !parsed.data) return
    if (parsed.id) {
      if (seenEventIds.has(parsed.id)) return
      seenEventIds.add(parsed.id)
    }

    if (parsed.event === 'error') {
      callbacks.onError(parsed.data)
      return
    }

    try {
      const event = JSON.parse(parsed.data)
      switch (event.type) {
        case 'token':
          callbacks.onToken(event.text || '')
          break
        case 'citations':
          callbacks.onCitations(event.citations || [])
          break
        case 'done':
          doneReceived = true
          callbacks.onDone(event.messageId)
          break
        case 'error':
          callbacks.onError(event.message || 'Unknown error')
          break
      }
    } catch {
      if (parsed.event === 'message') {
        callbacks.onToken(parsed.data)
      }
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        handleBlock(block)
        boundary = buffer.indexOf('\n\n')
      }
    }

    buffer += decoder.decode()
    if (buffer.trim()) {
      handleBlock(buffer)
    }

    if (!doneReceived && !signal?.aborted) {
      callbacks.onError('Stream ended before done event')
    }
  } catch (e: any) {
    if (e.name === 'AbortError' || signal?.aborted) {
      return
    }
    callbacks.onError(e.message || 'Network stream failed')
  } finally {
    reader.releaseLock()
  }
}

function parseSseBlock(block: string): ParsedSseEvent | null {
  const event: ParsedSseEvent = { data: '' }
  const dataLines: string[] = []
  let sawField = false

  for (const rawLine of block.split('\n')) {
    if (!rawLine || rawLine.startsWith(':')) continue
    const separator = rawLine.indexOf(':')
    const field = separator >= 0 ? rawLine.slice(0, separator) : rawLine
    const value = separator >= 0 ? rawLine.slice(separator + 1).replace(/^ /, '') : ''
    switch (field) {
      case 'id':
        sawField = true
        event.id = value
        break
      case 'event':
        sawField = true
        event.event = value
        break
      case 'data':
        sawField = true
        dataLines.push(value)
        break
    }
  }

  event.data = sawField ? dataLines.join('\n') : block.trim()
  return event.data || event.event ? event : null
}
