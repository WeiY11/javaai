<template>
  <div class="dashboard-page" v-loading="dashboardLoading">
    <section class="dashboard-hero">
      <div class="hero-copy">
        <p class="eyebrow">Evidence Workspace</p>
        <h2>把资料、证据、问答和输出串成一条工作流</h2>
        <p>
          先建知识库，再上传文档，接着用检索和对话验证答案，最后沉淀笔记、导出引用和分析报告。
        </p>
      </div>
      <div class="hero-actions">
        <RouterLink class="primary-action" to="/knowledge-bases">
          <el-icon><Collection /></el-icon>
          <span>配置知识库</span>
        </RouterLink>
        <RouterLink class="secondary-action" to="/documents">
          <el-icon><Files /></el-icon>
          <span>上传文档</span>
        </RouterLink>
      </div>
    </section>

    <div v-if="dashboardLoadError" class="dashboard-load-status">
      <div>
        <strong>数据加载失败</strong>
        <span>{{ dashboardLoadError }}</span>
      </div>
      <el-button type="primary" plain @click="loadDashboardData">重新加载</el-button>
    </div>

    <div class="metric-grid">
      <div class="metric-card"><span>知识库</span><strong>{{ kbStore.knowledgeBases.length }}</strong></div>
      <div class="metric-card"><span>已启用</span><strong>{{ activeKnowledgeBases }}</strong></div>
      <div class="metric-card"><span>会话</span><strong>{{ chatStore.conversations.length }}</strong></div>
      <div class="metric-card"><span>当前知识库</span><strong>{{ selectedKbName }}</strong></div>
    </div>

    <section class="dashboard-grid">
      <div class="workspace-card command-center">
        <div class="toolbar">
          <div>
            <h2 class="section-title">下一步</h2>
            <p class="section-subtitle">按证据链推进，不在页面之间盲跳。</p>
          </div>
          <el-tag type="info" effect="plain">{{ readinessLabel }}</el-tag>
        </div>

        <div class="workflow-list">
          <RouterLink
            v-for="step in workflowSteps"
            :key="step.to"
            class="workflow-step"
            :to="step.to"
          >
            <span class="step-index">{{ step.index }}</span>
            <span>
              <strong>{{ step.title }}</strong>
              <small>{{ step.detail }}</small>
            </span>
          </RouterLink>
        </div>
      </div>

      <div class="workspace-card activity-panel">
        <div class="toolbar">
          <div>
            <h2 class="section-title">最近会话</h2>
            <p class="section-subtitle">从已有问题继续，而不是重新开始。</p>
          </div>
          <RouterLink class="text-link" to="/chat">进入问答</RouterLink>
        </div>

        <div v-if="recentConversations.length" class="conversation-preview-list">
          <RouterLink
            v-for="conversation in recentConversations"
            :key="conversation.id"
            class="conversation-preview"
            to="/chat"
            @click="chatStore.selectConversation(conversation)"
          >
            <strong>{{ conversation.title || '新的证据问答' }}</strong>
            <span>{{ conversation.modelProvider || 'default' }} · {{ formatDate(conversation.updatedAt || conversation.createdAt) }}</span>
          </RouterLink>
        </div>
        <el-empty v-else description="暂无会话，先选择知识库并创建问答" />
      </div>

      <div v-if="isAdmin" class="workspace-card health-panel" v-loading="runtimeHealthLoading">
        <div class="toolbar">
          <div>
            <h2 class="section-title">运行健康</h2>
            <p class="section-subtitle">首页直接暴露后端状态和依赖组件，避免降级运行被隐藏。</p>
          </div>
          <el-tag :type="runtimeHealthTagType" effect="plain">{{ runtimeHealth?.status || '检查中' }}</el-tag>
        </div>

        <div v-if="runtimeHealthError" class="runtime-health-status">
          <div>
            <strong>运行健康检查失败</strong>
            <span>{{ runtimeHealthError }}</span>
          </div>
          <el-button type="primary" plain :loading="runtimeHealthLoading" @click="loadRuntimeHealth">重新检查</el-button>
        </div>

        <div v-else class="runtime-health-summary">
          <div>
            <span>后端状态</span>
            <strong>{{ runtimeHealth?.status || '等待检查' }}</strong>
          </div>
          <div>
            <span>组件状态</span>
            <strong>{{ runtimeComponentSummary }}</strong>
          </div>
          <el-button type="primary" plain :loading="runtimeHealthLoading" @click="loadRuntimeHealth">重新检查</el-button>
        </div>

        <div v-if="runtimeComponentEntries.length" class="runtime-component-list">
          <div v-for="[name, component] in runtimeComponentEntries" :key="name">
            <span class="status-dot" :class="componentStatusClass(component.status, component.required)"></span>
            <strong>{{ componentLabel(name) }}</strong>
            <em v-if="component.required === false" class="component-optional-badge">Optional</em>
            <small>{{ component.status }}{{ component.message ? ` · ${component.message}` : '' }}</small>
            <small
              v-if="componentActionHint(name, component.status, component.action, component.required)"
              class="component-action-hint"
            >
              <b>处理建议</b>{{ componentActionHint(name, component.status, component.action, component.required) }}
            </small>
          </div>
        </div>

        <div class="status-list">
          <div>
            <span class="status-dot" :class="{ muted: kbStore.knowledgeBases.length === 0 }"></span>
            <strong>知识库</strong>
            <small>{{ kbStore.knowledgeBases.length ? '已可使用' : '需要创建' }}</small>
          </div>
          <div>
            <span class="status-dot" :class="{ muted: chatStore.conversations.length === 0 }"></span>
            <strong>问答历史</strong>
            <small>{{ chatStore.conversations.length ? '可继续追问' : '暂无历史' }}</small>
          </div>
          <div>
            <span class="status-dot"></span>
            <strong>输出链路</strong>
            <small>分析、笔记、引用导出已在侧栏集中入口</small>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Collection, Files } from '@element-plus/icons-vue'
