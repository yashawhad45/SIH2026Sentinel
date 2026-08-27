import cv2
import numpy as np

class DocumentWarper:
    def __init__(self, target_w=800, target_h=510):
        self.target_w = target_w
        self.target_h = target_h

    def warp_document_and_boxes(self, img, text_blocks):
        # img: BGR numpy array
        # text_blocks: list of dicts: {"text": str, "left": int, "top": int, "right": int, "bottom": int}
        
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        gray = cv2.GaussianBlur(gray, (5, 5), 0)
        edged = cv2.Canny(gray, 75, 200)
        
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
        edged = cv2.morphologyEx(edged, cv2.MORPH_CLOSE, kernel)

        contours, _ = cv2.findContours(edged.copy(), cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        contours = sorted(contours, key=cv2.contourArea, reverse=True)[:5]
        
        screen_cnt = None
        for c in contours:
            peri = cv2.arcLength(c, True)
            approx = cv2.approxPolyDP(c, 0.02 * peri, True)
            if len(approx) == 4:
                # Check if it's large enough to be the document
                if cv2.contourArea(approx) > (img.shape[0] * img.shape[1] * 0.2):
                    screen_cnt = approx
                    break
                
        # If no 4-point contour found, fallback to the entire image
        if screen_cnt is None:
            H, W = img.shape[:2]
            screen_cnt = np.array([
                [[0, 0]],
                [[W, 0]],
                [[W, H]],
                [[0, H]]
            ])
            
        pts = screen_cnt.reshape(4, 2)
        rect = self._order_points(pts)
        
        dst = np.array([
            [0, 0],
            [self.target_w - 1, 0],
            [self.target_w - 1, self.target_h - 1],
            [0, self.target_h - 1]
        ], dtype="float32")
        
        M = cv2.getPerspectiveTransform(rect, dst)
        warped = cv2.warpPerspective(img, M, (self.target_w, self.target_h))
        
        transformed_blocks = []
        for block in text_blocks:
            left, top, right, bottom = block["left"], block["top"], block["right"], block["bottom"]
            box_pts = np.array([
                [[left, top]],
                [[right, top]],
                [[right, bottom]],
                [[left, bottom]]
            ], dtype="float32")
            
            warped_pts = cv2.perspectiveTransform(box_pts, M)
            
            pts_reshaped = warped_pts.reshape(4, 2)
            new_left = int(min(pts_reshaped[:, 0]))
            new_top = int(min(pts_reshaped[:, 1]))
            new_right = int(max(pts_reshaped[:, 0]))
            new_bottom = int(max(pts_reshaped[:, 1]))
            
            new_left = max(0, min(new_left, self.target_w - 1))
            new_top = max(0, min(new_top, self.target_h - 1))
            new_right = max(0, min(new_right, self.target_w - 1))
            new_bottom = max(0, min(new_bottom, self.target_h - 1))
            
            if new_right > new_left and new_bottom > new_top:
                transformed_blocks.append({
                    "text": block["text"],
                    "bbox": (new_left, new_top, new_right - new_left, new_bottom - new_top) # x, y, w, h
                })
                
        return warped, transformed_blocks, (screen_cnt is not None)

    def _order_points(self, pts):
        rect = np.zeros((4, 2), dtype="float32")
        s = pts.sum(axis=1)
        rect[0] = pts[np.argmin(s)]
        rect[2] = pts[np.argmax(s)]
        diff = np.diff(pts, axis=1)
        rect[1] = pts[np.argmin(diff)]
        rect[3] = pts[np.argmax(diff)]
        return rect

