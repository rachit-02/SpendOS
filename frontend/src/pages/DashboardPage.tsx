import { PageHeader } from '@/components/common/PageHeader'
import { EmptyState } from '@/components/ui/feedback'
import { useAuthStore } from '@/store/authStore'

export default function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  return (
    <>
      <PageHeader title={`Welcome${user?.fullName ? `, ${user.fullName.split(' ')[0]}` : ''}`} description="Your financial overview" />
      <EmptyState title="No data yet" description="Import a statement or add transactions to see your dashboard." />
    </>
  )
}
