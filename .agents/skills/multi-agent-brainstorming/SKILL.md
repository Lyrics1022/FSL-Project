---
name: multi-agent-brainstorming
description: >-
  Use this skill when orchestrating multi-agent debates, stochastic consensus ideation, adversarial design reviews, or stress-testing complex architectural decisions across multiple agent personas.
license: Apache-2.0
---

# Multi-Agent Brainstorming & Stochastic Consensus

This skill establishes a dialectical, multi-perspective ideation and decision framework to eliminate single-model bias, uncover fatal edge cases, and converge on hardened architectural decisions.

---

## 1. Core Brainstorming Topologies

### Pattern A: Stochastic Consensus (Broad Ideation)
Best for open-ended design problems where multiple viable solutions exist.
1. **Deconstruct the Objective:** Formulate the core challenge and constraints into a standardized prompt packet.
2. **Deploy Diverse Personas (Parallel Subagents):**
   - **The Pragmatic Minimalist:** Emphasizes simplicity, lowest maintenance, and fewest dependencies.
   - **The Performance Maximizer:** Focuses on sub-millisecond latency, concurrency, zero-copy, and throughput.
   - **The Security & Edge-Case Realist:** Actively hunts for failure modes, race conditions, adversarial inputs, and data corruption.
   - **The Future-Proof Architect:** Focuses on modularity, clean interfaces, testability, and plugin extensibility.
3. **Synthesis & Convergence:** Synthesize votes, cluster common themes, isolate contrarian insights, and construct the consensus recommendation.

### Pattern B: Multi-Round Dialectical Debate (Deep Scrutiny)
Best for evaluating a high-stakes binary or ternary choice (e.g., *ONNXRuntime on edge vs. Cloud VLM API*).
- **Round 1 (Thesis & Antithesis):** Agent A defends Proposal 1; Agent B defends Proposal 2 with explicit proofs and benchmarks.
- **Round 2 (Rebuttal & Cross-Examination):** Each agent critiques the counter-proposal's vulnerabilities, maintenance liabilities, and failure modes.
- **Round 3 (Judge / Synthesis):** A neutral synthesizer reviews the transcript, evaluates verified claims, and outputs an executive Architectural Decision Record (ADR).

---

## 2. Operational Execution Runbook

### Step 1: Initialize the Debate Frame
```markdown
## Problem Definition
- **Objective:** [Clear 1-2 sentence goal]
- **Non-Negotiable Constraints:** [Latency, cost, memory, dependencies]
- **Competing Options:** [Option A, Option B, Option C]
```

### Step 2: Persona Prompt Template
When spawning subagents or formulating dialectical tracks:
```text
Role: You are [Persona: e.g., Security & Reliability Realist].
Objective: Review the proposed system design.
Instructions:
1. Identify the top 3 single points of failure.
2. Assume the primary dependency fails or times out: what happens?
3. Propose a hardened fallback pattern with minimal code complexity.
```

### Step 3: Synthesis & Scorecard
Generate an evaluation matrix:

| Evaluation Dimension | Option A (Minimalist) | Option B (Performance) | Option C (Modular Extensible) |
| :--- | :--- | :--- | :--- |
| **Complexity Burden** | Low | High | Medium |
| **P99 Latency Target** | ~50ms | ~5ms | ~25ms |
| **Failure Blast Radius** | Contained | High (Complex concurrency) | Isolated per plugin |
| **Maintenance Cost** | Low | High | Low-Medium |

---

## 3. Output Deliverable
Always conclude multi-agent brainstorming with:
1. **Winning Architecture / Strategy.**
2. **Documented Trade-offs & Rejected Alternatives.**
3. **Hardened Invariants (Rules the implementation must never break).**
