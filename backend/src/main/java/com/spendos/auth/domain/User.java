package com.spendos.auth.domain;

import com.spendos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Tokens with an issued-at before this instant are rejected (password change, deletion). */
    @Column(name = "tokens_valid_after")
    private Instant tokensValidAfter;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.emailVerified = false;
        this.active = true;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePassword(String newPasswordHash, Instant tokensValidAfter) {
        this.passwordHash = newPasswordHash;
        this.tokensValidAfter = tokensValidAfter;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public Instant getTokensValidAfter() {
        return tokensValidAfter;
    }

    /** Soft delete: data is retained for 30 days for recovery, then purged. */
    public void softDelete(LocalDateTime deletedAt, Instant tokensValidAfter) {
        this.deletedAt = deletedAt;
        this.active = false;
        this.tokensValidAfter = tokensValidAfter;
    }

    /** True when the user may authenticate and use tokens issued at {@code issuedAt}. */
    public boolean acceptsTokenIssuedAt(Instant issuedAt) {
        return active && deletedAt == null
                && (tokensValidAfter == null || issuedAt == null || !issuedAt.isBefore(tokensValidAfter));
    }
}
