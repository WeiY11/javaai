<template>
  <div class="academic-page">
    <div class="metric-grid">
      <div class="metric-card"><span>引用链接</span><strong>{{ stats?.totalCitationLinks ?? 0 }}</strong></div>
      <div class="metric-card"><span>有引用文档</span><strong>{{ stats?.documentsWithCitations ?? 0 }}</strong></div>
      <div class="metric-card"><span>DOI 覆盖率</span><strong>{{ doiCoverage }}</strong></div>
      <div class="metric-card"><span>当前知识库</span><strong>{{ selectedKbName }}</strong></div>
    </div>

    <section class="workspace-card academic-workbench">
      <div class="toolbar">
        <div>
          <h2 class="section-title">学术图谱工作台</h2>
          <p class="section-subtitle">先确认知识库、引用抽取和综述主题，再查看引用网络或生成文献综述。</p>
        </div>
        <el-tag :type="academicReadiness.type" effect="plain">{{ academicReadiness.label }}</el-tag>
      </div>

      <div class="academic-workbench-layout">
        <div class="academic-scope-panel">
          <span>分析范围</span>
          <strong>{{ academicScopeSummary }}</strong>
          <p>{{ citationCoverageSummary }}</p>
        </div>

        <div class="academic-readiness-list">
          <div v-for="item in academicReadiness.items" :key="item.label" class="academic-readiness-item">
            <span class="status-dot" :class="{ muted: !item.ready }"></span>
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.detail }}</small>
            </div>
          </div>
        </div>

        <div class="academic-workbench-actions">
          <el-select v-model="selectedKbId" placeholder="选择知识库" filterable class="kb-select" @change="loadGraph">
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button :disabled="!selectedKbId" @click="loadGraph">刷新引用网络</el-button>
        </div>
      </div>

      <div v-if="workspaceLoading || workspaceLoadError" class="academic-load-status">
        <span>{{ workspaceLoading ? '正在加载学术图谱工作台...' : workspaceLoadError }}</span>
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

    <section class="academic-summary-strip">
      <div>
        <span>引用覆盖</span>
        <strong>{{ citationCoverageSummary }}</strong>
      </div>
      <div>
        <span>高频被引</span>
        <strong>{{ mostCitedSummary }}</strong>
      </div>
      <div>
        <span>综述状态</span>
        <strong>{{ reviewStatusSummary }}</strong>
      </div>
      <div>
        <span>证据范围</span>
        <strong>{{ reviewEvidenceSummary }}</strong>
      </div>
    </section>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">引用网络图谱</h2>
          <p class="section-subtitle">可视化知识库中论文之间的引用关系。节点大小反映引用频次。</p>
        </div>
      </div>

      <div v-if="graphActionError" class="academic-graph-action-status">
        <div>
          <strong>引用图谱加载失败</strong>
          <span>{{ graphActionError }}</span>
        </div>
        <el-button type="primary" plain :loading="graphLoading" :disabled="!selectedKbId" @click="loadGraph">
          重新加载引用网络
        </el-button>
      </div>

      <div v-loading="graphLoading" class="graph-container">
        <div v-if="!graphLoading && (!graph || graph.nodes.length === 0)" class="graph-empty citation-empty-guide">
          <strong>{{ emptyCitationGuide }}</strong>
          <p>上传包含参考文献的 PDF 论文，等待文档入库和引用抽取完成后再刷新引用网络。</p>
        </div>
        <div ref="chartRef" class="graph-chart" :style="{ display: graph?.nodes.length ? 'block' : 'none' }" />
      </div>
    </section>

    <div class="bottom-panels">
      <section class="workspace-card stats-panel">
        <div class="toolbar">
          <div>
            <h2 class="section-title">高频被引文献</h2>
            <p class="section-subtitle">知识库中最常被引用的论文</p>
          </div>
        </div>
        <el-table :data="stats?.mostCitedDois || []" size="small" max-height="320">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="DOI / 标题" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="ref-cell">
                <strong>{{ row.title || row.doi }}</strong>
                <span v-if="row.title && row.doi" class="ref-doi">{{ row.doi }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="authors" label="作者" width="160" show-overflow-tooltip />
          <el-table-column prop="year" label="年份" width="80" />
          <el-table-column prop="citationCount" label="引用次数" width="100" sortable />
        </el-table>

        <div v-if="stats && stats.yearDistribution && Object.keys(stats.yearDistribution).length > 0" class="year-dist">
          <h3 class="sub-heading">年份分布</h3>
          <div class="year-bars">
            <div
              v-for="(count, year) in stats.yearDistribution"
              :key="year"
              class="year-bar-wrap"
            >
              <div class="year-bar" :style="{ height: yearBarHeight(count) + 'px' }" :title="`${year}: ${count} 篇`" />
              <span class="year-label">{{ String(year).slice(2) }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="workspace-card review-panel">
        <div class="toolbar">
          <div>
            <h2 class="section-title">文献综述生成</h2>
            <p class="section-subtitle">基于知识库中的论文自动生成指定主题的文献综述</p>
          </div>
        </div>

        <div class="review-readiness-panel">
          <div>
            <span>当前状态</span>
            <strong>{{ reviewStatusSummary }}</strong>
          </div>
          <div>
            <span>引用证据</span>
            <strong>{{ reviewEvidenceSummary }}</strong>
          </div>
          <p>{{ reviewInputGuide }}</p>
        </div>

        <div class="review-form">
          <el-input
            v-model="reviewTopic"
            placeholder="输入研究主题，例如：深度学习在自然语言处理中的应用"
            clearable
            @keyup.enter="handleGenerateReview"
          />
          <el-button type="primary" :loading="reviewLoading" :disabled="!reviewTopic || !selectedKbId" @click="handleGenerateReview">
            生成综述
          </el-button>
        </div>

        <div v-if="reviewActionError" class="review-action-status">
          <div>
            <strong>文献综述生成失败</strong>
            <span>{{ reviewActionError }}</span>
          </div>
          <el-button
            type="primary"
            plain
            :loading="reviewLoading"
            :disabled="!reviewTopic || !selectedKbId"
            @click="handleGenerateReview"
          >
            重新生成
          </el-button>
        </div>

        <div v-if="reviewResult" class="review-output">
          <div class="review-toolbar">
            <el-tag type="success" size="small">已生成</el-tag>
            <el-button size="small" text @click="copyReview">复制</el-button>
          </div>
          <div class="review-content" v-html="renderedReview" />
        </div>
        <div v-else-if="!reviewLoading" class="review-placeholder">
          <p>{{ reviewInputGuide }}</p>
        </div>
        <div v-else class="review-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在生成文献综述，预计需要 1-2 分钟...</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import * as academicApi from '../api/academic'
import type { CitationGraph, CitationStats } from '../api/academic'
import { initGraphChart, type ECharts } from '../utils/graph-chart'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const kbStore = useKnowledgeBaseStore()
const selectedKbId = ref<number>()
const graph = ref<CitationGraph>()
const stats = ref<CitationStats>()
const workspaceLoading = ref(false)
const workspaceLoadError = ref('')
const graphActionError = ref('')
const graphLoading = ref(false)
const reviewTopic = ref('')
const reviewResult = ref('')
const reviewLoading = ref(false)
const reviewActionError = ref('')
const chartRef = ref<HTMLElement>()
let chartInstance: ECharts | null = null

const selectedKbName = computed(() =>
  kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value)?.name || '未选择'
)

const selectedKnowledgeBase = computed(() =>
  kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value) || null
)

