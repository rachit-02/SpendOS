# SpendOS API Design

## API Principles

1. **RESTful** - Follows REST conventions for CRUD operations
2. **Stateless** - JWT-based, no server-side sessions
3. **Versioned** - `/api/v1/` prefix for future compatibility
4. **Consistent Responses** - Standard envelope for all responses
5. **Proper Status Codes** - Meaningful HTTP status codes
6. **Pagination** - For large result sets
7. **Filtering & Sorting** - Flexible query parameters
8. **Error Handling** - Detailed, actionable error responses
9. **Security** - Authorization on every resource endpoint
10. **Documentation** - OpenAPI/Swagger for all endpoints

---

## Response Envelope

### Success Response

```json
{
  "success": true,
  "data": {
    /* actual response payload */
  },
  "timestamp": "2026-09-06T10:30:45Z",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Error Response

```json
{
  "success": false,
  "error": {
    "code": "INVALID_TRANSACTION",
    "message": "Transaction amount must be positive",
    "details": {
      "field": "amount",
      "value": -100,
      "constraint": "positive_amount"
    }
  },
  "timestamp": "2026-09-06T10:30:45Z",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### List Response (with pagination)

```json
{
  "success": true,
  "data": [
    /* array of items */
  ],
  "pagination": {
    "totalItems": 250,
    "totalPages": 13,
    "currentPage": 1,
    "pageSize": 20,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-06T10:30:45Z",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Authentication Endpoints

### Register

```
POST /api/v1/auth/register

Request:
{
  "email": "user@example.com",
  "password": "SecurePassword123!",
  "fullName": "John Doe"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "fullName": "John Doe",
    "createdAt": "2026-09-06T10:30:45Z"
  }
}

Errors:
- 400: Email already exists
- 400: Password too weak
- 400: Invalid email format
```

### Login

```
POST /api/v1/auth/login

Request:
{
  "email": "user@example.com",
  "password": "SecurePassword123!"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 3600,
    "user": {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "fullName": "John Doe"
    }
  }
}

Errors:
- 401: Invalid email or password
- 429: Too many login attempts
```

### Refresh Token

```
POST /api/v1/auth/refresh

Request:
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}

Response (200 OK):
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 3600
  }
}

Errors:
- 401: Invalid or expired refresh token
```

### Logout

```
POST /api/v1/auth/logout

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "message": "Logged out successfully"
  }
}
```

---

## User Endpoints

### Get Current User

```
GET /api/v1/users/me

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "fullName": "John Doe",
    "emailVerified": false,
    "createdAt": "2026-09-06T10:30:45Z"
  }
}

Errors:
- 401: Unauthorized
```

### Update Profile

```
PUT /api/v1/users/me

Headers: Authorization: Bearer {accessToken}

Request:
{
  "fullName": "Jane Doe",
  "email": "newemail@example.com"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "newemail@example.com",
    "fullName": "Jane Doe",
    "updatedAt": "2026-09-06T10:30:45Z"
  }
}

Errors:
- 401: Unauthorized
- 400: Email already in use
```

### Change Password

```
POST /api/v1/users/me/change-password

Headers: Authorization: Bearer {accessToken}

Request:
{
  "currentPassword": "OldPassword123!",
  "newPassword": "NewPassword123!"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "message": "Password changed successfully"
  }
}

Errors:
- 401: Current password incorrect
- 400: New password too weak
```

### Get User Preferences

```
GET /api/v1/users/me/preferences

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "currencyCode": "INR",
    "timezone": "Asia/Kolkata",
    "theme": "light",
    "language": "en",
    "demoMode": false,
    "emailReportsEnabled": false,
    "emailAlertsEnabled": false
  }
}
```

### Update User Preferences

```
PUT /api/v1/users/me/preferences

Headers: Authorization: Bearer {accessToken}

Request:
{
  "currencyCode": "USD",
  "timezone": "America/New_York",
  "theme": "dark",
  "emailReportsEnabled": true
}

Response (200 OK):
{
  "success": true,
  "data": {
    "currencyCode": "USD",
    "timezone": "America/New_York",
    "theme": "dark",
    "language": "en",
    "emailReportsEnabled": true
  }
}
```

### Delete Account

```
DELETE /api/v1/users/me

