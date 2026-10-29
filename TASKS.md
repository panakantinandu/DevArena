# DevArena: task checklist

Legend: `[x]` done and tested · `[~]` partial (details given) · `[ ]` not started.

## Phase 1: Foundation
- [x] Monorepo: `backend/` (Spring Boot 4.1, Java 21), `frontend/` (React 19 + Vite), `docs/`, `design-system/`
- [x] Design system from the ui-ux-pro-max skill → `design-system/devarena/MASTER.md`; Tailwind v4 tokens
- [x] Flyway schema (V1–V3), `ddl-auto: validate`
- [x] Auth: register, login, logout, me; bcrypt; JDBC sessions; CSRF (SPA mode); CORS allowlist; brute-force throttle
- [x] App shell: collapsible sidebar, mobile drawer, skip link, connection banner, protected routes, onboarding
- [x] Profile & settings (display name, avatar, bio, language, role, level, daily minutes, streak minimum, time zone)
- [x] Health checks (`/actuator/health`, liveness and readiness)

## Phase 2: Solo practice
- [x] Curated content: 47 topics, 152 MCQs with explanations, 7 coding problems, 6 design problems, 27 interview guides, 10 achievements
- [x] Versioned, idempotent, non-destructive content seeder (checksum log, upsert by slug, deterministic option shuffle)
- [x] Practice library: category and topic navigation, search, difficulty filter, bookmarks, "my mistakes", pagination, attempt history
- [x] Daily missions: 90-day roadmap (3 phases), deterministic planner (weak areas, variety, time budget, revision, daily minimum)
- [x] Mission completion: transactional, once-only XP (DB unique keys), review after completion, study missions reveal the guide after you answer
- [x] XP ledger, configurable levels, time-zone and DST-aware streaks, achievements
- [x] Deterministic mastery model (documented formula)
- [ ] Weekly challenges (an XP source the prompt mentions; not built)
- [~] Spaced repetition: recent mistakes come back as revision missions; there is no full interval-based scheduler

## Phase 3: Multiplayer quiz
- [x] Rooms with 6-character codes, invite links, lobby, ready-up, host-only start, leave and cancel
- [x] Explicit states WAITING → READY → IN_PROGRESS → COMPLETED / CANCELLED; invalid transitions rejected
- [x] Server-authoritative timers, server scoring (100 + up to 25 speed), answers hidden until round end
- [x] Duplicate-answer prevention (DB unique key, tested with 8 concurrent requests), participant-only actions
- [x] WebSocket events, reconnection with backoff, re-subscribe and refetch, timer recovery after restart
- [x] Results, per-question breakdown, history, XP for correct answers and wins
- [x] Stale lobbies auto-cancelled after 2 hours

## Phase 4: Peer interviews
- [x] Nine interview types; guides with expected concepts, strong-answer outline, common mistakes, follow-ups and rubric notes
- [x] States CREATED → LOBBY → ACTIVE → FEEDBACK → COMPLETED / CANCELLED
- [x] Role separation enforced by the API and DTO types (candidate gets 403 on the interviewer view; tested)
- [x] Private per-question notes (autosaved, never shown to the candidate)
- [x] Five-criterion rubric, fixed weighted overall, strengths and improvements, report for both participants
- [x] Role swap via rematch; friend invites over WebSocket
- [~] "Interviewer chooses topics": the interview type decides the topic pool; picking individual topics isn't built

## Phase 5: Coding workspace
- [x] Monaco (bundled locally, custom theme, font-size controls, Java plus 6 more languages)
- [x] Solo problems: draft recovery, reset confirmation, submission history, reference outline after submitting
- [x] Live interview coding: incremental deltas with versions, resync on gaps, drafts persisted, read-only interviewer mirror
- [x] `CodeExecutionService` seam with an honest "unavailable" implementation; user code is never executed by the API
- [ ] Sandboxed judge with test cases (future)
- [ ] Live coding battles (future; the leaderboard counts coding practice instead)

## Phase 6: Enhancements
- [x] Analytics: KPIs, 90-day heatmap, topic accuracy, most-missed topics, difficulty trend, interview score trend, mastery
- [x] Friends: search, request, accept, decline or withdraw, remove, profiles, challenge and invite shortcuts
- [x] Leaderboards: week, month or all-time × XP, quiz wins, interviews or coding problems; friends or everyone
- [x] System design workspace: box-and-arrow editor (keyboard-movable), notes, submit, guide after submit, interviewer view
- [~] Collaborative drawing: the interviewer view refreshes every 4s; true multi-cursor editing isn't built
- [x] Admin question authoring (create, edit, archive; one-correct-option validation)

## Brain gym (senior judgment trainer)
- [x] Four modes: Estimation sprint, Incident detective (paid clues), Spot the flaw (AI-written code), Trade-off arena (partial credit + named risk)
- [x] 29 curated puzzles with worked explanations (V4 migration, seeded via the content seeder)
- [x] Confidence on every answer, Brier-based calibration score and a calibration chart
- [x] Daily five: same puzzles for everyone per date, friends board (scores only after finishing); practice runs prefer unseen puzzles
- [x] Server-side scoring (GymScoring), secrets only after answering, clue costs recorded server-side, one answer per puzzle, XP once per run
- [x] Tests: 6 scoring unit tests, 3 integration tests (sharing, secrecy, clues, privacy, concurrency), Playwright full daily run
- [ ] More puzzles (authoring UI); timed "pressure" mode; head-to-head gym duels

## Deployment
- [x] Multi-stage Dockerfile (non-root user, layered jar, memory-tuned), `render.yaml`, `application-prod.yml`
- [x] `vercel.json` (same-origin `/api` rewrite, SPA fallback, security headers), env reference, `.env.example`
- [ ] Actually deploying (requires your Neon, Render and Vercel accounts; see docs/deployment.md)

## Quality
- [x] Backend: 27 tests passing (unit + real-PostgreSQL integration, including concurrency)
- [x] Frontend: 9 component and form tests passing; type-check, lint (warnings only) and production build all pass
- [x] Playwright critical flow passing end to end with two browsers
- [ ] Automated accessibility audit (axe) and visual regression
- [ ] CI pipeline (GitHub Actions); deliberately not created because no repository has been published

## Future ideas
Spaced-repetition scheduler · topic picker for interviews · sandboxed code judge · coding battles · shared pub/sub for multiple instances · weekly challenges · email notifications.
