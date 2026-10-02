# TechConnect AI Ticket Intelligence Architecture

## 1. System Overview
The **TechConnect AI Ticket Intelligence Service** provides automated Natural Language Processing (NLP) and supervised machine learning intelligence to assist enterprise IT Service Management (ITSM) workflows.

```
+-------------------------------------------------------------+
|                      React Frontend                         |
|  - CreateTicket: "Analyze with AI" interactive button       |
|  - AiTicketAnalysisCard: Displays recommendations & badges  |
+------------------------------+------------------------------+
                               |
                        HTTPS / REST (JWT)
                               |
                               v
+-------------------------------------------------------------+
|                  Spring Boot REST Backend                   |
|  - TicketController: POST /api/tickets/analyze              |
|  - AiTicketIntelligenceService: Orchestrates call           |
|  - AiTicketIntelligenceClient: RestClient + Circuit Fallback|
+------------------------------+------------------------------+
                               |
                       Internal HTTP / REST
                               |
                               v
+-------------------------------------------------------------+
|             Python FastAPI Microservice (:8000)             |
|  - GET /health: Health probe                                |
|  - GET /api/v1/ticket-intelligence/model-info               |
|  - POST /api/v1/ticket-intelligence/analyze                 |
|               |                             |               |
|               v                             v               |
|  +------------------------+    +--------------------------+ |
|  | Supervised ML Model    |    | Deterministic Engines    | |
|  | - TfidfVectorizer      |    | - Priority Rules Engine  | |
|  | - LogisticRegression   |    | - Team Routing Matrix    | |
|  | - Statistical Probs    |    | - ITSM Summarizer        | |
|  +------------------------+    +--------------------------+ |
+-------------------------------------------------------------+
```

---

## 2. Core Design Principle: Human Decision Authority
In an enterprise ITSM platform, AI recommendations are **predictive assistance, NOT authoritative mandates**.
- Human requesters and IT engineers retain authoritative control over ticket categories, priorities, and assignments.
- When an employee clicks "Analyze with AI", suggestions are displayed with confidence scores and explainable rationale.
- The user can review, modify, or choose their own values.
- If the AI predicts `VPN` with 92% confidence, but the employee selects `SOFTWARE`, the system strictly preserves `SOFTWARE`.

---

## 3. Machine Learning & Natural Language Processing Architecture

### 3.1 Algorithm
- **Feature Extraction**: `scikit-learn` `TfidfVectorizer`
  - N-gram Range: `(1, 2)` (unigrams and bigrams to capture phrases like "vpn not connecting" or "screen flickering")
  - Sublinear Term Frequency: `sublinear_tf=True` (logarithmic scaling $1 + \log(\text{tf})$)
  - Stop Words: Standard English stopwords removed
  - Max Features: `1,200` vocabulary features
- **Classification Engine**: `LogisticRegression`
  - Optimization: Multi-class one-vs-rest / multinomial with L2 regularization ($C=1.5$)
  - Class Weighting: `class_weight="balanced"` to guarantee equal treatment across all 8 ITSM categories
  - Probability Calibration: Directly exposes true class likelihoods via `.predict_proba()`
- **Model Persistence**: Serialized via `joblib` into `app/models/`
  - `category_model.joblib`: Serialized Logistic Regression estimator
  - `category_vectorizer.joblib`: Fitted TF-IDF vocabulary matrix
  - `model_metadata.json`: Training timestamp, dataset size, classes, and evaluation metrics

### 3.2 Dataset Specification
- **File**: `ai_services/ticket_intelligence/data/tickets_training_data.csv`
- **Volume**: 160 realistic, balanced enterprise support tickets (20 examples per category).
- **Categories**:
  1. `HARDWARE`: Laptops, monitors, power supplies, docks, trackpads, keyboards.
  2. `SOFTWARE`: Operating system crashes, IDE faults, ERP sync errors, memory leaks.
  3. `NETWORK`: Wi-Fi disconnects, DNS resolution failures, switch port packet loss.
  4. `SECURITY`: Phishing alerts, malware detections, ransomware, compromised accounts.
  5. `ACCESS_MANAGEMENT`: SSO permission grants, password resets, MFA device tokens.
  6. `EMAIL`: Outlook crashes, mailbox quota limits, SPF/DKIM bounces.
  7. `VPN`: Cisco AnyConnect tunnel drops, split tunneling, certificate errors.
  8. `OTHER`: Facility requests, ergonomic furniture, office keys.