Headers: Authorization: Bearer {accessToken}

Request:
{
  "confirmPassword": "CurrentPassword123!"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "message": "Account deleted successfully. Your data will be permanently removed within 30 days."
  }
}

Errors:
- 401: Confirmation password incorrect
```

### Export User Data

```
GET /api/v1/users/me/export

Headers: Authorization: Bearer {accessToken}

Response (200 OK - CSV/JSON file download):
- Content-Type: application/zip
- Contains: transactions.csv, budgets.csv, goals.csv, etc.

Errors:
- 401: Unauthorized
```

---

## Transaction Endpoints

### List Transactions

```
GET /api/v1/transactions?page=1&pageSize=20&startDate=2026-08-01&endDate=2026-09-06&categoryId=xxx&merchantId=yyy&sortBy=date&sortOrder=desc

Headers: Authorization: Bearer {accessToken}

Query Parameters:
- page: integer (default: 1)
- pageSize: integer (default: 20, max: 100)
- startDate: ISO date (YYYY-MM-DD)
- endDate: ISO date
- categoryId: UUID
- merchantId: UUID
- minAmount: decimal
- maxAmount: decimal
- transactionType: string ('debit', 'credit', 'transfer')
- searchText: string (searches merchant, description)
- sortBy: string ('date', 'amount', 'merchant')
- sortOrder: string ('asc', 'desc')

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "accountId": "...",
      "merchantId": "...",
      "merchantName": "Zomato",
      "categoryId": "...",
      "categoryName": "Food",
      "subcategoryId": "...",
      "subcategoryName": "Food Delivery",
      "amount": 450.00,
      "currencyCode": "INR",
      "transactionType": "debit",
      "transactionDate": "2026-09-05",
      "description": "Food Delivery",
      "paymentMethod": "upi",
      "isRecurring": false,
      "isTransfer": false,
      "categorizationConfidence": 0.95,
      "categorizationSource": "merchant_mapping",
      "createdAt": "2026-09-05T14:22:30Z",
      "updatedAt": "2026-09-05T14:22:30Z"
    }
  ],
  "pagination": { /* ... */ }
}

Errors:
- 401: Unauthorized
- 400: Invalid date format
- 400: Invalid query parameters
```

### Get Transaction Detail

```
GET /api/v1/transactions/{transactionId}

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "accountId": "...",
    "merchantId": "...",
    "merchantName": "Zomato",
    "categoryId": "...",
    "categoryName": "Food",
    "amount": 450.00,
    "currencyCode": "INR",
    "transactionType": "debit",
    "transactionDate": "2026-09-05",
    "description": "Food Delivery",
    "paymentMethod": "upi",
    "externalReference": "UPI_REF_12345",
    "isRecurring": false,
    "isTransfer": false,
    "categorizationConfidence": 0.95,
    "categorizationSource": "merchant_mapping",
    "isDuplicate": false,
    "createdAt": "2026-09-05T14:22:30Z",
    "updatedAt": "2026-09-05T14:22:30Z"
  }
}

Errors:
- 401: Unauthorized
- 404: Transaction not found
```

### Create Transaction (Manual)

```
POST /api/v1/transactions

Headers: Authorization: Bearer {accessToken}

Request:
{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "merchantName": "Zomato",
  "categoryId": "550e8400-e29b-41d4-a716-446655440001",
  "amount": 450.00,
  "currencyCode": "INR",
  "transactionType": "debit",
  "transactionDate": "2026-09-05",
  "description": "Lunch delivery",
  "paymentMethod": "upi"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440010",
    "accountId": "550e8400-e29b-41d4-a716-446655440000",
    "merchantId": "...",
    "categoryId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": 450.00,
    /* ... */
  }
}

Errors:
- 401: Unauthorized
- 400: Invalid amount
- 400: Invalid date
```

### Update Transaction

```
PUT /api/v1/transactions/{transactionId}

Headers: Authorization: Bearer {accessToken}

Request:
{
  "categoryId": "550e8400-e29b-41d4-a716-446655440002",
  "description": "Updated description",
  "merchantName": "Zomato Online"
}

