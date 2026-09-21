-- Evidence behind each insight (supporting transaction IDs, baselines, normal ranges) so every
-- insight can show the transactions it is based on, and a score used to rank insights.
ALTER TABLE insights ADD COLUMN IF NOT EXISTS details JSONB;
ALTER TABLE insights ADD COLUMN IF NOT EXISTS importance_score NUMERIC(6, 2) DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_insights_user_period ON insights(user_id, period_start_date, period_end_date);
