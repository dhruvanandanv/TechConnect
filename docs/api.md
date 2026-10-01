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
