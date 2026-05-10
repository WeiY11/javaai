import type { CitationFormat } from '../types/research.types'
import { post } from '../utils/request'

export async function exportCitations(documentIds: number[], format: CitationFormat): Promise<string> {
  return post(`/citations/export?format=${format}`, documentIds)
}
