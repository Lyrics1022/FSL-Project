"""
Prototypical Network (ProtoNet) Module for Few-Shot Filipino Sign Language Recognition.
Allows on-device calibration and rapid learning of new vocabulary signs from <= 5 sample videos.
"""

from typing import Tuple
import torch
import torch.nn as nn
import torch.nn.functional as F


class FSLFeatureEmbeddingNet(nn.Module):
    """Encodes a (B, T=30, D=159) sequence into a compact D=128 embedding vector."""

    def __init__(self, input_dim: int = 159, embedding_dim: int = 128):
        super().__init__()
        self.conv1 = nn.Conv1d(input_dim, 64, kernel_size=3, padding=1)
        self.bn1 = nn.BatchNorm1d(64)
        self.conv2 = nn.Conv1d(64, 128, kernel_size=3, padding=1)
        self.bn2 = nn.BatchNorm1d(128)
        self.gru = nn.GRU(128, embedding_dim, num_layers=2, batch_first=True, bidirectional=True)
        self.fc = nn.Linear(embedding_dim * 2, embedding_dim)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (B, T, D) -> (B, D, T)
        h = F.relu(self.bn1(self.conv1(x.transpose(1, 2))))
        h = F.relu(self.bn2(self.conv2(h)))
        # (B, D, T) -> (B, T, D)
        gru_out, _ = self.gru(h.transpose(1, 2))
        pooled = torch.mean(gru_out, dim=1)  # Mean pooling across time
        embedding = self.fc(pooled)
        return F.normalize(embedding, p=2, dim=1)  # L2 Normalized embedding


class ProtoNetClassifier:
    """Manages class prototypes in embedding space and calculates Euclidean distances."""

    @staticmethod
    def compute_prototypes(
        support_embeddings: torch.Tensor,
        support_labels: torch.Tensor,
        n_way: int,
    ) -> torch.Tensor:
        """
        Computes centroid prototype c_k for each class k in the support set.
        
        Args:
            support_embeddings: (N * K, D)
            support_labels: (N * K) with values in range [0, n_way - 1]
            n_way: number of unique classes N
            
        Returns:
            prototypes: (N, D)
        """
        prototypes = []
        for k in range(n_way):
            mask = (support_labels == k)
            class_embeds = support_embeddings[mask]
            proto = class_embeds.mean(dim=0)
            prototypes.append(proto)
        return torch.stack(prototypes)

    @staticmethod
    def classify_queries(
        prototypes: torch.Tensor,
        query_embeddings: torch.Tensor,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        """
        Computes negative Euclidean distance logits for query embeddings.
        
        Returns:
            logits: (M, N)
            predicted_classes: (M,)
        """
        # Distance: (M, N)
        dists = torch.cdist(query_embeddings, prototypes)
        logits = -dists
        predicted = torch.argmax(logits, dim=-1)
        return logits, predicted
