import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImportPage from './ImportPage'
import { importService } from '@/services/importService'
import { accountService } from '@/services/accountService'
import { ApiError } from '@/services/api'
import { renderWithProviders } from '@/test/utils'
import { validateStatementFile } from '@/components/imports/csvValidation'

const account = {
  id: 'acc-1',
  accountName: 'HDFC Savings',
  accountType: 'savings' as const,
  accountNumberMasked: 'XXXX1234',
  isPrimary: true,
  isActive: true,
  currencyCode: 'INR',
  openingBalance: 0,
  createdAt: '2026-09-01T00:00:00Z',
}

const completedJob = {
  id: 'job-1',
  fileName: 'sept.csv',
  importStatus: 'completed' as const,
  importedCount: 6,
  duplicateCount: 1,
  invalidCount: 2,
  totalRowsProcessed: 9,
  totalRows: 9,
  createdAt: '2026-09-21T10:00:00Z',
}

function csvFile(content = 'Date,Description,Amount\n05-09-2026,Zomato,-450\n', name = 'sept.csv') {
  return new File([content], name, { type: 'text/csv' })
}

describe('validateStatementFile', () => {
  it('accepts csv and pdf and rejects other types, empty and oversized files', () => {
    expect(validateStatementFile(csvFile())).toBeNull()
    expect(validateStatementFile(new File(['%PDF-1.7'], 'statement.pdf', { type: 'application/pdf' }))).toBeNull()
    expect(validateStatementFile(new File(['x'], 'photo.png'))).toMatch(/Only CSV or PDF/)
    expect(validateStatementFile(new File([], 'empty.csv'))).toMatch(/empty/)
    const big = new File(['x'], 'big.pdf')
    Object.defineProperty(big, 'size', { value: 51 * 1024 * 1024 })
    expect(validateStatementFile(big)).toMatch(/limit is 50 MB/)
  })
})

describe('ImportPage', () => {
  beforeEach(() => {
    vi.spyOn(accountService, 'list').mockResolvedValue([account])
    vi.spyOn(importService, 'history').mockResolvedValue({
      items: [],
      pagination: { totalItems: 0, totalPages: 0, currentPage: 1, pageSize: 10, hasNext: false, hasPrevious: false },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uploads a file, polls progress and shows the summary with skipped rows', async () => {
    const upload = vi.spyOn(importService, 'upload').mockResolvedValue({
      importJobId: 'job-1',
      status: 'processing',
      message: 'started',
    })
    vi.spyOn(importService, 'status')
      .mockResolvedValueOnce({ importJobId: 'job-1', status: 'processing', progress: { processed: 3, total: 9, percentage: 33 } })
      .mockResolvedValue({ importJobId: 'job-1', status: 'completed', progress: { processed: 9, total: 9, percentage: 100 } })
    vi.spyOn(importService, 'get').mockResolvedValue(completedJob)
    vi.spyOn(importService, 'errors').mockResolvedValue({
      items: [{ id: 'e1', rowNumber: 8, rawData: ',Missing date,-100', errorMessage: 'Missing transaction date', errorCode: 'MISSING_DATE' }],
      pagination: { totalItems: 1, totalPages: 1, currentPage: 1, pageSize: 10, hasNext: false, hasPrevious: false },
    })

    renderWithProviders(<ImportPage />)
    await waitFor(() => expect(screen.getByLabelText('Import into account')).toHaveValue('acc-1'))

    fireEvent.change(screen.getByTestId('file-input'), { target: { files: [csvFile()] } })
    expect(await screen.findByLabelText('File preview')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Import transactions' }))
    expect(upload).toHaveBeenCalledWith(expect.any(File), 'acc-1', undefined)

    expect(await screen.findByText('Imported', {}, { timeout: 4000 })).toBeInTheDocument()
    expect(screen.getByText('6')).toBeInTheDocument()
    expect(await screen.findByText('Missing transaction date')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View transactions' })).toHaveAttribute('href', '/transactions')
  })

  it('explains duplicate file uploads', async () => {
    vi.spyOn(importService, 'upload').mockRejectedValue(new ApiError('dup', 'DUPLICATE_IMPORT', 409))

    renderWithProviders(<ImportPage />)
    fireEvent.change(await screen.findByTestId('file-input'), { target: { files: [csvFile()] } })
    await userEvent.click(screen.getByRole('button', { name: 'Import transactions' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('already been imported')
  })

  it('uploads a PDF statement without trying to preview it as CSV', async () => {
    const upload = vi.spyOn(importService, 'upload').mockResolvedValue({ importJobId: 'job-2', status: 'processing', message: 'started' })
    vi.spyOn(importService, 'status').mockResolvedValue({ importJobId: 'job-2', status: 'completed', progress: { processed: 37, total: 37, percentage: 100 } })
    vi.spyOn(importService, 'get').mockResolvedValue({ ...completedJob, id: 'job-2', fileName: 'statement.pdf' })
    vi.spyOn(importService, 'errors').mockResolvedValue({
      items: [], pagination: { totalItems: 0, totalPages: 0, currentPage: 1, pageSize: 10, hasNext: false, hasPrevious: false },
    })
    renderWithProviders(<ImportPage />)
    await waitFor(() => expect(screen.getByLabelText('Import into account')).toHaveValue('acc-1'))

    const pdf = new File(['%PDF-1.7 ...'], 'statement.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByTestId('file-input'), { target: { files: [pdf] } })

    expect(await screen.findByTestId('pdf-note')).toHaveTextContent("scanned or photographed pages can't be read yet")
    expect(screen.queryByLabelText('File preview')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Import transactions' }))
    expect(upload).toHaveBeenCalledWith(pdf, 'acc-1', undefined)
  })

  it("shows the server's explanation when a PDF can't be read", async () => {
    vi.spyOn(importService, 'upload').mockRejectedValue(new ApiError(
      "This PDF is a scanned image: it contains pictures of the pages but no selectable text. SpendOS can't read scanned statements yet (that needs OCR).",
      'PDF_SCANNED_IMAGE', 400))
    renderWithProviders(<ImportPage />)
    await waitFor(() => expect(screen.getByLabelText('Import into account')).toHaveValue('acc-1'))

    fireEvent.change(screen.getByTestId('file-input'), { target: { files: [new File(['%PDF-1.4'], 'scan.pdf')] } })
    await userEvent.click(screen.getByRole('button', { name: 'Import transactions' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('needs OCR')
  })

  it('refuses unsupported files before uploading', async () => {
    const upload = vi.spyOn(importService, 'upload')
    renderWithProviders(<ImportPage />)

    fireEvent.change(await screen.findByTestId('file-input'), { target: { files: [new File(['x'], 'photo.png')] } })

    expect(await screen.findByRole('alert')).toHaveTextContent('Only CSV or PDF')
    expect(screen.getByRole('button', { name: 'Import transactions' })).toBeDisabled()
    expect(upload).not.toHaveBeenCalled()
  })
})
