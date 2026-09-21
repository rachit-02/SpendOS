import { describe, expect, it } from 'vitest'
import { isPasswordAcceptable, isValidEmail, parseAmount, passwordChecks } from './validators'

describe('password validation', () => {
  it('accepts the documented valid examples', () => {
    for (const password of ['Tr0pic@lThund3r!', 'C0ff33#M0rning$', 'BlueSky@2026!']) {
      expect(isPasswordAcceptable(password)).toBe(true)
    }
  })

  it('reports each failing rule', () => {
    const failed = passwordChecks('Abc123').filter((check) => !check.passed).map((check) => check.label)
    expect(failed).toEqual(['At least 12 characters', 'Symbol'])
  })
})

describe('isValidEmail', () => {
  it('accepts normal addresses and rejects malformed ones', () => {
    expect(isValidEmail('user@example.com')).toBe(true)
    expect(isValidEmail('user@example')).toBe(false)
    expect(isValidEmail('not an email')).toBe(false)
  })
})

describe('parseAmount', () => {
  it('parses formatted amounts', () => {
    expect(parseAmount('1,250.50')).toBe(1250.5)
    expect(parseAmount('₹ 450')).toBe(450)
  })

  it('rejects zero, negatives, and more than two decimals', () => {
    expect(parseAmount('0')).toBeNull()
    expect(parseAmount('-5')).toBeNull()
    expect(parseAmount('1.234')).toBeNull()
    expect(parseAmount('abc')).toBeNull()
  })
})
