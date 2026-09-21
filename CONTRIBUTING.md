# Contributing to SpendOS

Thank you for your interest in contributing to SpendOS! This document provides guidelines and instructions for contributing to the project.

---

## Code of Conduct

SpendOS is committed to providing a welcoming and inclusive environment. Please be respectful, professional, and considerate when interacting with the community.

---

## Getting Started

### 1. Set Up Development Environment

Follow the [SETUP.md](./SETUP.md) guide to:
- Clone the repository
- Install dependencies
- Set up the database
- Run the application locally

### 2. Review Documentation

- Read [ARCHITECTURE.md](./ARCHITECTURE.md) to understand system design
- Review [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md) to understand phases
- Check [API_DESIGN.md](./API_DESIGN.md) for endpoint specifications
- Study [SECURITY.md](./SECURITY.md) for security requirements

### 3. Create Feature Branch

```bash
git checkout -b feature/your-feature-name
# or
git checkout -b bugfix/bug-description
```

---

## Development Workflow

### Code Style & Conventions

#### Backend (Java)

- **Language:** Java 17
- **Framework:** Spring Boot 3.x
- **Code Style:** Google Java Style Guide
  - Run `mvn checkstyle:check` to validate
  - Use 4-space indentation
  - Use meaningful variable names
  - Keep methods focused and small (< 30 lines)

**Example:**
```java
@PostMapping("/transactions")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<TransactionDTO> createTransaction(
    @Valid @RequestBody CreateTransactionRequest request,
    @AuthenticationPrincipal JwtUser user) {
    
    TransactionDTO result = transactionService.create(request, user);
    return ResponseEntity.status(201).body(result);
}
```

#### Frontend (TypeScript/React)

- **Language:** TypeScript 5.x
- **Framework:** React 18.x
- **Linter:** ESLint
- **Formatter:** Prettier
- **Code Style:**
  - Use functional components
  - Use hooks instead of class components
  - Keep components small and focused
  - Use TypeScript strictly (no `any` types)

**Example:**
```typescript
interface UserProfileProps {
  userId: string;
}

export const UserProfile: React.FC<UserProfileProps> = ({ userId }) => {
  const [user, setUser] = React.useState<User | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    loadUser();
  }, [userId]);

  const loadUser = async () => {
    try {
      setLoading(true);
      const data = await api.getUser(userId);
      setUser(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorAlert message={error} />;
  if (!user) return null;

  return <div>{/* render user */}</div>;
};
```

### Database Changes

1. **Never** modify existing migrations
2. **Always** create new migration files for schema changes
3. Use Flyway naming convention: `V<number>__description.sql`
4. Write migrations to be:
   - Idempotent (can run multiple times safely)
   - Reversible (document rollback steps)
   - Backward compatible when possible

**Example migration:**
```sql
-- V2__add_user_preferences_table.sql
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    currency_code VARCHAR(3) DEFAULT 'INR',
    -- ...
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
```

### Testing Requirements

#### Backend Tests

- **Unit Tests:** Test business logic in isolation
- **Integration Tests:** Test database and Spring integration
- **Target Coverage:** 80%+ for Phase 1

```bash
# Run tests
mvn test

# Run specific test
mvn test -Dtest=UserServiceTest

# View coverage
mvn test jacoco:report
open target/site/jacoco/index.html
```

**Example unit test:**
```java
@SpringBootTest
class UserServiceTest {
    
    @MockBean
    UserRepository userRepository;
    
    @InjectMocks
    UserService userService;
    
    @Test
    void testCreateUserWithValidEmail() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("test@example.com", "password");
        
        // Act
        UserDTO result = userService.createUser(request);
        
        // Assert
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(userRepository).save(any(User.class));
    }
}
```

#### Frontend Tests

- **Component Tests:** Test React components
- **Hook Tests:** Test custom hooks
- **Target Coverage:** 70%+ for Phase 1

```bash
# Run tests
npm test

# Run with coverage
npm run test:coverage

# Run specific test file
npm test -- UserProfile.test.tsx
```

