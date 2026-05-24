package com.example.freeformshell

import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * CompatibilityManager — single source of truth for per-Android-version workarounds.
 *
 * Each fix has:
 *  - A unique string ID (the SharedPreferences key suffix, stored as "compat_<id>")
 *  - A human-readable label and description used by the Settings UI
 *  - A smart default computed from the current SDK_INT
 *  - A consumer-facing helper function (e.g. isAndroid12VisibilityBypassEnabled)
 *
 * Adding a new fix: add a FixDef entry to ALL_FIXES, then add a named helper function.
 */
object CompatibilityManager {

    const val COMPAT_ISOLATED_TITLEBAR_LINKING = "compat_isolated_titlebar_linking"
    const val COMPAT_PRECISE_TOUCH_REGION = "compat_precise_touch_region"
    const val COMPAT_HYBRID_LEASH_MINIMIZATION = "compat_hybrid_leash_minimization"
    const val COMPAT_BORDER_FADE_ON_RESIZE = "compat_border_fade_on_resize"
    const val COMPAT_RESOLUTION_OVERRIDE = "compat_resolution_override"
    const val COMPAT_REFRESH_RATE_OVERRIDE = "compat_refresh_rate_override"
    const val COMPAT_HYBRID_FRAME_PACING = "compat_android13_14_hybrid_pacing"

    // ── Fix Definitions ────────────────────────────────────────────────────────

    data class FixDef(
        val id: String,
        val label: String,
        val description: String,
        /** Which Android version this fix targets, as a user-readable string. */
        val affectsVersionLabel: String,
        /** Returns true if this fix should be ON by default on the running device. */
        val smartDefault: () -> Boolean
    )

    val ALL_FIXES: List<FixDef> = listOf(

        FixDef(
            id = COMPAT_RESOLUTION_OVERRIDE,
            label = "Display: Resolution Customization",
            description = "Allows changing the resolution of the built-in or connected displays. Uses ShellExecutor commands to override WxH values safely. Deselect to hide these controls.",
            affectsVersionLabel = "Android 11+  (API ≥ 30)",
            smartDefault = { Build.VERSION.SDK_INT >= 30 }
        ),

        FixDef(
            id = COMPAT_REFRESH_RATE_OVERRIDE,
            label = "Display: Refresh Rate Customization",
            description = "Allows changing the refresh rate of connected displays natively. Uses WindowManager.LayoutParams preferredDisplayModeId to adjust active refresh rates. Deselect to hide these controls.",
            affectsVersionLabel = "Android 11+  (API ≥ 30)",
            smartDefault = { Build.VERSION.SDK_INT >= 30 }
        ),

        FixDef(
            id = COMPAT_ISOLATED_TITLEBAR_LINKING,
            label = "Android 14: Isolated Titlebar Surface Linking",
            description = "Reparents the titlebar view directly to the application root task surface control to eliminate window movement latency.",
            affectsVersionLabel = "Android 14+ (API ≥ 34)",
            smartDefault = { Build.VERSION.SDK_INT >= 34 }
        ),

        FixDef(
            id = COMPAT_PRECISE_TOUCH_REGION,
            label = "Android 14: Precise Touch Region Cutout",
            description = "Carves out the center application area from our full-frame overlay's touchable region. Allows touches to pass through directly to underlying application windows while keeping borders/titlebars touchable.",
            affectsVersionLabel = "Android 13+ (API ≥ 33)",
            smartDefault = { Build.VERSION.SDK_INT >= 33 }
        ),

        FixDef(
            id = COMPAT_HYBRID_FRAME_PACING,
            label = "Android 13+: Hybrid Frame-Pacing Pipeline",
            description = "Combines high-frequency GPU buffer-stretching during movement with a single crisp view hierarchy pass on gesture release. Eliminates real-time resizing jitter on Android 13 and 14 BLAST architectures.",
            affectsVersionLabel = "Android 13+ (API ≥ 33)",
            smartDefault = { Build.VERSION.SDK_INT >= 33 }
        ),

        FixDef(
            id = "android12_visibility_bypass",
            label = "Android 12: Task Visibility Bypass",
            description = "On Android 12, freeform task visibility is misreported as false even when windows are clearly visible. This fix forces them to be treated as visible.",
            affectsVersionLabel = "Android 12 / 12L  (API ≤ 32)",
            smartDefault = { Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 }
        ),

        FixDef(
            id = "finger_defer_focus",
            label = "Touch: Defer Window Focus to Finger Lift",
            description = "Calling moveTaskToFront during ACTION_DOWN for a touchscreen finger drag causes InputDispatcher to fire ACTION_CANCEL, which kills the whole drag gesture. This fix defers the focus call to ACTION_UP.",
            affectsVersionLabel = "Android 12+  (API ≥ 31) — touch input only",
            smartDefault = { true } // Safe and beneficial on all versions
        ),

        FixDef(
            id = "hover_phantom_exit_check",
            label = "Hover: Phantom Exit Position Check",
            description = "On some devices ACTION_HOVER_EXIT fires while the pointer is still physically inside the view. This fix validates the pointer's screen coordinates before accepting a hover-exit event.",
            affectsVersionLabel = "Android 12  (API 31–32) — mouse / stylus input",
            smartDefault = { true } // Harmless on all versions
        ),

        FixDef(
            id = "hidden_api_bypass",
            label = "Hidden API Bypass (LSPosed)",
            description = "Android 9+ restricts reflection on internal APIs. This uses LSPosed HiddenApiBypass to unlock internal window management methods. Disable only if your ROM has its own bypass mechanism or blocks this library.",
            affectsVersionLabel = "Android 9+  (API ≥ 28)",
            smartDefault = { Build.VERSION.SDK_INT >= 28 }
        ),

        FixDef(
            id = "debug_touch_regions",
            label = "Touch: Visual Region Debugger",
            description = "Draws the actual computed, transparent touchable regions of the window overlays on screen using a translucent green overlay. Extremely useful for verifying tapjacking cutout alignment.",
            affectsVersionLabel = "All Versions",
            smartDefault = { false }
        ),
        
        FixDef(
            id = COMPAT_HYBRID_LEASH_MINIMIZATION,
            label = "Android 12: Compositor Leash Minimization & Focus Guard",
            description = "An Android 12 compatibility workaround that hides and shifts task windows offscreen natively to minimize them. Gated strictly to avoid issues on Android 14+. IMPORTANT: After service startup, you must click/interact with desktop background windows once to initialize their custom title bars.",
            affectsVersionLabel = "Android 12 / 12L (API ≤ 32)",
            smartDefault = { Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 }
        ),
        
        FixDef(
            id = COMPAT_BORDER_FADE_ON_RESIZE,
            label = "Android 12: Border Fading & WMS Sync",
            description = "Fades overlay borders and corner handles to 0 opacity during active resize gestures and WMS sync to avoid visual desync. Restricts this transition strictly to Android 12 and below, keeping borders static/normal on Android 14.",
            affectsVersionLabel = "Android 12 / 12L (API ≤ 32)",
            smartDefault = { Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 }
        ),

        FixDef(
            id = "strict_wms_sync",
            label = "Strict WMS Synchronization",
            description = "Tightens the synchronization between the overlay and the system's Window Manager. Reduces 'bounce' and 'rubber-banding' effects at the end of resize/drag gestures.",
            affectsVersionLabel = "All Versions",
            smartDefault = { true }
        )
    )

