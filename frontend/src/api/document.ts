import type { Document, PageResult } from '../types/document.types'
import { get, del, upload, post } from '../utils/request'

export async function uploadDocument(file: File, knowledgeBaseId: number): Promise<Document> {
  return upload('/documents/upload', file, { knowledgeBaseId: String(knowledgeBaseId) })
}

export async function listDocuments(knowledgeBaseId: number, page = 1, size = 10): Promise<PageResult<Document>> {
  return get('/documents', { knowledgeBaseId, page, size })
}

export async function getDocument(id: number): Promise<Document> {
  return get(`/documents/${id}`)
}

export async function deleteDocument(id: number): Promise<void> {
  return del(`/documents/${id}`)
}

export async function retryIngestion(id: number): Promise<void> {
  return post(`/documents/${id}/retry`)
}
