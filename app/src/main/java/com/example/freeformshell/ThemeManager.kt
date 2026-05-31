package com.example.freeformshell

import android.content.Context
import android.graphics.Color

object ThemeManager {
    private const val PREFS_NAME = "freeform_theme_prefs"
    
    // Keys
    private const val KEY_MODE = "theme_mode" // 0: Auto, 1: Light, 2: Dark
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_TITLE_BG_LIGHT = "title_bg_light"
    private const val KEY_TITLE_BG_DARK = "title_bg_dark"
    private const val KEY_TEXT_COLOR_LIGHT = "text_color_light"
    private const val KEY_TEXT_COLOR_DARK = "text_color_dark"
    private const val KEY_ROUNDNESS = "window_roundness"
    private const val KEY_OPACITY = "window_opacity"
    private const val KEY_BORDER_WIDTH = "border_width"
    private const val KEY_PILL_FOR_SNAPPED = "pill_for_snapped"
    private const val KEY_WINDOW_THEME = "window_theme" // 0: Classic, 1: Dynamic
    private const val KEY_APP_UI_STYLE = "app_ui_style" // 0: Classic, 1: Expressive
    private const val KEY_EXPRESSIVE_THEME_TYPE = "expr_theme_type" // 0: System, 1: Mono, 2: Custom, 3: Image
    private const val KEY_EXPRESSIVE_THEME_COLOR = "expr_theme_color"
    private const val KEY_EXPRESSIVE_THEME_IMAGE = "expr_theme_image"
    private const val KEY_SIDEBAR_HOVER_EXPAND = "sidebar_hover_expand"
    private const val KEY_SIDEBAR_HOVER_EXPAND_TABLET = "sidebar_hover_expand_tablet"
    private const val KEY_SIDEBAR_AUTO_COLLAPSE = "sidebar_auto_collapse"
    private const val KEY_SIDEBAR_AUTO_COLLAPSE_TABLET = "sidebar_auto_collapse_tablet"
    private const val KEY_USE_TABLET_MODE = "use_tablet_mode"
    private const val KEY_APP_LAUNCH_DISPLAY = "app_launch_display" // 0: Phone, 1: Secondary, 2: Auto
    private const val KEY_SHOW_SHADOWS = "show_shadows"
    private const val KEY_APP_UI_SCALE = "app_ui_scale_v2" // Float value, default 1.0f
    private const val KEY_AUTO_UI_SCALING = "auto_ui_scaling_v2" // Boolean value, default true
    
    fun showShadows(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_SHADOWS, false)

    fun setShowShadows(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_SHADOWS, value).apply()
    
    fun getWindowTheme(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_WINDOW_THEME, 1) // Default to Dynamic

    fun setWindowTheme(context: Context, theme: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_WINDOW_THEME, theme).apply()

