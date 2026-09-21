import { api, unwrap } from './api'
import type { Preferences, User } from '@/types/auth'

export const userService = {
  me: () => unwrap<User>(api.get('/users/me')),
  updateProfile: (input: { fullName?: string; email?: string }) => unwrap<User>(api.put('/users/me', input)),
  changePassword: (currentPassword: string, newPassword: string) =>
    unwrap<{ message: string }>(api.post('/users/me/change-password', { currentPassword, newPassword })),
  deleteAccount: (confirmPassword: string) =>
    unwrap<{ message: string }>(api.delete('/users/me', { data: { confirmPassword } })),
  preferences: () => unwrap<Preferences>(api.get('/users/me/preferences')),
  updatePreferences: (input: Partial<Preferences>) => unwrap<Preferences>(api.put('/users/me/preferences', input)),
}
