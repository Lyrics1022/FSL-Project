"""
Evaluation report for the FSL ONNX model.
Loads models/fsl_model.onnx via ONNX Runtime, runs all clips in FSL_Features
(sorted file order), and reports accuracy, confusion analysis, weak signs,
and a confidence-cutoff table. Implements the app's dual-hypothesis "mirror"
check (swap left/right hand blocks, negate every x, pick more confident run).

Usage:
    python scripts/eval_report.py                     # evaluate on FSL_Features
    python scripts/eval_report.py --holdout path/to/holdout_folder
    python scripts/eval_report.py --model models/fsl_model_v2.onnx
"""

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import onnxruntime as ort


# ── constants matching FslClassifier.kt ──────────────────────────────────────
SEQ_LEN = 30
FEAT_DIM = 159
TOTAL_INPUT_SIZE = SEQ_LEN * FEAT_DIM


def load_vocab(path: str = "models/vocab.json") -> list[str]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_dataset(feat_dir: str, vocab: list[str]):
    """Load clips in sorted file order, return (samples, labels, sign_names)."""
    feat_path = Path(feat_dir)
    samples, labels = [], []
    for idx, cls_name in enumerate(vocab):
        cls_folder = feat_path / cls_name
        if not cls_folder.exists():
            # Try matching with typographic apostrophe variants
            matches = [
                d
                for d in feat_path.iterdir()
                if d.is_dir()
                and d.name.replace("\u2018", "'").replace("\u2019", "'")
                == cls_name.replace("\u2018", "'").replace("\u2019", "'")
            ]
            if matches:
                cls_folder = matches[0]
            else:
                continue
        npy_files = sorted(cls_folder.glob("*.npy"))
        for f in npy_files:
            data = np.load(f)
            if data.shape == (SEQ_LEN, FEAT_DIM):
                samples.append(data.astype(np.float32))
                labels.append(idx)
    return samples, labels


def create_mirrored(clip: np.ndarray) -> np.ndarray:
    """Mirror a (30, 159) clip: swap left/right hand, negate every x."""
    mirrored = np.zeros_like(clip)
    for t in range(SEQ_LEN):
        # Swap hands and negate x
        for j in range(21):
            lh = j * 3
            rh = 63 + j * 3
            # RH -> new LH (negate x)
            mirrored[t, lh] = -clip[t, rh]
            mirrored[t, lh + 1] = clip[t, rh + 1]
            mirrored[t, lh + 2] = clip[t, rh + 2]
            # LH -> new RH (negate x)
            mirrored[t, rh] = -clip[t, lh]
            mirrored[t, rh + 1] = clip[t, lh + 1]
            mirrored[t, rh + 2] = clip[t, lh + 2]
        # Pose: negate x
        for k in range(11):
            pi = 126 + k * 3
            mirrored[t, pi] = -clip[t, pi]
            mirrored[t, pi + 1] = clip[t, pi + 1]
            mirrored[t, pi + 2] = clip[t, pi + 2]
    return mirrored


def softmax(logits: np.ndarray) -> np.ndarray:
    e = np.exp(logits - logits.max(axis=-1, keepdims=True))
    return e / e.sum(axis=-1, keepdims=True)


def evaluate(
    session: ort.InferenceSession,
    samples: list[np.ndarray],
    labels: list[int],
    vocab: list[str],
):
    """Run all clips with dual-hypothesis mirror inference, return detailed results."""
    preds = []
    confs = []
    true_labels = labels

    for clip in samples:
        inp = clip.reshape(1, SEQ_LEN, FEAT_DIM)

        # Hypothesis A: standard
        logits_a = session.run(None, {"landmarks_sequence": inp})[0][0]
        probs_a = softmax(logits_a)
        pred_a = int(np.argmax(probs_a))
        conf_a = float(probs_a[pred_a])

        # Hypothesis B: mirrored
        mirrored = create_mirrored(clip).reshape(1, SEQ_LEN, FEAT_DIM)
        logits_b = session.run(None, {"landmarks_sequence": mirrored})[0][0]
        probs_b = softmax(logits_b)
        pred_b = int(np.argmax(probs_b))
        conf_b = float(probs_b[pred_b])

        # Pick the more confident
        if conf_b > conf_a:
            preds.append(pred_b)
            confs.append(int(conf_b * 100))
        else:
            preds.append(pred_a)
            confs.append(int(conf_a * 100))

    return np.array(true_labels), np.array(preds), np.array(confs)


def short_name(vocab_name: str) -> str:
    return vocab_name.removeprefix("FSL_Dataset_").replace("_", " ")


