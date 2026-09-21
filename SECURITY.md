# SpendOS Security Design

## Security Principles

1. **Privacy-First** - Minimize data collection, user controls all data
2. **Never Store Secrets** - Credentials, PINs, OTPs never stored
3. **Defense in Depth** - Multiple layers of security
4. **Least Privilege** - Users access only their own data
5. **Secure by Default** - Security baked into architecture
6. **Transparency** - Users know what's collected and why
7. **Compliance Ready** - GDPR, local privacy laws in mind
8. **No Backdoors** - No admin override of user permissions

---

## What We Will NOT Do

❌ **Never:**
- Collect UPI PINs, OTPs, banking passwords, card PINs
- Scrape Google Pay or other financial services
- Store authentication credentials from banks
- Access unauthorized financial data APIs
- Monetize user financial data
- Sell data to third parties
- Track across different users
- Store sensitive data in logs

---

## What We WILL Do (MVP)

✅ **Safe Integration Points:**
- CSV/statement file imports (user-controlled)
- Synthetic/demo financial data
- UPI/card transaction history (user provides via upload)
- Manual transaction entry
- Future: Official OAuth2/OIDC from authorized providers

---

## Authentication & Authorization

### JWT-Based Authentication

**Strategy:**
- User registers with email + strong password
- Login returns JWT access token + refresh token
- Access token included in Authorization header
- Refresh token used to get new access token
- Tokens expire to limit damage from leaks

**Token Structure:**

```json
{
  "iss": "spendos",
  "sub": "user-id-uuid",
  "email": "user@example.com",
  "roles": ["USER"],
  "iat": 1694000000,
  "exp": 1694003600,
  "jti": "token-id"
}
```

**Token Expiration:**
- Access token: 1 hour
- Refresh token: 30 days
- Refresh tokens single-use (revoked after use)

### Password Security

**Requirements:**
- Minimum 12 characters
- Mix of uppercase, lowercase, numbers, symbols
- NOT in common password dictionary
- Rate-limited registration (max 5 per IP per hour)

**Storage:**
- bcrypt with cost factor 12
- Random salt per user
- Never log or display passwords

**Examples (all INVALID):**
- `password123` - too common
- `Abc123` - too short, no special chars
- `admin123` - common pattern
- `user@password` - contains username

**Examples (all VALID):**
- `Tr0pic@lThund3r!`
- `C0ff33#M0rning$`
- `BlueSky@2026!`

### Authorization Checks

**Rule: Every endpoint checks ownership**

```java
// NEVER
GET /api/transactions/123
└─ Returns transaction 123 regardless of user

// ALWAYS
GET /api/transactions/123
└─ Check: transaction.userId == currentUser.id
└─ Returns transaction ONLY if owned by current user
└─ Returns 404 if not owned or doesn't exist
```

**Examples:**

```java
// ✓ CORRECT
@GetMapping("/transactions/{id}")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<TransactionDTO> getTransaction(
    @PathVariable UUID id,
    @AuthenticationPrincipal JwtUser user) {
    
    Transaction transaction = transactionRepository
        .findByIdAndUserId(id, user.getId())
        .orElseThrow(() -> new NotFoundException("Transaction not found"));
    
    return ResponseEntity.ok(transactionMapper.toDTO(transaction));
}

// ✗ WRONG - No authorization check
@GetMapping("/transactions/{id}")
public ResponseEntity<TransactionDTO> getTransaction(@PathVariable UUID id) {
    Transaction transaction = transactionRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Not found"));
    return ResponseEntity.ok(transactionMapper.toDTO(transaction));
}

// ✗ WRONG - Only checks if user exists
@GetMapping("/transactions/{id}")
public ResponseEntity<TransactionDTO> getTransaction(
    @PathVariable UUID id,
    @AuthenticationPrincipal JwtUser user) {
    
    if (user == null) {
        throw new UnauthorizedException();
    }
    
    // Still allows access to other users' transactions!
    Transaction transaction = transactionRepository.findById(id)
        .orElseThrow();
    
    return ResponseEntity.ok(transactionMapper.toDTO(transaction));
}
```

### Role-Based Access Control

**Roles (MVP):**
- `USER` - Regular user
- `ADMIN` - System administrator (for infrastructure only)

**Authorization Examples:**

