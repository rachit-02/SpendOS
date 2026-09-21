import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

/** Renders children only for signed-in users; otherwise redirects to /login, remembering the target. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const location = useLocation()
  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <>{children}</>
}

/** Keeps signed-in users away from the login/register pages. */
export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const accessToken = useAuthStore((state) => state.accessToken)
  if (accessToken) {
    return <Navigate to="/dashboard" replace />
  }
  return <>{children}</>
}
