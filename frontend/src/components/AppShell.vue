<template>
  <div class="workspace-shell" :class="{ 'nav-open': navOpen }">
    <aside class="nav-rail" aria-label="主导航">
      <div class="brand-mark">
        <span class="brand-icon">E</span>
        <span class="brand-text">EviMind</span>
      </div>

      <nav class="rail-links">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          class="rail-link"
          :to="item.path"
          :title="item.label"
          @click="navOpen = false"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <div class="workspace-frame">
      <header class="workspace-topbar">
        <button class="mobile-menu" type="button" aria-label="打开导航" @click="navOpen = !navOpen">
          <el-icon><Menu /></el-icon>
        </button>
        <div>
          <p class="eyebrow">{{ activeMeta.eyebrow }}</p>
          <h1>{{ activeMeta.title }}</h1>
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
  Files,
  Menu,
  Notebook,
  Reading,
  TrendCharts
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth.store'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const navOpen = ref(false)
const isDark = ref(localStorage.getItem('darkMode') === 'true')

const navItems = [
  { path: '/', label: '智能问答', icon: ChatDotRound, title: '智能问答', eyebrow: '证据化 RAG 对话' },
  { path: '/knowledge-bases', label: '知识库', icon: Collection, title: '知识库管理', eyebrow: '权限、切片与证据阈值' },
  { path: '/documents', label: '文档', icon: Files, title: '文档入库', eyebrow: '上传、入库状态与切片' },
  { path: '/analysis', label: '文件分析', icon: TrendCharts, title: '文件分析', eyebrow: '批量分析与报告导出' },
  { path: '/citations', label: '引用导出', icon: Reading, title: '引用导出', eyebrow: 'BibTeX 与 APA' },
  { path: '/notes', label: '科研笔记', icon: Notebook, title: '科研笔记', eyebrow: '切片批注与研究记录' }
]

const activeMeta = computed(() => navItems.find(item => item.path === route.path) || navItems[0])

function toggleDark(val: string | number | boolean) {
  const enabled = Boolean(val)
  document.documentElement.classList.toggle('is-dark', enabled)
  localStorage.setItem('darkMode', String(enabled))
}

function logout() {
  authStore.logout()
  router.push('/login')
}

onMounted(() => {
  document.documentElement.classList.toggle('is-dark', isDark.value)
  if (authStore.isLoggedIn) authStore.fetchUser()
})
</script>
