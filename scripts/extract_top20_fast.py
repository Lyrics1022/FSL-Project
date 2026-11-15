"""
Fast Feature Extractor for Strategy B: Top 20 Essential Daily Signs.
Extracts 10 videos per sign with optimized temporal sampling.
"""

from concurrent.futures import ProcessPoolExecutor
import json
import os
from pathlib import Path
import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

from src.preprocessing.normalizer import SpatialNormalizer, TemporalResampler

# Load active signs directly from models/vocab.json
with open("models/vocab.json", "r", encoding="utf-8") as _f:
    TOP_20_SIGNS = json.load(_f)

_WORKER_HAND_LM = None
_WORKER_POSE_LM = None


def _init_worker():
    global _WORKER_HAND_LM, _WORKER_POSE_LM
    hand_opts = vision.HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path="models/tasks/hand_landmarker.task"),
        num_hands=2,
        min_hand_detection_confidence=0.3,
    )
    pose_opts = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path="models/tasks/pose_landmarker_lite.task"),
        min_pose_detection_confidence=0.3,
    )
    _WORKER_HAND_LM = vision.HandLandmarker.create_from_options(hand_opts)
    _WORKER_POSE_LM = vision.PoseLandmarker.create_from_options(pose_opts)


def _process_single_video(args):
    video_path, out_file = args
    try:
        features = extract_video_fast(video_path, _WORKER_HAND_LM, _WORKER_POSE_LM, target_len=30)
        np.save(out_file, features)
        return (True, str(video_path))
    except Exception as e:
        return (False, f"{video_path}: {e}")



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


def _resolve_folder(base_dir: Path, name: str):
    direct = base_dir / name
    if direct.exists():
        return direct
    key = name.replace("’", "'").replace("'", "")
    for d in base_dir.iterdir():
        if d.is_dir() and d.name.replace("’", "'").replace("'", "") == key:
            return d
    return None


def main():
    dataset_dir = Path("FSL_Dataset")
    output_dir = Path("FSL_Features")
    output_dir.mkdir(parents=True, exist_ok=True)

    tasks = []
    skipped = 0

    for folder_name in TOP_20_SIGNS:
        folder = _resolve_folder(dataset_dir, folder_name)
        if folder is None:
            print(f"[!] Warning: Folder {folder_name} not found.")
            continue

        out_folder = _resolve_folder(output_dir, folder_name) or (output_dir / folder.name)
        out_folder.mkdir(exist_ok=True)

        videos = sorted(list(folder.glob("*.MOV")))
        for v in videos:
            out_file = out_folder / f"{v.stem}.npy"
            if out_file.exists():
                skipped += 1
            else:
                tasks.append((v, out_file))

    print(f"[*] Active vocab: {len(TOP_20_SIGNS)} signs | Already extracted: {skipped} | Pending: {len(tasks)}")
    if not tasks:
        print("[+] All features already extracted!")
        return

    workers = min(8, os.cpu_count() or 4)
    print(f"[*] Starting parallel MediaPipe extraction with {workers} workers...")
    done = 0
    errors = 0

    with ProcessPoolExecutor(max_workers=workers, initializer=_init_worker) as executor:
        for ok, info in executor.map(_process_single_video, tasks):
            if ok:
                done += 1
            else:
                errors += 1
                print(f"    Error processing {info}")
            if (done + errors) % 50 == 0 or (done + errors) == len(tasks):
                print(f"    Progress: {done + errors}/{len(tasks)} (ok={done}, err={errors})")

    print(f"[+] Extraction Complete! Newly extracted: {done}, skipped: {skipped}, errors: {errors}")


if __name__ == "__main__":
    main()