import { getRuntimeHealth, type RuntimeHealthComponent } from '../api/health'
import { useAuthStore } from '../stores/auth.store'
import { useChatStore } from '../stores/chat.store'
import { useKnowledgeBaseStore } from '../stores/knowledge-base.store'

const authStore = useAuthStore()
const kbStore = useKnowledgeBaseStore()
const chatStore = useChatStore()
const dashboardLoading = ref(false)
const dashboardLoadError = ref('')
const runtimeHealth = ref<Awaited<ReturnType<typeof getRuntimeHealth>> | null>(null)
const runtimeHealthLoading = ref(false)
const runtimeHealthError = ref('')

const isAdmin = computed(() => authStore.user?.systemRole === 'ADMIN')

const activeKnowledgeBases = computed(() =>
  kbStore.knowledgeBases.filter(kb => (kb.status || 'ACTIVE') === 'ACTIVE').length
)

const selectedKbName = computed(() => kbStore.currentKb?.name || kbStore.knowledgeBases[0]?.name || '未选择')

const readinessLabel = computed(() => {
  if (!kbStore.knowledgeBases.length) return '先建库'
  if (!chatStore.conversations.length) return '可开始问答'
  return '可继续工作'
})

const recentConversations = computed(() => chatStore.conversations.slice(0, 4))

const runtimeComponentEntries = computed(() => Object.entries(runtimeHealth.value?.components || {}))

const requiredComponentCount = computed(() =>
  runtimeComponentEntries.value.filter(([, component]) => isRequiredComponent(component)).length
)

const degradedRequiredComponentCount = computed(() =>
  runtimeComponentEntries.value.filter(
    ([, component]) => isRequiredComponent(component) && !isHealthyStatus(component.status)
  ).length
)

const optionalComponentCount = computed(() =>
  runtimeComponentEntries.value.filter(([, component]) => !isRequiredComponent(component)).length
)

const runtimeComponentSummary = computed(() => {
  if (!runtimeHealth.value) return '等待健康检查'
  const componentCount = runtimeComponentEntries.value.length
  if (!componentCount) return '未返回组件'
  if (!degradedRequiredComponentCount.value) {
    return optionalComponentCount.value
      ? `${requiredComponentCount.value} 项必需正常，${optionalComponentCount.value} 项可选`
      : `${componentCount} 项正常`
  }
  return `${degradedRequiredComponentCount.value} 项必需需处理 / ${requiredComponentCount.value} 项必需`
})

