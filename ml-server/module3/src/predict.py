"""
src/predict.py
Real-time UPI Transaction Inference & Risk Scoring Engine:
1. Loads trained model (models/upi_model.pkl) and feature column schema (data/processed/feature_columns.pkl)
2. Preprocesses and one-hot encodes raw transaction payloads identically to data_prep.py
3. Calculates fraud probability score (0-100) via predict_proba
4. Identifies contextual rule-based security flags
5. Returns a structured risk assessment with tier ("low" | "caution" | "high"), flags, and explanation
"""

import os
import re
import json
import joblib
import pandas as pd
from typing import Dict, Any, List

# ── Paths configuration ───────────────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS_DIR = os.path.join(BASE_DIR, "models")
DATA_DIR = os.path.join(BASE_DIR, "data")
PROCESSED_DIR = os.path.join(DATA_DIR, "processed")

MODEL_PATH = os.path.join(MODELS_DIR, "upi_model.pkl")
FEATURE_COLUMNS_PATH = os.path.join(PROCESSED_DIR, "feature_columns.pkl")
LEAKY_COLUMNS_PATH = os.path.join(DATA_DIR, "leaky_columns.json")

# Columns to drop — same uninformative/zero-variance set as in data_prep.py
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
    "is_fraud",
    "business_name_match",
]

# Lazy-loaded globals for fast warm inference
_MODEL = None
_FEATURE_COLUMNS = None


def _get_artifacts():
    """Load model and feature column names into memory (cached)."""
    global _MODEL, _FEATURE_COLUMNS
    if _MODEL is None:
        if not os.path.exists(MODEL_PATH):
            raise FileNotFoundError(f"Model file not found at: {MODEL_PATH}. Please run train_model.py first.")
        _MODEL = joblib.load(MODEL_PATH)

    if _FEATURE_COLUMNS is None:
        if not os.path.exists(FEATURE_COLUMNS_PATH):
            raise FileNotFoundError(f"Feature columns file not found at: {FEATURE_COLUMNS_PATH}. Please run data_prep.py first.")
        _FEATURE_COLUMNS = joblib.load(FEATURE_COLUMNS_PATH)

    return _MODEL, _FEATURE_COLUMNS


def _sanitize_column_name(name: str) -> str:
    """Sanitize column names for compatibility with tree models."""
    return re.sub(r"[\[\]<>]", "_", str(name))


def _extract_security_flags(transaction: Dict[str, Any]) -> List[str]:
    """Inspect raw transaction metadata and generate human-readable security flags."""
    flags = []

    # 1. Payee verification
    if str(transaction.get("handle_verification_status", "")).lower() == "unverified":
        flags.append("Unverified payee UPI handle")

    # 2. Receiver account age
    receiver_age = transaction.get("receiver_account_age", 365)
    if receiver_age is not None and float(receiver_age) <= 7:
        flags.append(f"Newly created receiver account ({int(receiver_age)} days old)")

    # 3. Transaction amount anomalies
    if transaction.get("unusual_transaction_amount_flag") == 1:
        flags.append("Transaction amount deviates significantly from user baseline")
    elif float(transaction.get("amount", 0)) >= 50000:
        flags.append("High-value transaction amount (> Rs. 50,000)")

    # 4. Unusual transaction hour (e.g., late night 11 PM - 5 AM)
    hour = transaction.get("transaction_time_of_day")
    if hour is not None and int(hour) in [0, 1, 2, 3, 4, 23]:
        flags.append(f"Unusual transaction hour ({hour:02d}:00 hrs)")

    # 5. Device & Network indicators
    if transaction.get("unusual_device_flag") == 1:
        flags.append("Transaction initiated from unrecognized or new device")
    if transaction.get("unusual_ip_flag") == 1:
        flags.append("Unusual IP subnet or suspicious network origin")
    if transaction.get("unusual_location_flag") == 1 or float(transaction.get("geographic_disparity", 0)) > 5000:
        flags.append("High geographic disparity between location and IP origin")

    # 6. Referral & Session source
    if str(transaction.get("session_source", "")).lower() == "link":
        flags.append("Transaction originated from external link / phishing vector")

    # 7. Social engineering & pressure
    if int(transaction.get("time_pressure_indicators", 0)) > 0:
        flags.append("Urgency or high time-pressure tactics detected")

    # 8. Authentication & Screen sharing
    if int(transaction.get("authentication_attempt_count", 1)) > 2:
        flags.append("Multiple failed authentication attempts detected")

    screen_share = str(transaction.get("recognized_screen_sharing_apps", "")).strip()
    if screen_share and screen_share not in ["[]", "none", "None", "nan"]:
        flags.append("Active screen-sharing application detected")

    return flags


def _generate_explanation(risk_tier: str, flags: List[str]) -> str:
    """Generate a single concise explanatory sentence based on tier and top flags."""
    if risk_tier == "low":
        if not flags:
            return "Transaction verified as low risk with standard user patterns and legitimate payee credentials."
        return f"Transaction is low risk, though noted: {flags[0].lower()}."

    if risk_tier == "caution":
        if flags:
            top_flags = "; ".join(flags[:2])
            return f"Caution advised: moderate risk detected due to {top_flags.lower()}."
        return "Caution advised: transaction shows moderate behavioral deviations from baseline."

    # High risk
    if flags:
        top_flags = "; ".join(flags[:2])
        return f"High risk transaction flagged due to {top_flags.lower()}."
    return "High risk transaction flagged due to anomalous behavioral and network patterns."


