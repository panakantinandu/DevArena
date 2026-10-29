import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function pct(v: number | null | undefined, digits = 0) {
  if (v == null) return '—'
  return `${(v * 100).toFixed(digits)}%`
}

export function relativeTime(iso: string | null | undefined) {
  if (!iso) return ''
  const diff = (Date.now() - new Date(iso).getTime()) / 1000
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  if (Math.abs(diff) < 60) return rtf.format(-Math.round(diff), 'second')
  if (Math.abs(diff) < 3600) return rtf.format(-Math.round(diff / 60), 'minute')
  if (Math.abs(diff) < 86400) return rtf.format(-Math.round(diff / 3600), 'hour')
  return rtf.format(-Math.round(diff / 86400), 'day')
}

export function formatClock(ms: number) {
  const total = Math.max(0, Math.ceil(ms / 1000))
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function shortDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

// Filled poster tags: ink text on fixed fills reads in both themes (contrast > 9:1).
export const DIFFICULTY_STYLE: Record<string, string> = {
  EASY: 'bg-[#86efac] text-ink',
  MEDIUM: 'bg-[#ffd23f] text-ink',
  HARD: 'bg-[#ff8a70] text-ink',
}
