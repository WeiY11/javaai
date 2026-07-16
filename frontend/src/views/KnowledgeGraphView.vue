<template>
  <div class="kg-page">
    <div class="metric-grid">
      <div class="metric-card"><span>实体数</span><strong>{{ stats?.totalEntities ?? 0 }}</strong></div>
      <div class="metric-card"><span>关系数</span><strong>{{ stats?.totalRelations ?? 0 }}</strong></div>
      <div class="metric-card"><span>实体类型</span><strong>{{ Object.keys(stats?.entityTypeDistribution || {}).length }}</strong></div>
      <div class="metric-card"><span>当前知识库</span><strong>{{ selectedKbName }}</strong></div>
    </div>

    <section class="workspace-card kg-workbench">
      <div class="toolbar">
        <div>
          <h2 class="section-title">图谱工作台</h2>
          <p class="section-subtitle">先确认知识库、实体抽取和关系密度，再进入节点详情或路径探索。</p>
        </div>
        <el-tag :type="graphReadiness.type" effect="plain">{{ graphReadiness.label }}</el-tag>
      </div>

      <div class="kg-workbench-layout">
        <div class="kg-scope-card">
          <span>图谱范围</span>
          <strong>{{ graphScopeSummary }}</strong>
          <p>{{ graphDensityLabel }}</p>
        </div>

        <div class="kg-readiness-list">
          <div v-for="item in graphReadiness.items" :key="item.label" class="kg-readiness-item">
            <span class="status-dot" :class="{ muted: !item.ready }"></span>
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.detail }}</small>
            </div>
          </div>
        </div>

        <div class="kg-workbench-actions">
          <el-select v-model="selectedKbId" placeholder="选择知识库" filterable class="kb-select" @change="loadGraph">
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button :disabled="!selectedKbId" @click="loadGraph">刷新图谱</el-button>
        </div>
      </div>

      <div v-if="workspaceLoading || workspaceLoadError" class="graph-load-status">
        <span>{{ workspaceLoading ? '正在加载图谱工作台...' : workspaceLoadError }}</span>
        <el-button
          v-if="workspaceLoadError"
          size="small"
          :loading="workspaceLoading"
          @click="loadWorkspaceData"
        >
          重试
        </el-button>
      </div>
    </section>

    <section class="kg-summary-strip">
      <div>
        <span>关系密度</span>
        <strong>{{ graphDensityLabel }}</strong>
      </div>
      <div>
        <span>核心实体</span>
        <strong>{{ topHubSummary }}</strong>
      </div>
      <div>
        <span>路径搜索</span>
        <strong>{{ pathSearchSummary }}</strong>
      </div>
      <div>
        <span>结果</span>
        <strong>{{ pathResultLabel }}</strong>
      </div>
    </section>

    <div class="kg-main">
      <section class="workspace-card graph-section">
        <div class="toolbar">
          <div>
            <h2 class="section-title">知识图谱</h2>
            <p class="section-subtitle">从文档中自动抽取的实体与关系网络，点击节点查看详情。</p>
          </div>
          <el-select v-model="selectedKbId" placeholder="选择知识库" filterable class="kb-select" @change="loadGraph">
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </div>

        <div v-if="graphActionError" class="kg-graph-action-status">
          <div>
            <strong>知识图谱加载失败</strong>
            <span>{{ graphActionError }}</span>
          </div>
          <el-button type="primary" plain :loading="graphLoading" :disabled="!selectedKbId" @click="loadGraph">
            重新加载图谱
          </el-button>
        </div>

        <div v-loading="graphLoading" class="graph-container">
          <div v-if="!graphLoading && (!graphData || graphData.nodes.length === 0)" class="graph-empty kg-empty-guide">
            <strong>{{ emptyGraphGuide }}</strong>
            <p>上传文档并等待入库完成后，实体抽取和关系识别会形成可探索图谱。</p>
          </div>
          <div ref="chartRef" class="graph-chart" :style="{ display: graphData?.nodes.length ? 'block' : 'none' }" />
        </div>

        <div v-if="selectedEntity" class="entity-detail">
          <div class="entity-header">
            <el-tag :type="entityTagType(selectedEntity.type)" size="small">{{ selectedEntity.type || 'unknown' }}</el-tag>
            <h3>{{ selectedEntity.name }}</h3>
            <el-button size="small" text @click="selectedEntity = null">关闭</el-button>
          </div>
          <p v-if="selectedEntity.description" class="entity-desc">{{ selectedEntity.description }}</p>
          <div v-if="entityNeighbors.length > 0" class="neighbor-list">
            <h4>关联实体</h4>
            <div v-for="conn in entityConnections" :key="conn.relationId" class="neighbor-item">
              <el-tag size="small" :type="conn.direction === 'outgoing' ? '' : 'warning'">
                {{ conn.direction === 'outgoing' ? '→' : '←' }} {{ conn.relationType }}
              </el-tag>
              <span class="neighbor-name">{{ conn.neighborName }}</span>
              <el-tag size="small" type="info">{{ conn.neighborType }}</el-tag>
            </div>
          </div>
        </div>
      </section>

      <aside class="kg-sidebar">
        <section class="workspace-card path-section">
          <h3 class="sub-heading">关系探索</h3>
          <p class="section-subtitle">查找两个实体之间的最短路径</p>
          <div class="path-summary">{{ pathSearchSummary }} · {{ pathResultLabel }}</div>
          <div class="path-form">
            <el-select v-model="pathSource" filterable placeholder="起始实体" size="small">
              <el-option v-for="n in graphData?.nodes" :key="n.id" :label="n.name" :value="n.id" />
            </el-select>
            <el-select v-model="pathTarget" filterable placeholder="目标实体" size="small">
              <el-option v-for="n in graphData?.nodes" :key="n.id" :label="n.name" :value="n.id" />
            </el-select>
            <el-button size="small" type="primary" :disabled="!pathSource || !pathTarget" @click="handlePathSearch">搜索</el-button>
          </div>
          <div v-if="pathSearchError" class="path-action-status">
            <div>
              <strong>路径搜索失败</strong>
              <span>{{ pathSearchError }}</span>
            </div>
            <el-button size="small" type="primary" plain :disabled="!pathSource || !pathTarget" @click="handlePathSearch">
              重新搜索
            </el-button>
          </div>
          <div v-if="pathResult.length > 0" class="path-result">
            <div v-for="(step, idx) in pathResult" :key="idx" class="path-step">
              <el-tag size="small" :type="entityTagType(step.entityType)">{{ step.entityType }}</el-tag>
              <strong>{{ step.entityName }}</strong>
              <span v-if="step.viaRelation" class="path-relation">—{{ step.viaRelation }}→</span>
            </div>
          </div>
          <div v-else-if="pathSearched" class="path-empty">未找到路径（{{ pathMaxHops }} 跳内）</div>
        </section>

        <section class="workspace-card stats-section">
          <h3 class="sub-heading">实体类型分布</h3>
          <div v-if="stats?.entityTypeDistribution" class="type-bars">
            <div v-for="(count, type) in stats.entityTypeDistribution" :key="String(type)" class="type-bar-row">
              <span class="type-label">{{ type }}</span>
              <div class="type-bar-track">
                <div class="type-bar-fill" :style="{ width: typeBarWidth(count) + '%' }" />
              </div>
              <span class="type-count">{{ count }}</span>
            </div>
          </div>

          <h3 class="sub-heading" style="margin-top: 16px">Hub 实体</h3>
          <div v-if="stats?.hubEntities?.length" class="hub-list">
            <div v-for="hub in stats.hubEntities" :key="hub.id" class="hub-item">
              <span class="hub-name">{{ hub.name }}</span>
              <el-tag size="small" type="info">度 {{ hub.degree }}</el-tag>
            </div>
          </div>
          <p v-else class="section-subtitle">暂无数据</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import * as kgApi from '../api/knowledge-graph'
