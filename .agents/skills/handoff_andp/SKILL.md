# 🛠️ Skill: Agent-Native Development Protocol (ANDP) & Session Handoff

This skill provides a highly optimized, token-efficient, and zero-context-drift operational model for AI agents working in this repository. By reading this file with `IsSkillFile: true`, the executing agent instantly inherits the complete project synchronization protocol, ensuring perfect developer parity and absolute coordination across multiple AI sessions.

---

## 🎯 Skill Capabilities & Scope
- **Bootstrapping**: Seamlessly acquire the active project state, constraints, and maintainer identities in under 30 seconds.
- **Token Conservation**: Reduce API cost and context pollution by up to 90% using graph-guided search and delta edits.
- **Perfect Synchronization**: Guarantee that all completed milestones, current progress, and blocking bugs are immediately transferred to subsequent agents.

---

## 🚀 Execution Flow (Step-by-Step)

### Phase 1: The Entry Protocol (Bootstrap)
Upon session start, the executing agent must perform the following actions:

1. **Step 1.1: Read the State Ledger**
   - **Action**: Open and read [.agents/LEDGER.md](file:///g:/Ai/FreeformShell/.agents/LEDGER.md).
   - **Goal**: Instantly learn the current active metadata, core architectural modules, platform compatibility guardrails, and completed feature lists.
   
2. **Step 1.2: Read the Handoff Brief**
   - **Action**: Open and read [.agents/HANDOFF.md](file:///g:/Ai/FreeformShell/.agents/HANDOFF.md).
   - **Goal**: Understand precisely what the previous agent completed, active context caches, and next task targets.
   
3. **Step 1.3: Read Team & Identity Rules**
   - **Action**: Open and read [.agents/rules/identity.md](file:///g:/Ai/FreeformShell/.agents/rules/identity.md).
   - **Goal**: Align with admin roles (Ayush as lead architect), expected voice tone, and styling requirements.

---

### Phase 2: Token-Efficient Development
During active execution, the agent must practice maximum token discipline:

1. **Step 2.1: Semantic Graph Traversal**
   - Before running large directory list operations or heavy grep queries, check `graphify-out/graph.json`.
   - Run localized queries to extract small, relevant subgraphs of the codebase (e.g. `graphify query "<concept>"`).
   
2. **Step 2.2: Precision Coding (Delta Edits)**
   - **Rule**: Do not rewrite full files or generate massive code blocks in the chat console.
   - Use `replace_file_content` for single contiguous edits, or `multi_replace_file_content` for non-contiguous changes.
   - Provide clickable file links using the format `[basename](file:///path)` for easy verification.

3. **Step 2.3: Planning & Checkpointing Artifacts**
   - **Complex tasks** MUST use planning mode:
     - `implementation_plan.md` -> Shared with user for architectural approval.
     - `task.md` -> Living task checklist updated throughout execution.
     - `walkthrough.md` -> Final summary of modifications, tests, and visual/log validation.

---

### Phase 3: The Exit Protocol (Handoff)
Before concluding the session, the agent must update the codebase repository state:

1. **Step 3.1: Update State Ledger**
   - Check off all finished items under `🟢 Completed Features` in [.agents/LEDGER.md](file:///g:/Ai/FreeformShell/.agents/LEDGER.md).
   - Shift newly identified features or refactoring tasks to the `🟡 In Progress / Planned` list.
   - Update the `*Last Updated*` stamp at the bottom of the ledger.

2. **Step 3.2: Re-generate Handoff Brief**
   - Overwrite [.agents/HANDOFF.md](file:///g:/Ai/FreeformShell/.agents/HANDOFF.md) with a fresh status update.
   - Clearly specify:
     - **Current State**: Exact compilation & runtime status of the active branch.
     - **Context Snapshot**: Active caches, workspace coordinates, and tools used.
     - **Next Tasks**: Concrete actionable instructions for the next agent to immediately begin execution.

3. **Step 3.3: Re-index Code Graph**
   - Run `graphify update .` to keep the codebase AST index perfectly up to date.

4. **Step 3.4: Perform Versioned Code Backup & Retention Check**
   - **Action**: Run the backup script to archive a clean copy of the current state:
     `python .agents/skills/backup/backup.py`
   - **Why**: Keeps a perfect timeline of branch milestones named using `<version>_<YYYY-MM-DD_HH-MM-SS>` within `g:\Ai\Freeform Backup\<branch>\`. It automatically manages storage limits, keeping the 5 most recent backups for the active version and archiving older ones to the `old/` directory.


---

## 📑 Core Document Templates

### 1. LEDGER.md Template
When creating or formatting a State Ledger, follow this standardized structure:
```markdown
# 📑 [Repository Name] — Active Repository State Ledger

This ledger is the single source of truth for the active state of the project.

## 🚀 Active Metadata
*   **Current Version:** vX.X
*   **Status:** [Stable / Active Development]
*   **Target OS / Platforms:** [Target environment details]
*   **Key Dependencies:** [Frameworks, key libraries]

## 🏛️ Core Architectural Modules
| Module | Core File | Purpose & Responsibilities |
| :--- | :--- | :--- |
| **Name** | [Link](file:///path) | Purpose description |

## 🛡️ Critical Guidelines & Pitfalls (Guardrails)
1. **Rule Name**: Description of runtime compatibility trap or pattern guideline.

## 📋 Active Implementation Checklists
### 🟢 Completed Features
- [x] **Feature Name**: Brief summary of architectural implementation details.

### 🟡 In Progress / Planned
- [ ] **Feature Name**: Actionable next step description.

*Last Updated: YYYY-MM-DD by AgentName (Summary of changes)*
```

### 2. HANDOFF.md Template
When writing the Session Handoff Brief, use this layout:
```markdown
# 🤝 Agent Handoff Brief

*   **From:** [Agent Name]
*   **To:** Any Subsequent AI Agent / Human Coder
*   **Date:** YYYY-MM-DD
*   **Current State:** [Brief high-level description of compilation/runtime state]

---

## ⚡ Context Snapshot
1.  **Codebase Graph Status**: [AST index status]
2.  **State Ledger Status**: [Ledger update status]
3.  **Active Rules Status**: [Active workspace rules]

---

## 🚀 How to Bootstrap This Session
1.  **Step 1**: Read State Ledger at [.agents/LEDGER.md](file:///g:/Ai/FreeformShell/.agents/LEDGER.md).
2.  **Step 2**: Read Handoff Brief at [.agents/HANDOFF.md](file:///g:/Ai/FreeformShell/.agents/HANDOFF.md).
3.  **Step 3**: Target specific work using graph-based pathing and delta edits.

---

## 🎯 Next Tasks On the Horizon
1.  **[High Priority Task]**: Direct execution guidelines.
2.  **[Secondary Task]**: Desired behavior.
```

---

> [!IMPORTANT]
> Always execute this skill at the beginning and end of every development phase to guarantee absolute structural integrity and maintain perfect synchronization across all collaborative workflows.
