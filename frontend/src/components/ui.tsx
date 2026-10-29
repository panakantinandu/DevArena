import { cva, type VariantProps } from 'class-variance-authority'
import { AlertTriangle, Loader2, RefreshCw } from 'lucide-react'
import { Dialog as D } from 'radix-ui'
import * as React from 'react'
import { ApiError, errorMessage } from '@/lib/api'
import { cn, DIFFICULTY_STYLE } from '@/lib/utils'

// ---------------------------------------------------------------- Button

// Neubrutalist button: ink outline + hard shadow that "presses" flat on click (skill: Neubrutalism).
const pressable =
  'rounded-ctl border-2 border-line shadow-[3px_3px_0_0_var(--shadow)] ' +
  'hover:-translate-x-px hover:-translate-y-px hover:shadow-[4px_4px_0_0_var(--shadow)] ' +
  'active:translate-x-[3px] active:translate-y-[3px] active:shadow-none ' +
  'disabled:translate-x-0 disabled:translate-y-0 disabled:shadow-[3px_3px_0_0_var(--shadow)]'

const buttonStyles = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap font-bold select-none cursor-pointer ' +
    'transition-[transform,box-shadow,background-color] duration-100 disabled:cursor-not-allowed disabled:opacity-55 min-h-11 text-sm',
  {
    variants: {
      variant: {
        primary: `${pressable} bg-brand text-ink hover:bg-brand-hover px-5 uppercase tracking-wide`,
        secondary: `${pressable} bg-surface-1 text-text hover:bg-surface-2 px-4`,
        ghost: 'rounded-ctl text-muted hover:text-text hover:bg-surface-2 px-3 font-semibold',
        danger: `${pressable} bg-surface-1 text-danger hover:bg-danger hover:text-white px-4`,
        live: `${pressable} bg-live text-ink hover:brightness-105 px-5 uppercase tracking-wide`,
      },
      size: {
        md: '',
        sm: 'min-h-9 px-3 text-xs',
        icon: 'size-11 p-0',
      },
    },
    defaultVariants: { variant: 'secondary', size: 'md' },
  },
)

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonStyles> {
  loading?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, children, type = 'button', ...props }, ref) => (
    <button
      ref={ref}
      type={type}
      className={cn(buttonStyles({ variant, size }), className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  ),
)
Button.displayName = 'Button'

// ---------------------------------------------------------------- Form fields

const fieldBase =
  'w-full rounded-ctl border-2 border-line bg-surface-1 px-3 text-sm text-text ' +
  'placeholder:text-muted/80 focus:outline-none focus:shadow-[3px_3px_0_0_var(--brand)] ' +
  'aria-[invalid=true]:border-danger aria-[invalid=true]:shadow-[3px_3px_0_0_var(--danger)] disabled:opacity-60'

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => <input ref={ref} className={cn(fieldBase, 'h-11', className)} {...props} />,
)
Input.displayName = 'Input'

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea ref={ref} className={cn(fieldBase, 'min-h-28 py-2.5 leading-relaxed', className)} {...props} />
  ),
)
Textarea.displayName = 'Textarea'

export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, children, ...props }, ref) => (
    <select ref={ref} className={cn(fieldBase, 'h-11 pr-8 cursor-pointer', className)} {...props}>
      {children}
    </select>
  ),
)
Select.displayName = 'Select'

