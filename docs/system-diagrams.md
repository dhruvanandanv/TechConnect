# TechConnect System Architecture & Lifecycle Diagrams

This document contains authoritative technical architecture and lifecycle diagrams for **TechConnect — AI-Powered Enterprise IT Service Management Platform**, rendered using Mermaid.

---

## 1. Overall System Architecture

```mermaid
flowchart TB
    subgraph ClientTier ["Presentation Layer (Client Tier)"]
        Browser["Web Browser (React 19 / Vite SPA)"]
    end

    subgraph IngressTier ["Edge & Ingress Tier"]
        Nginx["Nginx Reverse Proxy & Static Host (Port: 5173 / 80)"]
    end

    subgraph ServiceTier ["Core Business & API Layer"]
        SpringBoot["Spring Boot 3.3.x Backend (Port: 8080)<br/>- Spring Security 6 & JWT<br/>- Sliding Window Rate Limiter<br/>- Actuator Health Probes<br/>- SLA Automation Engine<br/>- HikariCP Connection Pool"]
    end

    subgraph AiTier ["AI & NLP Microservice Tier"]
        FastAPI["Python 3.12 FastAPI Service (Port: 8000)<br/>- Ticket Triage Classifier<br/>- SentenceTransformers (all-MiniLM-L6-v2)<br/>- Grounded RAG Copilot<br/>- Engineer Resolution Assistant"]
    end

    subgraph PersistenceTier ["Polyglot Persistence Layer"]
        Postgres[("PostgreSQL 16 Relational DB<br/>- Users, Roles, Teams, Depts<br/>- Tickets, Status History, Comments<br/>- SLA Rules & Compliance<br/>- pgvector (384-dim HNSW embeddings)")]
        Mongo[("MongoDB 7.0 Document DB<br/>- Knowledge Base Articles<br/>- Markdown SOPs & Versions<br/>- View & Feedback Counters")]
    end

    Browser -->|"HTTP / Static Assets"| Nginx
    Browser -->|"HTTPS / REST / JWT Bearer"| Nginx
    Nginx -->|"/api/ Reverse Proxy"| SpringBoot
    SpringBoot -->|"JPA / Hibernate / JDBC"| Postgres
    SpringBoot -->|"Spring Data MongoDB Wire Protocol"| Mongo
    SpringBoot -->|"Internal HTTP REST Client (JSON)"| FastAPI
    FastAPI -->|"Direct pgvector Cosine Query"| Postgres
```

---

## 2. Frontend to Backend Request Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as Client / Browser
    participant Nginx as Nginx Proxy (:5173)
    participant RateLimit as RateLimitingFilter
    participant JwtFilter as JwtAuthenticationFilter
    participant Controller as TicketController
    participant Service as TicketService
    participant Repo as TicketRepository
    participant DB as PostgreSQL 16

    User->>Nginx: POST /api/tickets (Authorization: Bearer <JWT>)
    Nginx->>RateLimit: Forward Request
    Note over RateLimit: Sliding Window Rate Limit Check<br/>(General: 120 rpm, AI: 20 rpm)
    alt Rate Limit Exceeded
        RateLimit-->>User: HTTP 429 Too Many Requests (Retry-After: 60)
    else Limit OK
        RateLimit->>JwtFilter: Pass downstream
    end
    Note over JwtFilter: Verify HMAC-SHA256 signature<br/>Extract Subject & Granted Roles
    alt Invalid/Expired Token
        JwtFilter-->>User: HTTP 401 Unauthorized
    else Token Valid
        JwtFilter->>Controller: Authenticated Principal attached
    end
    Controller->>Service: createTicket(request, userEmail)
    Service->>Service: Validate DTO & evaluate SLA Policy
    Service->>Repo: save(ticketEntity)
    Repo->>DB: INSERT INTO tickets (...)
    DB-->>Repo: Saved Ticket Entity with ID
    Repo-->>Service: Ticket
    Service-->>Controller: TicketResponse DTO
    Controller-->>User: HTTP 201 Created (Ticket JSON)