const citationEdgeCount = computed(() => stats.value?.totalCitationLinks || graph.value?.edges.length || 0)
const citedDocumentCount = computed(() => stats.value?.documentsWithCitations || graph.value?.nodes.filter(node => node.type === 'document').length || 0)
const citedReferenceCount = computed(() => stats.value?.mostCitedDois?.length || graph.value?.nodes.filter(node => node.type === 'cited').length || 0)
const reviewTopicText = computed(() => reviewTopic.value.trim())

const doiCoverage = computed(() => {
  if (!stats.value || stats.value.totalCitationLinks === 0) return '0%'
  return Math.round((stats.value.citationsWithDoi / stats.value.totalCitationLinks) * 100) + '%'
})

const renderedReview = computed(() => {
  return reviewResult.value ? md.render(reviewResult.value) : ''
})

const reviewLineCount = computed(() => {
  if (!reviewResult.value.trim()) return 0
  return reviewResult.value.trim().split(/\r?\n/).filter(Boolean).length
})

const academicScopeSummary = computed(() => {
  if (!selectedKnowledgeBase.value) return '未选择知识库'
  return `${selectedKnowledgeBase.value.name} · ${citedDocumentCount.value} 篇有引用文档`
})

const citationCoverageSummary = computed(() => {
  if (!citationEdgeCount.value) return '暂无 DOI 覆盖'
  if (!stats.value) return `${citationEdgeCount.value} 条引用链接`
  return `${doiCoverage.value} DOI，${stats.value.citationsWithoutDoi} 条待补齐`
})

