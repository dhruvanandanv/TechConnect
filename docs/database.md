# TechConnect Database Design & Architecture

## 1. Polyglot Persistence Strategy (PostgreSQL + MongoDB)

TechConnect implements a **polyglot persistence** architecture, choosing each storage engine based on the data access patterns, consistency requirements, and schema volatility:

| Criteria | PostgreSQL (Relational Core) | MongoDB (Knowledge & AI Store) |
|---|---|---|
| **Primary Domain** | Users, Tickets, Workflows, SLAs, Audit Trail | Knowledge Base articles, RAG embeddings, AI analysis artifacts |
| **Consistency Model** | Strict ACID (Immediate Consistency) | BASE / Tunable Document-level consistency |
| **Schema Nature** | Normalized, fixed relational tables with strict foreign keys | Polymorphic, semi-structured document model |
| **Integrity Checks** | Database-enforced constraints (FK, Unique, Not Null) | Application-level validation for flexible schema evolution |
| **Primary Advantage** | Prevents orphaned records (e.g. ticket without user), enables complex transactional joins, guarantees audit compliance | Accommodates variable article formats, multimedia blocks, dynamic tags, and vector search metadata |

---

## 2. PostgreSQL Relational Schema Specification

### 2.1 `roles`
Stores authorization roles.
- `id`: BIGSERIAL PRIMARY KEY
- `name`: VARCHAR(30) UNIQUE NOT NULL (`ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`)
- `description`: VARCHAR(255)

### 2.2 `departments`
Enterprise organizational divisions.
- `id`: BIGSERIAL PRIMARY KEY
- `name`: VARCHAR(100) UNIQUE NOT NULL
- `code`: VARCHAR(20) UNIQUE NOT NULL
- `description`: VARCHAR(255)
- `created_at`: TIMESTAMP NOT NULL
- `updated_at`: TIMESTAMP NOT NULL

### 2.3 `teams`
Functional support groups belonging to a department.
- `id`: BIGSERIAL PRIMARY KEY
- `name`: VARCHAR(100) NOT NULL
- `department_id`: BIGINT NOT NULL REFERENCES `departments(id)` ON DELETE RESTRICT
- `description`: VARCHAR(255)
- `created_at`: TIMESTAMP NOT NULL
- `updated_at`: TIMESTAMP NOT NULL

### 2.4 `users`
Authenticated personnel across all roles.
- `id`: BIGSERIAL PRIMARY KEY
- `email`: VARCHAR(120) UNIQUE NOT NULL (Index: `idx_user_email`)
- `password_hash`: VARCHAR(255) NOT NULL
- `first_name`: VARCHAR(60) NOT NULL
- `last_name`: VARCHAR(60) NOT NULL
- `phone`: VARCHAR(25)
- `role_id`: BIGINT NOT NULL REFERENCES `roles(id)` (Index: `idx_user_role`)
- `department_id`: BIGINT REFERENCES `departments(id)`
- `team_id`: BIGINT REFERENCES `teams(id)`
- `is_active`: BOOLEAN NOT NULL DEFAULT TRUE
- `created_at`: TIMESTAMP NOT NULL
- `updated_at`: TIMESTAMP NOT NULL

### 2.5 `sla_rules`
Configurable SLA response and resolution targets per priority level.
- `id`: BIGSERIAL PRIMARY KEY
- `priority`: VARCHAR(20) UNIQUE NOT NULL (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) (Index: `idx_sla_priority`)
- `response_time_hours`: INT NOT NULL (Maximum hours to first operational IT response)
- `resolution_time_hours`: INT NOT NULL (Maximum hours to complete ticket resolution)
- `warning_threshold_hours`: INT NOT NULL
- `description`: VARCHAR(255)

