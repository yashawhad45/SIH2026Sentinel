"""
src/train_model_stripped.py
Leakage-Stripped Model Benchmarking & Defense Pipeline:
- Removes 7 high-correlation / potential shortcut features:
    handle_verification_status, merchant_category_code, session_source,
    transaction_type, recognized_screen_sharing_apps, unusual_device_flag, unusual_ip_flag
- Retrains LogisticRegression, RandomForest, and XGBoost on purely behavioral/statistical features
- Evaluates Accuracy, Precision, Recall, F1-Score, and ROC-AUC on 20% test split
- Saves metrics to models/model_metrics_stripped.json and best model to models/upi_model_stripped.pkl
- Proves model robustness to hackathon judges when shortcut flags are absent
"""

import os
import re
import json
import warnings
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split
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
MODELS_DIR = os.path.join(BASE_DIR, "models")
RAW_DATA_PATH = os.path.join(DATA_DIR, "fraud_dataset.csv")
BEST_MODEL_STRIPPED_PATH = os.path.join(MODELS_DIR, "upi_model_stripped.pkl")
METRICS_STRIPPED_PATH = os.path.join(MODELS_DIR, "model_metrics_stripped.json")
FULL_METRICS_PATH = os.path.join(MODELS_DIR, "model_metrics.json")

# Standard uninformative columns
BASE_COLUMNS_TO_DROP = [
    "transaction_id",
    "user_id",
    "merchant_id",
    "device_id",
    "ip_address",
    "description",
    "request_description",
    "url_referrer",
    "location",
    "timestamp",
    "relationship_to_requester",
    "upi_handle_age",
    "handle_contains_official_terms",
    "social_media_presence",
]

# Additional shortcut / potential leakage features to drop
LEAKAGE_COLUMNS_TO_DROP = [
    "handle_verification_status",
    "merchant_category_code",
    "session_source",
    "transaction_type",
    "recognized_screen_sharing_apps",
    "unusual_device_flag",
    "unusual_ip_flag",
]

TARGET_COLUMN = "is_fraud"


def sanitize_column_name(name: str) -> str:
    """Sanitize column names for compatibility with tree models."""
    return re.sub(r"[\[\]<>]", "_", str(name))


def prepare_stripped_data(raw_path: str = RAW_DATA_PATH, test_size: float = 0.2, random_state: int = 42):
    """Loads raw data, drops uninformative + shortcut features, encodes, and splits."""
    print(f"Loading raw dataset from: {raw_path}")
    df = pd.read_csv(raw_path)
    print(f"Original dataset shape: {df.shape}")

    total_drop_cols = BASE_COLUMNS_TO_DROP + LEAKAGE_COLUMNS_TO_DROP
    present_drop_cols = [c for c in total_drop_cols if c in df.columns]
    
    df_clean = df.drop(columns=present_drop_cols)
    print(f"Dropped {len(present_drop_cols)} columns (14 uninformative + 7 shortcut flags).")
    print(f"Remaining raw feature columns: {df_clean.shape[1] - 1}")

    y = df_clean[TARGET_COLUMN]
    X = df_clean.drop(columns=[TARGET_COLUMN])

    # One-hot encode remaining categorical columns
    X_encoded = pd.get_dummies(X, drop_first=True, dtype=int)
    X_encoded.columns = [sanitize_column_name(col) for col in X_encoded.columns]
    print(f"Encoded feature count: {X_encoded.shape[1]}")

    X_train, X_test, y_train, y_test = train_test_split(
        X_encoded,
        y,
        test_size=test_size,
        stratify=y,
        random_state=random_state,
    )
    print(f"Train split: {X_train.shape} | Test split: {X_test.shape}\n")
    return X_train, X_test, y_train, y_test, list(X_encoded.columns)


