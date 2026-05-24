# Graph Report - FreeformShell  (2026-05-24)

## Corpus Check
- 51 files · ~153,702 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 819 nodes · 1208 edges · 53 communities (39 shown, 14 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 25 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `733d07b7`
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
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 52|Community 52]]

## God Nodes (most connected - your core abstractions)
1. `ThemeManager` - 85 edges
2. `DragResizeOverlay` - 61 edges
3. `FreeformOverlayService` - 51 edges
4. `ShellExecutor` - 25 edges
5. `CompatibilityManager` - 21 edges
6. `🆕 What's New Since v1.1?` - 19 edges
7. `MainScreen()` - 17 edges
8. `TaskManager` - 14 edges
9. `FreeformShell 🚀` - 14 edges
10. `DisplayShell` - 13 edges

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

## Communities (53 total, 14 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.06
Nodes (29): Background Thread Concurrency Lock & Drag Sync Bypass, Dynamic Screen-to-View Occlusion Clipping, Persistent SharedPreferences Blacklist, Rendering Integrity & Flicker Prevention, DisplayShell, ensureForceCloseLoaded(), ensureLoaded(), FreeformOverlayService (+21 more)

### Community 2 - "Community 2"
Cohesion: 0.07
Nodes (14): State-Aware Edge Hold Timer, Interaction Window Hiding, Handle Isolation, Jitter-Free Title Bar Pinning, Smart Snap Corner Restraints & Edge Hold Delays, Snapping Exit & Window Scaling Rules, Touch-Aware Edge Snap Thresholds, dismissActiveSnapMenu() (+6 more)

### Community 3 - "Community 3"
Cohesion: 0.04
Nodes (48): 1. Dynamic Android 14 Hidden API Bypass via LSPosed vs. Legacy Double Reflection, 1. Persistent Interactive Shell Session vs. Spawning One-Off Shell Processes, 2. High-Performance Interaction Overlay Hiding (5 Overlays down to 1), 2. Programmatic Active Focus Routing via setFocusedRootTask vs. Simulated Touch Inject Taps, 3. Consolidating Multi-Window Overlays into a Single Full-Screen Cutout Overlay (Android 14+ Bypass), 3. Edge-Snapping Hold Delay Timers vs. Instant Snapping, 4. Background Thread Concurrency Lock & Intelligent Drag Sync Bypass, 5. Persistent SharedPreferences Blacklist & Set Exact-Matching (+40 more)

### Community 4 - "Community 4"
Cohesion: 0.05
Nodes (39): 10. Tiled Layout Swap, 11. Launcher & Dock Avoidance, 12. Drag & Resize Tint Overlay, 13. Title Bar Opacity Control, 14. Pill Shrink Style Picker, 15. Real-time Resize Toggle, 16. Instant Shell Resizing (Disable System Animations), 17. Force Close per App (+31 more)

### Community 5 - "Community 5"
Cohesion: 0.12
Nodes (24): Android 14 Hidden API Exemption, Dynamic Tiling Grid Engine, AppIcon(), AppInfo, AppSettingsScreen(), BlacklistScreen(), CompatibilityScreen(), CustomizationScreen() (+16 more)

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (34): 1. Compile the App, 📊 1. Dashboard & Diagnostics, 1️⃣ Step 1: Install the APK, 2. Install to Connected Device, 2️⃣ Step 2: Establish the Binder Connection, 🎨 2. Window Customization & Style, 📐 3. Display Safe Area Offsetter, 3️⃣ Step 3: Enable the Accessibility Service (+26 more)

### Community 7 - "Community 7"
Cohesion: 0.09
Nodes (9): Containers Dump (dumpsys activity containers), Recents Dump (dumpsys activity recents), Tasks Dump (dumpsys activity tasks), Windows Dump (dumpsys window), FreeformTaskManagerWidgetFactory, FreeformTaskManagerWidgetService, AppTask, CombinedTaskState (+1 more)

### Community 8 - "Community 8"
Cohesion: 0.21
Nodes (7): Persistent Interactive Shell Session, Programmatic Active Focus Routing, High-Performance Shell Throttling, CommandResult, onConnected(), onDisconnected(), ShellExecutor

### Community 9 - "Community 9"
Cohesion: 0.10
Nodes (19): 1. Advanced Window Snapping & Quadrants, 📺 1. Windows (Dashboard), 2. Multi-Display & Safe Area Dock Margins, 🎨 2. Theme (Customization), 3. Dynamic App-Themed Branding, 🛡️ 3. Safe Area, 🚫 4. Blacklist, 4. Interactive Split Snapping & Splitter Handles (+11 more)