### 2.6 `tickets`
Central transactional entity for IT incidents.
- `id`: BIGSERIAL PRIMARY KEY
- `title`: VARCHAR(255) NOT NULL
- `description`: TEXT NOT NULL
- `category`: VARCHAR(30) NOT NULL (`HARDWARE`, `SOFTWARE`, `NETWORK`, `SECURITY`, `ACCESS_MANAGEMENT`, `EMAIL`, `VPN`, `OTHER`) (Index: `idx_ticket_category`)
- `priority`: VARCHAR(20) NOT NULL (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) (Index: `idx_ticket_priority`)
- `status`: VARCHAR(30) NOT NULL DEFAULT 'OPEN' (Index: `idx_ticket_status`)
- `created_by_id`: BIGINT NOT NULL REFERENCES `users(id)` (Index: `idx_ticket_created_by`)
- `assigned_engineer_id`: BIGINT REFERENCES `users(id)` (Index: `idx_ticket_assigned_engineer`)
- `assigned_team_id`: BIGINT REFERENCES `teams(id)` (Index: `idx_ticket_assigned_team`)
- `department_id`: BIGINT REFERENCES `departments(id)`
- `sla_id`: BIGINT REFERENCES `sla_rules(id)`
- `response_deadline`: TIMESTAMP (Index: `idx_ticket_response_deadline`)
- `responded_at`: TIMESTAMP (Index: `idx_ticket_responded_at`)
- `sla_deadline`: TIMESTAMP (Resolution Deadline) (Index: `idx_ticket_sla_deadline`)
- `resolved_at`: TIMESTAMP (Index: `idx_ticket_resolved_at`)
- `sla_paused_at`: TIMESTAMP
- `total_paused_duration_minutes`: BIGINT NOT NULL DEFAULT 0
- `resolution_description`: TEXT
- `created_at`: TIMESTAMP NOT NULL
- `updated_at`: TIMESTAMP NOT NULL

### 2.7 `ticket_comments`
Discussion and technical investigation notes on a ticket.
- `id`: BIGSERIAL PRIMARY KEY
- `ticket_id`: BIGINT NOT NULL REFERENCES `tickets(id)` ON DELETE CASCADE (Index: `idx_comment_ticket`)
- `author_id`: BIGINT NOT NULL REFERENCES `users(id)` (Index: `idx_comment_author`)
- `content`: TEXT NOT NULL
- `is_internal`: BOOLEAN NOT NULL DEFAULT FALSE
- `created_at`: TIMESTAMP NOT NULL

### 2.8 `ticket_assignments`
Audit log of ticket dispatch and assignment events.
- `id`: BIGSERIAL PRIMARY KEY
- `ticket_id`: BIGINT NOT NULL REFERENCES `tickets(id)` ON DELETE CASCADE (Index: `idx_assignment_ticket`)
- `assigned_by_id`: BIGINT NOT NULL REFERENCES `users(id)`
- `assigned_engineer_id`: BIGINT REFERENCES `users(id)` (Index: `idx_assignment_engineer`)
- `assigned_team_id`: BIGINT REFERENCES `teams(id)` (Index: `idx_assignment_team`)
- `notes`: TEXT
- `assigned_at`: TIMESTAMP NOT NULL

### 2.9 `ticket_status_history`
Immutable history of status changes tracking the lifecycle state machine.
- `id`: BIGSERIAL PRIMARY KEY
- `ticket_id`: BIGINT NOT NULL REFERENCES `tickets(id)` ON DELETE CASCADE (Index: `idx_history_ticket`)
- `changed_by_id`: BIGINT REFERENCES `users(id)` (Index: `idx_history_changed_by`)
- `old_status`: VARCHAR(30)
- `new_status`: VARCHAR(30) NOT NULL
- `change_reason`: VARCHAR(255)
- `changed_at`: TIMESTAMP NOT NULL

### 2.10 `ticket_attachments`
Uploaded log files, screenshots, and error dumps.
- `id`: BIGSERIAL PRIMARY KEY
- `ticket_id`: BIGINT NOT NULL REFERENCES `tickets(id)` ON DELETE CASCADE (Index: `idx_attachment_ticket`)
- `uploaded_by_id`: BIGINT NOT NULL REFERENCES `users(id)` (Index: `idx_attachment_uploader`)
- `file_name`: VARCHAR(255) NOT NULL
- `file_type`: VARCHAR(100) NOT NULL
- `file_size`: BIGINT
- `file_url`: VARCHAR(500) NOT NULL
- `created_at`: TIMESTAMP NOT NULL

### 2.11 `notifications`
User alerts for ticket assignments, SLA breaches, and updates.
- `id`: BIGSERIAL PRIMARY KEY
- `recipient_id`: BIGINT NOT NULL REFERENCES `users(id)` ON DELETE CASCADE (Index: `idx_notification_recipient`)
- `ticket_id`: BIGINT REFERENCES `tickets(id)` ON DELETE SET NULL
- `title`: VARCHAR(150) NOT NULL
- `message`: TEXT NOT NULL
- `type`: VARCHAR(50)
- `is_read`: BOOLEAN NOT NULL DEFAULT FALSE (Index: `idx_notification_read`)
- `created_at`: TIMESTAMP NOT NULL

