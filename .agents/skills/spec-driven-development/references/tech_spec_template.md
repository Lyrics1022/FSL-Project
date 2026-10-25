# Technical Specification & Engineering Contract

## 1. Technical Context & High-Level Architecture
- **Purpose:** Engineering blueprint translating PRD/SRS requirements into code contracts.
- **Architectural Style:** Modular Pipeline / Event-Driven.

```mermaid
graph LR
    Input[Stream Source] --> Ingest[Capture Worker]
    Ingest --> Preprocess[OpenCV / MediaPipe Pipeline]
    Preprocess --> Model[Few-Shot Inference Engine]
    Model --> HUD[Annotation Renderer]
    Model --> State[Result State / Event Bus]
```

---

## 2. Data Models & Schemas

### 2.1 Core Domain Entities (Pydantic / TypeScript)
```python
from pydantic import BaseModel, Field
from typing import List, Optional

class LandmarkPoint(BaseModel):
    x: float = Field(..., ge=-5.0, le=5.0)
    y: float = Field(..., ge=-5.0, le=5.0)
    z: float = Field(..., ge=-5.0, le=5.0)

class HandPose(BaseModel):
    handedness: str = Field(..., pattern="^(Left|Right)$")
    confidence: float = Field(..., ge=0.0, le=1.0)
    landmarks: List[LandmarkPoint] = Field(..., min_items=21, max_items=21)

class InferenceResult(BaseModel):
    predicted_class: str
    confidence: float
    latency_ms: float
    timestamp: float
```

---

## 3. Interface Contracts & API Signatures

### 3.1 Python Internal Module Contract
```python
class IFewShotClassifier:
    def fit_prototypes(self, support_tensors: torch.Tensor, support_labels: torch.Tensor) -> None:
        """Computes and stores class prototypes in memory."""
        ...

    def predict(self, query_tensor: torch.Tensor) -> InferenceResult:
        """Returns top predicted class and confidence."""
        ...
```

---

## 4. Behavior-Driven Development (BDD) Acceptance Criteria
*Written in Gherkin syntax for automated validation.*

```gherkin
Feature: Real-Time Sign Classification

  Scenario: Video feed is captured and landmarks are extracted
    Given the camera device is connected and initialized
    When a user raises their hand in front of the lens
    Then the system extracts 21 hand landmarks within 15ms
    And all coordinates are normalized relative to the wrist origin

  Scenario: Unknown or ambiguous gesture presented
    Given the classifier is loaded with trained prototypes
    When a gesture with maximum cosine similarity < 0.60 is observed
    Then the system returns class "Unknown"
    And confidence score is flagged with low-confidence warning
```

---

## 5. Work Breakdown Structure (WBS / Task DAG)
1. **Task 1 (Foundational):** Implement video stream wrapper with threaded queue. *(Dependencies: None)*
2. **Task 2 (Feature Extraction):** Integrate MediaPipe hand landmarks and spatial normalization. *(Dependencies: Task 1)*
3. **Task 3 (Inference Engine):** Build Prototypical Network inference module. *(Dependencies: Task 2)*
4. **Task 4 (E2E Integration):** Assemble live HUD rendering and test suite. *(Dependencies: Task 1, 2, 3)*
