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
    private var KEY_WINDOW_THEME = "window_theme" // 0: Classic, 1: Dynamic
    private const val KEY_USE_TABLET_MODE = "use_tablet_mode"
    private const val KEY_APP_LAUNCH_DISPLAY = "app_launch_display" // 0: Phone, 1: Secondary, 2: Auto
    private const val KEY_SHOW_SHADOWS = "show_shadows"
    
    fun showShadows(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_SHADOWS, false)

    fun setShowShadows(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_SHADOWS, value).apply()
    
    fun getWindowTheme(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_WINDOW_THEME, 1) // Default to Dynamic

    fun setWindowTheme(context: Context, theme: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_WINDOW_THEME, theme).apply()
    
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

    fun usePillForSnapped(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PILL_FOR_SNAPPED, false)

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

    fun getPillAutoShrink(context: Context, displayId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("pill_auto_shrink_d$displayId", false)

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
}
