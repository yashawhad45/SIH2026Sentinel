"""
module3/demo/demo.py
Quick end-to-end demo — trains on synthetic data and calls the local API.
Run AFTER starting the API server:
    uvicorn main:app --reload --port 8003

Then in a second terminal:
    python demo/demo.py
"""

import requests
import json

API_URL = "http://127.0.0.1:8003"

LEGIT_TXN = {
    "transaction_amount": 850.0,
    "transaction_hour": 14,
    "transaction_day": 2,
    "sender_account_age_days": 730.0,
    "receiver_account_age_days": 500.0,
    "sender_txn_count_24h": 3,
    "receiver_txn_count_24h": 2,
    "avg_txn_amount_7d": 900.0,
    "amount_deviation_from_avg": 50.0,
    "transaction_type": "P2P",
    "device_type": "mobile",
    "payment_platform": "gpay",
}

SUSPICIOUS_TXN = {
    "transaction_amount": 98000.0,
    "transaction_hour": 2,           # 2 AM
    "transaction_day": 6,
    "sender_account_age_days": 1.0,  # brand-new account
    "receiver_account_age_days": 2.0,
    "sender_txn_count_24h": 45,      # unusually high velocity
    "receiver_txn_count_24h": 40,
    "avg_txn_amount_7d": 300.0,
    "amount_deviation_from_avg": 97700.0,
    "transaction_type": "P2P",
    "device_type": "web",
    "payment_platform": "bhim",
}


def print_result(label: str, result: dict):
    print(f"\n{'='*50}")
    print(f"  {label}")
    print(f"{'='*50}")
    print(f"  Fraud Probability : {result.get('fraud_probability', 'N/A')}")
    print(f"  Is Fraud          : {result.get('is_fraud', 'N/A')}")
    print(f"  Risk Level        : {result.get('risk_level', 'N/A')}")


def main():
    print("\n[demo] Checking API health...")
    r = requests.get(f"{API_URL}/")
    print(f"  Response: {r.json()['message']}")

    print("\n[demo] Predicting on a LEGIT transaction...")
    r = requests.post(f"{API_URL}/predict", json=LEGIT_TXN)
    if r.status_code == 200:
        print_result("LEGIT TRANSACTION", r.json())
    else:
        print(f"  Error: {r.status_code} — {r.text}")

    print("\n[demo] Predicting on a SUSPICIOUS transaction...")
    r = requests.post(f"{API_URL}/predict", json=SUSPICIOUS_TXN)
    if r.status_code == 200:
        print_result("SUSPICIOUS TRANSACTION", r.json())
    else:
        print(f"  Error: {r.status_code} — {r.text}")


if __name__ == "__main__":
    main()
