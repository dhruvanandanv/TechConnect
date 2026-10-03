# TechConnect Portfolio Screenshot & Demo Walkthrough Guide

This document provides a comprehensive portfolio demonstration and screenshot guide for **TechConnect — AI-Powered Enterprise IT Service Management Platform**.

---

## 1. Overview & Demonstration Objective

For technical portfolio presentations, engineering interviews, and project evaluations, capture clean, high-resolution screenshots (1920x1080 or 2560x1440) showcasing the end-to-end capabilities of TechConnect.

---

## 2. Recommended Screenshot Checklist

| # | Screen / Feature | Route / URL | Role Required | Key UI Elements to Highlight |
|:---:|:---|:---|:---|:---|
| **1** | **Authentication Portal** | `http://localhost:5173/login` | Public | Brand banner, clean credentials card, secure login validation, demo accounts selector |
| **2** | **Executive Dashboard** | `http://localhost:5173/dashboard` | Manager / Admin | Active KPI counter cards, SLA compliance gauge, recent ticket stream, shortcut actions |
| **3** | **Ticket Creation & AI Triage** | `http://localhost:5173/tickets/new` | Employee / Staff | Multi-category dropdown, priority picker, "AI Predict Triage" suggestions, clean validation alerts |
| **4** | **Ticket Details & Lifecycle** | `http://localhost:5173/tickets/1` | Engineer / Staff | Ticket status badge, priority badge, assignment card, SLA timeline, threaded comments, status update modal |
| **5** | **SLA Performance Dashboard** | `http://localhost:5173/sla` | Manager / Admin | Active tickets, On Track, At Risk (<20%), Breached tickets table, First Response vs Resolution compliance |
| **6** | **Knowledge Base Management** | `http://localhost:5173/knowledge` | Any Authenticated | Category filter cards, published SOPs, version tags, article search bar, author metadata |
| **7** | **Semantic Vector Search** | `http://localhost:5173/knowledge` | Any Authenticated | Natural language query box, cosine similarity match score badges (e.g. `94% match`), extracted highlight snippets |
| **8** | **AI Support Copilot** | `http://localhost:5173/ai-support` | Any Authenticated | Chat interface, contextual question prompt, real-time grounded synthesized answer |
| **9** | **AI Source Citations** | `http://localhost:5173/ai-support` | Any Authenticated | Verifiable `[SOURCE N]` badges, clickable links opening source knowledge articles, confidence score |
| **10**| **AI Resolution Assistant** | `http://localhost:5173/tickets/1` | Engineer / Staff | "Suggest Resolution" panel, numbered troubleshooting action steps, similar historical tickets, "Copy to Resolution" button |
| **11**| **ITSM Executive Analytics** | `http://localhost:5173/analytics` | Engineer / Staff | 14-day activity trend chart, priority breakdown bar, category shares, engineer capacity scorecard |
| **12**| **Container Orchestration** | CLI / Terminal | Administrator | `docker compose ps` showing 5 healthy containers, Actuator health status `{"status":"UP"}` |

---

## 3. Step-by-Step Manual Capture Instructions

### Screen 1: Login Portal
1. Navigate to `http://localhost:5173/login`.
2. Notice the modern card layout, clean brand typography, and inputs for email and password.
3. Capture full browser window or cropped container card.

### Screen 2: Executive Dashboard
1. Log in as Manager: `manager@techconnect.com` / `SecureDevPassword2026!` (or local dev user).
2. Arrive at `http://localhost:5173/dashboard`.
3. Notice the 4 primary KPI cards (`Total Tickets`, `SLA On Track`, `SLA At Risk`, `SLA Breached`), response SLA metric highlights, and the recent tickets table.

### Screen 3: Ticket Creation with AI Prediction
1. Click **Create Ticket** or navigate to `http://localhost:5173/tickets/new`.
2. Type in:
   - **Title**: `VPN connection repeatedly dropping during video conference calls`
   - **Description**: `Every 10 minutes when connected to the corporate gateway, GlobalProtect disconnects with error code 502.`
3. Click **AI Predict Triage**.
4. Observe the AI auto-selecting `Category: NETWORK` and suggesting `Priority: HIGH`.

### Screen 4: Ticket Details & Lifecycle Transition
1. Open a ticket, e.g. `http://localhost:5173/tickets/1`.
2. Observe the dual-column layout:
   - Left: Ticket description, requester details, chronological comment timeline.
   - Right: Real-time SLA Countdown, assigned engineer/team, and status action buttons.

### Screen 5: SLA Governance Dashboard
1. Navigate to `http://localhost:5173/sla`.
2. Capture the 4 SLA status cards (`Total Active`, `On Track`, `At Risk`, `Breached`).
3. Scroll to the Breached Incidents table displaying overdue tickets and their calculated breach duration.

### Screen 6: Knowledge Base Portal
1. Navigate to `http://localhost:5173/knowledge`.
2. Capture the article grid showcasing titles, summaries, tags, view counts, and helpful vote counts.

### Screen 7: Semantic Vector Search
1. In the Knowledge Base search bar, enter a semantic query: `"cannot access internal git repo after password reset"`.
2. Observe dense vector search results displaying similarity percentage badges (e.g. `91% Relevance`).

### Screen 8 & 9: AI Support Copilot & Source Citations
1. Navigate to `http://localhost:5173/ai-support`.
2. Ask: `"How do I configure my VPN credentials on macOS?"`.
3. Capture the grounded markdown answer, accompanied by the **Verified Sources** footer citing the exact Knowledge Article title and slug.

### Screen 10: AI Engineer Resolution Assistant
1. As an Engineer, open an assigned ticket at `http://localhost:5173/tickets/1`.
2. Scroll to the **AI Resolution Assistant** card and click **Generate Resolution Proposal**.
3. Capture the tri-fold grounded suggestion:
   - Actionable Step-by-Step Resolution.
   - Cites Knowledge Base SOPs.
   - Lists Similar Historical Resolved Incidents.
   - The "Copy to Resolution" button staging the resolution text into the modal.

### Screen 11: ITSM Executive Analytics Dashboard
1. Navigate to `http://localhost:5173/analytics`.
2. Capture the 5-tab interface:
   - Tab 1: Executive Overview with 14-day activity trend.
   - Tab 2: Incidents by Category and Status breakdown.
   - Tab 3: SLA First Response vs. Resolution performance.
   - Tab 4: Engineer Workload and capacity status.
   - Tab 5: Knowledge Base and AI grounding metrics.

### Screen 12: Production Infrastructure & Health
1. Open terminal and run:
   ```bash
   docker compose ps
   curl -i http://localhost:8080/actuator/health
   ```
2. Capture the terminal showing all 5 microservices running in state `Up (healthy)` and the sanitized JSON health probe.
