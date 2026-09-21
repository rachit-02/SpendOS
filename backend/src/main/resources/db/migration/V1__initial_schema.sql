-- SpendOS Initial Database Schema
-- Version: 1
-- This is the baseline schema for SpendOS

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create tables in dependency order

-- 1. Users table
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
CREATE INDEX idx_users_created_at ON users(created_at);

-- 2. User Preferences table
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

-- 3. Accounts table
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    account_number_masked VARCHAR(20),
    bank_name VARCHAR(255),
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    currency_code VARCHAR(3) DEFAULT 'INR',
    opening_balance NUMERIC(19, 2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_account_type CHECK (account_type IN ('savings', 'checking', 'credit', 'digital_wallet'))
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_is_active ON accounts(is_active);
CREATE UNIQUE INDEX idx_accounts_one_primary_per_user
    ON accounts(user_id)
    WHERE is_primary = TRUE;

-- 4. Categories table
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_name VARCHAR(100) NOT NULL UNIQUE,
    icon_name VARCHAR(50),
    color_hex VARCHAR(7),
    description TEXT,
    display_order INTEGER,
    is_system BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_hex_color CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$' OR color_hex IS NULL)
);

CREATE INDEX idx_categories_name ON categories(category_name);

-- Insert default categories
INSERT INTO categories (category_name, icon_name, color_hex, display_order, is_system) VALUES
    ('Food', 'utensils', '#FF6B6B', 1, TRUE),
    ('Transport', 'car', '#4ECDC4', 2, TRUE),
    ('Shopping', 'shopping-bag', '#45B7D1', 3, TRUE),
    ('Bills', 'receipt', '#FFA07A', 4, TRUE),
    ('Entertainment', 'film', '#98D8C8', 5, TRUE),
    ('Education', 'book', '#F7DC6F', 6, TRUE),
    ('Healthcare', 'heart', '#BB8FCE', 7, TRUE),
    ('Travel', 'plane', '#85C1E2', 8, TRUE),
    ('Subscriptions', 'credit-card', '#F8B88B', 9, TRUE),
    ('Income', 'trending-up', '#52C41A', 10, TRUE),
    ('Transfers', 'send', '#1890FF', 11, TRUE),
    ('Other', 'help-circle', '#A9A9A9', 12, TRUE);

-- 5. Subcategories table
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

-- Insert default subcategories for Food
INSERT INTO subcategories (category_id, subcategory_name, icon_name, display_order) VALUES
    ((SELECT id FROM categories WHERE category_name = 'Food'), 'Restaurants', 'fork-and-knife', 1),
    ((SELECT id FROM categories WHERE category_name = 'Food'), 'Food Delivery', 'truck', 2),
    ((SELECT id FROM categories WHERE category_name = 'Food'), 'Groceries', 'shopping-cart', 3);

-- Insert default subcategories for Transport
INSERT INTO subcategories (category_id, subcategory_name, icon_name, display_order) VALUES
    ((SELECT id FROM categories WHERE category_name = 'Transport'), 'Cab', 'taxi', 1),
    ((SELECT id FROM categories WHERE category_name = 'Transport'), 'Fuel', 'gas-pump', 2),
    ((SELECT id FROM categories WHERE category_name = 'Transport'), 'Public Transport', 'bus', 3);

-- 6. Merchants table
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_name VARCHAR(255) NOT NULL UNIQUE,
    merchant_name_lower VARCHAR(255) NOT NULL UNIQUE,
    logo_url VARCHAR(500),
    website VARCHAR(500),
    category_id UUID REFERENCES categories(id),
    confidence_score NUMERIC(3, 2),
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_confidence CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1))
);

CREATE INDEX idx_merchants_name ON merchants(merchant_name);
CREATE INDEX idx_merchants_name_lower ON merchants(merchant_name_lower);
CREATE INDEX idx_merchants_category_id ON merchants(category_id);

-- 7. Merchant Normalization Rules table
CREATE TABLE merchant_normalization_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(50) NOT NULL,
    target_merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    priority INTEGER DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_pattern_type CHECK (pattern_type IN ('exact', 'contains', 'regex', 'starts_with'))
);

CREATE INDEX idx_merchant_rules_pattern ON merchant_normalization_rules(pattern);
CREATE INDEX idx_merchant_rules_active ON merchant_normalization_rules(is_active);
CREATE INDEX idx_merchant_rules_priority ON merchant_normalization_rules(priority);

-- 8. User Merchant Mappings table
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

-- 9. Transactions table (core table)
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    merchant_id UUID REFERENCES merchants(id),
    category_id UUID REFERENCES categories(id),
    subcategory_id UUID REFERENCES subcategories(id),
    
    amount NUMERIC(19, 2) NOT NULL,
    currency_code VARCHAR(3) DEFAULT 'INR',
    transaction_type VARCHAR(50) NOT NULL,
    transaction_date DATE NOT NULL,
    
    description TEXT,
    raw_description TEXT,
    payment_method VARCHAR(50),
    
    external_reference VARCHAR(255),
    is_recurring BOOLEAN DEFAULT FALSE,
    is_transfer BOOLEAN DEFAULT FALSE,
    transfer_to_account_id UUID REFERENCES accounts(id),
    
    categorization_confidence NUMERIC(3, 2),
    categorization_source VARCHAR(50),
    
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
CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date DESC);
CREATE INDEX idx_transactions_user_merchant ON transactions(user_id, merchant_id);
CREATE INDEX idx_transactions_is_duplicate ON transactions(is_duplicate);
CREATE INDEX idx_transactions_is_recurring ON transactions(is_recurring);
CREATE INDEX idx_transactions_user_category ON transactions(user_id, category_id);

