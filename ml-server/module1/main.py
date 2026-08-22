from fastapi import FastAPI, UploadFile, File
from model import ForgeryDetectionModel
from srm_analyzer import SrmAnalyzer
import os

app = FastAPI(title="Sentinel ML Server", version="2.0")

# Initialize models
model_path = os.path.join(os.path.dirname(__file__), "casia_resnet50.pth")
detector = ForgeryDetectionModel(model_path if os.path.exists(model_path) else None)
srm = SrmAnalyzer()

@app.post("/detect-forgery")
async def detect_forgery(file: UploadFile = File(...)):
    contents = await file.read()
    
    # Run CNN detection
    cnn_result = detector.predict(contents)
    
    # Run SRM noise analysis
    srm_result = srm.analyze(contents)
    
    # Combine scores
    cnn_prob = cnn_result.get("forgery_probability", 0.5)
    srm_score = srm_result.get("noise_inconsistency", 0.0)
    
    # Weighted combination: CNN 40%, SRM 60% (SRM is more reliable for splicing)
    combined_prob = (cnn_prob * 0.4) + (srm_score * 0.6)
    
    return {
        "filename": file.filename,
        "forgery_probability": round(combined_prob, 4),
        "is_forged": combined_prob > 0.5,
        "cnn_score": round(cnn_prob, 4),
        "srm_score": round(srm_score, 4),
        "srm_details": srm_result.get("details", ""),
        "srm_outlier_blocks": srm_result.get("outlier_blocks", 0),
        "srm_total_blocks": srm_result.get("total_blocks", 0)
    }

@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.0", "layers": ["CNN-CASIA", "SRM-Noise"]}
