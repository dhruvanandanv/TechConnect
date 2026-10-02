# Knowledge Base Management System (Phase 10)

## 1. Purpose & Overview
TechConnect Phase 10 introduces an enterprise-grade Knowledge Base Management System. Designed specifically as the foundational document corpus for future AI/RAG capabilities (Phases 11+), it provides structured troubleshooting documentation, self-service resolutions, and standard operating procedures (SOPs) for enterprise IT support.

### Scope Boundaries:
- **Phase 10 Scope**: Polyglot persistence with MongoDB, structured troubleshooting data models, role-based lifecycle workflows (Draft -> Published -> Archived), atomic engagement metrics (views, helpful/not helpful feedback), version tracking, audit history, keyword search, and React user interface.
- **Explicit Non-Goals (Reserved for Future Phases)**: No vector embeddings, no vector databases (e.g. pgvector, Milvus, Pinecone), no semantic similarity search, no LLM integrations, no AI Copilot, no Power BI, and no WebSockets.

---

## 2. Polyglot Architecture & Cross-Database Design

```
+-----------------------------------------------------------------------------------+
|                                 React Frontend                                    |
|              (Knowledge Base, Article Viewer, Role-Guarded Editor)                |
+-----------------------------------------------------------------------------------+
                                         |
                                         | Authenticated REST (JWT Bearer)
                                         v
+-----------------------------------------------------------------------------------+
|                            Spring Boot REST API Layer                             |
|          (Security Filter, RBAC, Validation, Business Services, Controllers)      |
+-----------------------------------------------------------------------------------+
               |                                                   |
               | JPA / Hibernate                                   | Spring Data Mongo
               v                                                   v
+-----------------------------+                     +-------------------------------+
|     PostgreSQL Database     |                     |       MongoDB Database        |
|     (techconnect_db)        |                     |    (techconnect_knowledge)    |
|                             |                     |                               |
| - Users & Roles             |                     | - knowledge_articles          |
| - Departments & Teams       |                     | - knowledge_article_history   |
| - Tickets & Assignments     |                     |                               |
| - SLA Policies & Logs       |                     | (Future RAG Normalized Source)|
+-----------------------------+                     +-------------------------------+
```

### Why MongoDB for Knowledge Base Documents?
1. **Document-Oriented Flexibility**: Knowledge articles are semi-structured technical guides containing variable fields (summary, problem statements, diagnostic causes, multi-step resolution procedures, tags, and formatting). MongoDB documents naturally model this hierarchical data without schema-migration friction.
2. **Future AI/RAG Readiness**: Future vector search and RAG ingestion pipelines require normalized text chunks, embedding metadata, source hashes, and chunking configurations that evolve rapidly. MongoDB documents seamlessly store flexible metadata alongside the raw content.
3. **Transactional Separation**: PostgreSQL remains the ACID-compliant single source of truth for core enterprise transactional data (Users, Tickets, SLA tracking, Assignments, and Comments). Storing high-volume, read-heavy, polymorphic documents in MongoDB isolates document retrieval from transactional relational workloads.

### Cross-Database Referencing (No Direct Foreign Keys)
- PostgreSQL and MongoDB are intentionally decoupled at the persistence layer.
- `authorId`: Stored as a scalar `Long` in MongoDB referencing `users.id` in PostgreSQL.
- `sourceTicketId`: Stored as a scalar `Long` in MongoDB referencing `tickets.id` in PostgreSQL.
- Spring Boot acts as the federating application layer, resolving relationships and enforcing authorization boundaries without relational joins across database engines.

---

## 3. MongoDB Schema & Data Design

Database: `techconnect_knowledge`