import type { GraphData, KgStats, PathStep } from '../api/knowledge-graph'
import { initGraphChart, type ECharts } from '../utils/graph-chart'

const kbStore = useKnowledgeBaseStore()
const selectedKbId = ref<number>()
const graphData = ref<GraphData>()
const stats = ref<KgStats>()
const workspaceLoading = ref(false)
const workspaceLoadError = ref('')
const graphActionError = ref('')
const graphLoading = ref(false)
const chartRef = ref<HTMLElement>()
let chartInstance: ECharts | null = null

const selectedEntity = ref<any>(null)
const entityNeighbors = ref<any[]>([])
const entityConnections = ref<any[]>([])

const pathSource = ref<number>()
const pathTarget = ref<number>()
const pathResult = ref<PathStep[]>([])
const pathSearched = ref(false)
const pathSearchError = ref('')
const pathMaxHops = 3

const selectedKbName = computed(() =>
  kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value)?.name || '未选择'
)

const selectedKnowledgeBase = computed(() =>
  kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value) || null
)

const graphNodeCount = computed(() => graphData.value?.nodes.length || stats.value?.totalEntities || 0)
const graphEdgeCount = computed(() => graphData.value?.edges.length || stats.value?.totalRelations || 0)
const graphTypeCount = computed(() => Object.keys(stats.value?.entityTypeDistribution || {}).length)

