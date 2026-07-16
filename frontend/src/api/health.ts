import { get } from '../utils/request'

export interface RuntimeHealthComponent {
  status: string
  required?: boolean
  message?: string
  action?: string
}

export interface RuntimeHealth {
  status: string
  timestamp?: string
  components?: Record<string, RuntimeHealthComponent>
}

export async function getRuntimeHealth(): Promise<RuntimeHealth> {
  return get('/health')
}
