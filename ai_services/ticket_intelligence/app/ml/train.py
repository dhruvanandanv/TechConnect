import json
import logging
import os
from datetime import datetime
from pathlib import Path

import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, f1_score, precision_score, recall_score
from sklearn.model_selection import train_test_split

from app.config import settings
from app.utils.text_cleaning import combine_ticket_text

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

SUPPORTED_CATEGORIES = [
    "HARDWARE",
    "SOFTWARE",
    "NETWORK",
    "SECURITY",
    "ACCESS_MANAGEMENT",
    "EMAIL",
    "VPN",
    "OTHER",
]


def train_category_model(data_path: str = None, output_dir: str = None) -> dict:
    """
    Train TF-IDF + LogisticRegression classifier for IT ticket categorization.
    Saves vectorizer, classifier, and metadata JSON.
    """
    if data_path is None:
        data_path = settings.data_file_path
    if output_dir is None:
        output_dir = settings.model_artifacts_dir

    logger.info("Loading training dataset from: %s", data_path)
    df = pd.read_csv(data_path)

    # 1. Dataset validation
    if "title" not in df.columns or "description" not in df.columns or "category" not in df.columns:
        raise ValueError("Dataset must contain 'title', 'description', and 'category' columns")

    df = df.dropna(subset=["title", "description", "category"])
    df["category"] = df["category"].str.strip().str.upper()

    invalid_cats = set(df["category"]) - set(SUPPORTED_CATEGORIES)
    if invalid_cats:
        raise ValueError(f"Dataset contains invalid categories: {invalid_cats}")

    logger.info("Dataset verified: %d records across %d categories", len(df), df["category"].nunique())

    # 2. Text feature preparation
    df["combined_text"] = df.apply(lambda row: combine_ticket_text(row["title"], row["description"]), axis=1)

    X = df["combined_text"]
    y = df["category"]

    # 3. Stratified split (80% train, 20% test)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )
    logger.info("Split dataset into %d training and %d test samples", len(X_train), len(X_test))

    # 4. TF-IDF Vectorization
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        sublinear_tf=True,
        min_df=1,
        stop_words="english",
        max_features=1200,
    )
    X_train_vec = vectorizer.fit_transform(X_train)
    X_test_vec = vectorizer.transform(X_test)

    # 5. LogisticRegression Classifier
    classifier = LogisticRegression(
        C=1.5,
        max_iter=1000,
        random_state=42,
        class_weight="balanced",
    )
    classifier.fit(X_train_vec, y_train)

    # 6. Evaluation
    y_pred = classifier.predict(X_test_vec)
    acc = accuracy_score(y_test, y_pred)
    prec = precision_score(y_test, y_pred, average="weighted", zero_division=0)
    rec = recall_score(y_test, y_pred, average="weighted", zero_division=0)
    f1 = f1_score(y_test, y_pred, average="weighted", zero_division=0)
    report = classification_report(y_test, y_pred, output_dict=True, zero_division=0)

    logger.info("Evaluation Results (Development Test Set):")
    logger.info("Accuracy:  %.4f", acc)
    logger.info("Precision: %.4f", prec)
    logger.info("Recall:    %.4f", rec)
    logger.info("F1-Score:  %.4f", f1)

    # 7. Model Persistence
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    model_file = out_path / "category_model.joblib"
    vec_file = out_path / "category_vectorizer.joblib"
    meta_file = out_path / "model_metadata.json"

    joblib.dump(classifier, model_file)
    joblib.dump(vectorizer, vec_file)

    metadata = {
        "model_name": "TF-IDF + LogisticRegression Category Classifier",
        "model_version": settings.model_version,
        "algorithm": "TfidfVectorizer + LogisticRegression",
        "trained_at": datetime.utcnow().isoformat(),
        "dataset_size": len(df),
        "train_samples": len(X_train),
        "test_samples": len(X_test),
        "classes": sorted(list(classifier.classes_)),
        "metrics": {
            "accuracy": round(float(acc), 4),
            "precision_weighted": round(float(prec), 4),
            "recall_weighted": round(float(rec), 4),
            "f1_weighted": round(float(f1), 4),
            "detailed_report": report,
        },
        "limitations": "Demonstration baseline trained on curated TechConnect ITSM incident samples.",
    }

    with open(meta_file, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    logger.info("Saved model artifacts to %s", str(out_path))
    return metadata


if __name__ == "__main__":
    train_category_model()
