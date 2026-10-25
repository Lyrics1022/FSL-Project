---
name: multi-agent-orchestrator
description: >-
  Use this skill when designing, coordinating, or running multi-agent workflows, supervisor-worker topologies, DAG pipelines, subagent task decomposition, and inter-agent communication.
license: Apache-2.0
---

# Multi-Agent Orchestrator

This skill prescribes architectural topologies, state synchronization patterns, and operational guardrails for orchestrating multi-agent systems and subagent swarms.

---

## 1. Orchestration Topologies

### 1. Supervisor-Worker (Hierarchical Router)
A central Supervisor decomposes high-level user goals into atomic subtasks, dispatches them to specialized worker agents, and aggregates results.
- **Best For:** Complex engineering workflows (e.g., Code Gen $\rightarrow$ Linter Worker $\rightarrow$ Test Runner Worker).
- **Communication:** Star topology; workers report strictly to the Supervisor.

### 2. Linear DAG (Sequential Pipeline)
Each agent takes the verified output of the preceding agent, transforms or enriches it, and passes it forward.
- **Best For:** Data processing and ML pipelines (e.g., Data Ingestion $\rightarrow$ Feature Extraction $\rightarrow$ Training $\rightarrow$ Quantization).

### 3. Blackboard / Shared Memory
Agents asynchronously inspect a shared workspace, blackboard artifact, or message bus, performing work whenever their activation condition is met.
- **Best For:** Collaborative ideation, real-time telemetry monitoring, and multi-file refactoring.

---

## 2. Safety & Reliability Guardrails

Multi-agent systems must prevent infinite recursion, context bloat, and runaway cost through strict limits:

```text
┌────────────────────────────────────────────────────────┐
│               ORCHESTRATION GUARDRAILS                 │
├─────────────────────────┬──────────────────────────────┤
│ Recursion Depth Limit   │ Max 5 nested subagent hops   │
│ Step Budget             │ Max 25 total tool calls/sub  │
│ Timeout Circuit Breaker │ 120s max execution per step  │
│ Context Compaction      │ Pass structured diffs only   │
│ Failure Fallback        │ Escalate to human supervisor │
└─────────────────────────┴──────────────────────────────┘
```

---

## 3. Inter-Agent State Machine Contract

All state passed between agents must adhere to strict JSON schemas:

```json
{
  "taskId": "task-uuid-v4",
  "sourceAgent": "cv-feature-extractor",
  "targetAgent": "ml-training-worker",
  "status": "COMPLETED",
  "payload": {
    "artifactPaths": ["data/features_normalized.parquet"],
    "summary": "Extracted 21 hand landmarks across 1,200 video frames.",
    "metrics": {"validFrameRatio": 0.992}
  },
  "error": null
}
```

---

## 4. Execution Checklist for Orchestrator Agents
1. **Decompose:** Split prompt into discrete, independently verifiable tasks.
2. **Assign:** Select the agent skill with the minimum required permissions.
3. **Isolate:** Provide only the context needed for that subtask (avoid dumping the whole chat history).
4. **Validate:** Enforce schema checks on worker output before passing to downstream agents.
5. **Recover:** If a worker fails twice, pause the pipeline and request clarification.
