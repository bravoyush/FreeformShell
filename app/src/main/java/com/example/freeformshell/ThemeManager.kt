package com.example.freeformshell

import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.view.Display
import android.util.DisplayMetrics
import android.util.Log

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
    private const val KEY_DESKTOP_THEME_STYLE = "desktop_theme_style" // 0: Classic, 1: Aluminum OS
    private const val KEY_SHOW_STATUS_BAR = "show_status_bar" // Boolean value, default true
    private const val KEY_CUSTOM_AI_APP_PACKAGE = "custom_ai_app_package"
    private const val KEY_SHOW_BATTERY_PERCENT = "show_battery_percent"
    private const val KEY_ENABLE_QUICK_SETTINGS = "enable_quick_settings"
    private const val KEY_STATUS_BAR_HEIGHT = "status_bar_height"
    private const val KEY_SPLIT_CLICK_BENTO = "split_click_bento"
    private const val KEY_SCROLL_PAGE_SWITCHING = "scroll_page_switching"
    private const val KEY_NOTIF_PANEL_SCALE = "notif_panel_scale"
    private const val KEY_CLOCK_FORMAT = "clock_format"
    private const val KEY_CLOCK_DATE_STYLE = "clock_date_style"
    private const val KEY_CLOCK_SHOW_AM_PM = "clock_show_am_pm"
    private const val KEY_WINDOWS_NOTIFS_ENABLED = "windows_notifs_enabled"
    private const val KEY_WINDOWS_NOTIFS_POSITION = "windows_notifs_position"
    private const val KEY_SETUP_COMPLETE = "setup_complete_v3"
    private const val KEY_ENHANCED_MULTIWINDOW = "enhanced_multiwindow"
    private const val KEY_NOTIF_PANEL_HEIGHT = "notif_panel_height"
    private const val KEY_NOTIF_PANEL_ANIM_DURATION = "notif_panel_anim_duration"
    private const val KEY_NOTIF_GROUPING_ENABLED = "notif_grouping_enabled"
    private const val KEY_MULTIWINDOW_WHITELIST = "multiwindow_whitelist"
    private const val KEY_REFRESH_RATE = "perf_refresh_rate" // 0: Auto, 1: 1Hz, 2: 2Hz, 3: 5Hz, 4: 10Hz
    
    // Hardware Overrides
    private const val KEY_USE_AUTO_HW = "use_auto_hw_detection"
    private const val KEY_HW_CPU_NAME = "hw_cpu_name"
    private const val KEY_HW_CPU_CODE = "hw_cpu_codename"
    private const val KEY_HW_CPU_VENDOR = "hw_cpu_vendor"
    private const val KEY_HW_GPU_NAME = "hw_gpu_name"
    private const val KEY_HW_GPU_VENDOR = "hw_gpu_vendor"
    private const val KEY_HW_DEVICE_NAME = "hw_device_name"
    private const val KEY_HW_DEVICE_MODEL = "hw_device_model"
    private const val KEY_HW_STORAGE_NAME = "hw_storage_name"
    private const val KEY_PERF_HISTORY_LENGTH = "perf_history_length" // 30, 60, 90, 120, 180, 300
    private const val KEY_PERF_TOOLTIP_ENABLED = "perf_tooltip_enabled"
    private const val KEY_PERF_USE_BITS = "perf_use_bits"

    fun getPerformanceHistoryLength(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_PERF_HISTORY_LENGTH, 60)

    fun setPerformanceHistoryLength(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_PERF_HISTORY_LENGTH, value).apply()

    fun getPerformanceRefreshRate(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_REFRESH_RATE, 0)

    fun setPerformanceRefreshRate(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_REFRESH_RATE, value).apply()

    fun isPerformanceTooltipEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PERF_TOOLTIP_ENABLED, true)

    fun setPerformanceTooltipEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_PERF_TOOLTIP_ENABLED, enabled).apply()

    fun useBitsForNetwork(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PERF_USE_BITS, true)

    fun setUseBitsForNetwork(context: Context, useBits: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_PERF_USE_BITS, useBits).apply()

    fun useAutoHardwareDetection(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_USE_AUTO_HW, true)

    fun setUseAutoHardwareDetection(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_USE_AUTO_HW, value).apply()

    fun getHardwareOverride(context: Context, key: String): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("hw_ov_$key", "") ?: ""

    fun setHardwareOverride(context: Context, key: String, value: String) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("hw_ov_$key", value).apply()

    fun isEnhancedMultiWindowEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENHANCED_MULTIWINDOW, true)

    fun setEnhancedMultiWindowEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENHANCED_MULTIWINDOW, enabled).apply()

    fun getNotifPanelHeight(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_NOTIF_PANEL_HEIGHT, 420)

    fun setNotifPanelHeight(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_NOTIF_PANEL_HEIGHT, value).apply()

    fun getNotifPanelAnimDuration(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_NOTIF_PANEL_ANIM_DURATION, 450)

    fun setNotifPanelAnimDuration(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_NOTIF_PANEL_ANIM_DURATION, value).apply()

    fun isNotifGroupingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_NOTIF_GROUPING_ENABLED, true)

    fun setNotifGroupingEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_NOTIF_GROUPING_ENABLED, enabled).apply()

    fun getMultiWindowWhitelist(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_MULTIWINDOW_WHITELIST, null)
        if (set == null) {
            // Smart Defaults: Chrome and our own app
            val defaults = setOf("com.android.chrome", "com.example.freeformshell")
            prefs.edit().putStringSet(KEY_MULTIWINDOW_WHITELIST, defaults).apply()
            return defaults
        }
        return set
    }

    fun toggleMultiWindowWhitelist(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getMultiWindowWhitelist(context).toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.edit().putStringSet(KEY_MULTIWINDOW_WHITELIST, current).apply()
    }

    fun isMultiWindowSupported(context: Context, packageName: String): Boolean {
        if (!isEnhancedMultiWindowEnabled(context)) return false
        return getMultiWindowWhitelist(context).contains(packageName)
    }

    fun isSetupComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SETUP_COMPLETE, false)

    fun setSetupComplete(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    fun isNotificationServiceEnabled(context: Context): Boolean {
        val cn = android.content.ComponentName(context, LockScreenNotificationListener::class.java)
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    fun getCustomAiAppPackage(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CUSTOM_AI_APP_PACKAGE, "") ?: ""

    fun setCustomAiAppPackage(context: Context, value: String) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CUSTOM_AI_APP_PACKAGE, value).apply()

    fun isScrollPageSwitchingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SCROLL_PAGE_SWITCHING, false)

    fun setScrollPageSwitchingEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SCROLL_PAGE_SWITCHING, enabled).apply()

    fun showShadows(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_SHADOWS, false)

    fun setShowShadows(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_SHADOWS, value).apply()
    
    fun getWindowTheme(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_WINDOW_THEME, 1) // Default to Dynamic

    fun setWindowTheme(context: Context, theme: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_WINDOW_THEME, theme).apply()

    fun getAppUiStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_APP_UI_STYLE, 1)

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

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, mode).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

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

    fun setDockPosition(context: Context, displayId: Int, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DOCK_POS_PREFIX + displayId, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    fun getDockSize(context: Context, displayId: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_DOCK_SIZE_PREFIX + displayId, 0)

    fun setDockSize(context: Context, displayId: Int, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DOCK_SIZE_PREFIX + displayId, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    data class DetectionResult(val position: Int, val size: Int, val description: String)

    @JvmStatic
    fun autoDetectTaskbar(context: Context, targetDisplayId: Int): DetectionResult {
        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(targetDisplayId) ?: return DetectionResult(0, 0, "Display $targetDisplayId not found")
            
            val realMetrics = DisplayMetrics()
            display.getRealMetrics(realMetrics)
            val displayW = realMetrics.widthPixels
            val displayH = realMetrics.heightPixels
            val density = realMetrics.density
            
            val dumpsys = ShellExecutor.exec("dumpsys window windows")
            if (dumpsys.isEmpty() || dumpsys.contains("Error")) {
                return DetectionResult(0, 0, "Failed to execute dumpsys command")
            }

            var currentDisplayId = 0
            var inTargetWindow = false
            var matchedWindowName = ""
            
            val lines = dumpsys.split("\n")
            val frameRegex = Regex("""(?:mFrame|Frame)=\[([-\d]+),([-\d]+)\]\[([-\d]+),([-\d]+)\]""")
            val displayIdRegex1 = Regex("""mDisplayId=(\d+)""")
            val displayIdRegex2 = Regex("""DisplayId=(\d+)""")
            val displayIdRegex3 = Regex("""Display\s+#?(\d+)""")
            
            val targetKeys = listOf("navigationbar", "taskbar", "navbar", "com.farmerbb.taskbar", "dock")
            val tolerance = (8 * density).toInt().coerceAtLeast(8)

            for (line in lines) {
                // 1. Detect display change
                displayIdRegex1.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                    currentDisplayId = it
                } ?: displayIdRegex2.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                    currentDisplayId = it
                } ?: displayIdRegex3.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                    currentDisplayId = it
                }

                // 2. Check if we enter a target window block
                if (line.contains("Window #") || line.contains("Window{")) {
                    val lowerLine = line.lowercase()
                    val isTarget = targetKeys.any { lowerLine.contains(it) }
                    if (isTarget) {
                        inTargetWindow = true
                        matchedWindowName = line.substringAfter("Window{").substringBefore("}").trim()
                        if (matchedWindowName.isEmpty()) {
                            matchedWindowName = line.trim()
                        }
                    } else {
                        inTargetWindow = false
                    }
                }

                // 3. Extract frames inside the target window block
                if (inTargetWindow && currentDisplayId == targetDisplayId) {
                    val match = frameRegex.find(line)
                    if (match != null) {
                        val left = match.groupValues[1].toIntOrNull() ?: 0
                        val top = match.groupValues[2].toIntOrNull() ?: 0
                        val right = match.groupValues[3].toIntOrNull() ?: 0
                        val bottom = match.groupValues[4].toIntOrNull() ?: 0
                        
                        val w = right - left
                        val h = bottom - top
                        
                        if (w > 0 && h > 0 && (w < displayW || h < displayH)) {
                            // Bottom Edge: Sitting near the display bottom edge
                            if (Math.abs(bottom - displayH) <= tolerance && h < displayH / 4) {
                                val size = displayH - top
                                return DetectionResult(2, size, "Bottom Dock detected: $matchedWindowName (${size}px)")
                            }
                            // Top Edge: Sitting near the display top edge
                            if (Math.abs(top) <= tolerance && h < displayH / 4) {
                                return DetectionResult(1, bottom, "Top Dock detected: $matchedWindowName (${bottom}px)")
                            }
                            // Left Edge: Sitting near the display left edge
                            if (Math.abs(left) <= tolerance && w < displayW / 4) {
                                return DetectionResult(3, right, "Left Dock detected: $matchedWindowName (${right}px)")
                            }
                            // Right Edge: Sitting near the display right edge
                            if (Math.abs(right - displayW) <= tolerance && w < displayW / 4) {
                                val size = displayW - left
                                return DetectionResult(4, size, "Right Dock detected: $matchedWindowName (${size}px)")
                            }
                        }
                        inTargetWindow = false // Reset block once frame is parsed
                    }
                }
            }
            return DetectionResult(0, 0, "No active system taskbar/dock found on Display $targetDisplayId")
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error auto-detecting taskbar", e)
            return DetectionResult(0, 0, "Auto-detection error: ${e.message}")
        }
    }

    fun getSnapSensitivity(context: Context, displayId: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("snap_sensitivity_" + displayId, 15) // Default to 15dp

    fun setSnapSensitivity(context: Context, displayId: Int, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("snap_sensitivity_" + displayId, value).apply()

    fun usePillForSnapped(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PILL_FOR_SNAPPED, false)

    fun setPillForSnapped(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_PILL_FOR_SNAPPED, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
            FreeformOverlayService.requestRefresh()
        } catch (e: Exception) {}
    }

    fun getAnimationSpeedScale(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat("animation_speed_scale", 1.0f)

    fun setAnimationSpeedScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat("animation_speed_scale", scale).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
            FreeformOverlayService.requestRefresh()
        } catch (e: Exception) {}
    }

    fun autoHideTitleBar(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("auto_hide_title_bar", true)

    fun setAutoHideTitleBar(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("auto_hide_title_bar", value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
            FreeformOverlayService.requestRefresh()
        } catch (e: Exception) {}
    }

    fun getAutoHideTriggerMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("auto_hide_trigger_mode", 0)

    fun setAutoHideTriggerMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("auto_hide_trigger_mode", mode).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
            FreeformOverlayService.requestRefresh()
        } catch (e: Exception) {}
    }

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

    fun isNotificationCooldownEnabled(context: Context, packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet("notification_cooldown_apps", emptySet()) ?: emptySet()
        return set.contains(packageName.lowercase())
    }

    fun toggleNotificationCooldown(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet("notification_cooldown_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        val lower = packageName.lowercase()
        if (set.contains(lower)) {
            set.remove(lower)
        } else {
            set.add(lower)
        }
        prefs.edit().putStringSet("notification_cooldown_apps", set).apply()
    }

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

    // ── Frequent Settings Tracker ──────────────────────────────────────────────
    fun recordSettingUsed(context: Context, settingKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("freq_$settingKey", 0) + 1
        prefs.edit().putInt("freq_$settingKey", count).apply()
    }

    fun getTopFrequentSettings(context: Context, fallbackDefaults: List<String>): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = listOf("theme_mode", "auto_hide", "desktop_mode", "pill_mode", "drag_tint", "auto_scale")
        val sorted = keys.map { key -> key to prefs.getInt("freq_$key", 0) }
            .sortedByDescending { it.second }
            .filter { it.second > 0 }
            .map { it.first }
        
        return (sorted + fallbackDefaults).distinct().take(4)
    }

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

    fun getDesktopThemeStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_DESKTOP_THEME_STYLE, 1)

    fun setDesktopThemeStyle(context: Context, style: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DESKTOP_THEME_STYLE, style).apply()

    fun showStatusBar(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_STATUS_BAR, true)

    fun setShowStatusBar(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_STATUS_BAR, value).apply()

    fun showBatteryPercent(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_BATTERY_PERCENT, true)

    fun setShowBatteryPercent(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_BATTERY_PERCENT, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    fun isQuickSettingsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLE_QUICK_SETTINGS, true)

    fun setQuickSettingsEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLE_QUICK_SETTINGS, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    fun getStatusBarHeight(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_STATUS_BAR_HEIGHT, 36)

    fun setStatusBarHeight(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_STATUS_BAR_HEIGHT, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    fun isSplitClickBentoEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SPLIT_CLICK_BENTO, true)

    fun setSplitClickBentoEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPLIT_CLICK_BENTO, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    private const val KEY_ACTIVE_TILES = "active_tiles"
    private const val KEY_TILE_LAYOUTS_JSON = "qs_tile_layouts_json"

    val DEFAULT_TILE_LAYOUTS = listOf(
        TileLayout("wifi", 0, 0, 2, 1),
        TileLayout("bluetooth", 2, 0, 2, 1),
        TileLayout("modes", 0, 1, 2, 1),
        TileLayout("screenrecord", 2, 1, 2, 1),
        TileLayout("screenshot", 0, 2, 2, 1)
    )

    fun getTileLayouts(context: Context): List<TileLayout> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TILE_LAYOUTS_JSON, null)
        if (jsonStr.isNullOrEmpty()) {
            return DEFAULT_TILE_LAYOUTS
        }
        try {
            val list = mutableListOf<TileLayout>()
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TileLayout(
                        key = obj.getString("key"),
                        col = obj.getInt("col"),
                        row = obj.getInt("row"),
                        w = obj.getInt("w"),
                        h = obj.getInt("h"),
                        page = obj.optInt("page", 0)
                    )
                )
            }
            return list
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error parsing tile layouts JSON", e)
            return DEFAULT_TILE_LAYOUTS
        }
    }

    fun setTileLayouts(context: Context, layouts: List<TileLayout>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val array = org.json.JSONArray()
            for (layout in layouts) {
                val obj = org.json.JSONObject()
                obj.put("key", layout.key)
                obj.put("col", layout.col)
                obj.put("row", layout.row)
                obj.put("w", layout.w)
                obj.put("h", layout.h)
                obj.put("page", layout.page)
                array.put(obj)
            }
            prefs.edit().putString(KEY_TILE_LAYOUTS_JSON, array.toString()).apply()
            
            // Sync traditional active_tiles key for compatibility
            val activeKeys = layouts.map { it.key }
            prefs.edit().putString(KEY_ACTIVE_TILES, activeKeys.joinToString(",")).apply()
            
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error saving tile layouts JSON", e)
        }
    }

    fun getActiveTiles(context: Context): List<String> {
        return getTileLayouts(context).map { it.key }
    }

    fun setActiveTiles(context: Context, tiles: List<String>) {
        val currentLayouts = getTileLayouts(context)
        val newLayouts = mutableListOf<TileLayout>()
        
        // Arrange new tiles neatly
        var nextRow = 0
        var nextCol = 0
        for (key in tiles) {
            val existing = currentLayouts.firstOrNull { it.key == key }
            if (existing != null) {
                newLayouts.add(existing)
            } else {
                val w = if (key == "music") 4 else 2
                val h = 1
                
                // Align to 4-column boundary
                if (nextCol + w > 4) {
                    nextCol = 0
                    nextRow++
                }
                newLayouts.add(TileLayout(key, nextCol, nextRow, w, h, 0))
                nextCol += w
                if (nextCol >= 4) {
                    nextCol = 0
                    nextRow++
                }
            }
        }
        setTileLayouts(context, newLayouts)
    }

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

    // ── Lock Screen / Keyguard Customizations ───────────────────────────────────
    fun getLockWallpaperTheme(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("lock_wallpaper_theme", 0)

    fun setLockWallpaperTheme(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("lock_wallpaper_theme", value).apply()

    fun getLockBlurRadius(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat("lock_blur_radius", 25f)

    fun setLockBlurRadius(context: Context, value: Float) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat("lock_blur_radius", value).apply()

    fun getLockClockPreset(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("lock_clock_preset", 0)

    fun setLockClockPreset(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("lock_clock_preset", value).apply()

    fun isLockSyncDismissEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_sync_dismiss", true)

    fun setLockSyncDismissEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_sync_dismiss", value).apply()

    fun isLockKeypadScramblerEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_keypad_scrambler", false)

    fun setLockKeypadScramblerEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_keypad_scrambler", value).apply()

    fun isLockKeyboardBypassEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_keyboard_bypass", false)

    fun setLockKeyboardBypassEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_keyboard_bypass", value).apply()

    fun getLockNotificationsMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("lock_notifications_mode", 1) // default: icons only

    fun setLockNotificationsMode(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("lock_notifications_mode", value).apply()

    fun isLockAodEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_aod_enabled", false)

    fun setLockAodEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_aod_enabled", value).apply()

    fun isLockWeatherWidgetEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_weather_widget", true)

    fun setLockWeatherWidgetEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_weather_widget", value).apply()

    fun isLockCalendarWidgetEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_calendar_widget", false)

    fun setLockCalendarWidgetEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_calendar_widget", value).apply()

    // Desktop Custom Lock Security
    fun isDesktopLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("desktop_lock_enabled", false)

    fun setDesktopLockEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("desktop_lock_enabled", value).apply()

    fun getDesktopLockPin(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("desktop_lock_pin", "1234") ?: "1234"

    fun setDesktopLockPin(context: Context, value: String) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("desktop_lock_pin", value).apply()

    fun getLockMusicControlStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("lock_music_control_style", 0)

    fun setLockMusicControlStyle(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("lock_music_control_style", value).apply()

    fun isLockKeyboardAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_keyboard_allowed", true)

    fun setLockKeyboardAllowed(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_keyboard_allowed", value).apply()

    fun getLockScreenDisplayTarget(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("lock_screen_display_target", 1) // default: Both displays (1)

    fun setLockScreenDisplayTarget(context: Context, value: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("lock_screen_display_target", value).apply()

    // ── Performance & Optimization Keys ───────────────────────────────────────
    fun isLockAutoDetectEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("lock_auto_detect", true)

    fun setLockAutoDetectEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("lock_auto_detect", value).apply()

    fun isDeepSleepOptimizationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("deep_sleep_optimization", true)

    fun setDeepSleepOptimizationEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("deep_sleep_optimization", value).apply()

    fun isWidgetRefreshDeduplicated(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("widget_refresh_deduplicated", true)

    fun setWidgetRefreshDeduplicated(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("widget_refresh_deduplicated", value).apply()

    fun isShellDisplayRequired(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("shell_display_required", true)

    fun setShellDisplayRequired(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("shell_display_required", value).apply()

    // ── System Modules Toggles ───────────────────────────────────────────────
    fun isModuleLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("module_lock_enabled", true)

    fun setModuleLockEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("module_lock_enabled", value).apply()

    fun isModuleWidgetEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("module_widget_enabled", true)

    fun setModuleWidgetEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("module_widget_enabled", value).apply()

    fun isModuleCaptureEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("module_capture_enabled", true)

    fun setModuleCaptureEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("module_capture_enabled", value).apply()

    fun isModuleMirrorEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("module_mirror_enabled", true)

    fun setModuleMirrorEnabled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("module_mirror_enabled", value).apply()

    // Notification & Settings Panel Scale
    fun getNotifPanelScale(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_NOTIF_PANEL_SCALE, 1.0f)

    fun setNotifPanelScale(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_NOTIF_PANEL_SCALE, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    // Top Bar Clock settings (0: 12h, 1: 24h, 2: System Default)
    fun getClockFormat(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_CLOCK_FORMAT, 2)

    fun setClockFormat(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_CLOCK_FORMAT, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    // Clock Date Style (0: Hide, 1: Numeric, 2: Medium, 3: Long, 4: Full)
    fun getClockDateStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_CLOCK_DATE_STYLE, 3)

    fun setClockDateStyle(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_CLOCK_DATE_STYLE, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    // Show AM/PM suffix for 12h clock
    fun getClockShowAmPm(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CLOCK_SHOW_AM_PM, true)

    fun setClockShowAmPm(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_CLOCK_SHOW_AM_PM, value).apply()
        try {
            context.sendBroadcast(android.content.Intent("com.example.freeformshell.THEME_CHANGED"))
        } catch (e: Exception) {}
    }

    fun isWindowsNotifsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_WINDOWS_NOTIFS_ENABLED, true)

    fun setWindowsNotifsEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_WINDOWS_NOTIFS_ENABLED, enabled).apply()

    fun getWindowsNotifsPosition(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_WINDOWS_NOTIFS_POSITION, 0)

    fun setWindowsNotifsPosition(context: Context, position: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_WINDOWS_NOTIFS_POSITION, position).apply()
}

data class TileLayout(
    val key: String,
    val col: Int,
    val row: Int,
    val w: Int,
    val h: Int,
    val page: Int = 0
)


