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


