export interface MonthValue {
  month: number
  year: number
}

export function shiftMonth({ month, year }: MonthValue, delta: number): MonthValue {
  const index = year * 12 + (month - 1) + delta
  return { month: (index % 12) + 1, year: Math.floor(index / 12) }
}

export function currentMonth(): MonthValue {
  const now = new Date()
  return { month: now.getMonth() + 1, year: now.getFullYear() }
}

export function monthLabel({ month, year }: MonthValue): string {
  return new Date(year, month - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

/** First and last day (YYYY-MM-DD) of a month, for transaction filters. */
export function monthRange({ month, year }: MonthValue): { startDate: string; endDate: string } {
  const pad = (n: number) => String(n).padStart(2, '0')
  const lastDay = new Date(year, month, 0).getDate()
  return { startDate: `${year}-${pad(month)}-01`, endDate: `${year}-${pad(month)}-${pad(lastDay)}` }
}
