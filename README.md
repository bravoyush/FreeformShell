# FreeformShell 🚀

[![Build and Release FreeformShell APK](https://github.com/Ayush/FreeformShell/actions/workflows/release.yml/badge.svg)](https://github.com/Ayush/FreeformShell/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](file:///g:/Ai/FreeformShell/LICENSE)
[![Android Compatibility](https://img.shields.io/badge/Android-11%20to%2015%2B-green.svg)](#compatibility-guidelines)
[![AI Co-Developed](https://img.shields.io/badge/AI--Co--Developed-Antigravity-orange.svg)](#ai-human-co-development-vibe-coding)

**FreeformShell** is a system-level window manager overlay operating directly on native Android windowing components. Leveraging deep system-level APIs via Shizuku, direct Binder reflection, and fallback ADB shell utilities, FreeformShell unlocks desktop-class display scaling, custom densities, and flexible window overlays on Android devices running **Android 11 (API 30) up to Android 15+ (API 35+)**.

---

## 📖 Quick Start & User Guide

Setting up FreeformShell is easy. Follow this step-by-step guide to get up and running:

### 1️⃣ Step 1: Install the APK
1. Navigate to [GitHub Releases](https://github.com/bravoyush/FreeformShell/releases).
2. Download the latest compiled asset:
   *   `FreeformShell-debug.apk` (Recommended for developers/testers).
   *   `Freeform-Beta-[Version]-debug.apk` (Standard beta branch build).
3. Install the APK on your Android device (ensure "Allow Installation from Unknown Sources" is enabled in settings).

![Step 1: APK Installation](docs/images/step1_install.png)

### 2️⃣ Step 2: Establish the Binder Connection
FreeformShell uses high-performance system Binder connections to manage windows safely.
*   **Option A: Using Shizuku (Recommended — On-Device)**:
    1. Install [Shizuku from Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api).
    2. Open Shizuku and follow the on-screen instructions to start it via **Wireless Debugging** (no computer required!).
    3. Open **Freeform Beta**, click on **Shizuku Permission** under the status dashboard, and grant permission in the popup.
*   **Option B: Using ADB Shell (Computer Fallback)**:
    If Shizuku is not running, connect your phone to a computer with USB Debugging enabled, and start Shizuku's binder service manually:
    ```bash
    adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
    ```

![Step 2: Shizuku & Binder Setup](docs/images/step2_shizuku.png)

### 3️⃣ Step 3: Enable the Accessibility Service
To display desktop window frames and drag handles on top of normal Android apps:
1. Open the **Freeform Beta** App.
2. Click the **Accessibility Service** chip under status dashboard.
3. System settings will open. Locate **Freeform Beta / FreeformShell**, toggle **On**, and accept overlay warnings.

![Step 3: Accessibility Permission](docs/images/step3_accessibility.png)

### 4️⃣ Step 4: Master the Window Controls & Gestures
Once set up, launching apps via the widgets or dashboard triggers the freeform shell:
*   **Drag & Move**: Hold the colored title bar at the top of a floating app to move it anywhere.
*   **Resize**: Grab the corners or bottom/side borders of a window to resize it smoothly.
*   **Minimize Bubble**: Click the `_` icon on the window frame. The window shrinks into a floating bubble on the screen edge. Tap the bubble to expand it back instantly!
*   **Maximize**: Click the `[]` icon to expand the window to fullscreen.
*   **Split-Snapping**: Drag a window toward the left or right edge of the display. A visual snapping guideline appears—release to snap the app to exactly half-screen, creating a split productivity workspace.
*   **Close**: Tap the `X` button on the title frame to close the task.

![Step 4: Window Controls & Floating Overlays](docs/images/step4_window_controls.png)

---

> [!NOTE]
> ### 💡 The Philosophy: Why "FreeformShell"?
> *   **`Freeform`**: Refers to Android’s native **Freeform Windowing Mode** (originally introduced in Android 7.0). This hidden system mode allows multiple applications to run simultaneously inside floating, resizable, overlapping windows, mimicking a desktop multitasking experience rather than standard mobile split-screen or single-app layouts.
> *   **`Shell`**: In operating systems, a *shell* is the outer interface that manages how users interact with services, organize active workspaces, and control tasks. FreeformShell acts as a comprehensive system-level overlay shell—providing window titles, close/minimize/maximize buttons, snap-to-edge guidelines, minimized floating bubbles, and display-density adjustment dialogs. It literally wraps the native Android window system in a desktop-like shell overlay.
> *   **`Freeform Beta` (Launcher Name)**: To ensure absolute user clarity during testing, the app displays as **Freeform Beta** in the Android launcher for experimental/beta branches, while keeping all internal packages, Gradle parameters, and system APIs unified under the robust `FreeformShell` name.

---

## ✨ Core Features Tour

Explore the main panels and settings screens of FreeformShell designed to give you a premium, tailored window management experience:

### 📊 1. Dashboard & Diagnostics
*   **Live Telemetry**: Monitor status checks of the Shizuku Binder Service, active binder permissions, and Accessibility Service.
*   **Display Enumerator**: Displays names, IDs, hardware resolutions, and default densities of all active displays (built-in phone screen + external screens).
*   **System Diagnostics**: Provides a real-time console log of underlying shell activity, command execution diagnostics, and ADB connection status.

### 🎨 2. Window Customization & Style
*   **Glassmorphic Aesthetics**: Adjust transparent window overlays, borders, and dimming backdrops to match your styling preference.
*   **Interactive Corner Sliders**: Adjust window corner roundness (`0dp` sharp up to `32dp` rounded) and border thickness in real-time.
*   **Title Bar & Stroke Opacities**: Customize handle transparency to make overlays bleed seamlessly into the background.
*   **Harmonized Accent Color Picker**: Integrates an HSL-based color selector to style overlay title frames, snap indicators, and minimized bubbles. Includes a real-time **Interactive Window Preview** within the app so you see styles apply instantly.

### 📐 3. Display Safe Area Offsetter
*   **Notch & Camera Offset**: Compensate for rounded corners, camera notches, or punch-hole cameras by defining safe-area insets.
*   **Multi-Monitor Customization**: Configure top, bottom, left, and right safe boundaries independently for each connected display panel.

### ⚙️ 4. Premium Display Density (DPI) Presets
*   **Scaling Down Focus**: Pack more information on screen! Presets focus heavily on scaling down, including **`120 DPI` (Ultra Compact)**, **`160 DPI` (Desktop mdpi)**, 240, 320, 360, Physical Default, and 480 DPI.
*   **Allow Unsafe Extreme DPI**: Developers can toggle a override option to unlock extreme custom values down to **`100 DPI`**.
*   **Windows-Style Reversion Shield**: To prevent touch soft-locks, applying density changes triggers a **15-second overlay countdown timer** with a 50% backdrop dimming effect. If unconfirmed, it automatically rolls back safely to your previous DPI.
*   **Unrestricted Secondary Displays**: Custom scale external desktop screens (e.g. mock monitors, USB-C docks, virtual displays) instantly without safe boundary limits or warning interruptions.

### 🚫 5. Application Blacklist Manager
*   **Compatibility Safety Valve**: Select specific applications (like mobile-only banking apps, camera software, or heavy fullscreen games) to blacklists. Blacklisted applications will launch in standard native fullscreen rather than freeform mode.

### 🧩 6. Interactive Home Screen Widgets
*   **One-Click Launches**: Pin specialized widgets to your Android launcher to launch pre-selected workspaces or specific apps directly into a floating freeform window.

---

## 🛠️ Developer & Contributor Guide

Welcome to the development center! Below are instructions on how the codebase is structured, compiled, and maintained using automated pipelines and AI-guided boundaries.

### 📂 Project Structure

```
├── .agents/                 # Developer Guidelines & AI Agent instructions
│   └── rules/
│       ├── compatibility.md  # Key Android 11-15+ compatibility boundaries
│       └── graphify.md       # Knowledge Graph navigation rules
├── .github/
│   └── workflows/
│       └── release.yml      # CI/CD Automated Gradle builder & Release drafter
├── app/                     # Main Android Application module
│   ├── src/main/java        # Source files (MainActivity, Services, Core managers)
│   └── build.gradle.kts     # Build definitions & Shizuku dependencies
├── docs/                    # Architectural plans & mermaid diagram snapshots
├── LICENSE                  # Apache 2.0 Open-Source License
└── NOTICE                   # Primary human-AI co-development attribution shield
```

### 🧩 Tech Stack & Architecture

*   **Language**: Modern Kotlin
*   **UI Framework**: Jetpack Compose (Material 3) with customized harmonized HSL-based styling
*   **System Core**: 
    *   **Shizuku / Rikka API** — For ultra-low latency direct Binder connection.
    *   **LSPosed HiddenApiBypass** — Safely bypass reflection limits on Android 14+.
    *   **Defensive Fallbacks** — Seamless transition between Binder commands and ADB shell commands.

### 🤖 AI-Human Co-Development & "Vibe-Coding"

This project is built using a modern **AI-Human Co-Development** paradigm. We check in the `.agents/` folder and `graphify-out/` into the Git repository to enable seamless agentic collaboration:

*   **`.agents/rules/`**: Any future developer's AI agent (like Cursor, Claude, Antigravity) will instantly read [compatibility.md](file:///g:/Ai/FreeformShell/.agents/ai_knowledge/generic/android_12_hover_loop.md) on startup, preventing them from introducing breaking changes or breaking API backwards compatibility.
*   **`graphify-out/`**: Houses the codebase's semantic relation graph, helping AI tools navigate and map class dependencies with zero latency.

*For more information on contributing, see our [CONTRIBUTING.md](file:///g:/Ai/FreeformShell/CONTRIBUTING.md) guide.*

### 🚀 Building & Running Locally

Ensure you have Android Studio installed and a device/emulator connected with ADB enabled.

#### 1. Compile the App
Compile the project directly via Gradle in your terminal:
```bash
./gradlew compileDebugKotlin
```

#### 2. Install to Connected Device
Install the debug APK on your device:
```bash
./gradlew installDebug
```

### 📦 Automated Release Pipeline (CI/CD)

FreeformShell includes a fully automated build pipeline. Simply push a version tag to GitHub to trigger the release workflow:

```bash
git tag v1.4.0
git push origin v1.4.0
```

GitHub Actions will automatically spin up, build the Debug and Unsigned Release APKs, compile changelogs from your commit history, and draft a release containing the downloadable files!

### 🛡️ License & Attributions

This project is open-sourced under the **Apache License 2.0**. 

Under Section 4d of the Apache 2.0 license, any downstream forks or commercial adaptations **must preserve and distribute the original `NOTICE` file** unmodified. This legal shield guarantees that you, your AI tool creators, and future contributors receive lasting attribution:

> See [NOTICE](file:///g:/Ai/FreeformShell/NOTICE) for details.
