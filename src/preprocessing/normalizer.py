"""
Spatial and Temporal Normalization Module for FSL Landmark Trajectories.
Converts raw MediaPipe joint positions into scale-, translation-, and speed-invariant tensors.
"""

from typing import Optional
import numpy as np


class SpatialNormalizer:
    """Normalizes 3D skeletal coordinates to achieve translation and scale invariance."""

    @staticmethod
    def normalize_hand_landmarks(landmarks_3d: np.ndarray, epsilon: float = 1e-6) -> np.ndarray:
        """
        Normalizes a (21, 3) hand landmark array relative to the wrist (landmark 0).
        
        Args:
            landmarks_3d: np.ndarray of shape (21, 3) with [x, y, z] coordinates.
            epsilon: small float to prevent division by zero.
            
        Returns:
            np.ndarray of shape (21, 3) normalized coordinates.
        """
        if landmarks_3d.shape != (21, 3):
            raise ValueError(f"Expected shape (21, 3), got {landmarks_3d.shape}")

        wrist = landmarks_3d[0]
        translated = landmarks_3d - wrist
        
        # Calculate maximum Euclidean distance from wrist to any joint as scale factor
        distances = np.linalg.norm(translated, axis=1)
        scale = np.max(distances)
        
        if scale < epsilon:
            return translated
        
        return translated / (scale + epsilon)

    @staticmethod
    def normalize_frame_features(
        left_hand: Optional[np.ndarray],
        right_hand: Optional[np.ndarray],
        pose_upper: Optional[np.ndarray],
    ) -> np.ndarray:
        """
        Combines and normalizes left hand (21,3), right hand (21,3), and upper body pose (11,3).
        Zero-pads missing hands/pose if tracking is lost in a frame.
        
        Returns:
            np.ndarray: Flattened 1D feature vector of shape ((21 + 21 + 11) * 3,) = (159,)
        """
        # 1. Left hand (21 * 3 = 63)
        if left_hand is not None and left_hand.size > 0:
            norm_lh = SpatialNormalizer.normalize_hand_landmarks(left_hand)
        else:
            norm_lh = np.zeros((21, 3), dtype=np.float32)

        # 2. Right hand (21 * 3 = 63)
        if right_hand is not None and right_hand.size > 0:
            norm_rh = SpatialNormalizer.normalize_hand_landmarks(right_hand)
        else:
            norm_rh = np.zeros((21, 3), dtype=np.float32)

        # 3. Upper body pose (11 joints: nose, shoulders, elbows, wrists, hips = 33 floats)
        if pose_upper is not None and pose_upper.size > 0:
            # Center pose around midpoint between shoulders (landmarks 11 and 12 in MediaPipe)
            mid_shoulder = (pose_upper[1] + pose_upper[2]) / 2.0 if len(pose_upper) > 2 else pose_upper[0]
            norm_pose = pose_upper - mid_shoulder
            pose_scale = np.max(np.linalg.norm(norm_pose, axis=1)) + 1e-6
            norm_pose = norm_pose / pose_scale
        else:
            norm_pose = np.zeros((11, 3), dtype=np.float32)

        combined = np.concatenate([norm_lh.flatten(), norm_rh.flatten(), norm_pose.flatten()])
        return combined.astype(np.float32)


class TemporalResampler:
    """Resamples variable-length frame sequences to a fixed temporal window length."""

    @staticmethod
    def resample_sequence(sequence: np.ndarray, target_len: int = 30) -> np.ndarray:
        """
        Linearly interpolates a (T, D) sequence along the temporal axis to shape (target_len, D).
        
        Args:
            sequence: np.ndarray of shape (T, D) where T is original frames, D is feature dim.
            target_len: target number of frames (default 30 frames = 1 second at 30 FPS).
            
        Returns:
            np.ndarray of shape (target_len, D).
        """
        t_orig, d_feat = sequence.shape
        if t_orig == target_len:
            return sequence.astype(np.float32)
        if t_orig == 0:
            return np.zeros((target_len, d_feat), dtype=np.float32)
        if t_orig == 1:
            return np.repeat(sequence, target_len, axis=0).astype(np.float32)

        # Original time indices [0.0 ... 1.0] and target time indices
        orig_indices = np.linspace(0.0, 1.0, num=t_orig)
        target_indices = np.linspace(0.0, 1.0, num=target_len)

        resampled = np.empty((target_len, d_feat), dtype=np.float32)
        for dim in range(d_feat):
            resampled[:, dim] = np.interp(target_indices, orig_indices, sequence[:, dim])

        return resampled
