"""
Phase 1 Super Robust Training Pipeline for FSL 20-Sign Vocabulary.
Applies continuous speed warping, 2D planar rotation, scale perturbation,
coordinate jitter, pose dropout, ambidextrous mirroring, and label smoothing.
Exports mobile-optimized fsl_model.onnx with verified parity.
"""

import json
from pathlib import Path
import random
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from sklearn.model_selection import train_test_split
import onnx
import onnxruntime as ort

from src.models.bilstm_classifier import FSLTemporalClassifier
from src.preprocessing.normalizer import TemporalResampler


def rotate_landmarks_2d(feat: np.ndarray, angle_deg: float) -> np.ndarray:
    """Rotates x, y coordinates by angle_deg in degrees around origin."""
    rad = np.deg2rad(angle_deg)
    cos_a, sin_a = np.cos(rad), np.sin(rad)
    out = feat.copy()
    
    # Left hand (0..62), Right hand (63..125)
    for start_idx in [0, 63]:
        for j in range(21):
            idx_x = start_idx + j * 3
            idx_y = idx_x + 1
            x = out[:, idx_x].copy()
            y = out[:, idx_y].copy()
            out[:, idx_x] = x * cos_a - y * sin_a
            out[:, idx_y] = x * sin_a + y * cos_a
            
    # Upper body pose (126..158: 11 points)
    for k in range(11):
        idx_x = 126 + k * 3
        idx_y = idx_x + 1
        x = out[:, idx_x].copy()
        y = out[:, idx_y].copy()
        out[:, idx_x] = x * cos_a - y * sin_a
        out[:, idx_y] = x * sin_a + y * cos_a

    return out


def resample_temporal(feat: np.ndarray, target_len: int = 30) -> np.ndarray:
    """Resamples variable length sequence to target_len using linear interpolation."""
    orig_len = len(feat)
    if orig_len == target_len:
        return feat.copy()
    orig_t = np.linspace(0.0, 1.0, num=orig_len)
    target_t = np.linspace(0.0, 1.0, num=target_len)
    res = np.empty((target_len, feat.shape[1]), dtype=np.float32)
    for d in range(feat.shape[1]):
        res[:, d] = np.interp(target_t, orig_t, feat[:, d])
    return res


class SuperRobustFSLDataset(Dataset):
    def __init__(self, samples, labels, augment=False):
        self.samples = samples
        self.labels = labels
        self.augment = augment

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        feat = self.samples[idx].copy()  # Shape: (30, 159)

        if self.augment:
            # 1. Temporal Speed Warping (0.8x to 1.25x speed)
            if random.random() < 0.65:
                warp_len = random.randint(22, 38)
                # Resample 30 -> warp_len
                t_warp = resample_temporal(feat, warp_len)
                # Resample back -> 30
                feat = resample_temporal(t_warp, 30)

            # 2. Temporal Offset / Shifting (-3 to +3 frames)
            if random.random() < 0.5:
                shift = random.randint(-3, 3)
                if shift > 0:
                    feat = np.pad(feat, ((shift, 0), (0, 0)), mode="edge")[:30]
                elif shift < 0:
                    feat = np.pad(feat, ((0, -shift), (0, 0)), mode="edge")[-shift:]
                    feat = np.pad(feat, ((0, 30 - len(feat)), (0, 0)), mode="edge")

            # 3. 2D Planar Rotation (+/- 12 degrees)
            if random.random() < 0.7:
                angle = random.uniform(-12.0, 12.0)
                feat = rotate_landmarks_2d(feat, angle)

            # 4. Hand Coordinate Scaling (0.88x to 1.12x)
            if random.random() < 0.6:
                scale = random.uniform(0.88, 1.12)
                feat[:, :126] *= scale

            # 5. Hand Swap & Horizontal Reflection (Ambidextrous signing)
            if random.random() < 0.5:
                lh = feat[:, :63].copy()
                rh = feat[:, 63:126].copy()
                feat[:, :63] = rh
                feat[:, 63:126] = lh
                # Invert X axis for hands
                feat[:, 0:126:3] = -feat[:, 0:126:3]
                # Invert X axis for pose
                feat[:, 126::3] = -feat[:, 126::3]

            # 6. Upper Body Pose Dropout (forces hand-only recognition)
            if random.random() < 0.5:
                feat[:, 126:] = 0.0

            # 7. Coordinate Gaussian Jitter (sensor vibration)
            if random.random() < 0.7:
                noise = np.random.normal(0, 0.012, feat.shape).astype(np.float32)
                feat += noise

        return torch.tensor(feat, dtype=torch.float32), torch.tensor(self.labels[idx], dtype=torch.long)


