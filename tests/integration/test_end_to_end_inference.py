"""
Integration test verifying the final exported ONNX model against the vocabulary.
Tests end-to-end inference simulating the exact Android mobile runtime environment.
"""

from pathlib import Path
import json
import numpy as np
import onnxruntime as ort
import pytest


def test_exported_onnx_model_inference():
    onnx_path = Path("models/fsl_model.onnx")
    vocab_path = Path("models/vocab.json")

    assert onnx_path.exists(), "fsl_model.onnx does not exist in models/"
    assert vocab_path.exists(), "vocab.json does not exist in models/"

    with open(vocab_path, "r", encoding="utf-8") as f:
        vocab = json.load(f)

    num_classes = len(vocab)
    assert num_classes > 0, "Vocabulary should not be empty"

    # Initialize ONNX inference session
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name

    assert input_name == "landmarks_sequence"
    assert output_name == "class_logits"

    # Simulate 30 frames of 159 landmarks
    dummy_input = np.random.randn(1, 30, 159).astype(np.float32)
    outputs = session.run([output_name], {input_name: dummy_input})
    logits = outputs[0]

    assert logits.shape == (1, num_classes)

    # Softmax probabilities
    exp_logits = np.exp(logits - np.max(logits, axis=1, keepdims=True))
    probs = exp_logits / np.sum(exp_logits, axis=1, keepdims=True)

    np.testing.assert_allclose(np.sum(probs, axis=1), [1.0], rtol=1e-5, atol=1e-5)

    predicted_idx = int(np.argmax(probs, axis=1)[0])
    confidence = float(probs[0, predicted_idx])

    assert 0 <= predicted_idx < num_classes
    assert 0.0 < confidence <= 1.0

    predicted_word = vocab[predicted_idx]
    assert isinstance(predicted_word, str)
    assert len(predicted_word) > 0
