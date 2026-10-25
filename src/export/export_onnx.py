"""
Model Export Script: PyTorch (.pt) -> ONNX / TFLite for Mobile Deployment.
Validates numerical parity between PyTorch and ONNX Runtime.
"""

import argparse
import json
from pathlib import Path
import numpy as np
import onnx
import onnxruntime as ort
import torch

from src.models.bilstm_classifier import FSLTemporalClassifier


def export_model_to_onnx(
    checkpoint_path: str = "models/best_model.pt",
    vocab_path: str = "models/vocab.json",
    output_onnx: str = "models/fsl_model.onnx",
):
    ckpt_file = Path(checkpoint_path)
    vocab_file = Path(vocab_path)
    out_file = Path(output_onnx)

    if not ckpt_file.exists():
        raise FileNotFoundError(f"Checkpoint not found: {ckpt_file}")
    if not vocab_file.exists():
        raise FileNotFoundError(f"Vocabulary file not found: {vocab_file}")

    with open(vocab_file, "r", encoding="utf-8") as f:
        vocab = json.load(f)

    num_classes = len(vocab)
    print(f"[*] Loading PyTorch model with {num_classes} classes from {ckpt_file}...")

    model = FSLTemporalClassifier(
        input_dim=159,
        hidden_dim=128,
        num_lstm_layers=2,
        num_classes=num_classes,
    )
    model.load_state_dict(torch.load(ckpt_file, map_location="cpu"))
    model.eval()

    # Create dummy input of shape (Batch=1, Seq_Len=30, Feat_Dim=159)
    dummy_input = torch.randn(1, 30, 159, dtype=torch.float32)

    print(f"[*] Exporting to ONNX: {out_file}...")
    torch.onnx.export(
        model,
        dummy_input,
        str(out_file),
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=["landmarks_sequence"],
        output_names=["class_logits"],
        dynamic_axes={
            "landmarks_sequence": {0: "batch_size"},
            "class_logits": {0: "batch_size"},
        },
    )

    # 1. Verify ONNX Graph
    onnx_model = onnx.load(str(out_file))
    onnx.checker.check_model(onnx_model)
    print("[+] ONNX model structure verified successfully.")

    # 2. Check numerical parity between PyTorch and ONNX Runtime
    with torch.no_grad():
        torch_out = model(dummy_input).numpy()

    ort_session = ort.InferenceSession(str(out_file), providers=["CPUExecutionProvider"])
    ort_inputs = {"landmarks_sequence": dummy_input.numpy()}
    ort_out = ort_session.run(None, ort_inputs)[0]

    np.testing.assert_allclose(torch_out, ort_out, rtol=1e-3, atol=1e-4)
    print("[+] Numerical parity verified: PyTorch and ONNX Runtime outputs match within 1e-4 tolerance!")
    print(f"[+] Export complete: {out_file} ({out_file.stat().st_size / 1024 / 1024:.2f} MB)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export PyTorch model to ONNX")
    parser.add_argument("--checkpoint", type=str, default="models/best_model.pt")
    parser.add_argument("--vocab", type=str, default="models/vocab.json")
    parser.add_argument("--output", type=str, default="models/fsl_model.onnx")
    args = parser.parse_args()

    export_model_to_onnx(
        checkpoint_path=args.checkpoint,
        vocab_path=args.vocab,
        output_onnx=args.output,
    )
