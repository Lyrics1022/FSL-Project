"""
Training Pipeline for FSL 1D-CNN + BiLSTM Classifier.
Runs on local CPU or GPU, tracks validation accuracy, and exports model checkpoints.
"""

import argparse
import json
from pathlib import Path
import time
import torch
import torch.nn as nn
from torch.utils.data import DataLoader

from src.models.bilstm_classifier import FSLTemporalClassifier
from src.training.dataset import prepare_fsl_data


def train_fsl_model(
    data_dir: str = "data/raw_videos",
    output_dir: str = "models",
    epochs: int = 50,
    batch_size: int = 16,
    lr: float = 1e-3,
    weight_decay: float = 1e-2,
):
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[*] Training on device: {device}")

    # 1. Prepare Data
    print(f"[*] Scanning and preprocessing dataset from '{data_dir}'...")
    train_dataset, val_dataset, vocab = prepare_fsl_data(data_dir=data_dir)
    print(f"[*] Found {len(vocab)} classes: {vocab}")
    print(f"[*] Training samples: {len(train_dataset)}, Validation samples: {len(val_dataset)}")

    # Save vocabulary dictionary for mobile inference mapping
    vocab_path = output_path / "vocab.json"
    with open(vocab_path, "w", encoding="utf-8") as f:
        json.dump(vocab, f, indent=2)
    print(f"[*] Saved vocabulary map to {vocab_path}")

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, drop_last=False)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    # 2. Instantiate Model
    model = FSLTemporalClassifier(
        input_dim=159,
        hidden_dim=128,
        num_lstm_layers=2,
        num_classes=len(vocab),
        dropout=0.3,
    ).to(device)

    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    best_val_acc = 0.0
    best_model_path = output_path / "best_model.pt"

    # 3. Training Loop
    print("\n" + "=" * 60)
    print(f"{'Epoch':<8} | {'Train Loss':<12} | {'Val Loss':<12} | {'Val Acc (%)':<12}")
    print("=" * 60)

    for epoch in range(1, epochs + 1):
        model.train()
        total_train_loss = 0.0

        for x_batch, y_batch in train_loader:
            x_batch = x_batch.to(device)
            y_batch = y_batch.to(device)

            optimizer.zero_grad()
            logits = model(x_batch)
            loss = criterion(logits, y_batch)
            loss.backward()

            nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            total_train_loss += loss.item()

        scheduler.step()
        avg_train_loss = total_train_loss / max(1, len(train_loader))

        # Evaluation
        model.eval()
        total_val_loss = 0.0
        correct = 0
        total = 0

        with torch.no_grad():
            for x_batch, y_batch in val_loader:
                x_batch = x_batch.to(device)
                y_batch = y_batch.to(device)

                logits = model(x_batch)
                loss = criterion(logits, y_batch)
                total_val_loss += loss.item()

                preds = logits.argmax(dim=-1)
                correct += (preds == y_batch).sum().item()
                total += y_batch.size(0)

        avg_val_loss = total_val_loss / max(1, len(val_loader))
        val_acc = (correct / total * 100.0) if total > 0 else 0.0

        print(f"{epoch:<8} | {avg_train_loss:<12.4f} | {avg_val_loss:<12.4f} | {val_acc:<12.2f}")

        # Checkpoint best model
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), best_model_path)

    print("=" * 60)
    print(f"[+] Training complete. Best Validation Accuracy: {best_val_acc:.2f}%")
    print(f"[+] Model checkpoint saved to: {best_model_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train FSL Temporal Classifier")
    parser.add_argument("--data_dir", type=str, default="data/raw_videos", help="Path to video dataset directory")
    parser.add_argument("--output_dir", type=str, default="models", help="Directory to save checkpoints")
    parser.add_argument("--epochs", type=int, default=50, help="Number of training epochs")
    parser.add_argument("--batch_size", type=int, default=16, help="Batch size")
    parser.add_argument("--lr", type=float, default=1e-3, help="Learning rate")
    args = parser.parse_args()

    train_fsl_model(
        data_dir=args.data_dir,
        output_dir=args.output_dir,
        epochs=args.epochs,
        batch_size=args.batch_size,
        lr=args.lr,
    )
