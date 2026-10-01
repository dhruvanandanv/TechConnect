# TechConnect Database Design

## 1. Dual Database Strategy
TechConnect uses a hybrid persistence model:
- **PostgreSQL**: Chosen for core business entities requiring strict transactional integrity (ACID), foreign-key relational constraints, predictable schemas, and audit compliance (tickets, assignments, SLA state machine).
- **MongoDB**: Introduced for flexible, semi-structured data including Knowledge Base articles (which have dynamic tags, rich text blocks, and version history) and AI vector embeddings/inference contexts.

## 2. Core Relational Entities (PostgreSQL)
1. **User**: `id`, `email`, `password_hash`, `first_name`, `last_name`, `role_id`, `department_id`, `created_at`, `updated_at`
2. **Role**: `id`, `name` (`EMPLOYEE`, `ENGINEER`, `MANAGER`, `ADMIN`)
3. **Department**: `id`, `name`, `code`
4. **Team**: `id`, `name`, `department_id`
5. **Ticket**: `id`, `title`, `description`, `category`, `priority`, `status`, `created_by_user_id`, `assigned_engineer_id`, `assigned_team_id`, `department_id`, `sla_deadline`, `resolved_at`, `created_at`, `updated_at`
6. **TicketComment**: `id`, `ticket_id`, `user_id`, `comment_text`, `is_internal`, `created_at`
7. **TicketStatusHistory**: `id`, `ticket_id`, `changed_by_user_id`, `old_status`, `new_status`, `change_reason`, `created_at`
8. **TicketAttachment**: `id`, `ticket_id`, `file_name`, `file_type`, `file_url`, `created_at`
9. **SLA**: `id`, `priority`, `resolution_time_hours`, `warning_threshold_hours`
10. **Notification**: `id`, `user_id`, `title`, `message`, `is_read`, `created_at`
11. **Feedback**: `id`, `ticket_id`, `rating`, `comments`, `created_at`
12. **AuditLog**: `id`, `action`, `entity_name`, `entity_id`, `user_id`, `details`, `timestamp`
