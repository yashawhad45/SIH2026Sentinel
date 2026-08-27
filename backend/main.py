import os
import shutil
import math
import subprocess
import warnings
import torch
import torchaudio
import librosa
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from transformers import AutoFeatureExtractor, AutoModelForAudioClassification

# --- CONFIGURATION ---
MODEL_DIR = "./final_model"
CHUNK_LENGTH_SEC = 4
TARGET_SAMPLE_RATE = 16000

# Dynamically add ffmpeg to PATH if needed
ffmpeg_bin = r"C:\Users\Chinmaya\AppData\Local\ffmpegio\ffmpeg-downloader\ffmpeg\bin"
if os.path.exists(ffmpeg_bin):
    os.environ["PATH"] += os.pathsep + ffmpeg_bin

app = FastAPI(title="Sentinel Deepfake Detector API")

print("Loading Sentinel AI Model into Memory...")
try:
    feature_extractor = AutoFeatureExtractor.from_pretrained(MODEL_DIR)
    model = AutoModelForAudioClassification.from_pretrained(MODEL_DIR)
    print("Model loaded successfully! Server is ready.")
except Exception as e:
    print(f"Warning: Model not found at {MODEL_DIR}: {e}")

@app.post("/analyze")
async def analyze_audio(file: UploadFile = File(...)):
    # 1. Save uploaded audio preserving original filename and extension
    filename = file.filename or "uploaded_audio.webm"
    temp_file_path = f"temp_{filename}"
    with open(temp_file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    wav_file_path = temp_file_path + ".wav"
    try:
        warnings.filterwarnings("ignore")

        # 2. Extract lossless, uncompressed linear PCM 16-bit audio from ANY format (webm, ogg, m4a, mp3, wav)
        # -vn: Disables video stream (essential for webm/mp4 container files)
        # -acodec pcm_s16le: Pure uncompressed 16-bit linear PCM (lossless, zero compression)
        # -ar 16000: Resample directly to 16kHz required by the AI model
        # -ac 1: Mono channel
        subprocess.run([
            "ffmpeg", "-y", "-i", temp_file_path,
            "-vn",
            "-acodec", "pcm_s16le",
            "-ar", str(TARGET_SAMPLE_RATE),
            "-ac", "1",
            wav_file_path
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        if os.path.exists(wav_file_path) and os.path.getsize(wav_file_path) > 0:
            audio_source = wav_file_path
        else:
            audio_source = temp_file_path

        # 3. Load uncompressed WAV into memory
        y, sample_rate = librosa.load(audio_source, sr=TARGET_SAMPLE_RATE)

        # Convert to PyTorch tensor format (1, num_samples)
        waveform = torch.tensor(y).unsqueeze(0)

        # 4. If stereo (2 channels), convert to mono
        if waveform.shape[0] > 1:
            waveform = torch.mean(waveform, dim=0, keepdim=True)

        # 5. Chunking Logic (Slice into 4-second pieces)
        total_samples = waveform.shape[1]
        chunk_samples = TARGET_SAMPLE_RATE * CHUNK_LENGTH_SEC

        num_chunks = max(1, math.ceil(total_samples / chunk_samples))

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
            # Label mapping: 0 = Genuine Human, 1 = Deepfake
            real_probability = probabilities[0][0].item()
            fake_probability = probabilities[0][1].item()

            if fake_probability > 0.85:
                fake_chunks_count += 1

            chunk_scores.append({
                "chunk_index": i + 1,
                "real_confidence": round(real_probability * 100, 2),
                "fake_confidence": round(fake_probability * 100, 2)
            })

        # MAJORITY VOTING: Flag as Deepfake if at least half the chunks are fake
        is_deepfake = fake_chunks_count >= (num_chunks / 2)

        # Cleanup temp files
        for p in [temp_file_path, wav_file_path]:
            if os.path.exists(p):
                try:
                    os.remove(p)
                except Exception:
                    pass

        # 8. Return final verdict
        return JSONResponse(content={
            "status": "success",
            "verdict": "DEEPFAKE DETECTED" if is_deepfake else "AUTHENTIC HUMAN",
            "is_deepfake": is_deepfake,
            "total_chunks_analyzed": num_chunks,
            "chunk_breakdown": chunk_scores
        })

    except Exception as e:
        for p in [temp_file_path, wav_file_path]:
            if os.path.exists(p):
                try:
                    os.remove(p)
                except Exception:
                    pass
        return JSONResponse(status_code=500, content={"status": "error", "message": str(e)})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
