"""
src/api.py
FastAPI Service for Sentinel UPI Fraud Detection (Module 3):
- Exposes GET /health (health check)
- Exposes POST /check-transaction (real-time transaction risk evaluation)
- Configured with CORS for frontend demo integration
- Powered by trained XGBoost/RandomForest model with contextual rule flags
"""

import os
import sys
from typing import List, Optional
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# Ensure local module imports work
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from src.predict import check_transaction

# ── FastAPI App initialization ────────────────────────────────────────────────
app = FastAPI(
    title="Sentinel UPI Fraud Detection API",
    description="Real-time UPI transaction risk scoring and anomaly detection microservice (Module 3).",
    version="1.0.0",
)

# ── CORS Middleware ───────────────────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Pydantic Request & Response Models ────────────────────────────────────────
class TransactionInput(BaseModel):
    """Raw transaction fields expected by check_transaction (excluding dropped columns)."""
    amount: float = Field(default=500.0, description="Transaction amount in INR")
    session_duration: int = Field(default=120, description="App session duration in seconds")
    authentication_attempts: int = Field(default=1, description="Number of auth attempts in session")
    receiver_account_age: int = Field(default=365, description="Age of receiver account in days")
    receiver_transaction_history: int = Field(default=10, description="Receiver total past transaction count")
    transaction_amount_vs_sender_history: float = Field(default=1.0, description="Ratio of current amount to sender's average")
    geographic_disparity: float = Field(default=10.0, description="Distance/disparity score for geolocation")
    transaction_time_of_day: int = Field(default=14, description="Hour of transaction (0-23)")
    merchant_category_code: str = Field(default="food", description="Merchant category or 'unknown'")
    session_source: str = Field(default="app", description="Session origin: 'app' | 'link' | 'web'")
    time_between_link_click_and_transaction: int = Field(default=0, description="Seconds between referral link and payment")
    unusual_device_flag: int = Field(default=0, description="1 if unrecognized device, else 0")
    unusual_ip_flag: int = Field(default=0, description="1 if unrecognized/suspicious IP, else 0")
    unusual_location_flag: int = Field(default=0, description="1 if anomalous location, else 0")
    dns_lookup_age: int = Field(default=0, description="DNS lookup age in seconds")
    recent_app_installs: str = Field(default="[]", description="JSON list of recently installed packages")
    input_timing_consistency: float = Field(default=0.9, description="Consistency score of user keystroke cadence")
    app_switching_frequency: int = Field(default=0, description="App backgrounding / switching count")
    keyboard_input_speed: float = Field(default=1.1, description="Keystrokes per second")
    input_pause_patterns: float = Field(default=0.1, description="Pause variance during typing")
    permissions_granted: str = Field(default="[]", description="High-risk permissions currently active")
    screen_active_time: int = Field(default=150, description="Continuous screen active time in seconds")
    geographic_location_vs_ip: float = Field(default=10.0, description="Calculated discrepancy between GPS and IP location")
    background_data_usage: float = Field(default=0.2, description="Active background data transfer rate in MB")
    recognized_screen_sharing_apps: str = Field(default="[]", description="Detected screen share / remote desktop tools")
    authentication_attempt_count: int = Field(default=1, description="Total attempts including OTP/PIN retries")
    time_between_otp_generation_and_input: int = Field(default=0, description="Latency in entering OTP")
    pin_entry_method: str = Field(default="manual", description="Entry mode: 'manual' | 'autofill' | 'accessibility'")
    pin_entry_speed: float = Field(default=1.2, description="Speed of entering PIN digits")
    unusual_transaction_amount_flag: int = Field(default=0, description="1 if amount exceeds standard user limits, else 0")
    otp_request_frequency: int = Field(default=0, description="OTP requests triggered within last 10 mins")
    otp_request_device_consistency: int = Field(default=1, description="1 if OTP requested on same device, else 0")
    transaction_velocity: int = Field(default=1, description="Number of transactions initiated in last hour")
    failed_transaction_count: int = Field(default=0, description="Failed payments in last 24 hours")
    authorization_method: str = Field(default="pin", description="Auth type: 'pin' | 'biometric' | 'otp'")
    transaction_type: str = Field(default="payment", description="'payment' | 'collection_request' | 'bill_pay'")
    request_description_keywords: str = Field(default="[]", description="Extracted keywords from request note")
    request_amount_roundness: float = Field(default=1.0, description="Roundness metric of requested amount")
    request_frequency: int = Field(default=0, description="Frequency of inbound payment requests")
    request_acceptance_rate: float = Field(default=0.0, description="Historical acceptance rate for requester")
    time_to_respond_to_request: int = Field(default=0, description="Seconds taken to approve payment request")
    time_pressure_indicators: int = Field(default=0, description="Urgency / countdown indicator score")
    requester_account_age: int = Field(default=0, description="Requester profile age in days")
    handle_similarity_score: float = Field(default=0.0, description="Levenshtein similarity to official brand handles")
    handle_typo_analysis: str = Field(default="none", description="'none' | 'typo_squatting' | 'suspicious'")
    handle_transaction_history: int = Field(default=0, description="Number of verified transactions on UPI handle")
    business_name_match: str = Field(default="none", description="'match' | 'partial' | 'mismatch' | 'none'")
    handle_registration_pattern: str = Field(default="standard", description="'standard' | 'recent' | 'random_alphanumeric'")
    handle_to_description_consistency: int = Field(default=1, description="Consistency score between handle and merchant name")
    handle_verification_status: str = Field(default="verified", description="'verified' | 'unverified'")


class TransactionResponse(BaseModel):
    risk_score: int = Field(..., description="Calculated risk score between 0 and 100")
    risk_tier: str = Field(..., description="Categorical risk tier: 'low' | 'caution' | 'high'")
    flags: List[str] = Field(..., description="Contextual security flags triggered by transaction parameters")
    explanation: str = Field(..., description="Human-readable decision explanation")


# ── Routes ────────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    """Health check endpoint."""
    return {"status": "ok"}


@app.post("/check-transaction", response_model=TransactionResponse)
def evaluate_transaction(payload: TransactionInput):
    """
    Evaluates a raw UPI transaction input and returns real-time fraud risk assessment.
    """
    try:
        transaction_dict = payload.model_dump()
        result = check_transaction(transaction_dict)
        return result
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Inference error: {str(exc)}")


# ── Server entry point ────────────────────────────────────────────────────────
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
