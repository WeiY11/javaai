<template>
  <div class="workspace-shell" :class="{ 'nav-open': navOpen }">
    <aside class="nav-rail" aria-label="主导航">
      <div class="brand-mark">
        <span class="brand-icon">E</span>
        <span class="brand-text">EviMind</span>
      </div>

      <nav class="rail-links" aria-label="工作流导航">
        <section v-for="group in visibleShellModuleGroups" :key="group.label" class="rail-group">
          <span class="nav-group-title">{{ group.label }}</span>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            class="rail-link"
            :to="item.path"
            :title="`${group.label} · ${item.label}`"
            @click="navOpen = false"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </RouterLink>
        </section>
      </nav>
    </aside>

    <div class="workspace-frame">
      <header class="workspace-topbar">
        <button class="mobile-menu" type="button" aria-label="打开导航" @click="navOpen = !navOpen">
          <el-icon><Menu /></el-icon>
        </button>
        <div class="topbar-title">
          <p class="eyebrow">{{ activeMeta.eyebrow }}</p>
          <h1>{{ activeMeta.title }}</h1>
          <div class="shell-context-strip">
            <span>当前阶段</span>
            <strong>{{ activeModuleGroup.label }}</strong>
            <span>{{ activeWorkflowLabel }}</span>
            <span>下一步</span>
            <strong>{{ activeNextStep }}</strong>
          </div>
          <div class="route-context-items">
            <span v-for="item in routeContextItems" :key="item">{{ item }}</span>
          </div>
        </div>
        <div class="topbar-actions">
          <el-switch
            v-model="isDark"
            inline-prompt
            active-text="暗"
            inactive-text="亮"
            style="--el-switch-on-color: var(--surface-muted); --el-switch-off-color: var(--surface-muted); border: 1px solid var(--border);"
            @change="toggleDark"
          />
          <el-tag effect="plain" type="info" round class="user-tag">{{ authStore.user?.username || '当前用户' }}</el-tag>
          <el-button @click="logout" text>退出登录</el-button>
        </div>
      </header>

      <main class="workspace-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  ChatDotRound,
  Collection,
  Connection,
  Files,
  Menu,
  Notebook,
  Reading,
  Share,
  TrendCharts
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth.store'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const navOpen = ref(false)
const isDark = ref(localStorage.getItem('darkMode') === 'true')

const shellModuleGroups = [
  {
    label: '准备资料',
    summary: '建库、上传、检查入库状态',
    items: [
      { path: '/', label: '工作台', icon: TrendCharts, title: '工作台', eyebrow: '项目总览与下一步', context: '总览', next: '创建知识库' },
      { path: '/knowledge-bases', label: '知识库', icon: Collection, title: '知识库管理', eyebrow: '权限、切片与证据阈值', context: '资料边界', next: '上传文档' },
      { path: '/documents', label: '文档', icon: Files, title: '文档入库', eyebrow: '上传、入库状态与切片', context: '入库状态', next: '开始问答' }
    ]
  },
  {
    label: '证据问答',
    summary: '检索、对话、图谱关系',
    items: [
      { path: '/chat', label: '智能问答', icon: ChatDotRound, title: '智能问答', eyebrow: '证据化 RAG 对话', context: '回答溯源', next: '沉淀笔记' },
      { path: '/knowledge-graph', label: '知识图谱', icon: Connection, title: '知识图谱', eyebrow: '实体关系与多跳推理', context: '关系探索', next: '分析输出' },
      { path: '/academic', label: '学术图谱', icon: Share, title: '学术图谱', eyebrow: '引用网络与文献综述', context: '引用网络', next: '生成综述' }
    ]
  },
  {
    label: '成果输出',
    summary: '分析、引用、笔记复用',
    items: [
      { path: '/analysis', label: '文件分析', icon: TrendCharts, title: '文件分析', eyebrow: '批量分析与报告导出', context: '批量报告', next: '导出引用', adminOnly: true },
      { path: '/citations', label: '引用导出', icon: Reading, title: '引用导出', eyebrow: 'BibTeX 与 APA', context: '引用文本', next: '整理笔记' },
      { path: '/notes', label: '科研笔记', icon: Notebook, title: '科研笔记', eyebrow: '切片批注与研究记录', context: '研究记录', next: '回到工作台' }
    ]
  }
]

const isAdmin = computed(() => authStore.user?.systemRole === 'ADMIN')
const visibleShellModuleGroups = computed(() =>
  shellModuleGroups
    .map(group => ({
      ...group,
      items: group.items.filter(item => !item.adminOnly || isAdmin.value)
    }))
    .filter(group => group.items.length > 0)
)
const navItems = computed(() => visibleShellModuleGroups.value.flatMap(group => group.items))
const activeMeta = computed(() => navItems.value.find(item => item.path === route.path) || navItems.value[0])
const activeModuleGroup = computed(() =>
  visibleShellModuleGroups.value.find(group => group.items.some(item => item.path === route.path)) ||
  visibleShellModuleGroups.value[0]
)
const activeWorkflowIndex = computed(() =>
  navItems.value.findIndex(item => item.path === activeMeta.value.path) + 1
)
const activeWorkflowLabel = computed(() => `${activeWorkflowIndex.value}/${navItems.value.length} · ${activeMeta.value.context}`)
const activeNextStep = computed(() => activeMeta.value.next)
const routeContextItems = computed(() => [
  activeModuleGroup.value.summary,
  activeMeta.value.eyebrow
])

function toggleDark(val: string | number | boolean) {
  const enabled = Boolean(val)
  document.documentElement.classList.toggle('is-dark', enabled)
  localStorage.setItem('darkMode', String(enabled))
}

function logout() {
  authStore.logout()
  router.push('/login')
}

async function restoreSession() {
  if (!authStore.isLoggedIn) return

  const userLoaded = await authStore.fetchUser()
  if (!userLoaded) {
    router.replace({ path: '/login', query: { redirect: route.fullPath } })
  }
}

onMounted(() => {
  document.documentElement.classList.toggle('is-dark', isDark.value)
  void restoreSession()
})
</script>
