"""
src/feature_importance.py
Feature Importance Extraction & Visualization Engine:
- Loads best trained model (models/upi_model.pkl) and feature names (data/processed/feature_columns.pkl)
- Dynamically extracts feature importances (for tree-based models) or absolute coefficients (for linear models)
- Formats feature names for maximum readability on slide decks
- Generates a presentation-grade horizontal bar chart saved to outputs/feature_importance.png
- Prints top 15 features ranking to terminal
"""

import os
import joblib
import pandas as pd
import numpy as np
import matplotlib

matplotlib.use("Agg")  # Headless backend for server/CLI
import matplotlib.pyplot as plt

# ── Paths configuration ───────────────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS_DIR = os.path.join(BASE_DIR, "models")
PROCESSED_DIR = os.path.join(BASE_DIR, "data", "processed")
OUTPUTS_DIR = os.path.join(BASE_DIR, "outputs")

MODEL_PATH = os.path.join(MODELS_DIR, "upi_model.pkl")
FEATURE_COLUMNS_PATH = os.path.join(PROCESSED_DIR, "feature_columns.pkl")
PLOT_SAVE_PATH = os.path.join(OUTPUTS_DIR, "feature_importance.png")


def format_feature_label(raw_name: str) -> str:
    """Converts raw code feature names to clean, presentation-ready labels."""
    replacements = {
        "receiver_account_age": "Receiver Account Age (Days)",
        "handle_verification_status_verified": "Payee Verification Status (Verified)",
        "receiver_transaction_history": "Receiver Historical Txn Volume",
        "unusual_transaction_amount_flag": "Anomalous Transaction Amount Flag",
        "handle_registration_pattern_recent": "Recently Registered Handle Pattern",
        "merchant_category_code_unknown": "Uncategorized Merchant Code",
        "amount": "Transaction Amount (INR)",
        "time_pressure_indicators": "Time Pressure / Urgency Score",
        "authentication_attempt_count": "Auth & OTP Retry Frequency",
        "transaction_amount_vs_sender_history": "Amount vs Sender 30-Day Avg Ratio",
        "input_timing_consistency": "Keystroke / Input Cadence Score",
        "business_name_match_none": "Payee Business Name Mismatch",
        "handle_typo_analysis_typo_squatting": "Handle Typo-Squatting Detected",
        "background_data_usage": "Background Data Transfer Rate",
        "handle_to_description_consistency": "Handle-to-Note Semantic Consistency",
        "geographic_disparity": "Geolocation vs IP Disparity (Km)",
        "transaction_time_of_day": "Transaction Hour (Night/Day)",
        "session_source_link": "Session Initiated via External Link",
        "keyboard_input_speed": "Typing Speed Variance",
        "input_pause_patterns": "Keystroke Pause Variance",
    }
    if raw_name in replacements:
        return replacements[raw_name]
    return raw_name.replace("_", " ").title()


