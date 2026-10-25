"""
Fast Feature Extractor for Strategy B: Top 20 Essential Daily Signs.
Extracts 10 videos per sign with optimized temporal sampling.
"""

import json
from pathlib import Path
import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

from src.preprocessing.normalizer import SpatialNormalizer, TemporalResampler

# Load active 20 daily signs directly from models/vocab.json
with open("models/vocab.json", "r", encoding="utf-8") as _f:
    TOP_20_SIGNS = json.load(_f)



def extract_video_fast(video_path: Path, hand_lm, pose_lm, target_len: int = 30) -> np.ndarray:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        return np.zeros((target_len, 159), dtype=np.float32)

    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if total_frames <= 0:
        total_frames = 200

    # Sample ~40 frames uniformly across the video to achieve 6x speedup
    sample_indices = np.linspace(0, max(0, total_frames - 1), num=min(45, total_frames), dtype=int)
    sample_set = set(sample_indices)

    frame_features = []
    frame_idx = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        if frame_idx in sample_set:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)

            hand_res = hand_lm.detect(mp_img)
            pose_res = pose_lm.detect(mp_img)

            lh, rh = None, None
            if hand_res.hand_landmarks:
                for i, handedness in enumerate(hand_res.handedness):
                    label = handedness[0].category_name
                    pts = np.array([[p.x, p.y, p.z] for p in hand_res.hand_landmarks[i]], dtype=np.float32)
                    if label == "Left":
                        lh = pts
                    else:
                        rh = pts

            pose_pts = None
            if pose_res.pose_landmarks and len(pose_res.pose_landmarks) > 0:
                indices = [0, 11, 12, 13, 14, 15, 16, 23, 24, 7, 8]
                pose_pts = np.array(
                    [[pose_res.pose_landmarks[0][idx].x, pose_res.pose_landmarks[0][idx].y, pose_res.pose_landmarks[0][idx].z] for idx in indices],
                    dtype=np.float32,
                )

            feat = SpatialNormalizer.normalize_frame_features(lh, rh, pose_pts)
            frame_features.append(feat)

        frame_idx += 1

    cap.release()

    if not frame_features:
        return np.zeros((target_len, 159), dtype=np.float32)

    seq = np.array(frame_features, dtype=np.float32)

    # Filter idle start/end if hands are present
    hand_energy = np.sum(np.abs(seq[:, :126]), axis=1)
    active = np.where(hand_energy > 0)[0]
    if len(active) >= 4:
        seq = seq[max(0, active[0] - 1): min(len(seq), active[-1] + 2)]

    return TemporalResampler.resample_sequence(seq, target_len=target_len)


def main():
    dataset_dir = Path(r"c:\Users\Rose\Desktop\FSL_Project\FSL_Dataset")
    output_dir = Path(r"c:\Users\Rose\Desktop\FSL_Project\FSL_Features")
    output_dir.mkdir(parents=True, exist_ok=True)

    hand_opts = vision.HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path="models/tasks/hand_landmarker.task"),
        num_hands=2,
        min_hand_detection_confidence=0.3,
    )
    pose_opts = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path="models/tasks/pose_landmarker_lite.task"),
        min_pose_detection_confidence=0.3,
    )

    print("[*] Initializing MediaPipe Tasks models...")
    hand_lm = vision.HandLandmarker.create_from_options(hand_opts)
    pose_lm = vision.PoseLandmarker.create_from_options(pose_opts)

    print(f"[*] Starting Strategy B extraction for {len(TOP_20_SIGNS)} daily signs (10 videos each)...")
    total_processed = 0

    for idx, folder_name in enumerate(TOP_20_SIGNS, 1):
        folder = dataset_dir / folder_name
        if not folder.exists():
            print(f"[!] Warning: Folder {folder_name} not found.")
            continue

        out_folder = output_dir / folder_name
        out_folder.mkdir(exist_ok=True)

        # Process all available videos per folder (up to 22 videos)
        videos = sorted(list(folder.glob("*.MOV")))
        print(f"[{idx}/{len(TOP_20_SIGNS)}] Processing {folder_name} ({len(videos)} videos)...")

        for v in videos:
            out_file = out_folder / f"{v.stem}.npy"
            if out_file.exists():
                total_processed += 1
                continue

            try:
                features = extract_video_fast(v, hand_lm, pose_lm, target_len=30)
                np.save(out_file, features)
                total_processed += 1
            except Exception as e:
                print(f"    Error processing {v.name}: {e}")

    hand_lm.close()
    pose_lm.close()
    print(f"[+] Extraction Complete! Total processed files: {total_processed} across {len(TOP_20_SIGNS)} signs.")


if __name__ == "__main__":
    main()
