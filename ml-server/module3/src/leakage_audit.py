import os
import json
import pandas as pd
import numpy as np

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
DATASET_PATH = os.path.join(DATA_DIR, "fraud_dataset.csv")

def main():
    print("=" * 75)
    print("                      DATA LEAKAGE AUDIT                      ")
    print("=" * 75)
    
    if not os.path.exists(DATASET_PATH):
        print(f"Dataset not found at {DATASET_PATH}")
        return

    df = pd.read_csv(DATASET_PATH)
    
    ignore_cols = {
        "is_fraud", "transaction_id", "user_id", "merchant_id", "device_id", 
        "ip_address", "description", "request_description", "url_referrer", 
        "location", "timestamp"
    }
    
    # Identify column types
    categorical_cols = []
    numeric_cols = []
    
    for col in df.columns:
        if col in ignore_cols:
            continue
        if pd.api.types.is_numeric_dtype(df[col]):
            numeric_cols.append(col)
        else:
            categorical_cols.append(col)

    flagged_columns = []
    
    print("\nAuditing Categorical Columns...")
    for col in categorical_cols:
        counts = df.groupby(col).size()
        fraud_rates = df.groupby(col)["is_fraud"].mean()
        
        for cat_val in fraud_rates.index:
            rate = fraud_rates[cat_val]
            count = counts[cat_val]
            
            if (rate == 0.0 or rate == 1.0) and count >= 50:
                flagged_columns.append(col)
                print(f"[FLAG] Categorical: '{col}' -> Value '{cat_val}' has fraud rate {rate*100}% (N={count} rows)")
                break # Only need to flag the column once

    print("\nAuditing Numeric Columns...")
    for col in numeric_cols:
        gen_data = df[df["is_fraud"] == 0][col].dropna()
        fraud_data = df[df["is_fraud"] == 1][col].dropna()
        
        if len(gen_data) == 0 or len(fraud_data) == 0:
            continue
            
        gen_min, gen_max = gen_data.min(), gen_data.max()
        fraud_min, fraud_max = fraud_data.min(), fraud_data.max()
        gen_std = gen_data.std()
        fraud_std = fraud_data.std()
        
        is_flagged = False
        reason = ""
        
        # Zero overlap check
        if fraud_max < gen_min or fraud_min > gen_max:
            is_flagged = True
            reason = f"Zero overlap (Gen: [{gen_min}, {gen_max}], Fraud: [{fraud_min}, {fraud_max}])"
            
        # Zero standard deviation check
        elif gen_std == 0:
            is_flagged = True
            reason = f"Genuine class has std=0 (All values={gen_min})"
        elif fraud_std == 0:
            is_flagged = True
            reason = f"Fraud class has std=0 (All values={fraud_min})"
            
        if is_flagged:
            if col not in flagged_columns:
                flagged_columns.append(col)
            print(f"[FLAG] Numeric: '{col}' -> {reason}")

    # Deduplicate flagged columns just in case
    flagged_columns = list(dict.fromkeys(flagged_columns))
    
    out_path = os.path.join(DATA_DIR, "leaky_columns.json")
    with open(out_path, "w") as f:
        json.dump(flagged_columns, f, indent=4)
        
    print("\n" + "=" * 75)
    print(f"Total flagged columns: {len(flagged_columns)}")
    print(f"Saved to: {out_path}")
    print("=" * 75)

if __name__ == "__main__":
    main()
