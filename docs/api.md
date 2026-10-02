# TechConnect REST API Documentation

## 1. Overview
All REST APIs communicate via JSON over HTTP/HTTPS with UTF-8 encoding.
Base URL: `/api`

### Consistent Error Response Structure
Every failed request returns a consistent, standardized JSON error body:
```json
{
  "success": false,
  "message": "Human readable error description",
  "timestamp": "2026-10-01T15:50:00.123456",
  "path": "/api/endpoint",
  "errors": [
    "fieldName: validation failure description"
  ]
}
```

---

## 2. Authentication & Authorization Architecture

### 2.1 JWT Bearer Token Authentication
TechConnect implements stateless authentication using JSON Web Tokens (JWT).
- The client obtains an access token by submitting valid credentials to `POST /api/auth/login`.
- For subsequent requests to protected endpoints, the client includes the token in the HTTP `Authorization` header:
  ```http
  Authorization: Bearer <JWT_TOKEN>
  ```
- If the token is missing, expired, malformed, or has an invalid signature, the server responds with **`401 Unauthorized`**.
- If the token is valid but the authenticated user does not have the required role, the server responds with **`403 Forbidden`**.

### 2.2 Token Lifecycle & Expiration
- Access tokens expire after a configurable duration specified by `security.jwt.expiration-ms` (default: `3600000` ms, i.e., 1 hour).
- Expiration is embedded inside the standard `exp` claim in the JWT.

### 2.3 Role Authorization Matrix

