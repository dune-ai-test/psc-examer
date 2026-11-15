-- Padanam · Neon schema
-- Run this once in the Neon SQL editor if you prefer to create the tables
-- yourself. The app also calls CREATE TABLE IF NOT EXISTS automatically before
-- its first sync, so this file is a manual fallback/reference.
--
-- The app talks to Neon over the HTTP /sql endpoint using the connection
-- string as a bearer-style header. Tables are plain Postgres; timestamps are
-- Unix epoch milliseconds stored as BIGINT.

CREATE TABLE IF NOT EXISTS sets (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    subject TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    source TEXT NOT NULL DEFAULT 'scan',
    image_uris_json TEXT
);

CREATE TABLE IF NOT EXISTS questions (
    id TEXT PRIMARY KEY,
    set_id TEXT NOT NULL REFERENCES sets(id) ON DELETE CASCADE,
    ord INTEGER NOT NULL,
    question_en TEXT NOT NULL,
    question_ml TEXT NOT NULL,
    option_a_en TEXT NOT NULL,
    option_b_en TEXT NOT NULL,
    option_c_en TEXT NOT NULL,
    option_d_en TEXT NOT NULL,
    option_a_ml TEXT NOT NULL,
    option_b_ml TEXT NOT NULL,
    option_c_ml TEXT NOT NULL,
    option_d_ml TEXT NOT NULL,
    correct_option TEXT NOT NULL,
    explanation_en TEXT NOT NULL,
    explanation_ml TEXT NOT NULL,
    topic TEXT NOT NULL,
    difficulty TEXT NOT NULL,
    confidence INTEGER NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS questions_set_id_idx ON questions (set_id);

CREATE TABLE IF NOT EXISTS attempts (
    id TEXT PRIMARY KEY,
    set_id TEXT,
    set_title TEXT NOT NULL,
    subject TEXT NOT NULL,
    mode TEXT NOT NULL,
    total INTEGER NOT NULL,
    correct INTEGER NOT NULL,
    wrong INTEGER NOT NULL,
    skipped INTEGER NOT NULL,
    score_percent INTEGER NOT NULL,
    duration_seconds INTEGER NOT NULL,
    started_at BIGINT NOT NULL,
    completed_at BIGINT NOT NULL,
    answers_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS attempts_set_id_idx ON attempts (set_id);
CREATE INDEX IF NOT EXISTS attempts_completed_at_idx ON attempts (completed_at);

-- Optional: verify what has synced.
-- SELECT COUNT(*) AS sets FROM sets;
-- SELECT COUNT(*) AS questions FROM questions;
-- SELECT set_title, score_percent, completed_at FROM attempts ORDER BY completed_at DESC;
