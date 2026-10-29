import { Monitor, Moon, Sun } from 'lucide-react'
import { useTheme, type ThemeMode } from '@/lib/theme'
import { cn } from '@/lib/utils'

const OPTIONS: { mode: ThemeMode; label: string; icon: typeof Sun }[] = [
  { mode: 'light', label: 'Light theme', icon: Sun },
  { mode: 'dark', label: 'Dark theme', icon: Moon },
  { mode: 'system', label: 'Match system theme', icon: Monitor },
]

/** Three-way light / dark / system switch. Compact variant shows icons only. */
export function ThemeToggle({ className, withLabels }: { className?: string; withLabels?: boolean }) {
  const { mode, setMode } = useTheme()
  return (
    <div role="radiogroup" aria-label="Colour theme"
      className={cn('inline-flex rounded-ctl border-2 border-line bg-surface-1 p-0.5', className)}>
      {OPTIONS.map(({ mode: m, label, icon: Icon }) => (
        <button key={m} type="button" role="radio" aria-checked={mode === m} aria-label={label} title={label}
          onClick={() => setMode(m)}
          className={cn('inline-flex min-h-9 min-w-9 cursor-pointer items-center justify-center gap-1.5 rounded-[4px] px-2 text-xs font-bold',
            mode === m ? 'bg-brand text-ink' : 'text-muted hover:text-text')}>
          <Icon className="size-4" aria-hidden />
          {withLabels && <span className="capitalize">{m}</span>}
        </button>
      ))}
    </div>
  )
}
