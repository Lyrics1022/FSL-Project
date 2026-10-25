"""
Real-Time Sliding Window Inference Engine.
Simulates on-device camera streaming, 30-frame ring buffer management,
confidence thresholding (tau >= 0.85), and temporal hysteresis.
"""

from collections import deque
from typing import Dict, List, Optional
import time
import cv2
import numpy as np
import torch

from src.models.bilstm_classifier import FSLTemporalClassifier
from src.preprocessing.extractor import VideoLandmarkExtractor


class RealTimeSignTranslator:
    """Manages streaming video input and rolling temporal classification."""

    def __init__(
        self,
        model: FSLTemporalClassifier,
        vocabulary: List[str],
        confidence_threshold: float = 0.85,
        window_size: int = 30,
        stride: int = 5,
        device: str = "cpu",
    ):
        self.model = model.to(device)
        self.model.eval()
        self.vocabulary = vocabulary
        self.confidence_threshold = confidence_threshold
        self.window_size = window_size
        self.stride = stride
        self.device = device

        self.extractor = VideoLandmarkExtractor(target_seq_len=window_size)
        self.frame_buffer: deque = deque(maxlen=window_size)
        self.frame_count = 0

        # State tracking
        self.current_prediction: str = "Listening..."
        self.current_confidence: float = 0.0
        self.history_predictions: deque = deque(maxlen=3)

    def process_frame(self, frame_bgr: np.ndarray) -> Dict[str, any]:
        """
        Ingests a new video frame, updates the rolling buffer, and runs inference every `stride` frames.
        
        Returns:
            dict containing current translation, confidence, and annotated visual frame.
        """
        # 1. Extract 159D landmark feature from current frame
        feat = self.extractor.extract_from_frame(frame_bgr)
        self.frame_buffer.append(feat)
        self.frame_count += 1

        # 2. Run inference only when buffer is full and at stride intervals
        if len(self.frame_buffer) == self.window_size and (self.frame_count % self.stride == 0):
            tensor_seq = torch.tensor(
                np.array(self.frame_buffer), dtype=torch.float32
            ).unsqueeze(0).to(self.device)  # Shape: (1, 30, 159)

            with torch.no_grad():
                logits = self.model(tensor_seq)
                probs = torch.softmax(logits, dim=-1)[0]
                max_prob, pred_idx = torch.max(probs, dim=-1)
                
                conf = max_prob.item()
                predicted_label = self.vocabulary[pred_idx.item()]

                # 3. Apply confidence threshold & temporal hysteresis
                if conf >= self.confidence_threshold:
                    self.history_predictions.append(predicted_label)
                    # Require consistency across consecutive evaluations
                    if self.history_predictions.count(predicted_label) >= 2:
                        self.current_prediction = predicted_label
                        self.current_confidence = conf
                else:
                    self.current_confidence = conf

        # 4. Render HUD onto the frame
        annotated_frame = frame_bgr.copy()
        h, w, _ = annotated_frame.shape
        
        # Background box for subtitle
        cv2.rectangle(annotated_frame, (20, h - 90), (w - 20, h - 20), (20, 20, 20), -1)
        cv2.rectangle(annotated_frame, (20, h - 90), (w - 20, h - 20), (0, 200, 100), 2)
        
        text = f"FSL: {self.current_prediction} ({self.current_confidence * 100:.1f}%)"
        cv2.putText(
            annotated_frame,
            text,
            (40, h - 45),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.8,
            (255, 255, 255),
            2,
            cv2.LINE_AA,
        )

        return {
            "prediction": self.current_prediction,
            "confidence": self.current_confidence,
            "annotated_frame": annotated_frame,
        }

    def close(self):
        self.extractor.close()
