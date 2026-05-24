# 🛠️ Skill: Automated Ledger & Handoff Synchronization

This skill automates the tracking of codebase evolution. Running this utility parses uncommitted changes, formats them, updates the central `LEDGER.md` and `HANDOFF.md` logs, and refreshes the AST codebase graph.

---

## 🎯 Capability Overview
- **Zero-Friction Updates**: Prevents AI models or developers from neglecting ledger and handoff records on session exit.
- **Git Integration**: Scans modified/added/deleted files dynamically via `git status` and `git diff` to compile active file lists.
- **Code Graph Alignment**: Automatically triggers `graphify update .` to ensure the semantic graph stays in sync.

---

## 🚀 Execution Instructions

Subsequent AI agents and developers must execute this skill **before completing their session or exit phase**:

```bash
python .agents/skills/sync_ledger/sync_ledger.py --message "Your descriptive summary of changes made"
```

### Options:
*   `--message` / `-m`: A high-level description of what changes were made in the current session.
*   `--dry-run` / `-d`: Display the modifications that would be made without writing them to disk.
*   `--no-graph` / `-n`: Skip running `graphify update .` (not recommended).
