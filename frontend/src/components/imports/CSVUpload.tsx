import { useRef, useState, type DragEvent } from 'react'
import { FileSpreadsheet, UploadCloud } from 'lucide-react'
import { cn } from '@/utils/cn'
import { formatBytes } from '@/utils/formatters'
import { validateCsvFile } from './csvValidation'

interface Props {
  file: File | null
  onFileSelected: (file: File | null) => void
  disabled?: boolean
}

/** Drag-and-drop or click-to-browse file picker, validating type and size before upload. */
export function CSVUpload({ file, onFileSelected, disabled }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function accept(candidate: File | undefined) {
    if (!candidate) return
    const problem = validateCsvFile(candidate)
    setError(problem)
    onFileSelected(problem ? null : candidate)
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragging(false)
    if (!disabled) accept(event.dataTransfer.files?.[0])
  }

  return (
    <div className="space-y-2">
      <div
        role="button"
        tabIndex={0}
        aria-disabled={disabled}
        aria-describedby="upload-help"
        onClick={() => !disabled && inputRef.current?.click()}
        onKeyDown={(event) => {
          if ((event.key === 'Enter' || event.key === ' ') && !disabled) {
            event.preventDefault()
            inputRef.current?.click()
          }
        }}
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex cursor-pointer flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed p-8 text-center transition-colors',
          dragging ? 'border-primary bg-accent' : 'border-input hover:border-primary/60 hover:bg-muted/40',
          disabled && 'cursor-not-allowed opacity-60',
        )}
      >
        {file ? (
          <>
            <FileSpreadsheet className="h-10 w-10 text-primary" aria-hidden="true" />
            <div>
              <p className="font-medium">{file.name}</p>
              <p className="text-sm text-muted-foreground">{formatBytes(file.size)} · click to choose another file</p>
            </div>
          </>
        ) : (
          <>
            <UploadCloud className="h-10 w-10 text-muted-foreground" aria-hidden="true" />
            <div>
              <p className="font-medium">Drop your statement here, or click to browse</p>
              <p id="upload-help" className="text-sm text-muted-foreground">CSV exported from your bank or card · up to 50 MB</p>
            </div>
          </>
        )}
        <input
          ref={inputRef}
          type="file"
          accept=".csv,.txt,text/csv"
          className="sr-only"
          aria-label="Statement file"
          data-testid="file-input"
          onChange={(event) => accept(event.target.files?.[0])}
          disabled={disabled}
        />
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
