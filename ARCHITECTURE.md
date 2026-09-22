# SpendOS Architecture

## Architectural Approach

**Modular Monolith** - A single deployable application with clear module boundaries, designed to evolve into microservices only if/when there's demonstrated need.

```
SpendOS Monolith
├── Auth Module
├── Users Module
├── Transactions Module
├── Imports Module
├── Merchants Module
├── Categories Module
├── Analytics Module
├── Insights Module
├── Budgets Module
├── Goals Module
├── Recurring Module
├── Notifications Module
├── Audit Module
└── Health Module
```

---

## Technology Stack

### Frontend
- **React 18+** - UI library
- **TypeScript** - Type safety
- **Vite** - Fast build tool
- **Tailwind CSS** - Utility-first styling
- **shadcn/ui** - Accessible component system
- **Recharts** - Financial data visualization
- **React Query** - Server state management
- **Zustand or Context API** - Client state management
- **Vitest** - Unit testing
- **React Testing Library** - Component testing
- **Playwright** - E2E testing

### Backend
- **Java 17+** - Language
- **Spring Boot 3.x** - Framework
- **Spring Security** - Authentication/Authorization
- **Spring Data JPA** - ORM
- **Spring Validation** - Input validation
- **Spring AOP** - Aspect-oriented programming
- **JUnit 5** - Testing
- **Mockito** - Mocking
- **Spring Boot Test** - Integration testing
- **Jackson** - JSON serialization
- **Lombok** - Reduce boilerplate
- **MapStruct** - DTO mapping

### Database
- **PostgreSQL 14+** - Primary database
- **Flyway** - Database migrations
- **HikariCP** - Connection pooling

### Caching
- **Redis** - For high-frequency queries (optional MVP, required later)
- **Spring Cache abstractions** - Pluggable caching

### Authentication
- **JWT (JSON Web Tokens)** - Stateless authentication
- **bcrypt** - Password hashing
- **Design pattern allows OAuth2/OIDC addition** later

### Infrastructure & DevOps
- **Docker** - Containerization
- **Docker Compose** - Local development orchestration
- **GitHub Actions** - CI/CD
- **GitHub Packages** or **Docker Hub** - Image registry

### Documentation
- **OpenAPI 3.0 / Swagger** - API documentation
- **Markdown** - Architecture & setup docs

### Observability
- **SLF4J + Logback** - Structured logging
- **Spring Boot Actuator** - Health checks and metrics
- **Request ID correlation** - Trace requests across system

---

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│           CLIENT LAYER (Browser/Mobile)             │
│  React SPA (TypeScript, Vite, Tailwind, shadcn)    │
└────────────┬────────────────────────────────────────┘
             │
             │ HTTPS/REST
             ↓
┌─────────────────────────────────────────────────────┐
│        API GATEWAY / LOAD BALANCER (Future)         │
└────────────┬────────────────────────────────────────┘
             │
             ↓
