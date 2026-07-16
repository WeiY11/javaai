export interface KnowledgeBase {
  id: number
  name: string
  description?: string
  groupId: number
  evidenceThreshold: number
  chunkStrategy: 'FIXED_LENGTH' | 'PARAGRAPH' | 'SEMANTIC'
  chunkSize: number
  chunkOverlap: number
  status: string
  creatorId: number
  createdAt: string
  updatedAt: string
}

export interface KbMember {
  id: number
  knowledgeBaseId: number
  userId: number
  role: 'OWNER' | 'MEMBER'
  joinedAt: string
}

export interface KnowledgeBaseSearchRequest {
  query: string
  topK?: number
  conversationHistory?: string
  rerank?: boolean
}

export interface KnowledgeBaseSearchResult {
  chunkId: string
  documentId: number
  knowledgeBaseId: number
  content: string
  chunkIndex: number
  score: number
  source: string
}

export type { PageResult } from './common.types'
