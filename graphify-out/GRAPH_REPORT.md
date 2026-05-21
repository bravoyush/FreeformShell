# Graph Report - FreeformShell  (2026-05-21)

## Corpus Check
- 34 files · ~137,872 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 524 nodes · 765 edges · 33 communities (24 shown, 9 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 16 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ce2d6e1a`
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

## God Nodes (most connected - your core abstractions)
1. `ThemeManager` - 72 edges
2. `DragResizeOverlay` - 52 edges
3. `FreeformOverlayService` - 34 edges
4. `ShellExecutor` - 23 edges
5. `🆕 What's New Since v1.1?` - 19 edges
6. `MainScreen()` - 14 edges
7. `DisplayShell` - 12 edges
8. `FreeformWidgetFactory` - 11 edges
9. `WorkspaceManager` - 11 edges
10. `SplitResizeHandle` - 10 edges

## Surprising Connections (you probably didn't know these)
- `Dynamic Screen-to-View Occlusion Clipping` --rationale_for--> `DragResizeOverlay`  [EXTRACTED]
  docs/architecture_evolution_log.md → app/src/main/java/com/example/freeformshell/DragResizeOverlay.kt
- `Dynamic Tiling Grid Engine` --rationale_for--> `DragResizeOverlay`  [EXTRACTED]
  docs/architecture_evolution_log.md → app/src/main/java/com/example/freeformshell/DragResizeOverlay.kt
- `Android 14 Hidden API Exemption` --rationale_for--> `FreeformOverlayService`  [EXTRACTED]
  docs/architecture_evolution_log.md → app/src/main/java/com/example/freeformshell/FreeformOverlayService.kt
- `High-Performance Shell Throttling` --rationale_for--> `ShellExecutor`  [EXTRACTED]
  docs/core_logic.md → app/src/main/java/com/example/freeformshell/ShellExecutor.kt
- `Programmatic Active Focus Routing` --rationale_for--> `ShellExecutor`  [EXTRACTED]
  docs/architecture_evolution_log.md → app/src/main/java/com/example/freeformshell/ShellExecutor.kt

## Hyperedges (group relationships)
- **Window Drag and Resize Interaction Loop** — freeformshell_dragresizeoverlay_dragresizeoverlay, freeformshell_freeformoverlayservice_freeformoverlayservice, freeformshell_shellexecutor_shellexecutor [INFERRED 0.95]
- **Dynamic Tiling and Docking Grid System** — freeformshell_dragresizeoverlay_dragresizeoverlay, freeformshell_workspacemanager_workspacemanager, core_logic_smart_snap_corner_restraints, core_logic_touch_aware_edge_snap_thresholds [INFERRED 0.95]

## Communities (33 total, 9 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.06
Nodes (25): Background Thread Concurrency Lock & Drag Sync Bypass, Dynamic Screen-to-View Occlusion Clipping, Persistent SharedPreferences Blacklist, Rendering Integrity & Flicker Prevention, DisplayShell, ensureForceCloseLoaded(), ensureLoaded(), FreeformOverlayService (+17 more)

### Community 2 - "Community 2"
Cohesion: 0.10
Nodes (12): State-Aware Edge Hold Timer, Interaction Window Hiding, Programmatic Active Focus Routing, Handle Isolation, High-Performance Shell Throttling, Jitter-Free Title Bar Pinning, Smart Snap Corner Restraints & Edge Hold Delays, Snapping Exit & Window Scaling Rules (+4 more)

### Community 3 - "Community 3"
Cohesion: 0.12
Nodes (22): Android 14 Hidden API Exemption, Dynamic Tiling Grid Engine, AppIcon(), AppInfo, AppSettingsScreen(), BlacklistScreen(), CompatibilityScreen(), CustomizationScreen() (+14 more)

### Community 4 - "Community 4"
Cohesion: 0.23
Nodes (4): Persistent Interactive Shell Session, CommandResult, onDisconnected(), ShellExecutor

### Community 5 - "Community 5"
Cohesion: 0.16
Nodes (3): FreeformWidgetFactory, FreeformWidgetService, groupToJson()

### Community 6 - "Community 6"
Cohesion: 0.22
Nodes (8): Containers Dump (dumpsys activity containers), Recents Dump (dumpsys activity recents), Tasks Dump (dumpsys activity tasks), Windows Dump (dumpsys window), AppTask, CombinedTaskState, TaskBounds, TaskManager

### Community 8 - "Community 8"
Cohesion: 0.05
Nodes (39): 10. Tiled Layout Swap, 11. Launcher & Dock Avoidance, 12. Drag & Resize Tint Overlay, 13. Title Bar Opacity Control, 14. Pill Shrink Style Picker, 15. Real-time Resize Toggle, 16. Instant Shell Resizing (Disable System Animations), 17. Force Close per App (+31 more)

### Community 9 - "Community 9"
Cohesion: 0.50
Nodes (3): artifactType, updatedAt, version

### Community 10 - "Community 10"
Cohesion: 0.50
Nodes (3): artifactType, updatedAt, version

### Community 16 - "Community 16"
Cohesion: 0.06
Nodes (32): 1. Persistent Interactive Shell Session vs. Spawning One-Off Shell Processes, 2. High-Performance Interaction Overlay Hiding (5 Overlays down to 1), 3. Edge-Snapping Hold Delay Timers vs. Instant Snapping, 4. Background Thread Concurrency Lock & Intelligent Drag Sync Bypass, 5. Persistent SharedPreferences Blacklist & Set Exact-Matching, 6. Dynamic Screen-to-View Occlusion Clipping & Race-Condition Bypass, 7. Corner-Only Quadrants, Smart Gap-Filling Snaps, and Pill Jitter Fixes, code:kotlin (// OLD CODE METHOD) (+24 more)

### Community 17 - "Community 17"
Cohesion: 0.10
Nodes (19): 1. Advanced Window Snapping & Quadrants, 📺 1. Windows (Dashboard), 2. Multi-Display & Safe Area Dock Margins, 🎨 2. Theme (Customization), 3. Dynamic App-Themed Branding, 🛡️ 3. Safe Area, 🚫 4. Blacklist, 4. Interactive Split Snapping & Splitter Handles (+11 more)

### Community 18 - "Community 18"
Cohesion: 0.13
Nodes (14): 1. Advanced Window Snapping & Quadrants, 2. Live Dynamic Color Branding, code:`carousel (![Snapping Left Split](/absolute/path/to/artifacts/snap_left), 🛠️ Configuration Settings, ✨ Core Features, 🚀 Getting Started, ❓ Overlays Not Showing Above Specific Apps, 📋 Prerequisites (+6 more)

### Community 19 - "Community 19"
Cohesion: 0.15
Nodes (12): 1. Dynamic Android 14 Hidden API Bypass via LSPosed vs. Legacy Double Reflection, 2. Programmatic Active Focus Routing via setFocusedRootTask vs. Simulated Touch Inject Taps, Architecture Evolution Log — FreeformShell, code:kotlin (// OLD REFLECTION METHOD), code:kotlin (// NEW METHOD), code:kotlin (// OLD TAP INJECTION METHOD), code:kotlin (// NEW DIRECT IPC FOCUS METHOD), **New Architecture** (+4 more)

### Community 20 - "Community 20"
Cohesion: 0.18
Nodes (10): 1. Dragging Windows Is Now Buttery Smooth, 2. Window Dragging Uses Less of Your Phone's Resources, 3. Smarter Window Snapping That Doesn't Get in the Way, 4. Background Updates Don't Fight Your Finger Anymore, 5. App Block List Now Saves Across Restarts, 6. Overlays Clip Correctly Over Floating Apps & Disappear Instantly When Apps Close, 7. Snapping, Title Bar Pinning & General Polish, FreeformShell v1.1 — What's New? 🎉 (+2 more)

### Community 21 - "Community 21"
Cohesion: 0.20
Nodes (9): 1. System Architecture Overview, 2. Core Feature Guides, 3. Settings & Preferences Reference, 🎛️ A. Custom Title Bars & Window Controls, 📐 B. Gesture Resize Strips (Edge Grabs), 🧲 C. Aero Snapping & Synced Layout Pairs, code:mermaid (graph TD), 📁 D. Workspace Management System (New in v1) (+1 more)

### Community 22 - "Community 22"
Cohesion: 0.22
Nodes (8): 1. Snapping Exit & Window Scaling Rules, 2. Jitter-Free Title Bar Pinning (Cursor Anchoring), 3. High-Performance Shell Throttling (Zero-Latency Drag-Resize & 60Hz IPC Throttling), 4. Touch-Aware Edge Snap Thresholds, 5. Smart Snap Corner Restraints & Edge Hold Delays, 6. Rendering Integrity & Flicker Prevention, 7. Handle Isolation (Hiding Inactive Resize Strips & Splitter Handles), FreeformShell: Core Logic & Architectural Contracts

### Community 26 - "Community 26"
Cohesion: 0.40
Nodes (4): Android Version Compatibility (Android 11 to 15+), Android Version Compatibility Guidelines (Android 11 to 15+), Guidelines:, Rules:

### Community 27 - "Community 27"
Cohesion: 0.18
Nodes (10): -----------------------------------------------, -----------------------------------------------, ⚡ Core Engine, 🎨 Customization, ⚠️ Don't Touch These, 🚀 Freeform Beta — Experimental Update, MESSAGE 1 — paste this first, MESSAGE 2 — paste this second (+2 more)

### Community 29 - "Community 29"
Cohesion: 0.25
Nodes (7): 📋 Active Implementation Checklists, 🚀 Active Metadata, 🟢 Completed Features (v1.2 Overhaul), 🏛️ Core Architectural Modules, 🛡️ Critical Guidelines & Pitfalls (Guardrails), 📑 FreeformShell — Active Repository State Ledger, 🟡 In Progress / Planned

### Community 30 - "Community 30"
Cohesion: 0.33
Nodes (5): 1. The Entry Protocol (Mandatory First Step), 2. Token Discipline & Context Optimization, 3. Planning & Checkpointing, 4. The Exit Protocol (Mandatory Final Step), AI Operational Guidelines & Token Conservation Rules

### Community 31 - "Community 31"
Cohesion: 0.40
Nodes (4): 🤝 Agent Handoff Brief, ⚡ Context Snapshot, 🚀 How to Bootstrapping This Session (Token Saving Entry), 🎯 Next Tasks On the Horizon

## Knowledge Gaps
- **138 isolated node(s):** `FixDef`, `artifactType`, `updatedAt`, `version`, `artifactType` (+133 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DragResizeOverlay` connect `Community 2` to `Community 0`, `Community 3`?**
  _High betweenness centrality (0.187) - this node is a cross-community bridge._
- **Why does `ThemeManager` connect `Community 1` to `Community 7`?**
  _High betweenness centrality (0.131) - this node is a cross-community bridge._
- **Why does `FreeformOverlayService` connect `Community 0` to `Community 3`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **What connects `FixDef`, `artifactType`, `updatedAt` to the rest of the system?**
  _147 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05649122807017544 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.027777777777777776 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.09653092006033183 - nodes in this community are weakly interconnected._