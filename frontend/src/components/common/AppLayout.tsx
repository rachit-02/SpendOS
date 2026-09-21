import { useEffect } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { LogOut, Menu, Moon, Sun, X } from 'lucide-react'
import { AlertsBell } from './AlertsBell'
import { DemoBanner } from './DemoBanner'
import { Logo } from './Logo'
import { navItems } from './navigation'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/store/authStore'
import { resolveDark, useUiStore } from '@/store/uiStore'
import { authService } from '@/services/authService'
import { cn } from '@/utils/cn'

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Main" className="flex flex-col gap-1">
      {navItems.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
              isActive ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )
          }
        >
          <Icon className="h-4 w-4" aria-hidden="true" />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}

export function AppLayout() {
  const user = useAuthStore((state) => state.user)
  const { theme, setTheme, sidebarOpen, setSidebarOpen } = useUiStore()
  const navigate = useNavigate()
  const location = useLocation()
  const dark = resolveDark(theme)

  useEffect(() => setSidebarOpen(false), [location.pathname, setSidebarOpen])

  async function handleLogout() {
    await authService.logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-background">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-card focus:p-2">
        Skip to content
      </a>
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 hidden w-60 print:!hidden flex-col border-r bg-card p-4 lg:flex">
        <Logo className="mb-8 px-2" />
        <NavLinks />
      </aside>

      {/* Mobile drawer */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-black/40" onClick={() => setSidebarOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col bg-card p-4 shadow-xl">
            <div className="mb-8 flex items-center justify-between">
              <Logo className="px-2" />
              <Button variant="ghost" size="icon" aria-label="Close navigation" onClick={() => setSidebarOpen(false)}>
                <X className="h-5 w-5" />
              </Button>
            </div>
            <NavLinks onNavigate={() => setSidebarOpen(false)} />
          </aside>
        </div>
      )}

      <div className="lg:pl-60 print:pl-0">
        <header className="sticky top-0 z-30 print:hidden flex h-14 items-center justify-between gap-2 border-b bg-background/80 px-4 backdrop-blur">
          <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation" onClick={() => setSidebarOpen(true)}>
            <Menu className="h-5 w-5" />
          </Button>
          <div className="flex-1" />
          <AlertsBell />
          <Button
            variant="ghost"
            size="icon"
            aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
            onClick={() => setTheme(dark ? 'light' : 'dark')}
          >
            {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>
          <span className="hidden text-sm text-muted-foreground sm:inline">{user?.fullName ?? user?.email}</span>
          {/* The text label is hidden on small screens, so the button carries its own name. */}
          <Button variant="ghost" size="sm" onClick={handleLogout} aria-label="Sign out">
            <LogOut className="h-4 w-4" aria-hidden="true" />
            <span className="hidden sm:inline">Sign out</span>
          </Button>
        </header>
        <DemoBanner />
        <main id="main" className="mx-auto w-full max-w-7xl p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
