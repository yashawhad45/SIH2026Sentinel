import os
import shutil
import math
import torch
import torchaudio
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from transformers import AutoFeatureExtractor, AutoModelForAudioClassification

# --- CONFIGURATION ---
MODEL_DIR = "./final_model"  # Make sure you extract your zip file here!
CHUNK_LENGTH_SEC = 4
TARGET_SAMPLE_RATE = 16000

app = FastAPI(title="Sentinel Deepfake Detector API")

print("Loading Sentinel AI Model into Memory...")
try:
    feature_extractor = AutoFeatureExtractor.from_pretrained("facebook/wav2vec2-base")
    model = AutoModelForAudioClassification.from_pretrained(MODEL_DIR)
    print("Model loaded successfully! Server is ready.")
except Exception as e:
    print(f"Warning: Model not found at {MODEL_DIR}. Please extract your zip file there.")

@app.post("/analyze")
async def analyze_audio(file: UploadFile = File(...)):
    # 1. Save the uploaded file temporarily
    temp_file_path = f"temp_{file.filename}"
    with open(temp_file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    try:
        # 2. Load the audio file automatically converting it to 16kHz
        import librosa
        import warnings
        warnings.filterwarnings("ignore") # Ignore librosa warnings
        
        # librosa magically handles MP3s, WAVs, FLACs and resamples them on the fly!
        y, sample_rate = librosa.load(temp_file_path, sr=TARGET_SAMPLE_RATE)
        
        # Convert to PyTorch tensor format (1, num_samples)
        waveform = torch.tensor(y).unsqueeze(0)

        # 4. If stereo (2 channels), convert to mono
        if waveform.shape[0] > 1:
            waveform = torch.mean(waveform, dim=0, keepdim=True)

        # 5. Chunking Logic (Slice into 4-second pieces)
        total_samples = waveform.shape[1]
        chunk_samples = TARGET_SAMPLE_RATE * CHUNK_LENGTH_SEC
        
        num_chunks = math.ceil(total_samples / chunk_samples)
        
        chunk_scores = []
        fake_chunks_count = 0

        # 6. Analyze each chunk
        for i in range(num_chunks):
            start_idx = i * chunk_samples
            end_idx = min(start_idx + chunk_samples, total_samples)
            
            chunk = waveform[:, start_idx:end_idx]
            
            inputs = feature_extractor(
                chunk[0].numpy(), 
                sampling_rate=TARGET_SAMPLE_RATE, 
                max_length=chunk_samples, 
                truncation=True, 
                padding="max_length", 
                return_tensors="pt"
            )
            
            with torch.no_grad():
                logits = model(**inputs).logits
                
            probabilities = torch.nn.functional.softmax(logits, dim=-1)
            real_probability = probabilities[0][1].item()
            fake_probability = probabilities[0][0].item()
            
            # Count how many chunks the AI thinks are fake (Must be 85% confident!)
            if fake_probability > 0.85:
                fake_chunks_count += 1
                
            chunk_scores.append({
                "chunk_index": i + 1,
                "real_confidence": round(real_probability * 100, 2),
                "fake_confidence": round(fake_probability * 100, 2)
            })

        # MAJORITY VOTING: Only flag as Deepfake if at least half the chunks are fake
        is_deepfake = fake_chunks_count >= (num_chunks / 2)

        # 7. Clean up temp file
        os.remove(temp_file_path)

        # 8. Return the final verdict to the Android App
        return JSONResponse(content={
            "status": "success",
            "verdict": "DEEPFAKE DETECTED" if is_deepfake else "AUTHENTIC HUMAN",
            "is_deepfake": is_deepfake,
            "total_chunks_analyzed": num_chunks,
            "chunk_breakdown": chunk_scores
        })

    except Exception as e:
        if os.path.exists(temp_file_path):
            os.remove(temp_file_path)
        return JSONResponse(status_code=500, content={"status": "error", "message": str(e)})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
