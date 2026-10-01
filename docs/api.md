# TechConnect REST API Documentation

## 1. Overview
All REST APIs communicate via JSON over HTTP/HTTPS with UTF-8 encoding.
Base URL: `/api`

Consistent Error Structure:
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

## 2. API Endpoints

### Phase 1: System Health

#### `GET /api/health`
- **Description**: Returns backend operational status.
- **Authentication**: Public
- **HTTP Status**: `200 OK`
- **Response**:
  ```text
  TechConnect Backend is running!
  ```

---

### Phase 3: Authentication & User Registration

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
      "message": "Public registration cannot assign administrative or managerial roles: ROLE_ADMIN",
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
- **Description**: Verifies user email and BCrypt-hashed password. Returns safe user details without credentials. (JWT generation will be integrated in Phase 4).
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

### Upcoming Endpoints (Phases 4-14)
- **Phase 4**: Spring Security JWT Filter, Bearer Token issuance (`/api/auth/login` returning JWT), Token Refresh.
- **Phase 5**: Tickets CRUD (`/api/tickets/**`), status transitions (`PATCH /api/tickets/{id}/status`), comments (`/api/tickets/{id}/comments`).
- **Phase 6**: SLA management and breach tracking endpoints.
- **Phase 9**: In-app notifications (`/api/notifications/**`).
- **Phase 10**: Python AI Triage integration (`/api/ai/analyze-ticket`).
