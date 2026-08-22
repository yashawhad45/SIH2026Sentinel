# Module 3 — UPI Fraud Detection

> **Sentinel** | SIH 2026 | ML Server — Module 3

Detects fraudulent UPI transactions in real-time using an XGBoost binary classifier served via FastAPI.

---

## 📁 Structure

```
module3/
├── data/
│   ├── fraud_dataset.csv        ← place your dataset here
│   └── processed/               ← auto-generated after preprocessing
├── models/                      ← saved XGBoost + scaler weights
├── outputs/                     ← plots (ROC, confusion matrix, feature importance)
├── src/
│   ├── preprocess.py            ← feature engineering pipeline
│   └── train.py                 ← standalone training script
├── tests/
│   └── test_model.py            ← pytest unit tests
├── demo/
│   └── demo.py                  ← hits the live API with example transactions
├── main.py                      ← FastAPI app (3 endpoints)
├── model.py                     ← UPIFraudModel class (XGBoost wrapper)
└── requirements.txt
```

---

## 🚀 Quick Start

```bash
# 1. Create and activate venv
python -m venv venv
.\venv\Scripts\Activate.ps1   # Windows
# source venv/bin/activate    # Linux/Mac

# 2. Install dependencies
pip install -r requirements.txt

# 3. (Optional) Preprocess the raw dataset
python src/preprocess.py

# 4. Train the model via CLI
python src/train.py --plots

# 5. Start the API server
uvicorn main:app --reload --port 8003
```

---

## 🌐 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/` | Health check |
| `POST` | `/train` | Upload CSV to train the model |
| `POST` | `/predict` | Predict fraud for a single transaction JSON |
| `GET`  | `/model-info` | Returns model status and feature list |

### Example `/predict` request

```json
{
  "transaction_amount": 98000.0,
  "transaction_hour": 2,
  "transaction_day": 6,
  "sender_account_age_days": 1.0,
  "receiver_account_age_days": 2.0,
  "sender_txn_count_24h": 45,
  "receiver_txn_count_24h": 40,
  "avg_txn_amount_7d": 300.0,
  "amount_deviation_from_avg": 97700.0,
  "transaction_type": "P2P",
  "device_type": "mobile",
  "payment_platform": "gpay"
}
```

### Example response

```json
{
  "fraud_probability": 0.9312,
  "is_fraud": true,
  "risk_level": "HIGH"
}
```

---

## 🧪 Running Tests

```bash
pip install pytest
pytest tests/ -v
```

---

## 📊 Model Details

- **Algorithm**: XGBoost Classifier
- **Imbalance handling**: `scale_pos_weight=10` (fraud << legit)
- **Features**: 9 numeric + 3 categorical
- **Risk levels**: `LOW` (< 0.4) · `MEDIUM` (0.4–0.75) · `HIGH` (> 0.75)
