# SpendOS Deployment Guide

How to run SpendOS in production, keep it healthy and recover when something goes wrong.
For local development see [SETUP.md](./SETUP.md); for the security model see [SECURITY.md](./SECURITY.md).

---

## 1. What you are deploying

| Component | Image / artifact | Notes |
|-----------|------------------|-------|
| Backend | `backend/Dockerfile` (Spring Boot 3.5, Java 17) | Stateless; scale horizontally behind a load balancer |
| Frontend | `frontend/Dockerfile` (nginx serving the Vite build) | Proxies `/api/` to the backend and sets browser security headers |
| Database | PostgreSQL 16 | The only stateful component; back it up |
| Redis | Optional | Not required; caching is in-process by default (`REDIS_ENABLED=false`) |

Database migrations (`backend/src/main/resources/db/migration`) run automatically on backend start via Flyway.
In production `validate-on-migrate` is on and `baseline-on-migrate` is off, so a changed migration file stops the
app instead of silently diverging.

## 2. Production checklist

- [ ] **TLS** terminated at the load balancer or ingress with a valid certificate; HTTP redirects to HTTPS.
      The API sends HSTS on HTTPS requests; enable HSTS on the proxy too.
- [ ] **Secrets** come from a secret manager or orchestrator secrets, never from files in the image:
      `JWT_SECRET` (32+ random bytes, e.g. `openssl rand -base64 48`), `DATABASE_PASSWORD`.
      The backend refuses to start with `APP_ENV=production` if either is a `CHANGE_ME` placeholder.
- [ ] `SPRING_PROFILES_ACTIVE=prod`, `APP_ENV=production`.
- [ ] `CORS_ALLOWED_ORIGINS` set to the exact public origin (wildcards are ignored).
- [ ] `SWAGGER_ENABLED` left `false`.
- [ ] `RATE_LIMIT_ENABLED` left `true` (default). Rate limits are in-memory per instance; with several backend
      instances, keep sticky sessions or accept per-instance limits.
- [ ] Database backups configured and a restore tested (section 4).
- [ ] Monitoring and alerting on the health endpoints and logs (section 5).
- [ ] `FEATURE_DEMO_MODE_ENABLED=false` unless the deployment is meant to offer the public demo.
- [ ] `ASSISTANT_LLM_ENABLED` left `false` unless you accept sending questions and aggregated figures to
      Anthropic (see "AI assistant" below).
- [ ] Dependency scans green in CI (`security` job: OWASP dependency-check fails at CVSS 7, `npm audit`).

## 3. Configuration reference

| Variable | Default | Purpose |
|----------|---------|---------|
| `DATABASE_URL` | – (required in prod) | JDBC URL, e.g. `jdbc:postgresql://db:5432/spendos` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | – (required in prod) | Database credentials |
| `DATABASE_HIKARI_MAXIMUM_POOL_SIZE` | 20 (prod profile: 30) | Connection pool size |
| `JWT_SECRET` | – (required in prod) | HMAC signing key, at least 32 bytes |
| `JWT_ACCESS_TOKEN_EXPIRATION` / `JWT_REFRESH_TOKEN_EXPIRATION` | 1 h / 30 days (ms) | Token lifetimes |
| `CORS_ALLOWED_ORIGINS` | localhost | Comma-separated allowed origins |
| `APP_ENV` | development | `production` enables the secret checks |
| `RATE_LIMIT_ENABLED` | true | Only disable for load tests on a private stack |
| `SWAGGER_ENABLED` | false in prod | API docs at `/api/swagger-ui.html` |
| `FILE_UPLOAD_MAX_SIZE` | 52428800 | Statement upload limit in bytes (nginx allows 51 MB) |
| `USERS_PURGE_SCHEDULE` | `0 30 3 * * *` | When deleted (30+ days) and demo (1+ day) accounts are purged |
| `FEATURE_*_ENABLED` | true | Feature toggles (assistant, demo mode, health score, predictions, detectors) |
| `ASSISTANT_LLM_ENABLED`, `ANTHROPIC_API_KEY`, `ASSISTANT_LLM_MODEL` | false, –, claude-haiku-4-5 | Optional LLM rewording of assistant answers |
| `SSL_ENABLED`, `SSL_KEYSTORE_PATH`, `SSL_KEYSTORE_PASSWORD` | false | In-process TLS if not terminated upstream |

### AI assistant

