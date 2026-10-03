# TechConnect Resume Project Description & Portfolio Highlights

This document provides formatted project descriptions, resume bullet variations, technical summaries, and interview talking points for showcasing **TechConnect — AI-Powered Enterprise IT Service Management Platform** on software engineering resumes and portfolio profiles.

---

## 1. One-Line Project Description

> **TechConnect**: Enterprise-grade IT Service Management (ITSM) and ticketing platform built with Java 21 Spring Boot, React 19, Python FastAPI, PostgreSQL (pgvector), and MongoDB, featuring automated SLA governance, RAG-grounded AI copilot assistance, and containerized deployment.

---

## 2. 3 High-Impact Resume Bullets (Compact Format)

- **Architected a full-stack enterprise ITSM platform** using Java 21, Spring Boot 3.3, and React 19, implementing deterministic ticket state transitions, role-based access control (RBAC), and automated priority-based SLA calculation with breach tracking.
- **Engineered a polyglot persistence architecture** leveraging PostgreSQL for ACID transactional integrity and 384-dimensional vector embeddings via `pgvector` (HNSW indexing), alongside MongoDB for polymorphic knowledge base document storage.
- **Built a grounded RAG AI copilot & resolution assistant** using Python FastAPI, SentenceTransformers, and anti-hallucination guardrails, reducing manual troubleshooting time with zero autonomous database mutation and full source citations.

---

## 3. 5 Comprehensive Resume Bullets (Detailed Format)

- **Engineered an enterprise IT Service Management platform** in Java 21 and Spring Boot 3.3, supporting 4 user roles (Employee, Engineer, Manager, Admin) with stateless JWT authentication, sliding-window rate limiting, and defensive IDOR authorization gates.
- **Developed a dynamic SLA automation engine** calculating response and resolution deadlines across 4 incident priorities (Critical: 2h, High: 4h, Medium: 8h, Low: 24h), with dynamic clock pause/resume triggers for user-pending states.
- **Implemented a polyglot persistence layer** combining PostgreSQL for relational data and dense vector chunk storage via `pgvector` (HNSW graphs), with MongoDB for rich Markdown knowledge articles, version history, and user feedback metrics.
- **Constructed a 4-stage Retrieval-Augmented Generation (RAG) pipeline** in Python FastAPI using `all-MiniLM-L6-v2` embeddings, strict context budgeting (4,000 chars), and prompt injection defenses, delivering verifiable technical solutions with cited sources.
- **Containerized the multi-service ecosystem** using multi-stage Docker builds with non-root security (`UID 1001`), container-aware JVM tuning, Actuator health probes (`show-details=never`), and Docker Compose dependency orchestration across 5 microservices.

---

## 4. Technical Project Summary

| Dimension | Details |
|:---|:---|
| **Core Backend** | Java 21, Spring Boot 3.3.x, Spring Data JPA, Spring Security 6, JWT, Lombok, Maven |
| **Frontend SPA** | React 19, Vite, Bootstrap 5, Bootstrap Icons, React Router 6, Axios, Reusable Modular Components |
| **AI Microservice** | Python 3.12, FastAPI, PyTorch, SentenceTransformers (`all-MiniLM-L6-v2`), Scikit-Learn (TF-IDF & Logistic Regression), Uvicorn |
| **Databases** | PostgreSQL 16 (`pgvector` extension for 384-dim HNSW vector search), MongoDB 7.0 (Knowledge Base document store) |
| **DevOps & Infrastructure**| Docker, Docker Compose, Multi-stage builds, Nginx reverse proxy, Actuator probes, HikariCP connection pooling |
| **Security & Hardening** | BCrypt password hashing, sliding-window rate limiting (20/60/120 rpm), IDOR assertions, HTTP security headers (`DENY`, `nosniff`), log sanitization |
| **Testing & Quality** | 196 Spring Boot integration/unit tests (100% pass), 71 Python ML/RAG pytest tests (100% pass), automated frontend bundle verification |

---

## 5. 30-Second Interview Introduction (Self-Pitch)

> "One of my flagship engineering projects is TechConnect, an AI-powered enterprise IT Service Management platform. I built it to solve the real-world operational challenges of ticket backlogs and SLA breaches while maintaining zero tolerance for AI hallucinations.
> 
> I designed the system with a polyglot architecture: Spring Boot handles transactional ticketing and SLA automation on PostgreSQL, while MongoDB stores knowledge base SOPs. For the AI assistance, I built a Python microservice with SentenceTransformers and pgvector that performs grounded semantic retrieval. Rather than letting the AI take unvetted actions, it generates verifiable answers citing exact internal documentation and assists engineers by proposing grounded resolutions that require human verification before submission.
> 
> The platform is fully containerized with multi-stage Docker builds, non-root users, in-memory rate limiting, and comprehensive automated test suites."