┌─────────────────────────────────────────────────────┐
│            SPRING BOOT APPLICATION                  │
├─────────────────────────────────────────────────────┤
│  Controllers (REST Endpoints)                       │
│  ├─ AuthController                                  │
│  ├─ TransactionController                           │
│  ├─ ImportController                                │
│  ├─ MerchantController                              │
│  ├─ CategoryController                              │
│  ├─ AnalyticsController                             │
│  ├─ InsightController                               │
│  ├─ BudgetController                                │
│  └─ AssistantController                             │
├─────────────────────────────────────────────────────┤
│  Service Layer (Business Logic)                     │
│  ├─ AuthService                                     │
│  ├─ TransactionService                              │
│  ├─ ImportService                                   │
│  ├─ MerchantNormalizationService                    │
│  ├─ CategoryEngine                                  │
│  ├─ AnalyticsEngine                                 │
│  ├─ InsightEngine                                   │
│  ├─ BudgetService                                   │
│  └─ AssistantService                                │
├─────────────────────────────────────────────────────┤
│  Domain Models (Business Rules)                     │
│  ├─ User, UserPreference                            │
│  ├─ Transaction, Account                            │
│  ├─ Merchant, MerchantMapping                       │
│  ├─ Category, Budget                                │
│  └─ ImportJob                                       │
├─────────────────────────────────────────────────────┤
│  Repository Layer (Data Access)                     │
│  ├─ UserRepository                                  │
│  ├─ TransactionRepository                           │
│  ├─ MerchantRepository                              │
│  ├─ CategoryRepository                              │
│  └─ ImportJobRepository                             │
├─────────────────────────────────────────────────────┤
│  Global Exception Handling                          │
│  Global Security Configuration                      │
│  Request/Response Interceptors                      │
└────────────┬────────────────────────────────────────┘
             │
    ┌────────┼────────┬──────────┐
    ↓        ↓        ↓          ↓
  ┌────┐  ┌────┐  ┌─────┐  ┌──────┐
  │PG  │  │Redis│  │Kafka│  │S3/   │
  │    │  │(opt)│  │(opt)│  │Blob  │
  └────┘  └────┘  └─────┘  └──────┘
  (Data)  (Cache) (Events) (Files)
