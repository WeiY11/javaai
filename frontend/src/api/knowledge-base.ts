import type { KnowledgeBase, KbMember, PageResult } from '../types/knowledge-base.types'
import { get, post, put, del } from '../utils/request'
import request from '../utils/request'

export async function listKnowledgeBases(groupId: number, page = 1, size = 10): Promise<PageResult<KnowledgeBase>> {
  return get('/knowledge-bases', { groupId, page, size })
}

export async function createKnowledgeBase(data: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
  return post('/knowledge-bases', data)
}

export async function updateKnowledgeBase(id: number, data: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
  return put(`/knowledge-bases/${id}`, data)
}

export async function deleteKnowledgeBase(id: number): Promise<void> {
  return del(`/knowledge-bases/${id}`)
}

export async function getKnowledgeBase(id: number): Promise<KnowledgeBase> {
  return get(`/knowledge-bases/${id}`)
}

export async function addMember(kbId: number, userId: number, role = 'MEMBER'): Promise<KbMember> {
  const res = await request.post(`/api/v1/knowledge-bases/${kbId}/members`, null, { params: { userId, role } })
  return res.data.data
}

export async function removeMember(kbId: number, userId: number): Promise<void> {
  return del(`/knowledge-bases/${kbId}/members/${userId}`)
}

export async function listMembers(kbId: number): Promise<KbMember[]> {
  return get(`/knowledge-bases/${kbId}/members`)
}
