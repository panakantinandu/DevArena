import { Cable, Database, Globe, HardDrive, Layers, Monitor, Network, Server, Trash2, Zap } from 'lucide-react'
import { useRef, useState } from 'react'
import type { Diagram, DiagramNode } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Button, Input } from './ui'

export const NODE_KINDS = [
  { kind: 'client', label: 'Client', icon: Monitor },
  { kind: 'lb', label: 'Load balancer', icon: Network },
  { kind: 'service', label: 'Service', icon: Server },
  { kind: 'database', label: 'Database', icon: Database },
  { kind: 'cache', label: 'Cache', icon: Zap },
  { kind: 'queue', label: 'Queue', icon: Layers },
  { kind: 'storage', label: 'Object storage', icon: HardDrive },
  { kind: 'external', label: 'External API', icon: Globe },
] as const

const W = 132
const H = 52
const KIND_COLOR: Record<string, string> = {
  // Poster fills for the kind strip (ink outlines keep them legible in both themes).
  client: '#D6D3CB', lb: '#7DD3FC', service: '#C4B5FD', database: '#86EFAC', cache: '#FFD23F', queue: '#F9A8D4',
  storage: '#93C5FD', external: '#FF8A70',
}

let seq = 0
const uid = (p: string) => `${p}${Date.now().toString(36)}${(seq++).toString(36)}`

/**
 * A small box-and-arrow editor. Drag to move, or focus a box and use the arrow keys.
 * Connect two boxes with the Connect tool. Everything is also editable from the side list,
 * so no step depends on dragging.
 */
