# SpendOS Product Specification

## Overview
SpendOS is an intelligent personal financial operating system that transforms authorized financial transaction data into understandable financial intelligence.

**Core Purpose:** Answer critical financial questions for everyday users:
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

## Core Product Principles

1. **Privacy-first** - User data never leaves without explicit consent
2. **Security-first** - Treat all financial information as highly sensitive
3. **Mobile-friendly** - Responsive design across all devices
4. **Beautiful but practical UI** - Premium modern design without clutter
5. **Explainable financial insights** - Users understand why, not just what
6. **No fake AI** - No meaningless AI-generated scores or hallucinations
7. **No unnecessary complexity** - Solve real problems elegantly
8. **Modular architecture** - Components are independent and testable
9. **Testable code** - Every feature is backed by tests
10. **Production-ready engineering practices** - Built for real users from day one

---

## Target Users (MVP)
- Students
- Young professionals
- Freelancers
- Working adults
- Anyone wanting to understand and improve personal spending

---

## Non-Goals (MVP)
- ❌ Direct Google Pay integration
- ❌ Banking password collection
- ❌ UPI PIN/OTP storage
- ❌ Enterprise accounting features
- ❌ Investment tracking (Phase 2+)
- ❌ Household/shared finances (Phase 2+)

---

## MVP Data Flow

```
CSV/Statement Import
    ↓
Transaction Parser & Validator
    ↓
Transaction Normalizer
    ↓
Duplicate Detector
    ↓
Merchant Normalization Engine
    ↓
Category Engine
    ↓
PostgreSQL Database
    ↓
Analytics Engine
    ↓
Insight Engine
    ↓
Dashboard & Reports
```

---

## Core Features (MVP)

### 1. Transaction Import
- **CSV/Statement Import**: Safe, user-controlled import mechanism
- **Smart Parsing**: Headers detection, date/amount normalization
- **Duplicate Detection**: Prevent re-importing same transactions
- **Row-Level Error Handling**: Report individual invalid rows without failing import
- **Import Summary**: Clear feedback on imported/duplicate/invalid counts
- **Import History**: Track all imports with timestamps

### 2. Transaction Management
- Transaction CRUD operations with full audit trail
- Search and filter by date, merchant, category, amount range
- Pagination for large datasets
- Bulk actions where appropriate

### 3. Merchant Normalization
Problem: Same merchant appears as "ZOMATO", "ZOMATO ONLINE", "ZOMATO LTD", "UPI-ZOMATO-XXXX"

Solution:
- Deterministic rule-based normalization (primary)
- Merchant mapping engine
- User corrections and feedback loop
- Future transaction learning from corrections

### 4. Category Engine
Hierarchical category system:
```
Food
├── Restaurants
├── Food Delivery
└── Groceries

Transport
├── Cab
├── Fuel
└── Public Transport

Shopping
├── Electronics
├── Clothing
└── General

Bills & Subscriptions
├── Electricity
├── Internet
├── Mobile
└── Subscriptions

Entertainment
Education
Healthcare
Travel
Income
Transfers
Other
```

Categorization approach:
- Deterministic rules (primary)
- Merchant-to-category mappings
- User corrections with learning
- Optional ML fallback with confidence scoring

### 5. Dashboard
"How much did I spend? Where did it go? Is anything unusual?"

Key metrics:
- Total spent (current month)
- Total earned
- Net savings
- Financial health score (0-100, explainable)

Visualizations:
- Category breakdown (pie/bar chart)
- Monthly trend (line chart)
- Top merchants
- Recent transactions
- Upcoming recurring payments
- Budget progress

Design: Premium, modern, whitespace-friendly, mobile-optimized

### 6. Financial Health Score
Explainable 0-100 score based on:
- Savings rate
- Budget adherence
- Spending volatility
- Recurring expense burden
- Emergency buffer (if provided)
- Unusual spending detection

Users see WHY score changed:
```
Financial Health: 82
+6 because savings increased
-4 because discretionary spending increased
-2 because budget exceeded in Food
```

### 7. Money Leak Detector
Identifies repeated small expenses:
- Frequent food delivery orders
- Small convenience purchases
- Recurring subscriptions
- Repeated transportation expenses

Only surfaces statistically meaningful patterns:
```
"You spent ₹1,860 across 18 similar small purchases this month,
42% more than your three-month average."
```

Always shows underlying transactions.

### 8. Recurring Payment Detection
Auto-detects likely recurring transactions:
- Netflix, Spotify subscriptions
- Rent, phone bill, internet
- Regular merchant patterns

Displays:
- Merchant name
- Typical amount
- Frequency
- Next expected date
- Confidence level

Does NOT automatically cancel anything.

### 9. Spending Anomaly Detection
Identifies unusual spending vs. user's own history:

```
"You normally spend ₹1,500–₹2,500 on food during this period.
This month you've spent ₹4,200."
```

Uses user's own historical baseline, not population comparisons.

