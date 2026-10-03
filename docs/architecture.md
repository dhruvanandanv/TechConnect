# TechConnect System Architecture

## 1. High-Level Architecture Overview
TechConnect follows a microservice-adjacent modular architecture with clear separation of concerns:
1. **Presentation Layer**: React Single Page Application (SPA) leveraging modern component design, responsive styling, and Axios client.
2. **Core Business Layer**: Spring Boot 3.x RESTful backend managing business logic, state machines, SLA engines, and access control.
3. **AI & RAG Engine**: Python FastAPI microservice dedicated to NLP classification, vector search, and LLM-assisted resolution synthesis.
4. **Data Persistence**:
   - **PostgreSQL**: Relational database for ACID transactions (Users, Tickets, Status History, SLA, Audits).
   - **MongoDB**: Document database for unstructured/semi-structured Knowledge Base articles and AI prompt/inference logs.

## 2. Backend Package Structure (Spring Boot)
The backend follows clean layered architecture:
```
com.techconnect/
├── config/             # Spring configuration (CORS, Security, OpenApi)
├── controller/         # REST Controllers exposing HTTP endpoints
├── dto/                # Request & Response Data Transfer Objects
├── entity/             # JPA entity models mapping relational tables
├── exception/          # GlobalExceptionHandler and custom exceptions
├── mapper/             # Entity-DTO mapping layer
├── repository/         # Spring Data JPA repositories
├── security/           # JWT filters, UserDetailsService, Auth providers
└── service/            # Business logic, SLA tracking, state transitions
```

## 3. Communication Patterns
- **Frontend <-> Backend**: HTTPS REST calls using JSON with Bearer JWT tokens.
- **Backend <-> PostgreSQL**: JDBC connection pooling via HikariCP + Spring Data JPA / Hibernate.
- **Backend <-> AI Service**: Internal synchronous HTTP calls via Spring's `RestClient` (`POST /api/v1/ticket-intelligence/analyze`) with configured timeouts and graceful fallback when unavailable.

## 4. AI Ticket Intelligence Microservice (Phase 9)
```
React Frontend (Analyze Button)
       |
       | REST + Bearer JWT
       v
Spring Boot TicketController (POST /api/tickets/analyze)
       |
       | RestClient (timeout 3000ms)
       v
Python FastAPI Microservice (POST /api/v1/ticket-intelligence/analyze)
       |
       +---> TF-IDF + LogisticRegression Classifier (Category Prediction)
       +---> Deterministic Rules Engine (Priority Urgency Inference)
       +---> Organizational Team Routing Matrix (Suggested Support Team)
       +---> Deterministic Summarizer (ITSM Incident Summary)
```
- **Predictive Assistance Only**: Human requester and engineer decisions remain strictly authoritative.
- **Circuit Fallback**: Failure of the AI microservice returns `aiAvailable: false` without impeding standard ticket creation.

## 5. Knowledge Base Management Architecture (Phase 10)
```
React Client (Knowledge Base Portal)
       |
       | REST + Bearer JWT
       v
Spring Boot KnowledgeArticleController (/api/knowledge/**)
       |
       +---> KnowledgeArticleService & Implementation (RBAC & State Machine)
                |
                +---> Spring Data MongoDB / MongoTemplate
                |        |
                |        v
                |     MongoDB: techconnect_knowledge
                |        - knowledge_articles (documents, search, counters)
                |        - knowledge_article_history (audit revisions)
                |
                +---> UserRepository (PostgreSQL user lookup for author & permissions)
```
- **Polyglot Persistence**: Clean separation of concerns where PostgreSQL handles transactional ACID ITSM state and MongoDB handles flexible, document-oriented troubleshooting articles.
- **Pre-computed RAG Structure**: Articles synthesize clean `normalized_text` blocks and record `embedding_status`, preparing seamless ingestion for future Phase 11 vector search without refactoring document schemas.
- **No Direct Frontend-to-Mongo Access**: The React client communicates strictly via authenticated Spring Boot APIs. MongoDB connection strings and internal collections are never exposed to the browser.

## 6. AI Knowledge Ingestion, Embeddings & Vector Search Architecture (Phase 11)

