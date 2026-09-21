import { api, unwrap } from './api'
import type { MonthValue } from '@/utils/months'
import type { AssistantAnswer, AssistantSuggestion } from '@/types/assistant'

export const assistantService = {
  ask: (question: string, { month, year }: MonthValue) =>
    unwrap<AssistantAnswer>(api.post('/assistant/query', {
      question,
      context: { selectedMonth: String(month), selectedYear: year },
    })),
  suggestions: () => unwrap<AssistantSuggestion[]>(api.get('/assistant/suggestions')),
}
