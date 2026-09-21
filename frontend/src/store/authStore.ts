import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { User } from '@/types/auth'

/**
 * Auth state persisted to localStorage so sessions survive reloads. Tokens in localStorage are
 * readable by injected scripts, so the app relies on the strict CSP and React's output escaping;
 * access tokens are short-lived (1h) and refresh tokens are single-use and revocable on logout.
 */
interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: User | null
  setSession: (session: { accessToken: string; refreshToken: string; user: User }) => void
  setTokens: (tokens: { accessToken: string; refreshToken: string }) => void
  setUser: (user: User) => void
  clear: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: ({ accessToken, refreshToken, user }) => set({ accessToken, refreshToken, user }),
      setTokens: ({ accessToken, refreshToken }) => set({ accessToken, refreshToken }),
      setUser: (user) => set({ user }),
      clear: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    {
      name: 'spendos-auth',
      storage: createJSONStorage(() => localStorage),
      partialize: ({ accessToken, refreshToken, user }) => ({ accessToken, refreshToken, user }),
    },
  ),
)

export const isAuthenticated = () => Boolean(useAuthStore.getState().accessToken)
