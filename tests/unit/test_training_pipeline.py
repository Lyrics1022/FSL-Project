# pyright: reportMissingImports=false, reportMissingModuleSource=false
"""
Unit tests verifying the end-to-end data preparation, training, and export pipeline.
"""

from pathlib import Path
import json
import sys
import numpy as np
import pytest
import torch

# Ensure project root is available on sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from src.training.dataset import FSLDataset  # type: ignore # noqa: E402
from src.models.bilstm_classifier import FSLTemporalClassifier  # type: ignore # noqa: E402
from src.export.export_onnx import export_model_to_onnx  # type: ignore # noqa: E402


def test_fsl_dataset_batching():
    # 20 samples, 30 frames, 159 features
    features = [np.random.rand(30, 159).astype(np.float32) for _ in range(20)]
    labels = [i % 5 for i in range(20)]  # 5 classes

    dataset = FSLDataset(features, labels, augment=True)
    assert len(dataset) == 20

    x, y = dataset[0]
    assert x.shape == (30, 159)
    assert isinstance(y.item(), int)


def test_training_one_step():
    model = FSLTemporalClassifier(input_dim=159, hidden_dim=32, num_lstm_layers=1, num_classes=5)
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    criterion = torch.nn.CrossEntropyLoss()

    x = torch.randn(4, 30, 159)
    y = torch.tensor([0, 1, 2, 3])

    optimizer.zero_grad()
    out = model(x)
    loss = criterion(out, y)
    loss.backward()
    optimizer.step()

    assert not torch.isnan(loss)
    assert loss.item() > 0


def test_export_onnx_pipeline(tmp_path):
    model = FSLTemporalClassifier(input_dim=159, hidden_dim=128, num_lstm_layers=2, num_classes=3)
    ckpt_path = tmp_path / "test_model.pt"
    vocab_path = tmp_path / "vocab.json"
    onnx_path = tmp_path / "test_model.onnx"

    torch.save(model.state_dict(), ckpt_path)
    with open(vocab_path, "w") as f:
        json.dump(["HELLO", "YES", "NO"], f)

    export_model_to_onnx(
        checkpoint_path=str(ckpt_path),
        vocab_path=str(vocab_path),
        output_onnx=str(onnx_path),
    )

    assert onnx_path.exists()
    assert onnx_path.stat().st_size > 0
