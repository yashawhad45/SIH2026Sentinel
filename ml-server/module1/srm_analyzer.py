import numpy as np
from PIL import Image
import io

class SrmAnalyzer:
    """
    Spatial Rich Model (SRM) Noise Analysis.
    Uses fixed high-pass filters to extract sensor noise residuals.
    When a photo is spliced/pasted, the noise pattern breaks.
    No training needed - pure mathematical filters.
    """

    def __init__(self):
        # Standard SRM high-pass filters (fixed, no training)
        self.filters = [
            # 1st order edge filter (horizontal)
            np.array([[-1, 1, 0],
                      [-1, 1, 0],
                      [-1, 1, 0]], dtype=np.float32),
            # 1st order edge filter (vertical)
            np.array([[-1, -1, -1],
                      [1, 1, 1],
                      [0, 0, 0]], dtype=np.float32),
            # 2nd order Laplacian
            np.array([[0, 1, 0],
                      [1, -4, 1],
                      [0, 1, 0]], dtype=np.float32),
            # 3rd order SRM filter
            np.array([[-1, 2, -1],
                      [2, -4, 2],
                      [-1, 2, -1]], dtype=np.float32),
        ]

    def analyze(self, image_bytes: bytes) -> dict:
        try:
            image = Image.open(io.BytesIO(image_bytes)).convert('RGB')
            img_array = np.array(image, dtype=np.float32)

            # Downscale if too large
            h, w = img_array.shape[:2]
            if max(h, w) > 800:
                scale = 800 / max(h, w)
                new_h, new_w = int(h * scale), int(w * scale)
                image = image.resize((new_w, new_h), Image.Resampling.LANCZOS)
                img_array = np.array(image, dtype=np.float32)
                h, w = new_h, new_w

            # Extract noise residuals using SRM filters
            gray = np.mean(img_array, axis=2)
            noise_map = np.zeros_like(gray)

            for filt in self.filters:
                filtered = self._convolve2d(gray, filt)
                noise_map += np.abs(filtered)

            noise_map /= len(self.filters)

            # Split into grid and compute variance per block
            block_size = min(h, w) // 4
            if block_size < 32:
                block_size = 32

            variances = []
            for row in range(0, h - block_size + 1, block_size):
                for col in range(0, w - block_size + 1, block_size):
                    block = noise_map[row:row + block_size, col:col + block_size]
                    variances.append(float(np.var(block)))

            if len(variances) < 4:
                return {
                    "success": True,
                    "noise_inconsistency": 0.0,
                    "is_suspicious": False,
                    "details": "Image too small for meaningful SRM analysis"
                }

            # Compute inconsistency score
            variances = np.array(variances)
            median_var = np.median(variances)
            if median_var < 1e-6:
                inconsistency = 0.0
            else:
                # Coefficient of variation of block variances
                inconsistency = float(np.std(variances) / (median_var + 1e-6))

            # Detect outlier blocks (potential spliced regions)
            q75 = np.percentile(variances, 75)
            q25 = np.percentile(variances, 25)
            iqr = q75 - q25
            outliers = int(np.sum(variances > q75 + 1.5 * iqr))

            # Normalize to 0-1 score
            score = min(inconsistency / 3.0, 1.0)
            if outliers > len(variances) * 0.15:
                score = max(score, 0.6)

            return {
                "success": True,
                "noise_inconsistency": round(score, 4),
                "is_suspicious": score > 0.4,
                "outlier_blocks": outliers,
                "total_blocks": len(variances),
                "details": f"Analyzed {len(variances)} blocks, {outliers} outliers detected"
            }

        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }

    def _convolve2d(self, image: np.ndarray, kernel: np.ndarray) -> np.ndarray:
        """Simple 2D convolution without scipy dependency."""
        kh, kw = kernel.shape
        ph, pw = kh // 2, kw // 2
        padded = np.pad(image, ((ph, ph), (pw, pw)), mode='reflect')
        output = np.zeros_like(image)
        for i in range(kh):
            for j in range(kw):
                output += kernel[i, j] * padded[i:i + image.shape[0], j:j + image.shape[1]]
        return output