def train_and_export():
    with open("models/vocab.json", "r", encoding="utf-8") as f:
        vocab = json.load(f)
    print(f"[*] Loaded active vocabulary: {len(vocab)} classes")

    feat_dir = Path("FSL_Features")
    samples, labels = [], []

    for idx, c in enumerate(vocab):
        c_folder = feat_dir / c
        if not c_folder.exists():
            matches = [d for d in feat_dir.iterdir() if d.is_dir() and d.name.replace("’", "'").replace("'", "") == c.replace("’", "'").replace("'", "")]
            if matches:
                c_folder = matches[0]
            else:
                print(f"[!] Warning: Folder for {c} not found")
                continue

        c_files = list(c_folder.glob("*.npy"))
        for f in c_files:
            data = np.load(f)
            if data.shape == (30, 159):
                samples.append(data.astype(np.float32))
                labels.append(idx)

    print(f"[*] Total valid dataset samples: {len(samples)} across {len(vocab)} classes")

    # Stratified Train/Val Split (80% train, 20% val)
    train_x, val_x, train_y, val_y = train_test_split(
        samples, labels, test_size=0.2, random_state=42, stratify=labels
    )

    train_dataset = SuperRobustFSLDataset(train_x, train_y, augment=True)
    val_dataset = SuperRobustFSLDataset(val_x, val_y, augment=False)

    train_loader = DataLoader(train_dataset, batch_size=16, shuffle=True, drop_last=False)
    val_loader = DataLoader(val_dataset, batch_size=16, shuffle=False)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[*] Training on: {device}")

    model = FSLTemporalClassifier(
        input_dim=159,
        hidden_dim=128,
        num_lstm_layers=2,
        num_classes=len(vocab),
        dropout=0.35,
    ).to(device)

    # Label smoothing regularizes prediction confidence and prevents overfitting
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-2)
    epochs = 100
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs, eta_min=1e-5)

    best_score = 0.0
    best_path = Path("models/robust_model.pt")

    print("\n" + "=" * 78)
    print(f"{'Epoch':<6} | {'Train Loss':<11} | {'Val Acc (%)':<12} | {'ZeroPose Acc':<13} | {'Mirror Acc':<12}")
    print("=" * 78)

    for epoch in range(1, epochs + 1):
        model.train()
        total_train_loss = 0.0

        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            optimizer.zero_grad()
            out = model(x)
            loss = criterion(out, y)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            total_train_loss += loss.item()

        scheduler.step()
        avg_train_loss = total_train_loss / max(1, len(train_loader))

        # 1. Clean Validation
        model.eval()
        correct, total = 0, 0
        with torch.no_grad():
            for x, y in val_loader:
                x, y = x.to(device), y.to(device)
                preds = model(x).argmax(dim=-1)
                correct += (preds == y).sum().item()
                total += y.size(0)
        clean_acc = correct / total * 100.0

        # 2. Zero-Pose Validation (stress test hand-only reliance)
        correct_zp = 0
        with torch.no_grad():
            for x, y in val_loader:
                x_zp = x.clone()
                x_zp[:, :, 126:] = 0.0
                x_zp, y = x_zp.to(device), y.to(device)
                preds = model(x_zp).argmax(dim=-1)
                correct_zp += (preds == y).sum().item()
        zp_acc = correct_zp / total * 100.0

        # 3. Mirror-Hand Validation (stress test ambidextrous robustness)
        correct_sw = 0
        with torch.no_grad():
            for x, y in val_loader:
                x_sw = x.clone()
                lh = x_sw[:, :, :63].clone()
                rh = x_sw[:, :, 63:126].clone()
                x_sw[:, :, :63] = rh
                x_sw[:, :, 63:126] = lh
                x_sw[:, :, 0:126:3] = -x_sw[:, :, 0:126:3]
                x_sw[:, :, 126::3] = -x_sw[:, :, 126::3]
                x_sw, y = x_sw.to(device), y.to(device)
                preds = model(x_sw).argmax(dim=-1)
                correct_sw += (preds == y).sum().item()
        sw_acc = correct_sw / total * 100.0

        # Composite score prioritizes both clean accuracy and robustness invariance
        composite = 0.5 * clean_acc + 0.25 * zp_acc + 0.25 * sw_acc

        if epoch % 5 == 0 or epoch == epochs:
            print(f"{epoch:<6} | {avg_train_loss:<11.4f} | {clean_acc:<12.2f} | {zp_acc:<13.2f} | {sw_acc:<12.2f}")

        if composite >= best_score:
            best_score = composite
            torch.save(model.state_dict(), best_path)

    print("=" * 78)
    print(f"[+] Best Composite Robustness Score: {best_score:.2f}%")

    # Export to ONNX
    print("\n[*] Exporting best robust model to ONNX...")
    model.load_state_dict(torch.load(best_path, map_location="cpu"))
    model.eval()

    dummy_input = torch.zeros(1, 30, 159, dtype=torch.float32)
    models_onnx = Path("models/fsl_model.onnx")
    android_onnx = Path("android/app/src/main/assets/fsl_model.onnx")

    data_file = Path("models/fsl_model.onnx.data")
    if data_file.exists():
        data_file.unlink()

    torch.onnx.export(
        model,
        dummy_input,
        str(models_onnx),
        input_names=["landmarks_sequence"],
        output_names=["class_logits"],
        dynamic_axes={"landmarks_sequence": {0: "batch_size"}, "class_logits": {0: "batch_size"}},
        opset_version=14,
        do_constant_folding=True,
        dynamo=False,
    )

    # Copy directly into Android assets
    import shutil
    shutil.copy2(models_onnx, android_onnx)
    shutil.copy2("models/vocab.json", "android/app/src/main/assets/vocab.json")
    print(f"[+] Successfully exported {models_onnx} and synced to {android_onnx} ({models_onnx.stat().st_size / 1024 / 1024:.2f} MB)")

    # Verify ONNX Runtime Parity
    onnx_model = onnx.load(str(models_onnx))
    onnx.checker.check_model(onnx_model)

    ort_session = ort.InferenceSession(str(models_onnx), providers=["CPUExecutionProvider"])
    ort_out = ort_session.run(None, {"landmarks_sequence": dummy_input.numpy()})[0]
    torch_out = model(dummy_input).detach().numpy()
    np.testing.assert_allclose(torch_out, ort_out, rtol=1e-3, atol=1e-4)
    print("[+] ONNX Runtime parity verified successfully with PyTorch!")


if __name__ == "__main__":
    train_and_export()
