---
name: ml-training-orchestrator
description: >-
  Use this skill when implementing deep learning training pipelines, writing PyTorch training loops, setting up optimizers, learning rate schedules, and mixed precision.
license: Apache-2.0
compatibility: requires-pytorch, requires-gpu-optional
---

# Deep Learning Model Training Orchestrator

Prescribes best practices for writing robust, performant PyTorch training loops.

## Training Protocol

### 1. Determinism & Seeding
```python
import torch, random, numpy as np

def seed_everything(seed=42):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.deterministic = True
```

### 2. Optimizer & Scheduler Configuration
- Default optimizer: `torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-2)`.
- Use `CosineAnnealingLR` with linear warmup.

### 3. Mixed Precision & Gradient Scaling
Always wrap the training loop in `torch.cuda.amp.autocast()` and use `GradScaler` when CUDA is available:
```python
scaler = torch.cuda.amp.GradScaler(enabled=torch.cuda.is_available())

for data, target in train_loader:
    optimizer.zero_grad()
    with torch.cuda.amp.autocast(enabled=torch.cuda.is_available()):
        output = model(data)
        loss = criterion(output, target)
    scaler.scale(loss).backward()
    scaler.unscale_(optimizer)
    torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
    scaler.step(optimizer)
    scaler.update()
```

### 4. Checkpointing & Early Stopping
- Save checkpoints based on minimum validation loss or maximum validation metric.
- Save `model_state_dict`, `optimizer_state_dict`, epoch, and metric history.
