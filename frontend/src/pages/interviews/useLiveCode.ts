import { useCallback, useEffect, useRef, useState } from 'react'
import type { CodeChange } from '@/components/CodeEditor'
import { api } from '@/lib/api'
import { realtime } from '@/lib/realtime'
import type { CodeSnapshot } from '@/lib/types'
import { useRoom } from '@/lib/useRoom'

interface UpdateMsg { sessionId: string; version: number; baseVersion: number; changes: CodeChange[] }

function apply(text: string, changes: CodeChange[]) {
  // Changes arrive sorted by descending offset, so applying in order is safe.
  let out = text
  for (const c of changes) out = out.slice(0, c.offset) + c.text + out.slice(c.offset + c.length)
  return out
}

/**
 * Candidate side: sends incremental edits tagged with the version they were made on.
 * If the server rejects a stale update, or the socket was down, it sends the full text once.
 * A local draft is kept in localStorage so a refresh or crash never loses code.
 */
export function useCandidateCode(sessionId: string, enabled: boolean) {
  const draftKey = `iv-code-${sessionId}`
  const [initial, setInitial] = useState<string | null>(null)
  const version = useRef(0)
  const text = useRef('')
  const needsReplace = useRef(false)

  const replace = useCallback(() => {
    if (realtime.send('CODE_REPLACE', { sessionId, code: text.current, language: 'JAVA' })) needsReplace.current = false
    else needsReplace.current = true
  }, [sessionId])

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    void api.get<CodeSnapshot>(`/api/interviews/${sessionId}/code`).then((snap) => {
      if (cancelled) return
      const draft = localStorage.getItem(draftKey)
      version.current = snap.version
      // Prefer the local draft: the candidate is the only writer, so it's the newest copy.
      const start = draft != null && draft !== snap.code ? draft : snap.code
      text.current = start
      setInitial(start)
      if (start !== snap.code) needsReplace.current = true
    })
    return () => { cancelled = true }
  }, [enabled, sessionId, draftKey])

  useRoom(enabled ? `interview:${sessionId}` : null, (m) => {
    // Our own edits echo back; never let an older echo move the version backwards.
    if (m.type === 'CODE_UPDATED') version.current = Math.max(version.current, (m.data as UpdateMsg).version)
    if (m.type === 'CODE_SNAPSHOT') version.current = (m.data as CodeSnapshot).version
  }, () => { if (needsReplace.current) replace() })

  useEffect(() => {
    if (!enabled) return
    return realtime.onGlobal((m) => {
      if (m.type === 'CODE_REJECTED') replace()
    })
  }, [enabled, replace])

  const onChanges = useCallback((changes: CodeChange[], full: string) => {
    text.current = full
    localStorage.setItem(draftKey, full)
    if (needsReplace.current) return replace()
    const sent = realtime.send('CODE_UPDATE', { sessionId, baseVersion: version.current, changes })
    if (sent) version.current += 1
    else needsReplace.current = true
  }, [sessionId, draftKey, replace])

  return { initial, onChanges, currentText: () => text.current, clearDraft: () => localStorage.removeItem(draftKey) }
}

/** Interviewer side: a read-only mirror that applies deltas in version order and resyncs on gaps. */
export function useMirroredCode(sessionId: string, enabled: boolean) {
  const [code, setCode] = useState('')
  const version = useRef(-1)

  const resync = useCallback(() => {
    void api.get<CodeSnapshot>(`/api/interviews/${sessionId}/code`).then((snap) => {
      version.current = snap.version
      setCode(snap.code)
    })
  }, [sessionId])

  useEffect(() => { if (enabled) resync() }, [enabled, resync])

  useRoom(enabled ? `interview:${sessionId}` : null, (m) => {
    if (m.type === 'CODE_SNAPSHOT') {
      const s = m.data as CodeSnapshot
      version.current = s.version
      setCode(s.code)
    } else if (m.type === 'CODE_UPDATED') {
      const u = m.data as UpdateMsg
      if (u.baseVersion === version.current) {
        version.current = u.version
        setCode((c) => apply(c, u.changes))
      } else if (u.version > version.current) {
        resync() // missed or out-of-order message
      }
    }
  }, resync)

  return code
}
