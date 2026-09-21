import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SecuritySettings } from './SecuritySettings'
import { userService } from '@/services/userService'
import { renderWithProviders } from '@/test/utils'

describe('SecuritySettings', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('downloads the data export', async () => {
    const exportData = vi.spyOn(userService, 'exportData').mockResolvedValue()
    renderWithProviders(<SecuritySettings />)

    await userEvent.click(screen.getByRole('button', { name: /Download my data/ }))

    expect(exportData).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('status')).toHaveTextContent('Your download has started.')
  })

  it('only deletes the account after the password and typed confirmation', async () => {
    const remove = vi.spyOn(userService, 'deleteAccount').mockResolvedValue({ message: 'ok' })
    renderWithProviders(<SecuritySettings />)
    const button = screen.getByRole('button', { name: 'Delete my account' })

    await userEvent.type(screen.getByLabelText('Confirm with your password'), 'Tr0pic@lThund3r!')
    expect(button).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'delete')
    expect(button).toBeDisabled()
    await userEvent.clear(screen.getByLabelText('Type DELETE to confirm'))
    await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'DELETE')
    await userEvent.click(button)

    expect(remove).toHaveBeenCalledWith('Tr0pic@lThund3r!')
  })
})
