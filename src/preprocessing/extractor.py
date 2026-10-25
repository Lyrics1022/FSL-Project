"""
MediaPipe Video Keypoint Extractor using modern MediaPipe Tasks API.
Processes raw FSL video files (.mp4, .mov, .avi) and outputs normalized sequence arrays (.npy).
"""

from pathlib import Path
from typing import List, Optional, Tuple
import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

from src.preprocessing.normalizer import SpatialNormalizer, TemporalResampler


class VideoLandmarkExtractor:
    """Extracts, normalizes, and resamples landmarks from video files using MediaPipe Tasks."""

    def __init__(
        self,
        hand_model_path: str = "models/tasks/hand_landmarker.task",
        pose_model_path: str = "models/tasks/pose_landmarker_lite.task",
        min_detection_confidence: float = 0.3,
        target_seq_len: int = 30,
    ):
        self.target_seq_len = target_seq_len
        self.hand_model_path = Path(hand_model_path)
        self.pose_model_path = Path(pose_model_path)

        if not self.hand_model_path.exists() or not self.pose_model_path.exists():
            raise FileNotFoundError(
                f"MediaPipe task models not found. Hand: {self.hand_model_path}, Pose: {self.pose_model_path}"
            )

        hand_options = vision.HandLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(self.hand_model_path)),
            num_hands=2,
            min_hand_detection_confidence=min_detection_confidence,
        )
        self.hand_landmarker = vision.HandLandmarker.create_from_options(hand_options)

        pose_options = vision.PoseLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(self.pose_model_path)),
            min_pose_detection_confidence=min_detection_confidence,
        )
        self.pose_landmarker = vision.PoseLandmarker.create_from_options(pose_options)

    def extract_from_frame(self, frame_bgr: np.ndarray) -> np.ndarray:
        """
        Processes a single BGR frame and returns a (159,) normalized feature vector.
        """
        rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)

        hand_res = self.hand_landmarker.detect(mp_image)
        pose_res = self.pose_landmarker.detect(mp_image)

        left_hand: Optional[np.ndarray] = None
        right_hand: Optional[np.ndarray] = None

        if hand_res.hand_landmarks:
            for i, handedness in enumerate(hand_res.handedness):
                label = handedness[0].category_name  # 'Left' or 'Right'
                pts = np.array(
                    [[lm.x, lm.y, lm.z] for lm in hand_res.hand_landmarks[i]],
                    dtype=np.float32,
                )
                if label == "Left":
                    left_hand = pts
                else:
                    right_hand = pts

        pose_upper: Optional[np.ndarray] = None
        if pose_res.pose_landmarks and len(pose_res.pose_landmarks) > 0:
            indices = [0, 11, 12, 13, 14, 15, 16, 23, 24, 7, 8]
            pose_upper = np.array(
                [
                    [
                        pose_res.pose_landmarks[0][idx].x,
                        pose_res.pose_landmarks[0][idx].y,
                        pose_res.pose_landmarks[0][idx].z,
                    ]
                    for idx in indices
                ],
                dtype=np.float32,
            )

        return SpatialNormalizer.normalize_frame_features(left_hand, right_hand, pose_upper)

    def extract_from_video_file(self, video_path: Path) -> np.ndarray:
        """
        Extracts all frames from a video file, normalizes them, filters idle start/end frames,
        and resamples to (target_seq_len, 159).
        """
        cap = cv2.VideoCapture(str(video_path))
        if not cap.isOpened():
            raise FileNotFoundError(f"Cannot open video file: {video_path}")

        frame_features: List[np.ndarray] = []
        try:
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                feat = self.extract_from_frame(frame)
                frame_features.append(feat)
        finally:
            cap.release()

        if not frame_features:
            return np.zeros((self.target_seq_len, 159), dtype=np.float32)

        sequence = np.array(frame_features, dtype=np.float32)  # Shape: (T, 159)

        # Filter to active motion segments if hands are detected
        # First 126 features correspond to left and right hands
        hand_energy = np.sum(np.abs(sequence[:, :126]), axis=1)
        active_indices = np.where(hand_energy > 0)[0]

        if len(active_indices) >= 5:
            start_idx = max(0, active_indices[0] - 2)
            end_idx = min(len(sequence), active_indices[-1] + 3)
            sequence = sequence[start_idx:end_idx]

        return TemporalResampler.resample_sequence(sequence, target_len=self.target_seq_len)

    def close(self):
        """Release MediaPipe resources."""
        self.hand_landmarker.close()
        self.pose_landmarker.close()