const mostCitedSummary = computed(() => {
  const top = stats.value?.mostCitedDois?.[0]
  if (!top) return '暂无高频被引'
  return `${top.title || top.doi} · ${top.citationCount} 次`
})

const reviewEvidenceSummary = computed(() => {
  if (!selectedKnowledgeBase.value) return '先选择知识库'
  if (!citationEdgeCount.value) return '暂无引用链路'
  return `${citationEdgeCount.value} 条引用，${citedReferenceCount.value} 个被引项`
})

const reviewStatusSummary = computed(() => {
  if (reviewLoading.value) return '正在生成'
  if (reviewResult.value.trim()) return '综述已生成'
  if (!selectedKnowledgeBase.value) return '待选择知识库'
  if (!citationEdgeCount.value) return '引用证据不足'
  if (!reviewTopicText.value) return '待输入主题'
  return '可生成综述'
})

const reviewInputGuide = computed(() => {
  if (reviewResult.value.trim()) return `已生成 ${reviewLineCount.value} 行，可复制后继续编辑`
  if (!selectedKnowledgeBase.value) return '先选择知识库，再确定综述主题'
  if (!citationEdgeCount.value) return '先上传含参考文献的论文，形成可引用的证据网络'
  if (reviewTopicText.value) return `主题：${reviewTopicText.value}`
  return '输入明确研究主题，优先覆盖方法、数据集、争议和趋势'
})

const emptyCitationGuide = computed(() => {
  if (!selectedKnowledgeBase.value) return '先选择知识库'
  if (citationEdgeCount.value) return '引用统计已有数据，图谱待刷新'
  return '当前知识库暂无可用引用网络'
})

const academicReadiness = computed(() => {
  const hasKnowledgeBase = !!selectedKnowledgeBase.value
  const hasCitations = citationEdgeCount.value > 0
  const hasTopic = !!reviewTopicText.value
  const items = [
    {
      label: '选择知识库',
      detail: hasKnowledgeBase ? academicScopeSummary.value : '先选择知识库',
      ready: hasKnowledgeBase
    },
    {
      label: '提取引用',
      detail: hasCitations ? citationCoverageSummary.value : '等待文档入库后提取参考文献',
      ready: hasCitations
    },
    {
      label: '生成综述',
      detail: hasTopic ? reviewTopicText.value : '输入研究主题后再生成',
      ready: hasTopic
    }
  ]

  if (!hasKnowledgeBase) return { label: '待选择知识库', type: 'info' as const, items }
  if (!hasCitations) return { label: '待引用提取', type: 'warning' as const, items }
  if (!hasTopic) return { label: '待输入主题', type: 'warning' as const, items }
  return { label: '可生成综述', type: 'success' as const, items }
})

function yearBarHeight(count: number): number {
  if (!stats.value?.yearDistribution) return 0
  const max = Math.max(...Object.values(stats.value.yearDistribution))
  return max > 0 ? Math.round((count / max) * 80) : 0
}

