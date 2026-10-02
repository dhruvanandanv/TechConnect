# TechConnect Full-Stack Integration & API Reliability Guide

This document provides a comprehensive operational overview of the TechConnect full-stack integration architecture, communication patterns, security boundaries, and reliability mechanisms established in **Phase 8**.

---

## 1. System Architecture

TechConnect operates as a decoupled, stateless client-server architecture:

```
+-------------------------------------------------------------+
|                      React 19 SPA                           |
|      (Components, Pages, RoleGuard, ProtectedRoute)         |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                     Axios HTTP Client                       |
|   (Request Bearer Interceptor, 401 Session Expiry Catch)    |
+------------------------------+------------------------------+
                               |
                   REST APIs / JSON / CORS
                               |
                               v
+-------------------------------------------------------------+
|                  Spring Boot 3.3 REST API                   |
|                    (Port 8080 /api)                         |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|              Spring Security 6 & JWT Filter                 |
|  (Stateless Context, Role Authorities, Exception Entry Pt)  |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                 Controllers & DTO Validation                |
|      (@Valid, Bean Validation, GlobalExceptionHandler)      |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|              Service Layer & Domain Logic                   |
|     (Ticket Lifecycle State Machine, SLA Automation, IDOR)  |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                Spring Data JPA Repositories                 |
|            (Specifications, Audit Logs, History)            |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                 PostgreSQL Relational DB                    |
|             (ACID Transactions, Foreign Keys)               |
+-------------------------------------------------------------+
```

---

## 2. API Communication Layer

### 2.1 Centralized Axios Client (`frontend/src/services/api.js`)
- **Base URL**: Controlled by `VITE_API_BASE_URL` (default: `http://localhost:8080/api`).
- **Timeout**: Enforces a strict 15-second request timeout (`timeout: 15000`).
- **Content Headers**: Sets `Content-Type: application/json` and `Accept: application/json`.
- **JWT Injection**: Automatically reads `techconnect_token` from `localStorage` and appends `Authorization: Bearer <token>` strictly when a non-empty, valid token exists.

### 2.2 Error Normalization (`extractErrorMessage`)
All HTTP and network failures pass through `extractErrorMessage(error)` to present safe, human-readable error messages without leaking internal server details:
- **Network Outage / Server Offline**: `"Unable to connect to TechConnect server. Please ensure the backend is running."`
- **Request Timeout**: `"Request timed out. Unable to connect to TechConnect server."`
- **400 Bad Request**: Structured bean validation error messages (`fieldName: reason`) or `"Invalid request submitted."`
- **401 Unauthorized**: `"Your session has expired. Please log in again."`
- **403 Forbidden**: `"You do not have permission to perform this action."`
- **404 Not Found**: `"The requested resource was not found."`
- **409 Conflict**: `"The requested operation conflicts with the current ticket state."`
- **500 Server Error**: `"Something went wrong on the server. Please try again later."`

---

## 3. Authentication & JWT Lifecycle

### 3.1 Authentication Workflow
1. **Registration** (`POST /api/auth/register`):
   - Open to corporate users for standard employee self-service (`ROLE_EMPLOYEE`).
   - Server strictly prohibits self-escalation to `ROLE_ADMIN` or `ROLE_MANAGER`.
   - Passwords are hashed using BCrypt.
2. **Login** (`POST /api/auth/login`):
   - Authenticates credentials against the PostgreSQL `users` table.
   - Generates a signed, stateless JWT containing the user's email subject, user ID claim, and role authority claim (`ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`).
   - Returns JWT and user profile metadata to the client.
3. **Session Storage**:
   - `authService.login()` stores the JWT in `localStorage` under `techconnect_token` and the sanitized user profile under `techconnect_user`.
   - `AuthContext` provides authentication status and active user state across the React component tree.
4. **Token Expiry & 401 Handling**:
   - When a protected API returns `401 Unauthorized` (e.g. token expired, invalid signature):
     1. Axios response interceptor verifies the request was not to an auth endpoint (`/auth/login`).
     2. Stale `techconnect_token` and `techconnect_user` are cleared from `localStorage`.
     3. An application-wide `techconnect:auth-expired` event is broadcast.
     4. `AuthContext` clears user state and populates `sessionExpiredMessage`.
     5. `ProtectedRoute` redirects the browser to `/login`, preserving original location in `location.state.from`.
     6. No infinite redirect loops occur.
5. **Logout**:
   - `authService.logout()` purges stored tokens and user details from `localStorage`.
   - Context is reset to `null` and user is routed to `/login`.

---

## 4. Role-Based Access Control (RBAC) & Authorization Matrix

Authorization is enforced at multiple layers: UI routes, navigation items, backend Spring Security URL matchers, method-level annotations, and service-level object ownership checks.

