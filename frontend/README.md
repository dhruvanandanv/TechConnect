# TechConnect Frontend Application

React-based Enterprise IT Service Management (ITSM) web portal consuming the Spring Boot REST API.

## 1. Overview
The TechConnect frontend delivers an enterprise dashboard and ticket management experience tailored to corporate roles:
- **Employee**: Self-service ticket logging, tracking status, reviewing resolution details, adding public comments, and confirming closure.
- **IT Support Engineer**: Managing assigned tickets, updating workflow status (In Progress, Waiting on User, Resolved, Escalated), adding internal notes, and self-assigning unassigned tickets.
- **Support Manager**: Overseeing team workload, reassigning tickets, and monitoring SLA compliance and breaches.
- **System Administrator**: Full platform visibility across all tickets, SLA metrics, and operational governance.

## 2. Technology Stack
- **Framework**: React 19 (JavaScript) with Vite
- **Routing**: React Router v7
- **HTTP Client**: Axios with centralized interceptors
- **Styling**: Bootstrap 5 + Bootstrap Icons + Custom Enterprise Design System
- **State Management**: React Context API (`AuthContext`)

## 3. Getting Started

### Prerequisites
- Node.js (v18+)
- npm (v9+)
- TechConnect Spring Boot Backend running on `http://localhost:8080`

### Installation
```bash
cd frontend
npm install
```

### Environment Configuration
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Default configuration:
```env
VITE_API_BASE_URL=http://localhost:8080/api
```

### Development Server
```bash
npm run dev
```
The application will launch on `http://localhost:5173`.

### Production Build
```bash
npm run build
```
The compiled output is located in `dist/`.

## 4. Key Directory Structure
```
frontend/
├── src/
│   ├── components/       # Reusable components (Navbar, Sidebar, Badges, Loaders)
│   ├── context/          # AuthContext for session management
│   ├── pages/            # Role-scoped Dashboard, Tickets, TicketDetails, SLA, Profile, Auth
│   ├── services/         # Axios API client, authService, ticketService, slaService
│   ├── utils/            # Date and enum formatting helpers
│   ├── App.jsx           # Route declarations and layout wrappers
│   ├── index.css         # Enterprise styling & Bootstrap tokens
│   └── main.jsx          # React DOM entry point
├── .env.example
├── package.json
└── README.md
```
