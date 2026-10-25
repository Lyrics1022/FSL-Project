---
name: system-architect
description: >-
  Use this skill when designing software architectures, creating system component models, establishing bounded contexts, or drafting API contracts.
license: Apache-2.0
---

# System Architecture & System Creation

Guides the design of robust, modular, and maintainable software architectures.

## Architectural Design Protocol

### 1. Requirements & Boundary Analysis
- Catalog functional requirements, non-functional requirements (SLAs, latency targets, throughput), and constraints.
- Delineate domain boundaries and service interactions using Domain-Driven Design (DDD) principles.

### 2. System Decomposition
- Select architectural style: Modular Monolith vs. Microservices vs. Event-Driven.
- Document components, responsibilities, and interface contracts (REST OpenAPI 3.1 or gRPC Protobuf).

### 3. Diagramming
Generate Mermaid diagrams illustrating:
- Level 1: System Context Diagram
- Level 2: Component & Container Diagram
- Data Flow Diagram illustrating request lifecycles.

### 4. Verification & Review
- Validate single responsibility per module.
- Check data persistence guarantees (ACID vs. Eventual Consistency).
- Create an Architecture Decision Record (ADR) summarizing the choice.
