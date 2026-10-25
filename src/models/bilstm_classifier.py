"""
1D-CNN + Bidirectional LSTM Temporal Neural Network for Real-Time FSL Classification.
Optimized for low-latency mobile inference and high sign recognition accuracy.
"""

import torch
import torch.nn as nn
import torch.nn.functional as F


class FSLTemporalClassifier(nn.Module):
    """
    Lightweight temporal neural network mapping (B, T=30, D=159) coordinate sequences
    to discrete FSL vocabulary classes.
    """

    def __init__(
        self,
        input_dim: int = 159,
        hidden_dim: int = 128,
        num_lstm_layers: int = 2,
        num_classes: int = 50,
        dropout: float = 0.3,
    ):
        super().__init__()
        self.input_dim = input_dim
        self.hidden_dim = hidden_dim
        self.num_classes = num_classes

        # 1. 1D Convolutional feature extractor (local joint dynamics & velocity)
        # Input shape to Conv1d: (B, Channels=input_dim, Length=T)
        self.conv1 = nn.Conv1d(
            in_channels=input_dim,
            out_channels=hidden_dim,
            kernel_size=3,
            padding=1,
        )
        self.bn1 = nn.BatchNorm1d(hidden_dim)
        self.relu = nn.ReLU()

        # 2. Bidirectional LSTM
        self.lstm = nn.LSTM(
            input_size=hidden_dim,
            hidden_size=hidden_dim,
            num_layers=num_lstm_layers,
            batch_first=True,
            bidirectional=True,
            dropout=dropout if num_lstm_layers > 1 else 0.0,
        )

        # 3. Temporal Attention Pooling
        self.attention_weights = nn.Linear(hidden_dim * 2, 1)

        # 4. Dense Classification Head
        self.dropout = nn.Dropout(dropout)
        self.fc1 = nn.Linear(hidden_dim * 2, hidden_dim)
        self.fc2 = nn.Linear(hidden_dim, num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: Tensor of shape (Batch, Seq_Len=30, Feat_Dim=159)
            
        Returns:
            logits: Tensor of shape (Batch, num_classes)
        """
        # Shape: (B, T, D) -> (B, D, T) for Conv1d
        x_conv = x.transpose(1, 2)
        x_conv = self.relu(self.bn1(self.conv1(x_conv)))

        # Shape: (B, D, T) -> (B, T, hidden_dim) for LSTM
        x_lstm_in = x_conv.transpose(1, 2)
        lstm_out, _ = self.lstm(x_lstm_in)  # Shape: (B, T, hidden_dim * 2)

        # Attention pooling over time
        attn_scores = self.attention_weights(lstm_out)  # Shape: (B, T, 1)
        attn_weights = F.softmax(attn_scores, dim=1)    # Shape: (B, T, 1)
        pooled = torch.sum(lstm_out * attn_weights, dim=1)  # Shape: (B, hidden_dim * 2)

        # Classification Head
        out = self.dropout(pooled)
        out = self.relu(self.fc1(out))
        logits = self.fc2(out)  # Shape: (B, num_classes)
        return logits
