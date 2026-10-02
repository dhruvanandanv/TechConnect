# TechConnect Vector Search & Embedding Pipeline (Phase 11)

## 1. Overview & Objectives

Phase 11 extends the TechConnect IT Service Management (ITSM) platform from simple keyword-based article retrieval into a semantic knowledge retrieval system.

While keyword search (Phase 10) matches exact tokens, substrings, and regex patterns across fields, semantic vector search understands the intent, meaning, and contextual relationships within support queries (e.g. mapping *"I cannot connect to the company VPN when working remotely from home"* to an article titled *"Configuring Corporate Cisco AnyConnect VPN"* without requiring identical wording).

> **Crucial RAG Boundary Notice**: Phase 11 is strictly a **Retrieval Pipeline**. It indexes, chunks, embeds, and retrieves nearest-neighbor knowledge chunks with high-fidelity similarity scores. Generative conversational response synthesis via LLMs belongs exclusively to **Phase 12 (AI Support Copilot & RAG)**.

---

## 2. Keyword vs. Semantic Search

| Dimension | Keyword Search (Phase 10) | Semantic Vector Search (Phase 11) |
| :--- | :--- | :--- |
| **API Endpoint** | `GET /api/knowledge/search?q=` | `POST /api/knowledge/semantic-search` or `GET /api/knowledge/semantic-search?q=` |
| **Query Engine** | MongoDB Multi-field Regex & Text Index | Dense Vector Cosine Similarity (PostgreSQL + pgvector / FastEmbed) |
| **Matching Style** | Lexical token/exact match | Semantic intent & conceptual proximity |
| **Response Type** | Whole `KnowledgeArticleSummaryResponse` | Section-level `SemanticSearchResultChunk` (e.g., `RESOLUTION`, `PROBLEM`, `CAUSE`) |
| **Ranking Metric** | View count & updatedAt recency | Cosine similarity score ($0.00$ to $1.00$) |
| **Synonym Handling**| Requires exact string match or tag overlap | Understands synonyms (*"WiFi disconnected"* $\approx$ *"Wireless link dropped"*) |
| **Failure Mode** | Zero results for rephrased queries | Gracefully degrades; falls back to Keyword search if AI service is offline |

---

## 3. Polyglot Architecture & Data Ownership

TechConnect adheres to a strict polyglot persistence architecture:

```
                      React Frontend (Vite)
                               |
                               | REST + JWT Bearer
                               v
                   Spring Boot Application (Port 8080)
                     |                             |
      (Document Source of Truth)        (AI Orchestration / Fallback)
                     v                             v
           MongoDB (Port 27017)           Python AI Service (Port 8000)
             - knowledge_articles                  |
             - knowledge_article_history           v
                                          PostgreSQL + pgvector (Port 5432)
                                            - knowledge_embedding_chunks
```

- **MongoDB (`techconnect_knowledge`)**: The authoritative system of record for knowledge article documents, life-cycle metadata (`status`, `version`, `author_id`, `helpful_count`), and full audit history.
- **PostgreSQL + pgvector (`knowledge_embedding_chunks`)**: The high-performance semantic retrieval store holding 384-dimensional dense vectors and chunk text.
- **Cross-Database Relationship**: MongoDB articles and PostgreSQL vector chunks are linked via the string identifier `article_id`. No relational foreign keys exist across database engines; referential integrity is preserved in the application/service layer.

---

## 4. Embedding Model & Vector Dimensions

- **Model**: `sentence-transformers/all-MiniLM-L6-v2` (loaded via ONNX runtime through FastEmbed for ultra-low latency, zero PyTorch path dependency issues on Windows, and thread-safe singleton caching).
- **Vector Dimension**: Programmatically verified to be **384 dimensions** at application startup (`get_actual_dimensions() == 384`).
- **Distance Metric**: **Cosine Similarity** ($\text{Cosine Distance} = 1 - \cos(\mathbf{u}, \mathbf{v})$).
  $$\cos(\theta) = \frac{\mathbf{u} \cdot \mathbf{v}}{\|\mathbf{u}\|_2 \|\mathbf{v}\|_2}$$
  - *Why Cosine Similarity?* MiniLM outputs vectors normalized on a unit hypersphere. Cosine distance isolates semantic orientation from text length discrepancies.
- **Model Version**: `1.0.0`
- **Model Loading**: Thread-safe singleton (`get_embedding_model()`) loaded once at startup and reused for all subsequent requests.

---

## 5. Normalization & Chunking Strategy

### 5.1 Deterministic Normalization
Articles are normalized into canonical structures before chunking:
```
TITLE:
...

SUMMARY:
...

PROBLEM:
...

CAUSE:
...

RESOLUTION:
...

CATEGORY:
...

TAGS:
...
```
- Normalizes CRLF and LF to standard LF (`\n`).
- Strips trailing spaces and collapses consecutive blank lines.
- Preserves technical syntax, commands (e.g. `sudo systemctl restart openvpn`), error codes, and code blocks.
- Sorts and deduplicates tags deterministically.

### 5.2 Section-Aware Intelligent Chunking
Rather than splitting arbitrarily at fixed character counts, chunking respects ITSM domain sections:
1. **PROBLEM Chunk**: Captures symptoms, errors, and failure modes.
2. **CAUSE Chunk**: Captures root cause analysis and diagnosis.
3. **RESOLUTION Chunk**: Captures step-by-step resolution procedures.
4. **SUMMARY Chunk**: Captures executive synopsis.

