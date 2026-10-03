# TechConnect Comprehensive Project Inventory & Technical Ledger

This document provides a complete inventory of technologies, frameworks, libraries, database schemas, security mechanisms, test suites, and documentation comprising **TechConnect — AI-Powered Enterprise IT Service Management Platform**.

---

## 1. Frontend Technologies & Component Inventory

| Category | Technology / Library | Version / Detail | Purpose |
|:---|:---|:---|:---|
| **Core Framework** | React.js | 19.x | Component-driven Single Page Application |
| **Build Tool** | Vite | 8.3.x | Fast HMR and optimized production bundling |
| **Styling & Icons** | Bootstrap & Bootstrap Icons | 5.3.x & 1.11.x | Responsive styling, grid system, and enterprise iconography |
| **Routing** | React Router DOM | 6.x | Client-side routing, protected routes, role guards |
| **HTTP Client** | Axios | 1.7.x | Intercepted REST communication with JWT injection |
| **Markdown Rendering** | React Markdown & Remark GFM | Latest | Renders knowledge base SOPs and AI Copilot responses |

### Frontend Page Inventory
1. `Login.jsx` — Authenticates users and stores JWT tokens in local storage.
2. `Register.jsx` — User self-registration with automatic role mapping.
3. `Dashboard.jsx` — Executive overview, role-specific KPI cards, quick actions, recent tickets.
4. `Analytics.jsx` — Full-scale ITSM Executive Analytics (Trends, Priority, Categories, SLA, Workloads, KB).
5. `Tickets.jsx` — Filterable, paginated ticket queues (My Tickets, Assigned Tickets, All Tickets).
6. `CreateTicket.jsx` — Multi-field ticket creation with AI predictive triage assistance.
7. `TicketDetails.jsx` — Detailed ticket inspection, SLA countdown, threaded comments, status transitions, AI Resolution Assistant.
8. `SlaDashboard.jsx` — Managerial SLA compliance monitoring and breached incidents tracker.
9. `KnowledgeBase.jsx` — Document repository with category filtering, search, and view/feedback metrics.
10. `KnowledgeArticleView.jsx` — Full Markdown article reader with version history and helpfulness rating.
11. `KnowledgeArticleEditor.jsx` — Markdown article authoring and editing with status workflows (Draft/Published/Archived).
12. `AiSupportCopilot.jsx` — Conversational support interface with verified source citations and anti-hallucination indicators.
13. `Profile.jsx` — Authenticated user details, system roles, department, and team memberships.
14. `NotFound.jsx` — Clean 404 navigation recovery page.

---

## 2. Backend Technologies & Component Inventory

| Category | Technology / Library | Version / Detail | Purpose |
|:---|:---|:---|:---|
| **Runtime & Language** | Java OpenJDK | 21 LTS | High-performance, type-safe core backend |
| **Framework** | Spring Boot | 3.3.x | Enterprise application framework |
| **Security** | Spring Security | 6.x | Stateless JWT authentication, RBAC, filter chain |
| **Data Access** | Spring Data JPA / Hibernate | 3.3.x | Object-Relational Mapping (ORM) and Criteria Queries |
| **Document Access** | Spring Data MongoDB | 3.3.x | Document querying and aggregation for Knowledge Base |
| **Observability** | Spring Boot Actuator | 3.3.x | Production liveness and readiness health probes |
| **Connection Pooling** | HikariCP | 5.1.x | High-throughput JDBC connection pooling |
| **Code Generation** | Project Lombok | 1.18.x | Minimizes boilerplate for getters, setters, and builders |
| **Build & Dependency** | Apache Maven | 3.9.x | Standardized build lifecycle and dependency management |

### Backend Component Counts
- **REST Controllers**: 9 (`TicketController`, `SlaController`, `KnowledgeArticleController`, `AiSupportCopilotController`, `AiResolutionAssistantController`, `AnalyticsController`, `AuthController`, `HealthController`, `RoleTestController`).
- **Service Interfaces & Implementations**: 8 service domains (`TicketService`, `SlaService`, `KnowledgeArticleService`, `AiSupportCopilotService`, `AiResolutionAssistantService`, `AnalyticsService`, `AuthService`, `JwtService`).
- **JPA & Mongo Repositories**: 15 repository interfaces.
- **Entity & Document Models**: 14 domain classes (12 PostgreSQL JPA entities + 2 MongoDB documents).
- **Data Transfer Objects (DTOs)**: 25+ request and response models with Jakarta validation annotations (`@Valid`, `@NotBlank`, `@Size`).

---

## 3. AI & Natural Language Processing Technologies

| Category | Technology / Library | Version / Detail | Purpose |
|:---|:---|:---|:---|
| **Framework** | FastAPI & Uvicorn | 0.115.x & 0.32.x | High-throughput asynchronous ASGI microservice |
| **Embeddings** | SentenceTransformers | 3.3.x (`all-MiniLM-L6-v2`) | 384-dimensional dense semantic vector generation |
| **Vector Engine** | pgvector (PostgreSQL extension) | pg16 / 0.7.x | Cosine distance search (`<=>`) with HNSW indexing |
| **Machine Learning** | Scikit-Learn | 1.5.x | TF-IDF vectorizer and Logistic Regression ticket classifier |
| **RAG Synthesis** | Extractive / LlmProvider | Custom / Modular | Grounded context builder, prompt security, and citations |
| **Data Processing** | NumPy & Pandas | 2.1.x & 2.2.x | High-performance vector calculations and dataset operations |

