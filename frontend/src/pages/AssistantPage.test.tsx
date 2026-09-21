import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AssistantPage from './AssistantPage'
import { assistantService } from '@/services/assistantService'
import { ApiError } from '@/services/api'
import { renderWithProviders } from '@/test/utils'
import { currentMonth } from '@/utils/months'
import type { AssistantAnswer } from '@/types/assistant'

const answer: AssistantAnswer = {
  question: 'Why did I spend more this month?',
  intent: 'spending_change',
  period: 'September 2026',
  answer: 'You spent ₹44,500 in September, ₹9,500 (+27.1%) more than ₹35,000 in August.',
  supportingData: {
    keyFindings: [{ category: 'Food', thisMonth: 8400, previousMonth: 6200, change: 2200, changePercentage: 35.5 }],
    relatedTransactions: [{ id: 't-1', merchant: 'Zomato', category: 'Food', amount: 5000, date: '2026-09-06' }],
  },
  followUpQuestions: ['How much did I spend on Food?'],
  answerSource: 'rules',
  currencyCode: 'INR',
}

describe('AssistantPage', () => {
  beforeEach(() => {
    vi.spyOn(assistantService, 'suggestions').mockResolvedValue([
      { question: 'Why did I spend more this month?', intent: 'spending_change' },
      { question: 'Which subscriptions do I have?', intent: 'subscriptions' },
    ])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('answers a suggested question with its supporting data', async () => {
    const ask = vi.spyOn(assistantService, 'ask').mockResolvedValue(answer)
    renderWithProviders(<AssistantPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Why did I spend more this month?' }))

    expect(ask).toHaveBeenCalledWith('Why did I spend more this month?', currentMonth())
    const log = screen.getByRole('log', { name: 'Conversation' })
    expect(await within(log).findByText(answer.answer)).toBeInTheDocument()
    const findings = within(log).getByRole('list', { name: 'Key findings' })
    expect(within(findings).getByText('₹8,400 vs ₹6,200 (+35.5%)')).toBeInTheDocument()
    expect(within(log).getByRole('link', { name: /Zomato/ })).toHaveAttribute('href', '/transactions/t-1')
    expect(within(log).getByText('Calculated from your transactions.')).toBeInTheDocument()
  })

  it('sends typed questions and follow-ups, and shows errors', async () => {
    const ask = vi.spyOn(assistantService, 'ask')
      .mockResolvedValueOnce(answer)
      .mockRejectedValueOnce(new ApiError('Too many requests', 'RATE_LIMITED', 429))
    renderWithProviders(<AssistantPage />)

    const input = await screen.findByLabelText('Your question')
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
    await userEvent.type(input, 'Why did I spend more?{Enter}')
    expect(ask).toHaveBeenLastCalledWith('Why did I spend more?', currentMonth())
    expect(input).toHaveValue('')

    await userEvent.click(await screen.findByRole('button', { name: 'How much did I spend on Food?' }))
    expect(ask).toHaveBeenLastCalledWith('How much did I spend on Food?', currentMonth())
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many requests')
  })
})