const graphScopeSummary = computed(() => {
  if (!selectedKnowledgeBase.value) return '未选择知识库'
  return `${selectedKnowledgeBase.value.name} · ${graphNodeCount.value} 个实体`
})

const graphDensityLabel = computed(() => {
  if (!graphNodeCount.value) return '暂无关系密度'
  const density = graphEdgeCount.value / graphNodeCount.value
  if (density >= 2) return `高密度 · ${density.toFixed(1)} 条关系/实体`
  if (density >= 1) return `中密度 · ${density.toFixed(1)} 条关系/实体`
  return `低密度 · ${density.toFixed(1)} 条关系/实体`
})

const topHubSummary = computed(() => {
  const topHub = stats.value?.hubEntities?.[0]
  if (!topHub) return '暂无 Hub'
  return `${topHub.name} · 度 ${topHub.degree}`
})

const pathSearchSummary = computed(() => {
  if (!pathSource.value || !pathTarget.value) return '未选择起止实体'
  const sourceName = graphData.value?.nodes.find(node => node.id === pathSource.value)?.name || `#${pathSource.value}`
  const targetName = graphData.value?.nodes.find(node => node.id === pathTarget.value)?.name || `#${pathTarget.value}`
  return `${sourceName} → ${targetName}`
})

const pathResultLabel = computed(() => {
  if (!pathSearched.value) return '未搜索'
  if (pathResult.value.length === 0) return `${pathMaxHops} 跳内无路径`
  return `${pathResult.value.length} 个节点路径`
})

const emptyGraphGuide = computed(() => {
  if (!selectedKnowledgeBase.value) return '先选择知识库'
  return '当前知识库暂无可用图谱'
})

const graphReadiness = computed(() => {
  const hasKnowledgeBase = !!selectedKnowledgeBase.value
  const hasGraph = graphNodeCount.value > 0
  const hasRelations = graphEdgeCount.value > 0
  const items = [
    {
      label: '选择知识库',
      detail: hasKnowledgeBase ? graphScopeSummary.value : '先选择知识库',
      ready: hasKnowledgeBase
    },
    {
      label: '抽取实体',
      detail: hasGraph ? `${graphNodeCount.value} 个实体，${graphTypeCount.value} 类` : '等待文档入库后抽取实体',
      ready: hasGraph
    },
    {
      label: '探索关系',
      detail: hasRelations ? `${graphEdgeCount.value} 条关系，${topHubSummary.value}` : '暂无可探索关系',
      ready: hasRelations
    }
  ]

  if (!hasKnowledgeBase) return { label: '待选择知识库', type: 'info' as const, items }
  if (!hasGraph) return { label: '待抽取实体', type: 'warning' as const, items }
  if (!hasRelations) return { label: '实体已就绪', type: 'warning' as const, items }
  return { label: '可探索', type: 'success' as const, items }
})

function entityTagType(type: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    '人物': 'danger', '组织': 'warning', '技术': 'success', '概念': '', '地点': 'info',
    'person': 'danger', 'organization': 'warning', 'technology': 'success', 'concept': '', 'location': 'info'
  }
  return map[type] || 'info'
}

function typeBarWidth(count: number): number {
  if (!stats.value?.entityTypeDistribution) return 0
  const max = Math.max(...Object.values(stats.value.entityTypeDistribution))
  return max > 0 ? (count / max) * 100 : 0
}

async function loadGraph() {
  if (!selectedKbId.value) return
  graphLoading.value = true
  graphActionError.value = ''
  selectedEntity.value = null
  pathResult.value = []
  pathSearched.value = false
  pathSearchError.value = ''
  try {
    const data = await kgApi.getGraph(selectedKbId.value)
    graphData.value = data
    stats.value = data.stats
    await nextTick()
    renderChart(data)
  } catch (e: any) {
    graphActionError.value = e.response?.data?.message || '加载知识图谱失败'
    ElMessage.error(graphActionError.value)
  } finally {
    graphLoading.value = false
  }
}