export function DesignCanvas({ value, onChange, readOnly }: { value: Diagram; onChange?: (d: Diagram) => void; readOnly?: boolean }) {
  const [selected, setSelected] = useState<string | null>(null)
  const [connectFrom, setConnectFrom] = useState<string | null>(null)
  const [connecting, setConnecting] = useState(false)
  const drag = useRef<{ id: string; dx: number; dy: number } | null>(null)
  const svg = useRef<SVGSVGElement>(null)

  const update = (d: Diagram) => onChange?.(d)
  const node = (id: string) => value.nodes.find((n) => n.id === id)

  const addNode = (kind: string, label: string) => {
    const i = value.nodes.length
    const n: DiagramNode = { id: uid('n'), kind, label, x: 40 + (i % 5) * 160, y: 40 + Math.floor(i / 5) * 100 }
    update({ ...value, nodes: [...value.nodes, n] })
    setSelected(n.id)
  }
  const moveNode = (id: string, x: number, y: number) =>
    update({ ...value, nodes: value.nodes.map((n) => (n.id === id ? { ...n, x: Math.max(0, Math.round(x)), y: Math.max(0, Math.round(y)) } : n)) })
  const removeNode = (id: string) => {
    update({ nodes: value.nodes.filter((n) => n.id !== id), edges: value.edges.filter((e) => e.from !== id && e.to !== id) })
    setSelected(null)
  }

  const clickNode = (id: string) => {
    if (readOnly) return
    if (connecting) {
      if (!connectFrom) setConnectFrom(id)
      else if (connectFrom !== id) {
        if (!value.edges.some((e) => e.from === connectFrom && e.to === id)) {
          update({ ...value, edges: [...value.edges, { id: uid('e'), from: connectFrom, to: id }] })
        }
        setConnectFrom(null)
        setConnecting(false)
      }
    } else setSelected(id)
  }

  const toLocal = (e: React.PointerEvent) => {
    const pt = svg.current!.createSVGPoint()
    pt.x = e.clientX
    pt.y = e.clientY
    return pt.matrixTransform(svg.current!.getScreenCTM()!.inverse())
  }

  const width = Math.max(820, ...value.nodes.map((n) => n.x + W + 40))
  const height = Math.max(440, ...value.nodes.map((n) => n.y + H + 40))
  const sel = selected ? node(selected) : undefined

  return (
    <div className="grid h-full min-h-0 gap-0 lg:grid-cols-[1fr_220px]">
      <div className="grid-bg relative min-h-[360px] overflow-auto bg-bg">
        <svg ref={svg} width={width} height={height} role="img"
          aria-label={`Architecture diagram with ${value.nodes.length} components and ${value.edges.length} connections`}
          onPointerMove={(e) => { if (drag.current) { const p = toLocal(e); moveNode(drag.current.id, p.x - drag.current.dx, p.y - drag.current.dy) } }}
          onPointerUp={() => { drag.current = null }}
          onPointerLeave={() => { drag.current = null }}>
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M0,0 L10,5 L0,10 z" fill="var(--text)" />
            </marker>
          </defs>
          {value.edges.map((e) => {
            const a = node(e.from)
            const b = node(e.to)
            if (!a || !b) return null
            const [x1, y1, x2, y2] = edgePoints(a, b)
            return <line key={e.id} x1={x1} y1={y1} x2={x2} y2={y2} stroke="var(--text)" strokeWidth={2} markerEnd="url(#arrow)" />
          })}
          {value.nodes.map((n) => {
            const color = KIND_COLOR[n.kind] ?? '#D6D3CB'
            const active = n.id === selected || n.id === connectFrom
            return (
              <g key={n.id} transform={`translate(${n.x},${n.y})`} tabIndex={readOnly ? -1 : 0} role="button"
                aria-label={`${n.label} (${n.kind}). ${readOnly ? '' : 'Arrow keys move it.'}`}
                className={cn(!readOnly && 'cursor-grab focus:outline-none')}
                onPointerDown={(e) => {
                  if (readOnly || connecting) return
                  const p = toLocal(e)
                  drag.current = { id: n.id, dx: p.x - n.x, dy: p.y - n.y }
                  setSelected(n.id)
                }}
                onClick={() => clickNode(n.id)}
                onKeyDown={(e) => {
                  if (readOnly) return
                  const step = e.shiftKey ? 40 : 10
                  const d = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }[e.key]
                  if (d) { e.preventDefault(); moveNode(n.id, n.x + d[0], n.y + d[1]) }
                  if (e.key === 'Enter') clickNode(n.id)
                  if (e.key === 'Delete' || e.key === 'Backspace') removeNode(n.id)
                }}>
                <rect x={4} y={4} width={W} height={H} rx={5} fill="var(--shadow)" />
                <rect width={W} height={H} rx={5} fill="var(--surface-1)" stroke={active ? 'var(--live)' : 'var(--line)'} strokeWidth={active ? 3 : 2} />
                <rect x={1} y={1} width={10} height={H - 2} rx={4} fill={color} />
                <text x={18} y={20} fill="var(--muted)" fontWeight={700} fontSize={9} fontFamily="JetBrains Mono" style={{ textTransform: 'uppercase', letterSpacing: 1 }}>{n.kind}</text>
                <text x={18} y={38} fill="var(--text)" fontWeight={600} fontSize={13} fontFamily="Public Sans">{n.label.length > 18 ? n.label.slice(0, 17) + '…' : n.label}</text>
              </g>
            )
          })}
        </svg>
        {value.nodes.length === 0 && (
          <p className="pointer-events-none absolute inset-0 grid place-items-center text-sm text-muted">
            {readOnly ? 'Nothing drawn yet.' : 'Add components from the panel, then connect them.'}
          </p>
        )}
      </div>

      {!readOnly && (
        <aside className="space-y-4 border-t-2 border-line bg-surface-1 p-3 lg:border-l-2 lg:border-t-0">
          <div>
            <p className="eyebrow mb-2">Add</p>
            <div className="grid grid-cols-2 gap-1.5">
              {NODE_KINDS.map(({ kind, label, icon: Icon }) => (
                <button key={kind} type="button" onClick={() => addNode(kind, label)}
                  className="flex min-h-10 cursor-pointer items-center gap-1.5 rounded-ctl border-2 border-line px-2 text-left text-xs font-semibold hover:bg-brand hover:text-ink">
                  <span aria-hidden className="grid size-5 shrink-0 place-items-center rounded-[3px] border-2 border-line text-ink" style={{ background: KIND_COLOR[kind] }}><Icon className="size-3" /></span> {label}
                </button>
              ))}
            </div>
          </div>
          <Button size="sm" variant={connecting ? 'live' : 'secondary'} className="w-full" aria-pressed={connecting}
            onClick={() => { setConnecting((c) => !c); setConnectFrom(null) }}>
            <Cable className="size-3.5" aria-hidden />
            {connecting ? (connectFrom ? 'Now pick the target' : 'Pick the source') : 'Connect two boxes'}
          </Button>
          {sel && (
            <div className="space-y-2 border-t-2 border-line pt-3">
              <label htmlFor="node-label" className="eyebrow">Selected label</label>
              <Input id="node-label" value={sel.label} maxLength={60}
                onChange={(e) => update({ ...value, nodes: value.nodes.map((n) => (n.id === sel.id ? { ...n, label: e.target.value } : n)) })} />
              <Button size="sm" variant="danger" className="w-full" onClick={() => removeNode(sel.id)}>
                <Trash2 className="size-3.5" aria-hidden /> Remove
              </Button>
            </div>
          )}
          {value.edges.length > 0 && (
            <div className="border-t-2 border-line pt-3">
              <p className="eyebrow mb-1.5">Connections</p>
              <ul className="space-y-1 text-xs">
                {value.edges.map((e) => (
                  <li key={e.id} className="flex items-center justify-between gap-1">
                    <span className="truncate">{node(e.from)?.label} → {node(e.to)?.label}</span>
                    <button type="button" aria-label="Remove connection" className="cursor-pointer p-1 text-muted hover:text-danger"
                      onClick={() => update({ ...value, edges: value.edges.filter((x) => x.id !== e.id) })}>
                      <Trash2 className="size-3" />
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </aside>
      )}
    </div>
  )
}

/** Start and end points on the box borders so arrows don't hide under boxes. */
function edgePoints(a: DiagramNode, b: DiagramNode): [number, number, number, number] {
  const ax = a.x + W / 2, ay = a.y + H / 2, bx = b.x + W / 2, by = b.y + H / 2
  const clip = (cx: number, cy: number, tx: number, ty: number): [number, number] => {
    const dx = tx - cx, dy = ty - cy
    const s = Math.min(Math.abs((W / 2) / (dx || 1e-6)), Math.abs((H / 2) / (dy || 1e-6)))
    return [cx + dx * s, cy + dy * s]
  }
  const [x1, y1] = clip(ax, ay, bx, by)
  const [x2, y2] = clip(bx, by, ax, ay)
  return [x1, y1, x2, y2]
}
