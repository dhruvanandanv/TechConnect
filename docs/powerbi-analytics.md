# TechConnect Power BI Analytics & Reporting Specification

This document provides a comprehensive, production-ready specification and implementation guide for building enterprise Power BI dashboards on top of **TechConnect — AI-Powered Enterprise IT Service Management Platform**.

---

## 1. Environment Status & Tooling Context

- **Environment Evaluation**: Power BI Desktop (`PBIDesktop.exe`) is not locally installed on this host environment.
- **Implementation Strategy**: In accordance with Phase 15 requirements, this guide delivers:
  1. A complete **Star Schema Data Model** mapping TechConnect's PostgreSQL relational schema.
  2. Exact **SQL Queries & REST Data Connectors** for importing ITSM and Knowledge Base data.
  3. Formulated **DAX Measures** for all key performance indicators.
  4. Detailed layout specifications for all **5 Recommended Dashboard Pages**.
  5. Step-by-step reproduction runbooks for deploying to Power BI Desktop and Power BI Service.

---

## 2. Power BI Data Architecture & Star Schema

TechConnect's operational schema maps directly into an analytical Star Schema optimized for fast Power BI aggregation:

```
                            ┌────────────────────────┐
                            │      Dim_Date          │
                            │  Date, Year, Month,    │
                            │  Quarter, DayOfWeek    │
                            └───────────┬────────────┘
                                        │ 1:N
                                        ▼
┌────────────────────────┐  ┌────────────────────────┐  ┌────────────────────────┐
│      Dim_User          │  │      Fact_Tickets      │  │     Dim_Category       │
│  User_ID, Name, Email, ├──┤  Ticket_ID (PK)        ├──┤  Category_ID, Name,   │
│  Role, Department, Team│  │  Created_Date (FK)     │  │  Description           │
└────────────────────────┘  │  Resolved_Date (FK)    │  └────────────────────────┘
                            │  Requester_ID (FK)     │
┌────────────────────────┐  │  Engineer_ID (FK)      │  ┌────────────────────────┐
│      Dim_Priority      ├──┤  Category_ID (FK)      ├──┤      Dim_Status        │
│  Priority_ID, Name,    │  │  Priority_ID (FK)      │  │  Status_ID, Name,      │
│  SLA_Target_Hours      │  │  Status_ID (FK)        │  │  Is_Closed_Flag        │
└────────────────────────┘  │  Resolution_Time_Hours │  └────────────────────────┘
                            │  SLA_Breached_Flag     │
                            └───────────┬────────────┘
                                        │ 1:N
                                        ▼
                            ┌────────────────────────┐
                            │    Fact_SLA_Events     │
                            │  Event_ID, Ticket_ID,  │
                            │  Milestone_Type, Met?  │
                            └────────────────────────┘
```

---

## 3. Data Ingestion & SQL Extraction Queries

In Power BI Desktop, navigate to **Home -> Get Data -> PostgreSQL Database**:
- **Server**: `localhost:5432` (or Docker host IP)
- **Database**: `techconnect_db`
- **Data Connectivity Mode**: `Import` (recommended for responsive caching) or `DirectQuery` (for real-time SLA alerting)

### Query 1: `Fact_Tickets`
```sql
SELECT 
    t.id AS ticket_id,
    t.title,
    t.category,
    t.priority,
    t.status,
    t.created_by_id AS requester_id,
    t.assigned_engineer_id AS engineer_id,
    t.assigned_team_id AS team_id,
    t.created_at,
    CAST(t.created_at AS DATE) AS created_date,
    t.resolved_at,
    CAST(t.resolved_at AS DATE) AS resolved_date,
    t.sla_deadline,
    CASE 
        WHEN t.resolved_at IS NOT NULL THEN 
            ROUND(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0, 2)
        ELSE NULL 
    END AS resolution_time_hours,
    CASE 
        WHEN t.status NOT IN ('RESOLVED', 'CLOSED') AND t.sla_deadline < NOW() THEN 1
        WHEN t.resolved_at IS NOT NULL AND t.resolved_at > t.sla_deadline THEN 1
        ELSE 0 
    END AS is_sla_breached
FROM tickets t;
```

### Query 2: `Dim_Engineers`
```sql
SELECT 
    u.id AS engineer_id,
    CONCAT(u.first_name, ' ', u.last_name) AS engineer_name,
    u.email AS engineer_email,
    d.name AS department_name,
    tm.name AS team_name
FROM users u
JOIN roles r ON u.role_id = r.id
LEFT JOIN departments d ON u.department_id = d.id
LEFT JOIN teams tm ON u.team_id = tm.id
WHERE r.name = 'ROLE_ENGINEER';
```

### Query 3: `Fact_Knowledge_Articles` (via REST API or MongoDB Connector)
Using Power BI **Get Data -> Web** querying `http://localhost:8080/api/analytics/overview`:
- Pulls aggregated metrics: `totalKnowledgeArticles`, `publishedKnowledgeArticles`, `totalKnowledgeArticleViews`, `totalKnowledgeArticleHelpfulVotes`.

