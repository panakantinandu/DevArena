import Editor, { loader, type OnMount } from '@monaco-editor/react'
// Core editor plus syntax for the languages a user can pick; not the full 4 MB bundle.
import * as monaco from 'monaco-editor/editor/editor.api'
import 'monaco-editor/languages/definitions/java/register'
import 'monaco-editor/languages/definitions/python/register'
import 'monaco-editor/languages/definitions/javascript/register'
import 'monaco-editor/languages/definitions/typescript/register'
import 'monaco-editor/languages/definitions/go/register'
import 'monaco-editor/languages/definitions/cpp/register'
import 'monaco-editor/languages/definitions/kotlin/register'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker'
import { Minus, Plus } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTheme } from '@/lib/theme'
import { Button, Spinner } from './ui'

// Bundle Monaco locally instead of pulling it from a CDN at runtime.
;(self as unknown as { MonacoEnvironment: unknown }).MonacoEnvironment = { getWorker: () => new EditorWorker() }
loader.config({ monaco })

let themed = false
function defineTheme(m: typeof monaco) {
  if (themed) return
  themed = true
  // Paper and charcoal editor themes that match the app (keywords in ink blue / signal yellow).
  m.editor.defineTheme('devarena-light', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: '1D4ED8', fontStyle: 'bold' },
      { token: 'string', foreground: '166534' },
      { token: 'number', foreground: 'B42D14' },
      { token: 'comment', foreground: '776F62', fontStyle: 'italic' },
      { token: 'type', foreground: '7C2D12' },
    ],
    colors: {
      'editor.background': '#FFFDF7',
      'editor.foreground': '#111111',
      'editor.lineHighlightBackground': '#F4EFE3',
      'editorLineNumber.foreground': '#A39C8C',
      'editorLineNumber.activeForeground': '#111111',
      'editor.selectionBackground': '#FFD23F99',
      'editorCursor.foreground': '#111111',
    },
  })
  m.editor.defineTheme('devarena-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: 'FFD23F', fontStyle: 'bold' },
      { token: 'string', foreground: '86EFAC' },
      { token: 'number', foreground: 'FF8A6B' },
      { token: 'comment', foreground: '8C8576', fontStyle: 'italic' },
      { token: 'type', foreground: 'FFB4A2' },
    ],
    colors: {
      'editor.background': '#1C1B18',
      'editor.foreground': '#F4EFE3',
      'editor.lineHighlightBackground': '#262420',
      'editorLineNumber.foreground': '#6E685C',
      'editorLineNumber.activeForeground': '#F4EFE3',
      'editor.selectionBackground': '#FFD23F55',
      'editorCursor.foreground': '#FFD23F',
    },
  })
}

export interface CodeChange { offset: number; length: number; text: string }

export const LANGUAGE_IDS: Record<string, string> = {
  JAVA: 'java', PYTHON: 'python', JAVASCRIPT: 'javascript', TYPESCRIPT: 'typescript', GO: 'go', CPP: 'cpp', KOTLIN: 'kotlin',
}

/**
 * Monaco with DevArena's theme and font-size controls. `onChanges` reports Monaco's
 * incremental edits (sorted by descending offset so they can be applied in order),
 * used for live interview sync instead of shipping the whole document each keystroke.
 */
export function CodeEditor({
  value, language = 'JAVA', readOnly, onChange, onChanges, height = '100%', editorRef,
}: {
  value: string
  language?: string
  readOnly?: boolean
  onChange?: (v: string) => void
  onChanges?: (changes: CodeChange[], fullText: string) => void
  height?: string
  editorRef?: React.MutableRefObject<monaco.editor.IStandaloneCodeEditor | null>
}) {
  const editorTheme = useTheme((t) => (t.resolved === 'dark' ? 'devarena-dark' : 'devarena-light'))
  const [fontSize, setFontSize] = useState(() => Number(localStorage.getItem('editor-font') ?? 14))
  const inner = useRef<monaco.editor.IStandaloneCodeEditor | null>(null)
  const applyingRemote = useRef(false)

  useEffect(() => localStorage.setItem('editor-font', String(fontSize)), [fontSize])
  useEffect(() => inner.current?.updateOptions({ fontSize }), [fontSize])

  // Read-only mirrors receive new values from outside; apply them without losing scroll position.
  useEffect(() => {
    const ed = inner.current
    if (!ed || ed.getValue() === value) return
    applyingRemote.current = true
    const model = ed.getModel()
    if (model) model.pushEditOperations([], [{ range: model.getFullModelRange(), text: value }], () => null)
    applyingRemote.current = false
  }, [value])

  const onMount: OnMount = (ed) => {
    inner.current = ed
    if (editorRef) editorRef.current = ed
    ed.onDidChangeModelContent((e) => {
      if (applyingRemote.current) return
      const text = ed.getValue()
      onChange?.(text)
      if (onChanges) {
        const changes = [...e.changes]
          .sort((a, b) => b.rangeOffset - a.rangeOffset)
          .map((c) => ({ offset: c.rangeOffset, length: c.rangeLength, text: c.text }))
        onChanges(changes, text)
      }
    })
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex items-center justify-end gap-1 border-b-2 border-line bg-surface-2 px-2 py-1">
        <span className="mr-auto font-mono text-2xs uppercase tracking-wider text-muted">
          {language.toLowerCase()}{readOnly ? ' · read-only' : ''}
        </span>
        <Button size="sm" variant="ghost" aria-label="Smaller font" onClick={() => setFontSize((s) => Math.max(11, s - 1))}><Minus className="size-3.5" /></Button>
        <span className="num w-6 text-center font-mono text-2xs text-muted">{fontSize}</span>
        <Button size="sm" variant="ghost" aria-label="Larger font" onClick={() => setFontSize((s) => Math.min(24, s + 1))}><Plus className="size-3.5" /></Button>
      </div>
      <div className="min-h-0 flex-1">
        <Editor
          height={height}
          defaultLanguage={LANGUAGE_IDS[language] ?? 'java'}
          language={LANGUAGE_IDS[language] ?? 'java'}
          defaultValue={value}
          theme={editorTheme}
          beforeMount={defineTheme}
          onMount={onMount}
          loading={<Spinner label="Loading editor" />}
          options={{
            readOnly,
            fontSize,
            fontFamily: '"JetBrains Mono", monospace',
            fontLigatures: false,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            tabSize: 4,
            autoIndent: 'full',
            formatOnPaste: true,
            renderLineHighlight: 'line',
            smoothScrolling: false,
            padding: { top: 12 },
            automaticLayout: true,
          }}
        />
      </div>
    </div>
  )
}
