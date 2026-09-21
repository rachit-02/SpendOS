import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label } from '@/components/ui/input'
import { PasswordChecklist } from './PasswordChecklist'
import { authService } from '@/services/authService'
import { toApiError } from '@/services/api'
import { isPasswordAcceptable, isValidEmail } from '@/utils/validators'

type Errors = { fullName?: string; email?: string; password?: string }

export function RegisterForm() {
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const next: Errors = {}
    if (!fullName.trim()) next.fullName = 'Enter your name'
    if (!isValidEmail(email)) next.email = 'Enter a valid email address'
    if (!isPasswordAcceptable(password)) next.password = 'Password does not meet the requirements'
    setErrors(next)
    if (Object.keys(next).length > 0) return

    setSubmitting(true)
    setFormError(null)
    try {
      await authService.register({ fullName: fullName.trim(), email: email.trim(), password })
      await authService.login(email.trim(), password)
      navigate('/dashboard', { replace: true })
    } catch (error) {
      const apiError = toApiError(error)
      if (apiError.code === 'DUPLICATE_EMAIL') setErrors({ email: 'An account with this email already exists' })
      else if (apiError.code === 'WEAK_PASSWORD') setErrors({ password: apiError.message })
      else setFormError(apiError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {formError && (
        <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
          {formError}
        </div>
      )}
      <div className="space-y-2">
        <Label htmlFor="fullName">Full name</Label>
        <Input id="fullName" autoComplete="name" value={fullName} onChange={(e) => setFullName(e.target.value)}
          aria-invalid={Boolean(errors.fullName)} aria-describedby="fullName-error" />
        <FieldError id="fullName-error" message={errors.fullName} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="email">Email</Label>
        <Input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
          aria-invalid={Boolean(errors.email)} aria-describedby="email-error" />
        <FieldError id="email-error" message={errors.email} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="password">Password</Label>
        <Input id="password" type="password" autoComplete="new-password" value={password}
          onChange={(e) => setPassword(e.target.value)} aria-invalid={Boolean(errors.password)}
          aria-describedby="password-error" />
        <PasswordChecklist password={password} />
        <FieldError id="password-error" message={errors.password} />
      </div>
      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? 'Creating account…' : 'Create account'}
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </form>
  )
}