### Community 10 - "Community 10"
Cohesion: 0.12
Nodes (15): Automated Tests, code:mermaid (sequenceDiagram), code:kotlin (val minDimension = minOf(display.width, display.height)), code:kotlin (if (pendingDpiReversions.isNotEmpty()) {), 🟢 `FreeformOverlayService.kt`, Implementation Plan — Display Density (DPI) Overhaul & Safety Guard System (Updated), Key Features, 🟢 `MainActivity.kt` (+7 more)

### Community 11 - "Community 11"
Cohesion: 0.12
Nodes (15): Automated Tests, code:mermaid (sequenceDiagram), code:kotlin (val minDimension = minOf(display.width, display.height)), code:kotlin (if (pendingDpiReversions.isNotEmpty()) {), 🟢 `FreeformOverlayService.kt`, Implementation Plan — Display Density (DPI) Overhaul & Safety Guard System (Updated to 120 DPI presets), Key Features, 🟢 `MainActivity.kt` (+7 more)

### Community 12 - "Community 12"
Cohesion: 0.13
Nodes (14): 1. Advanced Window Snapping & Quadrants, 2. Live Dynamic Color Branding, code:`carousel (![Snapping Left Split](/absolute/path/to/artifacts/snap_left), 🛠️ Configuration Settings, ✨ Core Features, 🚀 Getting Started, ❓ Overlays Not Showing Above Specific Apps, 📋 Prerequisites (+6 more)

### Community 13 - "Community 13"
Cohesion: 0.14
Nodes (3): CompatibilityManager, FixDef, ResizeStyle

### Community 14 - "Community 14"
Cohesion: 0.14
Nodes (13): 1. Build Verification, 2. Device Verification, 🤖 Automated PR Review Process (For Maintainers), code:bash (graphify update .), code:bash (./gradlew compileDebugKotlin), code:bash (./gradlew installDebug), code:bash (git checkout -b feature/cool-new-overlay), code:bash (git commit -m "feat: added new compact scale toggle down to ) (+5 more)

### Community 15 - "Community 15"
Cohesion: 0.16
Nodes (3): FreeformWidgetFactory, FreeformWidgetService, groupToJson()

### Community 17 - "Community 17"
Cohesion: 0.17
Nodes (11): 1. LEDGER.md Template, 2. HANDOFF.md Template, code:markdown (# 📑 [Repository Name] — Active Repository State Ledger), code:markdown (# 🤝 Agent Handoff Brief), 📑 Core Document Templates, 🚀 Execution Flow (Step-by-Step), Phase 1: The Entry Protocol (Bootstrap), Phase 2: Token-Efficient Development (+3 more)

### Community 18 - "Community 18"
Cohesion: 0.18
Nodes (10): -----------------------------------------------, -----------------------------------------------, ⚡ Core Engine, 🎨 Customization, ⚠️ Don't Touch These, 🚀 Freeform Beta — Experimental Update, MESSAGE 1 — paste this first, MESSAGE 2 — paste this second (+2 more)

### Community 19 - "Community 19"
Cohesion: 0.18
Nodes (10): 1. Dragging Windows Is Now Buttery Smooth, 2. Window Dragging Uses Less of Your Phone's Resources, 3. Smarter Window Snapping That Doesn't Get in the Way, 4. Background Updates Don't Fight Your Finger Anymore, 5. App Block List Now Saves Across Restarts, 6. Overlays Clip Correctly Over Floating Apps & Disappear Instantly When Apps Close, 7. Snapping, Title Bar Pinning & General Polish, FreeformShell v1.1 — What's New? 🎉 (+2 more)

### Community 20 - "Community 20"
Cohesion: 0.20
Nodes (9): 1. System Architecture Overview, 2. Core Feature Guides, 3. Settings & Preferences Reference, 🎛️ A. Custom Title Bars & Window Controls, 📐 B. Gesture Resize Strips (Edge Grabs), 🧲 C. Aero Snapping & Synced Layout Pairs, code:mermaid (graph TD), 📁 D. Workspace Management System (New in v1) (+1 more)

### Community 21 - "Community 21"
Cohesion: 0.22
Nodes (8): 1. Snapping Exit & Window Scaling Rules, 2. Jitter-Free Title Bar Pinning (Cursor Anchoring), 3. High-Performance Shell Throttling (Zero-Latency Drag-Resize & 60Hz IPC Throttling), 4. Touch-Aware Edge Snap Thresholds, 5. Smart Snap Corner Restraints & Edge Hold Delays, 6. Rendering Integrity & Flicker Prevention, 7. Handle Isolation (Hiding Inactive Resize Strips & Splitter Handles), FreeformShell: Core Logic & Architectural Contracts

### Community 23 - "Community 23"
Cohesion: 0.25
Nodes (7): 📋 Active Implementation Checklists, 🚀 Active Metadata, 🟢 Completed Features (v1.2 Overhaul), 🏛️ Core Architectural Modules, 🛡️ Critical Guidelines & Pitfalls (Guardrails), 📑 FreeformShell — Active Repository State Ledger, 🟡 In Progress / Planned

### Community 25 - "Community 25"
Cohesion: 0.25
Nodes (7): **Antigravity (AI Co-Developer)**, **Ayush (Admin / Owner / Lead Architect)**, 📜 Coding & Attribution Guidelines for AIs, **Core Maintainers & Approved Contributors**, 👥 FreeformShell Project Roles & Team Roster, 🛠️ The Team & Contributors, 👑 The Workspace Authority

### Community 26 - "Community 26"
Cohesion: 0.29
Nodes (6): code:kotlin (ShellExecutor.triggerShellInteraction("wm density ...")), Metadata, 🔗 Related Components, Samsung DeX Focus Routing & Knox Overlay Restrictions, ⚠️ The Issue, 🛠️ The Workaround / Solution

### Community 27 - "Community 27"
Cohesion: 0.29
Nodes (6): 🤝 Agent Handoff Brief, ⚡ Context Snapshot, 🚀 How to Bootstrap This Session (Token Saving Entry), 🚀 How to Bootstrapping This Session (Token Saving Entry), 🎯 Next Tasks On the Horizon, 👥 Project Team Roles & Roster

### Community 28 - "Community 28"
Cohesion: 0.29
Nodes (6): 1. Before You Code (Querying), 2. When You Discover Something New (Writing), AI Knowledge Exchange Hub 🧠🤖, code:block1 (.agents/ai_knowledge/), 📂 Directory Layout, 🔍 How to Use This Hub

### Community 29 - "Community 29"
Cohesion: 0.29
Nodes (6): code:kotlin (// Embed code snippets demonstrating the defensive pattern), Discovery Title (e.g., Samsung DeX Input Focus Interception), Metadata, 🔗 Related Components, ⚠️ The Issue, 🛠️ The Workaround / Solution

### Community 30 - "Community 30"
Cohesion: 0.40
Nodes (6): 📱 Android Compatibility Guidelines, 📢 Discord Announcement, 📘 Experimental Beta Guide, Programmatic Focus Routing, LSPosed HiddenApiBypass, InputDispatcher Touch Safety

### Community 31 - "Community 31"
Cohesion: 0.33
Nodes (5): Android 12 Jetpack Compose Hover Loop Crash, Metadata, 🔗 Related Components, ⚠️ The Issue, 🛠️ The Workaround / Solution

### Community 32 - "Community 32"
Cohesion: 0.33
Nodes (5): 1. The Entry Protocol (Mandatory First Step), 2. Token Discipline & Context Optimization, 3. Planning & Checkpointing, 4. The Exit Protocol (Mandatory Final Step), AI Operational Guidelines & Token Conservation Rules

### Community 33 - "Community 33"
Cohesion: 0.33
Nodes (5): 🎯 Capability Overview, code:bash (python .agents/skills/sync_ledger/sync_ledger.py --message "), 🚀 Execution Instructions, Options:, 🛠️ Skill: Automated Ledger & Handoff Synchronization

### Community 34 - "Community 34"
Cohesion: 0.67
Nodes (5): generate_handoff(), get_git_changes(), main(), run_command(), update_ledger()

### Community 39 - "Community 39"
Cohesion: 0.40
Nodes (4): Android Version Compatibility (Android 11 to 15+), Android Version Compatibility Guidelines (Android 11 to 15+), Guidelines:, Rules:

### Community 40 - "Community 40"
Cohesion: 0.40
Nodes (4): code:bash (python .agents/skills/sync_ledger/sync_ledger.py --message "), Entry Phase (Bootstrapping), Exit Phase (Handoff & Ledger Auto-Sync), Workflow: handoff-andp

### Community 41 - "Community 41"
Cohesion: 0.50
Nodes (3): artifactType, updatedAt, version

### Community 42 - "Community 42"
Cohesion: 0.50
Nodes (3): artifactType, updatedAt, version

### Community 45 - "Community 45"
Cohesion: 0.67
Nodes (4): 🤝 Agent Handoff Brief, 🤖 AI Operational Guidelines, 📑 Repository State Ledger, Safe Presets & Glassmorphic DPI Overlay

## Knowledge Gaps
- **207 isolated node(s):** `ResizeStyle`, `artifactType`, `updatedAt`, `version`, `artifactType` (+202 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ThemeManager` connect `Community 1` to `Community 0`?**
  _High betweenness centrality (0.080) - this node is a cross-community bridge._
- **Why does `DragResizeOverlay` connect `Community 2` to `Community 0`, `Community 8`, `Community 5`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **Why does `FreeformOverlayService` connect `Community 0` to `Community 5`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **What connects `ResizeStyle`, `artifactType`, `updatedAt` to the rest of the system?**
  _218 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05531914893617021 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.023529411764705882 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.07155399473222125 - nodes in this community are weakly interconnected._