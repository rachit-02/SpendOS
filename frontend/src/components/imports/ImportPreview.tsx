import { useEffect, useState } from 'react'
import { readFilePreview, type CsvPreview } from '@/utils/csvPreview'
import { Table, TBody, Td, Th, THead, Tr } from '@/components/ui/table'

const delimiterNames: Record<string, string> = { ',': 'comma', ';': 'semicolon', '\t': 'tab', '|': 'pipe' }

/** Shows the first rows of the selected file so the user can confirm it is the right statement. */
export function ImportPreview({ file }: { file: File }) {
  const [preview, setPreview] = useState<CsvPreview | null>(null)

  useEffect(() => {
    let cancelled = false
    readFilePreview(file).then((result) => {
      if (!cancelled) setPreview(result)
    })
    return () => {
      cancelled = true
    }
  }, [file])

  if (!preview || preview.rows.length === 0) return null
  const [header, ...rows] = preview.rows

  return (
    <div className="space-y-2">
      <p className="text-xs text-muted-foreground">
        Preview · {delimiterNames[preview.delimiter] ?? preview.delimiter}-separated · first {rows.length} rows
      </p>
      <div className="rounded-md border">
        <Table aria-label="File preview">
          <THead>
            <tr>
              {header.map((cell, index) => (
                <Th key={index}>{cell || `Column ${index + 1}`}</Th>
              ))}
            </tr>
          </THead>
          <TBody>
            {rows.map((row, rowIndex) => (
              <Tr key={rowIndex}>
                {header.map((_, cellIndex) => (
                  <Td key={cellIndex} className="max-w-[16rem] truncate whitespace-nowrap">
                    {row[cellIndex] ?? ''}
                  </Td>
                ))}
              </Tr>
            ))}
          </TBody>
        </Table>
      </div>
    </div>
  )
}
