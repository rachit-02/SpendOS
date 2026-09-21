export interface User {
  userId: string
  email: string
  fullName: string
  emailVerified?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

export interface RefreshResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface Preferences {
  currencyCode: string
  timezone: string
  fiscalYearStartMonth: number
  theme: 'light' | 'dark' | 'system'
  language: string
  financialHealthScoreEnabled: boolean
  demoMode: boolean
  emailReportsEnabled: boolean
  emailAlertsEnabled: boolean
}
