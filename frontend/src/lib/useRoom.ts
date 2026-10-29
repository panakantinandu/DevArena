import { useEffect, useRef } from 'react'
import { realtime, type ServerMessage } from './realtime'

/**
 * Subscribes to a room topic for the lifetime of the component. `onResync` runs after every
 * (re)subscription so the page can refetch authoritative state it may have missed while offline.
 */
export function useRoom(topic: string | null, onMessage: (m: ServerMessage) => void, onResync?: () => void) {
  const handler = useRef(onMessage)
  const resync = useRef(onResync)
  handler.current = onMessage
  resync.current = onResync

  useEffect(() => {
    if (!topic) return
    return realtime.subscribe(topic, (m) => {
      if (m.type === 'SUBSCRIBED') resync.current?.()
      else handler.current(m)
    })
  }, [topic])
}
