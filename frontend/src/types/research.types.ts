export interface ResearchNote {
  id?: number
  documentId?: number
  chunkId?: number
  content: string
  quote?: string
  tags?: string
  color?: string
  createdAt?: string
  updatedAt?: string
}

export type CitationFormat = 'bibtex' | 'apa'
