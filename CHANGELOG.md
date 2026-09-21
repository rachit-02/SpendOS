# Changelog

All notable changes to SpendOS. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-09-21

First feature-complete MVP, built phase by phase from DEVELOPMENT_PLAN.md.

### Added

- **Accounts and security**
  - Registration, sign-in, single-use refresh tokens and logout with server-side revocation.
  - Password change and account deletion invalidate every existing token.
  - Profile and preferences.
- **Transactions**
  - Accounts, transaction CRUD, bulk edit and delete.
  - Full-text and trigram search, filters, and CSV export.
- **Statement import**
  - Async CSV pipeline with delimiter, date-format and column detection.
  - Row validation with per-row errors.
  - Duplicate detection (exact and fuzzy), import history and progress.
- **Categorization and merchants**
  - Deterministic rules engine with confidence scores.
  - Merchant normalization by exact, contains, prefix and regex rules, with a Levenshtein fallback for typos ("SWIGY" → Swiggy).
  - Per-user merchant corrections that apply to past and future transactions.
  - Suggested matches for unrecognized statement names.
- **Dashboard and analytics**
  - Monthly overview, category and merchant breakdowns, and trends.
  - Month-to-month and year-over-year comparisons.
  - CSV and PDF export.
- **Budgets**
  - Monthly, quarterly, annual and custom budgets with category limits.
  - Pace tracking and threshold alerts.
- **Recurring payments**: automatic subscription detection with confirm and dismiss.
- **Insights**: money leaks, anomalies with adjustable sensitivity, savings opportunities and trends, each with the transactions behind it.
- **Monthly money autopsy**: an end-of-month report with a PDF version, printing, and scheduled generation.
- **Planning**
  - Month-end spending forecast with a range and a stated confidence.
  - "Can I afford this?" check.
  - What-if simulator with saved and compared scenarios.
  - Savings goals with projections based on actual savings.
- **Financial assistant**
  - Natural-language questions answered from the user's own data by rule-based intent detection and SQL: why spending changed, where money went, category and merchant totals, largest purchases, subscriptions, month comparisons, and savings.
  - Optional Claude rewording, off by default. It is rejected if it introduces any number the backend did not supply.
- **Financial health score**
  - Explainable 0-100 score across five weighted factors.
  - Monthly history, an explanation of what changed, and recommendations using the user's own amounts.
- **Data rights**
  - `GET /users/me/export` returns a ZIP of all user data.
  - Deleted accounts are permanently purged after 30 days; audit logs are kept anonymized.
- **Demo mode**
  - One-click demo account with about six months of realistic synthetic data, resettable and removed after a day.
- **Operations**
  - Docker Compose stack and a CI/CD pipeline covering tests, coverage gates, dependency scans, image builds and a compose smoke test.
  - A readiness probe that includes the database.
  - DEPLOYMENT.md.

### Security

- Stateless JWT authentication with all non-public endpoints verified. A test sends missing, unsigned (`alg=none`), tampered and Basic credentials to every endpoint and expects 401.
- Every lookup is user-scoped; other users' records return 404.
- Rate limits per SECURITY.md, plus limits on the assistant (30/min), data export (5/h) and demo creation (5/h per IP).
- Security headers on the API and the frontend: CSP, X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy, and HSTS over HTTPS.
- Spreadsheet formula-injection protection in all CSV exports.
- `audit_logs` is append-only, enforced by a database trigger. Profile, password, preference, deletion and export actions are audited, profile changes with their old values.
- In production, startup is refused with placeholder secrets.
- Dependencies:
  - Upgraded to Spring Boot 3.5.15 with patched transitive libraries; no known vulnerabilities (OSV scan).
  - Upgraded to React Router 7; production npm dependencies report 0 advisories.

### Fixed

- Container health checks used `localhost`, which resolves to IPv6 `::1` in Alpine while nginx listens on IPv4
  only, so the frontend was reported unhealthy although it served traffic; checks now use `127.0.0.1`.
- `RATE_LIMIT_ENABLED` in `.env` had no effect; it is now wired to `app.rate-limit.enabled`.
- The readiness probe now includes the database, so instances without a database connection stop receiving traffic.

### Known limitations

- Rate limits are held in memory per backend instance.
- `vite` 5 and `vitest` 1 (dev server and test runner only, not shipped) have published advisories. Upgrading to vite 8 / vitest 5 is blocked by a peer-dependency conflict and is planned.
- Colour contrast is not covered by the automated accessibility tests (jsdom cannot compute it) and still needs a browser-based check.
- Currency amounts in backend-generated sentences use Western digit grouping (₹360,000), while the UI uses Indian grouping (₹3,60,000).
