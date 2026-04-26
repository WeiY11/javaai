import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '../types/auth.types'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const isLoggedIn = ref(!!localStorage.getItem('accessToken'))

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    localStorage.setItem('accessToken', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    user.value = res.userInfo
    isLoggedIn.value = true
  }

  async function register(username: string, password: string, email?: string) {
    const res = await authApi.register({ username, password, email })
    localStorage.setItem('accessToken', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    user.value = res.userInfo
    isLoggedIn.value = true
  }

  function logout() {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    user.value = null
    isLoggedIn.value = false
  }

  async function fetchUser() {
    try {
      user.value = await authApi.getCurrentUser()
    } catch {
      logout()
    }
  }

  return { user, isLoggedIn, login, register, logout, fetchUser }
})