    fun getAppUiStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_APP_UI_STYLE, 0)

    fun setAppUiStyle(context: Context, style: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_APP_UI_STYLE, style).apply()

    fun getExpressiveThemeType(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_EXPRESSIVE_THEME_TYPE, 0)

    fun setExpressiveThemeType(context: Context, type: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_EXPRESSIVE_THEME_TYPE, type).apply()

    fun getExpressiveThemeColor(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_EXPRESSIVE_THEME_COLOR, android.graphics.Color.parseColor("#6750A4"))

    fun setExpressiveThemeColor(context: Context, color: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_EXPRESSIVE_THEME_COLOR, color).apply()

    fun getExpressiveThemeImage(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_EXPRESSIVE_THEME_IMAGE, null)

    fun setExpressiveThemeImage(context: Context, uri: String) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_EXPRESSIVE_THEME_IMAGE, uri).apply()

    fun isSidebarHoverExpandEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SIDEBAR_HOVER_EXPAND, false)

    fun setSidebarHoverExpandEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SIDEBAR_HOVER_EXPAND, enabled).apply()

    fun isSidebarHoverExpandTabletEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SIDEBAR_HOVER_EXPAND_TABLET, false)

    fun setSidebarHoverExpandTabletEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SIDEBAR_HOVER_EXPAND_TABLET, enabled).apply()

    fun isSidebarAutoCollapseEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SIDEBAR_AUTO_COLLAPSE, false)

    fun setSidebarAutoCollapseEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SIDEBAR_AUTO_COLLAPSE, enabled).apply()

    fun isSidebarAutoCollapseTabletEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SIDEBAR_AUTO_COLLAPSE_TABLET, false)

    fun setSidebarAutoCollapseTabletEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SIDEBAR_AUTO_COLLAPSE_TABLET, enabled).apply()
    
    // Dock / Safe Area (Per Display)
    private const val KEY_DOCK_POS_PREFIX = "dock_position_" // 0: None, 1: Top, 2: Bottom, 3: Left, 4: Right
    private const val KEY_DOCK_SIZE_PREFIX = "dock_size_"
    private const val KEY_DENSITY_PREFIX = "display_density_"

    fun getThemeMode(context: Context): Int = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_MODE, 0)

    fun setThemeMode(context: Context, mode: Int) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, mode).apply()

    fun getAccentColor(context: Context, systemAccent: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_ACCENT_COLOR, systemAccent)

    fun setAccentColor(context: Context, color: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_ACCENT_COLOR, color).apply()

    fun getTitleBg(context: Context, isDark: Boolean): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (isDark) {
            prefs.getInt(KEY_TITLE_BG_DARK, Color.parseColor("#2D2D30"))
        } else {
            prefs.getInt(KEY_TITLE_BG_LIGHT, Color.parseColor("#F3F6FA"))
        }
    }

    fun setTitleBg(context: Context, isDark: Boolean, color: Int) {
        val key = if (isDark) KEY_TITLE_BG_DARK else KEY_TITLE_BG_LIGHT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(key, color).apply()
    }

    fun getTextColor(context: Context, isDark: Boolean): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (isDark) {
            prefs.getInt(KEY_TEXT_COLOR_DARK, Color.WHITE)
        } else {
            prefs.getInt(KEY_TEXT_COLOR_LIGHT, Color.parseColor("#1C1B1F"))
        }
    }

    fun setTextColor(context: Context, isDark: Boolean, color: Int) {
        val key = if (isDark) KEY_TEXT_COLOR_DARK else KEY_TEXT_COLOR_LIGHT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(key, color).apply()
    }

    fun getRoundness(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_ROUNDNESS, 16f)

    fun setRoundness(context: Context, value: Float) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_ROUNDNESS, value).apply()

    fun getOpacity(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_OPACITY, 255)

    fun setOpacity(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_OPACITY, value).apply()

    fun getTitleBarOpacity(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("title_bar_opacity", 100)

    fun setTitleBarOpacity(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("title_bar_opacity", value).apply()

    fun isDragTintEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("drag_tint_enabled", true)

    fun setDragTintEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("drag_tint_enabled", value).apply()

    fun getBorderWidth(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_BORDER_WIDTH, 4f)

    fun setBorderWidth(context: Context, value: Float) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_BORDER_WIDTH, value).apply()

    fun getDockPosition(context: Context, displayId: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_DOCK_POS_PREFIX + displayId, 0)

    fun setDockPosition(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DOCK_POS_PREFIX + displayId, value).apply()

    fun getDockSize(context: Context, displayId: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_DOCK_SIZE_PREFIX + displayId, 0)

    fun setDockSize(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DOCK_SIZE_PREFIX + displayId, value).apply()

    fun getSnapSensitivity(context: Context, displayId: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("snap_sensitivity_" + displayId, 100) // Default to 100dp

    fun setSnapSensitivity(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("snap_sensitivity_" + displayId, value).apply()

    fun usePillForSnapped(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PILL_FOR_SNAPPED, true)

    fun setPillForSnapped(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_PILL_FOR_SNAPPED, value).apply()

    fun getAppLaunchDisplay(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_APP_LAUNCH_DISPLAY, 2)

    fun setAppLaunchDisplay(context: Context, mode: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_APP_LAUNCH_DISPLAY, mode).apply()

    fun getDensity(context: Context, displayId: Int, default: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_DENSITY_PREFIX + displayId, default)

    fun setDensity(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DENSITY_PREFIX + displayId, value).apply()

    fun getPhysicalDensity(context: Context, displayId: Int, default: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("physical_density_" + displayId, default)

    fun setPhysicalDensity(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("physical_density_" + displayId, value).apply()

    fun getWidth(context: Context, displayId: Int, default: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("width_" + displayId, default)

    fun setWidth(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("width_" + displayId, value).apply()

    fun getHeight(context: Context, displayId: Int, default: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("height_" + displayId, default)

    fun setHeight(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("height_" + displayId, value).apply()

    fun getRefreshRate(context: Context, displayId: Int, default: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("refreshrate_" + displayId, default)

    fun setRefreshRate(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("refreshrate_" + displayId, value).apply()

    fun getRefreshRateMode(context: Context, displayId: Int, default: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("refreshrate_mode_" + displayId, default)

    fun setRefreshRateMode(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("refreshrate_mode_" + displayId, value).apply()

    fun getPerAppDensity(context: Context, packageName: String): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("app_density_$packageName", 0)

    fun setPerAppDensity(context: Context, packageName: String, density: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("app_density_$packageName", density).apply()

    fun useTabletMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_USE_TABLET_MODE, false)

    fun setUseTabletMode(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_USE_TABLET_MODE, value).apply()

    fun realtimeResize(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("realtime_resize", false)

    fun setRealtimeResize(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("realtime_resize", value).apply()

    fun instantResizeNoAnim(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("instant_resize_no_anim", false)

    fun setInstantResizeNoAnim(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("instant_resize_no_anim", value).apply()
        Thread {
            try {
                val scale = if (value) "0" else "1"
                ShellExecutor.executeCommand("settings put global window_animation_scale $scale")
                ShellExecutor.executeCommand("settings put global transition_animation_scale $scale")
                ShellExecutor.executeCommand("settings put global animator_duration_scale $scale")
            } catch (e: Exception) {
                android.util.Log.e("ThemeManager", "Failed to update animation settings", e)
            }
        }.start()
    }

    fun getPillAutoShrink(context: Context, displayId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("pill_auto_shrink_d$displayId", true)

    fun setPillAutoShrink(context: Context, displayId: Int, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("pill_auto_shrink_d$displayId", value).apply()

    fun getPillInactiveScale(context: Context, displayId: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("pill_inactive_scale_d$displayId", 70)

    fun setPillInactiveScale(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("pill_inactive_scale_d$displayId", value).apply()

    fun getWorkspaceAutoSnap(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("workspace_auto_snap", true)

    fun setWorkspaceAutoSnap(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("workspace_auto_snap", value).apply()

    fun getPairedScalingGlobal(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("paired_group_resizing_global", false)

    fun setPairedScalingGlobal(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("paired_group_resizing_global", value).apply()

    fun getPairedScaling(context: Context, displayId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("paired_group_resizing_d$displayId", true)

    fun setPairedScaling(context: Context, displayId: Int, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("paired_group_resizing_d$displayId", value).apply()

    fun getHideOnLauncherActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("hide_on_launcher_active", false)

    fun setHideOnLauncherActive(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("hide_on_launcher_active", value).apply()

    fun getDockLauncherPackage(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("dock_launcher_package", "") ?: ""

    fun setDockLauncherPackage(context: Context, value: String) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("dock_launcher_package", value).apply()

    fun getTiledSwapGlobal(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("tiled_swap_enabled_global", true)

    fun setTiledSwapGlobal(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("tiled_swap_enabled_global", value).apply()

    fun getTiledSwap(context: Context, displayId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("tiled_swap_enabled_d$displayId", true)

    fun setTiledSwap(context: Context, displayId: Int, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("tiled_swap_enabled_d$displayId", value).apply()

    fun getPillShrinkStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("pill_shrink_style", 1) // Default to physical resizing style

    fun setPillShrinkStyle(context: Context, style: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("pill_shrink_style", style).apply()

    fun isDisplayShellEnabled(context: Context, displayId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("display_shell_enabled_$displayId", true)

    fun setDisplayShellEnabled(context: Context, displayId: Int, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("display_shell_enabled_$displayId", enabled).apply()

    fun getVisualCornerHandlesGlobal(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("visual_corner_handles_global", true)

    fun setVisualCornerHandlesGlobal(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("visual_corner_handles_global", value).apply()

    fun getVisualCornerHandles(context: Context, displayId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("visual_corner_handles_d$displayId", true)

    fun setVisualCornerHandles(context: Context, displayId: Int, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("visual_corner_handles_d$displayId", enabled).apply()

    // ── Compatibility Fix Helpers ───────────────────────────────────────────
    // Each fix is stored as "compat_<fixId>" in the main prefs file.
    // `defaultOn` is only used as fallback when no value has been saved yet.

    fun getCompFix(context: Context, fixId: String, defaultOn: Boolean): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("compat_$fixId", defaultOn)

    fun setCompFix(context: Context, fixId: String, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("compat_$fixId", value).apply()

    /** Removes all compat_* keys so every fix reverts to its smart default on next read. */
    fun resetCompFixes(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("compat_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun isGlobalOverlayEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("global_overlay_enabled", true)

    fun setGlobalOverlayEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("global_overlay_enabled", enabled).apply()

    fun getAppUiScale(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_APP_UI_SCALE, 1.0f)

    fun setAppUiScale(context: Context, value: Float) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_APP_UI_SCALE, value).apply()

    fun isAutoUiScalingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_UI_SCALING, false)

    fun setAutoUiScalingEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_UI_SCALING, enabled).apply()

    fun isForceDesktopModeEnabled(context: Context): Boolean =
        android.provider.Settings.Global.getInt(context.contentResolver, "force_desktop_mode_on_external_displays", 0) == 1

    /**
     * Applies the force_desktop_mode_on_external_displays global setting via Shizuku shell.
     * The callback is invoked on a background thread with `true` on success, `false` on failure.
     */
    fun setForceDesktopModeEnabled(enabled: Boolean, onDone: ((success: Boolean) -> Unit)? = null) {
        val value = if (enabled) 1 else 0
        Thread {
            try {
                val result = ShellExecutor.executeCommandWithResult(
                    "settings put global force_desktop_mode_on_external_displays $value"
                )
                val success = result.third == 0
                android.util.Log.d("ThemeManager", "setForceDesktopModeEnabled($enabled) => exit=${result.third} out=${result.first} err=${result.second}")
                onDone?.invoke(success)
            } catch (e: Exception) {
                android.util.Log.e("ThemeManager", "setForceDesktopModeEnabled failed", e)
                onDone?.invoke(false)
            }
        }.start()
    }
}

