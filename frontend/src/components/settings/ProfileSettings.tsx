import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label } from '@/components/ui/input'
import { userService } from '@/services/userService'
import { errorMessage } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import { isValidEmail } from '@/utils/validators'

export function ProfileSettings() {
  const queryClient = useQueryClient()
  const setUser = useAuthStore((state) => state.setUser)
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: userService.me })
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string>()
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (me) {
      setFullName(me.fullName)
      setEmail(me.email)
    }
  }, [me])

  const mutation = useMutation({
    mutationFn: () => userService.updateProfile({ fullName: fullName.trim(), email: email.trim() }),
    onSuccess: (user) => {
      setUser(user)
      queryClient.setQueryData(['me'], user)
      setSaved(true)
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSaved(false)
    setError(undefined)
    if (!fullName.trim()) return setError('Name is required')
    if (!isValidEmail(email)) return setError('Enter a valid email address')
    mutation.mutate()
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Profile</CardTitle>
        <CardDescription>Your name and sign-in email.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="grid gap-4 sm:max-w-md">
          <div className="space-y-2">
            <Label htmlFor="profile-name">Full name</Label>
            <Input id="profile-name" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="profile-email">Email</Label>
            <Input id="profile-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <FieldError message={error} />
          {saved && <p role="status" className="text-sm text-success">Profile saved.</p>}
          <div>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Saving…' : 'Save profile'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
