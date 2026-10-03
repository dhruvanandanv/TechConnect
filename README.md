# TechConnect

**TechConnect** is an enterprise-grade AI-Powered IT Service Management (ITSM) Platform. It streamlines technical support workflows across organizations with automated ticket lifecycle management, strict SLA governance, real-time tracking, and AI-assisted triage and resolution.

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Key Features](#key-features)
3. [Technology Stack](#technology-stack)
4. [System Architecture](#system-architecture)
5. [Project Structure](#project-structure)
6. [Prerequisites](#prerequisites)
7. [Running Locally](#running-locally)
8. [API Endpoints](#api-endpoints)
9. [Development Roadmap](#development-roadmap)

---

## Project Overview
TechConnect bridges the gap between employees experiencing technical bottlenecks (e.g., VPN issues, hardware failures, access controls) and IT support teams. It enforces role-based access control (Employee, Engineer, Manager, Admin), monitors SLA compliance with automated breach detection, and integrates AI assistance for intelligent category prediction and RAG-grounded troubleshooting.

---

## Key Features
- **Ticket Lifecycle Management**: Deterministic state transitions from `OPEN` to `ASSIGNED`, `IN_PROGRESS`, `WAITING_FOR_USER`, `RESOLVED`, and `CLOSED`, with escalation support.
- **SLA Engine**: Configurable, priority-based resolution deadlines (Critical: 2h, High: 4h, Medium: 8h, Low: 24h).
- **Role-Based Portals**: Dedicated operational interfaces for Employees, Engineers, Managers, and Administrators.
- **AI Triage & Copilot**: Automated category/priority tagging and RAG-based resolution recommendations grounded in knowledge base articles.
- **Dual Database Architecture**: PostgreSQL for ACID transactional data + MongoDB for polymorphic knowledge articles and vector embeddings.
- **Enterprise Analytics**: Workload monitoring and Power BI connectivity for SLA and operational visibility.

---

## Technology Stack

| Layer | Technologies |
|---|---|
| **Frontend** | React.js, JavaScript, HTML5, CSS3, Bootstrap, React Router, Axios |
| **Backend** | Java 21, Spring Boot 3.3.x, Spring Data JPA, Spring Security, JWT, Lombok, Maven |
| **Databases** | PostgreSQL (Relational/Transactional), MongoDB (Knowledge Base/AI Docs) |
| **AI Service** | Python 3.11+, FastAPI, NLP, Embeddings, RAG, Vector Search |
| **DevOps & Tools** | Docker, Docker Compose, Git, Postman, Power BI |

---

## System Architecture

```
                  +--------------------------+
                  |  React Frontend Client   |
                  |  (Employee / Eng / Mgr)  |
                  +-------------+------------+
                                |
                           REST / JWT
                                |
                                v
                  +-------------+------------+
                  |    Spring Boot Backend   |
                  |     (Business Logic)     |
                  +-------+----------+-------+
                          |          |
         +----------------+          +----------------+
         |                                            |
         v                                            v
+------------------+                        +------------------+
|    PostgreSQL    |                        |   FastAPI (AI)   |
| (Tickets, Users, |                        |  (Triage & RAG)  |
|  SLAs, Audit)    |                        +--------+---------+
+------------------+                                 |
                                                     v
                                            +------------------+
                                            |     MongoDB      |
                                            | (Knowledge Base) |
                                            +------------------+
```

---

## Project Structure

```
TechConnect/
├── backend/            # Spring Boot application
├── frontend/           # React dashboard SPA
├── ai_services/        # Python FastAPI AI and RAG engine
├── docker/             # Docker configurations & container manifests
├── docs/               # Architecture, API, and DB documentation
│   ├── requirements.md
│   ├── architecture.md
│   ├── database.md
│   └── api.md
├── .env.example        # Environment variable blueprint
├── .gitignore          # Version control ignore rules
├── docker-compose.yml  # Multi-service composition
└── README.md           # Project documentation
```

---

## Phase Completion Status
- **Phase 1 – Project Foundation**: COMPLETE
- **Phase 2 – PostgreSQL + JPA Database**: COMPLETE
- **Phase 3 – Registration + Login**: COMPLETE
- **Phase 4 – JWT + Spring Security + RBAC**: COMPLETE
- **Phase 5 – IT Service Ticket Management**: COMPLETE
- **Phase 6 – SLA Management & Automation Engine**: COMPLETE
- **Phase 7 – React Frontend & Dashboard**: COMPLETE
- **Phase 8 – Full-Stack Integration Hardening & API Reliability**: COMPLETE
- **Phase 9 – AI-Powered Ticket Intelligence Service**: COMPLETE
- **Phase 10 – Knowledge Base Management System**: COMPLETE
- **Phase 11 – AI Knowledge Ingestion, Embeddings & Vector Search**: COMPLETE
- **Phase 12 – RAG-Based AI Support Copilot**: COMPLETE

---

## Running Locally

### 1. MongoDB & PostgreSQL Services
```bash
# Start MongoDB (27017) and PostgreSQL (5432) via Docker Compose
docker compose up -d

# Or ensure local MongoDB service is running on localhost:27017
# and PostgreSQL is running on localhost:5432
```

### 2. Python AI Ticket Intelligence Service
```bash
cd ai_services/ticket_intelligence
# Install dependencies
pip install -r requirements.txt

# Run ML test suite
pytest -v

# Start FastAPI service on http://localhost:8000
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 3. Spring Boot Backend
```bash
cd backend
# Run test suite (161 unit & integration tests)
./mvnw.cmd clean test

# Run application on http://localhost:8080
./mvnw.cmd spring-boot:run
```

### 4. React Frontend
```bash
cd frontend
# Install dependencies
npm install

# Run Vite dev server on http://localhost:5173
npm run dev

# Or build production distribution
npm run build
```

---

## Documentation
- **AI Engineer Resolution Assistant (Phase 13)**: Tri-fold grounding (Active Ticket Context, Verified Knowledge Articles, Similar Resolved Tickets), resolution prompt strategy, RBAC barriers, and non-autonomous advisory flow are documented in [docs/ai-resolution-assistant.md](docs/ai-resolution-assistant.md).
- **RAG-Based AI Support Copilot (Phase 12)**: 4-stage RAG architecture, semantic chunk retrieval, anti-hallucination gates, and source citations are documented in [docs/rag.md](docs/rag.md).
- **AI Knowledge Ingestion, Embeddings & Vector Search**: Text normalization, section-aware chunking, SentenceTransformers/FastEmbed (`all-MiniLM-L6-v2`, 384 dimensions), PostgreSQL + pgvector chunk storage, cosine vector retrieval, and version invalidation are documented in [docs/vector-search.md](docs/vector-search.md).
- **Knowledge Base Management System**: Polyglot persistence, MongoDB document model, RBAC lifecycle state machine, atomic view/feedback metrics, audit history, and future RAG data preparation are documented in [docs/knowledge-base.md](docs/knowledge-base.md).
- **AI Ticket Intelligence Architecture**: Model training, TF-IDF vectorization, priority inference, team routing, and fallback mechanisms are documented in [docs/ai-ticket-intelligence.md](docs/ai-ticket-intelligence.md).
- **Full-Stack Integration & Architecture**: Detailed full-stack architecture, API reliability, JWT lifecycle, error handling, CORS, and IDOR matrix are documented in [docs/integration.md](docs/integration.md).
- **Backend API Reference**: REST API contracts and error payloads are documented in [docs/api.md](docs/api.md).
- **Frontend Architecture**: Component layout, routing, role guards, and client state are documented in [docs/frontend.md](docs/frontend.md).