- **Maximum Chunk Size**: 500 characters (~80–100 tokens), cleanly fitting within MiniLM's 256-token optimal context window without semantic dilution.
- **Chunk Overlap**: 80 characters (~15 tokens) with sentence and line boundary detection (`\n\n`, `\n`, `. `, `? `, `! `, `; `) to preserve boundary context.
- **Chunk Schema**:
  ```json
  {
    "chunkId": "art-101-resolution-2",
    "articleId": "art-101",
    "chunkIndex": 2,
    "section": "RESOLUTION",
    "text": "[RESOLUTION] Corporate VPN\nDownload latest Root CA bundle...",
    "category": "NETWORK",
    "tags": ["vpn", "tls"],
    "articleVersion": 2
  }
  ```

---

## 6. Vector Database Schema & Indexing

Table: `knowledge_embedding_chunks`
```sql
CREATE TABLE IF NOT EXISTS knowledge_embedding_chunks (
    id SERIAL PRIMARY KEY,
    article_id VARCHAR(100) NOT NULL,
    chunk_id VARCHAR(150) NOT NULL UNIQUE,
    chunk_index INT NOT NULL,
    article_version INT NOT NULL,
    section VARCHAR(50) NOT NULL,
    chunk_text TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    tags TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PUBLISHED',
    embedding vector(384),
    embedding_model VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Active chunk lookup index
CREATE INDEX IF NOT EXISTS idx_chunks_article_active 
ON knowledge_embedding_chunks (article_id, is_active);

-- Status filter index (RBAC)
CREATE INDEX IF NOT EXISTS idx_chunks_status 
ON knowledge_embedding_chunks (status);

-- HNSW Vector Index for sub-millisecond approximate nearest neighbor retrieval
CREATE INDEX IF NOT EXISTS idx_chunks_vector_cosine 
ON knowledge_embedding_chunks USING hnsw (embedding vector_cosine_ops);
```

### Dual-Backend Resilience:
If PostgreSQL is offline or running under in-memory H2 during integration testing, the Python AI service seamlessly activates an embedded vector store with exact NumPy cosine calculations, ensuring 100% test suite reliability without external dependencies.

---

## 7. Ingestion Worker Lifecycle & Observable Trigger

### Lifecycle States:
```
PENDING  ──>  PROCESSING  ──>  COMPLETED
     │
     └──>  (On Error)  ──>  FAILED
```

- When an article is created or updated, MongoDB stores `embedding_status = "PENDING"`.
- Controlled Ingestion Endpoint: `POST /api/knowledge/ingestion/run` (Staff only: `ENGINEER`, `MANAGER`, `ADMIN`).
- Atomic transition to `PROCESSING` prevents race conditions.
- Batch embeds chunks, invalidates stale version chunks, and updates MongoDB to `COMPLETED` with `embedding_model`, `embedding_version`, and `embedding_updated_at`.
- If an error occurs, status transitions to `FAILED` with a sanitized message in `embedding_error`.

**Observable Response:**
```json
{
  "articlesDiscovered": 5,
  "articlesProcessed": 5,
  "chunksCreated": 18,
  "chunksEmbedded": 18,
  "failures": 0,
  "message": "Ingestion batch completed successfully"
}
```

---

## 8. Version Consistency & Reindexing

- **Version Increment**: When an article's meaningful content is modified in `PUT /api/knowledge/articles/{id}`, its version increments to $N+1$, and its embedding status reverts to `PENDING`.
- **Stale Vector Invalidation**: In the vector repository, chunks with `article_version != current_version` are deactivated (`is_active = false`) or deleted upon reindexing. Outdated guidance is never retrieved.
- **Manual Reindex Endpoint**: `POST /api/knowledge/articles/{id}/reindex` allows authorized staff to re-embed individual articles on demand.

---

## 9. Security, RBAC & Failure Handling

1. **Authorization at Application Layer**: The vector store is not a security perimeter. Access control is enforced prior to and during retrieval:
   - **ROLE_EMPLOYEE**: Strictly restricted to chunks from `PUBLISHED` articles (`allowedStatuses = ['PUBLISHED']`). Drafts and archived articles are never returned.
   - **ROLE_ENGINEER**: Accesses `PUBLISHED` articles plus their own authored drafts.
   - **ROLE_MANAGER / ROLE_ADMIN**: Full access across all knowledge statuses.
2. **Defensive Validation**:
   - `topK`: default 5, min 1, hard max 20 (rejects abusive queries).
   - `query`: max 1000 characters, non-empty.
   - `minSimilarity`: bounded between 0.0 and 1.0.
3. **Graceful Degradation**:
   If the AI vector service or PostgreSQL is unreachable:
   - Keyword search (`/api/knowledge/search`) continues operating without interruption.
   - Semantic search returns `{ "available": false, "message": "Semantic search is temporarily unavailable." }`.
   - Zero stack traces or internal secrets are leaked to the client.

---

## 10. Phase 12 RAG Preparation Grounding

The semantic search payload contains structured grounding metadata ready for Phase 12 LLM synthesis:
```json
{
  "searchType": "SEMANTIC",
  "query": "VPN connection dropped after Windows update",
  "totalHits": 1,
  "results": [
    {
      "articleId": "65b...",
      "chunkId": "65b...-resolution-0",
      "articleVersion": 2,
      "title": "Corporate VPN Troubleshooting",
      "slug": "corporate-vpn-troubleshooting",
      "section": "RESOLUTION",
      "content": "[RESOLUTION] Corporate VPN Troubleshooting\nRun ipconfig /flushdns and restart the Cisco AnyConnect service...",
      "similarity": 0.8874,
      "category": "NETWORK",
      "tags": ["vpn", "network", "remote"]
    }
  ]
}
```
This payload will serve as the prompt context for Phase 12 RAG pipelines.
