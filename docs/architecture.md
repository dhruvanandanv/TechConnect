# TechConnect System Architecture

## 1. High-Level Architecture Overview
TechConnect follows a microservice-adjacent modular architecture with clear separation of concerns:
1. **Presentation Layer**: React Single Page Application (SPA) leveraging modern component design, responsive styling, and Axios client.
2. **Core Business Layer**: Spring Boot 3.x RESTful backend managing business logic, state machines, SLA engines, and access control.
3. **AI & RAG Engine**: Python FastAPI microservice dedicated to NLP classification, vector search, and LLM-assisted resolution synthesis.
4. **Data Persistence**:
   - **PostgreSQL**: Relational database for ACID transactions (Users, Tickets, Status History, SLA, Audits).
   - **MongoDB**: Document database for unstructured/semi-structured Knowledge Base articles and AI prompt/inference logs.

## 2. Backend Package Structure (Spring Boot)
The backend follows clean layered architecture:
```
com.techconnect/
├── config/             # Spring configuration (CORS, Security, OpenApi)
├── controller/         # REST Controllers exposing HTTP endpoints
├── dto/                # Request & Response Data Transfer Objects
├── entity/             # JPA entity models mapping relational tables
├── exception/          # GlobalExceptionHandler and custom exceptions
├── mapper/             # Entity-DTO mapping layer
├── repository/         # Spring Data JPA repositories
├── security/           # JWT filters, UserDetailsService, Auth providers
└── service/            # Business logic, SLA tracking, state transitions
```

## 3. Communication Patterns
- **Frontend <-> Backend**: HTTPS REST calls using JSON with Bearer JWT tokens.
- **Backend <-> PostgreSQL**: JDBC connection pooling via HikariCP + Spring Data JPA / Hibernate.
- **Backend <-> AI Service**: Internal synchronous HTTP calls via Spring's `RestClient` (`POST /api/v1/ticket-intelligence/analyze`) with configured timeouts and graceful fallback when unavailable.

## 4. AI Ticket Intelligence Microservice (Phase 9)
```
React Frontend (Analyze Button)
       |
       | REST + Bearer JWT
       v
Spring Boot TicketController (POST /api/tickets/analyze)
       |
       | RestClient (timeout 3000ms)
       v
Python FastAPI Microservice (POST /api/v1/ticket-intelligence/analyze)
       |
       +---> TF-IDF + LogisticRegression Classifier (Category Prediction)
       +---> Deterministic Rules Engine (Priority Urgency Inference)
       +---> Organizational Team Routing Matrix (Suggested Support Team)
       +---> Deterministic Summarizer (ITSM Incident Summary)
```
- **Predictive Assistance Only**: Human requester and engineer decisions remain strictly authoritative.
- **Circuit Fallback**: Failure of the AI microservice returns `aiAvailable: false` without impeding standard ticket creation.

