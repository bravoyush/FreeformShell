# Graph Report - FreeformShell  (2026-05-22)

## Corpus Check
- 42 files · ~133,841 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 652 nodes · 952 edges · 42 communities (33 shown, 9 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 17 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `456af6cc`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]

## God Nodes (most connected - your core abstractions)
1. `ThemeManager` - 73 edges
2. `DragResizeOverlay` - 56 edges
3. `FreeformOverlayService` - 39 edges
4. `ShellExecutor` - 24 edges
5. `🆕 What's New Since v1.1?` - 19 edges
6. `MainScreen()` - 15 edges
7. `DisplayShell` - 13 edges
8. `WorkspaceManager` - 12 edges
9. `SplitResizeHandle` - 11 edges
10. `FreeformWidgetFactory` - 11 edges

## Surprising Connections (you probably didn't know these)
- `DragResizeOverlay` --rationale_for--> `High-Performance Shell Throttling`  [EXTRACTED]
  app/src/main/java/com/example/freeformshell/DragResizeOverlay.kt → docs/core_logic.md
- `DragResizeOverlay` --rationale_for--> `Dynamic Screen-to-View Occlusion Clipping`  [EXTRACTED]
  app/src/main/java/com/example/freeformshell/DragResizeOverlay.kt → docs/architecture_evolution_log.md
- `DragResizeOverlay` --rationale_for--> `Dynamic Tiling Grid Engine`  [EXTRACTED]
  app/src/main/java/com/example/freeformshell/DragResizeOverlay.kt → docs/architecture_evolution_log.md
- `DragResizeOverlay` --rationale_for--> `Programmatic Active Focus Routing`  [EXTRACTED]
  app/src/main/java/com/example/freeformshell/DragResizeOverlay.kt → docs/architecture_evolution_log.md
- `FreeformOverlayService` --rationale_for--> `Android 14 Hidden API Exemption`  [EXTRACTED]
  app/src/main/java/com/example/freeformshell/FreeformOverlayService.kt → docs/architecture_evolution_log.md

## Hyperedges (group relationships)
- **Window Drag and Resize Interaction Loop** — freeformshell_dragresizeoverlay_dragresizeoverlay, freeformshell_freeformoverlayservice_freeformoverlayservice, freeformshell_shellexecutor_shellexecutor [INFERRED 0.95]
- **Dynamic Tiling and Docking Grid System** — freeformshell_dragresizeoverlay_dragresizeoverlay, freeformshell_workspacemanager_workspacemanager, core_logic_smart_snap_corner_restraints, core_logic_touch_aware_edge_snap_thresholds [INFERRED 0.95]

## Communities (42 total, 9 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.06
Nodes (28): Background Thread Concurrency Lock & Drag Sync Bypass, Dynamic Screen-to-View Occlusion Clipping, Persistent SharedPreferences Blacklist, Rendering Integrity & Flicker Prevention, DisplayShell, ensureForceCloseLoaded(), ensureLoaded(), FreeformOverlayService (+20 more)

### Community 2 - "Community 2"
Cohesion: 0.09
Nodes (11): State-Aware Edge Hold Timer, Interaction Window Hiding, Handle Isolation, Jitter-Free Title Bar Pinning, Smart Snap Corner Restraints & Edge Hold Delays, Snapping Exit & Window Scaling Rules, Touch-Aware Edge Snap Thresholds, dismissActiveSnapMenu() (+3 more)

### Community 3 - "Community 3"
Cohesion: 0.04
Nodes (44): 1. Dynamic Android 14 Hidden API Bypass via LSPosed vs. Legacy Double Reflection, 1. Persistent Interactive Shell Session vs. Spawning One-Off Shell Processes, 2. High-Performance Interaction Overlay Hiding (5 Overlays down to 1), 2. Programmatic Active Focus Routing via setFocusedRootTask vs. Simulated Touch Inject Taps, 3. Edge-Snapping Hold Delay Timers vs. Instant Snapping, 4. Background Thread Concurrency Lock & Intelligent Drag Sync Bypass, 5. Persistent SharedPreferences Blacklist & Set Exact-Matching, 6. Dynamic Screen-to-View Occlusion Clipping & Race-Condition Bypass (+36 more)

### Community 4 - "Community 4"
Cohesion: 0.05
Nodes (39): 10. Tiled Layout Swap, 11. Launcher & Dock Avoidance, 12. Drag & Resize Tint Overlay, 13. Title Bar Opacity Control, 14. Pill Shrink Style Picker, 15. Real-time Resize Toggle, 16. Instant Shell Resizing (Disable System Animations), 17. Force Close per App (+31 more)

### Community 5 - "Community 5"
Cohesion: 0.20
Nodes (7): Persistent Interactive Shell Session, Programmatic Active Focus Routing, High-Performance Shell Throttling, CommandResult, onConnected(), onDisconnected(), ShellExecutor

