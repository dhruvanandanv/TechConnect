# TechConnect Frontend Architecture Documentation

## 1. Overview
The TechConnect frontend is a single-page application (SPA) built using React, Vite, React Router, Axios, and Bootstrap 5. It consumes the TechConnect Spring Boot REST APIs to provide an enterprise-grade IT Service Management (ITSM) experience.

---

## 2. Directory Architecture

```
frontend/
├── src/
│   ├── components/
│   │   ├── ErrorAlert.jsx          # Dismissible bootstrap alert with icon
│   │   ├── LoadingSpinner.jsx      # Center-aligned spinner with message
│   │   ├── Navbar.jsx              # Top header with branding and user menu
│   │   ├── PriorityBadge.jsx       # Visual indicators for LOW, MEDIUM, HIGH, CRITICAL
│   │   ├── ProtectedRoute.jsx      # Route guard redirecting to /login if unauthenticated
│   │   ├── RoleGuard.jsx           # Guard restricting managerial/admin routes
│   │   ├── Sidebar.jsx             # Left sidebar with role-aware navigation links
│   │   ├── SlaBadge.jsx            # Visual indicators for ON_TRACK, AT_RISK, BREACHED, PAUSED, COMPLETED
│   │   └── StatusBadge.jsx         # Badges for all 8 TicketStatus enum values
│   │
│   ├── context/
│   │   └── AuthContext.jsx         # Context provider for currentUser, token, and auth methods
│   │
│   ├── pages/
│   │   ├── CreateTicket.jsx        # Ticket submission form matching CreateTicketRequest
│   │   ├── Dashboard.jsx           # Role-aware dashboard (Employee, Engineer, Manager, Admin)
│   │   ├── Login.jsx               # Login form with error handling and demo credentials
│   │   ├── NotFound.jsx            # 404 handler
│   │   ├── Profile.jsx             # User attributes and role capability overview
│   │   ├── Register.jsx            # Employee self-registration
│   │   ├── SlaDashboard.jsx        # Manager & Admin SLA compliance dashboard
│   │   ├── TicketDetails.jsx       # Tabbed ticket view (Overview, Comments, History, Assignments)
│   │   └── Tickets.jsx             # Filterable and paginated ticket management table
│   │
│   ├── services/
│   │   ├── api.js                  # Centralized Axios instance with JWT interceptor
│   │   ├── authService.js          # Authentication API calls and token storage
│   │   ├── slaService.js           # SLA metric calculation and breached tickets APIs
│   │   └── ticketService.js        # Ticket CRUD, status update, assignment, and comment APIs
│   │
│   ├── utils/
│   │   └── formatters.js           # Date, duration, role, and enum label formatters
│   │
│   ├── App.css                     # Component utility helpers
│   ├── App.jsx                     # Route definitions and layout structure
│   ├── index.css                   # Enterprise design tokens and Bootstrap 5 styling
│   └── main.jsx                    # React application mount
│
├── .env.example                    # Sample environment variables
├── package.json                    # Project metadata and dependencies
└── README.md                       # Setup and running instructions
```

---

## 3. Authentication & JWT Handling

1. **Login Flow**:
   - The user inputs their email and password on `/login`.
   - The frontend calls `authService.login(email, password)` which invokes `POST /api/auth/login`.
   - On success, the backend returns:
     ```json
     {
       "success": true,
       "token": "eyJhbGciOi...",
       "tokenType": "Bearer",
       "expiresIn": 3600000,
       "user": {
         "id": 1,
         "name": "Alex Engineer",
         "email": "engineer@techconnect.com",
         "role": "ROLE_ENGINEER",
         "departmentName": "IT Operations & Infrastructure",
         "teamName": "Network Support"
       }
     }
     ```
   - The JWT token is saved to `localStorage` under `techconnect_token`, and the user object is saved under `techconnect_user`.
   - `AuthContext` updates its state, triggering a redirect to `/dashboard`.

2. **Bearer Token Injection**:
   - In `services/api.js`, an Axios request interceptor automatically retrieves `techconnect_token` from `localStorage` and appends:
     ```http
     Authorization: Bearer <token>
     ```

3. **Session Expiry & 401 Interception**:
   - When a request returns `401 Unauthorized`, the Axios response interceptor removes the token and user from `localStorage`, dispatches a `techconnect:auth-expired` window event, and redirects to `/login` with an informative banner.

4. **Security Isolation**:
   - Frontend route protection (`ProtectedRoute` and `RoleGuard`) provides a smooth user experience and hides unauthorized views.
   - The Spring Boot backend remains the single source of truth for authorization checks via `SecurityConfig` and method security annotations.

---

## 4. Role-Aware UI Matrix

| Role | Navigation Links | Dashboard View | Available Actions |
| :--- | :--- | :--- | :--- |
| **ROLE_EMPLOYEE** | Dashboard, New Ticket, My Tickets, Profile | Personal ticket counts (Open, In Progress, Awaiting Info, Resolved); Recent tickets | Create ticket, comment, close resolved tickets |
| **ROLE_ENGINEER** | Dashboard, New Ticket, Ticket Queue, My Assignments, Profile | Queue count, In Progress count, Waiting on User count, Escalated count | Self-assign open tickets, change status (In Progress, Waiting, Resolved, Escalated), post internal & public comments |
| **ROLE_MANAGER** | Dashboard, New Ticket, All Tickets, SLA Performance, Profile | Active team ticket count, SLA On Track, SLA At Risk, SLA Breached, response & resolution compliance | Reassign tickets, update status, view SLA dashboard and breached queues |
| **ROLE_ADMIN** | Dashboard, New Ticket, All Tickets, SLA Performance, Profile | System-wide active tickets, full SLA compliance rates, all ticket queues | Unrestricted assignment, status transition, governance review |

---

## 5. SLA Tracking Integration

- **Ticket Details**: Each ticket queries `GET /api/tickets/{id}/sla` to fetch real-time SLA metrics:
  - First Response Deadline & countdown in minutes.
  - Resolution Deadline & countdown in minutes.
  - Overall status (`ON_TRACK`, `AT_RISK`, `BREACHED`, `PAUSED`, `COMPLETED`).
  - Pause indicator and cumulative paused duration when status is `WAITING_FOR_USER`.
- **SLA Dashboard (`/sla`)**:
  - Accessible strictly to `ROLE_MANAGER` and `ROLE_ADMIN`.
  - Queries `GET /api/sla/summary` to display organization-wide compliance percentages.
  - Queries `GET /api/sla/breached` to display a prioritized list of breached tickets for urgent triage.
