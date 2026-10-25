"""
Unit tests for SpatialNormalizer and TemporalResampler.
"""

import numpy as np
import pytest

from src.preprocessing.normalizer import SpatialNormalizer, TemporalResampler


def test_hand_normalization_wrist_at_origin():
    # Construct synthetic 21 joints where wrist is at (10.0, 20.0, 30.0)
    raw_hand = np.zeros((21, 3), dtype=np.float32)
    raw_hand[0] = [10.0, 20.0, 30.0]  # Wrist
    raw_hand[4] = [15.0, 20.0, 30.0]  # Thumb tip (dist = 5.0)
    raw_hand[8] = [10.0, 28.0, 30.0]  # Index tip (dist = 8.0 -> max distance)

    norm_hand = SpatialNormalizer.normalize_hand_landmarks(raw_hand)

    # 1. Wrist must be at origin (0, 0, 0)
    np.testing.assert_allclose(norm_hand[0], [0.0, 0.0, 0.0], atol=1e-5)

    # 2. Maximum joint distance must be normalized to <= 1.0
    distances = np.linalg.norm(norm_hand, axis=1)
    assert np.max(distances) <= 1.0 + 1e-5


def test_frame_feature_vector_dimension():
    left_hand = np.random.rand(21, 3).astype(np.float32)
    right_hand = np.random.rand(21, 3).astype(np.float32)
    pose_upper = np.random.rand(11, 3).astype(np.float32)

    features = SpatialNormalizer.normalize_frame_features(left_hand, right_hand, pose_upper)

    # Total features: (21 + 21 + 11) * 3 = 53 * 3 = 159
    assert features.shape == (159,)
    assert not np.isnan(features).any()


def test_missing_hands_zero_padded():
    # Both hands missing (e.g. signer resting hands below frame)
    features = SpatialNormalizer.normalize_frame_features(None, None, None)
    assert features.shape == (159,)
    np.testing.assert_allclose(features, np.zeros(159), atol=1e-5)


def test_temporal_resampler_interpolation():
    # Sequence of 15 frames resampled to 30 frames
    orig_seq = np.linspace(0, 100, 15 * 159).reshape(15, 159).astype(np.float32)
    resampled = TemporalResampler.resample_sequence(orig_seq, target_len=30)

    assert resampled.shape == (30, 159)
    # Start and end values should be preserved
    np.testing.assert_allclose(resampled[0], orig_seq[0], atol=1e-4)
    np.testing.assert_allclose(resampled[-1], orig_seq[-1], atol=1e-4)
