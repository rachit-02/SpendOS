import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Bot, Send } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { MonthSelector } from '@/components/dashboard/MonthSelector'
import { AnswerCard } from '@/components/assistant/AnswerCard'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { assistantService } from '@/services/assistantService'
import { errorMessage } from '@/services/api'
import { currentMonth, type MonthValue } from '@/utils/months'
import type { AssistantAnswer } from '@/types/assistant'

type NewMessage =
  | { role: 'user'; text: string }
  | { role: 'assistant'; answer: AssistantAnswer }
  | { role: 'error'; text: string }
type Message = NewMessage & { id: number }

const MAX_LENGTH = 500

export default function AssistantPage() {
  const [month, setMonth] = useState<MonthValue>(currentMonth)
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const nextId = useRef(0)
  const endRef = useRef<HTMLDivElement>(null)
  const suggestions = useQuery({ queryKey: ['assistant-suggestions'], queryFn: assistantService.suggestions })

  const append = (message: NewMessage) =>
    setMessages((current) => [...current, { ...message, id: nextId.current++ }])

  const ask = useMutation({
    mutationFn: (question: string) => assistantService.ask(question, month),
    onSuccess: (answer) => append({ role: 'assistant', answer }),
    onError: (error) => append({ role: 'error', text: errorMessage(error) }),
  })

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' })
  }, [messages, ask.isPending])

  function send(question: string) {
    const text = question.trim()
    if (!text || ask.isPending) return
    append({ role: 'user', text })
    setDraft('')
    ask.mutate(text)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    send(draft)
  }

  return (
    <div className="flex h-[calc(100dvh-8rem)] min-h-[28rem] flex-col">
      <PageHeader
        title="Assistant"
        description="Ask about your spending. Answers are calculated from your own transactions."
        actions={<MonthSelector value={month} onChange={setMonth} />}
      />

      <div className="flex-1 space-y-4 overflow-y-auto rounded-lg border bg-card p-3 sm:p-4" role="log" aria-label="Conversation" aria-live="polite">
        {messages.length === 0 && (
          <div className="flex flex-col items-center gap-4 py-8 text-center">
            <Bot className="h-10 w-10 text-primary" aria-hidden="true" />
            <p className="max-w-md text-sm text-muted-foreground">
              Try asking why your spending changed, where your money went, or what your subscriptions cost.
              &quot;This month&quot; means the month selected above.
            </p>
            <div className="flex max-w-xl flex-wrap justify-center gap-2" role="group" aria-label="Suggested questions">
              {suggestions.data?.map((suggestion) => (
                <button key={suggestion.question} type="button" onClick={() => send(suggestion.question)}
                  className="rounded-full border bg-background px-3 py-1.5 text-sm hover:bg-muted">
                  {suggestion.question}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((message) => {
          if (message.role === 'user') {
            return (
              <div key={message.id} className="flex justify-end">
                <p className="max-w-[85%] rounded-2xl rounded-br-sm bg-primary px-3 py-2 text-sm text-primary-foreground">
                  {message.text}
                </p>
              </div>
            )
          }
          if (message.role === 'error') {
            return <p key={message.id} role="alert" className="text-sm text-destructive">{message.text}</p>
          }
          return (
            <div key={message.id} className="max-w-[95%] rounded-2xl rounded-bl-sm bg-muted/60 px-3 py-2 sm:max-w-[85%]">
              <AnswerCard answer={message.answer} onFollowUp={send} />
            </div>
          )
        })}
        {ask.isPending && <p className="text-sm text-muted-foreground" aria-label="Assistant is thinking">Working it out…</p>}
        <div ref={endRef} />
      </div>

      <form onSubmit={submit} className="mt-3 flex gap-2" aria-label="Ask the assistant">
        <Input aria-label="Your question" placeholder="Why did I spend more this month?" value={draft}
          maxLength={MAX_LENGTH} onChange={(e) => setDraft(e.target.value)} autoComplete="off" />
        <Button type="submit" disabled={!draft.trim() || ask.isPending} aria-label="Send">
          <Send className="h-4 w-4" />
        </Button>
      </form>
    </div>
  )
}