### Collection: `knowledge_articles`
```json
{
  "_id": "674e2b10a4f59e001234abcd",
  "title": "Configuring Corporate VPN via Cisco AnyConnect",
  "slug": "configuring-corporate-vpn-via-cisco-anyconnect",
  "summary": "Step-by-step diagnostic guide to establish split-tunnel VPN connections.",
  "problem": "Users encounter Gateway Timeout 504 when connecting remotely.",
  "cause": "DNS cache corruption or outdated client XML profile.",
  "resolution": "1. Flush DNS using ipconfig /flushdns.\n2. Update AnyConnect profile to v3.4.\n3. Re-authenticate via Azure AD MFA.",
  "content": "### Problem & Symptoms\n\nUsers encounter Gateway Timeout...",
  "category": "VPN",
  "tags": ["vpn", "cisco", "anyconnect", "network"],
  "status": "PUBLISHED",
  "author_id": 2,
  "author_name": "Alex Engineer",
  "author_email": "engineer@techconnect.com",
  "version": 2,
  "view_count": 48,
  "helpful_count": 12,
  "not_helpful_count": 1,
  "feedback_user_ids": [4, 15, 23],
  "source_type": "TICKET",
  "source_ticket_id": 104,
  "created_at": "2026-10-02T10:15:00",
  "updated_at": "2026-10-02T10:45:00",
  "published_at": "2026-10-02T10:20:00",
  "archived_at": null,
  "normalized_text": "Title: Configuring Corporate VPN...\nSummary: Step-by-step...\nResolution: ...",
  "embedding_status": "PENDING"
}
```

### Collection: `knowledge_article_history`
```json
{
  "_id": "674e2c80a4f59e001234abce",
  "article_id": "674e2b10a4f59e001234abcd",
  "action": "PUBLISHED",
  "performed_by_id": 2,
  "performed_by_name": "Alex Engineer",
  "performed_by_email": "engineer@techconnect.com",
  "performed_at": "2026-10-02T10:20:00",
  "version": 1,
  "details": "Article published for employee self-service"
}
```

---

## 4. Article Lifecycle & State Machine

```
              +--------------------------+
              |          DRAFT           | <---------------+
              +--------------------------+                 |
                 |                    ^                    |
        Publish  |                    | Revert to Draft    |
                 v                    |                    |
              +--------------------------+                 | Restore to Draft
              |        PUBLISHED         |                 |
              +--------------------------+                 |
                 |                    |                    |
         Archive |                    | Archive            |
                 v                    v                    |
              +--------------------------------------------+
              |                 ARCHIVED                   |
              +--------------------------------------------+
```

### Transition Validation Rules:
1. `DRAFT -> PUBLISHED`: Transitions article to public catalog. Sets `publishedAt = now()`.
2. `PUBLISHED -> DRAFT`: Allows staff to revise live articles safely without exposing intermediate edits.
3. `PUBLISHED -> ARCHIVED`: Retires obsolete documentation. Sets `archivedAt = now()`. Employees can no longer view the article.
4. `ARCHIVED -> DRAFT`: Restores an archived article to working draft status for overhaul.
5. `ARCHIVED -> PUBLISHED`: Directly restores an archived article to published status.
6. **Invalid Transitions**: Attempting to publish an already published article or revert an already draft article throws `InvalidKnowledgeArticleStateTransitionException` (HTTP 400 Bad Request).

---

## 5. Role-Based Access Control (RBAC) & Ownership

| Action | EMPLOYEE | ENGINEER | MANAGER | ADMIN |
|---|---|---|---|---|
| View Published Articles | Yes | Yes | Yes | Yes |
| Keyword Search Published | Yes | Yes | Yes | Yes |
| Increment View Count | Yes (Atomic) | Yes | Yes | Yes |
| Submit Helpful/Not Helpful | Yes (Unique) | Yes | Yes | Yes |
| View Drafts & Archives | No (403) | Own drafts only | Team / All | All |
| Create Articles | No (403) | Yes (Author) | Yes | Yes |
| Edit Articles | No (403) | Own articles only | Yes | Yes |
| Publish / Archive | No (403) | Own articles only | Yes | Yes |
| View Article History | No (403) | Own articles only | Yes | Yes |

### Ownership Enforcement:
- An Engineer cannot edit, publish, or archive another engineer's article. Attempted tampering throws `KnowledgeArticleAccessDeniedException` (HTTP 403 Forbidden).
- Managers have governance oversight over department/team knowledge assets.
- Administrators possess unrestricted management authority across all collections.