### 2.12 `feedbacks`
End-user satisfaction rating upon ticket resolution.
- `id`: BIGSERIAL PRIMARY KEY
- `ticket_id`: BIGINT UNIQUE NOT NULL REFERENCES `tickets(id)` ON DELETE CASCADE (Index: `idx_feedback_ticket`)
- `submitted_by_id`: BIGINT NOT NULL REFERENCES `users(id)` (Index: `idx_feedback_user`)
- `rating`: INT NOT NULL CHECK (rating BETWEEN 1 AND 5)
- `comments`: TEXT
- `created_at`: TIMESTAMP NOT NULL

### 2.13 `audit_logs`
Security, compliance, and governance record for system actions.
- `id`: BIGSERIAL PRIMARY KEY
- `action`: VARCHAR(100) NOT NULL (Index: `idx_audit_action`)
- `entity_name`: VARCHAR(100) NOT NULL
- `entity_id`: BIGINT
- `performed_by_id`: BIGINT REFERENCES `users(id)` (Index: `idx_audit_user`)
- `details`: TEXT
- `timestamp`: TIMESTAMP NOT NULL (Index: `idx_audit_timestamp`)

---

## 3. Seeded Baselines (`DatabaseInitializer`)
The application automatically ensures the following baseline data is initialized on boot:
- **Roles**: `ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`
- **SLA Policies**:
  - `CRITICAL`: 2h resolution / 1h warning
  - `HIGH`: 4h resolution / 2h warning
  - `MEDIUM`: 8h resolution / 4h warning
  - `LOW`: 24h resolution / 12h warning
- **Departments & Teams**:
  - IT Operations (`IT-OPS`) -> Network Support, Hardware & Workplace
  - Information Security (`INFOSEC`) -> Identity & Access
- **Default Accounts**: Seeded administrative, managerial, engineering, and employee test accounts.

---

## 4. Ticket Domain Entity Relationships (Phase 5)

The Ticket Management system ties together employees, IT staff, organization structures, and audit histories. Below is a beginner-friendly explanation of how the core entities relate:

```
┌──────────────┐         1:N         ┌──────────────┐
│  Department  ├────────────────────►│     Team     │
└──────┬───────┘                     └──────┬───────┘
       │                                    │
       │ 1:N                          1:N   │
       ▼                                    ▼
┌──────────────┐         1:N (Requester)    ┌──────────────┐
│     User     ├───────────────────────────►│    Ticket    │
│              ├───────────────────────────►│              │
└──────┬───────┘         1:N (Engineer)     └──────┬───────┘
       │                                           │
       │                                     1:N   │ (Cascade Delete)
       │         ┌─────────────────────────────────┼─────────────────────────────────┐
       │         │                                 │                                 │
       ▼         ▼                                 ▼                                 ▼
┌──────────────────────┐        ┌──────────────────────┐        ┌─────────────────────────┐
│    TicketComment     │        │   TicketAssignment   │        │   TicketStatusHistory   │
│ (author_id -> User)  │        │(engineer_id -> User) │        │ (changed_by -> User)    │
│(ticket_id -> Ticket) │        │ (ticket_id -> Ticket)│        │ (ticket_id -> Ticket)   │
└──────────────────────┘        └──────────────────────┘        └─────────────────────────┘
```

### 4.1 Relationship Breakdown in Plain English

1. **Department & Team (`1-to-Many`)**:
   - A **Department** represents a top-level enterprise division (e.g., *IT Operations*, *Finance*).
   - Each Department contains multiple specialized **Teams** (e.g., *Network Support*, *Desktop Engineering*).
   - Foreign key: `teams.department_id` -> `departments.id`.

2. **User & Organization (`Many-to-1`)**:
   - Every **User** belongs to an optional Department and an optional functional Team (especially relevant for IT Engineers who belong to operational support teams).
   - Foreign keys: `users.department_id` -> `departments.id`, `users.team_id` -> `teams.id`.

3. **Ticket & Requester (`User`) (`Many-to-1`)**:
   - An employee creates a ticket when they have an IT problem. The employee is the **Requester** (`created_by_id`).
   - One user can create many tickets over time, but each ticket has exactly one original requester.
   - Foreign key: `tickets.created_by_id` -> `users.id`.

4. **Ticket & Assigned Engineer (`User`) (`Many-to-1`, Optional)**:
   - When a ticket is triaged or accepted, an IT Engineer (`ROLE_ENGINEER`) is assigned to resolve the issue (`assigned_engineer_id`).
   - When the ticket is newly created in `OPEN` status, this field is `NULL`.
   - One engineer can be actively working on multiple assigned tickets simultaneously.
   - Foreign key: `tickets.assigned_engineer_id` -> `users.id`.

