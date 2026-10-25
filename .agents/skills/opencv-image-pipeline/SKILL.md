---
name: opencv-image-pipeline
description: >-
  Use this skill when building OpenCV computer vision pipelines, image preprocessing, morphological operations, contour detection, or video frame processing.
license: Apache-2.0
compatibility: requires-opencv-python
---

# OpenCV Image Processing Pipeline

Standard operational procedures for computer vision and image processing.

## Standard Pipeline Steps

### 1. Ingestion & Color Space Safety
Always remember: OpenCV loads images in BGR format by default.
```python
import cv2

image = cv2.imread("input.jpg")
if image is None:
    raise FileNotFoundError("Image failed to load")
rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
```

### 2. Preprocessing & Noise Suppression
- Use `cv2.GaussianBlur(gray, (5, 5), 0)` for general noise.
- Use `cv2.bilateralFilter(gray, 9, 75, 75)` to smooth textures while preserving sharp edges.

### 3. Adaptive Thresholding & Edge Detection
```python
# Adaptive illumination handling
clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
enhanced = clahe.apply(gray)
edges = cv2.Canny(enhanced, threshold1=50, threshold2=150)
```

### 4. Contours & Key Feature Extraction
```python
contours, hierarchy = cv2.findContours(edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
valid_contours = [c for c in contours if cv2.contourArea(c) > 100]
```

### 5. Validation Check
Always verify frame dimensions, channels, and data types (`uint8` vs `float32`) before passing to neural networks.
