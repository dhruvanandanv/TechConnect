# TechConnect

**TechConnect** is an enterprise-grade AI-Powered IT Service Management (ITSM) Platform. It streamlines technical support workflows across organizations with automated ticket lifecycle management, strict SLA governance, real-time tracking, and AI-assisted triage and resolution.

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Key Features](#key-features)
3. [Technology Stack](#technology-stack)
4. [System Architecture](#system-architecture)
5. [Project Structure](#project-structure)
6. [Prerequisites](#prerequisites)
7. [Running Locally](#running-locally)
8. [API Endpoints](#api-endpoints)
9. [Development Roadmap](#development-roadmap)

---

## Project Overview
TechConnect bridges the gap between employees experiencing technical bottlenecks (e.g., VPN issues, hardware failures, access controls) and IT support teams. It enforces role-based access control (Employee, Engineer, Manager, Admin), monitors SLA compliance with automated breach detection, and integrates AI assistance for intelligent category prediction and RAG-grounded troubleshooting.

---

## Key Features
- **Ticket Lifecycle Management**: Deterministic state transitions from `OPEN` to `ASSIGNED`, `IN_PROGRESS`, `WAITING_FOR_USER`, `RESOLVED`, and `CLOSED`, with escalation support.
- **SLA Engine**: Configurable, priority-based resolution deadlines (Critical: 2h, High: 4h, Medium: 8h, Low: 24h).
- **Role-Based Portals**: Dedicated operational interfaces for Employees, Engineers, Managers, and Administrators.
- **AI Triage & Copilot**: Automated category/priority tagging and RAG-based resolution recommendations grounded in knowledge base articles.
- **Dual Database Architecture**: PostgreSQL for ACID transactional data + MongoDB for polymorphic knowledge articles and vector embeddings.
- **Enterprise Analytics**: Workload monitoring and Power BI connectivity for SLA and operational visibility.

---

## Technology Stack

| Layer | Technologies |
|---|---|
| **Frontend** | React.js, JavaScript, HTML5, CSS3, Bootstrap, React Router, Axios |
| **Backend** | Java 21, Spring Boot 3.3.x, Spring Data JPA, Spring Security, JWT, Lombok, Maven |
| **Databases** | PostgreSQL (Relational/Transactional), MongoDB (Knowledge Base/AI Docs) |
| **AI Service** | Python 3.11+, FastAPI, NLP, Embeddings, RAG, Vector Search |
| **DevOps & Tools** | Docker, Docker Compose, Git, Postman, Power BI |

---

## System Architecture

```
                  +--------------------------+
                  |  React Frontend Client   |
                  |  (Employee / Eng / Mgr)  |
                  +-------------+------------+
                                |
                           REST / JWT
                                |
                                v
                  +-------------+------------+
                  |    Spring Boot Backend   |
                  |     (Business Logic)     |
                  +-------+----------+-------+
                          |          |
         +----------------+          +----------------+
         |                                            |
         v                                            v
+------------------+                        +------------------+
|    PostgreSQL    |                        |   FastAPI (AI)   |
| (Tickets, Users, |                        |  (Triage & RAG)  |
|  SLAs, Audit)    |                        +--------+---------+
+------------------+                                 |
                                                     v
                                            +------------------+
                                            |     MongoDB      |
                                            | (Knowledge Base) |
                                            +------------------+
```

---

## Project Structure

```
TechConnect/
├── backend/            # Spring Boot application
├── frontend/           # React dashboard SPA
├── ai_services/        # Python FastAPI AI and RAG engine
├── docker/             # Docker configurations & container manifests
├── docs/               # Architecture, API, and DB documentation
│   ├── requirements.md
│   ├── architecture.md
│   ├── database.md
│   └── api.md
├── .env.example        # Environment variable blueprint
├── .gitignore          # Version control ignore rules
├── docker-compose.yml  # Multi-service composition
└── README.md           # Project documentation
```

---

## Phase 1 Status
- **Backend Foundation**: Active (Spring Boot, Health Endpoint `GET /api/health`).
