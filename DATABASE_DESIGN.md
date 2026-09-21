# SpendOS Database Design

## Design Principles

1. **Normalized Schema** - Minimize redundancy, maintain referential integrity
2. **BigDecimal for Money** - Never use floating-point for financial values
3. **Timestamps** - Track creation and modification for audit trail
4. **Soft Deletes** - Only where necessary (users) for data recovery
5. **Indexes** - Performance-critical queries indexed
6. **Constraints** - Enforce data integrity at database level
7. **Sequences** - For distributed transaction IDs (future)

---

## Database Schema

### 1. Users

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    is_email_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    
    CONSTRAINT email_not_empty CHECK (LENGTH(TRIM(email)) > 0)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_is_active ON users(is_active);
```

### 2. User Preferences

```sql
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    currency_code VARCHAR(3) DEFAULT 'INR',
    timezone VARCHAR(50) DEFAULT 'Asia/Kolkata',
    fiscal_year_start_month INTEGER DEFAULT 1,
    theme VARCHAR(50) DEFAULT 'light',
    language VARCHAR(10) DEFAULT 'en',
    financial_health_score_enabled BOOLEAN DEFAULT TRUE,
    email_reports_enabled BOOLEAN DEFAULT FALSE,
    email_alerts_enabled BOOLEAN DEFAULT FALSE,
    demo_mode BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_month CHECK (fiscal_year_start_month >= 1 AND fiscal_year_start_month <= 12),
    CONSTRAINT valid_currency CHECK (LENGTH(currency_code) = 3)
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
```

### 3. Accounts

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL, -- 'savings', 'checking', 'credit', 'digital_wallet'
    account_number_masked VARCHAR(20), -- last 4 digits only
    bank_name VARCHAR(255),
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    currency_code VARCHAR(3) DEFAULT 'INR',
    opening_balance NUMERIC(19, 2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_primary_per_user UNIQUE (user_id, is_primary) WHERE is_primary = TRUE,
    CONSTRAINT valid_account_type CHECK (account_type IN ('savings', 'checking', 'credit', 'digital_wallet'))
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_is_active ON accounts(is_active);
```

### 4. Categories

```sql
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_name VARCHAR(100) NOT NULL UNIQUE,
    icon_name VARCHAR(50),
    color_hex VARCHAR(7),
    description TEXT,
    display_order INTEGER,
    is_system BOOLEAN DEFAULT TRUE, -- system vs user-created
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_hex_color CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$' OR color_hex IS NULL)
);

CREATE INDEX idx_categories_name ON categories(category_name);

-- Predefined categories:
-- Food, Transport, Shopping, Bills, Entertainment, Education, Healthcare, Travel, Subscriptions, Income, Transfers, Other
```

### 5. Subcategories

```sql
CREATE TABLE subcategories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    subcategory_name VARCHAR(100) NOT NULL,
    icon_name VARCHAR(50),
    display_order INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_subcategory_per_category UNIQUE (category_id, subcategory_name)
);

CREATE INDEX idx_subcategories_category_id ON subcategories(category_id);
```

### 6. Merchants

```sql
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_name VARCHAR(255) NOT NULL UNIQUE,
    merchant_name_lower VARCHAR(255) NOT NULL UNIQUE,
    logo_url VARCHAR(500),
    website VARCHAR(500),
    category_id UUID REFERENCES categories(id),
    confidence_score NUMERIC(3, 2), -- 0.00 to 1.00
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_confidence CHECK (confidence_score >= 0 AND confidence_score <= 1)
);

CREATE INDEX idx_merchants_name ON merchants(merchant_name);
CREATE INDEX idx_merchants_name_lower ON merchants(merchant_name_lower);
CREATE INDEX idx_merchants_category_id ON merchants(category_id);
```

### 7. Merchant Normalization Rules

```sql
CREATE TABLE merchant_normalization_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(50) NOT NULL, -- 'exact', 'contains', 'regex', 'starts_with'
    target_merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    priority INTEGER DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_pattern_type CHECK (pattern_type IN ('exact', 'contains', 'regex', 'starts_with'))
);

CREATE INDEX idx_merchant_rules_pattern ON merchant_normalization_rules(pattern);
CREATE INDEX idx_merchant_rules_active ON merchant_normalization_rules(is_active);
CREATE INDEX idx_merchant_rules_priority ON merchant_normalization_rules(priority);
```

