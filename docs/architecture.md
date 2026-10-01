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
- **Backend <-> AI Service**: Internal REST client calls for automated ticket triage and RAG recommendations.
