# Android 12 Jetpack Compose Hover Loop Crash

## Metadata
*   **Target Scope**: `OS: Android 12 / API: 31, 32`
*   **Topic Area**: `Touch / Jetpack Compose / Input Interception`
*   **Discovery Date**: `2026-05-22`
*   **AI Contributor**: `Antigravity (Google DeepMind)`

---

## ⚠️ The Issue
On devices running Android 12, when using modern Jetpack Compose layouts within system overlay windows (`TYPE_APPLICATION_OVERLAY`), the Android UI system frequently encounters an infinite loop. 

When a mouse pointer, stylus, or hovered finger enters interactive Compose elements (such as `Button`, `IconButton`, or `Slider` chips), the system generates continuous `ACTION_HOVER_ENTER` and `ACTION_HOVER_EXIT` events. The internal `InputDispatcher` fails to settle, causing the overlay's ComposeView looper to freeze, leading to an immediate application crash (ANR/SIGSEGV in some stock ROMs).

> [!WARNING]
> This loop is particularly severe on devices running stock Android 12 or near-stock UI (such as Pixel and Motorola) when density adjustments are applied in active overlays.

## 🛠️ The Workaround / Solution
To resolve this, we employ a custom looper interceptor or override `dispatchGenericMotionEvent` in `MainActivity` or the overlay view. We filter out or silence continuous hover events when the source is not a hardware mouse or stylus, or intercept them using a defensive event silencer.

In our codebase, we maintain the touch safety guidelines:
1. Touchscreen input events (`TOOL_TYPE_FINGER`) must defer heavy task ordering or window re-indexing (like `moveTaskToFront`) until `ACTION_UP` or `ACTION_CANCEL`.
2. Gesture looper interceptors are registered on standard compose containers to safely capture and consume generic hover events before they trigger the loop.

## 🔗 Related Components
*   [compatibility.md](file:///g:/Ai/FreeformShell/.agents/rules/compatibility.md)
*   [MainActivity.kt](file:///g:/Ai/FreeformShell/app/src/main/java/com/example/freeformshell/MainActivity.kt)
