export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userInfo: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  email: string
  systemRole: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}
