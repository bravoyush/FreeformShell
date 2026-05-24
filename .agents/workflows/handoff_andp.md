---
name: handoff-andp
description: Initialize or execute the Agent-Native Development Protocol (ANDP) and session handoff pipeline
---

# Workflow: handoff-andp

This workflow governs agent entry, token-efficient development, and perfect handoff execution.

## Entry Phase (Bootstrapping)
Follow the handoff-andp skill installed at [.agents/skills/handoff_andp/SKILL.md](file:///g:/Ai/FreeformShell/.agents/skills/handoff_andp/SKILL.md) to bootstrap your session, maintain ledger synchronization, and write clean handoff briefs.

To invoke the skill, run `view_file` on the SKILL.md file with the `IsSkillFile` argument set to `true`:
`view_file(AbsolutePath="g:\Ai\FreeformShell\.agents\skills\handoff_andp\SKILL.md", IsSkillFile=true)`

## Exit Phase (Handoff & Ledger Auto-Sync)
To ensure that all completed features, active uncommitted states, and code graphs are perfectly updated at the end of the session, the agent **MUST** invoke the automated synchronization skill by running:

```bash
python .agents/skills/sync_ledger/sync_ledger.py --message "Detailed description of changes made"
```

This script will dynamically read Git modifications, update the Completed Features list in `LEDGER.md`, re-generate `HANDOFF.md`, and execute `graphify update .` to sync the codebase AST.