Response (200 OK):
{
  "success": true,
  "data": {
    /* updated transaction */
  }
}

Errors:
- 401: Unauthorized
- 404: Transaction not found
- 400: Invalid update data
```

### Delete Transaction

```
DELETE /api/v1/transactions/{transactionId}

Headers: Authorization: Bearer {accessToken}

Response (204 No Content):
(No response body)

Errors:
- 401: Unauthorized
- 404: Transaction not found
```

### Bulk Update Transactions

```
POST /api/v1/transactions/bulk-update

Headers: Authorization: Bearer {accessToken}

Request:
{
  "transactionIds": ["...", "...", "..."],
  "updates": {
    "categoryId": "550e8400-e29b-41d4-a716-446655440002"
  }
}

Response (200 OK):
{
  "success": true,
  "data": {
    "updated": 3,
    "failed": 0
  }
}

Errors:
- 401: Unauthorized
- 400: Invalid request
```

---

## Import Endpoints

### Get Import History

```
GET /api/v1/imports/history?page=1&pageSize=10&sortBy=createdAt&sortOrder=desc

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "fileName": "sept_transactions.csv",
      "importStatus": "completed",
      "importedCount": 982,
      "duplicateCount": 31,
      "invalidCount": 7,
      "totalRowsProcessed": 1020,
      "createdAt": "2026-09-05T10:00:00Z",
      "completedAt": "2026-09-05T10:02:15Z"
    }
  ],
  "pagination": { /* ... */ }
}
```

### Get Import Job Details

```
GET /api/v1/imports/{importJobId}

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fileName": "sept_transactions.csv",
    "fileSizeBytes": 125000,
    "importStatus": "completed",
    "importedCount": 982,
    "duplicateCount": 31,
    "invalidCount": 7,
    "totalRowsProcessed": 1020,
    "createdAt": "2026-09-05T10:00:00Z",
    "startedAt": "2026-09-05T10:00:05Z",
    "completedAt": "2026-09-05T10:02:15Z"
  }
}
```

### Get Import Errors

```
GET /api/v1/imports/{importJobId}/errors?page=1&pageSize=20

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "rowNumber": 5,
      "rawData": "Zomato,,,450", 
      "errorMessage": "Missing transaction date",
      "errorCode": "MISSING_DATE"
    }
  ],
  "pagination": { /* ... */ }
}
```

### Upload CSV Import

```
POST /api/v1/imports/upload

Headers: 
  Authorization: Bearer {accessToken}
  Content-Type: multipart/form-data

Form Data:
  file: (CSV file, max 50MB)
  accountId: UUID (account to import into)
  dateFormat: string (optional, e.g., 'DD-MM-YYYY')

Response (202 Accepted - Async processing):
{
  "success": true,
  "data": {
    "importJobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "processing",
    "message": "Import processing started. Check status with GET /imports/{importJobId}"
  }
}

Errors:
- 401: Unauthorized
- 400: File type not CSV
- 413: File too large
- 400: Invalid account ID
```

### Check Import Status

```
GET /api/v1/imports/{importJobId}/status

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "importJobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "processing", // 'pending', 'processing', 'completed', 'failed'
    "progress": {
      "processed": 500,
      "total": 1000,
      "percentage": 50
    }
  }
}
```

---

## Merchant Endpoints

### List Merchants

```
GET /api/v1/merchants?page=1&pageSize=50&searchText=zomato&categoryId=xxx&sortBy=name

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "merchantName": "Zomato",
      "categoryId": "...",
      "categoryName": "Food",
      "logoUrl": "https://...",
      "website": "https://zomato.com",
      "isVerified": true,
      "confidenceScore": 0.99
    }
  ],
  "pagination": { /* ... */ }
}
```

### Get Merchant Detail

```
GET /api/v1/merchants/{merchantId}

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "merchantName": "Zomato",
    "categoryId": "...",
    "categoryName": "Food",
    "logoUrl": "https://...",
    "website": "https://zomato.com",
    "isVerified": true,
    "confidenceScore": 0.99,
    "transactionCount": 42,
    "averageTransactionAmount": 385.50,
    "lastTransaction": "2026-09-05T14:22:30Z"
  }
}
```

### Create Custom Merchant Mapping

```
POST /api/v1/merchants/mappings

