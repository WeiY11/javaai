<template>
  <div class="academic-page">
    <div class="metric-grid">
      <div class="metric-card"><span>引用链接</span><strong>{{ stats?.totalCitationLinks ?? 0 }}</strong></div>
      <div class="metric-card"><span>有引用文档</span><strong>{{ stats?.documentsWithCitations ?? 0 }}</strong></div>
      <div class="metric-card"><span>DOI 覆盖率</span><strong>{{ doiCoverage }}</strong></div>
      <div class="metric-card"><span>当前知识库</span><strong>{{ selectedKbName }}</strong></div>
    </div>

    <section class="workspace-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">引用网络图谱</h2>
          <p class="section-subtitle">可视化知识库中论文之间的引用关系。节点大小反映引用频次。</p>
        </div>
        <el-select v-model="selectedKbId" placeholder="选择知识库" filterable class="kb-select" @change="loadGraph">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>

      <div v-loading="graphLoading" class="graph-container">
        <div v-if="!graphLoading && (!graph || graph.nodes.length === 0)" class="graph-empty">
          <p>当前知识库中暂无引用数据。上传包含参考文献的 PDF 论文后将自动提取引用关系。</p>
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

        <div v-if="reviewResult" class="review-output">
          <div class="review-toolbar">
            <el-tag type="success" size="small">已生成</el-tag>
            <el-button size="small" text @click="copyReview">复制</el-button>
          </div>
          <div class="review-content" v-html="renderedReview" />
        </div>
        <div v-else-if="!reviewLoading" class="review-placeholder">
          <p>输入研究主题后，AI 将基于知识库中的论文自动生成结构化文献综述。</p>
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
import * as echarts from 'echarts'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'
import * as academicApi from '../api/academic'
import type { CitationGraph, CitationStats } from '../api/academic'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const kbStore = useKnowledgeBaseStore()
const selectedKbId = ref<number>()
const graph = ref<CitationGraph>()
const stats = ref<CitationStats>()
const graphLoading = ref(false)
const reviewTopic = ref('')
const reviewResult = ref('')
const reviewLoading = ref(false)
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

const selectedKbName = computed(() =>
  kbStore.knowledgeBases.find(kb => kb.id === selectedKbId.value)?.name || '未选择'
)

const doiCoverage = computed(() => {
  if (!stats.value || stats.value.totalCitationLinks === 0) return '0%'
  return Math.round((stats.value.citationsWithDoi / stats.value.totalCitationLinks) * 100) + '%'
})

const renderedReview = computed(() => {
  return reviewResult.value ? md.render(reviewResult.value) : ''
})

function yearBarHeight(count: number): number {
  if (!stats.value?.yearDistribution) return 0
  const max = Math.max(...Object.values(stats.value.yearDistribution))
  return max > 0 ? Math.round((count / max) * 80) : 0
}

async function loadGraph() {
  if (!selectedKbId.value) return
  graphLoading.value = true
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
    ElMessage.error('加载引用图谱失败')
  } finally {
    graphLoading.value = false
  }
}

function renderChart(data: CitationGraph) {
  if (!chartRef.value || !data.nodes.length) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
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
  try {
    reviewResult.value = await academicApi.generateLiteratureReview(
      selectedKbId.value,
      reviewTopic.value.trim()
    )
    ElMessage.success('文献综述已生成')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '文献综述生成失败')
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

onMounted(async () => {
  await kbStore.loadKnowledgeBases()
  selectedKbId.value = kbStore.knowledgeBases[0]?.id
  if (selectedKbId.value) await loadGraph()
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
  align-items: center;
  justify-content: center;
  height: 460px;
  color: var(--text-muted);
  text-align: center;
  padding: 40px;
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
  .bottom-panels {
    grid-template-columns: 1fr;
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .kb-select {
    width: 100%;
  }
}
</style>
