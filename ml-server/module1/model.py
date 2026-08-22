import sys
import os
import torch
import torch.nn as nn
from PIL import Image
import io
import numpy as np
import warnings
warnings.filterwarnings('ignore')

DOCTAMPER_PATH = os.path.join(os.path.dirname(__file__), "doctamper_repo", "models")
sys.path.insert(0, DOCTAMPER_PATH)

import swins
# HACK to fix "module '__main__' has no attribute 'BasicLayer'" since swin_imagenet.pt 
# was pickled with classes in __main__
sys.modules['__main__'].BasicLayer = swins.BasicLayer
sys.modules['__main__'].SwinTransformerBlock = swins.SwinTransformerBlock
sys.modules['__main__'].WindowAttention = swins.WindowAttention
sys.modules['__main__'].Mlp = swins.Mlp
sys.modules['__main__'].PatchMerging = swins.PatchMerging
sys.modules['__main__'].PatchEmbed = swins.PatchEmbed
# Alias SwinTransformer to SwinTransformerV2
sys.modules['__main__'].SwinTransformer = swins.SwinTransformerV2

from dtd import DTD

class ForgeryDetectionModel:
    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        print(f"Loading DocTamper (DTD) model on {self.device}...")
        
        # Change dir so DTD can find vph_imagenet.pt and swin_imagenet.pt in current dir
        old_cwd = os.getcwd()
        os.chdir(DOCTAMPER_PATH + "/pths")
        
        self.model = DTD()
        state_dict = torch.load("dtd_doctamper.pth", map_location=self.device)
        self.model.load_state_dict(state_dict, strict=False)
        self.model.to(self.device)
        self.model.eval()
        print("DocTamper loaded successfully!")
        
        # Revert cwd
        os.chdir(old_cwd)

    def predict(self, image_bytes: bytes) -> dict:
        try:
            image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
            # Resize image to a multiple of 32 (e.g. 512x512) for Swin Transformer
            image = image.resize((512, 512))
            img_np = np.array(image).astype(np.float32) / 255.0
            mean = np.array([0.485, 0.455, 0.406], dtype=np.float32)
            std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
            img_np = (img_np - mean) / std
            
            # (H, W, C) -> (1, C, H, W)
            img_tensor = torch.from_numpy(img_np).permute(2, 0, 1).unsqueeze(0).to(self.device)
            
            # Dummy DCT and QT since we can't extract them without jpegio / original dataset
            # DCT shape expected: (B, H, W, 1) -> Actually based on FPH, it's (B, 1, H, W) or (B, H, W, 1)
            dct_dummy = torch.zeros((1, 512, 512, 1), dtype=torch.float32).to(self.device)
            qt_dummy = torch.zeros((1, 15), dtype=torch.long).to(self.device)
            
            with torch.no_grad():
                pred_mask = self.model(img_tensor, dct_dummy, qt_dummy)

            if isinstance(pred_mask, torch.Tensor):
                mask = torch.sigmoid(pred_mask).squeeze().cpu().numpy()
            else:
                mask = np.array(pred_mask)

            tampered_ratio = float((mask > 0.5).mean())
            forgery_prob = min(tampered_ratio * 8.0, 1.0)

            return {
                "success": True,
                "forgery_probability": round(forgery_prob, 4),
                "is_forged": forgery_prob > 0.5,
                "tampered_pixel_ratio": round(tampered_ratio, 4),
                "model": "DocTamper-DTD (No-DCT)"
            }
        except Exception as e:
            import traceback
            traceback.print_exc()
            return {
                "success": False,
                "error": str(e),
                "forgery_probability": 0.0,
                "is_forged": False
            }

