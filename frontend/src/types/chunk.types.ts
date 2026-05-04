export interface DocumentChunk {
  id: number
  documentId: number
  knowledgeBaseId: number
  content: string
  chunkIndex: number
  vectorId?: string
  createdAt: string
}