def evaluate_model(name: str, model, X_test, y_test) -> dict:
    """Calculate classification metrics."""
    y_pred = model.predict(X_test)
    y_prob = model.predict_proba(X_test)[:, 1]

    acc = float(accuracy_score(y_test, y_pred))
    prec = float(precision_score(y_test, y_pred, zero_division=0))
    rec = float(recall_score(y_test, y_pred, zero_division=0))
    f1 = float(f1_score(y_test, y_pred, zero_division=0))
    roc_auc = float(roc_auc_score(y_test, y_prob))

    return {
        "model_name": name,
        "accuracy": round(acc, 4),
        "precision": round(prec, 4),
        "recall": round(rec, 4),
        "f1_score": round(f1, 4),
        "roc_auc": round(roc_auc, 4),
    }


def train_and_benchmark_stripped():
    X_train, X_test, y_train, y_test, feature_columns = prepare_stripped_data()

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

    print("=" * 72)
    print("        LEAKAGE-STRIPPED MODEL TRAINING & EVALUATION PIPELINE        ")
    print("=" * 72)

    for name, model in models.items():
        print(f"\n[1/2] Training {name} (Stripped Features)...")
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
    print("\n" + "=" * 72)
    print("               STRIPPED FEATURES BENCHMARK RESULTS               ")
    print("=" * 72)
    print(f"{'Model':<22} | {'Accuracy':<9} | {'Precision':<9} | {'Recall':<9} | {'F1-Score':<9} | {'ROC-AUC':<9}")
    print("-" * 72)
    for name, m in results.items():
        print(f"{name:<22} | {m['accuracy']:<9.4f} | {m['precision']:<9.4f} | {m['recall']:<9.4f} | {m['f1_score']:<9.4f} | {m['roc_auc']:<9.4f}")
    print("=" * 72)

    # ── Compare Full-Feature vs Stripped if full metrics exist ─────────────────
    if os.path.exists(FULL_METRICS_PATH):
        try:
            with open(FULL_METRICS_PATH, "r") as f:
                full_data = json.load(f)
            full_models = full_data.get("models", {})
            print("\n" + "=" * 72)
            print("        SIDE-BY-SIDE: FULL FEATURES vs. LEAKAGE-STRIPPED        ")
            print("=" * 72)
            print(f"{'Model':<20} | {'Full F1':<9} | {'Stripped F1':<11} | {'Full AUC':<9} | {'Stripped AUC':<12}")
            print("-" * 72)
            for name in results.keys():
                ff1 = full_models.get(name, {}).get("f1_score", "N/A")
                fauc = full_models.get(name, {}).get("roc_auc", "N/A")
                sf1 = results[name]["f1_score"]
                sauc = results[name]["roc_auc"]
                print(f"{name:<20} | {str(ff1):<9} | {sf1:<11.4f} | {str(fauc):<9} | {sauc:<12.4f}")
            print("=" * 72)
        except Exception as e:
            print(f"Could not load full metrics comparison: {e}")

    # ── Select and save best stripped model ───────────────────────────────────
    best_model_name = max(
        results,
        key=lambda k: (results[k]["f1_score"], results[k]["roc_auc"], results[k]["precision"]),
    )
    best_f1 = results[best_model_name]["f1_score"]
    best_model = trained_models[best_model_name]

    os.makedirs(MODELS_DIR, exist_ok=True)
    joblib.dump(best_model, BEST_MODEL_STRIPPED_PATH)
    print(f"\n[BEST STRIPPED MODEL] '{best_model_name}' selected (F1 = {best_f1:.4f})")
    print(f"[SAVED] Persisted model to: {BEST_MODEL_STRIPPED_PATH}")

    # ── Save stripped metrics to JSON ─────────────────────────────────────────
    metrics_payload = {
        "best_model": best_model_name,
        "best_f1_score": best_f1,
        "dropped_leakage_features": LEAKAGE_COLUMNS_TO_DROP,
        "remaining_features_count": len(feature_columns),
        "models": results,
    }
    with open(METRICS_STRIPPED_PATH, "w") as f:
        json.dump(metrics_payload, f, indent=4)
    print(f"[SAVED] Exported stripped metrics to: {METRICS_STRIPPED_PATH}\n")

    return results, best_model_name


if __name__ == "__main__":
    train_and_benchmark_stripped()
