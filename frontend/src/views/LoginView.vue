<template>
  <main class="auth-page">
    <section class="auth-hero">
      <div class="auth-brand">
        <span class="auth-logo">E</span>
        <span>EviMind Evidence Workspace</span>
      </div>
      <h1>把企业知识库、科研文档和 AI 问答放在同一个证据工作台里。</h1>
      <p>上传资料、构建知识库、追踪引用来源，并把分析结果沉淀为可复用的研究笔记。</p>
      <div class="auth-metrics">
        <div><strong>RAG</strong><span>混合检索</span></div>
        <div><strong>25+</strong><span>文档格式</span></div>
        <div><strong>4</strong><span>模型供应商</span></div>
      </div>
    </section>

    <section class="auth-card">
      <div class="auth-card-header">
        <p class="eyebrow">{{ isRegister ? '创建账号' : '欢迎回来' }}</p>
        <h2>{{ isRegister ? '注册 EviMind' : '登录工作台' }}</h2>
      </div>
      <el-form :model="form" label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password size="large" />
        </el-form-item>
        <el-form-item v-if="isRegister" label="邮箱">
          <el-input v-model="form.email" placeholder="可选，用于团队协作通知" size="large" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="auth-submit" @click="handleSubmit">
          {{ isRegister ? '注册并进入' : '登录' }}
        </el-button>
      </el-form>
      <p class="toggle-text">
        {{ isRegister ? '已有账号？' : '还没有账号？' }}
        <el-link type="primary" @click="isRegister = !isRegister">
          {{ isRegister ? '去登录' : '创建账号' }}
        </el-link>
      </p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth.store'

const router = useRouter()
const authStore = useAuthStore()
const isRegister = ref(false)
const loading = ref(false)
const form = reactive({ username: '', password: '', email: '' })

async function handleSubmit() {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    if (isRegister.value) {
      await authStore.register(form.username.trim(), form.password, form.email.trim() || undefined)
    } else {
      await authStore.login(form.username.trim(), form.password)
    }
    ElMessage.success('已进入 EviMind 工作台')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '认证失败，请检查账号信息')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 40px;
  align-items: center;
  padding: 48px;
  background:
    linear-gradient(120deg, rgba(37, 99, 235, 0.12), rgba(20, 184, 166, 0.12)),
    var(--bg);
}

.auth-hero {
  max-width: 760px;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text);
  font-weight: 800;
}

.auth-logo {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563eb, #14b8a6);
  color: white;
}

.auth-hero h1 {
  margin: 32px 0 16px;
  color: var(--text);
  font-size: 46px;
  line-height: 1.08;
  letter-spacing: 0;
}

.auth-hero p {
  max-width: 620px;
  margin: 0;
  color: var(--text-muted);
  font-size: 17px;
  line-height: 1.7;
}

.auth-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 130px));
  gap: 12px;
  margin-top: 32px;
}

.auth-metrics div {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.68);
}

.auth-metrics strong,
.auth-metrics span {
  display: block;
}

.auth-metrics strong {
  color: var(--text);
  font-size: 22px;
}

.auth-metrics span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.auth-card {
  padding: 28px;
}

.auth-card-header {
  margin-bottom: 22px;
}

.auth-card h2 {
  margin: 0;
  color: var(--text);
  font-size: 26px;
}

.auth-submit {
  width: 100%;
}

.toggle-text {
  margin: 18px 0 0;
  text-align: center;
  color: var(--text-muted);
}

@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: 1fr;
    padding: 24px;
  }
  .auth-hero h1 {
    font-size: 34px;
  }
}
</style>
