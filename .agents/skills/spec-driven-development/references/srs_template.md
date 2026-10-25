# Software Requirements Specification (SRS)
*Conforming to IEEE Std 830 / ISO/IEC/IEEE 29148*

## 1. Introduction
### 1.1 Purpose
State the purpose of this SRS document and its intended audience.
### 1.2 Document Conventions
Define typography, priority tags (P0, P1, P2), and requirement numbering schemes (e.g., `REQ-SYS-001`).
### 1.3 Intended Audience & Reading Suggestions
Outline project stakeholders (developers, testers, researchers).
### 1.4 Project Scope
Define the software boundaries, capabilities, and system interfaces.

---

## 2. Overall Description
### 2.1 Product Perspective
Is this system a standalone application, part of a distributed pipeline, or a subsystem?
### 2.2 Product Features Summary
High-level summary of system capabilities.
### 2.3 User Classes & Characteristics
Operator personas, accessibility requirements, and operator expertise.
### 2.4 Operating Environment
Target OS (Windows, Linux), minimum hardware requirements (CPU, GPU, RAM, Webcam), and runtime versions (Python 3.10+, PyTorch, OpenCV).
### 2.5 Design & Implementation Constraints
Library restrictions, licensing requirements, and memory bounds.
### 2.6 Assumptions & Dependencies
External hardware dependencies (webcam, lighting conditions), third-party dependencies (MediaPipe, PyTorch).

---

## 3. External Interface Requirements
### 3.1 User Interfaces (UI / UX)
Console HUD, web interface, or desktop display specs.
### 3.2 Hardware Interfaces
Video capture device protocols (USB video class, resolution, FPS).
### 3.3 Software Interfaces
Operating system APIs, database storage, file formats (Parquet, ONNX).
### 3.4 Communications Protocols
Local IPC, WebSockets, or HTTP REST APIs.

---

## 4. System Features & Functional Requirements
### 4.1 Feature 1: [e.g., Real-Time Video Ingestion]
- **`REQ-FUNC-001` (P0):** The system shall capture video frames from the primary capture device at $\ge 30$ FPS.
- **`REQ-FUNC-002` (P0):** The system shall maintain an asynchronous frame queue of maximum size 2 to prevent buffer accumulation.
- **`REQ-FUNC-003` (P1):** The system shall log a warning if frame drop rate exceeds 5% over a 60-second window.

### 4.2 Feature 2: [e.g., Landmark Extraction & Normalization]
- **`REQ-FUNC-004` (P0):** The system shall extract 21 3D hand landmarks per detected hand.
- **`REQ-FUNC-005` (P0):** The system shall translate coordinates relative to the wrist origin and scale-normalize them.

---

## 5. Non-Functional Requirements (NFR)
### 5.1 Performance Requirements
- **`REQ-NFR-001` (Latency):** Total end-to-end pipeline latency (frame capture to classification) shall not exceed $35\text{ms}$ on standard hardware.
- **`REQ-NFR-002` (Memory):** Peak RAM utilization shall not exceed 1.5 GB during active inference.
### 5.2 Safety & Privacy Requirements
- **`REQ-NFR-003` (Privacy):** No raw facial or biometric video frames shall be transmitted over external networks; all processing must remain local-first.
### 5.3 Reliability & Availability
- **`REQ-NFR-004` (Recovery):** The video stream processor shall automatically attempt reconnection within 1 second if the camera connection drops.
### 5.4 Maintainability & Code Quality
- **`REQ-NFR-005` (Test Coverage):** Automated unit and integration test coverage across core modules shall be $\ge 85\%$.

---

## 6. Verification & Validation Matrix
| Requirement ID | Verification Method | Acceptance Criteria |
| :--- | :--- | :--- |
| `REQ-FUNC-001` | Automated Test | Benchmark capture FPS $\ge 30$ over 1000 frames |
| `REQ-FUNC-004` | Unit Test | Landmark vector shape equals `(21, 3)` with values in $[0, 1]$ |
| `REQ-NFR-001` | Performance Test | P95 latency $\le 35\text{ms}$ |
