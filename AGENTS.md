# Project Agents & Customization Guide

Welcome to the **FSL Project** workspace. This repository is configured with specialized agent skills designed to assist across every stage of development—from architectural design and computer vision to machine learning, testing, research, and multi-agent coordination.

## Installed Workspace Skills (`.agents/skills/`)

The following skills are available on-demand via **progressive disclosure**:

| Skill Name | Purpose |
| :--- | :--- |
| **`skill-finder`** | Discover, assess, and install new skills from open registries (SkillDB, agentskills.io, GitHub). |
| **`system-architect`** | Design modular system architectures, C4 diagrams, and API interface contracts. |
| **`spec-driven-development`** | Author formal PRD, IEEE 830 SRS, technical specs, and Gherkin acceptance criteria. |
| **`structured-brainstorming`** | Pre-implementation ideation and trade-off evaluation to prevent first-idea bias. |
| **`multi-agent-brainstorming`** | Multi-agent debate, stochastic consensus, and adversarial stress-testing across diverse personas. |
| **`multi-agent-orchestrator`** | Hierarchical supervisor-worker coordination, DAG task decomposition, and inter-agent safety guardrails. |
| **`opencv-image-pipeline`** | Core OpenCV image preprocessing, color spaces, CLAHE, and contour detection. |
| **`video-stream-processor`** | Thread-safe webcam and RTSP video stream capture without OS buffer lag. |
| **`landmark-pose-extractor`** | MediaPipe hand, body pose, and face mesh keypoint extraction with OneEuro temporal filtering. |
| **`ml-training-orchestrator`** | PyTorch deep learning training loops with mixed precision (`torch.amp`), schedulers, and checkpointing. |
| **`few-shot-learning-toolkit`** | Episodic data samplers, Prototypical Networks, and metric learning for low-data classification. |
| **`arxiv-literature-reviewer`** | Search academic papers on arXiv/Semantic Scholar, extract mathematical formulations and SOTA baselines. |
| **`code-quality-linter`** | Run static analysis, formatting, and linting (`ruff`, `flake8`, `black`, `biome`). |
| **`unit-integration-tester`** | Author, execute, and debug automated unit and integration tests with `pytest`. |
| **`e2e-browser-tester`** | End-to-end browser automation and user journey testing with Playwright. |
| **`root-cause-debugger`** | Hypothesis-driven root-cause analysis (MRE reproduction, stack isolation, verification). |

## Skill Discovery Rules

- Each skill is defined in `.agents/skills/<skill-name>/SKILL.md` with standard YAML frontmatter.
- Skills are only loaded into active context when triggered by relevant tasks, saving tokens while preserving high operational fidelity.
