---
name: code-quality-linter
description: >-
  Use this skill when running linters, formatting codebases, fixing style violations, or enforcing static code quality rules.
license: Apache-2.0
---

# Code Quality & Linter Runbook

Prescribes static analysis standards, formatting enforcement, and automated fixing.

## Python Toolchain: Ruff & Black

### 1. Fast Linting with Ruff
Execute check across codebase:
```bash
ruff check .
```

To automatically apply safe fixes:
```bash
ruff check --fix .
```

### 2. Formatting Enforcement
```bash
ruff format --check .
```

To format in-place:
```bash
ruff format .
```

### 3. Baseline `pyproject.toml` Configuration
Ensure project configuration specifies:
```toml
[tool.ruff]
line-length = 100
target-version = "py310"

[tool.ruff.lint]
select = ["E", "F", "I", "W", "UP", "B"]
ignore = ["E501"]  # line length handled by formatter
```
