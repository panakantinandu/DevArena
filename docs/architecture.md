# Architecture

## Shape

A **modular monolith**: one Spring Boot service and one React SPA. Modules are packages with their own controller, service and DTO classes. They share one database, but each module owns its own tables.

```
backend/src/main/java/com/devarena/
  auth/          login, registration, session, WS tickets, brute-force throttle
  users/         profile & settings
  friends/       friend requests, public profiles
  questions/     topics, MCQ bank, practice, bookmarks, admin authoring
  content/       versioned idempotent seeder for the curated content
  missions/      90-day roadmap, deterministic planner, mission completion
  battles/       real-time quiz battles (state machine, timers, scoring)
  interviews/    peer interview sessions, guides, notes, evaluation, reports
  coding/        problems, submissions, live code sync, CodeExecutionService seam
  design/        system design problems & diagram documents
  progress/      mastery model, dashboard, analytics
  gamification/  XP ledger, levels, streaks, achievements, leaderboards
  realtime/      WebSocket hub, topic authorization, message handlers
  common/        errors, ids, enums, after-commit helper
  config/        security, websocket, properties, clock
```

Microservices were deliberately not used. Two friends and a small team gain nothing from network hops, distributed transactions or many deployments. The module boundaries would allow splitting later if that ever became necessary.

## Request path

```
Browser ──► Vercel (static SPA + rewrite /api/* ──► Render) ──► Spring Security filters ──► controllers ──► services ──► PostgreSQL
Browser ──► wss://render/ws?ticket=…  (ticket from POST /api/auth/ws-ticket via the same-origin proxy)
```

## Authentication and the threat model

- **Sessions, not tokens in JS.** After login, the server sets an `HttpOnly`, `Secure` (prod), `SameSite=Lax` `SESSION` cookie. JavaScript never sees a credential, so an XSS bug can't steal one.
- **Same-origin through a proxy.** Vercel rewrites `/api/*` to Render, so the browser only ever talks to the Vercel origin. The cookie is first-party, which avoids the third-party cookie blocking that breaks cross-site cookie setups.
- **CSRF.** Spring Security's SPA mode issues an `XSRF-TOKEN` cookie. The SPA echoes it in `X-XSRF-TOKEN` on every unsafe request. `SameSite=Lax` adds a second layer.
- **CORS** is an explicit allowlist (`DEVARENA_CORS_ALLOWED_ORIGINS`) with credentials. It is mainly relevant if the proxy is bypassed.
- **WebSockets** connect directly to Render, where the Vercel cookie isn't sent. The SPA first asks for a **single-use ticket valid for 30 seconds** over the authenticated REST API, then presents it in the handshake. The handshake also enforces an **Origin allowlist**. The sender's identity always comes from the ticket, never from message content.
- **Sessions are stored in PostgreSQL** (Spring Session JDBC), so a Render restart or sleep doesn't sign everyone out. Sessions expire after 14 days of inactivity.
- **Passwords** use bcrypt via `DelegatingPasswordEncoder`, so the algorithm can be upgraded later. The login message never reveals whether an account exists.
- **Brute force.** After 5 failures, an account is locked for 15 minutes. An IP gets a looser budget (20), so two friends behind one router don't lock each other out. Login and registration both pass through the throttle.
- **Authorization** is enforced in services, for every room-specific REST call and WebSocket subscription. Asking for another user's mission, battle or interview returns **404** rather than 403, so IDs can't be probed.
- **Interview secrecy** is enforced by types, not CSS. Candidate responses use `CandidateQuestion` and `SessionView`, which have no guide fields at all. Guides come only from `GET /api/interviews/{id}/interviewer`, which returns 403 for the candidate. Private notes are returned only to their author, before and after the interview.
- **Errors** use one shape (`{code, message, fieldErrors, timestamp}`). Stack traces and SQL never reach clients.
- **No secrets in the repo.** All credentials come from environment variables. `VITE_*` values are public by design and contain no secrets.

## Integrity guarantees enforced by the database

| Rule | Mechanism |
| --- | --- |
| XP is never awarded twice for the same thing | `UNIQUE (user_id, source_type, source_id)` on `xp_transactions` + `ON CONFLICT DO NOTHING` |
| A mission completes once | `UNIQUE (assignment_id)` on `mission_completions` |
| One answer per player per battle question | `UNIQUE (battle_question_id, user_id)` on `quiz_answers` |
| Each question has exactly one correct option | Partial unique index on `question_options (question_id) WHERE correct` |
| One friendship per pair, whoever asked first | Unique index on `(LEAST(a,b), GREATEST(a,b))` |
| Concurrent edits to battles, interviews and users | `@Version` optimistic locking |

## Real-time design

- **Battles are server-authoritative.** Every mutation runs under a per-battle lock and inside a transaction. Rounds open and close on server timers; a round also closes early once everyone has answered. Points (100 plus up to 25 for speed) are computed from **server timestamps**; the client only sends an option ID. Correct answers are revealed only when a round closes. On startup, `BattleService.recover()` rebuilds timers for battles that were still running.
- **Interview events are signals.** Payloads carry only `{status, currentIndex}`, and each client refetches its own role-specific view. That keeps secret data off the shared channel. Events are published **after commit** (`AfterCommit`), so a refetch always sees the change.
- **Live code** uses a single writer, the candidate. Monaco change deltas are sent tagged with the version they were made against; the server applies only matching versions and broadcasts the deltas. On any gap, the candidate re-sends the full text once, or the interviewer fetches a snapshot. Drafts are flushed to `interview_code_drafts` every 5 seconds.
- **Reconnection.** The client retries with exponential backoff and full jitter (capped at 30 seconds), re-subscribes to its rooms, and refetches authoritative state after each resubscription. A banner shows while the connection is down.

### Single-instance limitation

Locks, timers, WebSocket subscriptions, login throttling and WS tickets are in memory. That is correct for one backend instance, which is what the free tier provides. Running more instances would need:
- shared pub/sub for room events
- a distributed lock or database leasing for battle timers
- shared throttling and ticket storage, e.g. Redis or PostgreSQL `LISTEN/NOTIFY`

Sessions are already shared, because they live in PostgreSQL.

## Deterministic intelligence (no AI)

- **Mission planner** (`MissionPlanner`): a pure function of roadmap day, prep level, time budget, topic mastery, recency and recent mistakes. Its rules are documented in the class Javadoc and covered by unit tests.
- **Mastery** (`MasteryService`): a weighted accuracy multiplied by a confidence term. The weights account for difficulty, a 21-day recency half-life, context, and repeated attempts. The formula is in the Javadoc and in the Progress page's "How mastery is calculated" section.
- **Interview overall score**: a fixed weighted average of five rubric scores (technical 30%, depth 20%, problem solving 20%, communication 15%, follow-ups 15%). Every score comes from a person; nothing is generated.
