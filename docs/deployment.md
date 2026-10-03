# TechConnect Deployment & Operations Guide

This document defines the deployment, containerization, operational management, database persistence, and backup/restore procedures for **TechConnect — AI-Powered Enterprise IT Service Management Platform**.

---

## 1. System Architecture & Container Topology

TechConnect is packaged as a multi-container system orchestrating five primary services:

```
                            [ External Web Clients / Browsers ]
                                            │
                                            ▼
                          ┌───────────────────────────────────┐
                          │   Frontend Container (Nginx)      │
                          │   Port: 5173 (Internal :80)       │
                          │   - Serves React/Vite SPA         │
                          │   - Reverse proxies /api/ requests│
                          └─────────────────┬─────────────────┘
                                            │ /api/
                                            ▼
                          ┌───────────────────────────────────┐
                          │   Backend Container (Spring Boot) │
                          │   Port: 8080 (Non-root user 1001) │
                          │   - REST API & RBAC Security      │
                          │   - Actuator Health Probes        │
                          │   - In-Memory Rate Limiting       │
                          └──────────┬─────────────┬──────────┘
                                     │             │
                    PostgreSQL / JPA │             │ MongoDB Client
                                     ▼             ▼
       ┌───────────────────────────────┐ ┌───────────────────────────────┐
       │ PostgreSQL 16 + pgvector      │ │ MongoDB 7.0                   │
       │ Port: 5432 (Isolated/Internal)│ │ Port: 27017 (Isolated/Internal│
       │ - Relational ITSM Schema      │ │ - Unstructured Knowledge Base │
       │ - Vector Chunks (HNSW index)  │ │   Articles & Metadata         │
       │ Volume: `postgres_data`       │ │ Volume: `mongo_data`          │
       └───────────────────────────────┘ └───────────────────────────────┘
                                     ▲
                     pgvector access │
                                     │ HTTP REST
                                     ▼
                          ┌───────────────────────────────────┐
                          │ Python AI Microservice (FastAPI)  │
                          │ Port: 8000 (Non-root appuser 1000)│
                          │ - Ticket Intelligence (Classifier)│
                          │ - SentenceTransformers Embeddings │
                          │ - RAG & Resolution Assistant      │
                          │ Volume: `ai_model_cache`          │
                          └───────────────────────────────────┘
```

---

## 2. Prerequisites

### Host System Requirements

| Component | Minimum Version | Notes |
|:---|:---|:---|
| **Docker Engine / Desktop** | 24.0+ | Docker Desktop on Windows requires **WSL2** (`wsl --install`) or Hyper-V backend enabled |
| **Docker Compose** | 2.20+ (Compose v2) | Syntax uses standard compose specification |
| **Java JDK** (Local dev) | OpenJDK 21 LTS | Eclipse Temurin 21 recommended |
| **Python** (Local dev) | Python 3.12.x | Virtual environment with pip/uv |
| **Node.js** (Local dev) | Node 20 LTS | npm 10+ |

### Port Allocation

The default configuration binds the following ports:

| Service | Host Port | Container Port | Exposure | Purpose |
|:---|:---|:---|:---|:---|
| **Frontend** | `5173` | `80` | Public / External | Nginx reverse proxy & React SPA |
| **Backend** | `8080` | `8080` | Host-only (`127.0.0.1:8080`) | Spring Boot REST API & Actuator |
| **AI Service** | Internal | `8000` | Internal network only | FastAPI Ticket Intelligence |
| **PostgreSQL** | `5432` | `5432` | Host-only (`127.0.0.1:5432`) | Relational DB & pgvector |
| **MongoDB** | `27017` | `27017` | Host-only (`127.0.0.1:27017`)| Knowledge Base document store |

---

## 3. Environment Variables Configuration

Copy `.env.example` to `.env` in the project root:

```bash
# Windows PowerShell
Copy-Item .env.example .env

# Linux / macOS
cp .env.example .env
```

