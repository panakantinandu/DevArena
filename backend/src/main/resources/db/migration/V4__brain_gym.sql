-- Brain Gym: judgment puzzles (estimation, incident debugging, AI code review, trade-offs) with calibration.

CREATE TABLE gym_puzzles (
    id          UUID PRIMARY KEY,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    mode        VARCHAR(20)  NOT NULL CHECK (mode IN ('ESTIMATION', 'INCIDENT', 'REVIEW', 'TRADEOFF')),
    difficulty  VARCHAR(10)  NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    title       VARCHAR(140) NOT NULL,
    -- Shown before answering.
    payload     JSONB        NOT NULL,
    -- Never sent to a client until that client has answered (clue bodies, answers, scores).
    secret      JSONB        NOT NULL,
    explanation TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_gym_puzzles_mode ON gym_puzzles (mode);

CREATE TABLE gym_runs (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind          VARCHAR(10) NOT NULL CHECK (kind IN ('DAILY', 'PRACTICE')),
    run_date      DATE        NOT NULL,
    mode          VARCHAR(20),
    status        VARCHAR(12) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    total_score   INT,
    created_at    TIMESTAMPTZ NOT NULL,
    completed_at  TIMESTAMPTZ
);
-- One daily run per user per local date.
CREATE UNIQUE INDEX ux_gym_daily_once ON gym_runs (user_id, run_date) WHERE kind = 'DAILY';
CREATE INDEX ix_gym_runs_user ON gym_runs (user_id, created_at DESC);

CREATE TABLE gym_run_puzzles (
    run_id      UUID NOT NULL REFERENCES gym_runs (id) ON DELETE CASCADE,
    puzzle_id   UUID NOT NULL REFERENCES gym_puzzles (id),
    position    INT  NOT NULL,
    PRIMARY KEY (run_id, puzzle_id),
    UNIQUE (run_id, position)
);

CREATE TABLE gym_clue_reveals (
    run_id      UUID        NOT NULL REFERENCES gym_runs (id) ON DELETE CASCADE,
    puzzle_id   UUID        NOT NULL REFERENCES gym_puzzles (id),
    clue_id     VARCHAR(40) NOT NULL,
    revealed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, puzzle_id, clue_id)
);

CREATE TABLE gym_answers (
    run_id       UUID         NOT NULL REFERENCES gym_runs (id) ON DELETE CASCADE,
    puzzle_id    UUID         NOT NULL REFERENCES gym_puzzles (id),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mode         VARCHAR(20)  NOT NULL,
    response     JSONB        NOT NULL,
    score        INT          NOT NULL CHECK (score BETWEEN 0 AND 100),
    -- Did the player "get it" (mode-specific threshold), and how sure were they (50–99%)?
    correct      BOOLEAN      NOT NULL,
    confidence   INT          NOT NULL CHECK (confidence BETWEEN 50 AND 99),
    answered_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (run_id, puzzle_id)
);
CREATE INDEX ix_gym_answers_user ON gym_answers (user_id, answered_at DESC);
