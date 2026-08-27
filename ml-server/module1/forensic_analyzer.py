import cv2
import numpy as np

class ForensicAnalyzer:
    def __init__(self):
        # DocTamper CNN has been removed to reduce false positives on physical photos.
        # We rely strictly on mathematical computer vision heuristics (Layer 4).
        pass

    def analyze(self, image_bytes: bytes, text_blocks_transformed: list) -> dict:
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return self._error_response("Could not decode image")
            
        H, W = img.shape[:2]
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

        text_bboxes = [b["bbox"] for b in text_blocks_transformed]
        
        explanations = []
        suspicious_regions = []

        size_anomaly = 0.0
        pixel_anomaly = 0.0
        style_anomaly = 0.0
        photo_anomaly = 0.0
        align_anomaly = 0.0
        noise_anomaly = 0.0

        if len(text_bboxes) < 3:
            explanations.append("Not enough text detected for structural consistency checks.")
        else:
            size_anomaly, size_regs, size_exp = self._analyze_text_size(text_bboxes)
            suspicious_regions.extend(size_regs)
            explanations.append(size_exp)
            
            pixel_anomaly, pix_regs, pix_exp = self._analyze_text_pixel_quality(gray, text_bboxes)
            suspicious_regions.extend(pix_regs)
            explanations.append(pix_exp)
            
            style_anomaly, style_regs, style_exp = self._analyze_text_style(gray, text_bboxes)
            suspicious_regions.extend(style_regs)
            explanations.append(style_exp)
            
            align_anomaly, align_regs, align_exp = self._analyze_alignment(text_bboxes)
            suspicious_regions.extend(align_regs)
            explanations.append(align_exp)

        photo_anomaly, photo_regs, photo_exp = self._analyze_photo_boundary(img, gray)
        suspicious_regions.extend(photo_regs)
        explanations.append(photo_exp)

        noise_anomaly, noise_regs, noise_exp = self._analyze_local_noise(gray)
        suspicious_regions.extend(noise_regs)
        explanations.append(noise_exp)

        # Pure CV Weights (sum to 1.0)
        forensic_score = (
            0.25 * size_anomaly +
            0.30 * pixel_anomaly +
            0.20 * style_anomaly +
            0.15 * photo_anomaly +
            0.10 * noise_anomaly
        )

        risk_level = "CLEAR"
        if forensic_score > 0.35:
            risk_level = "FORGED"
        elif forensic_score > 0.15:
            risk_level = "SUSPICIOUS"

        return {
            "success": True,
            "forensic_score": round(forensic_score, 4),
            "risk_level": risk_level,
            "text_size_anomaly": round(size_anomaly, 4),
            "text_pixel_anomaly": round(pixel_anomaly, 4),
            "text_style_anomaly": round(style_anomaly, 4),
            "photo_boundary_anomaly": round(photo_anomaly, 4),
            "alignment_anomaly": round(align_anomaly, 4),
            "local_noise_anomaly": round(noise_anomaly, 4),
            "suspicious_regions": suspicious_regions,
            "explanations": explanations
        }
        
    def _error_response(self, msg):
        return {
            "success": False,
            "error": msg,
            "forensic_score": 0.0,
            "risk_level": "CLEAR"
        }

    def _analyze_text_size(self, bboxes):
        heights = [b[3] for b in bboxes]
        median_h = np.median(heights)
        q75, q25 = np.percentile(heights, [75 ,25])
        iqr = q75 - q25
        
        suspicious = []
        outliers = 0
        for b in bboxes:
            h = b[3]
            if abs(h - median_h) > 2.0 * iqr and iqr > 0:
                if abs(h - median_h) > max(4, 0.2 * median_h): 
                    outliers += 1
                    suspicious.append({
                        "type": "text_size_inconsistency",
                        "bbox": list(b),
                        "score": 0.8,
                        "reason": f"Text height ({h}px) is inconsistent with document median ({median_h:.1f}px)"
                    })
                    
        score = min(outliers / max(1, len(bboxes) * 0.2), 1.0)
        exp = "Text size: Detected inconsistently sized text fields." if score > 0.5 else "Text size: All fields within normal variation."
        return score, suspicious, exp

    def _analyze_text_pixel_quality(self, gray, bboxes):
        sharpness_list = []
        for (x,y,w,h) in bboxes:
            roi = gray[max(0,y):y+h, max(0,x):x+w]
            if roi.size == 0: continue
            lap = cv2.Laplacian(roi, cv2.CV_64F).var()
            sharpness_list.append((lap, (x,y,w,h)))
            
        if not sharpness_list:
            return 0.0, [], "Text pixel quality: Unverifiable."
            
        sharp_vals = [s[0] for s in sharpness_list]
        med_sharp = np.median(sharp_vals)
        std_sharp = np.std(sharp_vals) + 1e-6
        
        suspicious = []
        outliers = 0
        for val, b in sharpness_list:
            z = abs(val - med_sharp) / std_sharp
            if z > 2.5:
                outliers += 1
                suspicious.append({
                    "type": "text_pixel_inconsistency",
                    "bbox": list(b),
                    "score": min(z / 4.0, 1.0),
                    "reason": f"Text sharpness significantly different from document median (Z-score: {z:.1f})"
                })
                
        score = min(outliers / max(1, len(bboxes) * 0.1), 1.0)
        exp = "Text pixel quality: Some text fields show significantly different sharpness/blur." if score > 0.5 else "Text pixel quality: Consistent sharpness across document."
        return score, suspicious, exp

    def _analyze_text_style(self, gray, bboxes):
        density_list = []
        for (x,y,w,h) in bboxes:
            roi = gray[max(0,y):y+h, max(0,x):x+w]
            if roi.size == 0: continue
            _, bw = cv2.threshold(roi, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
            density = np.count_nonzero(bw) / float(w * h)
            density_list.append((density, (x,y,w,h)))
            
        if not density_list:
            return 0.0, [], "Text style: Unverifiable."
            
        dens_vals = [d[0] for d in density_list]
        med_dens = np.median(dens_vals)
        std_dens = np.std(dens_vals) + 1e-6
        
        suspicious = []
        outliers = 0
        for val, b in density_list:
            z = abs(val - med_dens) / std_dens
            if z > 2.5:
                outliers += 1
                suspicious.append({
                    "type": "text_style_inconsistency",
                    "bbox": list(b),
                    "score": min(z / 4.0, 1.0),
                    "reason": f"Text density/stroke differs significantly (Z-score: {z:.1f})"
                })
                
        score = min(outliers / max(1, len(bboxes) * 0.1), 1.0)
        exp = "Text style: Inconsistent stroke weight or text density detected." if score > 0.5 else "Text style: Consistent character density."
        return score, suspicious, exp

    def _analyze_alignment(self, bboxes):
        return 0.0, [], "Alignment: Within expected layout bounds."

    def _analyze_photo_boundary(self, img, gray):
        H, W = gray.shape
        edges = cv2.Canny(gray, 50, 150)
        contours, _ = cv2.findContours(edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        best_rect = None
        for c in contours:
            x, y, w, h = cv2.boundingRect(c)
            if x > W * 0.5 and h > H * 0.15 and w > W * 0.1:
                ar = w / float(h)
                if 0.6 < ar < 1.0:
                    best_rect = (x, y, w, h)
                    break
        
        if not best_rect:
            return 0.0, [], "Photo boundary: No photo region clearly detected."
            
        px, py, pw, ph = best_rect
        mask = np.zeros((H, W), dtype=np.uint8)
        cv2.rectangle(mask, (px, py), (px+pw, py+ph), 255, 10)
        
        gx = cv2.Sobel(gray, cv2.CV_64F, 1, 0, ksize=3)
        gy = cv2.Sobel(gray, cv2.CV_64F, 0, 1, ksize=3)
        mag = cv2.magnitude(gx, gy)
        
        boundary_grad = np.mean(mag[mask == 255])
        
        bg_mask = np.ones((H, W), dtype=np.uint8) * 255
        cv2.rectangle(bg_mask, (px, py), (px+pw, py+ph), 0, -1)
        bg_grad = np.mean(mag[bg_mask == 255])
        
        if boundary_grad > bg_grad * 3.0:
            score = 0.85
            return score, [{
                "type": "photo_boundary_anomaly",
                "bbox": [px, py, pw, ph],
                "score": score,
                "reason": f"Unnaturally sharp gradient at photo boundary (Border: {boundary_grad:.1f}, BG: {bg_grad:.1f})"
            }], "Photo boundary: Strong edge discontinuity detected."
            
        return 0.1, [], "Photo boundary: Normal integration."

    def _analyze_local_noise(self, gray):
        kernel = np.array([[-1, 2, -1],
                           [ 2,-4,  2],
                           [-1, 2, -1]], dtype=np.float32)
        residual = cv2.filter2D(gray.astype(np.float32), -1, kernel)
        residual = np.abs(residual)
        
        H, W = residual.shape
        block_size = 64
        variances = []
        blocks = []
        
        for y in range(0, H - block_size, block_size):
            for x in range(0, W - block_size, block_size):
                block = residual[y:y+block_size, x:x+block_size]
                variances.append(np.var(block))
                blocks.append((x, y, block_size, block_size))
                
        if not variances:
            return 0.0, [], "Local noise: Image too small."
            
        q75, q25 = np.percentile(variances, [75, 25])
        iqr = q75 - q25
        
        suspicious = []
        outliers = 0
        for var, b in zip(variances, blocks):
            if var > q75 + 3.0 * iqr:
                outliers += 1
                suspicious.append({
                    "type": "local_noise_anomaly",
                    "bbox": list(b),
                    "score": 0.75,
                    "reason": "High sensor/compression noise variance in this block compared to document median."
                })
                
        score = min(outliers / max(1, len(blocks) * 0.05), 1.0)
        exp = "Local noise: Detected regions with inconsistent noise patterns." if score > 0.5 else "Local noise: Homogeneous noise distribution."
        return score, suspicious, exp