5. **Ticket & Assigned Team (`Team`) (`Many-to-1`, Optional)**:
   - Tickets can be routed to a specific support team (e.g., *Network Support*) even before an individual engineer is assigned, or to indicate team queue ownership.
   - Foreign key: `tickets.assigned_team_id` -> `teams.id`.

6. **Ticket & Comments (`TicketComment`) (`1-to-Many`, Cascade Delete)**:
   - Requester, assigned engineers, and managers can exchange messages and progress notes on a ticket.
   - Each comment stores its text, the authoring user, whether it is an internal IT-only note (`is_internal = true`), and the timestamp.
   - If a ticket is deleted, all its associated comments are deleted automatically (`ON DELETE CASCADE`).
   - Foreign keys: `ticket_comments.ticket_id` -> `tickets.id`, `ticket_comments.author_id` -> `users.id`.

7. **Ticket & Assignment History (`TicketAssignment`) (`1-to-Many`, Cascade Delete)**:
   - Tickets frequently get escalated or reassigned from Level 1 helpdesk to Tier 2/3 engineering.
   - Instead of overwriting who worked on a ticket, every dispatch creates an immutable `TicketAssignment` log containing who made the assignment (`assigned_by_id`), who received it (`assigned_engineer_id`), what team it was assigned to (`assigned_team_id`), and dispatch notes.
   - Foreign keys: `ticket_assignments.ticket_id` -> `tickets.id`, `ticket_assignments.assigned_by_id` -> `users.id`, `ticket_assignments.assigned_engineer_id` -> `users.id`.

8. **Ticket & Status History (`TicketStatusHistory`) (`1-to-Many`, Cascade Delete)**:
   - The ticket lifecycle tracks state transitions (`OPEN` -> `ASSIGNED` -> `IN_PROGRESS` -> `RESOLVED` -> `CLOSED`).
   - Every valid transition logs the previous status (`old_status`), new status (`new_status`), the user who triggered the change (`changed_by_id`), an optional reason or resolution description, and the exact timestamp.
   - This history is critical for Phase 6 SLA calculation (measuring how long a ticket remained in each state).
   - Foreign keys: `ticket_status_history.ticket_id` -> `tickets.id`, `ticket_status_history.changed_by_id` -> `users.id`.

9. **Ticket & Attachments (`TicketAttachment`) (`1-to-Many`, Cascade Delete)**:
   - Users can attach error logs, crash dumps, and screenshots to assist troubleshooting.
   - Foreign keys: `ticket_attachments.ticket_id` -> `tickets.id`, `ticket_attachments.uploaded_by_id` -> `users.id`.

10. **Ticket & SLA Policy (`SLA`) (`Many-to-1`, Optional)**:
    - When a ticket is created, the system matches its priority (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) against the active `sla_rules` configuration.
    - The ticket references this policy to record the agreed first response time, resolution time, and warning threshold.
    - Foreign key: `tickets.sla_id` -> `sla_rules.id`.

---

## 5. MongoDB Knowledge Base Schema Specification (Phase 10)

Database: `techconnect_knowledge`

### 5.1 Collection: `knowledge_articles`
Stores structured troubleshooting documentation, SOPs, and knowledge articles.