```java
// User can only access own profile
@PreAuthorize("isAuthenticated()")
GET /api/users/me

// Users cannot access other users
@PreAuthorize("isAuthenticated()")
GET /api/users/{userId}  // 403 if userId != currentUser

// Only admins can access system health
@PreAuthorize("hasRole('ADMIN')")
GET /api/admin/health

// Only user can delete own account
@PreAuthorize("isAuthenticated()")
DELETE /api/users/me
```

---

## Data Access Control

### SQL Injection Protection

**Strategy: Always use parameterized queries**

```java
// ✓ CORRECT - JPA handles parameterization
transactionRepository.findByIdAndUserId(transactionId, userId)

// ✓ CORRECT - Named parameters
@Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.userId = :userId")
Transaction findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId)

// ✗ WRONG - String concatenation
String query = "SELECT * FROM transactions WHERE id = '" + id + "'";
resultSet = executeQuery(query);
```

### XSS (Cross-Site Scripting) Protection

**Strategy: JSON encoding, CSP headers**

```java
// ✓ CORRECT - Jackson automatically encodes JSON
public TransactionDTO {
    public String description;  // Encoded when serialized to JSON
}

// Frontend
const description = `Lunch at <script>alert('hack')</script>`;
fetch('/api/transactions', { body: JSON.stringify({ description }) });
// Server stores: "Lunch at <script>alert('hack')</script>"
// Frontend receives: JSON-encoded, safe when parsed

// ✗ WRONG - HTML rendering without escaping
<p>{{ transaction.description }}</p>  // If using templates
```

**Response Headers:**

```
Content-Security-Policy: default-src 'self'; script-src 'self'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
```

### CSRF (Cross-Site Request Forgery) Protection

**Strategy: Stateless JWT (inherent protection)**

Because we use JWT tokens in headers (not cookies):
- CSRF attacks require cookie-based session
- JWTs are sent in `Authorization: Bearer` header
- Browser cannot include header in cross-origin requests

**If cookies needed in future:**

```java
@Configuration
public class CsrfConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf()
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
        return http.build();
    }
}
```

---

## Input Validation

### Validation Strategy

**All user input validated at controller layer**

```java
@PostMapping("/transactions")
public ResponseEntity<TransactionDTO> createTransaction(
    @Valid @RequestBody CreateTransactionRequest request,
    @AuthenticationPrincipal JwtUser user) {
    
    // request automatically validated before reaching this method
    // Invalid requests return 400 with detailed errors
    
    return ResponseEntity.status(201).body(transactionService.create(request, user));
}
```

### DTO Validation

```java
public class CreateTransactionRequest {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @DecimalMax("99999999.99")
    private BigDecimal amount;
    
    @NotNull
    @PastOrPresent
    private LocalDate transactionDate;
    
    @NotBlank
    @Size(min = 1, max = 255)
    private String merchantName;
    
    @NotNull
    private UUID categoryId;
    
    @NotNull
    @Pattern(regexp = "^(debit|credit|transfer)$")
    private String transactionType;
}
```

### Custom Validators

```java
@Component
public class TransactionValidator {
    
    @Transactional(readOnly = true)
    public void validateTransaction(Transaction transaction, UUID userId) throws ValidationException {
        // Verify user owns account
        if (!accountRepository.existsByIdAndUserId(transaction.getAccountId(), userId)) {
            throw new ValidationException("Account not found");
        }
        
        // Verify category exists
        if (!categoryRepository.existsById(transaction.getCategoryId())) {
            throw new ValidationException("Category not found");
        }
        
        // Verify amount is reasonable (not $1 million for food)
        if (transaction.getAmount().compareTo(BigDecimal.valueOf(999999)) > 0) {
            throw new ValidationException("Amount exceeds maximum allowed");
        }
    }
}
```

### File Upload Validation

```java
@PostMapping("/imports/upload")
public ResponseEntity<ImportJobDTO> uploadCSV(
    @RequestParam("file") MultipartFile file,
    @RequestParam UUID accountId,
    @AuthenticationPrincipal JwtUser user) {
    
    // Validate file type
    String contentType = file.getContentType();
    if (!contentType.equals("text/csv") && !contentType.equals("text/plain")) {
        throw new ValidationException("File must be CSV format");
    }
    
    // Validate file size (50MB limit)
    if (file.getSize() > 50_000_000) {
        throw new ValidationException("File too large (max 50MB)");
    }
    
    // Validate file is actually CSV (check headers)
    try {
        CSVFormat format = CSVFormat.DEFAULT.withFirstRecordAsHeader();
        CSVParser parser = CSVFormat.DEFAULT.parse(new InputStreamReader(file.getInputStream()));
        if (parser.getRecordNumber() > 0) {
            // Valid CSV
        }
    } catch (IOException e) {
        throw new ValidationException("File is not valid CSV");
    }
    
    return ResponseEntity.accepted().body(importService.processImport(file, accountId, user));
}
```