Headers: Authorization: Bearer {accessToken}

Request:
{
  "rawMerchantName": "ZOMATO ONLINE",
  "normalizedMerchantId": "550e8400-e29b-41d4-a716-446655440000",
  "categoryId": "550e8400-e29b-41d4-a716-446655440001"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440010",
    "rawMerchantName": "ZOMATO ONLINE",
    "normalizedMerchantName": "Zomato",
    "categoryName": "Food",
    "createdAt": "2026-09-05T15:00:00Z"
  }
}
```

### Get User's Merchant Mappings

```
GET /api/v1/merchants/mappings?page=1&pageSize=50

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440010",
      "rawMerchantName": "ZOMATO ONLINE",
      "normalizedMerchantName": "Zomato",
      "categoryName": "Food",
      "usageCount": 12,
      "createdAt": "2026-09-05T15:00:00Z"
    }
  ]
}
```

---

## Category Endpoints

### List Categories

```
GET /api/v1/categories

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "categoryName": "Food",
      "iconName": "utensils",
      "colorHex": "#FF6B6B",
      "displayOrder": 1,
      "isSystem": true,
      "subcategories": [
        {
          "id": "550e8400-e29b-41d4-a716-446655440001",
          "subcategoryName": "Restaurants",
          "displayOrder": 1
        },
        {
          "id": "550e8400-e29b-41d4-a716-446655440002",
          "subcategoryName": "Food Delivery",
          "displayOrder": 2
        }
      ]
    }
  ]
}
```

### Get Category Statistics

```
GET /api/v1/categories/{categoryId}/statistics?startDate=2026-08-01&endDate=2026-09-06

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "categoryId": "550e8400-e29b-41d4-a716-446655440000",
    "categoryName": "Food",
    "totalAmount": 8400.00,
    "transactionCount": 24,
    "averageTransactionAmount": 350.00,
    "percentageOfTotal": 19.8,
    "topMerchants": [
      {
        "merchantName": "Zomato",
        "amount": 4200.00,
        "count": 12
      }
    ],
    "subcategoryBreakdown": [
      {
        "subcategoryName": "Food Delivery",
        "amount": 6500.00,
        "percentage": 77.4
      }
    ]
  }
}
```

---

## Dashboard Endpoints

### Get Dashboard Overview

```
GET /api/v1/dashboard

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "period": {
      "startDate": "2026-09-01",
      "endDate": "2026-09-06",
      "month": "September",
      "year": 2026
    },
    "summary": {
      "totalIncome": 75000.00,
      "totalExpense": 42300.00,
      "netSavings": 32700.00,
      "savingsRate": 0.436,
      "currencyCode": "INR"
    },
    "financialHealth": {
      "score": 82,
      "factors": {
        "savingsRate": 20,
        "budgetAdherence": 18,
        "spendingVolatility": 15,
        "recurringBurden": 14,
        "emergencyBuffer": 15
      },
      "changes": [
        { "factor": "savingsRate", "change": 6, "reason": "Savings increased" },
        { "factor": "spending", "change": -4, "reason": "Discretionary spending increased" }
      ]
    },
    "spending": {
      "byCategory": [
        {
          "categoryName": "Food",
          "amount": 8400.00,
          "percentage": 19.8,
          "trend": "up" // 'up', 'down', 'stable'
        },
        {
          "categoryName": "Transport",
          "amount": 3600.00,
          "percentage": 8.5,
          "trend": "stable"
        }
      ],
      "topMerchants": [
        {
          "merchantName": "Zomato",
          "amount": 4200.00,
          "count": 12
        }
      ]
    },
    "recentTransactions": [
      {
        "id": "...",
        "merchantName": "Zomato",
        "categoryName": "Food",
        "amount": 450.00,
        "transactionDate": "2026-09-05",
        "transactionType": "debit"
      }
    ],
    "insights": [
      {
        "type": "opportunity",
        "title": "Money leak detected",
        "description": "You spent ₹1,860 across 18 small purchases this month",
        "actionable": true
      }
    ],
    "recurringPayments": [
      {
        "id": "...",
        "merchantName": "Netflix",
        "typicalAmount": 499.00,
        "frequency": "monthly",
        "nextExpectedDate": "2026-10-05"
      }
    ],
    "budgets": [
      {
        "budgetName": "Food Budget",
        "totalAmount": 9000.00,
        "spentAmount": 8400.00,
        "percentage": 93.3,
        "remainingAmount": 600.00,
        "isExceeded": false,
        "alertThreshold": 90
      }
    ]
  }
}

