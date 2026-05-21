package com.example.freeformshell

import android.content.Context
import android.os.Build

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

    fun isAndroid12VisibilityBypassEnabled(context: Context): Boolean =
        isFixEnabled(context, "android12_visibility_bypass")

    fun isFingerDeferFocusEnabled(context: Context): Boolean =
        isFixEnabled(context, "finger_defer_focus")

    fun isHoverPhantomExitCheckEnabled(context: Context): Boolean =
        isFixEnabled(context, "hover_phantom_exit_check")

    fun isHiddenApiBypassEnabled(context: Context): Boolean =
        isFixEnabled(context, "hidden_api_bypass")
}