---

## Encryption & Secrets Management

### Secrets Storage

**Never in Git:**
- .env files
- application.properties with secrets
- API keys
- Database passwords
- JWT secrets

**Always in Environment:**

```bash
# .env.example (committed to Git)
DATABASE_URL=postgres://user:password@localhost/spendos
DATABASE_USERNAME=spendos_user
DATABASE_PASSWORD=CHANGE_ME
JWT_SECRET=CHANGE_ME_MINIMUM_32_CHARACTERS

# .env (NOT committed, local development only)
DATABASE_URL=postgres://user:realpassword@localhost/spendos
DATABASE_USERNAME=spendos_user
DATABASE_PASSWORD=actualpassword123
JWT_SECRET=TruelyRandomSecretMinimum32CharactersLong

# Production (environment variables set by platform)
Managed by: Docker, Kubernetes, Cloud Platform
Never stored in Git or logs
```

### Application Configuration

```yaml
# application.yml (NEVER contains secrets)
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Never auto-create in production
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect

jwt:
  secret: ${JWT_SECRET}
  expiration: 3600000  # 1 hour in milliseconds
  refreshExpiration: 2592000000  # 30 days

app:
  name: SpendOS
  environment: ${APP_ENV:development}
  corsAllowedOrigins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

### Database Connection Encryption

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.example.com:5432/spendos
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximumPoolSize: 20
      minimumIdle: 5
      connectionTimeout: 30000
      idleTimeout: 600000
      maxLifetime: 1800000
      # SSL for PostgreSQL
      ssl: true
      sslMode: require
```

---

## Network & Transport Security

### HTTPS Only

**Production:**
```
HTTPS enforced
- Redirect HTTP → HTTPS
- HSTS headers
- TLS 1.2+ required
- Strong ciphers only
```

**Development:**
```
HTTP allowed on localhost only
HTTPS used for any remote deployment
```

### Security Headers

```java
@Component
public class SecurityHeadersFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Prevent clickjacking
        httpResponse.setHeader("X-Frame-Options", "DENY");
        
        // Prevent MIME type sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        
        // Enable browser XSS protection
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Content Security Policy
        httpResponse.setHeader("Content-Security-Policy", 
            "default-src 'self'; script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; img-src 'self' data: https:");
        
        // Referrer policy
        httpResponse.setHeader("Referrer-Policy", "no-referrer");
        
        // HSTS (strict transport security) - 1 year, include subdomains
        httpResponse.setHeader("Strict-Transport-Security", 
            "max-age=31536000; includeSubDomains; preload");
        
        chain.doFilter(request, response);
    }
}
```

---

## Audit Logging

### What Gets Logged

**Always Log:**
- User login/logout
- Failed authentication attempts
- Authorization failures
- Data creation/updates/deletion
- Import job completions
- Budget overages
- Unusual access patterns

**Never Log:**
- Passwords
- Full transaction amounts (log only last 4 digits + category)
- Full card numbers
- OTPs or sensitive tokens
- API keys or secrets

### Audit Log Structure

```java
@Entity
public class AuditLog {
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;  // Who did it
    
    @Column(nullable = false)
    private String entityType;  // What entity (transaction, budget, user)
    
    @Column(nullable = false)
    private String entityId;  // Which entity instance
    
    @Column(nullable = false)
    private String action;  // What action (create, update, delete)
    
    @Column(columnDefinition = "jsonb")
    private JsonNode oldValues;  // Previous state
    
    @Column(columnDefinition = "jsonb")
    private JsonNode newValues;  // New state
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private String ipAddress;  // For suspicious pattern detection
    private String userAgent;
}
```

