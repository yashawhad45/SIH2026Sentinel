from fastapi import FastAPI, UploadFile, File
from model import ForgeryDetectionModel
from srm_analyzer import SrmAnalyzer
import os

app = FastAPI(title="Sentinel ML Server", version="2.0")

# Initialize models
model_path = os.path.join(os.path.dirname(__file__), "casia_resnet50.pth")
detector = ForgeryDetectionModel()
srm = SrmAnalyzer()

@app.post("/detect-forgery")
async def detect_forgery(file: UploadFile = File(...)):
    contents = await file.read()
    
    # Run CNN detection
    cnn_result = detector.predict(contents)
    
    # Run SRM noise analysis
    srm_result = srm.analyze(contents)
    
    doctamper_prob = cnn_result.get("forgery_probability", 0.0)
    srm_score = srm_result.get("noise_inconsistency", 0.0)
    
    return {
        "filename": file.filename,
        "forgery_probability": round(doctamper_prob, 4),
        "doctamper_pixel_ratio": cnn_result.get("tampered_pixel_ratio", 0.0),
        "is_forged": doctamper_prob > 0.5,
        "srm_score": round(srm_score, 4),
        "srm_details": srm_result.get("details", "")
    }

@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.0", "layers": ["CNN-CASIA", "SRM-Noise"]}

