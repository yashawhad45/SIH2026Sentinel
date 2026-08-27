from fastapi import FastAPI, UploadFile, File, Form
from forensic_analyzer import ForensicAnalyzer
from document_warper import DocumentWarper
import os
import json
import cv2
import numpy as np

app = FastAPI(title="Sentinel ML Server", version="2.0")

forensic_analyzer = ForensicAnalyzer()
warper = DocumentWarper()

@app.post("/forensic-analysis")
async def forensic_analysis(file: UploadFile = File(...), ocr_blocks: str = Form(...)):
    contents = await file.read()
    blocks = json.loads(ocr_blocks)
    
    # 1. Decode raw image
    nparr = np.frombuffer(contents, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    
    if img is None:
        return {"success": False, "error": "Could not decode image"}
        
    # 2. Warp document and transform MLKit bounding boxes
    warped, transformed_blocks, card_detected = warper.warp_document_and_boxes(img, blocks)
    print(f'Received {len(blocks)} blocks from Android, kept {len(transformed_blocks)} after warp')
    
    # 3. Encode warped image back to bytes for the analyzer
    _, buffer = cv2.imencode('.jpg', warped)
    warped_bytes = buffer.tobytes()
    
    # 4. Run Forensic Analysis using perfect flat image and perfectly aligned boxes
    result = forensic_analyzer.analyze(warped_bytes, transformed_blocks)
    result["card_detected"] = card_detected
    return result

@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.0", "layers": ["Forensic-Consistency-Robust"]}



