-- Tokens issued before this instant are rejected. Set on password change and account deletion so
-- that every outstanding access/refresh token for the user stops working immediately.
ALTER TABLE users ADD COLUMN IF NOT EXISTS tokens_valid_after TIMESTAMP WITH TIME ZONE;