def generate_feature_importance_plot(top_n: int = 15):
    # 1. Load artifacts
    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError(f"Model not found at: {MODEL_PATH}. Please run train_model.py first.")
    if not os.path.exists(FEATURE_COLUMNS_PATH):
        raise FileNotFoundError(f"Feature columns not found at: {FEATURE_COLUMNS_PATH}. Please run data_prep.py first.")

    print(f"Loading model from: {MODEL_PATH}")
    model = joblib.load(MODEL_PATH)
    feature_columns = joblib.load(FEATURE_COLUMNS_PATH)

    # 2. Extract importances
    model_type = type(model).__name__
    print(f"Loaded Model Type: {model_type}")

    if hasattr(model, "feature_importances_"):
        raw_importances = model.feature_importances_
        metric_label = "Gini Feature Importance (Relative Weight)"
    elif hasattr(model, "coef_"):
        raw_importances = np.abs(model.coef_[0])
        metric_label = "Absolute Logistic Coefficient (|Weight|)"
    else:
        raise AttributeError(f"Model of type {model_type} does not expose feature_importances_ or coef_.")

    # 3. Create DataFrame and sort top N
    df_fi = pd.DataFrame({
        "raw_feature": feature_columns,
        "importance": raw_importances,
    }).sort_values(by="importance", ascending=False).reset_index(drop=True)

    df_top = df_fi.head(top_n).copy()
    df_top["clean_label"] = df_top["raw_feature"].apply(format_feature_label)
    df_top["percentage"] = (df_top["importance"] / df_top["importance"].sum()) * 100

    # 4. Print ranking to console
    print("\n" + "=" * 76)
    print(f"       TOP {top_n} FRAUD PREDICTIVE FEATURES ({model_type.upper()})       ")
    print("=" * 76)
    print(f"{'Rank':<5} | {'Feature':<45} | {'Importance':<12} | {'Relative %':<10}")
    print("-" * 76)
    for idx, row in df_top.iterrows():
        print(f"#{idx+1:<4} | {row['clean_label']:<45} | {row['importance']:<12.4f} | {row['percentage']:<10.2f}%")
    print("=" * 76 + "\n")

    # 5. Plot presentation-grade horizontal bar chart
    os.makedirs(OUTPUTS_DIR, exist_ok=True)
    
    # Invert so highest is on top
    df_plot = df_top.iloc[::-1].reset_index(drop=True)

    # Styling setup
    plt.style.use("dark_background")
    fig, ax = plt.subplots(figsize=(12, 8), dpi=300)
    fig.patch.set_facecolor("#0b0f19")
    ax.set_facecolor("#111827")

    # Color gradient across bars
    cmap = matplotlib.colormaps.get("viridis", plt.cm.viridis)
    norm = plt.Normalize(vmin=df_plot["importance"].min(), vmax=df_plot["importance"].max())
    colors = [cmap(norm(val)) for val in df_plot["importance"]]

    bars = ax.barh(
        df_plot["clean_label"],
        df_plot["importance"],
        color=colors,
        edgecolor="#1f293d",
        height=0.68,
        zorder=3,
    )

    # Gridlines
    ax.grid(axis="x", color="#1e293b", linestyle="--", linewidth=0.8, alpha=0.7, zorder=0)

    # Annotate bar values with clean formatting
    max_val = df_plot["importance"].max()
    for bar, imp in zip(bars, df_plot["importance"]):
        width = bar.get_width()
        ax.text(
            width + (max_val * 0.015),
            bar.get_y() + bar.get_height() / 2,
            f"{imp:.3f} ({imp/df_fi['importance'].sum()*100:.1f}%)",
            va="center",
            ha="left",
            fontsize=10,
            fontweight="600",
            color="#e2e8f0",
            fontfamily="sans-serif",
        )

    # Axis and Title Labels
    ax.set_title(
        f"Top {top_n} Fraud Predictive Features\nSentinel UPI ML Engine ({model_type})",
        fontsize=16,
        fontweight="bold",
        color="#ffffff",
        pad=20,
        loc="left",
    )
    ax.set_xlabel(metric_label, fontsize=12, fontweight="600", color="#94a3b8", labelpad=12)
    ax.tick_params(axis="y", labelsize=11, colors="#f1f5f9")
    ax.tick_params(axis="x", labelsize=10, colors="#94a3b8")

    # Spines styling
    for spine in ["top", "right", "left", "bottom"]:
        ax.spines[spine].set_color("#1f293d")
        ax.spines[spine].set_linewidth(1.2)

    # Adjust limits to fit value labels
    ax.set_xlim(0, max_val * 1.25)
    plt.tight_layout()

    # Save artifact
    fig.savefig(PLOT_SAVE_PATH, dpi=300, facecolor=fig.get_facecolor(), bbox_inches="tight")
    plt.close(fig)
    print(f"Presentation chart saved to: {PLOT_SAVE_PATH}\n")


if __name__ == "__main__":
    generate_feature_importance_plot()