**Example Log Entry:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "entityType": "transaction",
  "entityId": "550e8400-e29b-41d4-a716-446655440001",
  "action": "update",
  "oldValues": {
    "categoryId": "550e8400-e29b-41d4-a716-446655440010",
    "amount": "500.00"
  },
  "newValues": {
    "categoryId": "550e8400-e29b-41d4-a716-446655440011",
    "amount": "500.00"
  },
  "createdAt": "2026-09-06T10:30:45Z",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0..."
}
```

### Structured Logging

```java
@Component
public class AuditLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    
    public void logTransactionCreated(Transaction transaction, UUID userId) {
        log.info(
            "Transaction created | userId={} | transactionId={} | amount={} | merchant={}",
            userId,
            transaction.getId(),
            transaction.getAmount(),
            transaction.getMerchantId(),
            "action=create", "entity=transaction"
        );
    }
    
    public void logFailedLogin(String email) {
        log.warn(
            "Failed login attempt | email={} | timestamp={}",
            email,
            LocalDateTime.now(),
            "action=failed_login"
        );
    }
}
```

---

## Rate Limiting & Abuse Prevention

### Rate Limits

```java
@Component
public class RateLimitingFilter implements Filter {
    
    private final Map<String, RateLimitBucket> clientLimits = new ConcurrentHashMap<>();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientKey = getClientIdentifier(httpRequest);  // IP + User ID
        RateLimitBucket bucket = clientLimits.computeIfAbsent(
            clientKey,
            k -> new RateLimitBucket()
        );
        
        if (!bucket.allowRequest()) {
            httpResponse.setStatus(429);  // Too Many Requests
            httpResponse.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
            return;
        }
        
        chain.doFilter(request, response);
    }
}
```

**Rate Limits (per user/IP):**

| Endpoint | Limit | Window |
|----------|-------|--------|
| POST /auth/login | 5 | 15 minutes |
| POST /auth/register | 5 | 1 hour |
| POST /imports/upload | 10 | 1 hour |
| GET /transactions | 100 | 1 minute |
| POST /transactions | 50 | 1 minute |
| General API | 1000 | 1 hour |

---

## Privacy & Data Protection

### Data Minimization

**Collect only what's necessary:**
- Email (authentication)
- Password (authentication)
- Transaction data (core feature)
- User preferences (UX)
- Audit logs (security)

**Do NOT collect:**
- Location data
- Device identifiers
- Browsing history
- Cross-service tracking

### User Data Rights

**Data Export:**
```
GET /api/users/me/export
└─ Returns ZIP with:
   - user_profile.json
   - transactions.csv
   - budgets.csv
   - goals.csv
   - insights.csv
   - audit_logs.json
```

**Account Deletion:**
```
DELETE /api/users/me
└─ Soft delete user (deleted_at = now)
└─ Data retained 30 days (for recovery)
└─ Then permanently purged
└─ Audit logs retained (legal requirement)
```

**Right to Rectification:**
```
PUT /api/users/me
└─ User can update profile and preferences
└─ Changes logged with old values
```

### Cookie Policy

**Cookies Used:**
- `auth-token` (optional, only if needed): HttpOnly, Secure, SameSite=Strict
- `preferences`: User can disable

**No Third-Party Cookies:**
- Google Analytics: NO
- Facebook Pixel: NO
- Advertising cookies: NO
- Social media trackers: NO

---

## Security Testing

### Test Categories

**Unit Tests:**
```java
@Test
void testUnauthorizedUserCannotAccessOtherUserTransactions() {
    User user1 = createUser("user1@example.com");
    User user2 = createUser("user2@example.com");
    Transaction transaction = createTransaction(user1);
    
    // user2 tries to access user1's transaction
    assertThrows(NotFoundException.class, () -> {
        transactionRepository.findByIdAndUserId(transaction.getId(), user2.getId())
            .orElseThrow(() -> new NotFoundException());
    });
}

