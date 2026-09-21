import { api, unwrap } from './api'

export interface HealthStatus {
  status: 'UP' | 'DOWN'
  timestamp: string
  checks: Record<string, string>
}

export const healthService = {
  get: () => unwrap<HealthStatus>(api.get('/health')),
}