def check_transaction(transaction: Dict[str, Any]) -> Dict[str, Any]:
    """
    Evaluates a single raw UPI transaction dictionary and returns risk assessment.

    Returns:
        {
            "risk_score": int (0-100),
            "risk_tier": "low" | "caution" | "high",
            "flags": List[str],
            "explanation": str
        }
    """
    model, feature_columns = _get_artifacts()

    # 1. Preprocess raw transaction input
    raw_df = pd.DataFrame([transaction])

    # Load leaky columns dynamically
    if os.path.exists(LEAKY_COLUMNS_PATH):
        with open(LEAKY_COLUMNS_PATH, "r") as f:
            leaky_cols = json.load(f)
        drop_list = list(set(COLUMNS_TO_DROP + leaky_cols))
    else:
        drop_list = COLUMNS_TO_DROP

    # Drop non-generalizable and leaky columns present in the input
    cols_to_drop_present = [col for col in drop_list if col in raw_df.columns]
    df_clean = raw_df.drop(columns=cols_to_drop_present)


    # 2. One-hot encode remaining categorical columns
    # In single-row inference, dummy indicators match presence of categories
    df_encoded = pd.get_dummies(df_clean, dtype=int)
    df_encoded.columns = [_sanitize_column_name(col) for col in df_encoded.columns]

    # 3. Align with training feature columns (fill missing dummy variables with 0)
    df_aligned = df_encoded.reindex(columns=feature_columns, fill_value=0)

    # 4. Predict probability
    prob_array = model.predict_proba(df_aligned)
    fraud_prob = float(prob_array[0][1])

    # 5. Compute risk metrics
    risk_score = int(round(fraud_prob * 100))

    if risk_score < 40:
        risk_tier = "low"
    elif risk_score <= 70:
        risk_tier = "caution"
    else:
        risk_tier = "high"

    # 6. Extract rule-based security flags and explanation
    flags = _extract_security_flags(transaction)
    explanation = _generate_explanation(risk_tier, flags)

    return {
        "risk_score": risk_score,
        "risk_tier": risk_tier,
        "flags": flags,
        "explanation": explanation,
    }


