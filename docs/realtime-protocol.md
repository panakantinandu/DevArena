# Real-time protocol

There is one WebSocket endpoint: `GET /ws?ticket=<single-use ticket>`.

Get a ticket with `POST /api/auth/ws-ticket` (authenticated, CSRF-protected). It is valid for 30 seconds and opens exactly one connection. The handshake is rejected with 401 if the ticket is missing, used or expired, and with 403 if the `Origin` isn't allowlisted.

## Envelope

Client → server:

```json
{ "type": "SUBSCRIBE", "topic": "battle:5f0c…" }
{ "type": "CODE_UPDATE", "data": { … } }
```

Server → client:

```json
{ "type": "QUESTION_STARTED", "topic": "battle:5f0c…", "data": { … }, "serverTime": 1793281000000 }
```

`serverTime` (epoch ms) lets clients compute clock skew, so countdowns match server deadlines. Messages over 64 KB are rejected. Malformed JSON gets an `ERROR` reply and never closes the socket.

## Built-in client messages

| type | fields | effect |
| --- | --- | --- |
| `PING` | – | replies `PONG` |
| `SUBSCRIBE` | `topic` | authorizes, then replies `SUBSCRIBED` (clients refetch state on this) or `ERROR {code: SUBSCRIBE_DENIED}` |
| `UNSUBSCRIBE` | `topic` | stop receiving the topic |

## Topics and authorization

| Topic | Who may subscribe |
| --- | --- |
| `battle:<battleId>` | participants of the battle |
| `interview:<sessionId>` | the interviewer and the candidate |

## Battle events (`battle:<id>`)

Every event except `ANSWER_SUBMITTED` carries the full shared `BattleState`, which is safe for every participant.

| type | when |
| --- | --- |
| `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_READY` | lobby changes |
| `BATTLE_STARTED` | host started; status `IN_PROGRESS` |
| `QUESTION_STARTED` | a round opened; `state.live` has the question (no correctness), `startedAt` and `endsAt` |
| `ANSWER_SUBMITTED` | `{userId, questionIndex}`. Says who answered, **never whether they were right** |
| `ROUND_ENDED` | round closed (timer or all answered); `state.reveal` has the correct option and per-player points |
| `BATTLE_COMPLETED` | final scores, `winnerId` (null on a tie) |

Battle **actions** go over REST (`/api/battles/{id}/ready|start|answer|leave`). This gives them CSRF protection, validation and clear error codes. The server rejects:
- answers after the deadline (plus 750 ms grace)
- answers for a round that isn't current
- duplicate answers (409)
- answers from non-participants (404)
- options that don't belong to the question (400)

## Interview events (`interview:<id>`)

| type | payload | client action |
| --- | --- | --- |
| `PLAYER_JOINED`, `INTERVIEW_STARTED`, `QUESTION_CHANGED`, `INTERVIEW_FINISHED`, `INTERVIEW_COMPLETED`, `INTERVIEW_CANCELLED`, `CODE_SUBMITTED` | `{sessionId, status, currentIndex}` | refetch its own role-specific view |
| `CODE_UPDATED` | `{sessionId, version, baseVersion, changes[]}` | apply if `baseVersion` equals the local version, otherwise fetch `GET /api/interviews/{id}/code` |
| `CODE_SNAPSHOT` | `{sessionId, code, language, version}` | replace the local text |

All interview events are published **after the database commit**.

### Code sync messages (candidate only, interview `ACTIVE`)

```json
{ "type": "CODE_UPDATE", "data": { "sessionId": "…", "baseVersion": 41,
  "changes": [ { "offset": 120, "length": 0, "text": "x" } ] } }
```

- `changes` must be sorted by **descending offset**, so applying them in order is correct. Monaco's events are sorted client-side before sending. Each message carries 1–200 changes and the document is capped at 100,000 characters.
- If `baseVersion` doesn't match, the server replies to the sender only with `CODE_REJECTED {reason: STALE_VERSION}`. The candidate then sends:

```json
{ "type": "CODE_REPLACE", "data": { "sessionId": "…", "code": "<full text>", "language": "JAVA" } }
```

The server bumps the version and broadcasts `CODE_SNAPSHOT`.

## User-level messages (no topic)

| type | payload |
| --- | --- |
| `WELCOME` | `{userId}` on connect |
| `FRIEND_REQUEST` | `{from: PublicUser}` |
| `FRIEND_ACCEPTED` | `{by: PublicUser}` |
| `INTERVIEW_INVITE` | `{sessionId, roomCode, from, typeLabel}` |
| `ERROR` | `{code, message}` |

## Reconnection

Clients reconnect with full-jitter exponential backoff: 0.5 s plus a random delay up to `min(30 s, 1 s · 2^(n-1))`. Each reconnect fetches a fresh ticket, re-sends `SUBSCRIBE` for every active topic, and refetches authoritative state on `SUBSCRIBED`.