**Example component test:**
```typescript
import { render, screen } from '@testing-library/react';
import { UserProfile } from './UserProfile';

describe('UserProfile', () => {
  it('displays loading state initially', () => {
    render(<UserProfile userId="123" />);
    expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();
  });

  it('displays user info after loading', async () => {
    render(<UserProfile userId="123" />);
    const userName = await screen.findByText('John Doe');
    expect(userName).toBeInTheDocument();
  });
});
```

### Security Requirements

**Every pull request must pass:**

- ✅ No hardcoded secrets or credentials
- ✅ All user inputs validated
- ✅ Authorization checks on sensitive endpoints
- ✅ No SQL injection vulnerabilities
- ✅ No XSS vulnerabilities
- ✅ No sensitive data in logs
- ✅ Dependency security scan passed

**Security checklist:**
```java
// ✅ CORRECT: Validate input
@PostMapping("/transactions")
public ResponseEntity<TransactionDTO> createTransaction(
    @Valid @RequestBody CreateTransactionRequest request) {
    // Validation happens automatically via @Valid annotation
}

// ✅ CORRECT: Check authorization
Transaction transaction = transactionRepository
    .findByIdAndUserId(id, currentUser.getId())
    .orElseThrow(() -> new NotFoundException());

// ❌ WRONG: No input validation
public ResponseEntity<TransactionDTO> createTransaction(
    @RequestBody Map<String, Object> data) {
    // Dangerous - no validation
}

// ❌ WRONG: No authorization check
Transaction transaction = transactionRepository.findById(id)
    .orElseThrow();  // Could return other user's data!
```

---

## Commit Message Convention

Follow conventional commits format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: A new feature
- `fix`: A bug fix
- `test`: Adding or updating tests
- `docs`: Documentation changes
- `refactor`: Code refactoring without feature changes
- `perf`: Performance improvements
- `security`: Security fixes or improvements
- `chore`: Maintenance tasks (dependencies, build, etc.)
- `ci`: CI/CD configuration changes

### Examples

```bash
git commit -m "feat(auth): add JWT token refresh endpoint"
git commit -m "fix(transactions): validate positive amount"
git commit -m "test(dashboard): add dashboard service tests"
git commit -m "docs: update API design documentation"
git commit -m "refactor(transactions): extract categorization logic"
git commit -m "security(auth): add rate limiting to login endpoint"
```

---

## Pull Request Process

### Before Creating PR

1. **Update your branch** with main
   ```bash
   git fetch origin
   git rebase origin/main
   ```

2. **Run all tests locally**
   ```bash
   # Backend
   cd backend
   mvn clean test
   
   # Frontend
   cd frontend
   npm test
   ```

3. **Check code quality**
   ```bash
   # Backend
   mvn checkstyle:check
   mvn spotbugs:check
   
   # Frontend
   npm run lint
   npm run type-check
   ```

4. **Test in Docker (if changed services)**
   ```bash
   docker-compose build
   docker-compose up
   # Test at http://localhost:3000
   ```

### Creating PR

1. **Push your branch**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Create Pull Request on GitHub**
   - Use clear title describing the change
   - Reference related issues: `Closes #123`
   - Describe what changed and why
   - Include screenshots for UI changes

3. **PR Title Format**
   ```
   [Phase X] Feature: Brief description
   [Phase 2] Feature: Add JWT authentication endpoints
   [Phase 5] Fix: Fix transaction categorization accuracy
   ```

4. **PR Description Template**
   ```markdown
   ## Description
   Brief explanation of the changes
   
   ## Type of Change
   - [ ] New feature
   - [ ] Bug fix
   - [ ] Breaking change
   - [ ] Documentation update
   
   ## Related Issue
   Closes #123
   
   ## Changes Made
   - Added JWT token refresh endpoint
   - Updated token expiration configuration
   
   ## Testing Done
   - Unit tests for TokenProvider (8 tests)
   - Integration test with AuthController
   - Manual testing with Postman
   
   ## Screenshots (if UI change)
   [Add screenshots here]
   
   ## Checklist
   - [x] Tests pass locally
   - [x] Code follows style guidelines
   - [x] No hardcoded secrets
   - [x] Documentation updated
   - [x] No breaking changes
   ```

