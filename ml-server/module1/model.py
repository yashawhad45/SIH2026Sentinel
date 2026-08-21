import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image
import io

class ForgeryDetectionModel:
    def __init__(self, model_path=None):
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        
        # We use a ResNet50 backbone, a standard choice for CASIA V2 manipulation detection
        self.model = models.resnet50(pretrained=False)
        
        # Modify the final classification layer for binary classification (Real vs Forged)
        num_ftrs = self.model.fc.in_features
        self.model.fc = nn.Linear(num_ftrs, 2)
        
        # Load CASIA pretrained weights if provided, otherwise use random init
        if model_path:
            try:
                self.model.load_state_dict(torch.load(model_path, map_location=self.device))
                print(f"Loaded weights from {model_path}")
            except Exception as e:
                print(f"Failed to load weights, using random initialization. Error: {e}")
        else:
            print("No weights provided. Using randomly initialized weights.")
            
        self.model.to(self.device)
        self.model.eval()
        
        # Standard ImageNet normalization commonly used for CASIA pretraining
        self.transform = transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406],
                                 std=[0.229, 0.224, 0.225])
        ])

    def predict(self, image_bytes: bytes) -> dict:
        """
        Runs inference on the raw image bytes and returns a probability score.
        Class 0: Authentic, Class 1: Forged
        """
        try:
            image = Image.open(io.BytesIO(image_bytes)).convert('RGB')
            tensor = self.transform(image).unsqueeze(0).to(self.device)
            
            with torch.no_grad():
                outputs = self.model(tensor)
                probabilities = torch.nn.functional.softmax(outputs, dim=1)
                
                # Probability of being forged (class 1)
                forged_prob = probabilities[0][1].item()
                
            return {
                "success": True,
                "forgery_probability": round(forged_prob, 4),
                "is_forged": forged_prob > 0.5
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }
