# Testing

All numbers below come from actual runs on 2026-10-29.

## Backend: `cd backend && mvn test` (36 tests, all passing)

Integration tests boot the full application against **real PostgreSQL 18**. The server is the zonky embedded distribution, so no Docker is needed. They talk to it over HTTP with cookies and CSRF tokens, as the browser does (`support/ApiClient`).

| Suite | Covers |
| --- | --- |
| `ApplicationBootTest` | Flyway migrations, schema validation, content seeding, idempotent re-seed, exactly one correct option per question, correct answers not clustered in one position |
| `AuthFlowTest` | register, login, me, logout · 401 on protected endpoints · generic bad-credentials message and lockout (429) · CSRF rejection (403) · duplicate email (409) and field validation · profile update and time-zone validation |
| `MissionFlowTest` | stable daily plan · answers hidden until completion · XP once, repeat submit gives 0 XP · **6 concurrent submits produce exactly one completion and one XP row** · another user's mission returns 404 · study guide revealed only after submission |
| `BattleFlowTest` | full two-player battle over three timed rounds · non-participant gets 404, guest can't start (403), start before ready (409) · duplicate answer (409) · live question never contains correctness · score equals the sum of server-computed points · **8 concurrent duplicate answers store exactly one** · foreign option (400), wrong round (409) |
| `InterviewFlowTest` | create, join, start, next, finish, evaluate · stranger blocked · candidate view has no guide fields · `/interviewer` returns 403 for the candidate · notes are interviewer-only · candidates see only asked questions · evaluation only in FEEDBACK and only by the interviewer · weighted overall = 3.80 · private notes absent from the candidate's report · rematch swaps roles |
| `GymFlowTest` | daily run shared by all players and resumable · no answers, clue bodies or option scores before answering · full run completes with XP once · duplicate answer 409 · clue cost recorded and deducted · runs private (404) · invalid estimate range and confidence (400) · 6 concurrent answers store one |
| `GymScoringTest` | estimation (log error + interval), incident clue penalties, review F1 (false alarms hurt), trade-off partial credit, Brier calibration (honest beats overconfident) |
| `DomainRulesTest` | streak rules, including a 23-hour DST day in Europe/Berlin · mastery monotonicity and recency · battle points (100 + up to 25) · battle and interview state machines · levels · planner determinism, weak-area priority, revision missions, daily minimum |

## Frontend: `cd frontend && npm test` (12 tests, all passing)

Vitest with Testing Library and jsdom covers:
- the question radio group and answer reveal, with screen-reader text so correctness isn't conveyed by colour alone
- field labels, hints and errors wired up for screen readers
- the error state's retry action
- login validation, the server error message and the CSRF header
- registration validation

## End to end: `cd frontend && npm run e2e` (1 flow, passing)

This needs the backend running, e.g. with `DevServer`. It uses your installed Chrome; set `PW_CHANNEL=` to use bundled Chromium instead. `e2e/critical-flow.spec.ts` covers the priority workflow with two real browser contexts:

1. Alice registers and onboards, then completes a daily mission.
2. Bob registers.
3. Alice creates a battle; Bob joins by code; both ready up; three live rounds; both see the results and breakdown.
4. Alice runs an interview. Bob joins and sees the question but not the guide; Alice sees the guide and takes private notes.
5. Alice finishes and scores the interview. Bob receives the report automatically, without her private notes.
6. Bob opens the progress page.

`e2e/screens.spec.ts` is a screenshot helper for design review. It is skipped unless `SCREENSHOTS=<dir>` is set.

## Not covered yet

- Visual regression and automated axe accessibility audits; accessibility was checked by hand.
- Load testing of WebSocket fan-out.
- Frontend unit tests for the battle and interview rooms; they are covered by the E2E flow instead.
