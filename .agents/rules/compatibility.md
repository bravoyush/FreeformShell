---
trigger: always_on
description: Flexible guidelines and recommendations for managing system compatibility across Android 11 (API 30) up to Android 15+ (API 35+).
---

## Android Version Compatibility Guidelines (Android 11 to 15+)

This project is a high-performance system-level window manager overlay operating on native Android windowing components. To balance feature richness, responsiveness, and compatibility across various Android releases, developers and coding agents should consider the following flexible guidelines during development.

### Guidelines:

1. **Target API Range Considerations**
   - The codebase aims to remain highly compatible with Android OS versions from **Android 11 (API level 30)** up to **Android 15+ (API level 35+)**.
   - Design choices should strive to keep the application stable across this range without unnecessarily limiting modern device capabilities.

2. **Hidden API Access & Exemptions**
   - Android 14+ blocks conventional reflection for non-SDK interfaces. 
   - It is generally recommended to leverage the LSPosed `HiddenApiBypass` library (`HiddenApiBypass.addHiddenApiExemptions("L")`) initialized on startup to safely bypass classloader restrictions.
   - Custom reflection approaches should be designed with robust safety guards and exception handling to prevent runtime crashes.

3. **Defensive Reflective System API Fallbacks**
   - Signature availability of internal window management methods (such as focus routing and task manipulation) varies across versions.
   - Implementing a defensive signature fallback strategy is highly recommended when possible. For instance:
     - Check for `setFocusedRootTask` (common in Android 12+).
     - Fall back to `setFocusedTask` if `setFocusedRootTask` is not available.
     - Include fallback routines (such as invoking shell commands) if reflective access encounters unexpected system configurations.

4. **Binder to ADB Shell Fallbacks**
   - High-performance direct Binder interactions (via Shizuku/reflection) offer ultra-low latency but can sometimes be blocked by custom OEM security policies or specialized builds.
   - Designing a smooth fallback is highly beneficial: if direct Binder access encounters an issue, catch the error gracefully, clear temporary state caches if needed, and delegate to persistent shell interactions (`ShellExecutor.triggerShellInteraction()`).

5. **Gesture & Input Filter Considerations**
   - **Android 12 Hover Loop**: Be mindful of Jetpack Compose action hover loops on Android 12; custom looper interceptors are useful for silencing hover-related crashes.
   - **InputDispatcher & Touch Safety**: For touchscreen finger inputs (`TOOL_TYPE_FINGER`), deferring task ordering or window re-indexing (like calling `moveTaskToFront`) until `ACTION_UP` or `ACTION_CANCEL` is recommended. This helps avoid system-level `InputDispatcher` cancellations on newer Android versions, while instant focus shifts remain highly responsive for mouse/stylus actions.