| Endpoint | Method | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `/api/health` | `GET` | **Public** (All) | Backend health probe |
| `/api/auth/register` | `POST` | **Public** (All) | Registers new employee accounts |
| `/api/auth/login` | `POST` | **Public** (All) | Verifies credentials, issues JWT |
| `/api/employee/test` | `GET` | `ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Accessible to all authenticated internal staff |
| `/api/engineer/test` | `GET` | `ROLE_ENGINEER`, `ROLE_ADMIN` | Accessible to IT engineers and administrators |
| `/api/manager/test` | `GET` | `ROLE_MANAGER`, `ROLE_ADMIN` | Accessible to departmental managers and administrators |
| `/api/admin/test` | `GET` | `ROLE_ADMIN` | Strictly restricted to administrators |
| `/api/**` (Other) | Any | **Authenticated** | Default fallback requires authentication |

---

## 3. API Endpoints

### 3.1 System Health

#### `GET /api/health`
- **Description**: Returns operational status of the backend application.
- **Authentication**: Public
- **HTTP Status**: `200 OK`
- **Response**:
  ```text
  TechConnect Backend is running!
  ```

---

### 3.2 Authentication

#### `POST /api/auth/register`
- **Description**: Registers a new user account. Passwords are encrypted using BCrypt before persistence. Public registration defaults to `ROLE_EMPLOYEE` and prohibits self-assigning `ROLE_ADMIN` or `ROLE_MANAGER`.
- **Authentication**: Public
- **Validation Rules**:
  - `name`: Required, 2-100 characters.
  - `email`: Required, valid email format, max 120 characters, must be unique across all accounts.
  - `password`: Required, minimum 8 characters, maximum 100 characters.
  - `role`: Optional. Defaults to `ROLE_EMPLOYEE`. Attempting to register as `ROLE_ADMIN` or `ROLE_MANAGER` triggers `400 Bad Request`.
- **HTTP Status**: `201 Created`
- **Request Example**:
  ```json
  {
    "name": "Jane Doe",
    "email": "jane.doe@example.com",
    "password": "UserSecurePass123!"
  }
  ```
- **Response Example (`201 Created`)**:
  ```json
  {
    "success": true,
    "message": "User registered successfully",
    "user": {
      "id": 5,
      "name": "Jane Doe",
      "email": "jane.doe@example.com",
      "role": "ROLE_EMPLOYEE",
      "departmentId": null,
      "departmentName": null,
      "teamId": null,
      "teamName": null,
      "isActive": true,
      "createdAt": "2026-10-01T15:50:12.345678"
    }
  }
  ```
- **Error Responses**:
  - `400 Bad Request` (Validation failure / Role escalation rejected):
    ```json
    {
      "success": false,
      "message": "Public registration cannot assign administrative roles (ROLE_ADMIN or ROLE_MANAGER)",
      "timestamp": "2026-10-01T15:50:15.123",
      "path": "/api/auth/register"
    }
    ```
  - `409 Conflict` (Duplicate email):
    ```json
    {
      "success": false,
      "message": "Email address 'jane.doe@example.com' is already registered",
      "timestamp": "2026-10-01T15:50:20.456",
      "path": "/api/auth/register"
    }
    ```

---

#### `POST /api/auth/login`
- **Description**: Verifies user email and BCrypt-hashed password. Upon successful verification, generates and returns a signed JWT access token along with user profile details.
- **Authentication**: Public
- **HTTP Status**: `200 OK`
- **Request Example**:
  ```json
  {
    "email": "jane.doe@example.com",
    "password": "UserSecurePass123!"
  }
  ```
- **Response Example (`200 OK`)**:
  ```json
  {
    "success": true,
    "message": "Login successful",
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqYW5lLmRvZUBleGFtcGxlLmNvbSIsInJvbGVzIjpbIlJPTEVfRU1QTE9ZRUUiXSwiaWF0IjoxNzU5MzI0NDAwLCJleHAiOjE3NTkzMjgwMDB9.EXAMPLE_SIGNATURE",
    "tokenType": "Bearer",
    "expiresIn": 3600000,
    "user": {
      "id": 5,
      "name": "Jane Doe",
      "email": "jane.doe@example.com",
      "role": "ROLE_EMPLOYEE",
      "departmentId": null,
      "departmentName": null,
      "teamId": null,
      "teamName": null,
      "isActive": true,
      "createdAt": "2026-10-01T15:50:12.345678"
    }
  }
  ```
- **Error Responses**:
  - `401 Unauthorized` (Invalid credentials or inactive account):
    ```json
    {
      "success": false,
      "message": "Invalid email or password",
      "timestamp": "2026-10-01T15:50:25.789",
      "path": "/api/auth/login"
    }
    ```

---

### 3.3 Role-Based Authorization Demonstration Endpoints

#### `GET /api/employee/test`
- **Description**: Test endpoint for standard employee access.
- **Authentication**: Bearer JWT required
- **Authorized Roles**: `ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`
- **Headers**:
  ```http
  Authorization: Bearer <token>
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "success": true,
    "message": "Authorized: Employee endpoint accessed successfully",
    "roleRequirement": "ROLE_EMPLOYEE, ROLE_ENGINEER, ROLE_MANAGER, or ROLE_ADMIN"
  }
  ```

---

#### `GET /api/engineer/test`
- **Description**: Test endpoint for engineering operations.
- **Authentication**: Bearer JWT required
- **Authorized Roles**: `ROLE_ENGINEER`, `ROLE_ADMIN`
- **Headers**:
  ```http
  Authorization: Bearer <token>
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "success": true,
    "message": "Authorized: Engineer endpoint accessed successfully",
    "roleRequirement": "ROLE_ENGINEER or ROLE_ADMIN"
  }
  ```
- **Forbidden Response (`403 Forbidden`)** (e.g., accessed by EMPLOYEE or MANAGER):
  ```json
  {
    "success": false,
    "message": "Access is denied: insufficient role privileges to access this resource",
    "timestamp": "2026-10-01T15:51:00.123",
    "path": "/api/engineer/test"
  }
  ```

---

#### `GET /api/manager/test`
- **Description**: Test endpoint for managerial oversight.
- **Authentication**: Bearer JWT required
- **Authorized Roles**: `ROLE_MANAGER`, `ROLE_ADMIN`
- **Headers**:
  ```http
  Authorization: Bearer <token>
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "success": true,
    "message": "Authorized: Manager endpoint accessed successfully",
    "roleRequirement": "ROLE_MANAGER or ROLE_ADMIN"
  }
  ```
- **Forbidden Response (`403 Forbidden`)** (e.g., accessed by EMPLOYEE or ENGINEER):
  ```json
  {
    "success": false,
    "message": "Access is denied: insufficient role privileges to access this resource",
    "timestamp": "2026-10-01T15:51:05.456",
    "path": "/api/manager/test"
  }
  ```

---

#### `GET /api/admin/test`
- **Description**: Test endpoint for system administrators.
- **Authentication**: Bearer JWT required
- **Authorized Roles**: `ROLE_ADMIN`
- **Headers**:
  ```http
  Authorization: Bearer <token>
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "success": true,
    "message": "Authorized: Admin endpoint accessed successfully",
    "roleRequirement": "ROLE_ADMIN"
  }
  ```
- **Forbidden Response (`403 Forbidden`)** (e.g., accessed by EMPLOYEE, ENGINEER, or MANAGER):
  ```json
  {
    "success": false,
    "message": "Access is denied: insufficient role privileges to access this resource",
    "timestamp": "2026-10-01T15:51:10.789",
    "path": "/api/admin/test"
  }
  ```

---

### 3.4 Common Security Responses

#### Unauthenticated Request (`401 Unauthorized`)
Returned when an endpoint requires authentication but no Authorization header is provided, or the token is expired/invalid/malformed:
```json
{
  "success": false,
  "message": "Full authentication is required to access this resource",
  "timestamp": "2026-10-01T15:51:15.000",
  "path": "/api/employee/test"
}
```

#### Unauthorized Privilege Access (`403 Forbidden`)
Returned when the user is properly authenticated, but their assigned role does not grant sufficient privileges for the target endpoint:
```json
{
  "success": false,
  "message": "Access is denied: insufficient role privileges to access this resource",
  "timestamp": "2026-10-01T15:51:20.000",
  "path": "/api/admin/test"
}
```

---

## 4. Ticket Management System APIs (Phase 5)

### 4.1 Overview & Ticket Workflow State Machine

The TechConnect Ticket Management System implements a strict ITIL-aligned status lifecycle with deterministic transitions and role-based guardrails:

```
[ OPEN ]
   │
   ▼
[ ASSIGNED ]
   │
   ▼
[ IN_PROGRESS ] ──────────► [ ESCALATED ]
   │      ▲                       │
   │      │                       ▼
   │  [ WAITING_FOR_USER ]    [ MANAGER_REVIEW ]
   │
   ▼
[ RESOLVED ]
   │
   ▼
[ CLOSED ]
```

#### State Transition Rules:
- `OPEN` ➔ `ASSIGNED`: Occurs upon engineer assignment.
- `ASSIGNED` ➔ `IN_PROGRESS`: Engineer begins work.
- `IN_PROGRESS` ➔ `WAITING_FOR_USER`: Waiting for employee clarification/logs.
- `WAITING_FOR_USER` ➔ `IN_PROGRESS`: Work resumes upon feedback.
- `IN_PROGRESS` ➔ `RESOLVED`: Engineer provides resolution description.
- `RESOLVED` ➔ `CLOSED`: Requester (Employee) or Manager/Admin confirms satisfaction and closes ticket.
- `IN_PROGRESS` ➔ `ESCALATED`: Escalation requested.
- `ESCALATED` ➔ `MANAGER_REVIEW`: Manager intervenes and reviews escalation.

Arbitrary jumps (e.g. `OPEN` ➔ `CLOSED`) are strictly rejected with `400 Bad Request` or `403 Forbidden`.

---

### 4.2 Role & Ownership Visibility Matrix

| Role | Ticket Creation | Ticket Visibility (`/my` & `/{id}`) | Permitted Updates | Status Transitions | Assignment | Comments |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`ROLE_EMPLOYEE`** | Yes (own tickets) | Only tickets requested by themselves | Title, description, category, priority (only while `OPEN`) | `RESOLVED` ➔ `CLOSED` (own tickets) | No | Public comments on own tickets |
| **`ROLE_ENGINEER`** | Yes | Tickets assigned to them | Operational fields (resolution description) | `ASSIGNED`, `IN_PROGRESS`, `WAITING_FOR_USER`, `RESOLVED`, `ESCALATED` (assigned tickets) | Self-assignment (if unassigned) | Public & internal notes on assigned tickets |
| **`ROLE_MANAGER`** | Yes | Tickets belonging to their team / department | Operational & managerial fields | Operational + `MANAGER_REVIEW`, `CLOSED` on team tickets | Assign/reassign engineers within team | Public & internal notes on team tickets |
| **`ROLE_ADMIN`** | Yes | All tickets across enterprise | Full update privileges | All valid workflow transitions across enterprise | Full assignment across all teams/engineers | Full comment access on all tickets |

---

### 4.3 Endpoints Reference

#### `POST /api/tickets`
- **Description**: Creates a new service ticket. Requester is automatically resolved from the authenticated JWT `SecurityContext`. Initial status is always `OPEN`.
- **Authentication**: Bearer JWT (`ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`)
- **Validation**:
  - `title`: Required, 3–255 characters.
  - `description`: Required, non-empty.
  - `category`: Required (`HARDWARE`, `SOFTWARE`, `NETWORK`, `SECURITY`, `ACCESS_MANAGEMENT`, `EMAIL`, `VPN`, `OTHER`).
  - `priority`: Required (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
- **Request Body**:
  ```json
  {
    "title": "Cannot connect to Cisco AnyConnect VPN",
    "description": "Getting error 442 failed to enable virtual adapter on Windows 11.",
    "category": "VPN",
    "priority": "HIGH"
  }
  ```
- **Response (`201 Created`)**:
  ```json
  {
    "id": 101,
    "title": "Cannot connect to Cisco AnyConnect VPN",
    "description": "Getting error 442 failed to enable virtual adapter on Windows 11.",
    "category": "VPN",
    "priority": "HIGH",
    "status": "OPEN",
    "requester": {
      "id": 4,
      "name": "John Doe",
      "email": "employee@techconnect.com",
      "role": "ROLE_EMPLOYEE"
    },
    "assignedEngineer": null,
    "assignedTeam": null,
    "slaDeadline": "2026-10-01T23:30:00",
    "resolvedAt": null,
    "resolutionDescription": null,
    "createdAt": "2026-10-01T19:30:00",
    "updatedAt": "2026-10-01T19:30:00"
  }
  ```

---

#### `GET /api/tickets/my`
- **Description**: Retrieves a paginated list of tickets scoped to the authenticated caller's role and ownership.
  - `ROLE_EMPLOYEE`: Returns tickets created by the caller.
  - `ROLE_ENGINEER`: Returns tickets assigned to the caller.
  - `ROLE_MANAGER`: Returns tickets assigned to the manager's team.
  - `ROLE_ADMIN`: Returns all tickets.
- **Authentication**: Bearer JWT
- **Query Parameters**:
  - `page`: Page index (zero-based, default `0`).
  - `size`: Page size (default `10`, max `100`).
  - `sort`: Field and direction, e.g., `createdAt,desc` or `priority,asc` (default: `createdAt,desc`).
  - `status`: Optional filter by `TicketStatus` enum (e.g., `OPEN`, `IN_PROGRESS`).
  - `priority`: Optional filter by `Priority` enum (e.g., `HIGH`, `CRITICAL`).
  - `category`: Optional filter by `Category` enum (e.g., `VPN`, `HARDWARE`).
- **Example Request**:
  ```http
  GET /api/tickets/my?page=0&size=10&sort=createdAt,desc&status=OPEN
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "content": [
      {
        "id": 101,
        "title": "Cannot connect to Cisco AnyConnect VPN",
        "category": "VPN",
        "priority": "HIGH",
        "status": "OPEN",
        "requesterName": "John Doe",
        "assignedEngineerName": null,
        "teamName": null,
        "slaDeadline": "2026-10-01T23:30:00",
        "createdAt": "2026-10-01T19:30:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
  ```

---

#### `GET /api/tickets/{id}`
- **Description**: Retrieves detailed information for a specific ticket. Enforces ownership/role checks.
- **Authentication**: Bearer JWT
- **Response (`200 OK`)**: Full `TicketResponse` object.
- **Error Codes**:
  - `403 Forbidden`: Authenticated user is not authorized to view this ticket.
  - `404 Not Found`: Ticket with the specified ID does not exist.

---

#### `PATCH /api/tickets/{id}`
- **Description**: Partially updates permitted fields on an existing ticket.
  - Employees can modify `title`, `description`, `category`, and `priority` only when ticket is in `OPEN` status.
  - Engineers, Managers, and Admins can update ticket attributes according to their operational scopes.
- **Authentication**: Bearer JWT
- **Request Body**:
  ```json
  {
    "title": "Cannot connect to Cisco VPN after OS update",
    "description": "Updated details: Issue started after KB5034441 update.",
    "category": "VPN",
    "priority": "CRITICAL"
  }
  ```
- **Response (`200 OK`)**: Updated `TicketResponse`.

---

#### `PATCH /api/tickets/{id}/status`
- **Description**: Progresses the ticket lifecycle through allowed workflow state transitions. Automatically records a new `TicketStatusHistory` audit record.
- **Authentication**: Bearer JWT
- **Request Body**:
  ```json
  {
    "status": "IN_PROGRESS",
    "reason": "Engineer has begun adapter diagnostic tests",
    "resolutionDescription": null
  }
  ```
- **Response (`200 OK`)**: Updated `TicketResponse` reflecting the new status and resolution timestamps (if resolved).
- **Error Codes**:
  - `400 Bad Request`: Invalid transition path (e.g., `OPEN` ➔ `CLOSED`).
  - `403 Forbidden`: Caller role not authorized for this transition.

---

#### `PATCH /api/tickets/{id}/assignment`
- **Description**: Assigns or reassigns an engineer and/or team to a ticket. Automatically updates status to `ASSIGNED` if ticket was `OPEN`, and logs an immutable `TicketAssignment` history event.
- **Authentication**: Bearer JWT (`ROLE_MANAGER`, `ROLE_ADMIN`, or `ROLE_ENGINEER` self-assign)
- **Validation**:
  - Assigned user must exist and have the `ROLE_ENGINEER` authority.
  - Assigned engineer must be active (`isActive = true`).
- **Request Body**:
  ```json
  {
    "engineerId": 2,
    "teamId": 1,
    "notes": "Assigned to Network Operations tier 2"
  }
  ```
- **Response (`200 OK`)**: Updated `TicketResponse` with `assignedEngineer` populated and status set to `ASSIGNED`.

---

#### `POST /api/tickets/{id}/comments`
- **Description**: Appends a comment or technical note to a ticket.
  - `isInternal = true`: Internal technical notes visible only to engineers, managers, and admins. Automatically forced to `false` if submitted by standard employees.
- **Authentication**: Bearer JWT (Must have access to the ticket)
- **Request Body**:
  ```json
  {
    "content": "Rebooted virtual TAP adapter driver; testing handshake now.",
    "isInternal": true
  }
  ```
- **Response (`201 Created`)**:
  ```json
  {
    "id": 55,
    "ticketId": 101,
    "author": {
      "id": 2,
      "name": "Jane Engineer",
      "email": "engineer@techconnect.com",
      "role": "ROLE_ENGINEER"
    },
    "content": "Rebooted virtual TAP adapter driver; testing handshake now.",
    "isInternal": true,
    "createdAt": "2026-10-01T20:15:00"
  }
  ```

---

#### `GET /api/tickets/{id}/comments`
- **Description**: Lists comments for a ticket. Non-internal staff (`ROLE_EMPLOYEE`) automatically receive only public comments (`isInternal = false`).
- **Authentication**: Bearer JWT (Authorized users)
- **Response (`200 OK`)**: Array of `TicketCommentResponse` objects.

---

#### `GET /api/tickets/{id}/history`
- **Description**: Retrieves the complete audit history of all status changes for the ticket.
- **Authentication**: Bearer JWT
- **Response (`200 OK`)**:
  ```json
  [
    {
      "id": 12,
      "ticketId": 101,
      "oldStatus": "OPEN",
      "newStatus": "ASSIGNED",
      "changedBy": {
        "id": 3,
        "name": "Sarah Manager",
        "email": "manager@techconnect.com",
        "role": "ROLE_MANAGER"
      },
      "changeReason": "Assigned to engineer Jane Engineer",
      "changedAt": "2026-10-01T19:45:00"
    }
  ]
  ```

---

#### `GET /api/tickets/{id}/assignments`
- **Description**: Retrieves the historical timeline of engineer and team dispatches for the ticket.
- **Authentication**: Bearer JWT
- **Response (`200 OK`)**: Array of `TicketAssignmentResponse` objects.

---

## 5. SLA Management & Automation Engine APIs (Phase 6)

### 5.1 Overview & SLA Policy Rules

Service Level Agreements (SLAs) enforce operational milestones for IT incident resolution. Policies are configured dynamically per priority:

| Priority | First Response SLA | Resolution SLA | Warning Threshold (At Risk) |
| :--- | :--- | :--- | :--- |
| **`CRITICAL`** | **1 Hour** | **2 Hours** | Remaining time ≤ 1h (or ≤ 20%) |
| **`HIGH`** | **4 Hours** | **24 Hours** | Remaining time ≤ 5h (or ≤ 20%) |
| **`MEDIUM`** | **8 Hours** | **48 Hours** | Remaining time ≤ 10h (or ≤ 20%) |
| **`LOW`** | **24 Hours** | **72 Hours** | Remaining time ≤ 14h (or ≤ 20%) |

#### SLA Statuses:
- `ON_TRACK`: More than 20% of policy duration remains.
- `AT_RISK`: Less than or equal to 20% of policy duration remains.
- `BREACHED`: Milestone deadline has passed without completion.
- `PAUSED`: Timer is temporarily frozen while awaiting user clarification (`WAITING_FOR_USER`).
- `COMPLETED`: Milestone was successfully achieved within deadline.

---

### 5.2 SLA Endpoints

#### `GET /api/tickets/{id}/sla`
- **Description**: Returns dynamic SLA calculations, remaining times, and objective states for a specific ticket.
- **Authentication**: Bearer JWT
- **Authorization**:
  - `ROLE_EMPLOYEE`: Permitted only for tickets they requested.
  - `ROLE_ENGINEER`: Permitted for tickets assigned to them.
  - `ROLE_MANAGER`: Permitted for tickets belonging to their team/department.
  - `ROLE_ADMIN`: Permitted for all enterprise tickets.
- **Response (`200 OK`)**:
  ```json
  {
    "ticketId": 101,
    "priority": "HIGH",
    "status": "IN_PROGRESS",
    "responseDeadline": "2026-10-01T23:30:00",
    "resolutionDeadline": "2026-10-02T19:30:00",
    "responseStatus": "COMPLETED",
    "resolutionStatus": "ON_TRACK",
    "overallStatus": "ON_TRACK",
    "respondedAt": "2026-10-01T20:15:00",
    "resolvedAt": null,
    "remainingResponseMinutes": 0,
    "remainingResolutionMinutes": 1395,
    "isPaused": false,
    "slaPausedAt": null,
    "totalPausedDurationMinutes": 0
  }
  ```
- **Error Codes**:
  - `403 Forbidden`: Authenticated user is not authorized to inspect this ticket's SLA.
  - `404 Not Found`: Ticket with specified ID does not exist.

---

#### `GET /api/sla/breached`
- **Description**: Returns all currently breached tickets across the enterprise (where either response or resolution SLA has failed).
- **Authentication**: Bearer JWT (`ROLE_MANAGER`, `ROLE_ADMIN`)
- **Response (`200 OK`)**:
  ```json
  [
    {
      "ticketId": 105,
      "title": "VPN Gateway authentication failure",
      "priority": "CRITICAL",
      "status": "OPEN",
      "responseDeadline": "2026-10-01T15:00:00",
      "resolutionDeadline": "2026-10-01T17:00:00",
      "responseStatus": "BREACHED",
      "resolutionStatus": "BREACHED",
      "remainingResolutionMinutes": 0,
      "breachedAt": "2026-10-01T15:00:00",
      "breachType": "BOTH"
    }
  ]
  ```
- **Error Codes**:
  - `403 Forbidden`: Access denied for `ROLE_EMPLOYEE` or `ROLE_ENGINEER`.

---

#### `GET /api/sla/summary`
- **Description**: Aggregates enterprise-wide SLA performance metrics for executive and operational dashboards.
- **Authentication**: Bearer JWT (`ROLE_MANAGER`, `ROLE_ADMIN`)
- **Response (`200 OK`)**:
  ```json
  {
    "totalActiveTickets": 45,
    "onTrack": 35,
    "atRisk": 6,
    "breached": 4,
    "responseSlaMet": 82,
    "responseSlaBreached": 7,
    "resolutionSlaMet": 75,
    "resolutionSlaBreached": 5
  }
  ```
---

## 4. Frontend Client Integration & CORS

### 4.1 CORS Policy
The TechConnect Spring Boot backend configures CORS in `com.techconnect.config.SecurityConfig`:
- **Allowed Origins**: Injected via property `techconnect.cors.allowed-origins` (defaults to `http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000`). Wildcard `*` is not used in order to maintain secure origin boundaries.
- **Allowed Methods**: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`
- **Allowed Headers**: `Authorization`, `Content-Type`, `Accept`, `Origin`, `X-Requested-With`
- **Max Age**: `3600` seconds

### 4.2 React Client Consumption & Reliability
- Centralized Axios client (`frontend/src/services/api.js`) applies `import.meta.env.VITE_API_BASE_URL`.
- Automatically attaches `Authorization: Bearer <token>` for all authenticated requests when a valid token exists.
- On `401 Unauthorized` responses (excluding auth endpoints `/auth/login` and `/auth/register`), clears local token storage and dispatches `techconnect:auth-expired` to redirect to `/login` with friendly session-expired toast notifications.
- Handles network failures, timeouts, and backend outages gracefully via `extractErrorMessage()`, returning `"Unable to connect to TechConnect server. Please ensure the backend is running."` instead of raw Axios error traces.
- All mutating actions (Ticket submission, self-assignment, status change, comments) feature UI loading spinners and button disablement to prevent duplicate submissions.