### 8. User Merchant Mappings

```sql
CREATE TABLE user_merchant_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    raw_merchant_name VARCHAR(255) NOT NULL,
    normalized_merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE RESTRICT,
    category_id UUID REFERENCES categories(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_user_mapping UNIQUE (user_id, raw_merchant_name)
);

CREATE INDEX idx_user_mappings_user_id ON user_merchant_mappings(user_id);
CREATE INDEX idx_user_mappings_raw_name ON user_merchant_mappings(raw_merchant_name);
```

### 9. Transactions

```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    merchant_id UUID REFERENCES merchants(id),
    category_id UUID REFERENCES categories(id),
    subcategory_id UUID REFERENCES subcategories(id),
    
    amount NUMERIC(19, 2) NOT NULL,
    currency_code VARCHAR(3) DEFAULT 'INR',
    transaction_type VARCHAR(50) NOT NULL, -- 'debit', 'credit', 'transfer'
    transaction_date DATE NOT NULL,
    
    description TEXT,
    raw_description TEXT,
    payment_method VARCHAR(50), -- 'upi', 'card', 'net_banking', 'cash', 'wallet'
    
    external_reference VARCHAR(255), -- bank reference/cheque number
    is_recurring BOOLEAN DEFAULT FALSE,
    is_transfer BOOLEAN DEFAULT FALSE,
    transfer_to_account_id UUID REFERENCES accounts(id),
    
    categorization_confidence NUMERIC(3, 2), -- 0.00 to 1.00
    categorization_source VARCHAR(50), -- 'rule', 'merchant_mapping', 'user', 'ml'
    
    is_duplicate BOOLEAN DEFAULT FALSE,
    duplicate_of_id UUID REFERENCES transactions(id),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    
    CONSTRAINT positive_amount CHECK (amount > 0),
    CONSTRAINT valid_type CHECK (transaction_type IN ('debit', 'credit', 'transfer')),
    CONSTRAINT valid_confidence CHECK (categorization_confidence IS NULL OR (categorization_confidence >= 0 AND categorization_confidence <= 1)),
    CONSTRAINT valid_date CHECK (transaction_date <= CURRENT_DATE)
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_merchant_id ON transactions(merchant_id);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date);
CREATE INDEX idx_transactions_user_merchant ON transactions(user_id, merchant_id);
CREATE INDEX idx_transactions_is_duplicate ON transactions(is_duplicate);
CREATE INDEX idx_transactions_is_recurring ON transactions(is_recurring);
```

### 10. Import Jobs

```sql
CREATE TABLE import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT,
    import_status VARCHAR(50) NOT NULL DEFAULT 'pending', -- 'pending', 'processing', 'completed', 'failed'
    
    total_rows_processed INTEGER DEFAULT 0,
    imported_count INTEGER DEFAULT 0,
    duplicate_count INTEGER DEFAULT 0,
    invalid_count INTEGER DEFAULT 0,
    
    error_summary TEXT, -- JSON: list of row errors
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    CONSTRAINT valid_status CHECK (import_status IN ('pending', 'processing', 'completed', 'failed'))
);

CREATE INDEX idx_import_jobs_user_id ON import_jobs(user_id);
CREATE INDEX idx_import_jobs_status ON import_jobs(import_status);
CREATE INDEX idx_import_jobs_created ON import_jobs(created_at);
```

### 11. Import Errors

```sql
CREATE TABLE import_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_job_id UUID NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    raw_data TEXT NOT NULL,
    error_message TEXT NOT NULL,
    error_code VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_import_errors_import_job ON import_errors(import_job_id);
```

### 12. Budgets

```sql
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    budget_name VARCHAR(255) NOT NULL,
    budget_type VARCHAR(50) DEFAULT 'monthly', -- 'monthly', 'quarterly', 'annual', 'custom'
    
    total_amount NUMERIC(19, 2) NOT NULL,
    currency_code VARCHAR(3) DEFAULT 'INR',
    
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    
    alert_threshold_percent INTEGER DEFAULT 90, -- alert at 90% spent
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT positive_amount CHECK (total_amount > 0),
    CONSTRAINT valid_dates CHECK (start_date < end_date),
    CONSTRAINT valid_alert_threshold CHECK (alert_threshold_percent > 0 AND alert_threshold_percent <= 100)
);

CREATE INDEX idx_budgets_user_id ON budgets(user_id);
CREATE INDEX idx_budgets_active ON budgets(is_active);
```

