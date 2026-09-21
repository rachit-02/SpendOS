-- Store revoked JWT IDs for logout and single-use refresh-token rotation.
CREATE TABLE revoked_tokens (
    token_id VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);
