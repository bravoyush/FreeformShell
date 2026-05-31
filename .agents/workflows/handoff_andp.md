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

## Exit Phase (Handoff, Ledger Auto-Sync, and Versioned Backup)
To ensure that all completed features, active uncommitted states, and code graphs are perfectly updated at the end of the session, the agent **MUST** execute the following sequence:

1. **Auto-Sync Ledger & Handoff**: Run the automated sync script to update documentation and graphs:
   ```bash
   python .agents/skills/sync_ledger/sync_ledger.py --message "Detailed description of changes made"
   ```

2. **Run Versioned Backup**: To protect the workspace and ensure code parity without overwriting existing root directories, run the versioned backup tool:
   ```bash
   python .agents/skills/backup/backup.py
   ```
   This script will automatically detect the current git branch, parse the active version from `LEDGER.md`, create a time-stamped clean archive, and apply the retention policy (moving older than 5 backups of the same version into `old/`).