---

## 4. Key DAX Formulations

Enter these DAX measures into a dedicated `_Measures` table in Power BI:

### Total & Active Ticket Volumes
```dax
Total Tickets = COUNTROWS(Fact_Tickets)

Open Tickets = 
CALCULATE(
    COUNTROWS(Fact_Tickets),
    Fact_Tickets[status] IN {"OPEN", "ASSIGNED", "IN_PROGRESS", "WAITING_FOR_USER", "ESCALATED"}
)

Resolved Tickets = 
CALCULATE(
    COUNTROWS(Fact_Tickets),
    Fact_Tickets[status] IN {"RESOLVED", "CLOSED"}
)
```

### SLA Compliance %
```dax
SLA Breached Tickets = 
CALCULATE(
    COUNTROWS(Fact_Tickets),
    Fact_Tickets[is_sla_breached] = 1
)

SLA Compliance % = 
VAR TotalEvaluated = [Total Tickets]
VAR Breached = [SLA Breached Tickets]
RETURN
IF(TotalEvaluated > 0, DIVIDE(TotalEvaluated - Breached, TotalEvaluated, 1), 1)
```

### Average Resolution Time
```dax
Avg Resolution Time (Hours) = 
AVERAGEX(
    FILTER(Fact_Tickets, NOT(ISBLANK(Fact_Tickets[resolution_time_hours]))),
    Fact_Tickets[resolution_time_hours]
)
```

### Engineer Workload
```dax
Active Engineer Workload = 
CALCULATE(
    [Open Tickets],
    USERELATIONSHIP(Fact_Tickets[engineer_id], Dim_Engineers[engineer_id])
)
```

---

## 5. Dashboard Page Specifications

### PAGE 1 — Executive Overview
- **Header Card 1**: `Total Tickets` (Formatted number: `1,248`).
- **Header Card 2**: `Open Tickets` (Primary Blue).
- **Header Card 3**: `Resolved Tickets` (Success Green).
- **Gauge Visual**: `SLA Compliance %` (Target: 95%, Green above 95%, Amber 90-95%, Red below 90%).
- **Card Visual**: `Avg Resolution Time (Hours)` (Formatted: `4.2 hrs`).
- **Trend Line Chart**: Daily ticket creation volume vs. resolution volume over the last 30 days.

### PAGE 2 — Ticket Analysis
- **Donut Chart**: Tickets by Priority (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`).
- **Bar Chart**: Tickets by Category (`NETWORK`, `HARDWARE`, `SOFTWARE`, `SECURITY`, `CLOUD_SERVICES`, `ACCESS_AND_IDENTITY`, `GENERAL_IT`).
- **Stacked Column Chart**: Ticket Status Distribution by Department.
- **Data Table**: Drill-down detail table with conditional formatting on high-priority tickets.

### PAGE 3 — SLA Analysis
- **Card Grid**: `SLA On Track`, `SLA At Risk (<20% Remaining)`, `SLA Breached`.
- **Comparison Clustered Bar**: First Response SLA Met vs. Breached.
- **Comparison Clustered Bar**: Final Resolution SLA Met vs. Breached.
- **SLA Breach Matrix**: Breached tickets grouped by Team and Priority with average breach duration.

### PAGE 4 — Engineer / Team Analysis
- **Clustered Horizontal Bar**: Active Tickets by Assigned Engineer.
- **Scatter Plot**: Number of Resolved Tickets (X-Axis) vs. Average Resolution Time (Y-Axis) per Engineer.
- **Table Visual**: Engineer Performance Scorecard:
  - Engineer Name
  - Team
  - Active Queue
  - Resolved Count
  - Average Resolution Time (hrs)
  - SLA Compliance %

### PAGE 5 — AI / Knowledge Analytics
- **KPI Card**: `Total Knowledge Articles` and `Published SOPs`.
- **KPI Card**: `Total Article Views` (Engagement metric).
- **Gauge Visual**: `Knowledge Helpful Ratio %` (`Helpful Votes / (Helpful + Not Helpful)`).
- **Table Visual**: Top 10 Most Consulted Knowledge Base Articles with category badges.
- **Advisory Summary**: AI Support Copilot and Resolution Assistant utilization indicators based on grounded RAG retrieval logs.

---

## 6. How to Connect and Refresh

1. Open **Power BI Desktop**.
2. Select **Get Data -> PostgreSQL** -> enter connection credentials from `.env` (`localhost:5432`, `techconnect_db`, `postgres`).
3. Paste the SQL Extraction Queries from Section 3.
4. Paste the DAX Measures from Section 4.
5. Publish the report to **Power BI Service** (`app.powerbi.com`).
6. Configure scheduled daily refresh via the **On-premises Data Gateway**.