const runtimeHealthTagType = computed(() => healthTagType(runtimeHealth.value?.status))

const workflowSteps = computed(() => [
  {
    index: '01',
    title: kbStore.knowledgeBases.length ? '维护知识库' : '创建第一个知识库',
    detail: '确定资料边界、切片策略和证据阈值。',
    to: '/knowledge-bases'
  },
  {
    index: '02',
    title: '上传并观察入库',
    detail: '检查提取、切片、嵌入和索引状态。',
    to: '/documents'
  },
  {
    index: '03',
    title: '用问答验证证据',
    detail: '每个回答都回到文档切片和引用来源。',
    to: '/chat'
  },
  {
    index: '04',
    title: '沉淀成果',
    detail: '把分析、笔记和引用导出整理成可交付材料。',
    to: '/analysis',
    adminOnly: true
  }
].filter(step => !step.adminOnly || isAdmin.value))

function formatDate(value?: string) {
  if (!value) return '未知时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function loadDashboardData() {
  dashboardLoading.value = true
  dashboardLoadError.value = ''
  try {
    await Promise.all([
      kbStore.loadKnowledgeBases(),
      chatStore.loadConversations()
    ])
  } catch (e: any) {
    dashboardLoadError.value = e.response?.data?.message || e.message || '工作台数据加载失败'
  } finally {
    dashboardLoading.value = false
  }
}

async function loadRuntimeHealth() {
  runtimeHealthLoading.value = true
  runtimeHealthError.value = ''
  try {
    runtimeHealth.value = await getRuntimeHealth()
  } catch (e: any) {
    runtimeHealthError.value = e.response?.data?.message || e.message || '运行健康检查失败'
  } finally {
    runtimeHealthLoading.value = false
  }
}

function isHealthyStatus(status?: string) {
  return ['UP', 'OK', 'HEALTHY'].includes((status || '').toUpperCase())
}

function isRequiredComponent(component: RuntimeHealthComponent) {
  return component.required !== false
}

function healthTagType(status?: string) {
  const normalized = (status || '').toUpperCase()
  if (isHealthyStatus(normalized)) return 'success'
  if (['DEGRADED', 'WARN', 'WARNING', 'NOT_CONFIGURED'].includes(normalized)) return 'warning'
  if (['DOWN', 'FAILED', 'ERROR'].includes(normalized)) return 'danger'
  return 'info'
}

function componentStatusClass(status?: string, required = true) {
  if (required === false) return { muted: true }
  const type = healthTagType(status)
  return {
    muted: type === 'info',
    warning: type === 'warning',
    danger: type === 'danger'
  }
}

function componentLabel(name: string) {
  const labels: Record<string, string> = {
    postgresql: 'PostgreSQL',
    elasticsearch: 'Elasticsearch',
    minio: 'MinIO'
  }
  return labels[name.toLowerCase()] || name
}

function componentActionHint(name: string, status?: string, action?: string, required?: boolean) {
  if (required === false) return ''
  if (action) return action
  const normalizedName = name.toLowerCase()
  const normalizedStatus = (status || '').toUpperCase()
  if (isHealthyStatus(normalizedStatus)) return ''
  if (normalizedName === 'elasticsearch') {
    return normalizedStatus === 'NOT_CONFIGURED'
      ? '配置 ES_URIS 并启动 Elasticsearch，全文检索和索引才会完整可用。'
      : '检查 ES_URIS 指向的 Elasticsearch 是否可访问。'
  }
  if (normalizedName === 'minio') {
    return normalizedStatus === 'NOT_CONFIGURED'
      ? '配置 MINIO_ENDPOINT、MINIO_ACCESS_KEY、MINIO_SECRET_KEY 和 MINIO_BUCKET。'
      : '检查 MinIO 服务、桶权限和 MINIO_ENDPOINT。'
  }
  if (normalizedName === 'postgresql') {
    return '检查 POSTGRES_HOST、POSTGRES_PORT、POSTGRES_DB、POSTGRES_USER 和 POSTGRES_PASSWORD。'
  }
  return normalizedStatus === 'NOT_CONFIGURED' ? '补齐该组件配置后重新检查。' : '检查该组件连通性和服务日志。'
}

