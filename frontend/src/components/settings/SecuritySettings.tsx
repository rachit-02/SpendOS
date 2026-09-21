import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label } from '@/components/ui/input'
import { PasswordChecklist } from '@/components/auth/PasswordChecklist'
import { userService } from '@/services/userService'
import { errorMessage } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import { isPasswordAcceptable } from '@/utils/validators'

export function SecuritySettings() {
  const navigate = useNavigate()
  const clear = useAuthStore((state) => state.clear)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordError, setPasswordError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmText, setConfirmText] = useState('')
  const [deleteError, setDeleteError] = useState<string>()

  async function handleChangePassword(event: FormEvent) {
    event.preventDefault()
    setPasswordError(undefined)
    if (!isPasswordAcceptable(newPassword)) return setPasswordError('New password does not meet the requirements')
    setBusy(true)
    try {
      await userService.changePassword(currentPassword, newPassword)
      // All existing sessions are invalidated server-side; sign in again with the new password.
      clear()
      navigate('/login', { replace: true })
    } catch (error) {
      setPasswordError(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(event: FormEvent) {
    event.preventDefault()
    setDeleteError(undefined)
    setBusy(true)
    try {
      await userService.deleteAccount(confirmPassword)
      clear()
      navigate('/login', { replace: true })
    } catch (error) {
      setDeleteError(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Change password</CardTitle>
          <CardDescription>You will be signed out of all devices after changing it.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleChangePassword} className="grid gap-4 sm:max-w-md">
            <div className="space-y-2">
              <Label htmlFor="current-password">Current password</Label>
              <Input id="current-password" type="password" autoComplete="current-password" value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="new-password">New password</Label>
              <Input id="new-password" type="password" autoComplete="new-password" value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)} />
              <PasswordChecklist password={newPassword} />
            </div>
            <FieldError message={passwordError} />
            <div>
              <Button type="submit" disabled={busy || !currentPassword}>Change password</Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="text-destructive">Delete account</CardTitle>
          <CardDescription>
            Your account is deactivated immediately and all data is permanently removed within 30 days.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleDelete} className="grid gap-4 sm:max-w-md">
            <div className="space-y-2">
              <Label htmlFor="delete-password">Confirm with your password</Label>
              <Input id="delete-password" type="password" autoComplete="current-password" value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="delete-confirm">Type DELETE to confirm</Label>
              <Input id="delete-confirm" value={confirmText} onChange={(e) => setConfirmText(e.target.value)} />
            </div>
            <FieldError message={deleteError} />
            <div>
              <Button type="submit" variant="destructive" disabled={busy || confirmText !== 'DELETE' || !confirmPassword}>
                Delete my account
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