### PR Review Process

- **At least 2 approvals required** before merge
- **All CI checks must pass**
- **Code coverage must not decrease**
- **Security scan must pass**

### Addressing Review Comments

```bash
# Make changes based on feedback
git add .
git commit -m "refactor: address review comments"
git push origin feature/your-feature-name
# Push updates, don't force push on open PRs
```

---

## Documentation

### Code Documentation

- Add JavaDoc to all public methods (backend)
- Add JSDoc to exported functions (frontend)
- Comment complex logic
- Keep comments up to date with code

**Example:**
```java
/**
 * Creates a new transaction for the user.
 *
 * @param request the transaction creation request containing merchant, amount, etc.
 * @param user the authenticated user creating the transaction
 * @return the created transaction as a DTO
 * @throws ValidationException if the request data is invalid
 * @throws NotFoundException if the category or account is not found
 */
@PostMapping("/transactions")
public ResponseEntity<TransactionDTO> createTransaction(
    @Valid @RequestBody CreateTransactionRequest request,
    @AuthenticationPrincipal JwtUser user) {
    // implementation
}
```

### Markdown Documentation

- Update [README.md](./README.md) if changing features
- Update [ARCHITECTURE.md](./ARCHITECTURE.md) if changing architecture
- Update [API_DESIGN.md](./API_DESIGN.md) if changing API
- Keep [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md) updated

---

## Common Issues & Solutions

### Issue: Tests fail locally but pass in CI

**Solution:**
- Ensure you're on the same JDK/Node version as CI
- Clear build caches: `mvn clean`, `npm cache clean`
- Run full test suite, not just changed tests

### Issue: Docker build fails

**Solution:**
```bash
# Rebuild without cache
docker-compose build --no-cache

# Clear Docker system
docker system prune -a --volumes
```

### Issue: Database migration fails

**Solution:**
```bash
# Check migration status
mvn flyway:info

# Clean and remigrate (⚠️ Deletes all data)
mvn flyway:clean
mvn flyway:migrate
```

### Issue: Merge conflicts

**Solution:**
```bash
# Pull latest main
git fetch origin
git rebase origin/main

# Resolve conflicts in files
# After resolving:
git add .
git rebase --continue
git push origin feature/your-feature-name -f
```

---

## Release Process

### Version Numbering

SpendOS uses [Semantic Versioning](https://semver.org/):
- **MAJOR.MINOR.PATCH** (e.g., 1.2.3)
- MAJOR: Breaking changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes

### Release Steps

1. Update version in `backend/pom.xml` and `frontend/package.json`
2. Update [CHANGELOG.md](./CHANGELOG.md)
3. Create release branch: `git checkout -b release/1.0.0`
4. Create PR with release notes
5. After merge, create Git tag: `git tag -a v1.0.0 -m "Release 1.0.0"`
6. Push tag: `git push origin v1.0.0`

---

## Reporting Issues

When reporting bugs, include:

1. **Description:** What happened?
2. **Steps to Reproduce:** How to recreate?
3. **Expected vs Actual:** What should happen?
4. **Environment:**
   - OS (Windows/macOS/Linux)
   - Java version (backend)
   - Node version (frontend)
   - Browser (frontend)
5. **Error Logs:**
   ```
   - Copy full error messages
   - Include stack traces
   - Attach log files if large
   ```
6. **Screenshots/Videos:** For UI bugs

---

## Getting Help

- **Questions:** Create a GitHub Discussion
- **Bugs:** Create a GitHub Issue with details
- **Documentation:** Check README and guides first
- **Code Review:** Ask for help in PR comments
- **Community:** Engage respectfully with other contributors

---

## Recognition

Contributors will be recognized in:
- [CONTRIBUTORS.md](./CONTRIBUTORS.md) file
- GitHub repository insights
- Release notes for their contributions

---

## Thank You! 🎉

Your contributions help make SpendOS better for everyone. Thank you for taking the time to contribute!

---

**Happy contributing!** 🚀

