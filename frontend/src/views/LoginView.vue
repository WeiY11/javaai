<template>
  <main class="auth-page">
    <section class="auth-entry-panel">
      <div class="auth-brand">
        <span class="auth-logo">E</span>
        <div>
          <strong>EviMind</strong>
          <span>Evidence Workspace</span>
        </div>
      </div>

      <div class="entry-copy">
        <p class="eyebrow">账号入口</p>
        <h1>登录后进入证据工作台</h1>
        <p>{{ authModeSummary }}</p>
      </div>

      <div class="auth-checklist">
        <div v-for="item in authChecklist" :key="item.label" class="auth-check-item">
          <span class="status-dot" :class="{ muted: !item.ready }"></span>
          <div>
            <strong>{{ item.label }}</strong>
            <small>{{ item.detail }}</small>
          </div>
        </div>
      </div>
    </section>

    <section class="auth-card-wrapper">
      <div class="auth-card">
        <div class="auth-card-header">
          <p class="eyebrow">{{ isRegister ? '创建账号' : '后端认证' }}</p>
          <h2>{{ isRegister ? '注册 EviMind' : '登录工作台' }}</h2>
          <p class="subtitle">{{ credentialHint }}</p>
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
          <div class="auth-error-hint">{{ credentialHint }}</div>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!canSubmit"
            class="auth-submit"
            @click="handleSubmit"
          >
            {{ submitLabel }}
            <el-icon class="submit-icon"><ArrowRight /></el-icon>
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
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth.store'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const isRegister = ref(false)
const loading = ref(false)
const form = reactive({ username: '', password: '', email: '' })

const trimmedUsername = computed(() => form.username.trim())
const trimmedEmail = computed(() => form.email.trim())
const canSubmit = computed(() => trimmedUsername.value.length > 0 && form.password.trim().length > 0)

const submitLabel = computed(() => (isRegister.value ? '注册并进入' : '登录工作台'))

const safeRedirectTarget = computed(() => {
  const redirect = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect
  if (typeof redirect !== 'string') return '/'

  const isInternalPath =
    redirect.startsWith('/') && !redirect.startsWith('//') && !redirect.startsWith('/login')
  return isInternalPath ? redirect : '/'
})

const authModeSummary = computed(() =>
  isRegister.value
    ? '创建账号后进入工作台，后续知识库、文档和笔记都归属到当前用户。'
    : '使用已有账号进入工作台，继续处理知识库、问答和研究输出。'
)

const credentialHint = computed(() => {
  if (!trimmedUsername.value && !form.password.trim()) return '填写用户名和密码后才能提交。'
  if (!trimmedUsername.value) return '用户名不能为空。'
  if (!form.password.trim()) return '密码不能为空。'
  if (isRegister.value && trimmedEmail.value) return `注册邮箱：${trimmedEmail.value}`
  return isRegister.value ? '注册请求将交给后端认证接口处理。' : '凭据将交给后端认证接口校验。'
})

const authChecklist = computed(() => [
  {
    label: '工作台入口',
    detail: isRegister.value ? '注册完成后进入项目总览' : '登录完成后回到上次请求页面',
    ready: true
  },
  {
    label: '后端认证',
    detail: '账号、密码和会话令牌由后端接口处理',
    ready: true
  },
  {
    label: '凭据填写',
    detail: canSubmit.value ? '用户名和密码已填写' : '等待用户名和密码',
    ready: canSubmit.value
  }
])

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    if (isRegister.value) {
      await authStore.register(trimmedUsername.value, form.password, trimmedEmail.value || undefined)
    } else {
      await authStore.login(trimmedUsername.value, form.password)
    }
    ElMessage.success('已进入 EviMind 工作台')
    router.push(safeRedirectTarget.value)
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
  grid-template-columns: minmax(0, 1fr) 460px;
  gap: 48px;
  align-items: center;
  padding: 56px;
  background:
    linear-gradient(90deg, rgba(15, 23, 42, 0.04), transparent 45%),
    var(--bg);
}

.auth-entry-panel {
  display: grid;
  gap: 36px;
  max-width: 760px;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.auth-logo {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--primary);
  color: white;
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 26px;
}

.auth-brand div {
  display: grid;
  gap: 2px;
}

.auth-brand strong {
  color: var(--text);
  font-size: 18px;
}

.auth-brand span {
  color: var(--text-muted);
  font-size: 13px;
}

.entry-copy {
  display: grid;
  gap: 14px;
}

.entry-copy h1 {
  margin: 0;
  max-width: 700px;
  color: var(--text);
  font-family: var(--font-display);
  font-size: clamp(34px, 5vw, 56px);
  line-height: 1.08;
  letter-spacing: 0;
}

.entry-copy p:not(.eyebrow) {
  max-width: 620px;
  margin: 0;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1.8;
}

.auth-checklist {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.auth-check-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.auth-check-item strong,
.auth-check-item small {
  display: block;
  overflow-wrap: anywhere;
}

.auth-check-item strong {
  color: var(--text);
}

.auth-check-item small {
  margin-top: 3px;
  color: var(--text-muted);
  line-height: 1.5;
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

.auth-card-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 36px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-solid);
  box-shadow: var(--shadow);
}

.auth-card-header {
  margin-bottom: 28px;
}

.auth-card h2 {
  margin: 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: 30px;
  letter-spacing: 0;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-soft);
  font-size: 14px;
  line-height: 1.6;
}

.auth-form :deep(.el-form-item__label) {
  color: var(--text);
  font-weight: 600;
  padding-bottom: 6px;
}

.auth-error-hint {
  margin-top: -2px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.auth-submit {
  width: 100%;
  height: 48px;
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-sm);
  font-size: 16px;
}

.submit-icon {
  font-size: 18px;
}

.toggle-wrapper {
  margin-top: 24px;
  padding-top: 22px;
  border-top: 1px solid var(--border-soft);
  text-align: center;
}

.toggle-text {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.toggle-link {
  margin-left: 6px;
  color: var(--primary);
  font-weight: 600;
  text-decoration: none;
}

.toggle-link:hover {
  color: var(--primary-strong);
}

@media (max-width: 1100px) {
  .auth-page {
    grid-template-columns: 1fr;
    padding: 32px;
  }

  .auth-card-wrapper {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .auth-page {
    padding: 20px;
  }

  .auth-checklist {
    grid-template-columns: 1fr;
  }

  .auth-card {
    padding: 24px;
  }
}
</style>
