import json
import numpy as np
from pathlib import Path
import random
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from sklearn.model_selection import train_test_split

from src.models.bilstm_classifier import FSLTemporalClassifier

class RobustFSLDataset(Dataset):
    def __init__(self, samples, labels, augment=False):
        self.samples = samples
        self.labels = labels
        self.augment = augment

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        feat = self.samples[idx].copy() # (30, 159)
        
        if self.augment:
            # 1. Pose Dropout: randomly zero out upper body pose (50% chance)
            if random.random() < 0.5:
                feat[:, 126:] = 0.0
                
            # 2. Mirror / Hand Swap: randomly swap LH and RH and flip X (50% chance)
            if random.random() < 0.5:
                lh = feat[:, :63].copy()
                rh = feat[:, 63:126].copy()
                feat[:, :63] = rh
                feat[:, 63:126] = lh
                # Invert X axis for hands
                feat[:, 0:126:3] = -feat[:, 0:126:3]
                # Invert X axis for pose if present
                feat[:, 126::3] = -feat[:, 126::3]
                
            # 3. Random Gaussian jitter (noise)
            if random.random() < 0.6:
                noise = np.random.normal(0, 0.02, feat.shape).astype(np.float32)
                feat += noise
                
            # 4. Temporal frame warping / jitter
            if random.random() < 0.5:
                warp_idx = np.sort(np.random.choice(30, size=30, replace=True))
                feat = feat[warp_idx]

        return torch.tensor(feat, dtype=torch.float32), torch.tensor(self.labels[idx], dtype=torch.long)

def main():
    with open("models/vocab.json", "r", encoding="utf-8") as f:
        vocab = json.load(f)
    print(f"[*] Loaded vocab: {len(vocab)} classes")
    
    feat_dir = Path("FSL_Features")
    samples = []
    labels = []
    
    for idx, c in enumerate(vocab):
        c_folder = feat_dir / c
        if not c_folder.exists():
            # Try finding folder ignoring unicode quote variations
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
                
    print(f"[*] Total valid samples collected: {len(samples)} across {len(vocab)} classes")
    
    train_x, val_x, train_y, val_y = train_test_split(
        samples, labels, test_size=0.2, random_state=42, stratify=labels
    )
    
    train_dataset = RobustFSLDataset(train_x, train_y, augment=True)
    val_dataset = RobustFSLDataset(val_x, val_y, augment=False)
    
    train_loader = DataLoader(train_dataset, batch_size=16, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=16, shuffle=False)
    
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = FSLTemporalClassifier(
        input_dim=159,
        hidden_dim=128,
        num_lstm_layers=2,
        num_classes=len(vocab),
        dropout=0.3
    ).to(device)
    
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-2)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=50)
    
    best_acc = 0.0
    best_path = Path("models/robust_model.pt")
    
    print("\n[*] Starting training with robust domain augmentations...")
    for epoch in range(1, 51):
        model.train()
        train_loss = 0.0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            optimizer.zero_grad()
            out = model(x)
            loss = criterion(out, y)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            train_loss += loss.item()
        scheduler.step()
        
        # Validation on normal samples
        model.eval()
        correct, total = 0, 0
        with torch.no_grad():
            for x, y in val_loader:
                x, y = x.to(device), y.to(device)
                preds = model(x).argmax(dim=-1)
                correct += (preds == y).sum().item()
                total += y.size(0)
        acc = correct / total * 100.0
        
        # Test also on zero-pose and swapped hands to check true robustness!
        if epoch % 10 == 0 or epoch == 50:
            with torch.no_grad():
                # Zero pose test
                c_zp = 0
                for x, y in val_loader:
                    x_zp = x.clone()
                    x_zp[:, :, 126:] = 0.0
                    x_zp, y = x_zp.to(device), y.to(device)
                    preds = model(x_zp).argmax(dim=-1)
                    c_zp += (preds == y).sum().item()
                acc_zp = c_zp / total * 100.0
                
                # Swapped hands test
                c_sw = 0
                for x, y in val_loader:
                    x_sw = x.clone()
                    lh = x_sw[:, :, :63].clone()
                    rh = x_sw[:, :, 63:126].clone()
                    x_sw[:, :, :63] = rh
                    x_sw[:, :, 63:126] = lh
                    x_sw[:, :, 0:126:3] = -x_sw[:, :, 0:126:3]
                    x_sw, y = x_sw.to(device), y.to(device)
                    preds = model(x_sw).argmax(dim=-1)
                    c_sw += (preds == y).sum().item()
                acc_sw = c_sw / total * 100.0
                
            print(f"Epoch {epoch:2d} | Val Acc: {acc:5.1f}% | Zero-Pose Acc: {acc_zp:5.1f}% | Mirror-Hand Acc: {acc_sw:5.1f}%")
        
        if acc >= best_acc:
            best_acc = acc
            torch.save(model.state_dict(), best_path)
            
    print(f"\n[+] Best Validation Accuracy: {best_acc:.2f}%")
    
    # Export to ONNX
    print("[*] Exporting robust model to ONNX...")
    model.load_state_dict(torch.load(best_path))
    model.eval()
    
    dummy_input = torch.zeros(1, 30, 159, dtype=torch.float32).to(device)
    onnx_path = Path("models/fsl_model.onnx")
    
    torch.onnx.export(
        model,
        dummy_input,
        str(onnx_path),
        input_names=["landmarks_sequence"],
        output_names=["class_logits"],
        dynamic_axes={"landmarks_sequence": {0: "batch_size"}, "class_logits": {0: "batch_size"}},
        opset_version=14,
        dynamo=False,
    )

    print(f"[+] Exported robust ONNX model to {onnx_path} ({onnx_path.stat().st_size / 1024 / 1024:.2f} MB)")

if __name__ == "__main__":
    main()