```

---

## 3. Database Architecture (Polyglot Persistence)

```mermaid
erDiagram
    USERS ||--o{ TICKETS : "creates"
    USERS ||--o{ TICKETS : "assigned as engineer"
    ROLES ||--o{ USERS : "defines permissions"
    DEPARTMENTS ||--o{ USERS : "belongs to"
    TEAMS ||--o{ USERS : "member of"
    TEAMS ||--o{ TICKETS : "assigned to"
    SLA_RULES ||--o{ TICKETS : "governs deadlines"
    TICKETS ||--o{ TICKET_STATUS_HISTORY : "tracks transitions"
    TICKETS ||--o{ TICKET_COMMENTS : "contains"
    TICKETS ||--o{ AUDIT_LOGS : "logs actions"

    POSTGRESQL_PGVECTOR ||--o{ KNOWLEDGE_EMBEDDING_CHUNKS : "stores dense vectors"
    KNOWLEDGE_EMBEDDING_CHUNKS {
        bigint id PK
        string article_id FK
        text chunk_text
        int chunk_index
        string status
        vector_384 embedding
    }

    MONGODB_KNOWLEDGE_BASE ||--o{ KNOWLEDGE_ARTICLES : "polymorphic documents"
    KNOWLEDGE_ARTICLES {
        string _id PK
        string title
        string slug UK
        string category
        string status
        text markdown_content
        bigint view_count
        bigint helpful_count
        set feedback_user_ids
    }
```

---

## 4. Ticket Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> OPEN : Ticket Created by Employee

    OPEN --> ASSIGNED : Manager assigns to Engineer
    OPEN --> IN_PROGRESS : Engineer self-assigns / starts work
    OPEN --> ESCALATED : Manager escalates ticket

    ASSIGNED --> IN_PROGRESS : Engineer begins investigation
    ASSIGNED --> ESCALATED : Escalation triggered

    IN_PROGRESS --> WAITING_FOR_USER : Information requested from employee<br/>(SLA Clock Pauses)
    WAITING_FOR_USER --> IN_PROGRESS : Employee responds with info<br/>(SLA Clock Resumes)

    IN_PROGRESS --> RESOLVED : Engineer inputs resolution & submits
    IN_PROGRESS --> ESCALATED : Technical bottleneck escalation

    ESCALATED --> IN_PROGRESS : Senior Engineer assigned
    ESCALATED --> RESOLVED : High-priority resolution provided

    RESOLVED --> CLOSED : Employee or Manager confirms fix
    RESOLVED --> IN_PROGRESS : Fix verified ineffective (Reopened)

    CLOSED --> [*] : Lifecycle Completed
```

---

## 5. SLA Lifecycle & Governance

```mermaid
flowchart TD
    Start["Ticket Created (e.g. Critical, High, Medium, Low)"] --> AssignSLA["Determine SLA Policy & Deadlines<br/>- Response Deadline (e.g. 30m, 1h)<br/>- Resolution Deadline (e.g. 2h, 4h, 8h, 24h)"]
    AssignSLA --> WaitAction{"First Staff Action?"}

    WaitAction -->|Assigned / Comment / Status Change| RecordResp["Record 'respondedAt' Milestone<br/>Compare with Response Deadline"]
    RecordResp --> StatusCheck{"Current Ticket Status?"}

    StatusCheck -->|"WAITING_FOR_USER"| PauseSLA["Pause SLA Clock<br/>Record 'slaPausedAt'"]
    PauseSLA --> ResumeWait{"User Responds?"}
    ResumeWait -->|"Leaves WAITING_FOR_USER"| ResumeSLA["Resume SLA Clock<br/>Adjust Deadlines by Paused Duration"]
    ResumeSLA --> ActiveMonitor["Active Real-time SLA Monitoring"]

    StatusCheck -->|"IN_PROGRESS / ASSIGNED"| ActiveMonitor

    ActiveMonitor --> CheckTime{"Time Remaining vs Deadline"}
    CheckTime -->|"> 20% Time Left"| OnTrack["SLA Status: ON_TRACK (Green)"]
    CheckTime -->|"<= 20% Time Left"| AtRisk["SLA Status: AT_RISK (Amber Warning)"]
    CheckTime -->|"Deadline Passed & Unresolved"| Breached["SLA Status: BREACHED (Red Alert)<br/>Logged for Managerial Review"]

    OnTrack --> ResolveCheck{"Ticket Resolved?"}
    AtRisk --> ResolveCheck
    Breached --> ResolveCheck

    ResolveCheck -->|"Status -> RESOLVED"| FinalEvaluation["Record 'resolvedAt'<br/>Compare with Final SLA Deadline<br/>Mark Resolution SLA Met or Breached"]
```

---

## 6. AI Ticket Intelligence Microservice (Triage & Classification)

```mermaid
flowchart LR
    Input["Incoming Ticket<br/>Title & Description"] --> Preprocess["Text Normalization<br/>Lowercasing, Punctuation & Stopwords"]
    Preprocess --> TFIDF["TF-IDF Vectorization<br/>(Feature Extraction)"]
    TFIDF --> Classifier["Logistic Regression Model<br/>(Category Prediction)"]

    Input --> Rules["Deterministic Priority Engine<br/>(Keyword Urgency Detection)"]
    Classifier --> Routing["Support Team Matrix<br/>(Team Suggestion)"]
    Input --> Summarizer["Deterministic ITSM Summarizer<br/>(One-Sentence Summary)"]

    Classifier --> Aggregate["Aggregated AI Prediction Response"]
    Rules --> Aggregate
    Routing --> Aggregate
    Summarizer --> Aggregate
```

---

## 7. Knowledge Ingestion & Semantic Search Pipeline

```mermaid
flowchart TD
    Article["Knowledge Base Article (Markdown)"] --> Clean["Text Normalization<br/>Remove codeblocks, HTML tags, excess whitespace"]
    Clean --> Chunk["Section-Aware Chunker<br/>(Heading preservation, max 300 words, 50-word overlap)"]
    Chunk --> Embed["SentenceTransformers Model<br/>(sentence-transformers/all-MiniLM-L6-v2)"]
    Embed --> Vector["384-Dimensional Dense Embeddings"]
    Vector --> Store[("PostgreSQL + pgvector<br/>HNSW Indexing (m=16, ef_construction=64)")]

    Query["User Search Inquiry"] --> QueryEmbed["Generate 384-dim Query Vector"]
    QueryEmbed --> CosineSearch["Cosine Distance Search (<=>)<br/>Filter: Status = PUBLISHED<br/>Threshold: minSimilarity >= 0.30"]
    Store --> CosineSearch
    CosineSearch --> Results["Ranked Grounded Chunks with Citations"]
```

---

## 8. RAG Pipeline (AI Support Copilot)

```mermaid
flowchart TD
    UserQuery["User Inquires in Copilot UI"] --> HarmCheck{"Harmful / Exploit Pattern Gate?<br/>(e.g. credential bypass, token dump)"}
    HarmCheck -->|"Matches Malicious Pattern"| Refusal["Safe Enterprise Refusal:<br/>'Cannot provide instructions for bypassing security controls'"]
    HarmCheck -->|"Safe Query"| Retrieve["Dense Vector Retrieval (pgvector)<br/>Extract top candidate SOP chunks"]

    Retrieve --> ThresholdCheck{"Any chunk >= 0.30 Similarity?"}
    ThresholdCheck -->|"No chunks qualify"| SafeFallback["Refusal Refusal Gate:<br/>'No verified knowledge base articles found for this topic'"]
    ThresholdCheck -->|"Chunks qualify"| Context["Context Builder<br/>- Format [SOURCE N] provenance tags<br/>- Enforce 4,000 char budget cap"]

    Context --> Prompt["Hardened System Prompt<br/>(Grounding Invariant: synthesize ONLY from provided sources)"]
    Prompt --> LLM["LlmProvider (Extractive or External LLM)<br/>10-Second Strict Timeout"]

    LLM --> Citation["Citation Packaging<br/>Attach Article IDs, Titles, and Similarity Scores"]
    Citation --> Output["Deliver Grounded Response to User with Sources"]
```

---

## 9. AI Engineer Resolution Assistant Architecture

```mermaid
flowchart TD
    Engineer["Support Engineer viewing Active Ticket"] --> Request["Click 'Suggest Resolution'<br/>POST /api/ai/tickets/{id}/resolution-suggestion"]
    Request --> AuthGate{"Role Authorization<br/>& IDOR Check"}
    AuthGate -->|"Role == EMPLOYEE"| Deny["HTTP 403 Forbidden"]
    AuthGate -->|"Staff (Eng/Mgr/Admin)"| FetchContext["Tri-Fold Context Retrieval"]

    subgraph TriFoldContext ["Tri-Fold Grounding Engine"]
        T1["1. Active Ticket Profile<br/>(Title, Description, Category, Priority)"]
        T2["2. Authoritative Knowledge Base SOPs<br/>(pgvector semantic search)"]
        T3["3. Similar Historical Resolved Tickets<br/>(Cosine matching against past resolved incidents)"]
    end

    FetchContext --> TriFoldContext
    TriFoldContext --> Budget["Resolution Context Builder<br/>(Provenance block, 6,000 char budget limit)"]
    Budget --> Synth["LLM Resolution Synthesizer<br/>Produces: Proposed Resolution + Action Steps"]
    Synth --> UI["Display Resolution Proposal in UI<br/>(Citations & Historical References)"]
    UI --> Staging["Click 'Copy to Resolution'<br/>Pre-fills text in status transition modal"]
    Staging --> Review["Engineer manually inspects, edits, and submits resolution.<br/>Zero autonomous database mutation!"]
```

---

## 10. Multi-Container Docker Deployment Architecture

```mermaid
flowchart TB
    subgraph Host ["Production Host Environment"]
        subgraph Ingress ["Public Ingress"]
            Port5173["Host Port 5173 (External)"]
            Port8080["Host Port 8080 (Localhost bound)"]
        end

        subgraph Network ["Isolated Internal Bridge Network (techconnect_internal)"]
            Frontend["techconnect-frontend (Nginx 1.27 Alpine)<br/>User: nginx (unprivileged)"]
            Backend["techconnect-backend (Temurin 21 JRE)<br/>User: spring (UID 1001)"]
            AiService["techconnect-ai-service (Python 3.12 FastAPI)<br/>User: appuser (UID 1000)"]
            PostgresContainer["techconnect-postgres (PostgreSQL 16 + pgvector)"]
            MongoContainer["techconnect-mongodb (MongoDB 7.0)"]
        end

        subgraph Storage ["Persistent Docker Named Volumes"]
            VolPG[("postgres_data<br/>/var/lib/postgresql/data")]
            VolMongo[("mongo_data<br/>/data/db")]
            VolModel[("ai_model_cache<br/>/app/data/models_cache")]
        end
    end

    Port5173 --> Frontend
    Port8080 --> Backend
    Frontend -->|"Reverse Proxy /api/"| Backend
    Backend -->|"Healthcheck: service_healthy"| PostgresContainer
    Backend -->|"Healthcheck: service_healthy"| MongoContainer
    Backend -->|"Healthcheck: service_healthy"| AiService

    PostgresContainer --- VolPG
    MongoContainer --- VolMongo
    AiService --- VolModel
```
