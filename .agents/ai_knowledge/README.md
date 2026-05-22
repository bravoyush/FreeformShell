# AI Knowledge Exchange Hub 🧠🤖

Welcome, fellow AI Agent! This directory serves as a shared, decentralized "brains trust" and knowledge-sharing wiki for **FreeformShell**. 

As we pair-program with human architects and explore the codebase, we encounter unique Android system behaviors, undocumented APIs, specific OEM restrictions (Samsung, Sony, Xiaomi, etc.), and custom version workarounds. This hub ensures that **any discovery made by one AI agent is instantly shared with all future AI agents** working on the project.

---

## 🔍 How to Use This Hub

### 1. Before You Code (Querying)
Whenever you start a task, check this directory (`.agents/ai_knowledge/`) and read files matching your task context.
*   **OEM Quirks**: If editing focus, touch dispatching, or multi-display features, check `oems/samsung/`, `oems/sony/`, etc.
*   **OS Releases**: If targeting specific API levels (Android 11 to 15+), check `generic/` for version-specific anomalies.

*Tip: Running `graphify update .` will automatically blend these files into the main semantic graph, allowing you to run `graphify explain "<concept>"` to pull this information!*

### 2. When You Discover Something New (Writing)
If you encounter a new system-level bug, OEM restriction, or write a custom reflective/ADB workaround:
1.  Locate the appropriate directory (create one under `oems/` or `generic/` if it doesn't exist).
2.  Create a markdown file using the format specified in [**`template.md`**](file:///g:/Ai/FreeformShell/.agents/ai_knowledge/template.md).
3.  Add it to your commit. When the user pushes the code to GitHub, your discovery is shared globally!

---

## 📂 Directory Layout

```
.agents/ai_knowledge/
├── README.md               # You are here
├── template.md             # Standard writing template for AI agents
├── oems/                   # OEM-Specific roots
│   ├── samsung/            # Samsung DeX, Knox, and focus constraints
│   ├── sony/               # Sony Xperia multi-window and overlay handling
│   ├── xiaomi/             # MIUI/HyperOS background autostart & overlays
│   └── generic/            # Pure Android OS quirks (Android 11 to 15+)
```
