# Database

PostgreSQL. The schema is owned by Flyway (`backend/src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate` and never changes the schema. All identifiers are application-generated UUIDs, and timestamps are `TIMESTAMPTZ` in UTC.

| Migration | Contents |
| --- | --- |
| `V1__core_schema.sql` | all domain tables, constraints and indexes |
| `V2__spring_session.sql` | Spring Session JDBC tables |
| `V3__interview_code_drafts.sql` | live code drafts and design-document uniqueness |
| `V4__brain_gym.sql` | `gym_puzzles` (public `payload` + private `secret` JSONB), `gym_runs` (one DAILY per user per date), `gym_run_puzzles`, `gym_clue_reveals`, `gym_answers` (one per run and puzzle) |

## Tables by module

| Module | Tables |
| --- | --- |
| Users | `users` (unique lower(email), lower(username)), `user_settings` (1:1, time zone, roadmap start, daily targets) |
| Friends | `friendships` (PENDING/ACCEPTED, one row per unordered pair) |
| Content | `topics`, `questions`, `question_options`, `coding_problems`, `design_problems`, `interview_questions`, `achievements`, `content_seed_log` |
| Practice | `question_attempts` (context PRACTICE/MISSION/BATTLE), `bookmarks` |
| Missions | `mission_assignments` (unique user + date + slot, JSONB payload), `mission_completions` (unique per assignment) |
| Progress | `skill_progress` (per user and topic mastery cache), `daily_activity` (per local date) |
| Gamification | `xp_transactions` (the XP ledger, unique per source), `user_achievements` |
| Battles | `quiz_battles` (state machine, `@Version`), `quiz_battle_participants`, `quiz_battle_questions` (server deadlines), `quiz_answers` (unique per player and question) |
| Interviews | `interview_sessions` (state machine, `@Version`), `interview_session_questions` (`asked_at` gates candidate visibility), `interview_evaluations` (one per session), `interview_notes` (private, per author), `interview_code_drafts` |
| Code & design | `code_submissions`, `design_documents` (JSONB diagram) |
| Sessions | `spring_session`, `spring_session_attributes` |

## Design notes

- **The XP total is derived**: `SUM(xp_transactions.amount)`. Nothing stores a mutable counter that could drift, and the unique key `(user_id, source_type, source_id)` makes double-awarding impossible even under concurrency.
- **Roadmap and levels are code configuration**, not tables. The prompt suggested `daily_missions`; it was folded into `RoadmapPlan` and the per-user `mission_assignments`, because the roadmap is versioned with the planner logic that reads it.
- **Seed data** is upserted by slug by `ContentSeeder`, and files whose SHA-256 is unchanged are skipped. It never deletes rows, so it's safe on every production start. Option order is shuffled once per slug (deterministically), so correct answers don't cluster in one position.
- **Interviewer-only columns** (`answer_outline`, `expected_concepts`, `common_mistakes`, `follow_ups`, `rubric_notes`, `solution_outline`, `discussion_topics`) are only ever read into interviewer DTOs.
- **Indexes** cover the hot paths: attempts by user and time, questions by topic and difficulty (partial, `ACTIVE` only), XP by user and time, interview sessions by participant, and battle participants by user.
