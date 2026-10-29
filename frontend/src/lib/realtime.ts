import { create } from 'zustand'
import { api } from './api'

/**
 * One WebSocket per tab. Authenticates with a single-use ticket fetched over the
 * authenticated REST API (the socket may live on another host where the session cookie
 * isn't sent). Reconnects with bounded exponential backoff and re-subscribes to every
 * topic the UI still cares about. See docs/realtime-protocol.md for the contract.
 */

export type ConnectionState = 'idle' | 'connecting' | 'open' | 'reconnecting'

export interface ServerMessage<T = unknown> {
  type: string
  topic?: string
  data: T
  serverTime: number
}

type Handler = (msg: ServerMessage) => void

export const useConnection = create<{ state: ConnectionState; attempt: number; skew: number }>(() => ({
  state: 'idle',
  attempt: 0,
  skew: 0,
}))

/** Server clock minus local clock, in ms. Timers use this so they match the server's deadline. */
export function serverNow() {
  return Date.now() + useConnection.getState().skew
}

const WS_BASE = (import.meta.env.VITE_WS_BASE_URL as string | undefined)?.replace(/\/$/, '')
const MAX_BACKOFF_MS = 30_000

class RealtimeClient {
  private socket: WebSocket | null = null
  private topics = new Map<string, Set<Handler>>()
  private globalHandlers = new Set<Handler>()
  private retry = 0
  private timer: ReturnType<typeof setTimeout> | null = null
  private wanted = false

  start() {
    if (this.wanted) return
    this.wanted = true
    void this.connect()
  }

  stop() {
    this.wanted = false
    if (this.timer) clearTimeout(this.timer)
    this.socket?.close(1000)
    this.socket = null
    useConnection.setState({ state: 'idle', attempt: 0 })
  }

  subscribe(topic: string, handler: Handler): () => void {
    let set = this.topics.get(topic)
    if (!set) {
      set = new Set()
      this.topics.set(topic, set)
      this.sendRaw({ type: 'SUBSCRIBE', topic })
    }
    set.add(handler)
    return () => {
      const s = this.topics.get(topic)
      if (!s) return
      s.delete(handler)
      if (s.size === 0) {
        this.topics.delete(topic)
        this.sendRaw({ type: 'UNSUBSCRIBE', topic })
      }
    }
  }

  /** Messages without a topic (invites, friend requests, errors). */
  onGlobal(handler: Handler): () => void {
    this.globalHandlers.add(handler)
    return () => this.globalHandlers.delete(handler)
  }

  send(type: string, data: unknown): boolean {
    return this.sendRaw({ type, data })
  }

  private sendRaw(msg: object): boolean {
    if (this.socket?.readyState !== WebSocket.OPEN) return false
    this.socket.send(JSON.stringify(msg))
    return true
  }

  private async connect() {
    if (!this.wanted) return
    useConnection.setState({ state: this.retry === 0 ? 'connecting' : 'reconnecting', attempt: this.retry })
    let ticket: string
    try {
      ticket = (await api.post<{ ticket: string }>('/api/auth/ws-ticket')).ticket
    } catch {
      this.scheduleReconnect()
      return
    }
    const base = WS_BASE ?? `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`
    const ws = new WebSocket(`${base}/ws?ticket=${encodeURIComponent(ticket)}`)
    this.socket = ws

    ws.onopen = () => {
      this.retry = 0
      useConnection.setState({ state: 'open', attempt: 0 })
      for (const topic of this.topics.keys()) this.sendRaw({ type: 'SUBSCRIBE', topic })
    }
    ws.onmessage = (ev) => {
      let msg: ServerMessage
      try {
        msg = JSON.parse(ev.data as string) as ServerMessage
      } catch {
        return
      }
      if (typeof msg.serverTime === 'number') {
        useConnection.setState({ skew: msg.serverTime - Date.now() })
      }
      if (msg.topic && msg.type !== 'SUBSCRIBED') {
        this.topics.get(msg.topic)?.forEach((h) => h(msg))
      } else {
        this.globalHandlers.forEach((h) => h(msg))
        if (msg.topic) this.topics.get(msg.topic)?.forEach((h) => h(msg))
      }
    }
    ws.onclose = () => {
      if (this.socket === ws) this.socket = null
      if (this.wanted) this.scheduleReconnect()
    }
    ws.onerror = () => ws.close()
  }

  private scheduleReconnect() {
    if (!this.wanted) return
    this.retry += 1
    // 1s, 2s, 4s … capped at 30s, with full jitter so many tabs don't reconnect in lockstep.
    const ceiling = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** (this.retry - 1))
    const delay = 500 + Math.random() * ceiling
    useConnection.setState({ state: 'reconnecting', attempt: this.retry })
    if (this.timer) clearTimeout(this.timer)
    this.timer = setTimeout(() => void this.connect(), delay)
  }
}

export const realtime = new RealtimeClient()