### Community 6 - "Community 6"
Cohesion: 0.13
Nodes (22): Android 14 Hidden API Exemption, Dynamic Tiling Grid Engine, AppIcon(), AppInfo, AppSettingsScreen(), BlacklistScreen(), CompatibilityScreen(), CustomizationScreen() (+14 more)

### Community 7 - "Community 7"
Cohesion: 0.10
Nodes (19): 1. Advanced Window Snapping & Quadrants, 📺 1. Windows (Dashboard), 2. Multi-Display & Safe Area Dock Margins, 🎨 2. Theme (Customization), 3. Dynamic App-Themed Branding, 🛡️ 3. Safe Area, 🚫 4. Blacklist, 4. Interactive Split Snapping & Splitter Handles (+11 more)

### Community 8 - "Community 8"
Cohesion: 0.13
Nodes (14): 1. Advanced Window Snapping & Quadrants, 2. Live Dynamic Color Branding, code:`carousel (![Snapping Left Split](/absolute/path/to/artifacts/snap_left), 🛠️ Configuration Settings, ✨ Core Features, 🚀 Getting Started, ❓ Overlays Not Showing Above Specific Apps, 📋 Prerequisites (+6 more)

### Community 9 - "Community 9"
Cohesion: 0.29
Nodes (6): code:kotlin (ShellExecutor.triggerShellInteraction("wm density ...")), Metadata, 🔗 Related Components, Samsung DeX Focus Routing & Knox Overlay Restrictions, ⚠️ The Issue, 🛠️ The Workaround / Solution

### Community 10 - "Community 10"
Cohesion: 0.16
Nodes (3): FreeformWidgetFactory, FreeformWidgetService, groupToJson()

### Community 11 - "Community 11"
Cohesion: 0.22
Nodes (8): Containers Dump (dumpsys activity containers), Recents Dump (dumpsys activity recents), Tasks Dump (dumpsys activity tasks), Windows Dump (dumpsys window), AppTask, CombinedTaskState, TaskBounds, TaskManager

### Community 12 - "Community 12"
Cohesion: 0.18
Nodes (10): -----------------------------------------------, -----------------------------------------------, ⚡ Core Engine, 🎨 Customization, ⚠️ Don't Touch These, 🚀 Freeform Beta — Experimental Update, MESSAGE 1 — paste this first, MESSAGE 2 — paste this second (+2 more)

### Community 13 - "Community 13"
Cohesion: 0.18
Nodes (10): 1. Dragging Windows Is Now Buttery Smooth, 2. Window Dragging Uses Less of Your Phone's Resources, 3. Smarter Window Snapping That Doesn't Get in the Way, 4. Background Updates Don't Fight Your Finger Anymore, 5. App Block List Now Saves Across Restarts, 6. Overlays Clip Correctly Over Floating Apps & Disappear Instantly When Apps Close, 7. Snapping, Title Bar Pinning & General Polish, FreeformShell v1.1 — What's New? 🎉 (+2 more)

### Community 14 - "Community 14"
Cohesion: 0.20
Nodes (9): 1. System Architecture Overview, 2. Core Feature Guides, 3. Settings & Preferences Reference, 🎛️ A. Custom Title Bars & Window Controls, 📐 B. Gesture Resize Strips (Edge Grabs), 🧲 C. Aero Snapping & Synced Layout Pairs, code:mermaid (graph TD), 📁 D. Workspace Management System (New in v1) (+1 more)

### Community 15 - "Community 15"
Cohesion: 0.22
Nodes (8): 1. Snapping Exit & Window Scaling Rules, 2. Jitter-Free Title Bar Pinning (Cursor Anchoring), 3. High-Performance Shell Throttling (Zero-Latency Drag-Resize & 60Hz IPC Throttling), 4. Touch-Aware Edge Snap Thresholds, 5. Smart Snap Corner Restraints & Edge Hold Delays, 6. Rendering Integrity & Flicker Prevention, 7. Handle Isolation (Hiding Inactive Resize Strips & Splitter Handles), FreeformShell: Core Logic & Architectural Contracts

### Community 18 - "Community 18"
Cohesion: 0.25
Nodes (7): 📋 Active Implementation Checklists, 🚀 Active Metadata, 🟢 Completed Features (v1.2 Overhaul), 🏛️ Core Architectural Modules, 🛡️ Critical Guidelines & Pitfalls (Guardrails), 📑 FreeformShell — Active Repository State Ledger, 🟡 In Progress / Planned

### Community 20 - "Community 20"
Cohesion: 0.33
Nodes (5): 1. The Entry Protocol (Mandatory First Step), 2. Token Discipline & Context Optimization, 3. Planning & Checkpointing, 4. The Exit Protocol (Mandatory Final Step), AI Operational Guidelines & Token Conservation Rules

### Community 21 - "Community 21"
Cohesion: 0.40
Nodes (6): 📱 Android Compatibility Guidelines, 📢 Discord Announcement, 📘 Experimental Beta Guide, Programmatic Focus Routing, LSPosed HiddenApiBypass, InputDispatcher Touch Safety

### Community 22 - "Community 22"
Cohesion: 0.40
Nodes (4): 🤝 Agent Handoff Brief, ⚡ Context Snapshot, 🚀 How to Bootstrapping This Session (Token Saving Entry), 🎯 Next Tasks On the Horizon

### Community 24 - "Community 24"
Cohesion: 0.40
Nodes (4): Android Version Compatibility (Android 11 to 15+), Android Version Compatibility Guidelines (Android 11 to 15+), Guidelines:, Rules:

### Community 25 - "Community 25"
Cohesion: 0.50
Nodes (3): artifactType, updatedAt, version

### Community 26 - "Community 26"
Cohesion: 0.50
Nodes (3): artifactType, updatedAt, version

### Community 28 - "Community 28"
Cohesion: 0.67
Nodes (4): 🤝 Agent Handoff Brief, 🤖 AI Operational Guidelines, 📑 Repository State Ledger, Safe Presets & Glassmorphic DPI Overlay

### Community 35 - "Community 35"
Cohesion: 0.12
Nodes (15): Automated Tests, code:mermaid (sequenceDiagram), code:kotlin (val minDimension = minOf(display.width, display.height)), code:kotlin (if (pendingDpiReversions.isNotEmpty()) {), 🟢 `FreeformOverlayService.kt`, Implementation Plan — Display Density (DPI) Overhaul & Safety Guard System (Updated), Key Features, 🟢 `MainActivity.kt` (+7 more)

### Community 36 - "Community 36"
Cohesion: 0.12
Nodes (15): Automated Tests, code:mermaid (sequenceDiagram), code:kotlin (val minDimension = minOf(display.width, display.height)), code:kotlin (if (pendingDpiReversions.isNotEmpty()) {), 🟢 `FreeformOverlayService.kt`, Implementation Plan — Display Density (DPI) Overhaul & Safety Guard System (Updated to 120 DPI presets), Key Features, 🟢 `MainActivity.kt` (+7 more)

### Community 37 - "Community 37"
Cohesion: 0.09
Nodes (28): 1. Compile the App, 📊 1. Dashboard & Diagnostics, 1️⃣ Step 1: Install the APK, 2. Install to Connected Device, 2️⃣ Step 2: Establish the Binder Connection, 🎨 2. Window Customization & Style, 📐 3. Display Safe Area Offsetter, 3️⃣ Step 3: Enable the Accessibility Service (+20 more)

### Community 38 - "Community 38"
Cohesion: 0.14
Nodes (13): 1. Build Verification, 2. Device Verification, 🤖 Automated PR Review Process (For Maintainers), code:bash (graphify update .), code:bash (./gradlew compileDebugKotlin), code:bash (./gradlew installDebug), code:bash (git checkout -b feature/cool-new-overlay), code:bash (git commit -m "feat: added new compact scale toggle down to ) (+5 more)

### Community 39 - "Community 39"
Cohesion: 0.29
Nodes (6): 1. Before You Code (Querying), 2. When You Discover Something New (Writing), AI Knowledge Exchange Hub 🧠🤖, code:block1 (.agents/ai_knowledge/), 📂 Directory Layout, 🔍 How to Use This Hub

### Community 40 - "Community 40"
Cohesion: 0.29
Nodes (6): code:kotlin (// Embed code snippets demonstrating the defensive pattern), Discovery Title (e.g., Samsung DeX Input Focus Interception), Metadata, 🔗 Related Components, ⚠️ The Issue, 🛠️ The Workaround / Solution

### Community 41 - "Community 41"
Cohesion: 0.33
Nodes (5): Android 12 Jetpack Compose Hover Loop Crash, Metadata, 🔗 Related Components, ⚠️ The Issue, 🛠️ The Workaround / Solution

## Knowledge Gaps
- **182 isolated node(s):** `artifactType`, `updatedAt`, `version`, `artifactType`, `updatedAt` (+177 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DragResizeOverlay` connect `Community 2` to `Community 0`, `Community 5`, `Community 6`?**
  _High betweenness centrality (0.099) - this node is a cross-community bridge._
- **Why does `ThemeManager` connect `Community 1` to `Community 17`?**
  _High betweenness centrality (0.090) - this node is a cross-community bridge._
- **Why does `FreeformOverlayService` connect `Community 0` to `Community 6`?**
  _High betweenness centrality (0.081) - this node is a cross-community bridge._
- **What connects `artifactType`, `updatedAt`, `version` to the rest of the system?**
  _193 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.06049382716049383 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.0273972602739726 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.09158249158249158 - nodes in this community are weakly interconnected._