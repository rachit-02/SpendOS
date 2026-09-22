# SpendOS

An intelligent personal financial operating system that transforms authorized financial transaction data into understandable financial intelligence.

**Mission:** Help individuals understand where their money goes, why, and how to make better financial decisions.

---

## 🎯 What SpendOS Does

SpendOS answers critical financial questions:

- Where did my money go?
- Why did I spend more this month?
- What spending patterns are unusual?
- What recurring payments do I have?
- Where are my money leaks?
- How much am I likely to spend by the end of the month?
- Can I afford a planned purchase?
- How much could I save if I changed a spending habit?
- What happened financially this month?
- What should I pay attention to next month?

---

## ✨ Core Features (MVP)

- 🔐 **Secure Authentication** - JWT-based login with no financial credential storage
- 📊 **Beautiful Dashboard** - Premium visualization of financial overview
- 📈 **Transaction Import** - CSV or text-based PDF bank statements, with smart parsing (scanned PDFs are not supported)
- 🏷️ **Auto-Categorization** - Intelligent merchant and category matching
- 💡 **Financial Insights** - Spending anomalies, money leaks, recurring payments
- 📋 **Budgeting** - Create and track monthly budgets with alerts
- 🎯 **Goals** - Save toward financial goals with progress tracking
- 📅 **Monthly Autopsy** - Comprehensive end-of-month financial report
- 🔮 **Spending Prediction** - Forecast month-end spending based on patterns
- 🧮 **Planning & What-if** - "Can I afford this?", scenario simulator and comparisons
- ❓ **AI Assistant** - Ask questions about your finances in natural language (answers are computed from your data; an optional LLM only rewords them)
- 🏪 **Merchant Corrections** - Fix merchant names and categories once; they apply to past and future transactions
- ❤️ **Financial Health Score** - Explainable 0-100 score with history and concrete advice
- 📦 **Your Data, Your Control** - Export everything as a ZIP; deleted accounts are purged after 30 days
- 🧪 **Demo Mode** - Try every feature on realistic synthetic data, no sign-up
- 📱 **Mobile Responsive** - Works seamlessly on desktop and mobile

---

## 🏗️ Architecture

**SpendOS is a modular monolith** with clear separation of concerns.

### Technology Stack

**Frontend:**
- React 18 + TypeScript
- Vite (fast build tool)
- Tailwind CSS
- shadcn/ui
- Recharts (visualizations)

**Backend:**
- Java 17 + Spring Boot 3.x
- Spring Security (JWT)
- Spring Data JPA
- PostgreSQL
- Redis (optional, for caching)

**Infrastructure:**
- Docker & Docker Compose
- GitHub Actions CI/CD
- PostgreSQL database

### Project Structure

```
SpendOS/
├── backend/              # Spring Boot application
├── frontend/             # React application
├── docs/                 # Documentation
├── docker-compose.yml    # Local development stack
└── README.md
```

---

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Node.js 18+ (for frontend development)
- Java 17+ (for backend development)
- PostgreSQL 14+ (for local development)

### Option 1: Docker Compose (Recommended)

```bash
# Clone repository
git clone https://github.com/yourname/spendos.git
cd spendos

# Start full stack (builds images, runs database migrations automatically)
cp .env.example .env            # optional: review settings first
docker compose up -d --build

# Access application
Frontend:    http://localhost:3000   (click "Try the demo" on the sign-in page)
Backend API: http://localhost:8080/api/v1/health
API docs:    http://localhost:8080/api/swagger-ui.html (development only)
```

To try it with your own flow, import [`samples/sample-bank-statement.csv`](./samples/sample-bank-statement.csv)
(two months of synthetic transactions in a typical Indian bank export format) on the Import page.

If port 8080 is taken (Jenkins often uses it), start with `BACKEND_PORT=8081 docker compose up -d --build`;
the frontend reaches the API through its own `/api` proxy, so nothing else changes.

### Option 2: Local Development

**Backend:**
```bash
cd backend
mvn clean install
mvn spring-boot:run
# Backend runs on http://localhost:8080
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
# Frontend runs on http://localhost:3000
```

### Option 3: Database Setup (if not using Docker)

```bash
# Create database and user (see SETUP.md for details)
createdb spendos

# Migrations (backend/src/main/resources/db/migration) run automatically on startup via Flyway
cd backend
mvn spring-boot:run

# Check health
curl http://localhost:8080/api/v1/health
```

---

## 📚 Documentation

