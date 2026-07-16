import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '../types/api.types'

type RetriableRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean }

function accessToken() {
  return localStorage.getItem('accessToken')
}

function refreshToken() {
  return localStorage.getItem('refreshToken')
}

function clearTokensAndRedirect() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  window.dispatchEvent(new Event('auth:logout'))
  if (window.location.pathname !== '/login') {
    const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
    const loginRedirectUrl = `/login?redirect=${encodeURIComponent(currentPath)}`
    window.location.href = loginRedirectUrl
  }
}

function attachAuth(config: InternalAxiosRequestConfig) {
  const token = accessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const token = refreshToken()
      if (!token) {
        throw new Error('No refresh token')
      }
      const res = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
        '/api/v1/auth/refresh',
        { refreshToken: token }
      )
      const nextAccessToken = res.data.data.accessToken
      const nextRefreshToken = res.data.data.refreshToken
      localStorage.setItem('accessToken', nextAccessToken)
      localStorage.setItem('refreshToken', nextRefreshToken)
      return nextAccessToken
    })().catch(error => {
      clearTokensAndRedirect()
      throw error
    }).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

export const rootRequest = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

function installAuthInterceptors(instance: AxiosInstance) {
  instance.interceptors.request.use(attachAuth)
  instance.interceptors.response.use(
    response => response,
    async (error: AxiosError) => {
      const originalRequest = error.config as RetriableRequestConfig | undefined
      if (error.response?.status !== 401 || !originalRequest || originalRequest._retry) {
        return Promise.reject(error)
      }

      originalRequest._retry = true
      try {
        const token = await refreshAccessToken()
        originalRequest.headers.Authorization = `Bearer ${token}`
        return instance(originalRequest)
      } catch (refreshError) {
        return Promise.reject(refreshError)
      }
    }
  )
}

installAuthInterceptors(request)
installAuthInterceptors(rootRequest)

export async function authenticatedFetch(
  input: RequestInfo | URL,
  init: RequestInit = {}
): Promise<Response> {
  return authenticatedFetchOnce(input, init, false)
}

async function authenticatedFetchOnce(
  input: RequestInfo | URL,
  init: RequestInit,
  retried: boolean
): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = accessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(input, { ...init, headers })
  if (response.status !== 401 || retried) {
    return response
  }

  const nextToken = await refreshAccessToken()
  const retryHeaders = new Headers(init.headers)
  retryHeaders.set('Authorization', `Bearer ${nextToken}`)
  return fetch(input, { ...init, headers: retryHeaders })
}

export async function get<T>(url: string, params?: Record<string, any>): Promise<T> {
  const res = await request.get<ApiResponse<T>>(url, { params })
  return res.data.data
}

export async function post<T>(url: string, data?: any): Promise<T> {
  const res = await request.post<ApiResponse<T>>(url, data)
  return res.data.data
}

export async function put<T>(url: string, data?: any): Promise<T> {
  const res = await request.put<ApiResponse<T>>(url, data)
  return res.data.data
}

export async function del<T>(url: string): Promise<T> {
  const res = await request.delete<ApiResponse<T>>(url)
  return res.data.data
}

export async function upload<T>(url: string, file: File, fields: Record<string, string>): Promise<T> {
  const formData = new FormData()
  formData.append('file', file)
  for (const [key, value] of Object.entries(fields)) {
    formData.append(key, value)
  }
  const res = await request.post<ApiResponse<T>>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data.data
}

export async function rootGet<T>(url: string, params?: Record<string, any>): Promise<T> {
  const res = await rootRequest.get<ApiResponse<T>>(url, { params })
  return res.data.data
}

export async function rootPost<T>(url: string, data?: any): Promise<T> {
  const res = await rootRequest.post<ApiResponse<T>>(url, data)
  return res.data.data
}

export default request