| Role | Scope of Access | Permitted Ticket Operations | SLA Governance Access |
|---|---|---|---|
| **`ROLE_EMPLOYEE`** | Own submitted tickets only | Create tickets, add public comments, transition own ticket `RESOLVED -> CLOSED` or `WAITING_FOR_USER -> IN_PROGRESS` | Own ticket SLA metrics only (`/api/tickets/{id}/sla`) |
| **`ROLE_ENGINEER`** | Assigned tickets, own tickets, and open unassigned tickets | Self-assign open tickets, update status (all except `MANAGER_REVIEW`), post internal notes and public comments | Ticket SLA metrics for assigned/visible tickets |
| **`ROLE_MANAGER`** | Managed department and team tickets | Assign/reassign tickets to engineers, transition status (including `MANAGER_REVIEW`), post notes | Full platform SLA summary (`/api/sla/summary`) and breached tickets (`/api/sla/breached`) |
| **`ROLE_ADMIN`** | System-wide access to all tickets and settings | Full lifecycle modifications, assignments, configuration | Full platform SLA governance and administrative oversight |

### 4.1 Horizontal Privilege Escalation Protection (IDOR)
- **Object-Level Guard (`assertCanViewTicket`)**:
  - Employee A cannot view, comment on, update, or inspect SLA metrics for Employee B's ticket.
  - Engineer A cannot view or update non-open tickets assigned to Engineer B.
  - Manager A cannot view, assign, or modify status on tickets outside their managed department or team.
  - Any horizontal escalation attempt triggers an immediate `403 Forbidden` response from the backend.

---

## 5. Ticket State Machine Integration

The frontend UI strictly mirrors the backend ITIL lifecycle transitions defined in `TicketServiceImpl.ALLOWED_TRANSITIONS`:

```
               +-------------+
               |    OPEN     |
               +------+------+
                      | (Auto or Engineer Assignment)
                      v
               +-------------+
               |  ASSIGNED   | <--------+
               +------+------+          |
                      |                 |
                      v                 |
       +--------> IN_PROGRESS <----+    |
       |              |            |    |
       | (Resume)     | (Clarify)  |    | (Reassign)
       |              v            |    |
       |      WAITING_FOR_USER ----+    |
       |              |                 |
       |              |                 |
       |              v (Escalate)      |
       |          ESCALATED             |
       |              |                 |
       |              v                 |
       |       MANAGER_REVIEW ----------+
       |              |
       | (Reopen)     v
       +---------- RESOLVED
                      | (Confirm)
                      v
                    CLOSED
```

### UI State Machine Enforcement:
- The "Update Status" modal in `TicketDetails.jsx` dynamically queries `getAllowedStatusTransitions()` based on the ticket's current status and the authenticated user's role.
- Impossible transitions (e.g. `OPEN -> CLOSED` or `RESOLVED -> WAITING_FOR_USER`) are never presented in the UI dropdown.
- Closed tickets disable all further status transitions.

---

## 6. SLA Engine Integration

### 6.1 Policies & Milestones
- **Priority Deadlines**:
  - `CRITICAL`: 1h Response / 2h Resolution
  - `HIGH`: 4h Response / 24h Resolution
  - `MEDIUM`: 8h Response / 48h Resolution
  - `LOW`: 24h Response / 72h Resolution
- **First Response Milestone**: Recorded upon initial IT staff action (status change to `IN_PROGRESS`, engineer assignment, or staff comment).
- **Pause & Resume Mechanism**: When entering `WAITING_FOR_USER`, the SLA timer pauses. When exiting, remaining deadlines are extended by the exact paused duration.
- **Resolution Milestone**: Recorded upon transition to `RESOLVED`.
- **Status Categories**: `ON_TRACK`, `AT_RISK` (remaining time <= 20%), `BREACHED`, `PAUSED`, `COMPLETED`.

---

## 7. Cross-Origin Resource Sharing (CORS)

### 7.1 Development Configuration
Spring Security is configured to accept CORS requests from designated development origins:
- `http://localhost:5173` (Vite dev server)
- `http://127.0.0.1:5173`
- `http://localhost:3000`
- `http://127.0.0.1:3000`

Configured in `application.properties`:
```properties
techconnect.cors.allowed-origins=${TECHCONNECT_CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}
```

Allowed HTTP Methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`
Allowed Headers: `Authorization, Content-Type, Accept, Origin, X-Requested-With`
Max Age: `3600` seconds (1 hour)

---

## 8. Environment Configuration

### Backend (`backend/src/main/resources/application.properties`):
| Variable | Default Value | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/techconnect_db` | Relational database connection string |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `TECHCONNECT_JWT_SECRET` | *(empty string; provided via env)* | 256-bit HMAC SHA secret key |
| `TECHCONNECT_JWT_EXPIRATION_MS` | `3600000` (1 hour) | JWT token lifespan in milliseconds |
| `TECHCONNECT_SEED_DEV_USERS` | `false` | Development seed trigger (true in dev) |
| `TECHCONNECT_DEV_PASSWORD` | *(empty string; provided via env)* | Password for seeded demo users |
| `TECHCONNECT_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Comma-separated allowed origins |

### Frontend (`frontend/.env`):
| Variable | Value | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | Spring Boot backend REST endpoint |

---

## 9. Local Setup & Verification

### 1. Start Backend:
```powershell
cd e:\TECHCONNECT\backend
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

