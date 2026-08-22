"""
src/train_model.py
Model Training & Evaluation Pipeline for UPI Fraud Detection:
1. Loads processed train/test datasets from data/processed/*.pkl
2. Trains three classifiers:
   - LogisticRegression (max_iter=1000, class_weight='balanced')
   - RandomForestClassifier (n_estimators=200, class_weight='balanced', random_state=42)
   - XGBClassifier (eval_metric='logloss', random_state=42)
3. Evaluates all models on the test set:
   - Accuracy, Precision, Recall, F1-Score, ROC-AUC
4. Saves the best model (by F1-score) to models/upi_model.pkl
5. Exports comparative metrics to models/model_metrics.json for benchmarking & Q&A
"""

import os
import json
import warnings
import joblib
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from xgboost import XGBClassifier
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
)
from sklearn.exceptions import ConvergenceWarning

warnings.filterwarnings("ignore", category=ConvergenceWarning)

# ── Paths configuration ───────────────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
PROCESSED_DIR = os.path.join(DATA_DIR, "processed")
MODELS_DIR = os.path.join(BASE_DIR, "models")
BEST_MODEL_PATH = os.path.join(MODELS_DIR, "upi_model.pkl")
METRICS_PATH = os.path.join(MODELS_DIR, "model_metrics.json")


def load_processed_data(processed_dir: str = PROCESSED_DIR):
    """Load train and test data splits from data/processed/."""
    print(f"Loading processed data splits from: {processed_dir}")
    X_train = joblib.load(os.path.join(processed_dir, "X_train.pkl"))
    X_test = joblib.load(os.path.join(processed_dir, "X_test.pkl"))
    y_train = joblib.load(os.path.join(processed_dir, "y_train.pkl"))
    y_test = joblib.load(os.path.join(processed_dir, "y_test.pkl"))

    print(f"X_train: {X_train.shape} | y_train: {y_train.shape}")
    print(f"X_test : {X_test.shape} | y_test : {y_test.shape}\n")
    return X_train, X_test, y_train, y_test


def evaluate_model(name: str, model, X_test, y_test) -> dict:
    """Compute standard classification metrics on the test set."""
    y_pred = model.predict(X_test)
    y_prob = model.predict_proba(X_test)[:, 1]

    acc = float(accuracy_score(y_test, y_pred))
    prec = float(precision_score(y_test, y_pred, zero_division=0))
    rec = float(recall_score(y_test, y_pred, zero_division=0))
    f1 = float(f1_score(y_test, y_pred, zero_division=0))
    roc_auc = float(roc_auc_score(y_test, y_prob))

    metrics = {
        "model_name": name,
        "accuracy": round(acc, 4),
        "precision": round(prec, 4),
        "recall": round(rec, 4),
        "f1_score": round(f1, 4),
        "roc_auc": round(roc_auc, 4),
    }
    return metrics


def train_and_evaluate_all():
    X_train, X_test, y_train, y_test = load_processed_data()

    models = {
        "Logistic Regression": LogisticRegression(
            max_iter=1000,
            class_weight="balanced",
            random_state=42,
        ),
        "Random Forest": RandomForestClassifier(
            n_estimators=200,
            class_weight="balanced",
            random_state=42,
            n_jobs=-1,
        ),
        "XGBoost": XGBClassifier(
            eval_metric="logloss",
            random_state=42,
            n_jobs=-1,
        ),
    }

    results = {}
    trained_models = {}

    print("=" * 70)
    print("                 TRAINING & EVALUATION PIPELINE                 ")
    print("=" * 70)

    for name, model in models.items():
        print(f"\n[1/2] Training {name}...")
        model.fit(X_train, y_train)
        trained_models[name] = model

        print(f"[2/2] Evaluating {name} on Test Set...")
        metrics = evaluate_model(name, model, X_test, y_test)
        results[name] = metrics

        print(f"      Accuracy  : {metrics['accuracy']:.4f}")
        print(f"      Precision : {metrics['precision']:.4f}")
        print(f"      Recall    : {metrics['recall']:.4f}")
        print(f"      F1-Score  : {metrics['f1_score']:.4f}")
        print(f"      ROC-AUC   : {metrics['roc_auc']:.4f}")

    # ── Comparison Summary Table ──────────────────────────────────────────────
    print("\n" + "=" * 70)
    print("                   MODEL COMPARISON BENCHMARK                   ")
    print("=" * 70)
    print(f"{'Model':<22} | {'Accuracy':<9} | {'Precision':<9} | {'Recall':<9} | {'F1-Score':<9} | {'ROC-AUC':<9}")
    print("-" * 70)
    for name, m in results.items():
        print(f"{name:<22} | {m['accuracy']:<9.4f} | {m['precision']:<9.4f} | {m['recall']:<9.4f} | {m['f1_score']:<9.4f} | {m['roc_auc']:<9.4f}")
    print("=" * 70)

    # ── Select and save best model by F1 score ────────────────────────────────
    # In case of ties, choose the one with higher ROC-AUC / Precision
    best_model_name = max(
        results,
        key=lambda k: (results[k]["f1_score"], results[k]["roc_auc"], results[k]["precision"]),
    )
    best_f1 = results[best_model_name]["f1_score"]
    best_model = trained_models[best_model_name]

    os.makedirs(MODELS_DIR, exist_ok=True)

    joblib.dump(best_model, BEST_MODEL_PATH)
    print(f"\n[BEST MODEL] '{best_model_name}' selected with F1-Score: {best_f1:.4f}")
    print(f"[SAVED] Best model persisted to: {BEST_MODEL_PATH}")

    # ── Save all model metrics to JSON ────────────────────────────────────────
    metrics_payload = {
        "best_model": best_model_name,
        "best_f1_score": best_f1,
        "models": results,
    }
    with open(METRICS_PATH, "w") as f:
        json.dump(metrics_payload, f, indent=4)
    print(f"[SAVED] Comparison metrics exported to: {METRICS_PATH}\n")

    return results, best_model_name


if __name__ == "__main__":
    train_and_evaluate_all()
