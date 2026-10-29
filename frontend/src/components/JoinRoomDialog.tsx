import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button, Dialog, Field, Input } from './ui'

/** One box for both kinds of room: 6-character battle codes and 8-character interview codes. */
export function JoinRoomDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState<string>()
  const navigate = useNavigate()

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const c = code.trim().toUpperCase()
    if (/^[A-Z0-9]{6}$/.test(c)) navigate(`/arena/join/${c}`)
    else if (/^[A-Z0-9]{8}$/.test(c)) navigate(`/interviews/join/${c}`)
    else return setError('Battle codes have 6 characters, interview codes have 8.')
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="Join a room" description="Paste the code your friend shared.">
      <form onSubmit={submit} className="space-y-4">
        <Field id="room-code" label="Room code" error={error}>
          <Input
            value={code}
            onChange={(e) => { setCode(e.target.value); setError(undefined) }}
            autoFocus
            autoComplete="off"
            spellCheck={false}
            className="font-mono text-lg uppercase tracking-[0.3em]"
            maxLength={8}
          />
        </Field>
        <Button type="submit" variant="primary" className="w-full">Join</Button>
      </form>
    </Dialog>
  )
}