    // ── Public Consumer API ────────────────────────────────────────────────────

    /**
     * Generic getter — resolves the stored preference or falls back to the
     * smart default for the given fix ID.
     */
    fun isFixEnabled(context: Context, fixId: String): Boolean {
        val def = ALL_FIXES.find { it.id == fixId } ?: return false
        return ThemeManager.getCompFix(context, fixId, def.smartDefault())
    }

    // Named helpers used by consuming code — keeps call sites readable.



    fun isDebugTouchRegionsEnabled(context: Context): Boolean =
        isFixEnabled(context, "debug_touch_regions")

    fun isAndroid12VisibilityBypassEnabled(context: Context): Boolean =
        isFixEnabled(context, "android12_visibility_bypass")

    fun isFingerDeferFocusEnabled(context: Context): Boolean =
        isFixEnabled(context, "finger_defer_focus")

    fun isHoverPhantomExitCheckEnabled(context: Context): Boolean =
        isFixEnabled(context, "hover_phantom_exit_check")

    fun isHiddenApiBypassEnabled(context: Context): Boolean =
        isFixEnabled(context, "hidden_api_bypass")

    fun isIsolatedTitlebarLinkingEnabled(context: Context): Boolean =
        isFixEnabled(context, COMPAT_ISOLATED_TITLEBAR_LINKING)

    fun isPreciseTouchRegionEnabled(context: Context): Boolean =
        isFixEnabled(context, COMPAT_PRECISE_TOUCH_REGION)

    fun isHybridLeashMinimizationEnabled(context: Context): Boolean =
        isFixEnabled(context, COMPAT_HYBRID_LEASH_MINIMIZATION)

    fun isStrictWmsSyncEnabled(context: Context): Boolean =
        isFixEnabled(context, "strict_wms_sync")

    fun isResolutionOverrideEnabled(context: Context): Boolean =
        isFixEnabled(context, COMPAT_RESOLUTION_OVERRIDE)

    fun isRefreshRateOverrideEnabled(context: Context): Boolean =
        isFixEnabled(context, COMPAT_REFRESH_RATE_OVERRIDE)

    fun isHybridFramePacingEnabled(context: Context): Boolean =
        isFixEnabled(context, COMPAT_HYBRID_FRAME_PACING)

    fun getActiveResizeStyle(context: Context): ResizeStyle {
        val prefs = context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
        val styleStr = prefs.getString("active_resize_style", ResizeStyle.REAL_TIME_SCALING.name)
        return try {
            ResizeStyle.valueOf(styleStr ?: ResizeStyle.REAL_TIME_SCALING.name)
        } catch (e: java.lang.Exception) {
            ResizeStyle.REAL_TIME_SCALING
        }
    }

    fun setActiveResizeStyle(context: Context, style: ResizeStyle) {
        val prefs = context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("active_resize_style", style.name).apply()
    }

    fun getIpcThrottleMs(context: Context): Int {
        val prefs = context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
        return prefs.getInt("ipc_throttle_ms", 16)
    }

    fun setIpcThrottleMs(context: Context, ms: Int) {
        val prefs = context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("ipc_throttle_ms", ms).apply()
    }
}

enum class ResizeStyle {
    BUFFER_STRETCH_HYBRID,
    REAL_TIME_SCALING,
    FUTURE_STYLE_A,
    FUTURE_STYLE_B
}