### Environment Variable Reference

```env
# ==============================================================================
# Database Configuration
# ==============================================================================
POSTGRES_DB=techconnect_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_postgres_password_here
POSTGRES_PORT=5432
POSTGRES_MAX_POOL_SIZE=20
POSTGRES_MIN_IDLE=5

MONGODB_DB=techconnect_knowledge
MONGODB_USER=techconnect_mongo_user
MONGODB_PASSWORD=your_secure_mongo_password_here
MONGODB_PORT=27017
MONGODB_URI=mongodb://mongodb:27017/techconnect_knowledge

# ==============================================================================
# Security & JWT Configuration
# ==============================================================================
JWT_SECRET=your_base64_encoded_minimum_256_bit_secure_jwt_secret_key_here
JWT_EXPIRATION_MS=3600000

# ==============================================================================
# CORS Configuration (Comma-separated origins)
# ==============================================================================
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://localhost

# ==============================================================================
# AI Ticket Intelligence & LLM Configuration
# ==============================================================================
AI_SERVICE_URL=http://ai-service:8000
TECHCONNECT_LLM_PROVIDER=extractive
TECHCONNECT_LLM_MODEL=grounded-extractive-v1
TECHCONNECT_LLM_API_KEY=
AI_SERVICE_TIMEOUT_MS=5000
COPILOT_TIMEOUT_MS=10000
RESOLUTION_TIMEOUT_MS=10000

# ==============================================================================
# Rate Limiting Configuration (Per Client IP)
# ==============================================================================
RATE_LIMIT_ENABLED=true
RATE_LIMIT_AI_REQUESTS_PER_MINUTE=20
RATE_LIMIT_SEARCH_REQUESTS_PER_MINUTE=60
RATE_LIMIT_GENERAL_REQUESTS_PER_MINUTE=120
```

> [!IMPORTANT]
> Never commit `.env` or production secrets to source control. Ensure `JWT_SECRET` is at least 256 bits (32+ characters) long.

---

## 4. Docker Build & Service Management

### Step 1: Validate Configuration

```bash
docker compose config
```

### Step 2: Build Container Images

Build all container images using the multi-stage build definitions:

```bash
docker compose build
```

To build a specific service without cache:

```bash
docker compose build --no-cache backend
docker compose build --no-cache frontend
docker compose build --no-cache ai-service
```

### Step 3: Start Services

Start all services in detached mode:

```bash
docker compose up -d
```

### Step 4: Verify Running Services

```bash
docker compose ps
```

All 5 services should show state `Up (healthy)`.

### Step 5: Stop Services

```bash
# Graceful stop
docker compose down

# Stop and remove volumes (WARNING: destroys local database state)
docker compose down -v
```

---

## 5. Health Checks & Verification

Each service includes a native health check configured in `docker-compose.yml`:

| Service | Health Check Command | Expected Output | Details Exposed |
|:---|:---|:---|:---|
| **Frontend** | `curl -f http://localhost/health` | `HTTP 200 OK` ("healthy") | None |
| **Backend** | `curl -f http://localhost:8080/actuator/health` | `{"status":"UP"}` | Sanitized (no DB URLs/creds) |
| **AI Service**| `curl -f http://localhost:8000/health` | `{"status":"ok","model_loaded":true}` | Model and memory status |
| **PostgreSQL**| `pg_isready -U postgres -d techconnect_db` | `accepting connections` | None |
| **MongoDB** | `mongosh --eval 'db.runCommand({ ping: 1 }).ok'` | `1` | None |

### Manual Verification Commands

```bash
# Verify backend Actuator health (sanitized, show-details=never)
curl -i http://localhost:8080/actuator/health

# Verify AI microservice health
curl -i http://localhost:8000/health

# Verify frontend Nginx probe
curl -i http://localhost:5173/health
```

---

## 6. Logging & Observability

### Viewing Logs

