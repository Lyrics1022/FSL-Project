---
name: arxiv-literature-reviewer
description: >-
  Use this skill when researching scientific papers, querying arXiv or Semantic Scholar, synthesizing state-of-the-art methodologies, or analyzing academic algorithms.
license: Apache-2.0
compatibility: requires-internet-access
---

# Academic Literature Reviewer

Guides systematic research, paper extraction, and SOTA synthesis across academic repositories.

## Review Protocol

### 1. Query Formulation
- Identify core keywords and MeSH/arXiv categories (e.g., `cs.CV`, `cs.LG`, `cs.AI`).
- Combine concept terms: `("few-shot" OR "prototypical") AND ("sign language" OR "gesture recognition")`.

### 2. Paper Inspection & Data Extraction
For each selected paper, extract:
- **Title, Authors, Year, Publication Venue.**
- **Core Contribution:** What is fundamentally novel? (New loss function, temporal aggregation, dataset).
- **Architecture Diagram:** Backbone (CNN vs. ViT vs. GCN) and feature dimensionality.
- **Benchmark Performance:** Accuracy, F1, Latency on standard datasets.
- **Limitations:** Stated assumptions, failure modes, computational overhead.

### 3. Synthesis Matrix
Generate a comparative Markdown table:
| Paper / Model | Modality | Backbone | Few-Shot Setting | Key Innovation | Accuracy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ... | ... | ... | ... | ... | ... |
