import { Table, TBody, Td, Th, THead, Tr } from '@/components/ui/table'
import { formatCurrency } from '@/utils/formatters'
import { cn } from '@/utils/cn'
import type { CategoryChange } from '@/types/analytics'

/** Category-by-category comparison between two periods. More spending is shown in red. */
export function ChangeTable({ changes, currency, previousLabel, currentLabel, caption }: {
  changes: CategoryChange[]
  currency: string
  previousLabel: string
  currentLabel: string
  caption: string
}) {
  return (
    <div className="rounded-md border">
      <Table aria-label={caption}>
        <THead>
          <tr>
            <Th>Category</Th>
            <Th className="text-right">{previousLabel}</Th>
            <Th className="text-right">{currentLabel}</Th>
            <Th className="text-right">Change</Th>
          </tr>
        </THead>
        <TBody>
          {changes.map((c) => (
            <Tr key={c.categoryName}>
              <Td>{c.categoryName}</Td>
              <Td className="text-right tabular-nums text-muted-foreground">{formatCurrency(c.previous, currency)}</Td>
              <Td className="text-right tabular-nums">{formatCurrency(c.current, currency)}</Td>
              <Td className={cn('text-right tabular-nums', c.change > 0 ? 'text-destructive' : c.change < 0 ? 'text-success' : '')}>
                {c.change > 0 ? '+' : ''}
                {formatCurrency(c.change, currency)}
                <span className="ml-1 text-xs">
                  {c.changePercentage === undefined || c.changePercentage === null ? '(new)' : `(${c.changePercentage > 0 ? '+' : ''}${c.changePercentage}%)`}
                </span>
              </Td>
            </Tr>
          ))}
        </TBody>
      </Table>
    </div>
  )
}
