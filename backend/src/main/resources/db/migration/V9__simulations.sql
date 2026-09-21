-- Saved what-if scenarios with the results computed when they were created.
CREATE TABLE IF NOT EXISTS simulations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    simulation_name VARCHAR(255) NOT NULL,
    scenarios JSONB NOT NULL,
    results JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_simulations_user ON simulations(user_id, created_at DESC);