---

## 6. Search, Filtering & Pagination

### Keyword Search (`GET /api/knowledge/search?q=`)
- Multi-field keyword query matching across:
  - `title`
  - `summary`
  - `problem`
  - `cause`
  - `resolution`
  - `tags`
  - `category`
- **Distinction from Future Semantic Search**: Current search is deterministic keyword pattern matching. It does not perform vector cosine similarity or natural language embedding inference.
- Results are ranked by `view_count` and `updated_at`, returning paginated summaries with `searchType: "KEYWORD"`.

### Pagination & Taxonomy
- REST endpoints use standard Spring Data `Pageable` parameters: `?page=0&size=10&sort=updated_at,desc`.
- Category filtering (`?category=VPN`) strictly enforces the existing `TicketCategory` enterprise vocabulary (`HARDWARE`, `SOFTWARE`, `NETWORK`, `SECURITY`, `ACCESS_MANAGEMENT`, `EMAIL`, `VPN`, `OTHER`).
- Tags are automatically normalized (trimmed, converted to lowercase, deduped, and empty entries purged).

---

## 7. Atomic Concurrency & Feedback

1. **View Count Increment**:
   - Implemented via MongoDB atomic `$inc` operator:
     ```java
     Query query = Query.query(Criteria.where("_id").is(id).and("status").is(ArticleStatus.PUBLISHED));
     Update update = new Update().inc("view_count", 1);
     mongoTemplate.updateFirst(query, update, KnowledgeArticle.class);
     ```
   - Eliminates read-modify-write lost updates under high concurrent web traffic.

2. **Helpful / Not Helpful Feedback**:
   - Endpoint: `POST /api/knowledge/articles/{id}/feedback` (`{"helpful": true}`)
   - Atomically updates `helpful_count` or `not_helpful_count` and stores the authenticated user's ID into the `feedback_user_ids` set via `$addToSet`.
   - Prevents duplicate voting by checking set membership.

---

## 8. Versioning & Audit History

- Articles maintain an integer `version` field (starting at 1).
- When a `PUBLISHED` article undergoes meaningful content modifications (title, summary, problem, cause, resolution), the version number increments automatically.
- Every state transition or update generates an immutable entry in the `knowledge_article_history` collection recording:
  - `articleId`
  - `action` (`CREATED`, `UPDATED`, `PUBLISHED`, `ARCHIVED`, `RESTORED`, `REVERTED_TO_DRAFT`)
  - `performedById`, `performedByName`, `performedByEmail`
  - `performedAt`
  - `version`
  - `details`

---

## 9. Ticket Linkage & ITSM Integration

- `sourceTicketId`: Optional reference field linking a knowledge article to a resolved ticket.
- Enables engineers to capture troubleshooting resolutions discovered during real incident management.
- The UI displays a direct navigation link back to the originating ticket (`/tickets/{sourceTicketId}`).

---

## 10. AI Vector Search & Ingestion Integration (Phase 11)

In Phase 11, the structured Knowledge Base documents from MongoDB are transformed into a dense vector search retrieval engine:
- `normalizedText`: Distills title, summary, symptoms, diagnostics, steps, and tags deterministically.
- `embeddingStatus`: Actively tracks the vectorization lifecycle (`PENDING` -> `PROCESSING` -> `COMPLETED` / `FAILED`).
- **Section-Aware Chunking**: Decomposes articles into structured chunks (`PROBLEM`, `CAUSE`, `RESOLUTION`, `GENERAL`) with 500-char limits and 80-char overlap.
- **Dense Vector Embedding**: Embeds chunks using `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions) via ONNX Runtime / FastEmbed.
- **pgvector Retrieval**: Vector chunks are stored in PostgreSQL `knowledge_embedding_chunks` and queried via cosine distance `<=>`.
- **RAG Preparation**: The retrieved chunks provide grounded context for Phase 12 RAG Copilot synthesis.
- Complete documentation: [docs/vector-search.md](vector-search.md).