---

## 4. Databases & Storage Infrastructure

1. **PostgreSQL 16 + pgvector**:
   - Primary ACID relational database.
   - Relational Tables: `users`, `roles`, `teams`, `departments`, `tickets`, `ticket_status_history`, `ticket_comments`, `ticket_assignments`, `ticket_attachments`, `sla_rules`, `audit_logs`.
   - Vector Storage Table: `knowledge_article_vectors` (columns: `id`, `article_id`, `chunk_text`, `chunk_index`, `status`, `embedding vector(384)` with HNSW index).
2. **MongoDB 7.0**:
   - Document Collections: `knowledge_articles`, `knowledge_article_history`.
   - Polymorphic markdown content, structured troubleshooting sections, revision versions, view counts, and user feedback sets.
3. **Storage Volumes**:
   - `postgres_data` (Docker named volume for relational & vector data).
   - `mongo_data` (Docker named volume for knowledge base documents).
   - `ai_model_cache` (Docker named volume for pre-warmed ML model artifacts).

---

## 5. Security & Protection Infrastructure

1. **Authentication**: Stateless HMAC-SHA256 signed JSON Web Tokens (JWT) with 1-hour expiration.
2. **Password Cryptography**: BCrypt adaptive hashing with individual 128-bit salts and cost factor 10.
3. **Role-Based Access Control (RBAC)**: 4 hierarchical roles (`ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`).
4. **Insecure Direct Object Reference (IDOR) Defense**: Service-layer `assertCanViewTicket` ownership and role checks.
5. **Rate Limiting**: Thread-safe in-memory sliding window filter (AI: 20 rpm, Search: 60 rpm, General: 120 rpm) returning HTTP 429.
6. **HTTP Security Headers**: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
7. **Prompt Injection Defense**: Pre-generation heuristic regex scanner intercepting unauthorized instructions and credential exploits.
8. **Anti-Hallucination Quality Gate**: Hard cosine threshold (`0.30`) refusing generation when evidence is absent.
9. **Log Sanitization**: Zero logging of passwords, raw JWT tokens, API keys, or Authorization headers.

---

## 6. Automated Testing Ledger

| Test Suite | Framework | Total Tests | Pass Count | Failure Count | Execution Time |
|:---|:---|:---:|:---:|:---:|:---:|
| **Python ML & RAG Suite** | Pytest 9.1 / Python 3.12 | 71 | 71 | 0 | ~11.3s |
| **Spring Boot Backend Suite** | JUnit 5 / SpringBootTest / MockMvc | 198 | 198 | 0 | ~1m 55s |
| **Frontend Production Build** | Vite 8.3 / Rollup | 127 modules | 127 modules | 0 | ~0.7s |
| **Docker Compose Config** | Docker Compose v2 | 5 services | 5 services | 0 | Instant |

**Total Verified Automated Tests**: **269 automated tests** passing with 100% success rate.

---

## 7. Complete Documentation Ledger

1. `README.md` — Polished GitHub portfolio landing page and project overview.
2. `docs/architecture.md` — Core system architecture, communication patterns, and layered design.
3. `docs/system-diagrams.md` — 10 authoritative Mermaid architecture, lifecycle, and pipeline diagrams.
4. `docs/deployment.md` — Production container deployment, Actuator monitoring, and disaster recovery runbooks.
5. `docs/production-readiness.md` — Formal readiness score, threat mitigations, and operational assumptions.
6. `docs/powerbi-analytics.md` — Power BI Star Schema, DAX measures, SQL queries, and dashboard layout guide.
7. `docs/screenshot-guide.md` — Recommended 12-screen capture checklist and walkthrough instructions.
8. `docs/interview-preparation.md` — Deep-dive technical interview defense, pitch scripts, and concept Q&As.
9. `docs/resume-project-description.md` — Formatted resume bullets and portfolio summaries.
10. `docs/security.md` — Cryptography, JWT lifecycle, RBAC matrix, IDOR defenses, and rate limiting.
11. `docs/rag.md` — 4-stage RAG Copilot pipeline, chunking strategies, and anti-hallucination gates.
12. `docs/ai-resolution-assistant.md` — Tri-fold grounding engine, similar ticket matching, and advisory flow.
13. `docs/vector-search.md` — Dense vector search, FastEmbed/SentenceTransformers, and pgvector HNSW indexing.
14. `docs/knowledge-base.md` — MongoDB document lifecycle, versioning, and feedback metrics.
15. `docs/sla.md` — Dynamic SLA calculation, clock pausing rules, and breach detection.
16. `docs/api.md` — Complete REST API reference and error response payloads.
17. `docs/project-inventory.md` — This exhaustive ledger of technologies and test totals.
18. `docs/phase-15-final-report.md` — Final closure report for Phase 15.
