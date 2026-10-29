import { Check, X } from 'lucide-react'
import { useId } from 'react'
import type { QuestionView } from '@/lib/types'
import { cn } from '@/lib/utils'
import { DifficultyTag } from './ui'

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F']

/**
 * Multiple-choice question as an accessible radio group. When `correctOptionId` is
 * known (after answering) options show correct and wrong states with icons, not colour alone.
 */
export function QuestionCard({
  question, selected, onSelect, correctOptionId, disabled, header, compact,
}: {
  question: QuestionView
  selected: string | null
  onSelect?: (optionId: string) => void
  correctOptionId?: string | null
  disabled?: boolean
  header?: React.ReactNode
  compact?: boolean
}) {
  const name = useId()
  const revealed = !!correctOptionId
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        {header}
        <span className="eyebrow">{question.topic.name}</span>
        <DifficultyTag value={question.difficulty} />
      </div>
      <p id={`${name}-prompt`} className={cn('font-medium leading-relaxed', compact ? 'text-base' : 'text-lg')}>
        {question.prompt}
      </p>
      {question.codeSnippet && (
        <pre className="mt-3 overflow-x-auto rounded-ctl border-2 border-line bg-bg p-3 font-mono text-sm leading-6">
          <code>{question.codeSnippet}</code>
        </pre>
      )}
      <div role="radiogroup" aria-labelledby={`${name}-prompt`} className="mt-5 space-y-2">
        {question.options.map((o, i) => {
          const isSel = selected === o.id
          const isCorrect = revealed && o.id === correctOptionId
          const isWrong = revealed && isSel && !isCorrect
          return (
            <button
              key={o.id}
              type="button"
              role="radio"
              aria-checked={isSel}
              disabled={disabled || revealed}
              onClick={() => onSelect?.(o.id)}
              onKeyDown={(e) => {
                const k = e.key.toUpperCase()
                const idx = LETTERS.indexOf(k)
                if (idx >= 0 && idx < question.options.length && !disabled && !revealed) onSelect?.(question.options[idx].id)
              }}
              className={cn(
                'flex w-full cursor-pointer items-start gap-3 rounded-ctl border-2 border-line px-3 py-3 text-left text-sm font-medium',
                'transition-[transform,box-shadow] duration-100 disabled:cursor-default',
                !revealed && !isSel && 'bg-surface-1 hover:-translate-y-px hover:shadow-[3px_3px_0_0_var(--shadow)]',
                !revealed && isSel && 'bg-brand text-ink shadow-[3px_3px_0_0_var(--shadow)]',
                isCorrect && 'bg-[#86efac] text-ink shadow-[3px_3px_0_0_var(--shadow)]',
                isWrong && 'bg-[#ff8a70] text-ink',
                revealed && !isCorrect && !isWrong && 'bg-surface-1 opacity-55',
              )}
            >
              <span
                className={cn(
                  'grid size-6 shrink-0 place-items-center rounded-[4px] border-2 font-mono text-xs font-bold',
                  (isSel || isCorrect || isWrong) ? 'border-ink bg-surface-1 text-ink' : 'border-line text-text',
                )}
                aria-hidden
              >
                {isCorrect ? <Check className="size-3.5" /> : isWrong ? <X className="size-3.5" /> : LETTERS[i]}
              </span>
              <span className="pt-0.5 leading-relaxed">{o.body}</span>
              {isCorrect && <span className="sr-only">(correct answer)</span>}
              {isWrong && <span className="sr-only">(your answer, incorrect)</span>}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function Explanation({ correct, text }: { correct: boolean; text: string }) {
  return (
    <div className={cn('mt-5 rounded-ctl border-2 border-line bg-surface-1 px-4 py-3 text-sm leading-relaxed',
      correct ? 'shadow-[4px_4px_0_0_var(--success)]' : 'shadow-[4px_4px_0_0_var(--danger)]')}>
      <p className={cn('mb-1 font-mono text-2xs font-bold uppercase tracking-wider', correct ? 'text-success' : 'text-danger')}>
        {correct ? 'Correct' : 'Not quite'}
      </p>
      {text}
    </div>
  )
}
