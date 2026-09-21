-- Stored monthly "money autopsy" reports: a snapshot of the month as generated, so a report reads the
-- same later even if the rules that produced it evolve.
CREATE TABLE IF NOT EXISTS monthly_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    report_json JSONB NOT NULL,
    is_complete BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT valid_report_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT unique_user_report_month UNIQUE (user_id, period_year, period_month)
);

CREATE INDEX IF NOT EXISTS idx_monthly_reports_user ON monthly_reports(user_id, period_year DESC, period_month DESC);