- [PRODUCT_SPEC.md](./PRODUCT_SPEC.md) - Detailed feature specifications
- [ARCHITECTURE.md](./ARCHITECTURE.md) - System architecture and design
- [DATABASE_DESIGN.md](./DATABASE_DESIGN.md) - Database schema and design
- [API_DESIGN.md](./API_DESIGN.md) - REST API endpoints
- [SECURITY.md](./SECURITY.md) - Security architecture and best practices
- [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md) - Implementation roadmap
- [CONTRIBUTING.md](./CONTRIBUTING.md) - Contribution guidelines
- [SETUP.md](./SETUP.md) - Detailed setup instructions
- [DEPLOYMENT.md](./DEPLOYMENT.md) - Production deployment, operations and troubleshooting
- [CHANGELOG.md](./CHANGELOG.md) - Release notes

---

## 🔐 Security & Privacy

**SpendOS is built with security and privacy as first-class concerns.**

### What We Protect

- ✅ User authentication with JWT tokens
- ✅ Password hashing with bcrypt
- ✅ Authorization checks on all endpoints
- ✅ SQL injection prevention
- ✅ XSS protection
- ✅ CSRF not applicable by design (bearer tokens in headers, no auth cookies)
- ✅ Rate limiting
- ✅ Append-only audit logging
- ✅ Security headers (CSP, frame, referrer and permissions policies, HSTS)
- ✅ Secrets only from the environment; production refuses placeholder secrets
- ✅ Data export and permanent deletion 30 days after account deletion

### What We DON'T Do

- ❌ Never store UPI PINs, OTPs, banking passwords
- ❌ Never store credit card information
- ❌ Never scrape financial services
- ❌ Never collect unauthorized data
- ❌ Never sell user data
- ❌ Never track across services

**See [SECURITY.md](./SECURITY.md) for complete security documentation.**

---

## 📖 API Examples

### Register

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123!",
    "fullName": "John Doe"
  }'
```

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123!"
  }'
```

### Get Dashboard

```bash
curl -X GET http://localhost:8080/api/v1/dashboard \
  -H "Authorization: Bearer {accessToken}"
```

### Import Transactions

```bash
curl -X POST http://localhost:8080/api/v1/imports/upload \
  -H "Authorization: Bearer {accessToken}" \
  -F "file=@transactions.csv" \
  -F "accountId=550e8400-e29b-41d4-a716-446655440000"
```

### Query Assistant

```bash
curl -X POST http://localhost:8080/api/v1/assistant/query \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Why did I spend more this month?"
  }'
```

**Full API documentation:** [API_DESIGN.md](./API_DESIGN.md)

---

## 💻 Development

### Running Tests

**Backend** (needs Docker running: integration tests use Testcontainers PostgreSQL):
```bash
cd backend
mvn test                    # Unit + integration tests
mvn verify                  # Tests + JaCoCo report + 80% line-coverage gate
                            # report: target/site/jacoco/index.html
```

**Frontend:**
```bash
cd frontend
npm test                    # Component, page and accessibility (axe) tests
npm run test:coverage       # Coverage with a 70% gate
npm run type-check && npm run lint
npm run test:e2e            # Playwright, against a running stack (docker compose up);
                            # E2E_BASE_URL overrides http://localhost:3000
```

**Load test** (k6, against a private stack started with `RATE_LIMIT_ENABLED=false`):
```bash
docker run --rm -i -e BASE_URL=http://host.docker.internal:3000 grafana/k6 run - < load/k6-core-api.js
```

### Building for Production

**Backend:**
```bash
cd backend
mvn clean package -DskipTests
docker build -t spendos-backend .
```

**Frontend:**
```bash
cd frontend
npm run build              # Creates optimized build
docker build -t spendos-frontend .
```

### Code Quality

**Backend:**
```bash
mvn spotbugs:check                                   # Bug detection
mvn checkstyle:check                                 # Style checking
mvn org.owasp:dependency-check-maven:check           # Known-vulnerability scan (set NVD_API_KEY)
```

**Frontend:**
```bash
npm run lint               # ESLint
npm run format             # Prettier
npm audit --omit=dev       # Production dependency advisories
```

---

## 🐛 Troubleshooting

### Database Connection Failed

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Check connection string in .env
DATABASE_URL=postgresql://user:password@localhost:5432/spendos

# Run migrations manually
cd backend
mvn flyway:migrate
```

### Frontend Can't Connect to Backend

```bash
# Check backend is running
curl http://localhost:8080/api/v1/health