Errors:
- 401: Unauthorized
```

---

## Analytics Endpoints

### Get Monthly Analytics

```
GET /api/v1/analytics/monthly?month=09&year=2026&metrics=all

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "period": {
      "month": "September",
      "year": 2026
    },
    "income": {
      "total": 75000.00,
      "bySource": [
        { "source": "Salary", "amount": 75000.00 }
      ]
    },
    "expenses": {
      "total": 42300.00,
      "byCategory": [
        { "categoryName": "Food", "amount": 8400.00 },
        { "categoryName": "Transport", "amount": 3600.00 }
      ],
      "byPaymentMethod": [
        { "method": "upi", "amount": 30000.00 },
        { "method": "card", "amount": 12300.00 }
      ]
    },
    "savings": 32700.00,
    "savingsRate": 0.436,
    "previousMonthComparison": {
      "expenseChange": 15.3, // percentage
      "spendingTrend": "up"
    }
  }
}
```

### Get Category Trends

```
GET /api/v1/analytics/categories/trends?categoryId=xxx&months=6

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "categoryName": "Food",
    "trend": [
      { "month": "April", "year": 2026, "amount": 7200.00, "count": 18 },
      { "month": "May", "year": 2026, "amount": 7800.00, "count": 20 },
      { "month": "June", "year": 2026, "amount": 6900.00, "count": 17 },
      { "month": "July", "year": 2026, "amount": 7500.00, "count": 19 },
      { "month": "August", "year": 2026, "amount": 7300.00, "count": 18 },
      { "month": "September", "year": 2026, "amount": 8400.00, "count": 24 }
    ],
    "average": 7533.33,
    "percentageChange": 14.7,
    "forecast": 8200.00
  }
}
```

---

## Insights Endpoints

### Get All Insights

```
GET /api/v1/insights?type=all&period=current_month

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "type": "money_leak",
      "title": "Frequent small expenses detected",
      "description": "You spent ₹1,860 across 18 similar small purchases this month, 42% more than your three-month average.",
      "impactValue": 1860.00,
      "impactPercentage": 4.4,
      "categoryName": "Food",
      "actionable": true,
      "suggestedAction": "Consider reducing food delivery usage"
    },
    {
      "type": "anomaly",
      "title": "Unusual spending detected",
      "description": "You spent ₹8,500 on Electronics, 5x your normal spend.",
      "impactValue": 8500.00,
      "merchantName": "Amazon"
    },
    {
      "type": "recurring",
      "title": "Netflix subscription confirmed",
      "description": "Regular payment to Netflix detected"
    }
  ]
}
```

### Get Spending Anomalies

```
GET /api/v1/insights/anomalies?categoryId=xxx&sensitivityLevel=high

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "...",
      "categoryName": "Food",
      "normalRange": { "min": 1500, "max": 2500 },
      "currentAmount": 4200.00,
      "percentageAboveNormal": 68,
      "period": "current_month",
      "transactionCount": 24,
      "relatedTransactions": [
        { "id": "...", "amount": 450, "merchant": "Zomato" }
      ]
    }
  ]
}
```

---

## Budgets Endpoints

### List Budgets

```
GET /api/v1/budgets?active=true&sortBy=updatedAt

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "budgetName": "Food Budget",
      "budgetType": "monthly",
      "totalAmount": 9000.00,
      "currencyCode": "INR",
      "startDate": "2026-09-01",
      "endDate": "2026-09-30",
      "alertThreshold": 90,
      "isActive": true,
      "categories": [
        {
          "categoryName": "Food",
          "allocatedAmount": 9000.00,
          "spentAmount": 8400.00,
          "percentage": 93.3
        }
      ]
    }
  ]
}
```

### Create Budget

```
POST /api/v1/budgets

