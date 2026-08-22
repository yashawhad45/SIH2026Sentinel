"""
module3/main.py
FastAPI server for UPI Fraud Detection (Sentinel Module 3).

Endpoints:
  GET  /              — health check
  POST /train         — train model on uploaded CSV
  POST /predict       — predict fraud for a single transaction JSON
  GET  /model-info    — returns model status and feature list
"""

import io
import os
import pandas as pd
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional

from model import UPIFraudModel, NUMERIC_FEATURES, CATEGORICAL_FEATURES, LABEL_COL

app = FastAPI(
    title="Sentinel UPI Fraud Detection API",
    description="Module 3 — Detects fraudulent UPI transactions using XGBoost.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global model instance — loaded lazily on first predict, or after /train
fraud_model = UPIFraudModel()


# ── request schema ────────────────────────────────────────────────────────────

class TransactionRecord(BaseModel):
    transaction_amount: float
    transaction_hour: int
    transaction_day: int
    sender_account_age_days: float
    receiver_account_age_days: float
    sender_txn_count_24h: int
    receiver_txn_count_24h: int
    avg_txn_amount_7d: float
    amount_deviation_from_avg: float
    transaction_type: Optional[str] = "P2P"
    device_type: Optional[str] = "mobile"
    payment_platform: Optional[str] = "gpay"


# ── routes ────────────────────────────────────────────────────────────────────

@app.get("/")
def health_check():
    return {
        "message": "Sentinel UPI Fraud Detection API is running.",
        "module": 3,
        "status": "online",
    }


@app.post("/train")
async def train_model(file: UploadFile = File(...)):
    """
    Upload a labelled CSV (must contain 'is_fraud' column) to train the model.
    The CSV should be placed in data/ before uploading or sent directly here.
    """
    if not file.filename.endswith(".csv"):
        raise HTTPException(status_code=400, detail="Please upload a CSV file.")

    try:
        contents = await file.read()
        df = pd.read_csv(io.BytesIO(contents))

        if LABEL_COL not in df.columns:
            raise HTTPException(
                status_code=422,
                detail=f"CSV must contain a '{LABEL_COL}' column (0 = legit, 1 = fraud).",
            )

        metrics = fraud_model.train(df)
        return {
            "status": "success",
            "rows_trained_on": len(df),
            "fraud_samples": int(df[LABEL_COL].sum()),
            "metrics": metrics,
        }
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Training failed: {str(exc)}")


@app.post("/predict")
def predict_fraud(transaction: TransactionRecord):
    """
    Accepts a single UPI transaction record and returns fraud probability.
    """
    try:
        result = fraud_model.predict(transaction.model_dump())

        if not result["success"]:
            raise HTTPException(status_code=503, detail=result["error"])

        return {
            "fraud_probability": result["fraud_probability"],
            "is_fraud": result["is_fraud"],
            "risk_level": result["risk_level"],
        }
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/model-info")
def model_info():
    return {
        "module": 3,
        "model_type": "XGBoostClassifier",
        "is_trained": fraud_model.is_trained,
        "numeric_features": NUMERIC_FEATURES,
        "categorical_features": CATEGORICAL_FEATURES,
        "label_column": LABEL_COL,
        "thresholds": {
            "fraud_flag": 0.5,
            "risk_medium": 0.4,
            "risk_high": 0.75,
        },
    }