Answers are always computed by the backend from the user's data. With `ASSISTANT_LLM_ENABLED=true` the
question, the draft answer and aggregated findings (no account numbers or raw transactions) are sent to
Anthropic for rewording; any reworded text containing a number the backend did not supply is discarded.
A missing API key with the LLM enabled stops startup.

## 4. Backups and restore

PostgreSQL holds everything. A nightly logical backup plus point-in-time recovery from your provider is
recommended. Minimal logical backup:

```bash
pg_dump --format=custom --no-owner --dbname="$DATABASE_URL_PG" > spendos-$(date +%F).dump
# restore into an empty database
pg_restore --no-owner --dbname="$TARGET_DATABASE_URL" spendos-2026-09-21.dump
```

Keep backups encrypted and for no longer than your retention policy: deleted accounts are purged from the
live database after 30 days (SECURITY.md) but remain in older backups until those expire.
`audit_logs` is append-only (a trigger rejects updates and deletes), so restores never alter history.

## 5. Monitoring

| Check | Endpoint | Expect |
|-------|----------|--------|
| Liveness | `GET /api/actuator/health/liveness` | 200 `UP` |
| Readiness (includes database) | `GET /api/actuator/health/readiness` | 200 `UP` |
| Public summary | `GET /api/v1/health` | 200 with `status: UP`; 503 when the database is down |
| Frontend | `GET /` on port 3000 | 200 |

Logs are structured single lines with a request ID (`X-Request-ID`, also returned to clients and shown in
error responses), user ID and action; they never contain passwords, tokens, email addresses or statement
contents. Alert on: readiness failures, 5xx rate, `Failed login attempt` spikes, `429` spikes, and
`Assistant explanation rejected` warnings if the LLM is enabled. Sentry or similar can be attached to the
backend and frontend if error tracking is wanted (not bundled).

## 6. Deploying a new version

1. CI must be green: backend tests with coverage gate, frontend tests/lint/type-check/build, security scans.
2. Build and push images (the `docker-build` CI job pushes to GHCR on `main`).
3. Take a database backup.
4. Roll out the backend; Flyway applies new migrations on the first instance to start. Migrations are
   additive, so the previous version keeps working during a rolling update.
5. Roll out the frontend.
6. Smoke test: `/api/v1/health`, sign in, dashboard. The `integration-test` CI job does the same against compose.

Rollback: redeploy the previous images. Migrations are not rolled back automatically; restore the backup
only if a migration itself was faulty.

## 7. Performance baseline

Measured with `load/k6-core-api.js` (100 concurrent users, demo accounts with ~6 months of data each,
dashboard/transactions/insights/budgets) on a single backend container. Re-run after significant changes.
Targets from DEVELOPMENT_PLAN.md: API median < 200 ms, dashboard < 2 s, error rate < 1%.
Record each run's results (median, p95, error rate, hardware) in CHANGELOG.md.

## 8. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Backend exits with `JWT_SECRET is a placeholder` | Production started with the example secret | Set a real `JWT_SECRET` |
| Backend exits with a Flyway validation error | A migration file changed after it was applied | Restore the original file; add changes as a new `V<n>__*.sql` |
| `401` right after deploy for everyone | `JWT_SECRET` changed | Expected: tokens are signed with the old key. Users sign in again |
| Frontend loads but API calls fail | nginx cannot reach `backend:8080`, or CORS origin mismatch | Check the `/api/` proxy target and `CORS_ALLOWED_ORIGINS` |
| Every API call returns `502` and the backend keeps restarting; its log shows `password authentication failed for user "spendos_user"` | `DATABASE_PASSWORD` was changed after the Postgres volume was first created. Postgres only applies `POSTGRES_PASSWORD` when it initializes an empty volume and ignores the new value on an existing one | Sync the existing role to the new value without printing it: `docker exec spendos-postgres sh -c 'echo "ALTER ROLE \"$POSTGRES_USER\" WITH PASSWORD :'\''pw'\'';" \| psql -v ON_ERROR_STOP=1 -v pw="$POSTGRES_PASSWORD" -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`, then `docker restart spendos-backend`. Data is kept. (`docker compose down -v` also works but deletes all data) |
| Uploads fail with 413 | File over 50 MB, or a proxy in front with a lower body limit | Raise the proxy limit or split the statement |
| Many `429` responses | Rate limits (SECURITY.md) | Expected under abuse; check for a misbehaving client |
| Health score / insights look stale | Per-user cache (5 min) | Changes to transactions evict it immediately; otherwise wait |
| Assistant answers never reworded | LLM disabled, failed, or rewording rejected | Check `Assistant explanation` warnings in logs; answers remain correct |
