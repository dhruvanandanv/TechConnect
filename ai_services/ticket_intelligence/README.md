# TechConnect AI Ticket Intelligence Service

The **TechConnect AI Ticket Intelligence Service** is a lightweight, dedicated Python FastAPI microservice that provides automated Natural Language Processing (NLP) and supervised machine learning intelligence to assist IT Service Management workflows.

---

## Capabilities
1. **Ticket Category Classification**: Supervised ML classification (`TF-IDF` + `LogisticRegression`) predicting the issue category across 8 standard ITSM categories.
2. **Priority Assessment**: Deterministic rule-based urgency inference evaluating high-risk keywords and operational impact signals (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
3. **Suggested Team Routing**: Recommends appropriate support teams (`Network Support`, `Hardware & Workplace`, `Identity & Access`, etc.).
4. **Automated Summarization**: Generates standardized, professional 3rd-person problem summaries.
5. **Explainability & Confidence**: Emits real statistical probability scores and human-readable decision reasons.
6. **Graceful Fallback**: Designed to fail open so that ITSM ticket creation is never blocked if the AI service is offline.

---

## Dataset & Model Architecture

### 1. Dataset Design
- **Path**: `data/tickets_training_data.csv`
- **Size**: 160 realistic, balanced IT service incident records (20 examples per category).
- **Categories**: `HARDWARE`, `SOFTWARE`, `NETWORK`, `SECURITY`, `ACCESS_MANAGEMENT`, `EMAIL`, `VPN`, `OTHER`.
- **Note**: This dataset serves as an enterprise demonstration and development baseline. It does not represent large-scale production training data.

### 2. Feature Pipeline & Model
- **Preprocessing**: Whitespace normalization, control character removal, title re-weighting.
- **Vectorization**: `TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True, stop_words="english", max_features=1200)`.
- **Classifier**: `LogisticRegression(C=1.5, max_iter=1000, class_weight="balanced")`.
- **Artifacts**: Persisted in `app/models/` (`category_model.joblib`, `category_vectorizer.joblib`, `model_metadata.json`).

### 3. Evaluation Metrics (Development Test Split)
- **Train/Test Split**: 80% Train (128 samples), 20% Test (32 samples), Stratified by category.
- **Accuracy**: ~62.5% on multi-class 8-way classification with 32 holdout test samples.
- **Confidence**: Sourced directly from `predict_proba()`.

---

## Running Locally

### Prerequisites
- Python 3.11+
- Virtual environment or system Python with required packages:
  ```bash
  pip install -r requirements.txt
  ```

### Train the Model
```bash
python -m app.ml.train
```

### Start the Service
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Run Tests
```bash
pytest -v
```

---

## API Endpoints

### 1. Health Probe
- **Endpoint**: `GET /health`
- **Response**:
  ```json
  {
    "status": "UP",
    "service": "ticket-intelligence"
  }
  ```

### 2. Model Metadata
- **Endpoint**: `GET /api/v1/ticket-intelligence/model-info`
- **Response**: Model version, classes, training timestamp, and loaded status.

### 3. Analyze Ticket
- **Endpoint**: `POST /api/v1/ticket-intelligence/analyze`
- **Request**:
  ```json
  {
    "title": "VPN is not connecting",
    "description": "I am unable to connect to the company VPN from my laptop",
    "category": null,
    "priority": null
  }
  ```
- **Response**:
  ```json
  {
    "category": { "value": "VPN", "confidence": 0.88 },
    "priority": { "value": "HIGH", "confidence": 0.85 },
    "suggested_team": { "value": "NETWORK_SUPPORT", "confidence": 0.84 },
    "summary": "VPN is not connecting: User is unable to connect to the company VPN from the assigned workstation.",
    "reasons": [
      "Category VPN predicted from salient terminology: vpn",
      "High-impact operational keywords identified: vpn",
      "Routed to Network Support for secure remote tunnels and gateway firewalls"
    ],
    "model_version": "ticket-intelligence-v1",
    "processing_time_ms": 14
  }
  ```
