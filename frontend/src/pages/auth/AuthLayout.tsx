import type { ReactNode } from 'react'
import { ThemeToggle } from '@/components/ThemeToggle'

const STATS = [
  ['90', 'day roadmap'],
  ['150+', 'curated questions'],
  ['1v1', 'live battles'],
]

function Wordmark() {
  return (
    <span className="flex items-center gap-1.5 font-display text-lg font-extrabold tracking-[-0.04em]">
      <img src="/favicon.svg" alt="" className="size-8" />
      DEV<span className="rounded-[4px] border-2 border-line bg-live px-1 text-ink">ARENA</span>
    </span>
  )
}

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      <section className="grid-bg relative hidden flex-col justify-between border-r-2 border-line p-12 lg:flex" aria-hidden>
        <Wordmark />
        <div>
          <p className="display text-[3.1rem] font-extrabold leading-[1.02]">
            Code.<br />
            <span className="bg-brand px-2 text-ink [box-decoration-break:clone]">Compete.</span><br />
            Interview.<br />
            Improve.
          </p>
          <p className="mt-6 max-w-md text-lg text-muted">
            Daily missions, live quiz battles and structured peer interviews, for two friends getting interview-ready together.
          </p>
        </div>
        <dl className="flex gap-4">
          {STATS.map(([v, l], i) => (
            <div key={l} className={i === 1 ? 'panel bg-live p-4 text-ink' : 'panel p-4'}>
              <dt className="sr-only">{l}</dt>
              <dd className="display text-2xl font-extrabold">{v}</dd>
              <dd className="mt-1 text-xs font-bold uppercase tracking-wider">{l}</dd>
            </div>
          ))}
        </dl>
      </section>
      <section className="relative flex items-center justify-center px-5 py-12">
        <ThemeToggle className="absolute right-5 top-5" />
        <div className="w-full max-w-sm">
          <span className="mb-10 inline-flex lg:hidden"><Wordmark /></span>
          <h1 className="text-[1.9rem] font-extrabold">{title}</h1>
          <p className="mt-2 text-sm text-muted">{subtitle}</p>
          <div className="panel mt-8 p-6">{children}</div>
        </div>
      </section>
    </div>
  )
}
