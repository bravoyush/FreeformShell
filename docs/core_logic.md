# FreeformShell: Core Logic & Architectural Contracts

This document establishes the **Core Logic Contracts** for the FreeformShell window manager. These rules dictate the primary window-management, gesture-responsiveness, and rendering behaviors of the shell. **These core behaviors must remain static and protected against regression across all versions of the application unless the user explicitly requests a change.**

---

## 1. Snapping Exit & Window Scaling Rules
*   **Behavior**: When a user drags a snapped/docked app away from its snapped state to undock it, the window must scale dynamically according to its pre-docked dimensions:
    *   **Scale to 50%**: If the window's original size prior to snapping was larger than **80% of the display dimensions**, it must scale down to exactly **50% of the display width and height** (safeguarding against display overflows).
    *   **Restore Original Size**: If the window's original size prior to snapping was **smaller than 80% of the display dimensions**, it must restore to its exact pre-docked coordinates and size (`preDockedRect`).
*   **Significance**: Prevents screen-flooding overlays, maintains a clean and organized workspace, and honors user-defined freeform sizes.

---

## 2. Jitter-Free Title Bar Pinning (Cursor Anchoring)
*   **Behavior**: During any scale change upon undocking (both from docked-snapped states and maximized states), the window must position itself relative to the pointer:
    *   The touch cursor must stay **perfectly pinned** to the exact same relative horizontal ratio (`touchXRatio`) on the title bar where the user originally grabbed it.
    *   **Formula**:
        $$\text{touchXRatio} = \frac{\text{startX} - \text{preInteractL}}{\text{preInteractW}}$$
        $$\text{winL} = \text{event.rawX} - (\text{winW} \times \text{touchXRatio})$$
*   **Significance**: Completely eliminates horizontal window jumps or visual "detaching" of the title bar from under the user's finger, providing a highly tactile, physical-feeling window-dragging experience.

---

## 3. High-Performance Shell Throttling (Zero-Latency Drag-Resize & 60Hz IPC Throttling)
*   **Behavior**: Window resizing/moving IPC adjustments via Shizuku/ADB shell calls and overlay layout updates must be throttled to prevent resource flooding and CPU thermal throttling:
    *   **Shell Execution Throttling**: Shell execution rate is restricted to a maximum of **60Hz (once every 16ms)** during active dragging/resizing (`applyBounds(false)`).
    *   **Overlay Layout IPC Throttling**: Real-time layout updates (`updateLayouts()`) and splitter movements (`resizeSplit()`) during active touch move events are strictly rate-limited to at most **once per 16ms (60Hz)**. This prevents high-refresh-rate displays (90Hz/120Hz) from flooding the system Window Manager with IPC layout calls.
    *   **Guaranteed End-State**: When the touch gesture completes (`ACTION_UP`), the throttling engine is bypassed (`applyBounds(true)` and final `resizeSplit()`) to instantly apply the absolute final coordinates.
*   **Significance**: Solves the lag/latency between the title bar overlay and the actual app window, and completely eliminates mobile CPU throttling/lag during drag gestures.

---

## 4. Touch-Aware Edge Snap Thresholds
*   **Behavior**: Edge snap thresholds are dynamic and context-aware, depending on whether display boundaries are already occupied:
    *   **Untouched Borders**: Free edges default to a wide, highly sensitive snap threshold of **`100 * density`** (~270px). This makes edge-snapping gestures feel light, responsive, and natural without forcing the window off-screen.
    *   **Touched Borders**: Boundaries occupied by existing snapped windows enforce a narrow snap threshold of **`30 * density`** (~80px).
*   **Significance**: Minimizes accidental snapped overlaps on occupied zones while keeping open boundaries extremely responsive to light gestures.

---

## 5. Smart Snap Corner Restraints & Edge Hold Delays
*   **Behavior**: Snap bounds calculations distinguish strictly between corners and edges:
    *   **Quadrant (1/4th) Snaps**: Corner-quadrant snap previews are **strictly restricted** to display corners (`100 * density` quadrant trigger).
    *   **Fullscreen Top Hold Delay**: Dragging to the top boundary suggests gap-filling column snaps first, and strictly delays the fullscreen snap upgrade by **4 seconds** of continuous hold.
*   **Significance**: Prevents accidental fullscreen snaps and quarter-screen snaps from interfering with standard edge-tiling gestures.

---

## 6. Rendering Integrity & Flicker Prevention
*   **Behavior**: The overlay service protects the screen from transient visual jitter:
    *   **Animator Cache**: Scale and alpha states in `updatePillShrink` are cached to reject redundant animations, preventing continuous scaling jitter/flicker.
    *   **Task Destruction Ignore Cache**: A short-lived concurrent cache (`recentlyClosedTaskIds`) ignores task bounds updates for **3 seconds post-closure**, completely eliminating window double-show ghost overlays when apps close.
*   **Significance**: Delivers premium, visual-grade transitions that feel solid, premium, and native.

---

## 7. Handle Isolation (Hiding Inactive Resize Strips & Splitter Handles)
*   **Behavior**: During *any* resizing gesture (whether manually via a window's border touch strips, or via a paired splitter handle):
    *   **Grabbed Handle Remains Active**: Only the grabbed resize strip or active splitter handle remains visible and responsive.
    *   **All Other Handles Disappear**: All other manual resize strips and all other split resize handles (`SplitResizeHandle`s) across the entire workspace are instantly set to `View.GONE` / hidden.
    *   **Instant Recovery**: Once the resize gesture is complete (`ACTION_UP`/`ACTION_CANCEL`), all resize strips and splitter handles are instantly restored to their default visible states.
*   **Significance**: Maximizes rendering and system IPC speed by omitting redundant layout passes, completely eliminates multi-handle visual clutter on the screen, and prevents accidental double-touches.
