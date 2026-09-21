/** Client-side mirror of the backend PasswordPolicy, used for instant feedback only. */
export interface PasswordCheck {
  label: string
  passed: boolean
}

export function passwordChecks(password: string): PasswordCheck[] {
  return [
    { label: 'At least 12 characters', passed: password.length >= 12 },
    { label: 'Uppercase letter', passed: /[A-Z]/.test(password) },
    { label: 'Lowercase letter', passed: /[a-z]/.test(password) },
    { label: 'Number', passed: /\d/.test(password) },
    { label: 'Symbol', passed: /[^A-Za-z0-9]/.test(password) },
  ]
}

export function isPasswordAcceptable(password: string): boolean {
  return passwordChecks(password).every((check) => check.passed)
}

export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
}

/** Parses a user-entered amount ("1,250.50") into a number, or null if invalid/non-positive. */
export function parseAmount(value: string): number | null {
  const normalized = value.replace(/[,\s₹$]/g, '')
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null
  const amount = Number(normalized)
  return amount > 0 ? amount : null
}
