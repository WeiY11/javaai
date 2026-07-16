import { get, post, del } from '../utils/request'

export interface KgNode {
  id: number
  name: string
  type: string
  description?: string
  documentId?: number
}

export interface KgEdge {
  id: number
  source: number
  target: number
  relation: string
  documentId?: number
}

export interface KgStats {
  totalEntities: number
  totalRelations: number
  entityTypeDistribution: Record<string, number>
  relationTypeDistribution: Record<string, number>
  hubEntities: Array<{
    id: number
    name: string
    type: string
    degree: number
  }>
}

export interface GraphData {
  nodes: KgNode[]
  edges: KgEdge[]
  stats: KgStats
}

export interface NeighborData {
  entity: { id: number; name: string; type: string; description: string }
  neighbors: Array<{ id: number; name: string; type: string }>
  connections: Array<{
    relationId: number
    relationType: string
    direction: 'incoming' | 'outgoing'
    neighborId: number
    neighborName: string
    neighborType: string
  }>
}

export interface PathStep {
  position: number
  entityId: number
  entityName: string
  entityType: string
  viaRelation?: string
  relationId?: number
}

export function getGraph(kbId: number): Promise<GraphData> {
  return get(`/knowledge-bases/${kbId}/graph`)
}

export function getGraphStats(kbId: number): Promise<KgStats> {
  return get(`/knowledge-bases/${kbId}/graph/stats`)
}

export function getNeighbors(kbId: number, entityId: number): Promise<NeighborData> {
  return get(`/knowledge-bases/${kbId}/graph/entities/${entityId}/neighbors`)
}

export function findPath(kbId: number, source: number, target: number, maxHops = 3): Promise<PathStep[]> {
  return get(`/knowledge-bases/${kbId}/graph/path`, { source, target, maxHops })
}

export function getDocumentPermissions(docId: number): Promise<any[]> {
  return get(`/documents/${docId}/permissions`)
}

export function grantPermission(docId: number, userId: number, permissionType: string): Promise<void> {
  return post(`/documents/${docId}/permissions`, { userId, permissionType })
}

export function revokePermission(docId: number, userId: number, permissionType: string): Promise<void> {
  return del(`/documents/${docId}/permissions/${userId}?permissionType=${permissionType}`)
}