### 10. Monthly Money Autopsy
End-of-month structured report:

```
MONTHLY MONEY AUTOPSY

Income: ₹75,000
Expenses: ₹42,300
Savings: ₹32,700
Savings Rate: 43.6%

WHAT CHANGED?
↑ Food: +18% vs last month
↓ Transport: -25% vs last month

LARGEST MERCHANTS
1. Zomato: ₹8,400
2. Amazon: ₹6,200
3. Rent: ₹25,000

RECURRING PAYMENTS
Netflix: ₹499/month
Gym: ₹1,500/month

UNUSUAL TRANSACTIONS
- ₹8,500 purchase (Apple Watch) - 5x normal electronics spend

BUDGET PERFORMANCE
Food: 95% (₹9,000 budget)
Transport: 60% (₹3,000 budget)

MOST IMPORTANT INSIGHT
Food delivery increased 42% - opportunity to save ₹2,500/month

SUGGESTED ACTION
Replace 50% of food delivery with home cooking
```

Everything traceable to actual transactions.

### 11. Future Spending Prediction
Estimates month-end spending based on historical patterns:

```
Current date: September 15
Current spending: ₹17,200
Predicted month-end: ₹29,400–₹31,200
```

Clear about confidence:
- "Not enough historical data for a reliable prediction" when data is insufficient
- Never pretends estimates are accurate without data
- Shows calculation methodology

### 12. "Can I Afford This?" Feature
User enters planned purchase, system evaluates affordability:

```
Planned Purchase: ₹10,000

ANALYSIS:
✓ Current plan allows this purchase
Impact: Monthly savings would reduce from ₹12,000 to ₹2,000

DISCLAIMER: This is a planning estimate, not professional financial advice.
```

Considers:
- Current month spending
- Expected income
- Upcoming recurring payments
- Budget constraints
- Savings goals

### 13. What-If Simulator
Run scenarios and see impact:

"What if I spend ₹2,000 less on food?"
"What if I save ₹5,000 more per month?"
"What if I buy a ₹70,000 laptop?"

Calculates:
- Monthly impact
- Annual impact
- Goal completion impact
- Deterministic, testable math (no LLM)

### 14. Financial Assistant
Q&A interface using user's data:

```
User: "Why did I spend more this month?"

System:
1. Detect intent (spending comparison)
2. Retrieve authorized transaction data
3. Calculate differences
4. Use LLM for explanation only
5. Return answer + supporting transactions
```

Supported questions:
- "Why did I spend more this month?"
- "Where did most of my money go?"
- "How much did I spend on food?"
- "What were my largest purchases?"
- "Which subscriptions do I have?"
- "Compare August and September."

Architecture:
- User question → Intent detection
- → Authorized data retrieval
- → Deterministic backend calculations
- → LLM for explanation only
- → Answer + supporting transactions

LLM never invents data. Backend is source of truth.

### 15. Budgets & Budget Alerts
- Create category-based budgets
- Monthly/custom period budgets
- Budget progress tracking
- Alerts when approaching/exceeding limits
- Budget vs actual analysis

### 16. Financial Goals
- Set savings goals (emergency fund, vacation, house down payment)
- Goal progress tracking
- Projected timeline based on current savings rate
- Goal impact on cash flow

### 17. Demo Mode
Fully functional demo with synthetic realistic data:
- No real banking information ever
- Realistic fake transactions
- Demonstrates all features
- Used for public website later
- Can be reset to initial state

---

## Data Integrity & Accuracy

### Transaction Validation
- ✓ Mandatory: date, amount, merchant, transaction type
- ✓ Amount validation: non-zero, properly formatted
- ✓ Date validation: reasonable range, parseable format
- ✓ Amount representation: BigDecimal/NUMERIC (never float)
- ✓ Duplicate detection: same merchant, amount, date within 1 day

### Merchant Normalization Accuracy
- Deterministic rules applied first
- User corrections override and improve future predictions
- Audit trail of all merchant mappings
- Manual review capability

### Categorization Confidence
- Every ML/AI categorization includes confidence score
- Low-confidence categorizations flagged for review
- User can override and train system
- Deterministic rules are always applied first

---

## Reporting & Export
- Monthly PDF autopsy report generation
- Transaction CSV export (user data)
- Category summary export
- Budget tracking export
- Data export for account deletion (GDPR-like)

---

## Performance Requirements
- Dashboard loads in < 2s (typical)
- Search/filter response < 1s
- CSV import handles 5,000+ transactions
- Handles 10+ years of transaction history
- Support 100K+ transactions per user (future)

---

## Accessibility
- WCAG Level AA compliance
- Semantic HTML
- Keyboard navigation
- Screen reader friendly
- High contrast mode support
- Readable font sizes on mobile

---

## Success Metrics (MVP)
- Users successfully import transactions
- 90%+ transaction categorization accuracy
- Dashboard loads smoothly on mobile
- Zero financial data leaks or breaches
- Users find at least one actionable insight
- All features properly tested

