<template>
  <main class="auth-page">
    <div class="auth-background">
      <div class="glow-orb orb-1"></div>
      <div class="glow-orb orb-2"></div>
      <div class="glow-orb orb-3"></div>
      <div class="glass-overlay"></div>
    </div>

    <section class="auth-hero">
      <div class="auth-brand">
        <span class="auth-logo">E</span>
        <span class="auth-brand-text">EviMind Evidence Workspace</span>
      </div>
      <h1 class="hero-title">把企业知识库、科研文档<br/>和 <span>AI 问答</span>放在同一个工作台里。</h1>
      <p class="hero-desc">上传资料、构建知识库、追踪引用来源，并把分析结果沉淀为可复用的研究笔记。通过多模态 AI 赋能，打造属于你的顶级知识大脑。</p>
      <div class="auth-metrics">
        <div class="metric-glass"><strong>RAG</strong><span>混合检索与溯源</span></div>
        <div class="metric-glass"><strong>25+</strong><span>支持文档格式</span></div>
        <div class="metric-glass"><strong>4</strong><span>顶级模型供应商</span></div>
      </div>
    </section>

    <section class="auth-card-wrapper">
      <div class="auth-card">
        <div class="auth-card-header">
          <p class="eyebrow">{{ isRegister ? 'CREATE ACCOUNT' : 'WELCOME BACK' }}</p>
          <h2>{{ isRegister ? '注册 EviMind' : '登录工作台' }}</h2>
          <p class="subtitle">{{ isRegister ? '开始构建你的智能知识宇宙' : '输入账号密码以继续使用' }}</p>
        </div>
        <el-form :model="form" label-position="top" @submit.prevent="handleSubmit" class="auth-form">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" class="modern-input" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password size="large" class="modern-input" />
          </el-form-item>
          <el-form-item v-if="isRegister" label="邮箱">
            <el-input v-model="form.email" placeholder="可选，用于团队协作通知" size="large" class="modern-input" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="auth-submit" @click="handleSubmit">
            {{ isRegister ? '注册并进入' : '登录' }}
            <el-icon class="submit-icon" style="margin-left: 8px;"><ArrowRight /></el-icon>
          </el-button>
        </el-form>
        <div class="toggle-wrapper">
          <p class="toggle-text">
            {{ isRegister ? '已有账号？' : '还没有账号？' }}
            <a href="javascript:void(0)" class="toggle-link" @click="isRegister = !isRegister">
              {{ isRegister ? '去登录' : '创建账号' }}
            </a>
          </p>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
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
  grid-template-columns: 1fr 500px;
  position: relative;
  overflow: hidden;
  background: var(--bg);
}

.auth-background {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  z-index: 0;
  overflow: hidden;
}

.glow-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.6;
  animation: float 20s infinite ease-in-out alternate;
}

.orb-1 { top: -10%; left: -10%; width: 600px; height: 600px; background: rgba(79, 70, 229, 0.4); }
.orb-2 { bottom: -10%; right: 20%; width: 500px; height: 500px; background: rgba(13, 148, 136, 0.3); animation-delay: -5s; }
.orb-3 { top: 40%; left: 30%; width: 400px; height: 400px; background: rgba(244, 63, 94, 0.2); animation-delay: -10s; }

.glass-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(80px);
  -webkit-backdrop-filter: blur(80px);
}

html.is-dark .glass-overlay { background: rgba(9, 9, 11, 0.4); }

.auth-hero {
  position: relative;
  z-index: 1;
  padding: 80px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 60px;
}

.auth-logo {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--primary-strong), var(--evidence));
  color: white;
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 28px;
  box-shadow: var(--shadow-glow);
}

.auth-brand-text {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--text);
}

.hero-title {
  font-size: 56px;
  line-height: 1.15;
  letter-spacing: -1.5px;
  margin: 0 0 24px;
  color: var(--text);
  font-family: var(--font-display);
}

.hero-title span {
  background: linear-gradient(135deg, var(--primary), var(--evidence));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-desc {
  font-size: 18px;
  line-height: 1.8;
  color: var(--text-muted);
  max-width: 600px;
  margin: 0 0 48px;
}

.auth-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  max-width: 640px;
}

.metric-glass {
  background: var(--surface);
  backdrop-filter: blur(20px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.metric-glass:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow);
  border-color: rgba(79, 70, 229, 0.3);
}

.metric-glass strong { display: block; font-family: var(--font-display); font-size: 28px; font-weight: 800; color: var(--text); }
.metric-glass span { display: block; margin-top: 8px; font-size: 14px; color: var(--text-muted); font-weight: 500; }

.auth-card-wrapper {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  background: var(--surface-solid);
  border-radius: 24px;
  padding: 48px 40px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-soft);
}

html.is-dark .auth-card {
  background: rgba(24, 24, 27, 0.8);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.auth-card-header { margin-bottom: 32px; }

.auth-card h2 { margin: 0; font-size: 32px; letter-spacing: -1px; color: var(--text); font-family: var(--font-display); }
.subtitle { margin: 8px 0 0; color: var(--text-soft); font-size: 15px; }

.auth-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--text);
  padding-bottom: 6px;
}

.auth-submit {
  width: 100%;
  margin-top: 16px;
  height: 48px;
  font-size: 16px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.submit-icon { font-size: 18px; }

.toggle-wrapper {
  margin-top: 24px;
  text-align: center;
  padding-top: 24px;
  border-top: 1px solid var(--border-soft);
}

.toggle-text { color: var(--text-muted); font-size: 14px; }

.toggle-link {
  color: var(--primary);
  font-weight: 600;
  text-decoration: none;
  margin-left: 6px;
  transition: color 0.2s;
}

.toggle-link:hover { color: var(--primary-strong); }

@keyframes float {
  0% { transform: translate(0, 0); }
  50% { transform: translate(5%, 5%); }
  100% { transform: translate(-5%, -5%); }
}

@media (max-width: 1100px) {
  .auth-page { grid-template-columns: 1fr; }
  .auth-hero { display: none; }
  .auth-card-wrapper { min-height: 100vh; }
}
</style>
