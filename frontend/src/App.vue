<template>
  <div class="app-layout">
    <el-container v-if="authStore.isLoggedIn">
      <el-header class="app-header">
        <h1>AI 数据分析平台</h1>
        <el-menu mode="horizontal" router :default-active="route.path">
          <el-menu-item index="/">对话</el-menu-item>
          <el-menu-item index="/knowledge-bases">知识库</el-menu-item>
        </el-menu>
        <div class="user-info">
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
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth.store'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

onMounted(() => {
  if (authStore.isLoggedIn) {
    authStore.fetchUser()
  }
})
</script>

<style>
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
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
