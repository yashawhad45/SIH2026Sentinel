"""
tests/test_api.py
Pre-Demo Health Check & End-to-End API Test Script:
- Verifies FastAPI server connectivity at http://localhost:8000/health
- Sends the 3 canonical benchmark scenarios (Low Risk, Borderline Caution, High Risk) to /check-transaction
- Formats and displays the risk score, colored risk tier, contextual flags, and explanation
- Can be run directly (python tests/test_api.py) with zero extra dependencies beyond requests
"""

import sys
import json
import requests

API_BASE_URL = "http://localhost:8000"
CHECK_ENDPOINT = f"{API_BASE_URL}/check-transaction"
HEALTH_ENDPOINT = f"{API_BASE_URL}/health"

# ── 3 Benchmark Test Transactions (Identical to predict.py) ───────────────────
TXN_LOW_RISK = {
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

TXN_BORDERLINE_CAUTION = {
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

TXN_HIGH_RISK = {
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


def print_formatted_result(scenario_name: str, status_code: int, response_data: dict):
    """Prints clean terminal box for test results."""
    tier = response_data.get("risk_tier", "unknown").upper()
    score = response_data.get("risk_score", "N/A")
    flags = response_data.get("flags", [])
    explanation = response_data.get("explanation", "")

    tier_tag = (
        "[LOW RISK]" if tier == "LOW"
        else "[CAUTION]" if tier == "CAUTION"
        else "[HIGH RISK]"
    )

    print("\n" + "=" * 76)
    print(f"  {scenario_name}")
    print("=" * 76)
    print(f"  HTTP Status   : {status_code} OK")
    print(f"  Risk Score    : {score} / 100")
    print(f"  Risk Tier     : {tier_tag}")
    print(f"  Explanation   : {explanation}")
    print("  Triggered Flags:")
    if flags:
        for idx, flag in enumerate(flags, start=1):
            print(f"    {idx}. {flag}")
    else:
        print("    (None - all security baseline checks passed)")
    print("-" * 76)


def run_all_checks():
    print("*" * 76)
    print("      SENTINEL UPI MODULE 3 -- LIVE API END-TO-END VALIDATION      ")
    print("*" * 76)

    # 1. Health Probe
    try:
        h_res = requests.get(HEALTH_ENDPOINT, timeout=5)
        print(f"\n[OK] Server Health Check: HTTP {h_res.status_code} -> {h_res.json()}")
    except requests.exceptions.ConnectionError:
        print(f"\n[ERROR] Unable to connect to server at {API_BASE_URL}!")
        print("Please start the server first with:")
        print("    uvicorn src.api:app --host 0.0.0.0 --port 8000\n")
        sys.exit(1)

    # 2. Evaluate Scenarios
    scenarios = [
        ("SCENARIO 1: CLEARLY LOW RISK (Everyday Coffee Payment - Rs. 450)", TXN_LOW_RISK),
        ("SCENARIO 2: BORDERLINE / CAUTION (Late Night / Unverified Payee - Rs. 3,200)", TXN_BORDERLINE_CAUTION),
        ("SCENARIO 3: CLEARLY HIGH RISK (Phishing Link / AnyDesk Active / 3 AM - Rs. 75,000)", TXN_HIGH_RISK),
    ]

    all_passed = True
    for name, payload in scenarios:
        try:
            res = requests.post(CHECK_ENDPOINT, json=payload, timeout=5)
            if res.status_code == 200:
                print_formatted_result(name, res.status_code, res.json())
            else:
                print(f"\n[ERROR] {name}: Failed with HTTP {res.status_code}: {res.text}")
                all_passed = False
        except Exception as exc:
            print(f"\n[ERROR] {name}: Request error: {exc}")
            all_passed = False

    if all_passed:
        print("\n[ALL CHECKS PASSED] Backend is live, healthy, and ready for demo!\n")
    else:
        print("\n[FAILED] One or more checks encountered errors.\n")


if __name__ == "__main__":
    run_all_checks()
