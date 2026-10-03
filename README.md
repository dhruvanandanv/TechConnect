# TechConnect

> **AI-Powered Enterprise IT Service Management (ITSM) Platform**  
> *A production-engineered full-stack solution featuring automated SLA governance, polyglot persistence, grounded RAG intelligence, and multi-container deployment.*

[![Java](https://img.shields.io/badge/Java-21_LTS-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19.x-blue.svg)](https://react.dev/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115.x-009688.svg)](https://fastapi.tiangolo.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16_+_pgvector-336791.svg)](https://github.com/pgvector/pgvector)
[![MongoDB](https://img.shields.io/badge/MongoDB-7.0-47A248.svg)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage_Builds-2496ED.svg)](https://www.docker.com/)
[![Tests](https://img.shields.io/badge/Tests-269_Passed_(100%25)-success.svg)](backend)

---

## 1. Problem Statement

Modern enterprise IT departments suffer from chronic ticket backlogs, repetitive manual triage delays, and frequent SLA breaches. Meanwhile, standard generative AI solutions present severe business risks: unvetted LLMs hallucinate inaccurate configuration commands, expose sensitive internal tickets across organizational boundaries, or execute unauthorized automated actions on operational systems.

**TechConnect** solves these challenges by combining strict deterministic service desk governance with a non-autonomous, grounded Retrieval-Augmented Generation (RAG) architecture. It eliminates manual triage bottlenecks, enforces dynamic priority-based SLAs, and empowers support engineers with cited, verifiable technical resolutions with zero hallucination tolerance.

---

## 2. Key Features

- **Deterministic Ticket Lifecycle**: Enforces strict state transitions (`OPEN` → `ASSIGNED` → `IN_PROGRESS` → `WAITING_FOR_USER` → `RESOLVED` → `CLOSED`, plus `ESCALATED`) with complete audit logging and status history tracking.
- **Dynamic SLA Automation Engine**: Calculates priority-based First Response and Resolution deadlines (Critical: 2h, High: 4h, Medium: 8h, Low: 24h). Automatically pauses SLA countdowns when awaiting employee input (`WAITING_FOR_USER`) and dynamically resumes with adjusted deadlines upon response.
- **Role-Based Access Control (RBAC)**: Tailored dashboards and granular permission barriers across 4 user roles: `ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, and `ROLE_ADMIN`.
- **AI Ticket Triage**: Scikit-Learn TF-IDF classification and rule-based urgency inference auto-predicting incident category, priority, and support team routing upon ticket creation.
- **RAG-Grounded AI Support Copilot**: Dense vector semantic retrieval (`sentence-transformers/all-MiniLM-L6-v2`) in PostgreSQL `pgvector`, synthesizing natural-language troubleshooting steps with verifiable `[SOURCE N]` citations and strict anti-hallucination thresholds (`minSimilarity >= 0.30`).
- **AI Engineer Resolution Assistant**: Tri-fold grounded proposal engine combining active ticket context, verified knowledge SOPs, and similar historical resolved tickets to generate actionable troubleshooting steps. Non-autonomous design stages text for engineer inspection.
- **Real-Time ITSM Analytics**: Interactive executive dashboards monitoring 14-day ticket trends, category shares, priority distribution, engineer capacity, and resolution velocities.
- **Hardened Multi-Container Deployment**: Multi-stage Docker packaging, unprivileged non-root runtime users (`UID 1001` / `UID 1000`), container-aware JVM tuning, in-memory sliding-window rate limiting, and sanitized Actuator health probes.

---

## 3. High-Level Architecture

```
                            [ External Web Clients / Browsers ]
                                            │
                                            ▼
                          ┌───────────────────────────────────┐
                          │   Frontend Container (Nginx)      │
                          │   Port: 5173 (Internal :80)       │
                          │   - Serves React 19 SPA           │
                          │   - Ingress security headers      │
                          │   - Reverse proxies /api/ requests│
                          └─────────────────┬─────────────────┘
                                            │ /api/
                                            ▼
                          ┌───────────────────────────────────┐
                          │   Backend Container (Spring Boot) │
                          │   Port: 8080 (Non-root user 1001) │
                          │   - REST API & RBAC Security      │
                          │   - Sliding Window Rate Limiting  │
                          │   - Actuator Health Probes        │
                          │   - HikariCP Connection Pool      │
                          └──────────┬─────────────┬──────────┘
                                     │             │
                    PostgreSQL / JPA │             │ MongoDB Client
                                     ▼             ▼
       ┌───────────────────────────────┐ ┌───────────────────────────────┐
       │ PostgreSQL 16 + pgvector      │ │ MongoDB 7.0                   │
       │ Port: 5432 (Internal network) │ │ Port: 27017 (Internal network)│
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
                          │ Port: 8000 (Non-root user 1000)   │
                          │ - Ticket Intelligence Classifier  │
                          │ - SentenceTransformers Embeddings │
                          │ - Grounded RAG Copilot            │
                          │ Volume: `ai_model_cache`          │
                          └───────────────────────────────────┘
```

> Complete Mermaid lifecycle, sequence, and component diagrams are available in [docs/system-diagrams.md](docs/system-diagrams.md).

---

## 4. Technology Stack

| Layer | Technologies & Frameworks |
|:---|:---|
| **Frontend** | React 19, JavaScript (ES6+), Vite, Bootstrap 5, Bootstrap Icons, React Router 6, Axios |
| **Backend** | Java 21 LTS, Spring Boot 3.3.x, Spring Data JPA, Spring Security 6, JWT, Lombok, Maven |
| **Relational & Vector DB** | PostgreSQL 16 with `pgvector` extension (384-dimensional HNSW vector search) |
| **Document DB** | MongoDB 7.0 (Knowledge Base articles, revision history, view/feedback counters) |
| **AI & NLP Microservice** | Python 3.12, FastAPI, PyTorch, SentenceTransformers (`all-MiniLM-L6-v2`), Scikit-Learn |
| **DevOps & Containers** | Docker, Docker Compose v2, Nginx 1.27 Alpine, Eclipse Temurin 21 JRE |

---

## 5. Security & Defense-in-Depth

TechConnect enforces rigorous security principles across every tier:
- **Stateless Authentication**: Cryptographically signed HMAC-SHA256 JSON Web Tokens (JWT) with 1-hour expiration. Passwords are never placed inside tokens.
- **Password Storage**: BCrypt adaptive one-way hashing (cost factor 10) with individual 128-bit salts.
- **Insecure Direct Object Reference (IDOR) Defense**: Service-layer `assertCanViewTicket` ownership and role checks. Employees can never inspect or query other employees' tickets.
- **Sliding-Window Rate Limiting**: In-memory `RateLimitingFilter` shielding AI endpoints (20 rpm), search operations (60 rpm), and general API calls (120 rpm), returning `HTTP 429 Too Many Requests` with `Retry-After: 60`.
- **HTTP Security Headers**: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: strict-origin-when-cross-origin`.
- **Prompt Injection Defense**: Heuristic pattern filters intercepting attempts to bypass security controls or dump credentials before reaching the vector engine or LLM.
- **Sanitized Observability**: Spring Boot Actuator `/actuator/health` configured with `show-details=never`, concealing database connection strings and credentials from unauthenticated callers.

---

## 6. Project Structure

```
TechConnect/
├── backend/                               # Spring Boot 3.3.x Backend Application
│   ├── src/main/java/com/techconnect/
│   │   ├── client/                        # Internal HTTP clients (AI microservice)
│   │   ├── config/                        # SecurityConfig, RateLimitingFilter, Hikari
│   │   ├── controller/                    # REST API Controllers (Ticket, SLA, Analytics, etc.)
│   │   ├── document/                      # MongoDB Document Models (KnowledgeArticle)
│   │   ├── dto/                           # Request & Response Data Transfer Objects
│   │   ├── entity/                        # PostgreSQL JPA Relational Entities
│   │   ├── exception/                     # GlobalExceptionHandler and sanitized responses
│   │   ├── repository/                    # Spring Data JPA & MongoDB Repositories
│   │   ├── security/                      # JWT filters, UserDetailsService
│   │   └── service/                       # Business logic, SLA tracking, state machines
│   ├── src/main/resources/
│   │   ├── application.properties         # Default development properties
│   │   └── application-prod.properties    # Hardened production profile
│   └── Dockerfile                         # Multi-stage Temurin 21 JRE container
│
├── frontend/                              # React 19 / Vite Single Page Application
│   ├── src/
│   │   ├── components/                    # Reusable UI elements (Navbar, Sidebar, Badges)
│   │   ├── context/                       # AuthContext with token persistence
│   │   ├── pages/                         # Dashboard, Tickets, Analytics, KnowledgeBase, Copilot
│   │   ├── services/                      # Axios API clients
│   │   └── utils/                         # Formatters and validators
│   ├── nginx.conf                         # Production Nginx reverse proxy configuration
│   └── Dockerfile                         # Multi-stage Node 20 / Nginx Alpine container
│
├── ai_services/                           # Python 3.12 FastAPI AI Microservice
│   └── ticket_intelligence/
│       ├── app/
│       │   ├── ml/                        # Model training and TF-IDF pipeline
│       │   ├── rag/                       # RAG service, context builder, prompt defenses
│       │   ├── routers/                   # FastAPI routes (triage, copilot, resolution)
│       │   └── vector/                    # pgvector client, embeddings, chunking
│       ├── tests/                         # Pytest test suite (71 passing tests)
│       └── Dockerfile                     # Python slim container with pre-cached weights
│
├── docs/                                  # Complete Technical Documentation Suite
│   ├── architecture.md                    # Core architecture specification
│   ├── system-diagrams.md                 # 10 Mermaid architectural & lifecycle diagrams
│   ├── deployment.md                      # Container deployment and DR runbooks
│   ├── production-readiness.md            # Formal readiness assessment & audit
│   ├── powerbi-analytics.md               # Power BI Star Schema, DAX, and 5-page layout
│   ├── screenshot-guide.md                # 12-screen demo walkthrough checklist
│   ├── interview-preparation.md           # Technical interview defense & pitch scripts
│   ├── resume-project-description.md      # Resume bullets and portfolio summaries
│   ├── project-inventory.md               # Complete ledger of technologies and tests
│   ├── security.md                        # Security concepts, JWT, RBAC, IDOR
│   ├── rag.md                             # RAG Copilot architecture
│   ├── ai-resolution-assistant.md         # Tri-fold resolution assistant
│   └── api.md                             # REST API contracts
│
├── .env.example                           # Safe configuration template
├── docker-compose.yml                     # Production multi-service orchestration
└── README.md                              # Portfolio documentation
```

---

## 7. Running the Platform

### Option A: Complete Multi-Container Deployment (Recommended)

1. **Configure Environment**:
   ```bash
   cp .env.example .env
   # Edit .env and supply secure secrets (JWT_SECRET, database passwords)
   ```

2. **Validate Configuration**:
   ```bash
   docker compose config
   ```

3. **Build & Start Services**:
   ```bash
   docker compose build
   docker compose up -d
   ```

4. **Verify Health**:
   ```bash
   docker compose ps
   curl -i http://localhost:8080/actuator/health
   curl -i http://localhost:8000/health
   ```
   Open `http://localhost:5173` in your browser.

---

### Option B: Local Development Execution

#### 1. Databases (PostgreSQL + pgvector & MongoDB)
Ensure PostgreSQL 16 is running on `localhost:5432` with `CREATE EXTENSION IF NOT EXISTS vector;`, and MongoDB 7.0 is running on `localhost:27017`.

#### 2. Python AI Microservice
```bash
cd ai_services/ticket_intelligence
py -3.12 -m venv .venv
# Activate virtual environment, then:
pip install -r requirements.txt
py -3.12 -m pytest
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### 3. Spring Boot Backend
```bash
cd backend
./mvnw.cmd clean test
./mvnw.cmd spring-boot:run
```

#### 4. React Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## 8. Automated Test Results

| Test Suite | Total Tests | Pass Count | Failures | Status |
|:---|:---:|:---:|:---:|:---:|
| **Python Pytest Suite** | 71 | 71 | 0 | **100% Passed** |
| **Spring Boot Maven Suite** | 198 | 198 | 0 | **100% Passed** |
| **Frontend Production Build** | 127 modules | 127 modules | 0 | **100% Passed** |

---

## 9. Portfolio Screenshots & Demo Walkthrough

A complete 12-screen capture checklist and walkthrough guide is provided in [docs/screenshot-guide.md](docs/screenshot-guide.md), featuring:
1. Authentication Portal & Role-Based Portals
2. Executive Dashboard with Real-Time KPIs
3. Ticket Creation with AI Predictive Triage
4. Ticket Details with Real-Time SLA Countdown Timeline
5. SLA Performance & Breached Incidents Tracker
6. Knowledge Base Management & SOP Repository
7. Semantic Search with Cosine Similarity Score Badges
8. AI Support Copilot with Anti-Hallucination Guardrails
9. Verifiable `[SOURCE N]` Citations and Article Provenance
10. AI Engineer Resolution Assistant with Tri-Fold Grounding
11. ITSM Executive Analytics (14-Day Activity Trends, Category Shares, Engineer Workload Scorecards)
12. Container Orchestration & Actuator Health Verification

---

## 10. Implemented Features vs. Future Possibilities

### Implemented & Verified in TechConnect:
- [x] Deterministic ticket state machine with SLA automation and clock pause/resume.
- [x] Dual-database polyglot persistence (PostgreSQL 16 + pgvector, MongoDB 7.0).
- [x] Dense semantic vector search using SentenceTransformers (`all-MiniLM-L6-v2`) and HNSW index.
- [x] 4-stage RAG AI Support Copilot with source citations and anti-hallucination refusal gates.
- [x] Tri-fold grounded AI Resolution Assistant with human-in-the-loop review.
- [x] Full-scale ITSM Executive Analytics dashboard with 14-day trends and engineer workloads.
- [x] Multi-stage Docker containers with non-root runtime users and sanitized health checks.
- [x] Sliding-window rate limiting protecting AI and semantic search endpoints.
- [x] Comprehensive 269-test automated regression suite.

### Future Scope (Outside Current Project Boundaries):
- *Real-Time WebSocket Streaming*: Live token-by-token streaming responses for conversational copilot sessions.
- *Power BI Service Gateway Integration*: Direct Cloud automated dataset refreshes via On-Premises Data Gateway.
- *Autonomous Remediation Agents*: Automated diagnostic script execution on target hosts (subject to strict IAM authorization).
- *Silent Refresh Token Rotation*: Rolling HTTP-only refresh tokens for zero-friction session extension.

---

## 11. License & Academic Attribution

TechConnect is developed as an enterprise IT Service Management research and portfolio project. All code, architecture diagrams, and documentation are original works demonstrating modern enterprise full-stack software engineering.
