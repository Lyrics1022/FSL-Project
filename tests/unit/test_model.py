"""
Unit tests for FSL neural network models.
"""

import pytest
import torch

from src.models.bilstm_classifier import FSLTemporalClassifier
from src.models.protonet import FSLFeatureEmbeddingNet, ProtoNetClassifier


def test_bilstm_classifier_forward_shape():
    batch_size = 4
    seq_len = 30
    feat_dim = 159
    num_classes = 25

    model = FSLTemporalClassifier(
        input_dim=feat_dim,
        hidden_dim=64,
        num_lstm_layers=1,
        num_classes=num_classes,
    )
    model.eval()

    dummy_input = torch.randn(batch_size, seq_len, feat_dim)
    with torch.no_grad():
        logits = model(dummy_input)

    assert logits.shape == (batch_size, num_classes)
    assert not torch.isnan(logits).any()


def test_protonet_few_shot_computation():
    embedding_dim = 64
    n_way = 5      # 5 classes
    k_shot = 3     # 3 samples per class -> 15 support samples
    q_queries = 10 # 10 query samples

    # 1. Test embedding net
    embed_net = FSLFeatureEmbeddingNet(input_dim=159, embedding_dim=embedding_dim)
    dummy_seq = torch.randn(15, 30, 159)
    support_embeddings = embed_net(dummy_seq)
    assert support_embeddings.shape == (15, embedding_dim)

    # 2. Test prototypes computation
    support_labels = torch.tensor([0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4])
    prototypes = ProtoNetClassifier.compute_prototypes(support_embeddings, support_labels, n_way=n_way)
    assert prototypes.shape == (n_way, embedding_dim)

    # 3. Test query classification
    dummy_queries = torch.randn(q_queries, 30, 159)
    query_embeddings = embed_net(dummy_queries)
    logits, predictions = ProtoNetClassifier.classify_queries(prototypes, query_embeddings)

    assert logits.shape == (q_queries, n_way)
    assert predictions.shape == (q_queries,)
    assert (predictions >= 0).all() and (predictions < n_way).all()
