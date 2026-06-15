import { get, post } from '../utils/request'

export interface CitationNode {
  id: string
  label: string
  type: 'document' | 'cited'
  doi?: string
  authors?: string
  year?: number
}

export interface CitationEdge {
  source: string
  target: string
  label: string
}

export interface CitationGraph {
  nodes: CitationNode[]
  edges: CitationEdge[]
}

export interface CitationStats {
  totalCitationLinks: number
  documentsWithCitations: number
  mostCitedDois: Array<{
    doi: string
    citationCount: number
    title?: string
    authors?: string
    year?: number
  }>
  yearDistribution: Record<string, number>
  citationsWithDoi: number
  citationsWithoutDoi: number
}

export interface CoCitedDocument {
  documentId: number
  fileName: string
  sharedCitations: number
  sharedReferences: Array<{ doi?: string; title?: string }>
}

export interface CitationLinkRecord {
  id: number
  documentId: number
  knowledgeBaseId: number
  citedDoi?: string
  citedTitle?: string
  citedAuthors?: string
  citedYear?: number
  rawReference: string
}

export function getCitationGraph(kbId: number): Promise<CitationGraph> {
  return get(`/academic/knowledge-bases/${kbId}/citation-graph`)
}

export function getCitationStats(kbId: number): Promise<CitationStats> {
  return get(`/academic/knowledge-bases/${kbId}/citation-stats`)
}

export function getDocumentCitations(docId: number): Promise<CitationLinkRecord[]> {
  return get(`/academic/documents/${docId}/citations`)
}

export function getCoCitedDocuments(docId: number): Promise<CoCitedDocument[]> {
  return get(`/academic/documents/${docId}/co-cited`)
}

export function generateLiteratureReview(kbId: number, topic: string): Promise<string> {
  return post(`/academic/knowledge-bases/${kbId}/literature-review`, { topic })
}
