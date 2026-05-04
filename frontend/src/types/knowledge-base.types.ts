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

export type { PageResult } from './common.types'