```bash
# Tail logs for all services
docker compose logs -f

# Tail logs for specific service
docker compose logs -f backend
docker compose logs -f ai-service
docker compose logs -f frontend

# View last 100 log lines with timestamps
docker compose logs --tail=100 -t backend
```

### Log Sanitization & Security Standard

TechConnect adheres to strict log-redaction policies across all tiers:
1. **Passwords**: Never logged in Spring Boot (`DatabaseInitializer`, `AuthServiceImpl`) or Python services.
2. **JWT Tokens**: Logged only as parse error status codes or generic messages; raw tokens are never emitted.
3. **Authorization Headers**: Suppressed in HTTP access logs.
4. **LLM API Keys**: Masked during provider configuration initialization.
5. **Database Connection Strings**: Omitted from Actuator health endpoints via `management.endpoint.health.show-details=never`.

---

## 7. Rate Limiting & Protection Against Abuse

The backend incorporates an in-memory sliding window rate limiter (`RateLimitingFilter`) enabled by default:

| Endpoint Pattern | Category | Default Limit | Response on Breach |
|:---|:---|:---|:---|
| `/api/ai/copilot/**`, `/api/ai/tickets/**/resolution-suggestions` | AI Operations | 20 requests / min | `HTTP 429 Too Many Requests`, `Retry-After: 60` |
| `/api/knowledge-base/search/**`, `/api/knowledge-base/articles/vector-search` | Semantic Search | 60 requests / min | `HTTP 429 Too Many Requests`, `Retry-After: 60` |
| Other `/api/**` endpoints | General API | 120 requests / min | `HTTP 429 Too Many Requests`, `Retry-After: 60` |
| `/actuator/health/**`, `/actuator/info` | Health Monitoring | Unlimited | Whitelisted |

---

## 8. Database Persistence vs. Backups

> [!WARNING]
> **Docker Volume Persistence is NOT a Backup.**
> A Docker named volume (`postgres_data`, `mongo_data`) persists files across container restarts and updates. However, it does not protect against host file corruption, hardware failure, accidental `docker compose down -v`, or administrative error. Regular logical and physical backups are mandatory.

### Storage Locations

- **PostgreSQL Volume**: `postgres_data` -> mapped to `/var/lib/postgresql/data`
- **MongoDB Volume**: `mongo_data` -> mapped to `/data/db`
- **Model Cache Volume**: `ai_model_cache` -> mapped to `/app/data/models_cache`

---

## 9. Backup & Disaster Recovery Procedures

### 9.1 PostgreSQL Backup (Relational ITSM + pgvector)

PostgreSQL stores the core relational data (tickets, users, roles, SLAs, audit logs) as well as the vector database table `knowledge_article_vectors` containing 384-dimensional vector embeddings and HNSW indexes.

#### Create Backup

```bash
# Format: Custom binary archive (compressed, flexible restore)
docker exec techconnect-postgres pg_dump \
  -U postgres \
  -d techconnect_db \
  -F c \
  -b \
  -v \
  -f /tmp/techconnect_pg_backup_$(date +%Y%m%d_%H%M%S).dump

# Copy backup file from container to host backup storage
docker cp techconnect-postgres:/tmp/techconnect_pg_backup_*.dump ./backups/postgres/
```

*For Windows PowerShell:*
```powershell
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
docker exec techconnect-postgres pg_dump -U postgres -d techconnect_db -F c -b -f "/tmp/techconnect_pg_$timestamp.dump"
docker cp "techconnect-postgres:/tmp/techconnect_pg_$timestamp.dump" ".\backups\postgres\techconnect_pg_$timestamp.dump"
```

#### Restore PostgreSQL

```bash
# 1. Ensure pgvector extension exists in destination database
docker exec -i techconnect-postgres psql -U postgres -d techconnect_db -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 2. Restore database from dump
docker exec -i techconnect-postgres pg_restore \
  -U postgres \
  -d techconnect_db \
  --clean \
  --if-exists \
  -v /tmp/techconnect_pg_backup.dump
```

