import { Link } from 'react-router-dom'
import { formatCurrencyRounded, formatDate, formatPercent } from '@/utils/formatters'
import type { AssistantAnswer, AssistantFinding } from '@/types/assistant'

function findingText(finding: AssistantFinding, currency: string): string {
  const money = (value?: number) => formatCurrencyRounded(value, currency)
  if (finding.thisMonth !== undefined) {
    const change = finding.change ?? 0
    const sign = change > 0 ? '+' : ''
    const percent = finding.changePercentage !== undefined ? ` (${sign}${formatPercent(finding.changePercentage)})` : ''
    return `${money(finding.thisMonth)} vs ${money(finding.previousMonth)}${percent}`
  }
  const parts = [money(finding.amount)]
  if (finding.frequency) parts.push(finding.frequency)
  if (finding.percentage !== undefined) parts.push(formatPercent(finding.percentage))
  if (finding.count !== undefined) parts.push(`${finding.count} transactions`)
  return parts.join(' · ')
}

/** The assistant's answer with the figures and transactions it is based on. */
export function AnswerCard({ answer, onFollowUp }: { answer: AssistantAnswer; onFollowUp: (question: string) => void }) {
  const { keyFindings, relatedTransactions } = answer.supportingData
  const currency = answer.currencyCode
  return (
    <div className="space-y-3">
      <p className="whitespace-pre-line text-sm leading-relaxed">{answer.answer}</p>

      {keyFindings.length > 0 && (
        <details className="rounded-md border bg-background/60 p-2 text-sm" open={keyFindings.length <= 3}>
          <summary className="cursor-pointer text-xs font-medium text-muted-foreground">Figures behind this answer</summary>
          <ul className="mt-2 space-y-1" aria-label="Key findings">
            {keyFindings.map((finding, index) => (
              <li key={index} className="flex justify-between gap-3">
                <span>{finding.category ?? finding.merchant}</span>
                <span className="text-right tabular-nums text-muted-foreground">{findingText(finding, currency)}</span>
              </li>
            ))}
          </ul>
        </details>
      )}

      {relatedTransactions.length > 0 && (
        <details className="rounded-md border bg-background/60 p-2 text-sm">
          <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
            Related transactions ({relatedTransactions.length})
          </summary>
          <ul className="mt-2 space-y-1" aria-label="Related transactions">
            {relatedTransactions.map((transaction) => (
              <li key={transaction.id} className="flex justify-between gap-3">
                <Link to={`/transactions/${transaction.id}`} className="truncate hover:underline">
                  {transaction.merchant ?? transaction.description ?? transaction.category}
                  <span className="ml-2 text-xs text-muted-foreground">{formatDate(transaction.date, 'd MMM')}</span>
                </Link>
                <span className="tabular-nums">{formatCurrencyRounded(transaction.amount, currency)}</span>
              </li>
            ))}
          </ul>
        </details>
      )}

      {answer.followUpQuestions.length > 0 && (
        <div className="flex flex-wrap gap-2" aria-label="Follow-up questions" role="group">
          {answer.followUpQuestions.map((question) => (
            <button key={question} type="button" onClick={() => onFollowUp(question)}
              className="rounded-full border bg-card px-3 py-1 text-xs hover:bg-muted">
              {question}
            </button>
          ))}
        </div>
      )}
      <p className="text-[11px] text-muted-foreground">
        {answer.answerSource === 'llm'
          ? 'Worded by AI; every number was checked against your data.'
          : 'Calculated from your transactions.'}
      </p>
    </div>
  )
}