### 13. Budget Categories

```sql
CREATE TABLE budget_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    allocated_amount NUMERIC(19, 2) NOT NULL,
    
    CONSTRAINT positive_amount CHECK (allocated_amount > 0),
    CONSTRAINT unique_budget_category UNIQUE (budget_id, category_id)
);

CREATE INDEX idx_budget_categories_budget_id ON budget_categories(budget_id);
CREATE INDEX idx_budget_categories_category_id ON budget_categories(category_id);
```

### 14. Recurring Payments

```sql
CREATE TABLE recurring_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    merchant_id UUID REFERENCES merchants(id),
    category_id UUID REFERENCES categories(id),
    
    merchant_name VARCHAR(255),
    typical_amount NUMERIC(19, 2),
    currency_code VARCHAR(3) DEFAULT 'INR',
    
    frequency VARCHAR(50) NOT NULL, -- 'daily', 'weekly', 'biweekly', 'monthly', 'quarterly', 'annual'
    next_expected_date DATE,
    last_occurrence_date DATE,
    occurrences_count INTEGER DEFAULT 1,
    
    confidence NUMERIC(3, 2), -- 0.00 to 1.00
    is_active BOOLEAN DEFAULT TRUE,
    is_user_confirmed BOOLEAN DEFAULT FALSE,
    
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT positive_amount CHECK (typical_amount > 0),
    CONSTRAINT valid_frequency CHECK (frequency IN ('daily', 'weekly', 'biweekly', 'monthly', 'quarterly', 'annual'))
);

CREATE INDEX idx_recurring_user_id ON recurring_payments(user_id);
CREATE INDEX idx_recurring_is_active ON recurring_payments(is_active);
CREATE INDEX idx_recurring_next_date ON recurring_payments(next_expected_date);
```

### 15. Financial Goals

```sql
CREATE TABLE financial_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_name VARCHAR(255) NOT NULL,
    goal_description TEXT,
    goal_type VARCHAR(50), -- 'savings', 'debt_payoff', 'expense_reduction'
    
    target_amount NUMERIC(19, 2) NOT NULL,
    current_progress NUMERIC(19, 2) DEFAULT 0,
    currency_code VARCHAR(3) DEFAULT 'INR',
    
    target_date DATE NOT NULL,
    priority INTEGER DEFAULT 100,
    
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT positive_amount CHECK (target_amount > 0),
    CONSTRAINT valid_progress CHECK (current_progress >= 0)
);

CREATE INDEX idx_goals_user_id ON financial_goals(user_id);
CREATE INDEX idx_goals_active ON financial_goals(is_active);
CREATE INDEX idx_goals_target_date ON financial_goals(target_date);
```

### 16. Insights

```sql
CREATE TABLE insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    insight_type VARCHAR(50) NOT NULL, -- 'spending_trend', 'anomaly', 'money_leak', 'opportunity'
    
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    
    impact_value NUMERIC(19, 2),
    impact_percentage NUMERIC(5, 2),
    
    related_category_id UUID REFERENCES categories(id),
    related_merchant_id UUID REFERENCES merchants(id),
    
    actionable BOOLEAN DEFAULT TRUE,
    suggested_action TEXT,
    
    confidence NUMERIC(3, 2),
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    
    CONSTRAINT valid_insight_type CHECK (insight_type IN ('spending_trend', 'anomaly', 'money_leak', 'opportunity'))
);

CREATE INDEX idx_insights_user_id ON insights(user_id);
CREATE INDEX idx_insights_type ON insights(insight_type);
CREATE INDEX idx_insights_created_at ON insights(created_at);
CREATE INDEX idx_insights_period ON insights(period_start_date, period_end_date);
```

