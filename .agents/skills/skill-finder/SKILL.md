---
name: skill-finder
description: >-
  Use this skill when searching for, discovering, evaluating, or installing new agent skills from registries (SkillDB, agentskills.io, GitHub) or project workspaces.
license: Apache-2.0
compatibility: requires-internet-access
---

# Skill Finder & Manager

This skill guides the discovery, security vetting, and installation of external skills into the agent environment.

## Workflow Steps

### 1. Identify Needed Capability
- Extract search keywords from user intent (e.g., `opencv`, `playwright`, `pytorch-tuning`).
- Check if an existing skill in `.agents/skills/` or `~/.gemini/config/skills/` already covers this need.

### 2. Query Registries
Search canonical repositories:
- `https://skilldb.dev`
- `https://agentskills.io`
- GitHub topic `agent-skills` or `cursor-rules`

### 3. Security & Quality Audit
Before installing any skill:
- Inspect YAML frontmatter for valid `name` and `description`.
- Check bundled `scripts/` for dangerous operations (unauthorized network calls, destructive filesystem edits).
- Verify licensing (MIT, Apache-2.0, BSD preferred).

### 4. Installation
- Download or scaffold the skill directory into `.agents/skills/<skill-name>/`.
- Validate that `SKILL.md` contains valid YAML frontmatter and clear procedural instructions.
