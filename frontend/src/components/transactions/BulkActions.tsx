import { useState } from 'react'
import { Download, Tag, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/input'
import { useCategories } from '@/hooks/useReferenceData'

interface Props {
  count: number
  busy?: boolean
  onRecategorize: (categoryId: string) => void
  onDelete: () => void
  onExport: () => void
  onClearSelection: () => void
}

/** Toolbar shown while rows are selected. Deleting requires a second, explicit confirmation. */
export function BulkActions({ count, busy, onRecategorize, onDelete, onExport, onClearSelection }: Props) {
  const { data: categories } = useCategories()
  const [categoryId, setCategoryId] = useState('')
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  return (
    <div role="toolbar" aria-label="Bulk actions" className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/30 bg-accent/60 p-3">
      <span className="text-sm font-medium">{count} selected</span>
      <div className="flex items-center gap-2">
        <Select aria-label="New category for selected" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className="h-8 w-44">
          <option value="">Move to category…</option>
          {categories?.map((category) => (
            <option key={category.id} value={category.id}>{category.categoryName}</option>
          ))}
        </Select>
        <Button size="sm" variant="secondary" disabled={!categoryId || busy} onClick={() => { onRecategorize(categoryId); setCategoryId('') }}>
          <Tag className="h-3.5 w-3.5" aria-hidden="true" /> Apply
        </Button>
      </div>
      <Button size="sm" variant="outline" onClick={onExport} disabled={busy}>
        <Download className="h-3.5 w-3.5" aria-hidden="true" /> Export
      </Button>
      {confirmingDelete ? (
        <span className="flex items-center gap-2">
          <span className="text-sm">Delete {count} transactions?</span>
          <Button size="sm" variant="destructive" disabled={busy} onClick={() => { onDelete(); setConfirmingDelete(false) }}>
            Yes, delete
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setConfirmingDelete(false)}>Cancel</Button>
        </span>
      ) : (
        <Button size="sm" variant="ghost" className="text-destructive" onClick={() => setConfirmingDelete(true)} disabled={busy}>
          <Trash2 className="h-3.5 w-3.5" aria-hidden="true" /> Delete
        </Button>
      )}
      <Button size="sm" variant="link" className="ml-auto" onClick={onClearSelection}>Clear selection</Button>
    </div>
  )
}
