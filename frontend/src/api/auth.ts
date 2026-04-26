import type { AuthResponse, LoginRequest, RegisterRequest, RefreshTokenRequest, UserInfo } from '../types/auth.types'
import { get, post } from '../utils/request'

export async function login(data: LoginRequest): Promise<AuthResponse> {
  return post('/auth/login', data)
}

export async function register(data: RegisterRequest): Promise<AuthResponse> {
  return post('/auth/register', data)
}

export async function refreshToken(data: RefreshTokenRequest): Promise<AuthResponse> {
  return post('/auth/refresh', data)
}

export async function getCurrentUser(): Promise<UserInfo> {
  return get('/auth/me')
}
