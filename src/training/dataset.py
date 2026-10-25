"""
Dataset and DataLoader Module for FSL Training.
Supports loading pre-extracted .npy landmark sequences or extracting directly from raw video folders.
"""

from pathlib import Path
from typing import Dict, List, Optional, Tuple
import numpy as np
import torch
from torch.utils.data import Dataset, DataLoader
from sklearn.model_selection import train_test_split

from src.preprocessing.extractor import VideoLandmarkExtractor


class FSLDataset(Dataset):
    """PyTorch Dataset for (30, 159) landmark sequences and their class labels."""

    def __init__(
        self,
        features: List[np.ndarray],
        labels: List[int],
        augment: bool = False,
    ):
        self.features = features
        self.labels = labels
        self.augment = augment

    def __len__(self) -> int:
        return len(self.features)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor]:
        feat = self.features[idx].copy()  # Shape: (30, 159)

        # Coordinate data augmentation during training
        if self.augment:
            # 1. Random jitter (Gaussian noise on joint coordinates)
            if np.random.rand() > 0.5:
                noise = np.random.normal(0, 0.015, feat.shape).astype(np.float32)
                feat += noise

            # 2. Random temporal frame drop (simulate minor frame skips)
            if np.random.rand() > 0.6:
                drop_indices = np.random.choice(30, size=2, replace=False)
                feat[drop_indices] = feat[np.maximum(0, drop_indices - 1)]

        tensor_x = torch.tensor(feat, dtype=torch.float32)
        tensor_y = torch.tensor(self.labels[idx], dtype=torch.long)
        return tensor_x, tensor_y


def prepare_fsl_data(
    data_dir: str or Path,
    target_seq_len: int = 30,
    test_size: float = 0.2,
    random_seed: int = 42,
) -> Tuple[FSLDataset, FSLDataset, List[str]]:
    """
    Scans a folder structured by class names:
      data_dir/
        ├── HELLO/
        │   ├── 01.mp4 (or .npy)
        │   └── 02.mp4
        └── THANK_YOU/
            └── ...

    Extracts landmarks (caching to .npy), splits into stratified train/val sets,
    and returns (train_dataset, val_dataset, vocab_labels).
    """
    data_path = Path(data_dir)
    if not data_path.exists():
        raise FileNotFoundError(f"Data directory does not exist: {data_path}")

    # Discover classes (subdirectories)
    class_folders = sorted([d for d in data_path.iterdir() if d.is_dir()])
    if not class_folders:
        raise ValueError(f"No class folders found in {data_path}. Expecting data_dir/<sign_name>/*.mp4")

    vocab = [d.name for d in class_folders]
    class_to_idx = {name: idx for idx, name in enumerate(vocab)}

    all_features: List[np.ndarray] = []
    all_labels: List[int] = []

    extractor = VideoLandmarkExtractor(target_seq_len=target_seq_len)

    try:
        for class_dir in class_folders:
            label = class_to_idx[class_dir.name]
            # Look for both raw videos and pre-saved .npy files
            video_files = list(class_dir.glob("*.mp4")) + list(class_dir.glob("*.mov")) + list(class_dir.glob("*.avi"))
            npy_files = list(class_dir.glob("*.npy"))

            # 1. Load pre-cached .npy files if available
            for npy_file in npy_files:
                feat = np.load(npy_file)
                if feat.shape == (target_seq_len, 159):
                    all_features.append(feat)
                    all_labels.append(label)

            # 2. Extract from raw videos and save cached .npy
            for vid_file in video_files:
                cached_npy = vid_file.with_suffix(".npy")
                if cached_npy.exists():
                    continue  # already loaded above

                feat = extractor.extract_from_video_file(vid_file)
                np.save(cached_npy, feat)  # Cache for fast re-runs
                all_features.append(feat)
                all_labels.append(label)

    finally:
        extractor.close()

    if len(all_features) == 0:
        raise ValueError("No video or .npy files were found in the class directories.")

    # Stratified Train/Val Split
    train_x, val_x, train_y, val_y = train_test_split(
        all_features,
        all_labels,
        test_size=test_size,
        random_state=random_seed,
        stratify=all_labels if len(set(all_labels)) > 1 else None,
    )

    train_dataset = FSLDataset(train_x, train_y, augment=True)
    val_dataset = FSLDataset(val_x, val_y, augment=False)

    return train_dataset, val_dataset, vocab