```
React Client (Knowledge Base Portal)
       |
       | REST + Bearer JWT (Semantic Search / Ingestion Run / Reindex)
       v
Spring Boot KnowledgeArticleController (/api/knowledge/semantic-search, /ingestion/run, /articles/{id}/reindex)
       |
       +---> Spring Security / RBAC Gate
       |        - Employee: PUBLISHED articles only
       |        - Engineer: PUBLISHED + own authored articles
       |        - Manager / Admin: Unrestricted access
       |
       +---> AiKnowledgeVectorClient (Spring RestClient)
       |        |
       |        | HTTP POST /api/v1/knowledge-vector/*
       |        v
       +---> Python AI Service (app/embeddings & app/vector)
                |
                +---> Normalizer (Deterministic: TITLE, SUMMARY, PROBLEM, CAUSE, RESOLUTION, CATEGORY, TAGS)
                +---> Chunker (Section-aware, sentence-boundary, 500 chars max, 80 chars overlap)
                +---> Embedding Generator (FastEmbed/ONNX Runtime: all-MiniLM-L6-v2, 384 dimensions)
                +---> Vector Repository (PostgreSQL + pgvector / HNSW Cosine Distance Index)
                |        |
                |        v
                |     PostgreSQL Table: knowledge_embedding_chunks
                |        - article_id, chunk_id, chunk_index, article_version, section,
                |          chunk_text, category, tags, embedding (vector(384)), status
                |
                +---> MongoDB Sync (MongoDB remains authoritative document source)
                         - Updates embeddingStatus: PENDING -> PROCESSING -> COMPLETED / FAILED
                         - Invalidates old chunks upon article version increments or edits
```

### 6.1 Polyglot Architectural Principles
1. **Source of Truth vs Retrieval Store**:
   - **MongoDB (`knowledge_articles`)**: Retains complete, authoritative knowledge article documents, revision counters, and engagement metrics.
   - **PostgreSQL + pgvector (`knowledge_embedding_chunks`)**: Retains pre-chunked, dense 384-dimensional vector representations strictly optimized for nearest-neighbor similarity search.
   - **Loose Coupling**: Articles and chunks are linked via the string identifier `articleId`. There are no cross-database foreign keys.
2. **Version Consistency & Invalidation**:
   - When an article is edited or updated, its `version` counter increments and `embeddingStatus` resets to `PENDING`.
   - Reindexing removes stale chunk vectors before ingesting new representations, ensuring outdated content is never returned by vector retrieval.
3. **Graceful Degradation**:
   - If the Python vector service or pgvector database is unreachable, semantic search returns a graceful status payload (`available: false`), and keyword search remains 100% operational without application-wide outages.

## 7. RAG-Based AI Support Copilot Architecture (Phase 12)

```
React Client (AI Support Copilot Portal / Ticket Details "Ask AI")
       |
       | REST + Bearer JWT (POST /api/ai/copilot/answer)
       v
Spring Boot Backend (/api/ai/copilot/answer)
       |
       +---> Spring Security Authentication (isAuthenticated())
       +---> IDOR Ticket Ownership Validation (assertCanViewTicket)
       +---> Knowledge Base RBAC Scoping (allowedStatuses, allowedArticleIds)
       +---> AiSupportCopilotClient (Spring RestClient with strict 10s timeout)
                |
                | HTTP POST /api/v1/rag/answer
                v
Python AI Service (app/rag/)
       |
       +---> Stage 1: RETRIEVAL (app/rag/retriever.py)
       |        - Embeds query with all-MiniLM-L6-v2 (384 dimensions)
       |        - Cosine similarity search against pgvector knowledge_embedding_chunks
       |        - Filters chunks by minSimilarity (0.30) and RBAC allowedStatuses
       |
       +---> Stage 2: CONTEXT CONSTRUCTION (app/rag/context_builder.py)
       |        - Formats identifiable [SOURCE N] provenance blocks
       |        - Injects advisory ticket context (Title, Priority, Category, Description)
       |        - Enforces max context budget cap (4,000 characters)
       |
       +---> Stage 3: GENERATION (app/rag/service.py & app/rag/llm_provider.py)
       |        - Pre-generation harmful request security filter
       |        - Hardened system prompt (grounding only, prompt injection defense)
       |        - Pluggable LlmProvider (OpenAI-compatible / local deterministic synthesizer)
       |
       +---> Stage 4: CITATION & RESPONSE (app/rag/service.py)
                - Packages grounded answer with citations and retrieval quality metrics
                - Returns to Spring Boot -> React Client
```

