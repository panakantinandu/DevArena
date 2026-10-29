import { create } from 'zustand'

export type ThemeMode = 'light' | 'dark' | 'system'

const KEY = 'devarena-theme'
const media = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  ? window.matchMedia('(prefers-color-scheme: dark)')
  : null

function resolve(mode: ThemeMode): 'light' | 'dark' {
  if (mode === 'system') return media?.matches ? 'dark' : 'light'
  return mode
}

function apply(mode: ThemeMode) {
  const resolved = resolve(mode)
  const root = document.documentElement
  root.classList.toggle('dark', resolved === 'dark')
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved === 'dark' ? '#171614' : '#f4efe3')
  return resolved
}

function initial(): ThemeMode {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(KEY) : null
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
}

export const useTheme = create<{ mode: ThemeMode; resolved: 'light' | 'dark'; setMode: (m: ThemeMode) => void }>((set) => {
  const mode = initial()
  return {
    mode,
    resolved: typeof document !== 'undefined' ? apply(mode) : 'light',
    setMode: (m) => {
      localStorage.setItem(KEY, m)
      set({ mode: m, resolved: apply(m) })
    },
  }
})

// Follow the OS when the user picked "system".
media?.addEventListener('change', () => {
  const { mode } = useTheme.getState()
  if (mode === 'system') useTheme.setState({ resolved: apply('system') })
})
