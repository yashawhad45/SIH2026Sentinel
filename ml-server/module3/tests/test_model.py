"""
module3/tests/test_model.py
Basic unit tests for the UPI Fraud Detection model.
Run with:  pytest tests/
"""

import sys
import os
import pytest
import pandas as pd
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from model import UPIFraudModel, NUMERIC_FEATURES, CATEGORICAL_FEATURES, LABEL_COL


def _make_dummy_df(n: int = 200, fraud_ratio: float = 0.15) -> pd.DataFrame:
    """Create a minimal synthetic dataset for testing."""
    rng = np.random.default_rng(42)
    n_fraud = max(1, int(n * fraud_ratio))
    n_legit = n - n_fraud

    records = []
    for label in [0] * n_legit + [1] * n_fraud:
        records.append({
            "transaction_amount": rng.uniform(100, 50000),
            "transaction_hour": int(rng.integers(0, 24)),
            "transaction_day": int(rng.integers(0, 7)),
            "sender_account_age_days": rng.uniform(1, 3650),
            "receiver_account_age_days": rng.uniform(1, 3650),
            "sender_txn_count_24h": int(rng.integers(1, 50)),
            "receiver_txn_count_24h": int(rng.integers(1, 50)),
            "avg_txn_amount_7d": rng.uniform(100, 10000),
            "amount_deviation_from_avg": rng.uniform(0, 5000),
            "transaction_type": rng.choice(["P2P", "P2M", "RECHARGE"]),
            "device_type": rng.choice(["mobile", "web", "pos"]),
            "payment_platform": rng.choice(["gpay", "phonepe", "paytm", "bhim"]),
            LABEL_COL: label,
        })
    return pd.DataFrame(records)


class TestUPIFraudModel:

    def test_train_returns_metrics(self):
        model = UPIFraudModel()
        df = _make_dummy_df()
        metrics = model.train(df)
        assert metrics["status"] == "trained"
        assert "val_auc" in metrics
        assert 0.0 <= metrics["val_auc"] <= 1.0

    def test_predict_after_train(self):
        model = UPIFraudModel()
        df = _make_dummy_df()
        model.train(df)

        record = {
            "transaction_amount": 95000.0,   # suspiciously large
            "transaction_hour": 3,            # 3 AM
            "transaction_day": 6,
            "sender_account_age_days": 2.0,   # brand-new account
            "receiver_account_age_days": 1.0,
            "sender_txn_count_24h": 30,
            "receiver_txn_count_24h": 25,
            "avg_txn_amount_7d": 500.0,
            "amount_deviation_from_avg": 94500.0,
            "transaction_type": "P2P",
            "device_type": "mobile",
            "payment_platform": "gpay",
        }
        result = model.predict(record)
        assert result["success"] is True
        assert "fraud_probability" in result
        assert isinstance(result["is_fraud"], bool)
        assert result["risk_level"] in ("LOW", "MEDIUM", "HIGH")

    def test_predict_range(self):
        model = UPIFraudModel()
        df = _make_dummy_df()
        model.train(df)

        for _ in range(10):
            record = _make_dummy_df(n=1).drop(columns=[LABEL_COL]).iloc[0].to_dict()
            result = model.predict(record)
            assert result["success"] is True
            assert 0.0 <= result["fraud_probability"] <= 1.0

    def test_missing_label_raises(self):
        model = UPIFraudModel()
        df = _make_dummy_df().drop(columns=[LABEL_COL])
        with pytest.raises(ValueError):
            model.train(df)

    def test_unknown_categorical_handled(self):
        model = UPIFraudModel()
        df = _make_dummy_df()
        model.train(df)
        record = _make_dummy_df(n=1).drop(columns=[LABEL_COL]).iloc[0].to_dict()
        record["payment_platform"] = "some_unknown_platform"
        result = model.predict(record)
        assert result["success"] is True