async function loadGraph() {
  if (!selectedKbId.value) return
  graphLoading.value = true
  graphActionError.value = ''
  try {
    const [graphData, statsData] = await Promise.all([
      academicApi.getCitationGraph(selectedKbId.value),
      academicApi.getCitationStats(selectedKbId.value)
    ])
    graph.value = graphData
    stats.value = statsData
    await nextTick()
    renderChart(graphData)
  } catch (e: any) {
    graphActionError.value = e.response?.data?.message || '加载引用图谱失败'
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
    workspaceLoadError.value = e.response?.data?.message || '加载学术图谱工作台失败'
  } finally {
    workspaceLoading.value = false
  }
}

function renderChart(data: CitationGraph) {
  if (!chartRef.value || !data.nodes.length) return

  if (!chartInstance) {
    chartInstance = initGraphChart(chartRef.value)
  }

  // Count incoming edges for sizing
  const inDegree: Record<string, number> = {}
  data.edges.forEach(e => {
    inDegree[e.target] = (inDegree[e.target] || 0) + 1
  })

  const nodes = data.nodes.map(n => {
    const degree = inDegree[n.id] || 0
    const isDoc = n.type === 'document'
    return {
      id: n.id,
      name: n.label,
      symbolSize: isDoc ? 36 : Math.max(14, Math.min(32, 10 + degree * 6)),
      category: isDoc ? 0 : 1,
      value: degree,
      itemStyle: {
        color: isDoc ? '#409EFF' : (degree >= 3 ? '#E6A23C' : '#67C23A')
      },
      label: {
        show: isDoc || degree >= 2,
        fontSize: isDoc ? 12 : 10
      }
    }
  })

  const links = data.edges.map(e => ({
    source: e.source,
    target: e.target,
    lineStyle: { curveness: 0.1, opacity: 0.5 }
  }))

  chartInstance.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        if (p.dataType === 'node') {
          const node = data.nodes.find(n => n.id === p.data.id)
          let html = `<strong>${node?.label}</strong>`
          if (node?.authors) html += `<br/>作者: ${node.authors}`
          if (node?.year) html += `<br/>年份: ${node.year}`
          if (node?.doi) html += `<br/>DOI: ${node.doi}`
          return html
        }
        return `${p.data.source} → ${p.data.target}`
      }
    },
    legend: {
      data: ['源文档', '被引文献'],
      top: 8,
      right: 16
    },
    animationDuration: 800,
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      categories: [
        { name: '源文档' },
        { name: '被引文献' }
      ],
      data: nodes,
      links: links,
      force: {
        repulsion: 260,
        edgeLength: [80, 200],
        gravity: 0.1
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 }
      },
      label: {
        position: 'right',
        formatter: '{b}'
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6
    }]
  }, true)
}

async function handleGenerateReview() {
  if (!selectedKbId.value || !reviewTopic.value.trim()) return
  reviewLoading.value = true
  reviewResult.value = ''
  reviewActionError.value = ''
  try {
    reviewResult.value = await academicApi.generateLiteratureReview(
      selectedKbId.value,
      reviewTopic.value.trim()
    )
    ElMessage.success('文献综述已生成')
  } catch (e: any) {
    reviewActionError.value = e.response?.data?.message || '文献综述生成失败'
    ElMessage.error(reviewActionError.value)
  } finally {
    reviewLoading.value = false
  }
}

async function copyReview() {
  await navigator.clipboard.writeText(reviewResult.value)
  ElMessage.success('已复制到剪贴板')
}

function handleResize() {
  chartInstance?.resize()
}

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
.academic-page {
  display: grid;
  gap: 16px;
}

.kb-select {
  width: 260px;
}

.academic-workbench {
  border-left: 3px solid var(--el-color-primary);
}

.academic-workbench-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.3fr) 260px;
  gap: 14px;
  align-items: stretch;
}

.academic-scope-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding-left: 12px;
  border-left: 1px solid var(--border);
}

.academic-scope-panel span,
.academic-summary-strip span,
.review-readiness-panel span {
  font-size: 12px;
  color: var(--text-muted);
}

