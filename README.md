# FreeformShell 🚀

[![Build and Release FreeformShell APK](https://github.com/Ayush/FreeformShell/actions/workflows/release.yml/badge.svg)](https://github.com/Ayush/FreeformShell/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android Compatibility](https://img.shields.io/badge/Android-11%20to%2015%2B-green.svg)](#compatibility-guidelines)
[![AI Co-Developed](https://img.shields.io/badge/AI--Co--Developed-Antigravity-orange.svg)](#ai-human-co-development-vibe-coding)

**FreeformShell** is a high-performance system-level window manager overlay operating directly on native Android windowing components. By leveraging deep system-level APIs via Shizuku, direct Binder reflection, and fallback ADB shell utilities, FreeformShell unlocks desktop-class display scaling, custom densities, and flexible window overlays on Android devices running **Android 11 (API 30) up to Android 15+ (API 35+)**.

---

## ✨ Key Features

*   **⚡ Premium Custom DPI Presets**: Seamlessly adjust display density across safety limits (from `120 DPI` Ultra Compact up to `480 DPI` Large). Includes a developer override toggle to unlock unsafe densities down to `100 DPI` globally.
*   **🖥️ Multi-Display & Secondary Screen Scaling**: Safely custom-scale secondary monitors or virtual desktop screens (e.g., via `scrcpy` external display mocks) independently of your main screen.
*   **🛡️ Windows-Style Reversion Shield**: Displays an overlay confirmation timer (15s) with a 50% backdrop dimming effect when applying changes. Instantly rolls back density modifications if unconfirmed, preventing soft-locks.
*   **🧩 Robust Compatibility Layer**: Integrates LSPosed `HiddenApiBypass`, defensive reflection fallbacks (e.g. `setFocusedRootTask` falling back to `setFocusedTask`), and ADB shell fallbacks for maximum resilience across stock and custom OEMs.
*   **🎨 Jetpack Compose UI**: Crafted with a premium glassmorphic theme, custom micro-animations, and dynamic visual feedbacks.

---

## 🛠️ Tech Stack & Architecture

*   **Language**: Modern Kotlin
*   **UI Framework**: Jetpack Compose (Material 3) with customized harmonized HSL-based styling
*   **System Core**: 
    *   **Shizuku / Rikka API** — For ultra-low latency direct Binder connection.
    *   **LSPosed HiddenApiBypass** — Safely bypass reflection limits on Android 14+.
    *   **Defensive Fallbacks** — Seamless transition between Binder commands and ADB shell commands.

---

## 📂 Project Structure

```
├── .agents/                 # Developer Guidelines & AI Agent instructions
│   └── rules/
│       ├── compatibility.md  # Key Android 11-15+ compatibility boundaries
│       └── graphify.md       # Knowledge Graph navigation rules
├── .github/
│   └── workflows/
│       └── release.yml      # CI/CD Automated Gradle builder & Release drafter
├── app/                     # Main Android Application module
│   ├── src/main/java        # Source code files (MainActivity, Services, etc.)
│   └── build.gradle.kts     # Build definitions & shizuku dependencies
├── docs/                    # Architectural plans & mermaid diagram snapshots
├── LICENSE                  # Apache 2.0 Open-Source License
└── NOTICE                   # Primary human-AI co-development attribution shield
```

---

## 🤖 AI-Human Co-Development & "Vibe-Coding"

This project is built using a modern **AI-Human Co-Development** paradigm. We check in the `.agents/` folder and `graphify-out/` into the Git repository to enable seamless agentic collaboration:

*   **`.agents/rules/`**: Any future developer's AI agent (like Cursor, Claude, Antigravity) will instantly read these rules on startup, preventing them from introducing breaking changes or breaking API backwards compatibility.
*   **`graphify-out/`**: Houses the codebase's semantic relation graph, helping AI tools navigate and map class dependencies with zero latency.

---

## 🚀 Building & Running Locally

Ensure you have Android Studio installed and a device/emulator connected with ADB enabled.

### 1. Compile the App
Compile the project directly via gradle in your terminal:
```bash
./gradlew compileDebugKotlin
```

### 2. Install to Connected Device
Install the debug APK on your device:
```bash
./gradlew installDebug
```

---

## 📦 Automated Release Pipeline (CI/CD)

FreeformShell includes a fully automated build pipeline. Simply push a version tag to GitHub to trigger the release workflow:

```bash
git tag v1.4.0
git push origin v1.4.0
```

GitHub Actions will automatically spin up, build the Debug and Unsigned Release APKs, compile changelogs from your commit history, and draft a release containing the downloadable files!

---

## 🛡️ License & Attributions

This project is open-sourced under the **Apache License 2.0**. 

Under Section 4d of the Apache 2.0 license, any downstream forks or commercial adaptations **must preserve and distribute the original `NOTICE` file** unmodified. This legal shield guarantees that you, your AI tool creators, and future contributors receive lasting attribution:

> See [NOTICE](file:///g:/Ai/FreeformShell/NOTICE) for details.