async function loadWorkspaceData() {
  workspaceLoading.value = true
  workspaceLoadError.value = ''
  try {
    await kbStore.loadKnowledgeBases()
    if (!selectedKbId.value || !kbStore.knowledgeBases.some(kb => kb.id === selectedKbId.value)) {
      selectedKbId.value = kbStore.currentKb?.id ?? kbStore.knowledgeBases[0]?.id
    }
    if (selectedKbId.value) await loadGraph()
  } catch (e: any) {
    workspaceLoadError.value = e.response?.data?.message || '加载图谱工作台失败'
  } finally {
    workspaceLoading.value = false
  }
}

function renderChart(data: GraphData) {
  if (!chartRef.value || !data.nodes.length) return
  if (!chartInstance) chartInstance = initGraphChart(chartRef.value)

  // Color by entity type
  const typeColors: Record<string, string> = {
    '人物': '#F56C6C', '组织': '#E6A23C', '技术': '#67C23A', '概念': '#409EFF',
    '地点': '#909399', '事件': '#B37FEB', 'person': '#F56C6C', 'organization': '#E6A23C',
    'technology': '#67C23A', 'concept': '#409EFF', 'location': '#909399'
  }

  // Count degree
  const degree: Record<number, number> = {}
  data.edges.forEach(e => {
    degree[e.source] = (degree[e.source] || 0) + 1
    degree[e.target] = (degree[e.target] || 0) + 1
  })

  const nodes = data.nodes.map(n => ({
    id: String(n.id),
    name: n.name,
    symbolSize: Math.max(18, Math.min(40, 12 + (degree[n.id] || 0) * 5)),
    category: n.type || 'unknown',
    itemStyle: { color: typeColors[n.type] || '#409EFF' },
    value: n.description || '',
    label: { show: true, fontSize: 11 }
  }))

  const links = data.edges.map(e => ({
    source: String(e.source),
    target: String(e.target),
    value: e.relation,
    label: { show: data.edges.length < 60, formatter: e.relation, fontSize: 9, color: '#999' },
    lineStyle: { curveness: 0.15, opacity: 0.5 }
  }))

  const categories = [...new Set(data.nodes.map(n => n.type || 'unknown'))].map(t => ({ name: t }))

  chartInstance.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        if (p.dataType === 'node') {
          const node = data.nodes.find(n => n.id === Number(p.data.id))
          let html = `<strong>${node?.name}</strong><br/>类型: ${node?.type || 'unknown'}`
          if (node?.description) html += `<br/>${node.description}`
          return html
        }
        return `${p.data.source} —[${p.data.value}]→ ${p.data.target}`
      }
    },
    legend: { data: categories.map(c => c.name), top: 8, right: 16 },
    animationDuration: 600,
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      categories,
      data: nodes,
      links,
      force: { repulsion: 300, edgeLength: [60, 180], gravity: 0.08 },
      emphasis: { focus: 'adjacency', lineStyle: { width: 3 } },
      label: { position: 'right', formatter: '{b}' },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 5
    }]
  }, true)

  chartInstance.off('click')
  chartInstance.on('click', (params: any) => {
    if (params.dataType === 'node') {
      handleNodeClick(Number(params.data.id))
    }
  })
}

async function handleNodeClick(entityId: number) {
  const knowledgeBaseId = selectedKbId.value
  if (!knowledgeBaseId) return
  try {
    const data = await kgApi.getNeighbors(knowledgeBaseId, entityId)
    selectedEntity.value = data.entity
    entityNeighbors.value = data.neighbors
    entityConnections.value = data.connections
  } catch {
    ElMessage.error('获取邻居信息失败')
  }
}

async function handlePathSearch() {
  if (!selectedKbId.value || !pathSource.value || !pathTarget.value) return
  pathSearchError.value = ''
  pathResult.value = []
  try {
    pathResult.value = await kgApi.findPath(selectedKbId.value, pathSource.value, pathTarget.value, pathMaxHops)
    pathSearched.value = true
  } catch (e: any) {
    pathSearched.value = false
    pathSearchError.value = e.response?.data?.message || '路径搜索失败'
    ElMessage.error(pathSearchError.value)
  }
}

function handleResize() { chartInstance?.resize() }

onMounted(() => {
  loadWorkspaceData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.kg-page { display: grid; gap: 16px; }

.kb-select { width: 260px; }

.kg-workbench { display: grid; gap: 16px; }

.kg-workbench-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.9fr) minmax(220px, 0.55fr);
  gap: 14px;
}

