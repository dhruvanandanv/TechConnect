# TechConnect REST API Documentation

## 1. Overview
All REST APIs communicate via JSON over HTTP/HTTPS. Base URL: `/api`.

## 2. API Endpoints by Phase

### Phase 1: Foundation
- **`GET /api/health`**
  - **Description**: Returns backend health and operational status.
  - **Auth**: Public
  - **Response Status**: `200 OK`
  - **Sample Response**:
    ```text
    TechConnect Backend is running!
    ```

### Upcoming Endpoints (Phases 2-9)
- **Auth**:
  - `POST /api/auth/register` - Create new user
  - `POST /api/auth/login` - Authenticate & obtain JWT
- **Tickets**:
  - `GET /api/tickets` - List/filter tickets (paginated)
  - `POST /api/tickets` - Create ticket
  - `GET /api/tickets/{id}` - Get ticket details
  - `PATCH /api/tickets/{id}/status` - Advance ticket state
  - `POST /api/tickets/{id}/comments` - Add comment
- **Admin & Manager**:
  - `GET /api/manager/workload` - Team workload summary
  - `GET /api/admin/users` - User directory
- **AI Service Integration**:
  - `POST /api/ai/analyze-ticket` - Automated category & priority inference
