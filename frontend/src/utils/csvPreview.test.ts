import { describe, expect, it } from 'vitest'
import { detectDelimiter, previewCsv, splitCsvLine } from './csvPreview'

describe('csvPreview', () => {
  it('splits quoted fields containing delimiters and escaped quotes', () => {
    expect(splitCsvLine('05-09-2026,"ZOMATO, ONLINE","say ""hi""",450', ',')).toEqual([
      '05-09-2026',
      'ZOMATO, ONLINE',
      'say "hi"',
      '450',
    ])
  })

  it('detects semicolon and tab delimiters', () => {
    expect(detectDelimiter(['Date;Narration;Amount', '05/09/2026;UPI;450,00'])).toBe(';')
    expect(detectDelimiter(['Date\tDescription\tAmount', '2026-09-05\tNetflix\t499'])).toBe('\t')
  })

  it('previews the first rows and strips the BOM', () => {
    const preview = previewCsv(String.fromCharCode(0xfeff) + 'Date,Description,Amount\n05-09-2026,Tea,20\n\n06-09-2026,Coffee,120\n', 2)
    expect(preview.rows).toEqual([
      ['Date', 'Description', 'Amount'],
      ['05-09-2026', 'Tea', '20'],
    ])
  })
})