.kg-scope-card {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  min-width: 0;
}

.kg-scope-card span,
.kg-summary-strip span {
  color: var(--text-muted);
  font-size: 12px;
}

.kg-scope-card strong,
.kg-summary-strip strong {
  color: var(--text);
  word-break: break-word;
}

.kg-scope-card p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.kg-readiness-list {
  display: grid;
  gap: 8px;
}

.kg-readiness-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}

.kg-readiness-item:last-child { border-bottom: 0; }

.kg-readiness-item strong,
.kg-readiness-item small {
  display: block;
}

.kg-readiness-item small {
  margin-top: 2px;
  color: var(--text-muted);
}

.status-dot.muted {
  background: var(--border-strong);
  box-shadow: none;
}

.kg-workbench-actions {
  display: grid;
  gap: 10px;
  align-content: start;
}

.kg-workbench-actions .kb-select {
  width: 100%;
}

.graph-load-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  color: var(--text-muted);
  font-size: 13px;
}

.kg-graph-action-status,
.path-action-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 12px;
  padding: 12px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
  color: var(--text-muted);
  font-size: 13px;
}

.kg-graph-action-status strong,
.kg-graph-action-status span,
.path-action-status strong,
.path-action-status span {
  display: block;
}

.kg-graph-action-status strong,
.path-action-status strong {
  color: var(--text);
}

.kg-graph-action-status span,
.path-action-status span {
  margin-top: 4px;
  line-height: 1.5;
}

.kg-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kg-summary-strip div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.kg-main { display: grid; grid-template-columns: 1fr 340px; gap: 16px; }

.graph-container {
  min-height: 480px;
  position: relative;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface-soft);
}

.graph-empty {
  display: flex; align-items: center; justify-content: center;
  height: 480px; color: var(--text-muted); text-align: center; padding: 40px;
}

.kg-empty-guide {
  flex-direction: column;
  gap: 8px;
}

.kg-empty-guide strong {
  color: var(--text);
}

.kg-empty-guide p {
  margin: 0;
  max-width: 520px;
}

.graph-chart { width: 100%; height: 480px; }

.entity-detail {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.entity-header {
  display: flex; align-items: center; gap: 10px;
}

.entity-header h3 { margin: 0; font-size: 16px; flex: 1; }

.entity-desc { color: var(--text-muted); margin: 8px 0 0; font-size: 13px; line-height: 1.6; }

.neighbor-list { margin-top: 12px; }
.neighbor-list h4 { margin: 0 0 8px; font-size: 13px; color: var(--text-muted); }

.neighbor-item {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 0; font-size: 13px;
}

.neighbor-name { font-weight: 500; }

.kg-sidebar { display: grid; gap: 16px; align-content: start; }

.sub-heading { font-size: 14px; font-weight: 600; margin: 0 0 8px; }

.path-summary {
  margin: 0 0 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.path-form { display: grid; gap: 8px; }

.path-result { margin-top: 10px; }

.path-step {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 0; font-size: 13px; flex-wrap: wrap;
}

.path-relation { color: var(--el-color-primary); font-size: 12px; }

.path-empty { color: var(--text-muted); font-size: 13px; margin-top: 8px; }

.type-bars { display: grid; gap: 6px; }

.type-bar-row {
  display: grid; grid-template-columns: 80px 1fr 32px; align-items: center; gap: 8px;
}

.type-label { font-size: 12px; color: var(--text-muted); text-overflow: ellipsis; overflow: hidden; white-space: nowrap; }

.type-bar-track { height: 8px; background: var(--surface-muted); border-radius: 4px; overflow: hidden; }

.type-bar-fill { height: 100%; background: var(--el-color-primary-light-3); border-radius: 4px; transition: width 0.3s ease; }

.type-count { font-size: 12px; color: var(--text-muted); text-align: right; }

.hub-list { display: grid; gap: 4px; }

.hub-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 4px 0; font-size: 13px;
}

.hub-name { font-weight: 500; }

@media (max-width: 900px) {
  .kg-workbench-layout,
  .kg-summary-strip,
  .kg-main { grid-template-columns: 1fr; }
  .kg-graph-action-status,
  .path-action-status { align-items: stretch; flex-direction: column; }
  .toolbar { flex-direction: column; align-items: stretch; }
  .kb-select { width: 100%; }
}
</style>
