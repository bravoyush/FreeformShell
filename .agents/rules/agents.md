## AI Operational Guidelines & Token Conservation Rules

You are an AI coding agent operating inside the **FreeformShell** workspace. To maximize token economy, prevent context drift, and guarantee perfect handoff fidelity, you **MUST** strictly adhere to the following rules:

### 1. The Entry Protocol (Mandatory First Step)
Before doing any research, analysis, or code modification:
1.  **Read the Ledger:** Open and read [.agents/LEDGER.md](file:///g:/Ai/FreeformShell/.agents/LEDGER.md) to instantly acquire the codebase schema, stability guardrails, and version status.
2.  **Read the Handoff:** Open and read [.agents/HANDOFF.md](file:///g:/Ai/FreeformShell/.agents/HANDOFF.md) to see exactly what the last agent did and what needs to be worked on now.

### 2. Token Discipline & Context Optimization
1.  **Use Graphify First:** Do not run large file reads or generic grep searches. If `graphify-out/graph.json` exists, use `query_graph` or `shortest_path` to read only the small, relevant subgraphs.
2.  **No Code Echoing:** Do not paste entire generated files into the chat thread. The user's screen is synchronized with the file system. Point directly to absolute markdown file links (e.g., `[basename](file:///path)`) and highlight only the key functional differences.
3.  **Targeted Edits:** Always perform modifications using small, contiguous chunks with `replace_file_content` or `multi_replace_file_content` instead of overwriting full files.

### 3. Planning & Checkpointing
1.  Use **Planning Mode** for all non-trivial changes:
    *   Create `implementation_plan.md` to align with the user on design details *before* editing source code.
    *   Create `task.md` to track progress sequentially during execution.
    *   Create `walkthrough.md` to document completed validations and test metrics.

### 4. The Exit Protocol (Mandatory Final Step)
Before finishing your session:
1.  Update the checklists and active status logs in [.agents/LEDGER.md](file:///g:/Ai/FreeformShell/.agents/LEDGER.md).
2.  Overwrite [.agents/HANDOFF.md](file:///g:/Ai/FreeformShell/.agents/HANDOFF.md) with a clear handoff brief for the next model or developer, detailing active state, completed work, and upcoming targets.
3.  Run `graphify update .` to keep the AST indices fresh (costs 0 API tokens).
