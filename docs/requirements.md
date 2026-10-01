# TechConnect Requirements Specification

## 1. Project Goal
TechConnect is an enterprise IT Service Management (ITSM) application designed to automate incident tracking, streamline resolution workflows, monitor SLA commitments, and provide AI-driven assistance to IT operations teams.

## 2. User Roles & Permissions
- **EMPLOYEE**:
  - Authenticate (Register/Login).
  - Submit technical support tickets with category, priority, and attachments.
  - Track progress and status transitions of submitted tickets.
  - Add clarifying comments and rate resolutions.
  - Search internal knowledge base articles.
- **ENGINEER**:
  - View assigned tickets queue.
  - Claim tickets and update status (`IN_PROGRESS`, `WAITING_FOR_USER`, `RESOLVED`).
  - View AI triage recommendations and suggested resolutions.
  - Post technical notes and resolution descriptions.
- **MANAGER**:
  - View team ticket queue and reassign tickets based on workload.
  - Monitor SLA compliance metrics and breach warnings.
  - Handle escalated tickets and trigger managerial reviews.
  - Review operational team analytics.
- **ADMIN**:
  - Manage users, roles, and department hierarchies.
  - Configure SLA rules (turnaround thresholds per priority).
  - Manage ticket categories and support teams.
  - Audit system logs and access controls.

## 3. Ticket Lifecycle State Machine
```
OPEN -> ASSIGNED -> IN_PROGRESS -> WAITING_FOR_USER -> RESOLVED -> CLOSED
                         |
                         +-> ESCALATED -> MANAGER_REVIEW
```
- Arbitrary transitions are prohibited. Backend validates each transition against allowed paths.

## 4. SLA Policies
- **CRITICAL**: 2 hours
- **HIGH**: 4 hours
- **MEDIUM**: 8 hours
- **LOW**: 24 hours
- Dynamic calculation based on creation timestamp and priority. Monitored for proactive alert generation.
