<template>
  <div class="app-layout" :class="{ 'is-dark': isDark }">
    <el-container v-if="authStore.isLoggedIn">
      <el-header class="app-header">
        <h1>AI 数据分析平台</h1>
        <el-menu mode="horizontal" router :default-active="route.path">
          <el-menu-item index="/">对话</el-menu-item>
          <el-menu-item index="/knowledge-bases">知识库</el-menu-item>
        </el-menu>
        <div class="user-info">
          <el-switch v-model="isDark" @change="toggleDark" active-icon="Moon" inactive-icon="Sunny" />
          <span>{{ authStore.user?.username }}</span>
          <el-button size="small" @click="authStore.logout(); router.push('/login')">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
    <router-view v-else />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth.store'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isDark = ref(localStorage.getItem('darkMode') === 'true')

function toggleDark(val: boolean) {
  document.documentElement.classList.toggle('is-dark', val)
  localStorage.setItem('darkMode', String(val))
}

onMounted(() => {
  if (isDark.value) {
    document.documentElement.classList.add('is-dark')
  }
  if (authStore.isLoggedIn) {
    authStore.fetchUser()
  }
})
</script>

<style>
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
html.is-dark body { background: #1a1a2e; color: #e0e0e0; }
html.is-dark .app-header { background: #16213e; border-color: #0f3460; }
html.is-dark .sidebar { background: #16213e !important; border-color: #0f3460 !important; }
html.is-dark .conv-item:hover { background: #0f3460 !important; }
html.is-dark .conv-item.active { background: #1a3a5c !important; }
html.is-dark .message.assistant .message-content { background: #2a2a4a; }
html.is-dark .message.user .message-content { background: #409eff; }
html.is-dark .el-table { --el-table-bg-color: #1a1a2e; --el-table-tr-bg-color: #1a1a2e; --el-table-header-bg-color: #16213e; }
.app-header {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
}
.app-header h1 { font-size: 18px; white-space: nowrap; }
.user-info { margin-left: auto; display: flex; align-items: center; gap: 8px; }
</style>
