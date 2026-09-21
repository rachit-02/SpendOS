# SpendOS Development Setup Guide

## Phase 1: Project Setup Complete ✅

This guide will help you get SpendOS running on your local machine.

---

## Prerequisites

### Required
- **Git** - Version control (https://git-scm.com/download)
- **Docker & Docker Compose** - Containerization (https://www.docker.com/products/docker-desktop)
  - Docker Engine 20.10+
  - Docker Compose 2.0+

### Optional (for local development without Docker)
- **Java 17+** - Backend runtime (https://adoptium.net/)
- **Maven 3.8+** - Build tool (https://maven.apache.org/)
- **Node.js 18+** - Frontend runtime (https://nodejs.org/)
- **npm 8+** - Package manager
- **PostgreSQL 14+** - Database (https://www.postgresql.org/)

### For Database Management (Optional)
- **pgAdmin** - PostgreSQL admin tool (included in docker-compose)
- **DBeaver** - Database IDE (https://dbeaver.io/)

---

## Option 1: Quick Start with Docker (Recommended)

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/spendos.git
cd spendos
```

### 2. Create Environment File

```bash
# Copy the example file
cp .env.example .env

# Edit if needed (optional - defaults work for local dev)
# nano .env  # or your favorite editor
```

**Default values for local dev:**
```
DATABASE_URL=postgresql://spendos_user:spendos_password@localhost:5432/spendos
DATABASE_USERNAME=spendos_user
DATABASE_PASSWORD=spendos_password
JWT_SECRET=dev_secret_change_in_production_minimum_32_chars
APP_ENV=development
```

### 3. Start Services

```bash
# Start all services (backend, frontend, database)
docker-compose up

# Or run in background
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### 4. Access the Application

- **Frontend:** http://localhost:3000
- **Backend API:** http://localhost:8080/api
- **API Docs (Swagger):** http://localhost:8080/swagger-ui.html
- **Backend Health:** http://localhost:8080/api/v1/health

### Troubleshooting Docker

```bash
# Rebuild images
docker-compose build

# Force recreate containers
docker-compose up --force-recreate

# Remove all volumes (clean slate)
docker-compose down -v

# View container logs
docker-compose logs backend
docker-compose logs frontend

# Execute command in container
docker-compose exec backend bash
docker-compose exec frontend sh
```

---

## Option 2: Local Development Setup

### Backend Setup

#### 2.1 Java Installation

```bash
# Verify Java is installed
java -version
# Should be Java 17+

# Install Java (if needed)
# On macOS with Homebrew:
brew install openjdk@17

# On Ubuntu/Debian:
sudo apt-get install openjdk-17-jdk
```

#### 2.2 Maven Installation

```bash
# Verify Maven
mvn -version

# Install Maven (if needed)
# On macOS:
brew install maven

# On Ubuntu/Debian:
sudo apt-get install maven
```

#### 2.3 PostgreSQL Setup

```bash
# Option A: Using Docker (recommended even for local dev)
docker run --name spendos-postgres \
  -e POSTGRES_USER=spendos_user \
  -e POSTGRES_PASSWORD=spendos_password \
  -e POSTGRES_DB=spendos \
  -p 5432:5432 \
  -d postgres:16-alpine

# Option B: Install locally
# macOS:
brew install postgresql

# Ubuntu/Debian:
sudo apt-get install postgresql postgresql-contrib

# Create database
createuser -P spendos_user  # Enter password when prompted
createdb -O spendos_user spendos
```

#### 2.4 Backend Development

```bash
cd backend

# Run migrations and start backend
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Or
mvn clean spring-boot:run -Dspring.profiles.active=dev

# Backend runs on http://localhost:8080
```

### Frontend Setup

#### 2.5 Node.js Installation

```bash
# Verify Node.js
node -v  # Should be v18+
npm -v   # Should be 8+

# Install Node.js (if needed)
# From https://nodejs.org/
# Or using package managers:
# macOS:
brew install node

# Ubuntu/Debian:
sudo apt-get install nodejs npm
```

#### 2.6 Frontend Development

```bash
cd frontend

# Install dependencies
npm install

# Start dev server
npm run dev

# Frontend runs on http://localhost:3000
```

---

## Development Workflow

### Daily Development

```bash
# Terminal 1: Backend
cd backend
./mvnw spring-boot:run -Dspring.profiles.active=dev

# Terminal 2: Frontend
cd frontend
npm run dev

# Terminal 3: Database (if not using Docker)
docker run --name spendos-postgres \
  -e POSTGRES_USER=spendos_user \
  -e POSTGRES_PASSWORD=spendos_password \
  -e POSTGRES_DB=spendos \
  -p 5432:5432 \
  postgres:16-alpine
```

### Testing

**Backend Tests:**
```bash
cd backend

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=UserControllerTest

# With coverage report
mvn test jacoco:report
# Report: target/site/jacoco/index.html
```

**Frontend Tests:**
```bash
cd frontend

# Run tests
npm test

# Run with UI
npm run test:ui

# With coverage
npm run test:coverage
```

### Building

**Backend Build:**
```bash
cd backend

# Build JAR
mvn clean package

# Output: target/spendos-backend-0.1.0.jar
```

**Frontend Build:**
```bash
cd frontend

# Build for production
npm run build

# Output: dist/ directory
```

### Code Quality

**Backend:**
```bash
cd backend

# Linting
mvn checkstyle:check

# Bug detection
mvn spotbugs:check

# Dependency security scan
mvn org.owasp:dependency-check-maven:check
```

**Frontend:**
```bash
cd frontend

# Linting
npm run lint

# Type checking
npm run type-check

# Format code
npm run format
```

---

## Database Management

### Create Tables

**Using Docker Compose:**
```bash
# Migrations run automatically via Flyway
docker-compose up postgres
# Wait for "database system is ready to accept connections"
```

**Manual Flyway Migration:**
```bash
cd backend
mvn flyway:migrate
```

### Access Database

```bash
# Using Docker
docker exec -it spendos-postgres psql -U spendos_user -d spendos

# Using local PostgreSQL
psql -U spendos_user -d spendos -h localhost

# Using pgAdmin (if running in docker-compose)
# http://localhost:5050
# Username: admin@example.com
# Password: admin
```

### Database Commands

```sql
-- List tables
\dt

-- Describe table
\d transactions

-- Show schema
\d+ transactions

-- Execute query
SELECT COUNT(*) FROM transactions;

-- Exit
\q
```

---

## Troubleshooting

### Backend Issues

**Port 8080 already in use:**
```bash
# Kill process using port 8080
# macOS/Linux:
lsof -i :8080
kill -9 <PID>

# Windows:
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

**Database connection refused:**
```bash
# Check if PostgreSQL is running
# Docker:
docker ps | grep postgres

# Verify connection string
# Check in .env: DATABASE_URL=postgresql://user:pass@host:port/db
```

**Migrations failed:**
```bash
# Check Flyway status
cd backend
mvn flyway:info

# Reset database (careful!)
mvn flyway:clean
mvn flyway:migrate
```

### Frontend Issues

**Port 3000 already in use:**
```bash
# Kill process
# macOS/Linux:
lsof -i :3000
kill -9 <PID>

# Windows:
netstat -ano | findstr :3000
taskkill /PID <PID> /F
```

**Node modules issues:**
```bash
cd frontend

# Clear cache
npm cache clean --force

# Remove node_modules
rm -rf node_modules package-lock.json

# Reinstall
npm install
```

**API connection issues:**
```bash
# Check backend is running
curl http://localhost:8080/api/v1/health

# Check CORS settings in backend
# backend/src/main/resources/application.yml
# cors.allowed-origins should include http://localhost:3000
```

### Docker Issues

**Containers won't start:**
```bash
# Check logs
docker-compose logs

# Remove and restart
docker-compose down -v
docker-compose up --build
```

**Database volumes full:**
```bash
# Clean up Docker system
docker system prune -a --volumes

# This removes all unused containers, images, and volumes
```

---

## Environment Variables

### Backend (.env or environment variables)

| Variable | Example | Description |
|----------|---------|-------------|
| DATABASE_URL | postgresql://localhost:5432/spendos | Database connection |
| DATABASE_USERNAME | spendos_user | Database user |
| DATABASE_PASSWORD | password123 | Database password |
| JWT_SECRET | (random 32+ chars) | JWT signing secret |
| APP_ENV | development | Environment: development, staging, production |
| CORS_ALLOWED_ORIGINS | http://localhost:3000 | Allowed frontend URLs |

### Frontend

Set in `frontend/.env` or `frontend/.env.local`:

```
VITE_API_URL=http://localhost:8080/api
VITE_ENV=development
```

---

## Git Workflow

### Initial Setup

```bash
# Clone repository
git clone https://github.com/yourusername/spendos.git
cd spendos

# Create your feature branch
git checkout -b feature/your-feature-name

# Make changes, commit, and push
git add .
git commit -m "feat: add new feature"
git push origin feature/your-feature-name

# Create Pull Request on GitHub
```

### Commit Convention

```
feat: add new feature
fix: fix bug
test: add tests
docs: update documentation
refactor: improve code structure
security: fix security issue
perf: improve performance
chore: maintenance tasks
```

---

## Useful Commands

### Maven Commands

```bash
# Clean build
mvn clean

# Compile
mvn compile

# Run tests
mvn test

# Build JAR
mvn package

# Install to local repository
mvn install

# Skip tests during build
mvn clean package -DskipTests

# Run specific goal
mvn spring-boot:run

# Show dependency tree
mvn dependency:tree
```

### npm Commands

```bash
# Install dependencies
npm install

# Update dependencies
npm update

# Add new package
npm install package-name

# Remove package
npm uninstall package-name

# Run development server
npm run dev

# Build for production
npm run build

# Run tests
npm test

# Check for security vulnerabilities
npm audit
```

### Docker Commands

```bash
# Build image
docker build -t spendos-backend:latest .

# Run container
docker run -p 8080:8080 spendos-backend:latest

# Execute command in running container
docker exec -it container_name bash

# View logs
docker logs -f container_name

# Stop container
docker stop container_name

# Remove container
docker rm container_name
```

---

## Next Steps

1. ✅ **Read** [ARCHITECTURE.md](../ARCHITECTURE.md) to understand the system design
2. ✅ **Review** [API_DESIGN.md](../API_DESIGN.md) to understand the API
3. ✅ **Check** [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) for Phase 2 tasks
4. ✅ **Start coding** Phase 2: Authentication & User Management

---

## Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://react.dev)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [Maven Documentation](https://maven.apache.org/guides/)

---

## Getting Help

- Check the troubleshooting section above
- Review GitHub Issues
- Create a new issue with error logs and environment details
- Check documentation files for specific topics

---

**Happy coding! 🚀**