@Test
void testPasswordHashedWithBcrypt() {
    String password = "SecurePassword123!";
    User user = new User();
    user.setPassword(bCryptPasswordEncoder.encode(password));
    
    assertTrue(bCryptPasswordEncoder.matches(password, user.getPassword()));
    assertFalse(bCryptPasswordEncoder.matches("WrongPassword", user.getPassword()));
}
```

**Integration Tests:**
```java
@Test
void testCSVImportValidatesFileType() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "test.txt",
        "text/plain",
        "malicious content".getBytes()
    );
    
    mockMvc.perform(multipart("/api/v1/imports/upload")
        .file(file)
        .param("accountId", accountId.toString())
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
}
```

**Security Tests:**
```java
@Test
void testXSSProtectionInTransactionDescription() throws Exception {
    String xssPayload = "<script>alert('XSS')</script>";
    
    mockMvc.perform(post("/api/v1/transactions")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of(
            "merchantName", "Zomato",
            "amount", "100",
            "description", xssPayload
        ))))
        .andExpect(status().isCreated())
        .andExpect(result -> {
            String response = result.getResponse().getContentAsString();
            // Verify payload is JSON-encoded, not executable
            assertTrue(response.contains("\\u003cscript\\u003e"));
        });
}
```

---

## Deployment Security

### Environment Variables Checklist

```bash
# Required before production deployment
✓ DATABASE_URL set
✓ DATABASE_USERNAME set
✓ DATABASE_PASSWORD set
✓ JWT_SECRET set (minimum 32 chars, random)
✓ APP_ENV=production
✓ CORS_ALLOWED_ORIGINS set (no *)
✓ HTTPS enabled
✓ Security headers configured
✓ Database encrypted at rest
✓ Logs not storing secrets
```

### Database Hardening

```sql
-- Create limited user for app
CREATE USER spendos_app WITH ENCRYPTED PASSWORD 'strong_password';

-- Grant minimal permissions
GRANT CONNECT ON DATABASE spendos TO spendos_app;
GRANT USAGE ON SCHEMA public TO spendos_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO spendos_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO spendos_app;

-- Disable superuser
ALTER USER spendos_app WITH NOSUPERUSER;
ALTER USER spendos_app WITH NOCREATEDB;
ALTER USER spendos_app WITH NOCREATEROLE;
```

### Container Security

```dockerfile
# Dockerfile (secure practices)

# Use minimal base image
FROM eclipse-temurin:17-jre-alpine

# Run as non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy application
COPY --chown=appuser:appgroup target/spendos.jar /app/spendos.jar

# Set working directory
WORKDIR /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD java -cp . HealthChecker

# Run application
ENTRYPOINT ["java", "-jar", "spendos.jar"]

# Do NOT include secrets in image
# Do NOT run as root
# Do NOT use latest tags
```

---

## Compliance & Regulations

### Privacy Laws

**GDPR (Europe)**
- ✓ Data minimization implemented
- ✓ User consent for data processing
- ✓ Right to access/export implemented
- ✓ Right to deletion implemented
- ✓ Data processing agreements for vendors

**CCPA (California)**
- ✓ Opt-out mechanisms
- ✓ Data sale prohibition (we don't sell)
- ✓ Consumer rights implementation

**Local Laws**
- Design follows privacy-by-default
- Easy to adapt to local regulations

### PCI-DSS Non-Compliance (Intentional)

**We do NOT process/store:**
- ❌ Full credit card numbers
- ❌ Card expiration dates
- ❌ CVV/CVC codes
- ❌ PINs or authentication secrets
- ❌ Account credentials

**Therefore:**
- ❌ PCI-DSS Level 3 certification NOT required
- ❌ We're outside PCI-DSS scope
- ❌ Lower compliance burden
- ✅ Better user security

---

## Security Incident Response

### Incident Response Plan

1. **Detect**: Automated alerts on security events
2. **Respond**: Isolate affected systems
3. **Investigate**: Root cause analysis
4. **Contain**: Prevent further damage
5. **Eradicate**: Remove threat
6. **Recover**: Restore normal operations
7. **Document**: Log incident for review
8. **Notify**: User notification if data affected

### Security Breach Notification

**If data is accessed without authorization:**

1. Notify affected users within 48 hours
2. Explain what data was affected
3. Describe steps we're taking
4. Provide contact for questions
5. Update security measures to prevent recurrence

**Never hide breaches.**

---

## Secure Development Practices

### Code Review Checklist

Before merging any code:

- [ ] No hardcoded secrets
- [ ] All user inputs validated
- [ ] Authorization checks on sensitive endpoints
- [ ] SQL injection protection (parameterized queries)
- [ ] XSS protection (proper encoding)
- [ ] No sensitive data in logs
- [ ] No debug code in production paths
- [ ] All external dependencies reviewed
- [ ] Tests include security cases
- [ ] Documentation clear about security assumptions

### Dependency Management

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>8.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Run before deployment:**
```bash
mvn dependency-check:check
# Identifies known vulnerabilities in dependencies
# Fails build if high-risk dependencies found
```

