---
name: structured-brainstorming
description: >-
  Use this skill when initiating feature ideation, brainstorming design alternatives, evaluating concept feasibility, or preventing premature design convergence.
license: Apache-2.0
---

# Structured Brainstorming & Ideation Runbook

Guarantees rigorous exploration and prevents "first-idea bias" before writing code.

## 4-Phase Ideation Framework

### Phase 1: Problem Grounding
- Summarize the core user objective in 2 sentences.
- Explicitly list non-negotiable technical, performance, and resource constraints.

### Phase 2: Divergent Solution Generation
Propose at least 3 distinct conceptual designs:
1. **Approach A (The Pragmatic Standard):** Familiar tools, minimal complexity, fastest time-to-deliver.
2. **Approach B (The Performance-Engineered):** Optimized for scale, maximum throughput, minimal latency.
3. **Approach C (The Future-Proof / Autonomous):** Cutting-edge abstractions, extensible plugin architecture.

### Phase 3: Stress-Testing & Trade-Off Matrix
Evaluate the options across:
- Implementation Complexity
- Runtime Efficiency & Latency
- Failure Modes & Edge Case Sensitivity
- Maintenance Burden

### Phase 4: Convergence & Decision Plan
- Recommend the optimal hybrid solution.
- Document any unresolved assumptions that require proof-of-concept validation.
