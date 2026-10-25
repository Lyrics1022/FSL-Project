---
name: landmark-pose-extractor
description: >-
  Use this skill when extracting skeletal landmarks, hand keypoints, pose estimation, or face mesh coordinates using MediaPipe or YOLO-Pose.
license: Apache-2.0
compatibility: requires-mediapipe, requires-numpy
---

# Landmark & Pose Extractor

Operational runbook for high-precision human keypoint extraction, normalization, and jitter reduction.

## Keypoint Extraction Pipeline

### 1. MediaPipe Setup & Hand Landmark Extraction
```python
import cv2
import mediapipe as mp
import numpy as np

mp_hands = mp.solutions.hands
hands = mp_hands.Hands(
    static_image_mode=False,
    max_num_hands=2,
    min_detection_confidence=0.7,
    min_tracking_confidence=0.6
)
```

### 2. Landmark Spatial Normalization
Raw MediaPipe coordinates (0.0 to 1.0) depend on camera distance and framing. Normalize coordinates relative to the wrist (landmark 0) and palm scale:

```python
def normalize_landmarks(landmarks):
    """
    Translates wrist to origin (0, 0, 0) and scales by maximum hand span.
    """
    coords = np.array([[lm.x, lm.y, lm.z] for lm in landmarks])
    wrist = coords[0]
    translated = coords - wrist
    
    # Scale normalization
    scale = np.max(np.linalg.norm(translated, axis=1))
    if scale > 1e-6:
        normalized = translated / scale
    else:
        normalized = translated
    return normalized.flatten()
```

### 3. Temporal Smoothing (Anti-Jitter)
For streaming sequences, apply exponential moving average (EMA) or a OneEuro filter to eliminate high-frequency landmark jitter without adding lag.