### 1. Start Python AI Ticket Intelligence Service:
```powershell
cd e:\TECHCONNECT\ai_services\ticket_intelligence
pytest -v
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. Start Backend:
```powershell
cd e:\TECHCONNECT\backend
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

### 3. Start Frontend:
```powershell
cd e:\TECHCONNECT\frontend
npm run build
npm run dev
```

### 4. Demo Accounts:
When running with development user seeding enabled (`TECHCONNECT_SEED_DEV_USERS=true`), the following test accounts are available:
- **Employee**: `employee@techconnect.com`
- **Engineer**: `engineer@techconnect.com`
- **Manager**: `manager@techconnect.com`
- **Admin**: `admin@techconnect.com`

---

## 10. AI Ticket Intelligence Integration (Phase 9)

```
React (CreateTicket.jsx) 
   ──[POST /api/tickets/analyze]──> Spring Boot (TicketController)
                                       ──[RestClient (3000ms)]──> Python FastAPI (:8000)
                                                                     ├── TF-IDF + LogisticRegression
                                                                     ├── Priority Rules Engine
                                                                     └── Team Routing Matrix
```
- **Assisting, Non-Authoritative**: AI suggestions are recommendations; human selections remain authoritative.
- **Fail-Open Architecture**: Unavailability of the AI microservice does not block ticket creation.

---

## 11. Knowledge Base Management Integration (Phase 10)

```
React (KnowledgeBase / Viewer / Editor)
   ──[REST /api/knowledge/**]──> Spring Boot (KnowledgeArticleController)
                                     ├── Spring Security JWT & RBAC Matrix
                                     ├── KnowledgeArticleService (Slug, Validation, History)
                                     └── Spring Data MongoDB / MongoTemplate
                                           └── MongoDB (:27017, techconnect_knowledge)
                                                 ├── knowledge_articles
                                                 └── knowledge_article_history
```

### Security & Access Boundaries:
- **No Direct MongoDB Exposure**: The browser client connects exclusively to authenticated Spring Boot REST endpoints. Direct MongoDB connections from frontend are strictly prohibited.
- **Employee Isolation**: Employees can only retrieve articles with `status: "PUBLISHED"`. Unpublished drafts, archived guides, and revision history are inaccessible and return `403 Forbidden`.
- **Engineer Ownership**: Engineers are restricted to modifying, publishing, and archiving articles they personally authored. Tampering with other engineers' articles is rejected with `403 Forbidden`.
- **Atomic Operations**: Article views and helpful/not helpful votes execute atomic `$inc` operations in MongoDB, completely avoiding read-modify-write lost updates under concurrency.

---

## 12. Common Troubleshooting & Error Resolution

1. **"Unable to connect to TechConnect server"**:
   - Check if Spring Boot is running on port 8080.
   - Verify `VITE_API_BASE_URL` in `frontend/.env` points to `http://localhost:8080/api`.
2. **"AI analysis is currently unavailable"**:
   - Verify the Python FastAPI microservice is running on `http://localhost:8000`.
   - Check `GET http://localhost:8000/health`.
   - Standard ticket creation is unaffected and continues normally.
3. **"Access is denied: insufficient role privileges" (403)**:
   - Check that the logged-in user possesses the required role authority for the resource or ticket.
4. **"Invalid ticket status transition" or "Invalid knowledge article state transition" (400)**:
   - State transition rules prohibit arbitrary skipping. Review permitted lifecycle state transitions.
5. **"Your session has expired. Please log in again" (401)**:
   - Token has exceeded its 1-hour validity window. Log in again to obtain a fresh JWT.
6. **"MongoDB connection timeout" (500)**:
   - Verify local MongoDB service is running on port 27017 (`net start MongoDB` on Windows or `docker compose up -d mongodb`).

---

## 13. Known Limitations (Post-Phase 10)
- **Keyword Search Only**: Search relies on MongoDB text and regex pattern matching. Semantic vector embeddings and neural similarity search belong to Phase 11+.
- **RAG & GenAI Copilot**: Knowledge articles maintain clean normalized text structures, but retrieval-augmented generation and LLM copilot are targeted for subsequent phases.
- **Token Refresh**: Silent refresh token rotation is not yet implemented; users must re-authenticate upon token expiration.
- **WebSocket Push**: Ticket updates and SLA status refresh via HTTP refetching rather than real-time WebSockets.