```

---

## Module Organization

### Backend Structure

```
backend/
├── src/main/java/com/spendos/
│   ├── auth/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── security/
│   │   └── exception/
│   ├── users/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   └── mapper/
│   ├── transactions/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   ├── mapper/
│   │   └── validation/
│   ├── imports/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── parser/
│   │   └── validator/
│   ├── merchants/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── normalizer/
│   │   └── rules/
│   ├── categories/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── engine/
│   │   └── rules/
│   ├── analytics/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   └── engine/
│   ├── insights/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   ├── engine/
│   │   └── detectors/
│   ├── budgets/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   └── mapper/
│   ├── goals/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   └── mapper/
│   ├── recurring/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── dto/
│   │   ├── mapper/
│   │   └── detector/
│   ├── assistant/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   └── engine/
│   ├── audit/
│   │   ├── service/
│   │   ├── domain/
│   │   ├── repository/
│   │   └── aspect/
│   ├── health/
│   │   └── controller/
│   ├── common/
│   │   ├── exception/
│   │   ├── dto/
│   │   ├── util/
│   │   ├── config/
│   │   └── security/
│   └── SpendosApplication.java
├── src/main/resources/
│   ├── db/migration/ (Flyway migrations)
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-prod.yml
├── src/test/java/
│   └── (mirror of src/main structure)
├── pom.xml
├── Dockerfile
└── .dockerignore
```

### Frontend Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── common/
│   │   │   ├── Header.tsx
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Footer.tsx
│   │   │   └── Loading.tsx
│   │   ├── auth/
│   │   │   ├── LoginForm.tsx
│   │   │   ├── RegisterForm.tsx
│   │   │   └── ProtectedRoute.tsx
│   │   ├── dashboard/
│   │   │   ├── OverviewCards.tsx
│   │   │   ├── SpendingChart.tsx
│   │   │   ├── CategoryBreakdown.tsx
│   │   │   ├── TopMerchants.tsx
│   │   │   ├── RecentTransactions.tsx
│   │   │   └── Insights.tsx
│   │   ├── transactions/
│   │   │   ├── TransactionList.tsx
│   │   │   ├── TransactionDetail.tsx
│   │   │   ├── TransactionForm.tsx
│   │   │   └── BulkActions.tsx
│   │   ├── imports/
│   │   │   ├── ImportWizard.tsx
│   │   │   ├── CSVUpload.tsx
│   │   │   ├── ImportPreview.tsx
│   │   │   ├── ImportSummary.tsx
│   │   │   └── ImportHistory.tsx
│   │   ├── analytics/
│   │   │   ├── MonthlyChart.tsx
│   │   │   ├── CategoryAnalysis.tsx
│   │   │   └── TrendAnalysis.tsx
│   │   ├── insights/
│   │   │   ├── InsightCard.tsx
│   │   │   ├── MoneyLeakDetector.tsx
│   │   │   ├── RecurringPayments.tsx
│   │   │   └── Anomalies.tsx
│   │   ├── budgets/
│   │   │   ├── BudgetForm.tsx
│   │   │   ├── BudgetProgress.tsx
│   │   │   └── BudgetList.tsx
│   │   ├── goals/
│   │   │   ├── GoalForm.tsx
│   │   │   ├── GoalProgress.tsx
│   │   │   └── GoalList.tsx
│   │   ├── reports/
│   │   │   ├── MonthlyAutopsy.tsx
│   │   │   ├── PredictionView.tsx
│   │   │   └── AffordabilityCalc.tsx
│   │   ├── assistant/
│   │   │   └── ChatInterface.tsx
│   │   └── settings/
│   │       ├── ProfileSettings.tsx
│   │       ├── SecuritySettings.tsx
│   │       └── PreferencesSettings.tsx
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useTransactions.ts
│   │   ├── useDashboard.ts
│   │   ├── useAnalytics.ts
│   │   └── useInsights.ts
│   ├── services/
│   │   ├── api.ts
│   │   ├── authService.ts
│   │   ├── transactionService.ts
│   │   ├── importService.ts
│   │   ├── analyticsService.ts
│   │   └── assistantService.ts
│   ├── store/
│   │   ├── authStore.ts
│   │   ├── uiStore.ts
│   │   └── filterStore.ts
│   ├── types/
│   │   ├── auth.ts
│   │   ├── transaction.ts
│   │   ├── analytics.ts
│   │   ├── insights.ts
│   │   └── common.ts
│   ├── utils/
│   │   ├── formatters.ts
│   │   ├── validators.ts
│   │   ├── dateHelpers.ts
│   │   └── calculations.ts
│   ├── styles/
│   │   └── globals.css
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── RegisterPage.tsx
│   │   ├── DashboardPage.tsx
│   │   ├── TransactionsPage.tsx
│   │   ├── ImportPage.tsx
│   │   ├── AnalyticsPage.tsx
│   │   ├── InsightsPage.tsx
│   │   ├── BudgetsPage.tsx
│   │   ├── GoalsPage.tsx
│   │   ├── ReportsPage.tsx
│   │   ├── AssistantPage.tsx
│   │   ├── SettingsPage.tsx
│   │   ├── DemoPage.tsx
│   │   └── 404Page.tsx
│   ├── App.tsx
│   └── main.tsx
├── tests/
│   ├── unit/
│   ├── integration/
│   └── e2e/
├── public/
│   └── (static assets)
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.ts
├── package.json
└── Dockerfile
```

---

## Data Flow Patterns

### Authentication Flow
```
User Input (email/password)
    ↓
AuthController.register/login
    ↓
AuthService (validate credentials, hash password)
    ↓
UserRepository (find or create user)
    ↓
JwtTokenProvider (generate JWT)
    ↓
Response with JWT token
```

### Transaction Import Flow
```
User uploads a CSV or PDF statement
    ↓
ImportController.uploadFile
    ↓
CsvStatementParser or PdfStatementParser (chosen by file content; both return the
same ParsedFile of columns and rows, so every later step is shared)
    ↓
TransactionValidator (row-level validation)
    ↓
DuplicateDetector (identify duplicates)
    ↓
MerchantNormalizer (normalize merchant names)
    ↓
CategoryEngine (categorize transactions)
    ↓
TransactionRepository.saveAll
    ↓
ImportJobRepository.logSummary
    ↓
Response with import summary
```

