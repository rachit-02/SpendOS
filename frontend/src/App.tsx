import { lazy, Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { createQueryClient, routerFuture } from '@/lib/queryClient'
import { AppLayout } from '@/components/common/AppLayout'
import { ProtectedRoute, PublicOnlyRoute } from '@/components/auth/ProtectedRoute'
import { Spinner } from '@/components/ui/feedback'
import { useThemeSync } from '@/hooks/useThemeSync'

// Route-level code splitting keeps the initial bundle small (charts load only where used).
const LoginPage = lazy(() => import('@/pages/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/RegisterPage'))
const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const SettingsPage = lazy(() => import('@/pages/SettingsPage'))
const ImportPage = lazy(() => import('@/pages/ImportPage'))
const TransactionsPage = lazy(() => import('@/pages/TransactionsPage'))
const AnalyticsPage = lazy(() => import('@/pages/AnalyticsPage'))
const BudgetsPage = lazy(() => import('@/pages/BudgetsPage'))
const InsightsPage = lazy(() => import('@/pages/InsightsPage'))
const ReportsPage = lazy(() => import('@/pages/ReportsPage'))
const RecurringPage = lazy(() => import('@/pages/RecurringPage'))
const StatusPage = lazy(() => import('@/pages/StatusPage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))

export function AppRoutes() {
  useThemeSync()
  return (
    <Suspense fallback={<Spinner className="min-h-screen" />}>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/login" element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} />
        <Route path="/register" element={<PublicOnlyRoute><RegisterPage /></PublicOnlyRoute>} />
        <Route path="/status" element={<StatusPage />} />
        <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/transactions/:transactionId" element={<TransactionsPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/insights" element={<InsightsPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/budgets" element={<BudgetsPage />} />
          <Route path="/recurring" element={<RecurringPage />} />
          <Route path="/import" element={<ImportPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}

const queryClient = createQueryClient()

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter future={routerFuture}>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
