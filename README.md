# DevArena

**Code. Compete. Interview. Improve.**

DevArena is a multiplayer interview-prep platform for software engineers who study together. It combines:
- a 90-day roadmap with daily missions
- real-time quiz battles
- structured peer interviews with answer guides and a rubric
- a coding workspace and a system design workspace
- progress analytics

It uses **no AI services or API keys**. Everything runs on curated content, deterministic rules and your own peer reviews.

| | |
| --- | --- |
| Frontend | React 19 · TypeScript · Vite · Tailwind CSS v4 · TanStack Query · React Hook Form + Zod · Monaco · Recharts |
| Backend | Java 21 · Spring Boot 4.1 · Spring Security · Spring Data JPA · Spring WebSocket · Flyway · Spring Session JDBC |
| Database | PostgreSQL (embedded PostgreSQL for tests and the no-install dev mode) |
| Deploy | Vercel (frontend) · Render via Docker (backend) · Neon or another free PostgreSQL |

## Feature status

| Area | Status |
| --- | --- |
| Registration, login, logout, sessions, profile and settings | ✅ Done |
| Daily missions (90-day roadmap, deterministic weak-area planner, revision of mistakes, once-only XP) | ✅ Done |
| Practice library (filters, search, bookmarks, mistakes, attempt history, explanations) | ✅ Done |
| **Brain gym**: daily and practice judgment puzzles (estimation, incident debugging, AI-code review, trade-offs) with confidence calibration and a friends daily board | ✅ Done |
| Multiplayer quiz battles (lobby, ready-up, server timers and scoring, reveal, results, history, restart recovery) | ✅ Done |
| Peer interviews (roles, guides, private notes, rubric scoring, report, role swap, friend invites) | ✅ Done |
| Live coding in interviews (Monaco, incremental sync to a read-only interviewer view, drafts) | ✅ Done |
| Solo coding workspace (problems, drafts, submissions history, reset) | ✅ Done; **code execution is not available** (interface only, see below) |
| System design workspace (diagram editor, notes, submit, interviewer view) | ✅ Done; interviewer view refreshes every 4s (not collaborative drawing) |
| XP, levels, streaks (time-zone and DST aware), achievements | ✅ Done |
| Leaderboards (weekly / monthly / all-time; XP, quiz wins, interviews, coding problems) | ✅ Done |
| Progress analytics (heatmap, topic accuracy, misses, difficulty trend, interview trend) | ✅ Done |
| Friends (search, requests, accept/decline, remove, profiles, challenge, invite) | ✅ Done |
| Question admin (create/edit/archive) | ✅ Done (promote an admin with SQL, see below) |
| Live *coding* battles, weekly challenges, choosing specific topics for interviews | ⏳ Not built (see [TASKS.md](TASKS.md)) |

## Run everything locally

Prerequisites: **Java 21**, **Maven 3.9+**, **Node 20+**. No Docker is needed.

### 1. Backend

**Option A: no database install (recommended).** The backend starts its own embedded PostgreSQL. Data is kept in `.data/pg`.

```bash
cd backend
mvn -q test-compile exec:java
```

**Option B: your own PostgreSQL.** Create a database and user once:

```bash
psql -U postgres -h localhost -c "CREATE ROLE devarena LOGIN PASSWORD 'devarena_local'" -c "CREATE DATABASE devarena OWNER devarena"
```

Then start the backend:

```bash
cd backend
DATABASE_PASSWORD=devarena_local mvn spring-boot:run          # bash
$env:DATABASE_PASSWORD='devarena_local'; mvn spring-boot:run  # PowerShell
```

On first start, Flyway creates the schema and the content seeder loads the question bank. The API runs on `http://localhost:8080` (health check: `/actuator/health`).

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` and `/ws` to the backend, so the session cookie stays first-party.

### Make yourself an admin (optional)

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'your_username';
```

Sign out and back in. **Question admin** then appears in the sidebar.

## Tests

```bash
cd backend && mvn test                  # 36 tests: unit and integration against real (embedded) PostgreSQL
cd frontend && npm test                 # 12 component, form and parser tests (Vitest)
cd frontend && npm run e2e              # Playwright: critical flow + Brain Gym run (needs the backend running)
```

See [docs/testing.md](docs/testing.md) for what is covered.

## Documentation

- [Architecture and security model](docs/architecture.md)
- [REST API](docs/api.md) · [Real-time protocol](docs/realtime-protocol.md)
- [Database schema](docs/database.md)
- [Deployment: Neon, Render and Vercel](docs/deployment.md) · [Environment variables](docs/environment.md)
- [Design system](design-system/devarena/MASTER.md): "Neubrutalist Arena", light and dark themes (from the ui-ux-pro-max skill)
- [Task checklist and roadmap](TASKS.md)

## Honest limitations

- **No code execution.** Submitted code is saved and reviewed by people. `CodeExecutionService` is the integration point for a future sandboxed judge. User code is never run inside the API.
- **Single backend instance.** Battle timers, WebSocket rooms and login throttling live in memory. After a restart, battles resume from the database, but live connections drop and must reconnect, which they do automatically. Running several instances would need a shared broker (e.g. Redis); see [docs/architecture.md](docs/architecture.md).
- **Free tiers sleep.** Render's free instance sleeps after inactivity, so the first request can take up to about a minute. The UI shows a "waking up" state and retries with backoff. Don't count on uninterrupted live sessions on free infrastructure.
- **Mastery is a study aid**, calculated from your own answers. It is not a validated hiring predictor.