# Check CORS settings in application.yml
# Should allow http://localhost:3000

# Clear browser cache and restart frontend
npm run dev
```

### Port Already in Use

Prefer moving SpendOS over killing other services: `BACKEND_PORT`, `FRONTEND_PORT` and `POSTGRES_PORT`
change the host ports in `docker-compose.yml`.

```bash
# Find what holds a port
lsof -i :8080                       # macOS / Linux
netstat -ano | findstr :8080        # Windows
```

### Backend tests fail with "Could not find a valid Docker environment"

Integration tests need a running Docker daemon. Testcontainers 1.21.4+ is required for Docker Engine 29.

### "429 Too Many Requests"

The API enforces the rate limits in SECURITY.md (e.g. 5 sign-in attempts per 15 minutes). Wait for the
`Retry-After` period. See [DEPLOYMENT.md](./DEPLOYMENT.md) for more operational troubleshooting.

---

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

**Code Quality Standards:**
- Write tests for all business logic
- Maintain 80%+ test coverage
- Follow existing code style
- Document public APIs
- Security review before merge
- No hardcoded secrets

**Commit Convention:**
```
feat(module): add new feature
fix(module): fix bug
test(module): add tests
docs(module): update documentation
refactor(module): improve code structure
security(module): fix security issue
```

---

## 📋 MVP Checklist

- [x] Architecture designed
- [x] Database schema created
- [x] API specification complete
- [x] Security architecture defined
- [x] Development plan created
- [x] Phase 1: Project setup
- [x] Phase 2: Authentication
- [x] Phase 3: Database & transactions
- [x] Phase 4: CSV import
- [x] Phase 5: Transaction management
- [x] Phase 6: Dashboard
- [x] Phase 7: Analytics
- [x] Phase 8: Budgets & recurring
- [x] Phase 9: Insights & anomalies
- [x] Phase 10: Monthly autopsy
- [x] Phase 11: Predictions & what-if
- [x] Phase 12: AI assistant
- [x] Phase 13: Merchant normalization
- [x] Phase 14: Health score
- [x] Phase 15: Security hardening
- [x] Phase 16: Testing & launch

**Current Status:** MVP feature-complete (v1.0.0). See [CHANGELOG.md](./CHANGELOG.md).

---

## 📊 Project Statistics

| Metric | Value |
|--------|-------|
| Documentation | 6 files |
| Estimated LOC (Backend) | 15,000-20,000 |
| Estimated LOC (Frontend) | 10,000-15,000 |
| Test Coverage Target | 80%+ |
| Development Timeline | 2-2.5 months |
| Supported Users (MVP) | 1,000+ concurrent |
| Database Size (1M transactions) | ~500MB |

---

## 🗺️ Roadmap

### MVP (Next 2-3 months)
- ✅ Core features (import, dashboard, insights)
- ✅ User authentication and profiles
- ✅ Budget and goal tracking
- ✅ Mobile responsive design
- ✅ Comprehensive testing

### Phase 2 (Months 4-6)
- 📱 Native mobile apps (iOS/Android)
- 📧 Email reports and alerts
- 🔔 SMS notifications
- 🎨 Advanced customization
- 📊 More sophisticated ML

### Phase 3 (Months 7-12)
- 🏦 Official bank integrations (OAuth2)
- 💱 Multi-currency support
- 📈 Investment tracking
- 👥 Household finance sharing
- 🌍 International expansion

### Phase 4+ (Year 2+)
- 🤖 Advanced AI features
- 📲 Smartwatch app
- 💳 Personal finance credit line
- 🔗 API for third-party integrations
- ☁️ Enterprise version

---

## 📞 Support

- **Questions?** Create an issue on GitHub
- **Bug report?** Include steps to reproduce
- **Feature request?** Open a discussion
- **Security issue?** Email security@spendos.com (private disclosure)

---

## 📄 License

This project is licensed under the MIT License - see [LICENSE](./LICENSE) file for details.

---

## 👥 Team

SpendOS is built with ❤️ by developers who believe personal finance should be transparent, secure, and empowering.

---

## 🙏 Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot)
- [React](https://react.dev)
- [PostgreSQL](https://www.postgresql.org)
- [Tailwind CSS](https://tailwindcss.com)
- [shadcn/ui](https://ui.shadcn.com)
- [Recharts](https://recharts.org)

---

**Building SpendOS. Making personal finance understandable. One transaction at a time.** 💰📊✨

