from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from model import ForgeryDetectionModel

app = FastAPI(title="Sentinel Micro-Forgery CNN API")

# Setup CORS to allow mobile clients or web dashboards
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize the model globally so it's loaded into memory once on startup.
# We point to a placeholder model path. Users can drop casia_resnet50.pth here.
cnn_model = ForgeryDetectionModel(model_path="casia_resnet50.pth")

@app.get("/")
def read_root():
    return {"message": "Sentinel CNN API is running. POST to /detect-forgery."}

@app.post("/detect-forgery")
async def detect_forgery(file: UploadFile = File(...)):
    """
    Accepts an image file and returns a forgery probability score.
    """
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="File must be an image.")
        
    try:
        # Read file bytes
        image_bytes = await file.read()
        
        # Run inference
        result = cnn_model.predict(image_bytes)
        
        if not result["success"]:
            raise HTTPException(status_code=500, detail=f"Model inference failed: {result['error']}")
            
        return {
            "filename": file.filename,
            "forgery_probability": result["forgery_probability"],
            "is_forged": result["is_forged"]
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
