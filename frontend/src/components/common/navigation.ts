import {
  BarChart3,
  LayoutDashboard,
  Receipt,
  Upload,
  Settings,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

/** Primary navigation. Items are added here as each feature area ships. */
export const navItems: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/transactions', label: 'Transactions', icon: Receipt },
  { to: '/analytics', label: 'Analytics', icon: BarChart3 },
  { to: '/import', label: 'Import', icon: Upload },
  { to: '/settings', label: 'Settings', icon: Settings },
]