Headers: Authorization: Bearer {accessToken}

Request:
{
  "budgetName": "Monthly Budget",
  "budgetType": "monthly",
  "totalAmount": 50000.00,
  "startDate": "2026-09-01",
  "endDate": "2026-09-30",
  "alertThreshold": 90,
  "categories": [
    {
      "categoryId": "...",
      "allocatedAmount": 9000.00
    }
  ]
}

Response (201 Created):
{
  "success": true,
  "data": { /* budget object */ }
}
```

---

## Recurring Payments Endpoints

### Get Recurring Payments

```
GET /api/v1/recurring?confirmed=all&sortBy=nextDate

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "merchantName": "Netflix",
      "typicalAmount": 499.00,
      "frequency": "monthly",
      "nextExpectedDate": "2026-10-05",
      "lastOccurrenceDate": "2026-09-05",
      "occurrencesCount": 12,
      "confidence": 0.98,
      "isUserConfirmed": true
    }
  ]
}
```

### Confirm Recurring Payment

```
POST /api/v1/recurring/{recurringId}/confirm

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "id": "...",
    "isUserConfirmed": true,
    "confirmedAt": "2026-09-06T10:30:45Z"
  }
}
```

---

## Goals Endpoints

### List Goals

```
GET /api/v1/goals?active=true&sortBy=targetDate

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "goalName": "Emergency Fund",
      "goalType": "savings",
      "targetAmount": 150000.00,
      "currentProgress": 45000.00,
      "progressPercentage": 30,
      "targetDate": "2027-03-06",
      "isActive": true,
      "monthsToTarget": 6,
      "projectedCompletionDate": "2026-11-06"
    }
  ]
}
```

### Create Goal

```
POST /api/v1/goals

Headers: Authorization: Bearer {accessToken}

Request:
{
  "goalName": "Vacation Fund",
  "goalType": "savings",
  "targetAmount": 100000.00,
  "targetDate": "2026-12-31"
}

Response (201 Created):
{
  "success": true,
  "data": { /* goal object */ }
}
```

---

## Reports Endpoints

### Get Monthly Autopsy

```
GET /api/v1/reports/monthly-autopsy?month=09&year=2026

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "period": "September 2026",
    "income": 75000.00,
    "expenses": 42300.00,
    "savings": 32700.00,
    "savingsRate": 0.436,
    "changes": {
      "largestIncreases": [
        { "category": "Food", "amount": 1200, "percentageChange": 16.5 }
      ],
      "largestDecreases": [
        { "category": "Transport", "amount": 800, "percentageChange": -18.2 }
      ]
    },
    "largestMerchants": [
      { "merchantName": "Zomato", "amount": 4200.00 }
    ],
    "recurringPayments": [
      { "merchantName": "Netflix", "amount": 499.00 }
    ],
    "unusualTransactions": [
      { "description": "Apple Watch purchase", "amount": 8500.00 }
    ],
    "budgetPerformance": {
      "Food": { "budget": 9000, "spent": 8400, "percentage": 93.3 }
    },
    "mostImportantInsight": "Food delivery increased 42% - opportunity to save ₹2,500/month",
    "suggestedAction": "Replace 50% of food delivery with home cooking"
  }
}
```

### Get Spending Prediction

```
GET /api/v1/reports/spending-prediction?month=09&year=2026

Headers: Authorization: Bearer {accessToken}

Response (200 OK):
{
  "success": true,
  "data": {
    "currentDate": "2026-09-06",
    "currentSpending": 17200.00,
    "daysElapsed": 6,
    "remainingDaysInMonth": 24,
    "projectedMonthEnd": {
      "min": 29400.00,
      "max": 31200.00,
      "median": 30300.00
    },
    "confidence": 0.75,
    "confidenceReason": "Based on 12 months of historical data",
    "methodology": "Average daily spend extrapolated to month end",
    "previousMonthTotal": 42300.00,
    "monthlyAverage": 41500.00
  }
}
```

### Calculate Affordability

```
POST /api/v1/reports/affordability

Headers: Authorization: Bearer {accessToken}

