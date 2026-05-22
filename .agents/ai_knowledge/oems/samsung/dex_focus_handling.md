# Samsung DeX Focus Routing & Knox Overlay Restrictions

## Metadata
*   **Target Scope**: `OEM: Samsung / OS: Android 12 to 15+ / API: 31 to 35+`
*   **Topic Area**: `Focus Routing / Multi-Window / Knox Security / Shell Fallback`
*   **Discovery Date**: `2026-05-22`
*   **AI Contributor**: `Antigravity (Google DeepMind)`

---

## ⚠️ The Issue
Samsung devices running stock OneUI or active **Samsung DeX (Desktop Experience)** enforce highly aggressive proprietary security layers (backed by Samsung Knox):

1.  **Reflection Restrictions**: Samsung ROMs alter the signatures of internal Android window management APIs (e.g. `setFocusedRootTask` is heavily restricted or renamed).
2.  **Knox Overlay Interception**: High-performance Binder transactions initiated via Shizuku/reflection to re-order windows or adjust densities are sometimes silently blocked by Knox security context managers on enterprise or locked-down models.
3.  **Task Focus Loops**: In DeX mode, secondary monitors run separate virtual desktop displays. Forcing a focus update on a window in a secondary display can trigger a recursive focus loop, causing the standard soft-keyboard or mouse router to crash.

> [!CAUTION]
> Hardcoded reflective calls to `setFocusedRootTask` will crash at runtime on OneUI 6.0+ (Android 14+) due to these OEM signature deviations.

## 🛠️ The Workaround / Solution
We resolve these Samsung-specific hurdles by implementing a **Defensive Signature Fallback Strategy** combined with a **Binder-to-ADB-Shell Fallback Mechanism**:

1.  **Defensive Reflection Fallback**:
    We check for the availability of `setFocusedRootTask` (prevalent in stock Android 12+). If it's missing or blocked, we immediately catch the error, clear state caches, and fall back to invoking `setFocusedTask`.
2.  **ADB Shell Delegation**:
    If direct Binder access is blocked by Knox or Samsung policies, the service gracefully catches the transaction error, logs the context, and routes the action through our ADB command bridge using:
    ```kotlin
    ShellExecutor.triggerShellInteraction("wm density ...")
    ```
    This delegates the task to persistent shell authorization, bypassing Knox application-level blocks.

## 🔗 Related Components
*   [compatibility.md](file:///g:/Ai/FreeformShell/.agents/rules/compatibility.md)
*   [MainActivity.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/MainActivity.kt)
*   [FreeformOverlayService.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/FreeformOverlayService.kt)
