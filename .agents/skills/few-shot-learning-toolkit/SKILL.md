---
name: few-shot-learning-toolkit
description: >-
  Use this skill when building few-shot learning systems, prototypical networks, metric-based meta-learning, or low-data classification pipelines.
license: Apache-2.0
compatibility: requires-pytorch
---

# Few-Shot Learning (FSL) Toolkit

Guides the implementation of metric-based meta-learning and Prototypical Networks for low-resource classification tasks (such as sign language gestures with limited sample recordings).

## Prototypical Networks Protocol

### 1. Episodic Sampling
In an $N$-way $K$-shot task:
- Select $N$ distinct classes.
- For each class, sample $K$ support examples (used to compute class prototypes).
- Sample $Q$ query examples (used to compute evaluation loss).

### 2. Computing Class Prototypes
A class prototype $c_k$ is the mean vector of the support set representations for class $k$:

$$c_k = \frac{1}{|S_k|} \sum_{(x_i, y_i) \in S_k} f_\theta(x_i)$$

```python
import torch
import torch.nn as nn
import torch.nn.functional as F

def compute_prototypes(support_embeddings, support_labels, n_way):
    # support_embeddings: (N*K, D)
    # support_labels: (N*K)
    prototypes = []
    for k in range(n_way):
        mask = (support_labels == k)
        proto = support_embeddings[mask].mean(dim=0)
        prototypes.append(proto)
    return torch.stack(prototypes)  # (N, D)
```

### 3. Euclidean Distance Loss
Classify query examples $x_q$ by finding the closest prototype in Euclidean space:

```python
def prototypical_loss(prototypes, query_embeddings, query_labels):
    # prototypes: (N, D)
    # query_embeddings: (M, D)
    dists = torch.cdist(query_embeddings, prototypes)  # (M, N)
    logits = -dists
    loss = F.cross_entropy(logits, query_labels)
    acc = (logits.argmax(dim=-1) == query_labels).float().mean()
    return loss, acc
```

### 4. Evaluation Strategy
Always report mean accuracy over $\ge 500$ randomly sampled episodes with 95% confidence intervals.
