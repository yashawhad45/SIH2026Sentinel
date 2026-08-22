"""
module3/src/preprocess.py
Data preprocessing utilities for UPI fraud detection.
Reads raw fraud_dataset.csv from data/, cleans and engineers features,
writes processed output to data/processed/.
"""

import os
import pandas as pd
import numpy as np
from datetime import datetime

_DIR = os.path.dirname(os.path.abspath(__file__))
RAW_PATH = os.path.join(_DIR, "..", "data", "fraud_dataset.csv")
OUT_PATH = os.path.join(_DIR, "..", "data", "processed", "fraud_dataset_processed.csv")


def load_raw(path: str = RAW_PATH) -> pd.DataFrame:
    df = pd.read_csv(path)
    print(f"[preprocess] Loaded {len(df):,} rows from {path}")
    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()

    # ── timestamp features ────────────────────────────────────────────────
    if "timestamp" in df.columns:
        df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")
        df["transaction_hour"] = df["timestamp"].dt.hour
        df["transaction_day"] = df["timestamp"].dt.dayofweek
    else:
        if "transaction_hour" not in df.columns:
            df["transaction_hour"] = 12
        if "transaction_day" not in df.columns:
            df["transaction_day"] = 0

    # ── amount deviation ──────────────────────────────────────────────────
    if "avg_txn_amount_7d" in df.columns and "transaction_amount" in df.columns:
        df["amount_deviation_from_avg"] = (
            df["transaction_amount"] - df["avg_txn_amount_7d"]
        ).abs()
    elif "amount_deviation_from_avg" not in df.columns:
        df["amount_deviation_from_avg"] = 0.0

    # ── fill sensible defaults for optional columns ───────────────────────
    defaults = {
        "sender_account_age_days": 365,
        "receiver_account_age_days": 180,
        "sender_txn_count_24h": 1,
        "receiver_txn_count_24h": 1,
        "avg_txn_amount_7d": df.get("transaction_amount", pd.Series([500])).median(),
        "transaction_type": "P2P",
        "device_type": "mobile",
        "payment_platform": "gpay",
    }
    for col, val in defaults.items():
        if col not in df.columns:
            df[col] = val

    # ── drop raw timestamp ─────────────────────────────────────────────────
    df.drop(columns=["timestamp"], errors="ignore", inplace=True)

    print(f"[preprocess] Feature engineering complete. Shape: {df.shape}")
    return df


def save_processed(df: pd.DataFrame, path: str = OUT_PATH):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    df.to_csv(path, index=False)
    print(f"[preprocess] Saved processed data to {path}")


def run_pipeline(raw_path: str = RAW_PATH, out_path: str = OUT_PATH) -> pd.DataFrame:
    df = load_raw(raw_path)
    df = engineer_features(df)
    save_processed(df, out_path)
    return df


if __name__ == "__main__":
    run_pipeline()
