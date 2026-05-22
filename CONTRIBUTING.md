# Contributing to FreeformShell

We love contributions from human architects, vibe-coders, and AI-assisted agents alike! To keep collaboration seamless and the codebase robust, please follow these guidelines.

---

## 🎨 Vibe-Coding & AI Agent Setup

FreeformShell is designed to be highly compatible with AI coding agents (such as Antigravity, Claude, Cursor, and GitHub Copilot).

*   **Read the Rules**: When starting an editing session, point your AI agent to the guidelines located in [`.agents/rules/compatibility.md`](file:///.agents/rules/compatibility.md). This keeps the agent from violating defensive reflection fallbacks or breaking older Android releases (API 30-35+).
*   **Semantic Graphing**: If you modify structural files, please run the graphify tool to update the codebase's semantic representation:
    ```bash
    graphify update .
    ```

---

## 🛠️ Local Development & Testing

### 1. Build Verification
Before submitting any Pull Request, ensure that the project compiles cleanly with **zero syntax errors**:
```bash
./gradlew compileDebugKotlin
```

### 2. Device Verification
Run the application on a target physical device or emulator to test overlay behaviors:
```bash
./gradlew installDebug
```

---

## 🔀 Pull Request Process

We track all contributions using standard **Pull Requests (PRs)**. Because Git only tracks **diffs** (exact lines changed, rather than copying the whole repo), your PR will be highly compact and readable!

1.  **Fork** the repository and create a new branch for your feature or bug fix:
    ```bash
    git checkout -b feature/cool-new-overlay
    ```
2.  **Make your changes**, ensuring you keep all existing unrelated code documentation and compatibility structures intact.
3.  **Validate your build** locally to prevent CI failures.
4.  **Commit your changes** with descriptive messages:
    ```bash
    git commit -m "feat: added new compact scale toggle down to 120 DPI"
    ```
5.  **Push** to your fork and open a Pull Request on GitHub.

---

## 🤖 Automated PR Review Process (For Maintainers)

If you are a maintainer reviewing external PRs, you can use your local AI agent to review contributions safely:

1.  **Fetch the PR branch** to your local workspace:
    ```bash
    git fetch origin pull/PR_NUMBER/head:contrib-review
    git checkout contrib-review
    ```
2.  **Instruct your AI agent**:
    > *"Check this branch. Run a compilation test and review the diff to make sure it respects our Android 11-15+ compatibility rules and keeps the HSL styling consistent."*
3.  If approved, merge it into the `main` branch. GitHub Actions will handle the rest!