if __name__ == "__main__":
    print("=" * 75)
    print("           SENTINEL UPI FRAUD DETECTION -- REAL-TIME INFERENCE           ")
    print("=" * 75)

    # ── Example 1: Clearly Low Risk ──────────────────────────────────────────
    txn_low_risk = {
        "transaction_id": "txn-legit-001",
        "user_id": "user_rahul_99",
        "merchant_id": "merch_starbucks_45",
        "amount": 450.0,
        "session_duration": 180,
        "authentication_attempts": 1,
        "receiver_account_age": 420,
        "receiver_transaction_history": 25,
        "transaction_amount_vs_sender_history": 0.95,
        "geographic_disparity": 12.5,
        "transaction_time_of_day": 15,          # 3:00 PM
        "merchant_category_code": "food",
        "session_source": "app",
        "time_between_link_click_and_transaction": 0,
        "unusual_device_flag": 0,
        "unusual_ip_flag": 0,
        "unusual_location_flag": 0,
        "dns_lookup_age": 0,
        "recent_app_installs": "[]",
        "input_timing_consistency": 0.95,
        "app_switching_frequency": 0,
        "keyboard_input_speed": 1.2,
        "input_pause_patterns": 0.05,
        "permissions_granted": "[]",
        "screen_active_time": 195,
        "geographic_location_vs_ip": 12.5,
        "background_data_usage": 0.15,
        "recognized_screen_sharing_apps": "[]",
        "authentication_attempt_count": 1,
        "time_between_otp_generation_and_input": 0,
        "pin_entry_method": "manual",
        "pin_entry_speed": 1.3,
        "unusual_transaction_amount_flag": 0,
        "otp_request_frequency": 0,
        "otp_request_device_consistency": 1,
        "transaction_velocity": 1,
        "failed_transaction_count": 0,
        "authorization_method": "pin",
        "transaction_type": "payment",
        "request_description_keywords": "[]",
        "request_amount_roundness": 1.0,
        "request_frequency": 0,
        "request_acceptance_rate": 0.0,
        "time_to_respond_to_request": 0,
        "time_pressure_indicators": 0,
        "requester_account_age": 0,
        "handle_similarity_score": 0.0,
        "handle_typo_analysis": "none",
        "handle_transaction_history": 15,
        "business_name_match": "match",
        "handle_registration_pattern": "standard",
        "handle_to_description_consistency": 1,
        "handle_verification_status": "verified",
    }

    # ── Example 2: Borderline / Caution ──────────────────────────────────────
    txn_borderline = {
        "transaction_id": "txn-caution-002",
        "user_id": "user_priya_21",
        "merchant_id": "merch_unknown_88",
        "amount": 3200.0,
        "session_duration": 60,
        "authentication_attempts": 1,
        "receiver_account_age": 45,
        "receiver_transaction_history": 5,
        "transaction_amount_vs_sender_history": 2.1,
        "geographic_disparity": 150.0,
        "transaction_time_of_day": 23,          # 11:00 PM
        "merchant_category_code": "unknown",
        "session_source": "app",
        "time_between_link_click_and_transaction": 0,
        "unusual_device_flag": 0,
        "unusual_ip_flag": 0,
        "unusual_location_flag": 0,
        "dns_lookup_age": 0,
        "recent_app_installs": "[]",
        "input_timing_consistency": 0.85,
        "app_switching_frequency": 0,
        "keyboard_input_speed": 1.0,
        "input_pause_patterns": 0.1,
        "permissions_granted": "[]",
        "screen_active_time": 90,
        "geographic_location_vs_ip": 150.0,
        "background_data_usage": 0.2,
        "recognized_screen_sharing_apps": "[]",
        "authentication_attempt_count": 1,
        "time_between_otp_generation_and_input": 0,
        "pin_entry_method": "manual",
        "pin_entry_speed": 1.1,
        "unusual_transaction_amount_flag": 0,
        "otp_request_frequency": 0,
        "otp_request_device_consistency": 1,
        "transaction_velocity": 2,
        "failed_transaction_count": 0,
        "authorization_method": "pin",
        "transaction_type": "payment",
        "request_description_keywords": "[]",
        "request_amount_roundness": 1.0,
        "request_frequency": 0,
        "request_acceptance_rate": 0.0,
        "time_to_respond_to_request": 0,
        "time_pressure_indicators": 0,
        "requester_account_age": 45,
        "handle_similarity_score": 0.0,
        "handle_typo_analysis": "none",
        "handle_transaction_history": 5,
        "business_name_match": "none",
        "handle_registration_pattern": "none",
        "handle_to_description_consistency": 0,
        "handle_verification_status": "unverified",
    }

    # ── Example 3: Clearly High Risk ─────────────────────────────────────────
    txn_high_risk = {
        "transaction_id": "txn-fraud-003",
        "user_id": "user_victim_10",
        "merchant_id": "scammer_target_99",
        "amount": 75000.0,
        "session_duration": 12,
        "authentication_attempts": 3,
        "receiver_account_age": 0,              # Brand new account
        "receiver_transaction_history": 0,
        "transaction_amount_vs_sender_history": 15.5,
        "geographic_disparity": 9800.0,
        "transaction_time_of_day": 3,           # 3:00 AM
        "merchant_category_code": "unknown",
        "session_source": "link",               # Phishing link
        "time_between_link_click_and_transaction": 6,
        "unusual_device_flag": 1,
        "unusual_ip_flag": 1,
        "unusual_location_flag": 1,
        "dns_lookup_age": 9,
        "recent_app_installs": "['AnyDesk']",
        "input_timing_consistency": 0.45,
        "app_switching_frequency": 4,
        "keyboard_input_speed": 0.42,
        "input_pause_patterns": 0.58,
        "permissions_granted": "['accessibility', 'overlay']",
        "screen_active_time": 25,
        "geographic_location_vs_ip": 16400.0,
        "background_data_usage": 0.88,
        "recognized_screen_sharing_apps": "['AnyDesk']",
        "authentication_attempt_count": 3,
        "time_between_otp_generation_and_input": 1,
        "pin_entry_method": "manual",
        "pin_entry_speed": 0.82,
        "unusual_transaction_amount_flag": 1,
        "otp_request_frequency": 3,
        "otp_request_device_consistency": 0,
        "transaction_velocity": 6,
        "failed_transaction_count": 2,
        "authorization_method": "pin",
        "transaction_type": "payment",
        "request_description_keywords": "['urgent', 'reward']",
        "request_amount_roundness": 1.0,
        "request_frequency": 4,
        "request_acceptance_rate": 0.0,
        "time_to_respond_to_request": 3,
        "time_pressure_indicators": 2,
        "requester_account_age": 0,
        "handle_similarity_score": 0.85,
        "handle_typo_analysis": "suspicious",
        "handle_transaction_history": 0,
        "business_name_match": "mismatch",
        "handle_registration_pattern": "random_alphanumeric",
        "handle_to_description_consistency": 0,
        "handle_verification_status": "unverified",
    }

    test_cases = [
        ("SCENARIO 1: CLEARLY LOW RISK (Everyday Coffee Payment)", txn_low_risk),
        ("SCENARIO 2: BORDERLINE / CAUTION (Late Night / Unverified Payee)", txn_borderline),
        ("SCENARIO 3: CLEARLY HIGH RISK (Phishing Link / Screen Share / 3 AM Spike)", txn_high_risk),
    ]

    for title, txn in test_cases:
        result = check_transaction(txn)
        print(f"\n>>> {title}")
        print("-" * 75)
        print(json.dumps(result, indent=4))

    print("\n" + "=" * 75 + "\n")
