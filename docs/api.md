# REST API

Base path: `/api`. JSON in and out. Every endpoint except register, login and csrf requires a session. Unsafe methods need the `X-XSRF-TOKEN` header (see [architecture](architecture.md)).

**Errors** always look like this:

```json
{ "code": "VALIDATION_FAILED", "message": "Some fields need attention", "fieldErrors": { "email": "must be a well-formed email address" }, "timestamp": "…" }
```

Status codes: `400` invalid input · `401` not signed in · `403` signed in but not allowed (for example, a candidate asking for the interviewer view) · `404` doesn't exist **or isn't yours** · `409` state conflict (duplicate answer, wrong state, concurrent update) · `429` too many login attempts.

## Auth & users

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/auth/csrf` | primes the `XSRF-TOKEN` cookie |
| POST | `/auth/register` | `{email, username, displayName, password(≥10), timeZone}` → 201 `Me`, signed in |
| POST | `/auth/login` | `{login, password}` → `Me`; throttled |
| POST | `/auth/logout` | 204 |
| POST | `/auth/ws-ticket` | `{ticket, expiresInSeconds}` |
| GET / PUT | `/users/me` | profile and study settings (time zone validated) |
| GET | `/users/search?q=` | ≥2 chars, public fields only |
| GET | `/users/{id}/profile` | public profile + friendship relation |

## Practice

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/topics` | with question counts and your mastery |
| GET | `/questions?category&topic&difficulty&search&bookmarked&mistakes&page&size` | paginated (size ≤ 50) |
| GET | `/questions/{id}` | options **without** correctness |
| POST | `/questions/{id}/answer` | `{optionId}` → `{correct, correctOptionId, explanation, xpAwarded}`; 5 XP only for your first correct answer |
| POST | `/questions/{id}/bookmark` | toggles |
| GET | `/attempts?page&size` | attempt history |

## Missions

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/missions/today` | generated once per local day, then stable |
| GET | `/missions/{id}` | review and guide data only after completion |
| POST | `/missions/{id}/submit` | question set: `{answers: {questionId: optionId}}`; study: `{notes, selfRating}`. Repeat submissions return the review with `xpAwarded: 0` |

## Battles

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/battles` | `{category, difficulty, questionCount 3–20, secondsPerQuestion 10–120}` → 201 |
| GET | `/battles` | your history |
| GET | `/battles/code/{code}` | preview before joining |
| POST | `/battles/join` | `{code}` (idempotent rejoin) |
| GET | `/battles/{id}` | `{state, yourAnswerForCurrent}`, participants only |
| POST | `/battles/{id}/ready` | `{ready}` |
| POST | `/battles/{id}/start` | host only, status `READY` |
| POST | `/battles/{id}/answer` | `{questionIndex, optionId}`, scored by the server |
| POST | `/battles/{id}/leave` | host leaving the lobby cancels it |
| GET | `/battles/{id}/results` | per-question breakdown once completed |

## Interviews

| Method | Path | Who |
| --- | --- | --- |
| GET | `/interviews/types`, `/interviews/rubric` | anyone signed in |
| POST | `/interviews` | creator becomes the interviewer: `{type, difficulty, durationMinutes, questionCount}` |
| GET | `/interviews` | your history (both roles) |
| POST | `/interviews/join` | `{code}`; first joiner becomes the candidate |
| GET | `/interviews/{id}` | both participants; candidate-safe view |
| GET | `/interviews/{id}/interviewer` | **interviewer only**: guides, rubric, private notes |
| POST | `/interviews/{id}/start`, `/next`, `/finish`, `/cancel` | interviewer |
| PUT | `/interviews/{id}/notes` | interviewer: `{sessionQuestionId?, body}` |
| POST | `/interviews/{id}/evaluation` | interviewer, status `FEEDBACK` |
| GET | `/interviews/{id}/report` | both, once completed (never includes private notes) |
| POST | `/interviews/{id}/rematch` | either participant; new room with roles swapped |
| POST | `/interviews/{id}/invite` | interviewer: `{userId}` (must be a friend) |
| GET | `/interviews/{id}/code` | live code snapshot |
| POST | `/interviews/{id}/code/submissions` | candidate |

## Coding & design

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/coding/execution` | `{available: false, reason}` until a judge is configured |
| GET | `/coding/problems`, `/coding/problems/{slug}` | the reference outline appears after your first submission |
| GET / POST | `/coding/problems/{slug}/submissions` | `{code, language, note?}`; 15 XP on your first submission per problem |
| GET | `/design/problems`, `/design/problems/{slug}` | discussion guide after you submit (solo only) |
| GET / PUT | `/design/documents?problemId&sessionId` | your diagram (≤150 nodes, ≤300 edges) |
| POST | `/design/documents/submit?problemId&sessionId` | 30 XP once per problem (solo) |
| GET | `/design/sessions/{sessionId}/candidate-document?problemId` | interviewer only |

## Brain gym

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/gym` | today, skills, calibration buckets, friends daily board, recent runs |
| POST | `/gym/daily` | start or resume today's run (same 5 puzzles for everyone that date) |
| POST | `/gym/practice` | `{mode: ESTIMATION|INCIDENT|REVIEW|TRADEOFF}` → 4 puzzles, least recently seen first |
| GET | `/gym/runs/{id}` | owner only; solutions appear only for answered puzzles |
| POST | `/gym/runs/{id}/puzzles/{pid}/clues/{clueId}` | incident only, before answering; the clue's cost is recorded |
| POST | `/gym/runs/{id}/puzzles/{pid}/answer` | `{response, confidence 50–99}` → score, verdict, solution; 409 if already answered |

## Progress, gamification, friends, admin

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/dashboard` | welcome, 7-day activity, skills, friend feed |
| GET | `/progress` | analytics bundle |
| GET | `/gamification/me` | level, levels, streak, achievements |
| GET | `/gamification/leaderboard?metric=XP\|QUIZ_WINS\|INTERVIEWS\|CODING&period=WEEK\|MONTH\|ALL&scope=friends\|all` | totals only |
| GET | `/friends` | friends + incoming/outgoing requests |
| POST | `/friends/requests` | `{userId}`; if they already asked you, this accepts |
| POST | `/friends/requests/{id}/accept` | addressee |
| DELETE | `/friends/requests/{id}` | decline or withdraw |
| DELETE | `/friends/{userId}` | unfriend |
| GET / POST / PUT | `/admin/questions[/{id}]` | `ADMIN` role only |

Health: `GET /actuator/health` (public, no details).
