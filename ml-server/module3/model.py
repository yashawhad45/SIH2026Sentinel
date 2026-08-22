"""
module3/model.py
UPI Fraud Detection Model — XGBoost-based binary classifier.
"""

import os
import joblib
import numpy as np
import pandas as pd
from xgboost import XGBClassifier
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.pipeline import Pipeline
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score

# ── paths ────────────────────────────────────────────────────────────────────
_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(_DIR, "models", "upi_fraud_xgb.joblib")
SCALER_PATH = os.path.join(_DIR, "models", "upi_scaler.joblib")

# ── feature list (must match training CSV columns) ───────────────────────────
NUMERIC_FEATURES = [
    "transaction_amount",
    "transaction_hour",
    "transaction_day",
    "sender_account_age_days",
    "receiver_account_age_days",
    "sender_txn_count_24h",
    "receiver_txn_count_24h",
    "avg_txn_amount_7d",
    "amount_deviation_from_avg",
]

CATEGORICAL_FEATURES = [
    "transaction_type",
    "device_type",
    "payment_platform",
]

LABEL_COL = "is_fraud"


class UPIFraudModel:
    """
    Wraps an XGBoost classifier with a standard-scaler pipeline.
    Call `train(df)` to fit, then `predict(record)` for inference.
    """

    def __init__(self):
        self.xgb = XGBClassifier(
            n_estimators=300,
            max_depth=6,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            scale_pos_weight=10,       # handles class imbalance (fraud << legit)
            use_label_encoder=False,
            eval_metric="logloss",
            random_state=42,
        )
        self.scaler = StandardScaler()
        self.label_encoders: dict[str, LabelEncoder] = {}
        self.is_trained = False

    # ── internal helpers ─────────────────────────────────────────────────────

    def _encode_categoricals(self, df: pd.DataFrame, fit: bool = False) -> pd.DataFrame:
        df = df.copy()
        for col in CATEGORICAL_FEATURES:
            if col not in df.columns:
                df[col] = "unknown"
            if fit:
                le = LabelEncoder()
                df[col] = le.fit_transform(df[col].astype(str))
                self.label_encoders[col] = le
            else:
                le = self.label_encoders.get(col)
                if le:
                    known = set(le.classes_)
                    df[col] = df[col].astype(str).apply(
                        lambda x: x if x in known else le.classes_[0]
                    )
                    df[col] = le.transform(df[col])
                else:
                    df[col] = 0
        return df

    def _build_feature_matrix(self, df: pd.DataFrame) -> np.ndarray:
        all_cols = NUMERIC_FEATURES + CATEGORICAL_FEATURES
        for col in all_cols:
            if col not in df.columns:
                df[col] = 0
        return df[all_cols].fillna(0).values

    # ── public API ────────────────────────────────────────────────────────────

    def train(self, df: pd.DataFrame) -> dict:
        """Train the model on a labelled DataFrame and persist weights."""
        if LABEL_COL not in df.columns:
            raise ValueError(f"Dataset must contain a '{LABEL_COL}' column.")

        df = self._encode_categoricals(df, fit=True)
        X = self._build_feature_matrix(df)
        y = df[LABEL_COL].values

        X_train, X_val, y_train, y_val = train_test_split(
            X, y, test_size=0.2, random_state=42, stratify=y
        )

        X_train = self.scaler.fit_transform(X_train)
        X_val = self.scaler.transform(X_val)

        self.xgb.fit(
            X_train, y_train,
            eval_set=[(X_val, y_val)],
            verbose=False,
        )
        self.is_trained = True

        # metrics
        y_pred = self.xgb.predict(X_val)
        y_prob = self.xgb.predict_proba(X_val)[:, 1]
        report = classification_report(y_val, y_pred, output_dict=True)
        auc = roc_auc_score(y_val, y_prob)

        # persist
        os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
        joblib.dump(self.xgb, MODEL_PATH)
        joblib.dump(
            {"scaler": self.scaler, "label_encoders": self.label_encoders},
            SCALER_PATH,
        )

        return {
            "status": "trained",
            "val_auc": round(auc, 4),
            "val_precision_fraud": round(report.get("1", {}).get("precision", 0), 4),
            "val_recall_fraud": round(report.get("1", {}).get("recall", 0), 4),
            "val_f1_fraud": round(report.get("1", {}).get("f1-score", 0), 4),
        }

    def load(self):
        """Load persisted weights from disk."""
        if not os.path.exists(MODEL_PATH):
            raise FileNotFoundError(
                f"No trained model found at {MODEL_PATH}. "
                "POST to /train first."
            )
        self.xgb = joblib.load(MODEL_PATH)
        artifacts = joblib.load(SCALER_PATH)
        self.scaler = artifacts["scaler"]
        self.label_encoders = artifacts["label_encoders"]
        self.is_trained = True

    def predict(self, record: dict) -> dict:
        """
        Run inference on a single transaction record (dict).
        Returns fraud probability and binary flag.
        """
        if not self.is_trained:
            try:
                self.load()
            except FileNotFoundError as exc:
                return {"success": False, "error": str(exc)}

        df = pd.DataFrame([record])
        df = self._encode_categoricals(df, fit=False)
        X = self._build_feature_matrix(df)
        X = self.scaler.transform(X)

        fraud_prob = float(self.xgb.predict_proba(X)[0][1])
        is_fraud = fraud_prob > 0.5

        return {
            "success": True,
            "fraud_probability": round(fraud_prob, 4),
            "is_fraud": is_fraud,
            "risk_level": (
                "HIGH" if fraud_prob > 0.75
                else "MEDIUM" if fraud_prob > 0.4
                else "LOW"
            ),
        }