| Field | BSON Type | Index | Description |
|---|---|---|---|
| `_id` | ObjectId / String | Primary Key | Unique document identifier |
| `title` | String | Text, Single | Article title |
| `slug` | String | Unique Single | URL-friendly unique slug |
| `summary` | String | Text | High-level problem overview |
| `problem` | String | Text | Observed symptoms, error codes |
| `cause` | String | Text | Root technical cause / diagnostic |
| `resolution` | String | Text | Step-by-step troubleshooting actions |
| `content` | String | None | Synthesized or custom Markdown body |
| `category` | String (Enum) | Single | `TicketCategory` vocabulary |
| `tags` | Array of String | Multikey, Text | Normalized lowercase keyword tags |
| `status` | String (Enum) | Single | `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| `author_id` | Int64 | Single | Reference to PostgreSQL `users.id` |
| `author_name` | String | None | Cached author display name |
| `author_email` | String | None | Cached author email |
| `version` | Int32 | None | Incremental revision counter (v1, v2...) |
| `view_count` | Int64 | None | Atomic counter of article reads |
| `helpful_count` | Int64 | None | Helpful feedback counter |
| `not_helpful_count` | Int64 | None | Not helpful feedback counter |
| `feedback_user_ids` | Array of Int64 | Single | Set of user IDs who have submitted feedback |
| `source_type` | String | None | `MANUAL` or `TICKET` |
| `source_ticket_id` | Int64 | Single | Optional reference to PostgreSQL `tickets.id` |
| `created_at` | Date | None | Document creation timestamp |
| `updated_at` | Date | Single | Last modification timestamp |
| `published_at` | Date | None | Date published to enterprise catalog |
| `archived_at` | Date | None | Date retired from public catalog |
| `normalized_text` | String | None | Pre-synthesized plain text for RAG chunking |
| `embedding_status` | String (Enum) | Single | Ingestion lifecycle: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `embedding_model` | String | None | Embedding model tag (e.g. `sentence-transformers/all-MiniLM-L6-v2`) |
| `embedding_version` | Int32 | None | Model/chunker schema version (default: 1) |
| `embedding_updated_at` | Date | None | Timestamp of last successful vector embedding |
| `embedding_error` | String | None | Safe error message if embedding ingestion fails |

### 5.2 Collection: `knowledge_article_history`
Maintains an immutable audit log of revisions and lifecycle transitions.

| Field | BSON Type | Index | Description |
|---|---|---|---|
| `_id` | ObjectId / String | Primary Key | Unique audit entry identifier |
| `article_id` | String | Single | Reference to `knowledge_articles._id` |
| `action` | String (Enum) | None | `CREATED`, `UPDATED`, `PUBLISHED`, `ARCHIVED`, `RESTORED`, `REVERTED_TO_DRAFT` |
| `performed_by_id` | Int64 | None | User ID who initiated action |
| `performed_by_name` | String | None | User display name |
| `performed_by_email` | String | None | User email |
| `performed_at` | Date | None | Timestamp of the event |
| `version` | Int32 | None | Article version at time of event |
| `details` | String | None | Contextual description of modification |

### 5.3 Cross-Database Referencing Strategy
- **No Foreign Key Constraints**: PostgreSQL and MongoDB operate as independent database engines. Foreign key constraints across database boundaries do not exist.
- **Application-Enforced Integrity**: Spring Boot services validate `author_id` against PostgreSQL `UserRepository` and verify role permissions prior to mutating MongoDB documents.
- **Eventual Consistency**: If a user is deactivated or renamed in PostgreSQL, article authorship preserves historical fidelity without relational cascade failures.

---

## 6. PostgreSQL + pgvector Vector Storage Specification (Phase 11)

### 6.1 Database Extension
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 6.2 Table: `knowledge_embedding_chunks`
Stores dense mathematical vector representations of chunked knowledge articles.

| Column | SQL Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Unique integer sequence identifier |
| `article_id` | VARCHAR(64) | NOT NULL, INDEX | Foreign key reference to MongoDB `knowledge_articles._id` |
| `chunk_id` | VARCHAR(80) | UNIQUE NOT NULL | Deterministic identifier (`{article_id}-{chunk_index}`) |
| `chunk_index` | INT | NOT NULL | Sequential index of chunk within article (0, 1, 2...) |
| `article_version` | INT | NOT NULL | Article version number at time of embedding generation |
| `section` | VARCHAR(50) | NOT NULL | Structural section (`TITLE`, `PROBLEM`, `CAUSE`, `RESOLUTION`, `GENERAL`) |
| `chunk_text` | TEXT | NOT NULL | Content payload of the chunk (max ~500 chars) |
| `category` | VARCHAR(50) | NULL | Ticket category classification for filtered retrieval |
| `tags` | TEXT[] / VARCHAR[] | NULL | Associated keyword tags |
| `embedding` | vector(384) | NOT NULL | Dense 384-dimensional vector (`all-MiniLM-L6-v2`) |
| `embedding_model` | VARCHAR(100) | NOT NULL | Model identifier used to generate vector |
| `status` | VARCHAR(20) | NOT NULL | Article publication status (`PUBLISHED`, `DRAFT`, `ARCHIVED`) for RBAC filtering |
| `created_at` | TIMESTAMP | NOT NULL | Timestamp when vector chunk was generated |

### 6.3 Vector Indexes & Distance Metric
- **Index Type**: Hierarchical Navigable Small World (`HNSW`) index
  ```sql
  CREATE INDEX idx_chunks_embedding_hnsw 
  ON knowledge_embedding_chunks 
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
  ```
- **Distance Operator**: `<=>` (Cosine Distance)
- **Similarity Conversion Formula**:
  $$\text{Cosine Similarity} = 1.0 - (\text{embedding} \iff \text{query\_vector})$$
- **Why Cosine Distance?**: Standard text embedding models like `all-MiniLM-L6-v2` normalize embeddings to unit length. Cosine similarity measures directional alignment irrespective of magnitude, producing intuitive similarity scores between 0.0 (orthogonal/unrelated) and 1.0 (identical semantics).



