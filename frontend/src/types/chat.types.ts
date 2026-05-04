export interface ChatMessage {
  id: number
  conversationId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  citations?: Citation[]
  toolCalls?: ToolCall[]
  createdAt: string
}

export interface Citation {
  documentId: number
  fileName?: string
  chunkIndex: number
  score: number
}

export interface ToolCall {
  toolName: string
  input: string
  output: string
}

export interface Conversation {
  id: number
  userId: number
  knowledgeBaseId?: number
  modelProvider: string
  title?: string
  summary?: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface StreamEvent {
  type: 'token' | 'citations' | 'done' | 'error'
  data: string
}
