# TechConnect SLA Management & Automation Engine

## 1. What is an SLA (Service Level Agreement)?

A **Service Level Agreement (SLA)** is an explicit, legally and operationally binding commitment between an IT Service Management (ITSM) provider and its end users (employees, departments, customers). It defines expected quality, responsiveness, and speed of service delivery.

In ITIL/ITSM standards, SLAs govern two distinct milestones for every service request or incident:
1. **First Response SLA**: The maximum allowable time before an authorized IT support specialist acknowledges the ticket and starts active investigation.
2. **Resolution SLA**: The maximum allowable time before the incident is completely resolved and normal business operations are restored.

---

## 2. SLA Policies & Priority-Based Targets

TechConnect calculates SLA deadlines automatically at ticket creation time based on the ticket's priority level. The policies are persisted in the `sla_rules` table and are fully configurable:

| Priority | First Response Target | Resolution Target | Warning Threshold (`AT_RISK`) | Operational Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **`CRITICAL`** | **1 Hour** | **2 Hours** | Remaining time ≤ 1h (or ≤ 20%) | Enterprise-wide outage; severe business disruption. |
| **`HIGH`** | **4 Hours** | **24 Hours** | Remaining time ≤ 5h (or ≤ 20%) | Major system impairment; core workflow blocked with workaround available. |
| **`MEDIUM`** | **8 Hours** | **48 Hours** | Remaining time ≤ 10h (or ≤ 20%) | Standard non-critical issue; individual productivity impacted. |
| **`LOW`** | **24 Hours** | **72 Hours** | Remaining time ≤ 14h (or ≤ 20%) | Minor inconvenience or administrative request; zero business stoppage. |

---

## 3. SLA States & Lifecycle State Machine

TechConnect tracks both milestones using the `SlaStatus` enumeration:

```
                  ┌──────────────────────┐
                  │       ON_TRACK       │ (> 20% remaining duration)
                  └──────────┬───────────┘
                             │
            ┌────────────────┴────────────────┐
            │ (< 20% remaining)               │ (Status = WAITING_FOR_USER)
            ▼                                 ▼
┌──────────────────────┐          ┌──────────────────────┐
│       AT_RISK        │          │        PAUSED        │
└──────────┬───────────┘          └──────────┬───────────┘
           │                                 │
           │ (Deadline passed)               │ (Returned to IN_PROGRESS)
           ▼                                 ▼
┌──────────────────────┐          ┌──────────────────────┐
│       BREACHED       │          │  Resumed & Extended  │
└──────────────────────┘          └──────────────────────┘
           ▲
           │ (Completed after deadline)
           │
┌──────────────────────┐
│      COMPLETED       │ (Achieved on or before deadline)
└──────────────────────┘
```

### Detailed State Explanations:
- **`ON_TRACK`**: The ticket is progressing normally with more than 20% of the allocated SLA window remaining.
- **`AT_RISK`**: The ticket is approaching its deadline (remaining duration is ≤ 20% of the total target window, or less than the warning threshold). It flags managerial dashboards for urgent triage.
- **`BREACHED`**: The milestone deadline elapsed without completion.
- **`PAUSED`**: The SLA timer is frozen because progress is blocked on external input (e.g. employee needs to supply diagnostic screenshots or verify credentials).
- **`COMPLETED`**: The milestone (first response or resolution) was fulfilled successfully on or before the deadline.

---

## 4. Response SLA: Definition & Operational Rules

### First Response Milestone Trigger
A ticket is considered **responded to** when an authorized IT staff member (`ROLE_ENGINEER`, `ROLE_MANAGER`, or `ROLE_ADMIN`) performs the first operational action on the ticket. Deterministic triggers include:
1. **Engineer Assignment**: When a manager assigns an engineer, or an engineer self-assigns an open ticket.
2. **Status Progression**: When an engineer transitions ticket status from `ASSIGNED` to `IN_PROGRESS`.
3. **Operational Comment**: When an engineer or manager posts a technical inquiry or note to the ticket.