/** Label + control + hint/error, wired with ids for screen readers. */
export function Field({
  label, hint, error, children, id,
}: { label: string; hint?: string; error?: string; children: React.ReactElement<Record<string, unknown>>; id: string }) {
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ') || undefined
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-text">
        {label}
      </label>
      {React.cloneElement(children, { id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {hint && !error && (
        <p id={`${id}-hint`} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- Small display pieces

export function Badge({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-[4px] border-2 border-line bg-surface-1 px-1.5 py-px font-mono text-2xs font-bold uppercase tracking-wider text-text',
        className,
      )}
    >
      {children}
    </span>
  )
}

export function DifficultyTag({ value }: { value: string }) {
  return <Badge className={DIFFICULTY_STYLE[value]}>{value.toLowerCase()}</Badge>
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded-ctl bg-surface-2', className)} />
}

export function Meter({ value, className, tone = 'brand', label }: {
  value: number; className?: string; tone?: 'brand' | 'live' | 'success' | 'warning'; label: string
}) {
  const clamped = Math.max(0, Math.min(1, value))
  const bar = { brand: 'bg-brand', live: 'bg-live', success: 'bg-[#86efac]', warning: 'bg-brand' }[tone]
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(clamped * 100)}
      className={cn('h-3 w-full overflow-hidden rounded-full border-2 border-line bg-surface-1', className)}
    >
      <div className={cn('h-full border-r-2 border-line transition-[width] duration-300', bar, clamped === 0 && 'border-r-0')} style={{ width: `${clamped * 100}%` }} />
    </div>
  )
}

export function Avatar({ name, url, size = 32 }: { name: string; url?: string | null; size?: number }) {
  const initials = name.split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase()
  if (url) {
    return <img src={url} alt="" width={size} height={size} className="rounded-full border-2 border-line object-cover" style={{ width: size, height: size }} />
  }
  // Deterministic hue from the name so each person keeps their colour.
  const hue = [...name].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7)
  return (
    <span
      aria-hidden
      className="inline-grid shrink-0 place-items-center rounded-full border-2 border-line font-mono text-[0.68rem] font-bold text-ink"
      style={{ width: size, height: size, background: `hsl(${hue} 85% 72%)` }}
    >
      {initials}
    </span>
  )
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2 text-sm text-muted">
      <Loader2 className="size-4 animate-spin" aria-hidden /> {label}
    </span>
  )
}

// ---------------------------------------------------------------- States

export function EmptyState({ icon: Icon, title, body, action }: {
  icon: React.ComponentType<{ className?: string }>; title: string; body?: string; action?: React.ReactNode
}) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-panel border-2 border-dashed border-line bg-surface-1 px-6 py-8">
      <Icon className="size-5 text-brand-text" />
      <div>
        <p className="text-lg font-extrabold">{title}</p>
        {body && <p className="mt-1 max-w-prose text-sm text-muted">{body}</p>}
      </div>
      {action}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const network = error instanceof ApiError && error.isNetwork
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-panel border-2 border-danger bg-surface-1 px-5 py-5 shadow-[4px_4px_0_0_var(--danger)]">
      <div className="flex items-center gap-2 text-danger">
        <AlertTriangle className="size-4" aria-hidden />
        <span className="font-medium">{network ? 'Server unavailable' : "Couldn't load this"}</span>
      </div>
      <p className="text-sm text-muted">{errorMessage(error)}</p>
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          <RefreshCw className="size-3.5" aria-hidden /> Try again
        </Button>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- Page header

export function PageHeader({ eyebrow, title, description, actions }: {
  eyebrow?: string; title: string; description?: string; actions?: React.ReactNode
}) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && <p className="eyebrow mb-1.5">{eyebrow}</p>}
        <h1 className="text-[1.75rem] font-semibold">{title}</h1>
        {description && <p className="mt-1 max-w-2xl text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

// ---------------------------------------------------------------- Dialog

export function Dialog({ open, onOpenChange, title, description, children, wide }: {
  open: boolean; onOpenChange: (o: boolean) => void; title: string; description?: string; children: React.ReactNode
  wide?: boolean
}) {
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-50 bg-black/60" />
        <D.Content
          className={cn(
            'panel fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto p-6',
            wide ? 'max-w-3xl' : 'max-w-lg',
          )}
        >
          <D.Title className="text-xl font-extrabold">{title}</D.Title>
          {description ? (
            <D.Description className="mt-1 text-sm text-muted">{description}</D.Description>
          ) : (
            <D.Description className="sr-only">{title}</D.Description>
          )}
          <div className="mt-5">{children}</div>
        </D.Content>
      </D.Portal>
    </D.Root>
  )
}