### 17. Audit Logs

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE SET NULL,
    entity_type VARCHAR(100) NOT NULL, -- 'transaction', 'budget', 'category', etc.
    entity_id VARCHAR(255) NOT NULL,
    
    action VARCHAR(50) NOT NULL, -- 'create', 'update', 'delete', 'export'
    
    old_values JSONB,
    new_values JSONB,
    
    ip_address VARCHAR(45),
    user_agent TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_action CHECK (action IN ('create', 'update', 'delete', 'export'))
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
```

### 18. Financial Health Metrics (for caching/performance)

```sql
CREATE TABLE financial_health_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    health_score INTEGER, -- 0-100
    
    savings_rate NUMERIC(5, 2), -- percentage
    average_monthly_income NUMERIC(19, 2),
    average_monthly_expense NUMERIC(19, 2),
    
    spending_volatility NUMERIC(5, 2), -- standard deviation
    recurring_expense_burden NUMERIC(5, 2), -- percentage
    emergency_buffer_months NUMERIC(5, 2),
    
    score_factors JSONB, -- breakdown of score calculation
    
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_score CHECK (health_score >= 0 AND health_score <= 100)
);

CREATE INDEX idx_health_metrics_user_id ON financial_health_metrics(user_id);
CREATE INDEX idx_health_metrics_updated_at ON financial_health_metrics(updated_at);
```

### 19. Demo Data Configuration

```sql
CREATE TABLE demo_data_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    config_json JSONB NOT NULL, -- stores template for demo accounts/transactions
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## Key Design Decisions

### Money Representation
- **Type**: `NUMERIC(19, 2)` - Supports up to 9,223,372,036,854.77 with 2 decimal places
- **Why**: Exact decimal arithmetic, no floating-point precision issues
- **Constraints**: `CHECK (amount > 0)` for most transaction amounts

### UUIDs vs Integers
- **Choice**: UUID as primary key
- **Why**: Distributed system ready, prevents ID enumeration attacks, easier for public APIs
- **Trade-off**: Slightly larger index, acceptable for MVP

### Soft Deletes
- **Used for**: Users (privacy compliance)
- **Not used for**: Transactions (immutable history), never delete user data without explicit request
- **deleted_at TIMESTAMP**: NULL means active

### Indexing Strategy
- **Composite indexes** on commonly filtered combinations (user_id, date)
- **Foreign key indexes** automatically maintained
- **Status/active flags** indexed for faster filtering
- **Date ranges** indexed for time-based queries

### JSONB Storage
- **Used for**: Flexible schema (error messages, import summaries, score factors)
- **Benefits**: Queryable, indexed support, allows evolution
- **Avoids**: Premature table splits

---

## Migration Strategy

Use **Flyway** for database migrations:

```
db/migration/
├── V1__initial_schema.sql
├── V2__add_audit_logs.sql
├── V3__add_merchant_rules.sql
└── V4__add_financial_health.sql
```

Each migration:
- Is idempotent (can run multiple times safely)
- Includes rollback considerations
- Is versioned (V#__description.sql)
- Is tested before deployment

---

## Performance Considerations

### Query Patterns
1. **Dashboard**: Most common (user_id + recent date range)
2. **Transaction Search**: Complex filtering
3. **Analytics**: Aggregations across date ranges
4. **Insights**: Statistical calculations

### Optimization Strategies
1. **Materialized Views** (Phase 2): Aggregate monthly summaries
2. **Partitioning** (Phase 2): Split transactions by year
3. **Caching Layer**: Redis for dashboard metrics
4. **Read Replicas** (Phase 3): Separate analytics queries

### Typical Query Volumes (per active user/month)
- Dashboard load: 10-50 queries
- Transaction list: 100-500 queries
- Analytics: 50-100 queries
- Imports: 1-5 per month

---

## Data Retention & Privacy

### Data Retention Policy
- **Active user data**: Kept indefinitely
- **Inactive accounts (90+ days)**: Eligible for cleanup (future)
- **Audit logs**: Retained for 7 years (compliance)
- **Import errors**: Retained for 1 year

### User Data Export/Deletion
- Export: All user data in standard format (CSV/JSON)
- Deletion: Soft delete with secure purge process
- Compliance: GDPR/local privacy law ready

---

## Scalability Path

### Current (MVP)
- Single PostgreSQL instance
- Connection pool: 10-20 connections
- Can handle 100K-1M transactions easily

### Phase 2 (Growth)
- Read replicas for analytics
- Redis caching layer
- Query optimization and partitioning

### Phase 3+ (Scale)
- Vertical partitioning (sharding by user)
- Kafka for event streaming
- Elasticsearch for search
- Time-series database for metrics

