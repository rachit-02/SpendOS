-- Substring search (ILIKE '%term%') on descriptions and merchant names cannot use B-tree indexes.
-- pg_trgm is a trusted extension (PostgreSQL 13+), so the database owner can enable it.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_transactions_description_trgm
    ON transactions USING gin (lower(description) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_transactions_raw_description_trgm
    ON transactions USING gin (lower(raw_description) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_merchants_name_lower_trgm
    ON merchants USING gin (merchant_name_lower gin_trgm_ops);

-- Amount-range filters and sorting by amount within a user's data.
CREATE INDEX IF NOT EXISTS idx_transactions_user_amount ON transactions(user_id, amount);