Request:
{
  "purchaseAmount": 10000.00,
  "purchaseDescription": "Laptop"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "purchase": {
      "amount": 10000.00,
      "description": "Laptop"
    },
    "affordability": {
      "isAffordable": true,
      "confidence": "high"
    },
    "analysis": {
      "currentMonthSpending": 17200.00,
      "expectedIncome": 75000.00,
      "upcomingRecurring": 2000.00,
      "expectedRemainingExpenses": 5200.00,
      "availableAfterPurchase": 40600.00,
      "projectedSavingsWithoutPurchase": 50600.00,
      "projectedSavingsWithPurchase": 40600.00,
      "savingsImpact": -10000.00
    },
    "explanation": "Based on your current plan, this purchase is likely affordable, but it would reduce your projected monthly savings from ₹50,600 to approximately ₹40,600.",
    "disclaimer": "This is a planning estimate, not professional financial advice."
  }
}
```

---

## What-If Simulator Endpoints

### Create Simulation

```
POST /api/v1/simulations

Headers: Authorization: Bearer {accessToken}

Request:
{
  "simulationName": "Save ₹2000 on food",
  "scenarios": [
    {
      "type": "spend_reduction",
      "categoryId": "...",
      "amount": 2000.00,
      "period": "monthly"
    }
  ]
}

Response (201 Created):
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "simulationName": "Save ₹2000 on food",
    "results": {
      "monthlyImpact": {
        "before": 42300.00,
        "after": 40300.00,
        "change": -2000.00,
        "changePercentage": -4.7
      },
      "annualImpact": {
        "before": 507600.00,
        "after": 483600.00,
        "change": -24000.00,
        "changePercentage": -4.7
      },
      "goalImpact": [
        {
          "goalName": "Emergency Fund",
          "currentMonthsToCompletion": 6,
          "projectedMonthsWithSimulation": 5
        }
      ]
    }
  }
}
```

---

## Assistant Endpoints

### Query Financial Assistant

```
POST /api/v1/assistant/query

Headers: Authorization: Bearer {accessToken}

Request:
{
  "question": "Why did I spend more this month?",
  "context": {
    "selectedMonth": "September",
    "selectedYear": 2026
  }
}

Response (200 OK):
{
  "success": true,
  "data": {
    "question": "Why did I spend more this month?",
    "answer": "Your spending increased primarily due to a 42% increase in food-related expenses. You spent ₹8,400 on food this month compared to your three-month average of ₹5,900...",
    "supportingData": {
      "keyFindings": [
        {
          "category": "Food",
          "thisMonth": 8400.00,
          "previousMonth": 6200.00,
          "change": 2200.00,
          "changePercentage": 35.5
        }
      ],
      "relatedTransactions": [
        {
          "id": "...",
          "merchant": "Zomato",
          "amount": 450.00,
          "date": "2026-09-05"
        }
      ]
    }
  }
}

Errors:
- 401: Unauthorized
- 400: Invalid question
- 500: Assistant processing error
```

---

## Health/Status Endpoints

### Health Check

```
GET /api/v1/health

Response (200 OK):
{
  "success": true,
  "data": {
    "status": "UP",
    "timestamp": "2026-09-06T10:30:45Z",
    "checks": {
      "database": "UP",
      "redis": "UP"
    }
  }
}
```

---

## Error Codes

| Code | Status | Description |
|------|--------|-------------|
| UNAUTHORIZED | 401 | Missing or invalid authentication token |
| FORBIDDEN | 403 | User doesn't have permission for this resource |
| NOT_FOUND | 404 | Requested resource not found |
| INVALID_REQUEST | 400 | Request body or parameters are invalid |
| INVALID_TRANSACTION | 400 | Transaction data validation failed |
| INVALID_AMOUNT | 400 | Amount validation failed |
| DUPLICATE_EMAIL | 400 | Email already registered |
| WEAK_PASSWORD | 400 | Password doesn't meet strength requirements |
| FILE_TOO_LARGE | 413 | Uploaded file exceeds size limit |
| INVALID_FILE_TYPE | 400 | File type not supported |
| RATE_LIMITED | 429 | Too many requests |
| INTERNAL_ERROR | 500 | Internal server error |
| SERVICE_UNAVAILABLE | 503 | Service temporarily unavailable |

