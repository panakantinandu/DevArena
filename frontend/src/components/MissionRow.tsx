import { BookOpenText, Check, ChevronRight, Network, RotateCcw, Target } from 'lucide-react'
import { Link } from 'react-router'
import type { MissionCard } from '@/lib/types'
import { cn } from '@/lib/utils'
import { DifficultyTag } from './ui'

const KIND_ICON = { QUESTION_SET: Target, INTERVIEW_STUDY: BookOpenText, DESIGN_STUDY: Network }

/** One mission as a row in a vertical checklist timeline. */
export function MissionRow({ m, last }: { m: MissionCard; last?: boolean }) {
  const Icon = m.revision ? RotateCcw : KIND_ICON[m.kind]
  return (
    <li className="relative flex gap-4">
      {/* timeline rail */}
      <div className="flex flex-col items-center">
        <span
          className={cn(
            'grid size-9 shrink-0 place-items-center rounded-full border-2',
            m.completed ? 'border-line bg-[#86efac] text-ink' : 'border-line bg-surface-1 text-text',
          )}
        >
          {m.completed ? <Check className="size-4" aria-hidden /> : <Icon className="size-4" aria-hidden />}
        </span>
        {!last && <span aria-hidden className="w-0.5 flex-1 bg-line" />}
      </div>
      <Link
        to={`/missions/${m.id}`}
        className="group mb-3 flex min-h-16 flex-1 items-center justify-between gap-3 rounded-panel border-2 border-line bg-surface-1 px-4 py-3 transition-[transform,box-shadow] duration-100 hover:-translate-x-px hover:-translate-y-px hover:shadow-[4px_4px_0_0_var(--shadow)]"
      >
        <div className="min-w-0">
          <p className="eyebrow">{m.revision ? 'Revision' : m.categoryLabel}</p>
          <p className={cn('truncate font-bold', m.completed && 'text-muted line-through decoration-line-strong')}>
            {m.title}
          </p>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
            <DifficultyTag value={m.difficulty} />
            <span className="num font-mono">{m.estimatedMinutes} min</span>
            <span className="num rounded-[4px] border-2 border-line bg-brand px-1 font-mono font-bold text-ink">+{m.xpReward} xp{m.kind === 'QUESTION_SET' ? ' max' : ''}</span>
            {m.completed && m.totalCount ? (
              <span className="num font-mono text-success">{m.correctCount}/{m.totalCount} correct</span>
            ) : null}
          </div>
        </div>
        <span className="flex shrink-0 items-center gap-1 text-sm font-bold group-hover:text-brand-text">
          <span className="hidden sm:inline">{m.completed ? 'Review' : 'Start'}</span>
          <ChevronRight className="size-4" aria-hidden />
        </span>
      </Link>
    </li>
  )
}
