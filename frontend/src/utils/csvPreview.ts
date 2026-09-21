/**
 * Lightweight client-side preview of the first rows of a CSV so users can check the file before
 * uploading. The server does the real parsing; this only needs to be good enough to eyeball.
 */
export interface CsvPreview {
  delimiter: string
  rows: string[][]
}

const DELIMITERS = [',', ';', '\t', '|']

export function splitCsvLine(line: string, delimiter: string): string[] {
  const cells: string[] = []
  let current = ''
  let quoted = false
  for (let i = 0; i < line.length; i++) {
    const char = line[i]
    if (char === '"') {
      if (quoted && line[i + 1] === '"') {
        current += '"'
        i++
      } else {
        quoted = !quoted
      }
    } else if (char === delimiter && !quoted) {
      cells.push(current.trim())
      current = ''
    } else {
      current += char
    }
  }
  cells.push(current.trim())
  return cells
}

export function detectDelimiter(lines: string[]): string {
  let best = ','
  let bestScore = -1
  for (const delimiter of DELIMITERS) {
    const counts = lines.map((line) => splitCsvLine(line, delimiter).length - 1)
    const max = Math.max(0, ...counts)
    if (max === 0) continue
    const consistent = counts.filter((count) => count === max).length / counts.length
    const score = max * consistent
    if (score > bestScore) {
      bestScore = score
      best = delimiter
    }
  }
  return best
}

/** Removes a leading UTF-8 byte-order mark (U+FEFF), which some bank exports include. */
function stripBom(text: string): string {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text
}

export function previewCsv(text: string, maxRows = 6): CsvPreview {
  const lines = stripBom(text).split(/\r?\n/).filter((line) => line.trim() !== '').slice(0, maxRows)
  const delimiter = detectDelimiter(lines)
  return { delimiter, rows: lines.map((line) => splitCsvLine(line, delimiter)) }
}

function readBlobText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsText(blob)
  })
}

export async function readFilePreview(file: File, maxRows = 6): Promise<CsvPreview> {
  // Only the first 64 KB are needed for a preview, even for 50 MB files.
  const text = await readBlobText(file.slice(0, 64 * 1024))
  return previewCsv(text, maxRows)
}