### 3.3 Evaluation Metrics (Development Test Holdout)
- **Train/Test Split**: 80% Train (128 samples), 20% Test (32 samples), Stratified by category.
- **Accuracy**: ~62.5% on 8-way multi-class holdout test set.
- **Classification Report**:
  - Precision (Weighted): ~0.55 - 0.62
  - Recall (Weighted): ~0.56 - 0.63
  - F1-Score (Weighted): ~0.54 - 0.61

---

## 4. Operational Priority & Team Routing Rules Engine

### 4.1 Priority Inference
Rather than relying on uninterpretable black-box priority predictions, TechConnect couples operational safety with transparent keyword and severity evaluation:
- `CRITICAL` (2h SLA): Detects data leaks, ransomware, DDoS, production outages, compromised accounts, or physical theft. Confidence: 0.88 - 0.96.
- `HIGH` (4h SLA): Detects VPN disconnects, gateway crashes, packet loss, frayed chargers, license activation failures. Confidence: 0.82 - 0.92.
- `LOW` (24h SLA): Detects general inquiries, ergonomic adjustments, supplies, office facilities. Confidence: 0.80 - 0.90.
- `MEDIUM` (8h SLA): Standard operational baseline for account requests, software permissions, intermittent bugs. Confidence: 0.72 - 0.88.

### 4.2 Support Team Auto-Routing
Maps ticket categories to existing organizational departments and support groups:
- `HARDWARE` $\longrightarrow$ `Hardware & Workplace` (`HARDWARE_SUPPORT`)
- `SOFTWARE` $\longrightarrow$ `Application Support` (`SOFTWARE_SUPPORT`)
- `NETWORK` $\longrightarrow$ `Network Support` (`NETWORK_SUPPORT`)
- `SECURITY` $\longrightarrow$ `Information Security` (`SECURITY_TEAM`)
- `ACCESS_MANAGEMENT` $\longrightarrow$ `Identity & Access` (`ACCESS_SUPPORT`)
- `EMAIL` $\longrightarrow$ `Network Support` (`NETWORK_SUPPORT`)
- `VPN` $\longrightarrow$ `Network Support` (`NETWORK_SUPPORT`)
- `OTHER` $\longrightarrow$ `IT Operations` (`IT_SUPPORT`)

---

## 5. API Endpoints

### 5.1 Python Service Endpoints
1. `GET /health`
   ```json
   {
     "status": "UP",
     "service": "ticket-intelligence"
   }
   ```
2. `GET /api/v1/ticket-intelligence/model-info`
   Returns loaded model status, supported categories, supported priorities, and model version.
3. `POST /api/v1/ticket-intelligence/analyze`
   Accepts `title` (3-255 chars), `description` (5-5000 chars), optional `category`, optional `priority`. Returns prediction scores, team recommendations, normalized summary, and explainable reasons.

### 5.2 Spring Boot Proxy Endpoint
- **URL**: `POST /api/tickets/analyze`
- **Security**: Requires authenticated JWT (`ROLE_EMPLOYEE`, `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`).
- **Body**: `AiAnalysisRequest` (`title`, `description`, `category`, `priority`)
- **Response**: `AiAnalysisResponse`

---

## 6. Failure Fallback & Resilience
The AI microservice is architected as an optional, assisting subsystem:
1. **Network Disconnect / Offline AI**:
   - `AiTicketIntelligenceClient` catches `ResourceAccessException` (connection refused or timeout).
   - Returns `aiAvailable: false` with message `"AI analysis service is unreachable or timed out"`.
   - Returns HTTP 200 OK to the frontend with fallback metadata.
2. **Ticket Creation Unaffected**:
   - Ticket creation (`POST /api/tickets`) functions independently and never depends synchronously on AI availability.
   - If the AI service is stopped, tickets are created seamlessly with 100% reliability.
3. **Frontend UI Handling**:
   - `AiTicketAnalysisCard` transitions to a gentle warning state alerting the user that AI is temporarily offline while encouraging them to proceed with manual creation.

---

## 7. Security Hardening
- **Stateless Proxying**: The Python service is not exposed to the public internet or directly to the React frontend; all calls pass through Spring Boot's authenticated filter chain.
- **Zero Secret Exposure**: Model internals, internal paths, and Python stack traces are not leaked in error responses.
- **Input Sanitization**: Pydantic validates string boundaries, trims whitespace, and rejects blank or oversized payloads.
