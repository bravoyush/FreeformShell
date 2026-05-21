# 📑 FreeformShell — Active Repository State Ledger

This ledger is the single source of truth for the active state of **FreeformShell**. All AI agents and human developers must read this document on session entry and update it before session exit.

---

## 🚀 Active Metadata
*   **Current Version:** `v1.2`
*   **Status:** Stable / In Active Evolution
*   **Target API Range:** Android 11 (API level 30) to Android 15+ (API level 35+)
*   **Key Dependencies:** LSPosed `HiddenApiBypass`, Shizuku Binder IPC

---

## 🏛️ Core Architectural Modules

FreeformShell operates as a high-performance system-level window manager overlay on Android. The codebase is structured around several critical core modules:

| Module | Core File | Purpose & Responsibilities |
| :--- | :--- | :--- |
| **Service Entrypoint** | [FreeformOverlayService.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/FreeformOverlayService.kt) | Manages overlay lifecycle, handles configuration changes, coordinates window layers. |
| **Window Interactions** | [DragResizeOverlay.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/DragResizeOverlay.kt) | Core touch-gesture loop, manages glassmorphic title bars, drag-resize strips, and edge snapping. |
| **Tiling Engine** | [WorkspaceManager.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/WorkspaceManager.kt) | Calculates gap-filling, quarter-screen corner quadrants, and columns for multi-window layout tiling. |
| **System Operations** | [ShellExecutor.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/ShellExecutor.kt) | Houses persistent interactive Shizuku shells and background dumpsys activity queries. |
| **Theme & Style** | `ThemeManager` | Manages live app-themed dynamic branding, pill-shrink styling, and dark mode overlays. |
| **Multi-Window Sync** | [SplitResizeHandle.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/SplitResizeHandle.kt) | Coordinates synchronized split-screen resizing and handle snapping pairs. |

---

## 🛡️ Critical Guidelines & Pitfalls (Guardrails)

When making modifications, **you must strictly adhere** to the following Android platform compatibility and behavioral rules:

1. **ART Hidden API Restrictions (Android 14+):**
   * Do not use standard meta-reflection for non-SDK interfaces. 
   * **Rule:** Initialize LSPosed `HiddenApiBypass.addHiddenApiExemptions("L")` in `FreeformOverlayService` to prevent runtime crashes.
2. **Programmatic Focus Routing:**
   * Do not inject simulated taps (`input tap x y`) to focus apps underneath overlays—Android 14's tapjacking/overlay filters will block them.
   * **Rule:** Route active input focus programmatically via `setFocusedRootTask` (falling back to `setFocusedTask` if absent) over Shizuku Binder IPC.
3. **High-Frequency Gesture IPC Throttling:**
   * Avoid calling WindowManager layout updates on every raw touch move event on high-refresh-rate screens (90Hz/120Hz).
   * **Rule:** Rate-limit coordinates to **at most 60Hz (16ms)** using the non-blocking throttled rate limiter.
4. **Touch Safety (InputDispatcher Safety):**
   * **Rule:** For finger inputs (`TOOL_TYPE_FINGER`), defer window re-indexing or task ordering calls (`moveTaskToFront`) until `ACTION_UP` or `ACTION_CANCEL` to prevent system-level `InputDispatcher` cancellations.

---

## 📋 Active Implementation Checklists

### 🟢 Completed Features (v1.2 Overhaul)
- [x] **Zero-Latency Shell Throttling:** Replaced delayed coroutine debouncers with non-blocking 60Hz throttled IPC rate-limitation.
- [x] **LSPosed API Bypass Integration:** Stabilized dynamic ART hidden API exemptions for Android 14+.
- [x] **Direct IPC Focus Routing:** Fully routed touch-focus using reflected root task managers instead of simulated touch tap injections.
- [x] **Smart Tiling Gaps:** Integrated edge snaps, quarterly corners, and non-overlapping tiling column engines (`getAvailableSnapGaps()`).
- [x] **Interaction Handle Isolation:** Hides all inactive resize handles during active drag gestures to optimize coordinate rendering cycles.

### 🟡 In Progress / Planned
- [ ] **Dynamic Display Padding Adjustments:** Adapting margins dynamically when camera cutouts/notches rotate.
- [ ] **Advanced Animation Profiles:** Smooth physics-based spring animations for overlay docking transitions.

---

*Last Updated: 2026-05-21 by Antigravity*
