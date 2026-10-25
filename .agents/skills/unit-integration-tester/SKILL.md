---
name: unit-integration-tester
description: >-
  Use this skill when creating, running, and debugging automated unit and integration tests using pytest, jest, or vitest, and evaluating code coverage.
license: Apache-2.0
---

# Unit & Integration Testing Runbook

Enforces reliable, fast, and comprehensive automated test suites.

## Testing Standards

### 1. Test Architecture
- Tests live in `tests/unit/` and `tests/integration/`.
- Use explicit, descriptive test names: `test_<function>_<scenario>_<expected_result>()`.
- Keep assertions clean and use `pytest.raises` for exception validation.

### 2. Parameterization & Fixtures
```python
import pytest

@pytest.mark.parametrize("input_val,expected", [
    (10, 20),
    (0, 0),
    (-5, -10),
])
def test_double_values(input_val, expected):
    assert double(input_val) == expected
```

### 3. Mocking I/O and External Dependencies
- Never hit external APIs, cameras, or real databases in unit tests.
- Use `unittest.mock` or `pytest-mock` (`mocker.patch`).

### 4. Execution & Coverage Command
```bash
pytest --cov=src --cov-report=term-missing --cov-fail-under=85
```