### Financial Insights Flow
```
Analytics query triggered (monthly, on-demand)
    ↓
TransactionRepository (fetch user transactions)
    ↓
AnalyticsEngine (calculate aggregations)
    ↓
InsightEngine (detect patterns)
    ├─ AnomalyDetector (unusual spending)
    ├─ RecurringDetector (recurring payments)
    ├─ MoneyLeakDetector (small repeated expenses)
    └─ FinancialHealthCalculator (health score)
    ↓
Cache results (Redis)
    ↓
Response with insights
```

---

## Security Architecture

### Authentication & Authorization
- JWT tokens issued on login
- Tokens include user ID and roles
- All endpoints check token validity
- Spring Security filters on every request
- User resources scoped by user ID

### Data Access Control
```java
// Every transaction fetch must verify ownership
TransactionRepository.findByIdAndUserId(transactionId, currentUserId)

// Never expose user data without ownership check
Never: User.findById(userId)
Always: User.findByIdAndAuthenticatedUser(userId, currentUser)
```

### Encryption & Secrets
- Secrets stored in environment variables
- No secrets in Git
- bcrypt for password hashing
- HTTPS enforced in production
- Database connection pooling with SSL

### Input Validation
- DTO validation with @Valid annotations
- Custom validators for financial amounts
- CSV/PDF file type/size validation (PDF detected by its %PDF- signature)
- XSS protection through JSON encoding
- SQL injection protection via JPA parameterization

---

## Performance & Scalability Strategy

### Phase 1 (MVP)
- Single database connection pool
- Optional Redis for dashboard caching
- In-memory categorization rules
- Synchronous processing for imports

### Phase 2 (Production)
- Redis caching layer
- Query optimization with database indexes
- Bulk import async processing
- Background jobs for insight generation

### Phase 3+ (Future)
- Event-driven architecture (Kafka optional)
- Read replicas for analytics
- Elasticsearch for transaction search
- Microservices (only if demonstrated need)

---

## Testing Strategy

### Backend
- Unit tests for services and business logic
- Repository tests with embedded PostgreSQL
- Integration tests for API endpoints
- Security tests for authorization
- Transaction validation tests
- Merchant normalization tests
- Category engine tests
- Analytics calculation tests
- Target: 80%+ coverage for business logic

### Frontend
- Component unit tests
- Integration tests for user flows
- E2E tests for critical paths
- Accessibility tests
- Mobile responsiveness tests

---

## Deployment Architecture

### Local Development
```
docker-compose up
└─ PostgreSQL
└─ Redis (optional)
└─ Backend (Spring Boot)
└─ Frontend (Vite dev server)
```

### CI/CD Pipeline (GitHub Actions)
```
Code push
    ↓
Run tests (backend + frontend)
    ↓
Build Docker images
    ↓
Push to registry
    ↓
Deploy to staging
    ↓
Run integration tests
    ↓
(Manual approval)
    ↓
Deploy to production
```

### Production Deployment (Future)
- Docker containers
- Kubernetes or managed container service
- Managed PostgreSQL database
- Redis cluster
- CDN for frontend assets
- Monitoring and alerting

---

## Design Patterns Used

1. **Repository Pattern** - Data access abstraction
2. **Service Layer Pattern** - Business logic encapsulation
3. **DTO Pattern** - Request/response translation
4. **Strategy Pattern** - Pluggable categorization/normalization rules
5. **Observer Pattern** - Audit logging via AOP
6. **Factory Pattern** - Transaction creation with validation
7. **Decorator Pattern** - Cross-cutting concerns (logging, security)

---

## Extensibility Points

### Easy to Add Later
1. OAuth2/OIDC authentication
2. Authorized bank data provider APIs
3. Mobile app (share backend)
4. Email reports
5. SMS notifications
6. Data aggregation services
7. Advanced ML models
8. Multi-currency support
9. Household finances
10. Investment tracking
11. Blockchain/crypto tracking
12. Tax reporting integration

### Architectural Decisions Supporting Extensibility
- Pluggable authentication providers
- Service layer abstractions
- Event-driven patterns
- Configuration-driven rules
- Separate concerns by module
- Clear API boundaries

