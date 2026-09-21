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
- 📈 **Transaction Import** - CSV/statement file upload with smart parsing
- 🏷️ **Auto-Categorization** - Intelligent merchant and category matching
- 💡 **Financial Insights** - Spending anomalies, money leaks, recurring payments
- 📋 **Budgeting** - Create and track monthly budgets with alerts
- 🎯 **Goals** - Save toward financial goals with progress tracking
- 📅 **Monthly Autopsy** - Comprehensive end-of-month financial report
- 🔮 **Spending Prediction** - Forecast month-end spending based on patterns
- ❓ **AI Assistant** - Ask questions about your finances in natural language
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

# Start full stack
docker-compose up

# Access application
Frontend: http://localhost:3000
Backend API: http://localhost:8080
API Docs: http://localhost:8080/swagger-ui.html
```

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
# Create database
createdb spendos

# Run migrations
cd backend
mvn flyway:migrate

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

---

## 🔐 Security & Privacy

**SpendOS is built with security and privacy as first-class concerns.**

### What We Protect

- ✅ User authentication with JWT tokens
- ✅ Password hashing with bcrypt
- ✅ Authorization checks on all endpoints
- ✅ SQL injection prevention
- ✅ XSS protection
- ✅ CSRF protection
- ✅ Rate limiting
- ✅ Audit logging
- ✅ Encrypted secrets management

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

**Backend:**
```bash
cd backend
mvn test                    # Unit tests
mvn verify                  # All tests + integration
mvn jacoco:report          # Coverage report
```

**Frontend:**
```bash
cd frontend
npm test                    # Unit tests
npm run test:e2e           # E2E tests
npm run test:coverage      # Coverage report
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
mvn spotbugs:check         # Bug detection
mvn pmd:check              # Code analysis
mvn checkstyle:check       # Style checking
```

**Frontend:**
```bash
npm run lint               # ESLint
npm run format             # Prettier
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

```bash
# Backend (8080)
lsof -i :8080
kill -9 <PID>

# Frontend (3000)
lsof -i :3000
kill -9 <PID>

# PostgreSQL (5432)
lsof -i :5432
kill -9 <PID>
```

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
- [ ] Phase 1: Project setup
- [ ] Phase 2: Authentication
- [ ] Phase 3: Database & transactions
- [ ] Phase 4: CSV import
- [ ] Phase 5: Transaction management
- [ ] Phase 6: Dashboard
- [ ] Phase 7: Analytics
- [ ] Phase 8: Budgets & recurring
- [ ] Phase 9: Insights & anomalies
- [ ] Phase 10: Monthly autopsy
- [ ] Phase 11: Predictions & what-if
- [ ] Phase 12: AI assistant
- [ ] Phase 13: Merchant normalization
- [ ] Phase 14: Health score
- [ ] Phase 15: Security hardening
- [ ] Phase 16: Testing & launch

**Current Status:** Documentation Complete, Ready for Development ✅

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