### Execution:
- The system captures `respondedAt = LocalDateTime.now()`.
- If `respondedAt <= responseDeadline`: Response SLA is marked **`COMPLETED`**.
- If `respondedAt > responseDeadline`: Response SLA is recorded as **`BREACHED`**.
- An immutable `AuditLog` event (`RESPONSE_COMPLETED` or `RESPONSE_BREACHED`) is recorded.

---

## 5. Resolution SLA: Milestone Trigger

### Resolution Milestone Trigger
Resolution SLA is fulfilled when the ticket transitions into the **`RESOLVED`** status.

> [!NOTE]
> In ITIL practice, resolution is measured at **`RESOLVED`** (when technical repair is verified), NOT at **`CLOSED`** (which is an administrative wrap-up after user satisfaction confirmation).

### Execution:
- The system captures `resolvedAt = LocalDateTime.now()`.
- If `resolvedAt <= slaDeadline`: Resolution SLA is marked **`COMPLETED`**.
- If `resolvedAt > slaDeadline`: Resolution SLA is recorded as **`BREACHED`**.
- An immutable `AuditLog` event (`RESOLUTION_COMPLETED` or `RESOLUTION_BREACHED`) is created.

---

## 6. Dynamic SLA Clock & Calculation Algorithm

The system calculates SLA metrics dynamically on demand rather than relying exclusively on background polling. This ensures zero latency and real-time accuracy:

```java
// Remaining time calculation
long remainingMinutes = Math.max(0, Duration.between(now, deadline).toMinutes());

// At-risk evaluation
double thresholdMinutes = totalPolicyMinutes * atRiskThresholdPercentage; // Default: 20%
if (remainingMinutes <= thresholdMinutes) {
    status = SlaStatus.AT_RISK;
} else {
    status = SlaStatus.ON_TRACK;
}
```

### Overall Ticket SLA Status Rule:
1. If either Response or Resolution is `BREACHED` ➔ Overall status is **`BREACHED`**.
2. Else if Resolution is `COMPLETED` ➔ Overall status is **`COMPLETED`**.
3. Else if either milestone is `PAUSED` ➔ Overall status is **`PAUSED`**.
4. Else if either milestone is `AT_RISK` ➔ Overall status is **`AT_RISK`**.
5. Otherwise ➔ Overall status is **`ON_TRACK`**.

---

## 7. SLA Pause & Resume Behavior

### Why Pause?
When an IT engineer asks an employee: *"Please provide the error screenshot from Event Viewer"*, the engineer cannot continue work until the employee responds. Penalizing the IT team's SLA while waiting for external user feedback is unfair and distorts performance KPIs.

### How Pause Works:
1. **Transition to `WAITING_FOR_USER`**:
   - `ticket.slaPausedAt` is stamped with current timestamp (`LocalDateTime.now()`).
   - SLA state transitions to `PAUSED`.
   - `AuditLog` records `SLA_PAUSED`.
2. **Transition out of `WAITING_FOR_USER` back to active work (`IN_PROGRESS`)**:
   - The elapsed duration `Duration paused = Duration.between(slaPausedAt, now)` is calculated.
   - `totalPausedDurationMinutes` is incremented by `paused.toMinutes()`.
   - **Deadline Extension**: The resolution deadline (`slaDeadline`) and pending response deadline (`responseDeadline`) are pushed forward by the exact paused duration:
     ```java
     ticket.setSlaDeadline(ticket.getSlaDeadline().plus(pausedDuration));
     ```
   - `ticket.slaPausedAt` is cleared to `null`.
   - `AuditLog` records `SLA_RESUMED`.

---

## 8. Future Enhancements: Business Hours & Regional Calendars

In Phase 6, SLA calculations utilize **elapsed clock time** (24 hours = 24 elapsed clock hours). In future enterprise iterations, the SLA engine will support:
- **Custom Business Calendars**: Calculating SLA duration only during Monday–Friday 09:00–18:00 local time.
- **Holiday Calendars**: Excluding national and regional public holidays from the SLA timer.
- **Timezone-Aware Calculations**: Scoping calendars to the requester's geographical branch.
