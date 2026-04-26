<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2>{{ isRegister ? '注册' : '登录' }}</h2>
      <el-form :model="form" @submit.prevent="handleSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item v-if="isRegister" label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱（可选）" />
        </el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="loading" style="width:100%">
          {{ isRegister ? '注册' : '登录' }}
        </el-button>
      </el-form>
      <p class="toggle-text">
        {{ isRegister ? '已有账号？' : '没有账号？' }}
        <el-link type="primary" @click="isRegister = !isRegister">
          {{ isRegister ? '去登录' : '去注册' }}
        </el-link>
      </p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.store'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const isRegister = ref(false)
const loading = ref(false)
const form = reactive({ username: '', password: '', email: '' })

async function handleSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    if (isRegister.value) {
      await authStore.register(form.username, form.password, form.email)
    } else {
      await authStore.login(form.username, form.password)
    }
    ElMessage.success('操作成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f0f2f5;
}
.login-card {
  width: 400px;
  padding: 20px;
}
.toggle-text {
  text-align: center;
  margin-top: 16px;
}
</style>
