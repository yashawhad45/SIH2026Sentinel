import os
import json
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from xgboost import XGBClassifier
from sklearn.model_selection import StratifiedKFold, cross_validate

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
PROCESSED_DIR = os.path.join(DATA_DIR, "processed")
MODELS_DIR = os.path.join(BASE_DIR, "models")

def main():
    print("=" * 75)
    print("          5-FOLD STRATIFIED CROSS-VALIDATION BENCHMARK          ")
    print("=" * 75)

    # 1. Load processed data
    import joblib
    
    x_train_path = os.path.join(PROCESSED_DIR, "X_train.pkl")
    y_train_path = os.path.join(PROCESSED_DIR, "y_train.pkl")
    x_test_path = os.path.join(PROCESSED_DIR, "X_test.pkl")
    y_test_path = os.path.join(PROCESSED_DIR, "y_test.pkl")
    
    if not (os.path.exists(x_train_path) and os.path.exists(y_train_path) and 
            os.path.exists(x_test_path) and os.path.exists(y_test_path)):
        print("Processed data not found. Please run data_prep.py first.")
        return

    # To do cross-validation over the whole dataset, we combine train and test back
    X_train = joblib.load(x_train_path)
    y_train = joblib.load(y_train_path)
    X_test = joblib.load(x_test_path)
    y_test = joblib.load(y_test_path)
    
    X = pd.concat([X_train, X_test], ignore_index=True)
    y = pd.concat([y_train, y_test], ignore_index=True)

    print(f"Loaded full dataset: {X.shape[0]} rows, {X.shape[1]} features.")
    print("Running 5-fold Stratified Cross-Validation...\n")

    # 2. Define models
    models = {
        "Random Forest": RandomForestClassifier(class_weight="balanced", random_state=42, n_jobs=-1),
        "XGBoost": XGBClassifier(eval_metric="logloss", random_state=42, n_jobs=-1)
    }

    scoring = ['accuracy', 'f1', 'roc_auc']
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)

    results_dict = {}

    for name, model in models.items():
        print(f"[{name}] Cross-validating...")
        cv_results = cross_validate(model, X, y, cv=cv, scoring=scoring, n_jobs=-1, return_train_score=False)
        
        acc_mean, acc_std = np.mean(cv_results['test_accuracy']), np.std(cv_results['test_accuracy'])
        f1_mean, f1_std = np.mean(cv_results['test_f1']), np.std(cv_results['test_f1'])
        auc_mean, auc_std = np.mean(cv_results['test_roc_auc']), np.std(cv_results['test_roc_auc'])

        # 3. Print formatted output
        # E.g., "Random Forest — Accuracy: 94.2% (+/- 1.8%), F1: 0.91 (+/- 0.02), ROC-AUC: 0.96 (+/- 0.01)"
        print(f"{name} — "
              f"Accuracy: {acc_mean*100:.1f}% (+/- {acc_std*100:.1f}%), "
              f"F1: {f1_mean:.4f} (+/- {f1_std:.4f}), "
              f"ROC-AUC: {auc_mean:.4f} (+/- {auc_std:.4f})")
        print("-" * 75)

        results_dict[name] = {
            "Accuracy_mean": acc_mean,
            "Accuracy_std": acc_std,
            "F1_mean": f1_mean,
            "F1_std": f1_std,
            "ROC_AUC_mean": auc_mean,
            "ROC_AUC_std": auc_std
        }

    # 4. Save results to models/cv_metrics.json
    os.makedirs(MODELS_DIR, exist_ok=True)
    out_path = os.path.join(MODELS_DIR, "cv_metrics.json")
    with open(out_path, "w") as f:
        json.dump(results_dict, f, indent=4)
    
    print(f"\n[SAVED] Full cross-validation results exported to: {out_path}")
    print("=" * 75)

if __name__ == "__main__":
    main()
