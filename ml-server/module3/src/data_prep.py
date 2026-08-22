"""
src/data_prep.py
Data Preparation Pipeline for UPI Fraud Detection:
1. Loads raw dataset from data/fraud_dataset.csv
2. Drops non-generalizable/high-cardinality/zero-variance columns
3. One-hot encodes remaining categorical columns
4. Performs stratified 80/20 train-test split (random_state=42)
5. Saves processed splits (X_train, X_test, y_train, y_test) & feature_columns to data/processed/*.pkl
6. Prints final shapes and class distributions (% fraud)
"""

import os
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split

# ── Paths configuration ───────────────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
PROCESSED_DIR = os.path.join(DATA_DIR, "processed")
RAW_DATA_PATH = os.path.join(DATA_DIR, "fraud_dataset.csv")

# ── Columns to drop ──────────────────────────────────────────────────────────
# High-cardinality IDs / free text / near-empty / zero-variance (confirmed by EDA)
COLUMNS_TO_DROP = [
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

TARGET_COLUMN = "is_fraud"


def prepare_data(
    raw_path: str = RAW_DATA_PATH,
    processed_dir: str = PROCESSED_DIR,
    test_size: float = 0.2,
    random_state: int = 42,
):
    # 1. Load data
    print(f"Loading raw dataset from: {raw_path}")
    df = pd.read_csv(raw_path)
    print(f"Loaded raw dataset shape: {df.shape}")

    # 2. Drop non-generalizable columns
    cols_to_drop_present = [col for col in COLUMNS_TO_DROP if col in df.columns]
    df = df.drop(columns=cols_to_drop_present)
    print(f"Dropped {len(cols_to_drop_present)} uninformative columns. Remaining columns: {df.shape[1]}")

    if TARGET_COLUMN not in df.columns:
        raise ValueError(f"Target column '{TARGET_COLUMN}' not found in dataset.")

    # Separate features and target
    y = df[TARGET_COLUMN]
    X = df.drop(columns=[TARGET_COLUMN])

    # 3. One-hot encode remaining categorical columns
    print("One-hot encoding categorical/text features...")
    X_encoded = pd.get_dummies(X, drop_first=True, dtype=int)
    feature_columns = list(X_encoded.columns)
    print(f"Total encoded features: {len(feature_columns)}")

    # 4. Stratified 80/20 train/test split
    print(f"Splitting dataset (train={1-test_size:.0%}, test={test_size:.0%}, stratify={TARGET_COLUMN}, random_state={random_state})...")
    X_train, X_test, y_train, y_test = train_test_split(
        X_encoded,
        y,
        test_size=test_size,
        stratify=y,
        random_state=random_state,
    )

    # 5. Save processed files using joblib
    os.makedirs(processed_dir, exist_ok=True)
    joblib.dump(X_train, os.path.join(processed_dir, "X_train.pkl"))
    joblib.dump(X_test, os.path.join(processed_dir, "X_test.pkl"))
    joblib.dump(y_train, os.path.join(processed_dir, "y_train.pkl"))
    joblib.dump(y_test, os.path.join(processed_dir, "y_test.pkl"))
    joblib.dump(feature_columns, os.path.join(processed_dir, "feature_columns.pkl"))

    print(f"Processed splits & feature column names saved to: {processed_dir}")

    # 6. Print summary statistics
    train_fraud_count = int(y_train.sum())
    train_total = len(y_train)
    train_fraud_pct = (train_fraud_count / train_total) * 100

    test_fraud_count = int(y_test.sum())
    test_total = len(y_test)
    test_fraud_pct = (test_fraud_count / test_total) * 100

    print("\n" + "=" * 55)
    print("              DATA PREPARATION SUMMARY              ")
    print("=" * 55)
    print(f"X_train Shape       : {X_train.shape}")
    print(f"X_test Shape        : {X_test.shape}")
    print(f"Total Features      : {len(feature_columns)}")
    print("-" * 55)
    print(f"Train Set Size      : {train_total:,} rows")
    print(f"Train Fraud Count   : {train_fraud_count:,} ({train_fraud_pct:.2f}%)")
    print(f"Train Legit Count   : {train_total - train_fraud_count:,} ({100 - train_fraud_pct:.2f}%)")
    print("-" * 55)
    print(f"Test Set Size       : {test_total:,} rows")
    print(f"Test Fraud Count    : {test_fraud_count:,} ({test_fraud_pct:.2f}%)")
    print(f"Test Legit Count    : {test_total - test_fraud_count:,} ({100 - test_fraud_pct:.2f}%)")
    print("=" * 55 + "\n")

    return X_train, X_test, y_train, y_test, feature_columns


if __name__ == "__main__":
    prepare_data()
