export interface Document {
  id: number
  knowledgeBaseId: number
  fileName: string
  fileFormat: string
  fileSize: number
  storagePath: string
  ingestionStatus: 'PENDING' | 'EXTRACTING' | 'CLEANING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXING' | 'COMPLETED' | 'FAILED'
  chunkCount: number
  uploaderId: number
  createdAt: string
  updatedAt: string
}

export interface AnalysisReport {
  id: number
  filePath: string
  fileName: string
  provider: string
  content: string
  createdAt: string
}

export type { PageResult } from './common.types'
