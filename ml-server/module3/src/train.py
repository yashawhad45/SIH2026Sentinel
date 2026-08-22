"""
module3/src/train.py
Standalone training script — can be run directly:
    python src/train.py

Reads the processed dataset, trains the XGBoost model,
and saves weights to models/.
"""

import os
import sys
import argparse
import pandas as pd
import matplotlib
matplotlib.use("Agg")   # headless backend
import matplotlib.pyplot as plt
from sklearn.metrics import (
    ConfusionMatrixDisplay, RocCurveDisplay,
    classification_report, roc_auc_score,
)

# Allow importing from parent directory
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from model import UPIFraudModel, LABEL_COL
from src.preprocess import run_pipeline

_DIR = os.path.dirname(os.path.abspath(__file__))
PROCESSED_PATH = os.path.join(_DIR, "..", "data", "processed", "fraud_dataset_processed.csv")
RAW_PATH = os.path.join(_DIR, "..", "data", "fraud_dataset.csv")
OUTPUTS_DIR = os.path.join(_DIR, "..", "outputs")


def plot_and_save(model: UPIFraudModel, X_val, y_val):
    os.makedirs(OUTPUTS_DIR, exist_ok=True)

    from sklearn.model_selection import train_test_split
    import numpy as np

    y_prob = model.xgb.predict_proba(X_val)[:, 1]
    y_pred = model.xgb.predict(X_val)

    # ROC curve
    fig, ax = plt.subplots(figsize=(6, 5))
    RocCurveDisplay.from_predictions(y_val, y_prob, ax=ax, name="XGBoost")
    ax.set_title("UPI Fraud Detection — ROC Curve")
    fig.tight_layout()
    fig.savefig(os.path.join(OUTPUTS_DIR, "roc_curve.png"), dpi=150)
    plt.close(fig)

    # Confusion matrix
    fig, ax = plt.subplots(figsize=(5, 4))
    ConfusionMatrixDisplay.from_predictions(y_val, y_pred, ax=ax)
    ax.set_title("Confusion Matrix (Validation Set)")
    fig.tight_layout()
    fig.savefig(os.path.join(OUTPUTS_DIR, "confusion_matrix.png"), dpi=150)
    plt.close(fig)

    # Feature importance
    importances = model.xgb.feature_importances_
    from model import NUMERIC_FEATURES, CATEGORICAL_FEATURES
    feature_names = NUMERIC_FEATURES + CATEGORICAL_FEATURES
    fig, ax = plt.subplots(figsize=(8, 5))
    idx = importances.argsort()[::-1]
    ax.bar(range(len(idx)), importances[idx])
    ax.set_xticks(range(len(idx)))
    ax.set_xticklabels([feature_names[i] for i in idx], rotation=45, ha="right")
    ax.set_title("XGBoost Feature Importances")
    fig.tight_layout()
    fig.savefig(os.path.join(OUTPUTS_DIR, "feature_importance.png"), dpi=150)
    plt.close(fig)

    print(f"[train] Plots saved to {OUTPUTS_DIR}/")


def main(args):
    # ── load data ──────────────────────────────────────────────────────────
    if not os.path.exists(PROCESSED_PATH):
        print("[train] Processed dataset not found — running preprocessing...")
        run_pipeline()

    df = pd.read_csv(PROCESSED_PATH)
    print(f"[train] Dataset loaded: {len(df):,} rows | fraud rate: {df[LABEL_COL].mean():.2%}")

    # ── train ─────────────────────────────────────────────────────────────
    model = UPIFraudModel()
    metrics = model.train(df)

    print("\n── Training Results ──────────────────────────────────────────")
    for k, v in metrics.items():
        print(f"  {k}: {v}")
    print("─────────────────────────────────────────────────────────────\n")

    # ── plots ─────────────────────────────────────────────────────────────
    if args.plots:
        from sklearn.preprocessing import StandardScaler
        from model import NUMERIC_FEATURES, CATEGORICAL_FEATURES

        # Re-build val set for plotting
        from sklearn.model_selection import train_test_split
        model_for_plot = UPIFraudModel()
        # Re-encode
        df_enc = model_for_plot._encode_categoricals(df, fit=True)
        X = model_for_plot._build_feature_matrix(df_enc)
        y = df_enc[LABEL_COL].values
        X_train, X_val, y_train, y_val = train_test_split(
            X, y, test_size=0.2, random_state=42, stratify=y
        )
        X_val_scaled = model.scaler.transform(X_val)
        plot_and_save(model, X_val_scaled, y_val)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train UPI Fraud Detection model")
    parser.add_argument("--plots", action="store_true", help="Save evaluation plots to outputs/")
    args = parser.parse_args()
    main(args)
