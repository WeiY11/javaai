import type { ResearchNote } from '../types/research.types'
import { del, get, post, put } from '../utils/request'

export async function listNotes(params: { documentId?: number; chunkId?: number }): Promise<ResearchNote[]> {
  return get('/notes', params)
}

export async function createNote(note: ResearchNote): Promise<ResearchNote> {
  return post('/notes', note)
}

export async function updateNote(id: number, note: ResearchNote): Promise<ResearchNote> {
  return put(`/notes/${id}`, note)
}

export async function deleteNote(id: number): Promise<void> {
  return del(`/notes/${id}`)
}