### 7.1 Enterprise Copilot Principles
1. **Knowledge Base as Source of Truth**: The LLM is an extractive synthesizer, never an authoritative knowledge source.
2. **Zero Direct LLM Exposure**: The browser never communicates with Python or LLM vendors; all traffic traverses Spring Boot for JWT and RBAC enforcement.
3. **No Autonomous Actions**: Copilot is strictly advisory and cannot modify tickets, execute commands, or adjust SLAs.
4. **Anti-Hallucination Quality Gate**: If semantic retrieval yields no chunks above the similarity threshold, the LLM is never called, and a safe refusal message is returned immediately.

---

## 8. Phase 13 — AI Engineer Resolution Assistant

Phase 13 introduces an engineer-facing resolution assistant that empowers support engineers, managers, and administrators to synthesize grounded, actionable resolution proposals for active IT tickets.

### 8.1 Tri-Fold Grounding Architecture
The resolution assistant combines three data sources:
1. **Active Ticket Profile**: Current ticket ID, title, problem description, category, and priority.
2. **Authoritative Knowledge Base Articles**: Retrieved from PostgreSQL `knowledge_embedding_chunks` using `sentence-transformers/all-MiniLM-L6-v2` dense vector semantic search.
3. **Similar Resolved Historical Tickets**: Historical tickets with status `RESOLVED` or `CLOSED` containing non-empty `resolutionDescription`, ranked by pairwise cosine similarity against the active ticket's embedding.

### 8.2 Guardrails & Safety Invariants
- **Non-Autonomous Invariant**: The assistant produces advisory text and discrete action steps. It **never** autonomously resolves tickets, closes tickets, modifies status, or adjusts SLAs.
- **Copy to Resolution**: The frontend button only pre-fills the resolution textarea in the status transition modal. The engineer must manually inspect and submit the resolution.
- **RBAC Gate**: Endpoint `POST /api/ai/tickets/{ticketId}/resolution-suggestion` strictly requires `ROLE_ENGINEER`, `ROLE_MANAGER`, or `ROLE_ADMIN`. Standard `ROLE_EMPLOYEE` requests are rejected with HTTP 403 Forbidden.
- **IDOR Protection**: `assertCanViewTicket` verifies ticket visibility before triggering AI synthesis.

---

## 9. Phase 14 — Production Engineering & Deployment Architecture

Phase 14 transitions TechConnect into a hardened, production-ready multi-container architecture.

### 9.1 Containerization Topology
- **Nginx Frontend Proxy**: Serves Vite-built static assets and reverse-proxies `/api/` traffic to the backend, enforcing SPA fallback routing and strict HTTP security headers (`DENY`, `nosniff`, `strict-origin-when-cross-origin`).
- **Spring Boot Backend Container**: Multi-stage Temurin 21 JRE container running as non-root user `spring` (UID 1001), tuned with container-aware JVM flags (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`).
- **FastAPI AI Microservice Container**: Python 3.12 slim container running as non-root user `appuser` (UID 1000) with pre-cached embedding weights.
- **PostgreSQL 16 with pgvector**: Relational transaction store and vector embedding store with persistent named volume `postgres_data`.
- **MongoDB 7.0**: Knowledge article document store with persistent named volume `mongo_data`.
- **Isolated Internal Network**: Containers communicate over bridge network `techconnect_internal`, preventing unauthorized external access to database or AI ports.

### 9.2 Observability & Health Probes
- **Spring Boot Actuator**: Health endpoint `/actuator/health` exposes liveness and readiness states with `management.endpoint.health.show-details=never` to prevent sensitive credential or connection string leakage.
- **Docker Compose Dependencies**: Utilizes `condition: service_healthy` across backend, databases, and AI services to ensure orderly, dependency-verified startup.
- **Structured Logging**: Clean console pattern with timestamps, thread identification, log levels, and automatic redaction of secrets, passwords, and tokens.

### 9.3 In-Memory Rate Limiting
- **Protection**: Jakarta Servlet Filter (`RateLimitingFilter`) intercepts requests ahead of the security filter.
- **Sliding Window Categories**:
  - AI Operations: 20 requests/minute.
  - Semantic Search: 60 requests/minute.
  - General API: 120 requests/minute.
- **Protocol**: Returns `HTTP 429 Too Many Requests` with `Retry-After: 60` and standard `ErrorResponse` JSON.