def print_report(
    true: np.ndarray, pred: np.ndarray, conf: np.ndarray, vocab: list[str], title: str
):
    n = len(true)
    correct = (true == pred).sum()
    acc = correct / n * 100
    print(f"\n{'='*78}")
    print(f"  {title}")
    print(f"{'='*78}")
    print(f"  Total clips: {n}")
    print(f"  Accuracy:    {correct}/{n} = {acc:.2f}%")

    # ── Top-3 Accuracy ───────────────────────────────────────────────────────
    # (not needed for Step 0 baseline, but included for completeness)

    # ── 15 Most Confused Pairs ───────────────────────────────────────────────
    confusion_pairs = Counter()
    for t, p in zip(true, pred):
        if t != p:
            confusion_pairs[(short_name(vocab[t]), short_name(vocab[p]))] += 1

    print(f"\n  15 Most Confused Pairs (true -> predicted):")
    print(f"  {'True':<25} {'Predicted':<25} {'Count':>5}")
    print(f"  {'-'*57}")
    for (t_name, p_name), cnt in confusion_pairs.most_common(15):
        print(f"  {t_name:<25} {p_name:<25} {cnt:>5}")

    # ── 10 Weakest Signs ─────────────────────────────────────────────────────
    per_sign_correct = defaultdict(int)
    per_sign_total = defaultdict(int)
    for t, p in zip(true, pred):
        per_sign_total[t] += 1
        if t == p:
            per_sign_correct[t] += 1

    sign_acc = []
    for idx in per_sign_total:
        sa = per_sign_correct[idx] / per_sign_total[idx] * 100
        sign_acc.append((idx, sa, per_sign_total[idx], per_sign_correct[idx]))
    sign_acc.sort(key=lambda x: x[1])

    print(f"\n  10 Weakest Signs:")
    print(f"  {'Sign':<30} {'Acc%':>6} {'Correct':>8} {'Total':>6}")
    print(f"  {'-'*52}")
    for idx, sa, tot, cor in sign_acc[:10]:
        print(f"  {short_name(vocab[idx]):<30} {sa:>5.1f}% {cor:>7}/{tot:<5}")

    # ── Confidence Cutoff Table ──────────────────────────────────────────────
    cutoffs = [0, 30, 40, 50, 60, 70, 80, 90]
    print(f"\n  Confidence Cutoff Table:")
    print(
        f"  {'Cutoff':>7} {'Shown%':>7} {'Shown Acc%':>11} {'Wrong Shown':>12} {'Wrong Shown%':>13}"
    )
    print(f"  {'-'*52}")

    for cutoff in cutoffs:
        mask = conf >= cutoff
        n_shown = mask.sum()
        pct_shown = n_shown / n * 100
        if n_shown > 0:
            correct_shown = ((true == pred) & mask).sum()
            wrong_shown = (mask & (true != pred)).sum()
            acc_shown = correct_shown / n_shown * 100
            wrong_shown_pct = wrong_shown / n_shown * 100
        else:
            acc_shown = 0.0
            wrong_shown = 0
            wrong_shown_pct = 0.0
        print(
            f"  {cutoff:>6}% {pct_shown:>6.1f}% {acc_shown:>10.1f}% {wrong_shown:>11} {wrong_shown_pct:>12.1f}%"
        )

    print(f"{'='*78}\n")


def main():
    parser = argparse.ArgumentParser(description="FSL ONNX Model Evaluation Report")
    parser.add_argument(
        "--model",
        default="models/fsl_model.onnx",
        help="Path to ONNX model (default: models/fsl_model.onnx)",
    )
    parser.add_argument(
        "--features",
        default="FSL_Features",
        help="Path to feature directory (default: FSL_Features)",
    )
    parser.add_argument(
        "--holdout",
        default=None,
        help="Path to holdout folder with same layout as FSL_Features",
    )
    parser.add_argument(
        "--vocab",
        default="models/vocab.json",
        help="Path to vocab.json (default: models/vocab.json)",
    )
    args = parser.parse_args()

    vocab = load_vocab(args.vocab)
    print(f"[*] Loaded vocabulary: {len(vocab)} classes")

    session = ort.InferenceSession(args.model, providers=["CPUExecutionProvider"])
    print(f"[*] Loaded model: {args.model}")
    for inp in session.get_inputs():
        print(f"    Input: {inp.name} shape={inp.shape} dtype={inp.type}")

    # Main dataset evaluation
    samples, labels = load_dataset(args.features, vocab)
    print(f"[*] Loaded {len(samples)} clips from {args.features}")
    if not samples:
        print("[!] No clips found. Exiting.")
        sys.exit(1)

    true, pred, conf = evaluate(session, samples, labels, vocab)
    print_report(true, pred, conf, vocab, f"Baseline Report — {args.model}")

    # Holdout evaluation
    if args.holdout:
        h_samples, h_labels = load_dataset(args.holdout, vocab)
        print(f"[*] Loaded {len(h_samples)} holdout clips from {args.holdout}")
        if h_samples:
            h_true, h_pred, h_conf = evaluate(session, h_samples, h_labels, vocab)
            print_report(
                h_true,
                h_pred,
                h_conf,
                vocab,
                f"Holdout Report — {args.holdout}",
            )
        else:
            print("[!] No holdout clips found.")


if __name__ == "__main__":
    main()
