-- Monthly health score snapshots, so the score can be tracked over time.
-- financial_health_metrics keeps only the latest score per user (DATABASE_DESIGN.md table 18).
CREATE TABLE IF NOT EXISTS financial_health_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_month DATE NOT NULL,
    health_score INTEGER,
    score_factors JSONB NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_health_history_month UNIQUE (user_id, period_month),
    CONSTRAINT valid_history_score CHECK (health_score IS NULL OR (health_score >= 0 AND health_score <= 100))
);

CREATE INDEX IF NOT EXISTS idx_health_history_user ON financial_health_history(user_id, period_month DESC);
