import {
  BarChart3,
  FileText,
  Flag,
  LineChart,
  LayoutDashboard,
  Lightbulb,
  PiggyBank,
  Repeat,
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
  { to: '/insights', label: 'Insights', icon: Lightbulb },
  { to: '/budgets', label: 'Budgets', icon: PiggyBank },
  { to: '/planning', label: 'Planning', icon: LineChart },
  { to: '/goals', label: 'Goals', icon: Flag },
  { to: '/reports', label: 'Reports', icon: FileText },
  { to: '/recurring', label: 'Recurring', icon: Repeat },
  { to: '/import', label: 'Import', icon: Upload },
  { to: '/settings', label: 'Settings', icon: Settings },
]