.academic-scope-panel strong,
.academic-summary-strip strong,
.review-readiness-panel strong {
  color: var(--text);
  overflow-wrap: anywhere;
}

.academic-scope-panel p,
.review-readiness-panel p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.academic-readiness-list {
  display: grid;
  gap: 8px;
}

.academic-readiness-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  min-width: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.academic-readiness-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.academic-readiness-item strong,
.academic-readiness-item small {
  display: block;
  overflow-wrap: anywhere;
}

.academic-readiness-item small {
  margin-top: 2px;
  color: var(--text-muted);
}

.status-dot {
  width: 9px;
  height: 9px;
  margin-top: 6px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--el-color-success);
  box-shadow: 0 0 0 3px var(--el-color-success-light-9);
}

.status-dot.muted {
  background: var(--el-color-warning);
  box-shadow: 0 0 0 3px var(--el-color-warning-light-9);
}

.academic-workbench-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  justify-content: center;
}

.academic-workbench-actions .kb-select {
  width: 100%;
}

.academic-load-status {
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

.academic-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.academic-summary-strip div {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.academic-summary-strip strong {
  display: block;
  margin-top: 4px;
  line-height: 1.45;
}

.academic-graph-action-status,
.review-action-status {
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

.academic-graph-action-status strong,
.academic-graph-action-status span,
.review-action-status strong,
.review-action-status span {
  display: block;
}

.academic-graph-action-status strong,
.review-action-status strong {
  color: var(--text);
}

.academic-graph-action-status span,
.review-action-status span {
  margin-top: 4px;
  line-height: 1.5;
}

.graph-container {
  min-height: 460px;
  position: relative;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface-soft);
}

.graph-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 460px;
  color: var(--text-muted);
  text-align: center;
  padding: 40px;
}

.citation-empty-guide strong {
  color: var(--text);
}

.citation-empty-guide p {
  margin: 0;
  max-width: 520px;
  line-height: 1.7;
}

.graph-chart {
  width: 100%;
  height: 460px;
}

.bottom-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.stats-panel,
.review-panel {
  min-height: 300px;
}

.ref-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ref-doi {
  font-size: 12px;
  color: var(--text-muted);
}

.sub-heading {
  font-size: 14px;
  font-weight: 600;
  margin: 16px 0 8px;
  color: var(--text);
}

.year-dist {
  margin-top: 8px;
}

.year-bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 100px;
  padding-bottom: 20px;
  position: relative;
}

.year-bar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  min-width: 16px;
}

.year-bar {
  width: 100%;
  max-width: 24px;
  background: var(--el-color-primary-light-3);
  border-radius: 3px 3px 0 0;
  min-height: 2px;
  transition: height 0.3s ease;
}

.year-label {
  font-size: 10px;
  color: var(--text-muted);
  margin-top: 4px;
}

.review-form {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.review-form .el-input {
  flex: 1;
}

.review-readiness-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
  margin-bottom: 14px;
  padding: 12px 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

.review-readiness-panel div {
  min-width: 0;
}

.review-readiness-panel strong {
  display: block;
  margin-top: 3px;
}

.review-readiness-panel p {
  grid-column: 1 / -1;
}

.review-output {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px;
  background: var(--surface-soft);
  max-height: 400px;
  overflow-y: auto;
}

.review-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.review-content {
  line-height: 1.8;
  color: var(--text);
}

.review-content :deep(h1),
.review-content :deep(h2),
.review-content :deep(h3) {
  margin: 16px 0 8px;
}

.review-content :deep(p) {
  margin: 6px 0;
}

.review-placeholder {
  color: var(--text-muted);
  text-align: center;
  padding: 40px 20px;
}

.review-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 20px;
  color: var(--text-muted);
}

@media (max-width: 900px) {
  .academic-workbench-layout,
  .academic-summary-strip,
  .review-readiness-panel {
    grid-template-columns: 1fr;
  }
  .bottom-panels {
    grid-template-columns: 1fr;
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .academic-graph-action-status,
  .review-action-status {
    align-items: stretch;
    flex-direction: column;
  }
  .kb-select {
    width: 100%;
  }
}
</style>
