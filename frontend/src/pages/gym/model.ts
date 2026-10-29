import type { PublicUser } from '@/lib/types'

export type GymMode = 'ESTIMATION' | 'INCIDENT' | 'REVIEW' | 'TRADEOFF'

export const MODE_INFO: Record<GymMode, { label: string; skill: string; blurb: string }> = {
  ESTIMATION: { label: 'Estimation sprint', skill: 'Estimation', blurb: 'Size systems in your head: a best guess plus an honest range.' },
  INCIDENT: { label: 'Incident detective', skill: 'Debugging', blurb: 'Find the root cause. Every clue costs points, so stop when the evidence is enough.' },
  REVIEW: { label: 'Spot the flaw', skill: 'Code review', blurb: 'AI wrote it, it compiles. Click the lines that will hurt you in production.' },
  TRADEOFF: { label: 'Trade-off arena', skill: 'Judgment', blurb: 'Pick the design that fits the constraints and name the risk you accept.' },
}

export interface Option { id: string; text?: string; label?: string; description?: string }

export interface AnswerView {
  response: Record<string, unknown>; score: number; correct: boolean; confidence: number
  solution: Record<string, any>; detail: Record<string, any>; explanation: string
}

export interface PuzzleView {
  id: string; position: number; mode: GymMode; difficulty: string; title: string
  payload: Record<string, any>; revealedClues: Record<string, string>; answer: AnswerView | null
}

export interface RunView {
  id: string; kind: 'DAILY' | 'PRACTICE'; mode: GymMode | null; runDate: string
  status: 'IN_PROGRESS' | 'COMPLETED'; totalScore: number | null; puzzles: PuzzleView[]
}

export interface AnswerResult { puzzle: PuzzleView; runStatus: string; totalScore: number | null; xpAwarded: number }

export interface GymOverview {
  today: string
  daily: { id: string; status: string; totalScore: number | null } | null
  puzzlesPerMode: Record<GymMode, number>
  skills: { key: string; label: string; score: number | null; samples: number }[]
  calibration: { label: string; from: number; to: number; answers: number; accuracy: number | null }[]
  calibrationScore: number | null
  answeredTotal: number
  dailyBoard: { user: PublicUser; status: string; score: number | null; you: boolean }[]
  recent: { id: string; kind: string; mode: GymMode | null; runDate: string; status: string; totalScore: number | null; createdAt: string }[]
}

const SUFFIX: Record<string, number> = { k: 1e3, m: 1e6, b: 1e9, bn: 1e9, t: 1e12 }

/**
 * Parses friendly numbers: "23000", "23,000", "2.3e4", "23k", "1.5M", "2bn", "55T".
 * Returns null when it isn't a positive finite number.
 */
export function parseAmount(raw: string): number | null {
  const s = raw.trim().toLowerCase().replace(/[,_\s]/g, '')
  if (!s) return null
  const m = /^(\d+(?:\.\d+)?(?:e[+-]?\d+)?)(k|m|bn|b|t)?$/.exec(s)
  if (!m) return null
  const v = Number(m[1]) * (m[2] ? SUFFIX[m[2]] : 1)
  return Number.isFinite(v) && v > 0 ? v : null
}

const SUP: Record<string, string> = { '-': '⁻', 0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹' }

/** 23148 → "2.3 × 10⁴" (for the "= …" hint next to inputs and in feedback). */
export function sci(v: number): string {
  if (!Number.isFinite(v) || v <= 0) return '—'
  const exp = Math.floor(Math.log10(v))
  if (exp >= -2 && exp < 4) return v.toLocaleString(undefined, { maximumSignificantDigits: 3 })
  const mant = v / 10 ** exp
  return `${mant.toFixed(1)} × 10${String(exp).split('').map((c) => SUP[c]).join('')}`
}
