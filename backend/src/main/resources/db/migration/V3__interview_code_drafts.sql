-- Latest live editor contents per interview, so a reconnect or restart doesn't lose code.
CREATE TABLE interview_code_drafts (
    session_id  UUID PRIMARY KEY REFERENCES interview_sessions (id) ON DELETE CASCADE,
    language    VARCHAR(20) NOT NULL,
    code        TEXT        NOT NULL,
    version     BIGINT      NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_design_documents_owner ON design_documents (user_id, problem_id, COALESCE(interview_session_id, '00000000-0000-0000-0000-000000000000'::uuid));