watch(isAdmin, canViewRuntimeHealth => {
  if (canViewRuntimeHealth && !runtimeHealth.value && !runtimeHealthLoading.value) {
    void loadRuntimeHealth()
  }
}, { immediate: true })

onMounted(() => {
  void loadDashboardData()
})
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 18px;
}

.dashboard-hero {
  min-height: 240px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 28px;
  align-items: end;
  padding: 32px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background:
    linear-gradient(135deg, rgba(79, 70, 229, 0.12), rgba(13, 148, 136, 0.08)),
    var(--surface-solid);
  box-shadow: var(--shadow);
}

.hero-copy {
  max-width: 760px;
}

.hero-copy h2 {
  margin: 0;
  color: var(--text);
  font-size: 34px;
  line-height: 1.25;
}

.hero-copy p:not(.eyebrow) {
  margin: 14px 0 0;
  color: var(--text-muted);
  line-height: 1.8;
  font-size: 16px;
}

.hero-actions {
  display: grid;
  gap: 10px;
  min-width: 180px;
}

.primary-action,
.secondary-action,
.workflow-step,
.conversation-preview,
.text-link {
  color: inherit;
  text-decoration: none;
}

.primary-action,
.secondary-action {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-sm);
  font-weight: 700;
}

.primary-action {
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-strong));
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.24);
}

.secondary-action {
  color: var(--text);
  background: var(--surface-muted);
  border: 1px solid var(--border);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.9fr);
  gap: 16px;
}

.dashboard-load-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
}

.dashboard-load-status strong,
.dashboard-load-status span {
  display: block;
}

.dashboard-load-status strong {
  color: var(--text);
}

.dashboard-load-status span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.command-center {
  display: grid;
  gap: 18px;
}

.workflow-list,
.conversation-preview-list,
.runtime-component-list,
.status-list {
  display: grid;
  gap: 10px;
}

.workflow-step {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.workflow-step:hover,
.conversation-preview:hover {
  border-color: var(--primary);
  transform: translateY(-2px);
}

.step-index {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--primary);
  background: var(--primary-soft);
  font-weight: 800;
}

.workflow-step strong,
.conversation-preview strong {
  display: block;
  color: var(--text);
}

.workflow-step small,
.conversation-preview span,
.runtime-component-list small,
.status-list small {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.activity-panel,
.health-panel {
  display: grid;
  align-content: start;
  gap: 16px;
}

.runtime-health-status,
.runtime-health-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--radius-sm);
  background: var(--el-color-warning-light-9);
}

.runtime-health-status strong,
.runtime-health-status span,
.runtime-health-summary span,
.runtime-health-summary strong {
  display: block;
}

.runtime-health-status strong,
.runtime-health-summary strong {
  color: var(--text);
}

.runtime-health-status span,
.runtime-health-summary span {
  margin-top: 4px;
  color: var(--text-muted);
  line-height: 1.5;
}

.runtime-component-list {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.runtime-component-list div {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-solid);
}

.runtime-component-list strong {
  margin-left: 8px;
  color: var(--text);
}

.component-action-hint {
  padding-top: 8px;
  color: var(--warning);
}

.component-optional-badge {
  margin-left: 8px;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  background: var(--surface-muted);
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.component-action-hint b {
  margin-right: 6px;
  color: var(--text);
}

.conversation-preview {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.text-link {
  color: var(--primary);
  font-weight: 700;
}

.health-panel {
  grid-column: 1 / -1;
}

.status-list {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.status-list div {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.status-list strong {
  margin-left: 8px;
  color: var(--text);
}

.status-dot.muted {
  background: var(--text-soft);
}

.status-dot.warning {
  background: var(--warning);
}

.status-dot.danger {
  background: var(--danger);
}

@media (max-width: 980px) {
  .dashboard-hero,
  .dashboard-grid,
  .runtime-component-list,
  .status-list {
    grid-template-columns: 1fr;
  }

  .runtime-health-status,
  .runtime-health-summary {
    flex-direction: column;
    align-items: stretch;
  }

  .dashboard-hero {
    align-items: start;
  }
}

@media (max-width: 640px) {
  .dashboard-hero {
    padding: 22px;
  }

  .hero-copy h2 {
    font-size: 26px;
  }
}
</style>