*For Windows PowerShell:*
```powershell
docker cp ".\backups\postgres\techconnect_pg_backup.dump" "techconnect-postgres:/tmp/techconnect_pg_backup.dump"
docker exec -i techconnect-postgres pg_restore -U postgres -d techconnect_db --clean --if-exists -v /tmp/techconnect_pg_backup.dump
```

---

### 9.2 MongoDB Backup (Knowledge Base Articles)

MongoDB stores full-text articles, rich markdown descriptions, article categories, view counts, and author relations.

#### Create Backup

```bash
# Archive dump with compression
docker exec techconnect-mongodb mongodump \
  --db techconnect_knowledge \
  --archive=/tmp/techconnect_mongo_backup_$(date +%Y%m%d_%H%M%S).gz \
  --gzip

# Copy archive from container to host backup storage
docker cp techconnect-mongodb:/tmp/techconnect_mongo_backup_*.gz ./backups/mongodb/
```

*For Windows PowerShell:*
```powershell
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
docker exec techconnect-mongodb mongodump --db techconnect_knowledge --archive="/tmp/techconnect_mongo_$timestamp.gz" --gzip
docker cp "techconnect-mongodb:/tmp/techconnect_mongo_$timestamp.gz" ".\backups\mongodb\techconnect_mongo_$timestamp.gz"
```

#### Restore MongoDB

```bash
# Restore from compressed archive, dropping existing collections
docker exec -i techconnect-mongodb mongorestore \
  --nsInclude="techconnect_knowledge.*" \
  --archive=/tmp/techconnect_mongo_backup.gz \
  --gzip \
  --drop
```

*For Windows PowerShell:*
```powershell
docker cp ".\backups\mongodb\techconnect_mongo_backup.gz" "techconnect-mongodb:/tmp/techconnect_mongo_backup.gz"
docker exec -i techconnect-mongodb mongorestore --nsInclude="techconnect_knowledge.*" --archive="/tmp/techconnect_mongo_backup.gz" --gzip --drop
```

---

## 10. Operational Troubleshooting Guide

### Issue 1: Backend Reports `Database connection failed`
- **Symptom**: Actuator `/actuator/health` returns `DOWN` or logs show `Connection refused` to `postgres:5432`.
- **Resolution**:
  1. Inspect PostgreSQL container: `docker compose ps postgres`
  2. Inspect logs: `docker compose logs postgres`
  3. Verify internal DNS: `docker compose exec backend nc -zv postgres 5432`

### Issue 2: AI Resolution Assistant / Copilot Times Out
- **Symptom**: HTTP 503 or fallback answer with error message "AI service temporarily unavailable".
- **Resolution**:
  1. Check AI service container status: `docker compose ps ai-service`
  2. Test health probe: `curl http://localhost:8000/health`
  3. Increase timeout in `.env` if using external LLM:
     ```env
     COPILOT_TIMEOUT_MS=15000
     RESOLUTION_TIMEOUT_MS=15000
     ```

### Issue 3: Rate Limit Exceeded (HTTP 429)
- **Symptom**: Frontend displays toast notification "Too many requests. Please retry in 60 seconds."
- **Resolution**:
  1. Inspect client IP and request headers.
  2. If running automated tests or batch ingestion, temporarily raise limits in `.env`:
     ```env
     RATE_LIMIT_AI_REQUESTS_PER_MINUTE=100
     RATE_LIMIT_SEARCH_REQUESTS_PER_MINUTE=200
     ```
  3. Restart backend: `docker compose restart backend`.

### Issue 4: Docker Desktop Linux Engine Unavailable on Windows
- **Symptom**: `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`.
- **Resolution**:
  1. Windows requires WSL2 or Hyper-V for Docker Linux containers.
  2. Run in elevated PowerShell: `wsl --install`.
  3. Restart Windows, open Docker Desktop, and verify `docker ps` returns active engine status.