-- 10. Import Jobs table
CREATE TABLE import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT,
    import_status VARCHAR(50) NOT NULL DEFAULT 'pending',
    
    total_rows_processed INTEGER DEFAULT 0,
    imported_count INTEGER DEFAULT 0,
    duplicate_count INTEGER DEFAULT 0,
    invalid_count INTEGER DEFAULT 0,
    
    error_summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    CONSTRAINT valid_status CHECK (import_status IN ('pending', 'processing', 'completed', 'failed'))
);

CREATE INDEX idx_import_jobs_user_id ON import_jobs(user_id);
CREATE INDEX idx_import_jobs_status ON import_jobs(import_status);
CREATE INDEX idx_import_jobs_created ON import_jobs(created_at DESC);

-- 11. Import Errors table
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

-- 12. Budgets table
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    budget_name VARCHAR(255) NOT NULL,
    budget_type VARCHAR(50) DEFAULT 'monthly',
    
    total_amount NUMERIC(19, 2) NOT NULL,
    currency_code VARCHAR(3) DEFAULT 'INR',
    
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    
    alert_threshold_percent INTEGER DEFAULT 90,
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT positive_amount CHECK (total_amount > 0),
    CONSTRAINT valid_dates CHECK (start_date < end_date),
    CONSTRAINT valid_alert_threshold CHECK (alert_threshold_percent > 0 AND alert_threshold_percent <= 100)
);

CREATE INDEX idx_budgets_user_id ON budgets(user_id);
CREATE INDEX idx_budgets_active ON budgets(is_active);

-- 13. Budget Categories table
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

-- 14. Recurring Payments table
CREATE TABLE recurring_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    merchant_id UUID REFERENCES merchants(id),
    category_id UUID REFERENCES categories(id),
    
    merchant_name VARCHAR(255),
    typical_amount NUMERIC(19, 2),
    currency_code VARCHAR(3) DEFAULT 'INR',
    
    frequency VARCHAR(50) NOT NULL,
    next_expected_date DATE,
    last_occurrence_date DATE,
    occurrences_count INTEGER DEFAULT 1,
    
    confidence NUMERIC(3, 2),
    is_active BOOLEAN DEFAULT TRUE,
    is_user_confirmed BOOLEAN DEFAULT FALSE,
    
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT positive_amount CHECK (typical_amount IS NULL OR typical_amount > 0),
    CONSTRAINT valid_frequency CHECK (frequency IN ('daily', 'weekly', 'biweekly', 'monthly', 'quarterly', 'annual'))
);

CREATE INDEX idx_recurring_user_id ON recurring_payments(user_id);
CREATE INDEX idx_recurring_is_active ON recurring_payments(is_active);
CREATE INDEX idx_recurring_next_date ON recurring_payments(next_expected_date);

-- 15. Financial Goals table
CREATE TABLE financial_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_name VARCHAR(255) NOT NULL,
    goal_description TEXT,
    goal_type VARCHAR(50),
    
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

-- 16. Insights table
CREATE TABLE insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    insight_type VARCHAR(50) NOT NULL,
    
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
CREATE INDEX idx_insights_created_at ON insights(created_at DESC);
CREATE INDEX idx_insights_period ON insights(period_start_date, period_end_date);

-- 17. Audit Logs table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    
    action VARCHAR(50) NOT NULL,
    
    old_values JSONB,
    new_values JSONB,
    
    ip_address VARCHAR(45),
    user_agent TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_action CHECK (action IN ('create', 'update', 'delete', 'export'))
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- 18. Financial Health Metrics table
CREATE TABLE financial_health_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    health_score INTEGER,
    
    savings_rate NUMERIC(5, 2),
    average_monthly_income NUMERIC(19, 2),
    average_monthly_expense NUMERIC(19, 2),
    
    spending_volatility NUMERIC(5, 2),
    recurring_expense_burden NUMERIC(5, 2),
    emergency_buffer_months NUMERIC(5, 2),
    
    score_factors JSONB,
    
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_score CHECK (health_score IS NULL OR (health_score >= 0 AND health_score <= 100))
);

CREATE INDEX idx_health_metrics_user_id ON financial_health_metrics(user_id);
CREATE INDEX idx_health_metrics_updated_at ON financial_health_metrics(updated_at DESC);

-- 19. Demo Data Configuration table
CREATE TABLE demo_data_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    config_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add comment to verify migration
COMMENT ON TABLE users IS 'Core users table for SpendOS application';
COMMENT ON TABLE transactions IS 'Financial transactions imported or manually created by users';

-- Create a sequence for batch operations (optional, for future use)
CREATE SEQUENCE IF NOT EXISTS batch_id_seq START 1;

COMMIT;
