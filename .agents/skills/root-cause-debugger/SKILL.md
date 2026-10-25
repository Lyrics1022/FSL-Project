---
name: root-cause-debugger
description: >-
  Use this skill when debugging crashes, fixing runtime errors, isolating subtle bugs, or conducting root cause analysis (RCA).
license: Apache-2.0
---

# Root Cause Debugger & RCA Protocol

A scientific, hypothesis-driven workflow for diagnosing and fixing software defects.

## 5-Step Diagnostic Protocol

### Step 1: Deterministic Reproduction
- Never attempt to patch code without first creating a minimal reproduction script or failing unit test.
- Isolate the minimal set of inputs and environment variables that trigger the defect.

### Step 2: Boundary Isolation & Stack Inspection
- Trace the call stack to find the exact boundary where expected state diverges from actual state.
- Inspect variable types, dimensions, and null/None states right before the failure occurs.

### Step 3: Hypothesis Formulation
- State explicitly: *"The crash occurs because [X assumption] fails when [Y condition happens]."*
- Test the hypothesis with a single targeted assertion or print/logging check.

### Step 4: Surgical Minimal Patch
- Fix the root cause rather than merely masking the symptom (e.g., do not add a blind `try/except: pass` or default fallback without resolving the invariant violation).
- Preserve existing comments and docstrings.

### Step 5: Regression Verification
- Run the full test suite (`pytest`) to prove that:
  1. The minimal reproduction test now passes.
  2. No existing tests were broken by the change.
