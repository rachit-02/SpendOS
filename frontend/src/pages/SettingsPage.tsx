import { PageHeader } from '@/components/common/PageHeader'
import { AccountsSettings } from '@/components/settings/AccountsSettings'
import { ProfileSettings } from '@/components/settings/ProfileSettings'
import { PreferencesSettings } from '@/components/settings/PreferencesSettings'
import { SecuritySettings } from '@/components/settings/SecuritySettings'

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="Manage your profile, preferences and security." />
      <div className="grid gap-6">
        <ProfileSettings />
        <AccountsSettings />
        <PreferencesSettings />
        <SecuritySettings />
      </div>
    </>
  )
}
