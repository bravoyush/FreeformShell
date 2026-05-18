package com.example.freeformshell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import androidx.palette.graphics.Palette
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.GridLayout
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility")
class DragResizeOverlay(
    private val context: Context,
    internal val taskId: Int,
    internal val packageName: String,
    private val displayId: Int = 0,
    private val onMinimize: () -> Unit,
    private val onClose: () -> Unit
) {
    val currentDisplayId: Int get() = displayId

    private val TAG = "DragResizeOverlay"
    private val textViewId = View.generateViewId()

    private val displayContext: Context = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val dContext = context.createDisplayContext(targetDisplay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            dContext
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error creating display context for $displayId", e)
        context
    }

    private val windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val realMetrics = android.util.DisplayMetrics().apply {
        displayContext.display?.getRealMetrics(this)
    }
    private var activityName: String? = null
    // UI State
    var isInteracting = false
        internal set
    internal var preInteractL = 200
    internal var preInteractT = 200
    internal var preInteractW = 800
    internal var preInteractH = 1000
    internal var preShouldShowPill = false
    var isResizing = false
        internal set
    private var lastShouldShowPill: Boolean? = null
    private var isFocused = false
    internal var isDocked = false
    internal var isMaximized = false
    
    private var preMaximizedRect: Rect? = null
    private var preDockedRect: Rect? = null
    private var occluders: List<Rect> = emptyList()

    fun setActivityName(name: String?) {
        this.activityName = name
    }

    // Current window bounds in PIXELS
    internal var winL = 200
    internal var winT = 200
    internal var winW = 800
    internal var winH = 1000

    internal val density = displayContext.resources.displayMetrics.density
    internal val titleBarHeight = (40 * density).toInt()
    private val borderWidth = (4 * density).toInt()
    private val touchStripWidth = (16 * density).toInt()

    private var resizeJob: Job? = null
    private var lastShellTime = 0L
    private var rLx = 0f
    private var rLy = 0f
    
    // The 5-window architecture: Bar, Frame, and 3 Resize Strips
    private var titleBarView: View? = null
    private var frameView: View? = null
    private var leftStrip: View? = null
    private var rightStrip: View? = null
    private var bottomStrip: View? = null
    
    private var lastInteractionTime = 0L
    
    private var isPillShrunk = false
    private val shrinkHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val shrinkRunnable = Runnable { updatePillShrink(true) }

    fun triggerPillInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        updatePillShrink(false)
        
        shrinkHandler.removeCallbacks(shrinkRunnable)
        shrinkHandler.postDelayed(shrinkRunnable, 3000)
    }

    fun updatePillShrink(shrink: Boolean) {
        isPillShrunk = shrink
        val globalEnabled = displayContext.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE).getBoolean("pill_auto_shrink_global", false)
        val displayEnabled = ThemeManager.getPillAutoShrink(displayContext, displayId)
        
        val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
        val shouldShowPill = isMaximized || (isDocked && usePillForSnapped)
        
        if (!globalEnabled || !displayEnabled || !shouldShowPill) {
            titleBarView?.let { v ->
                v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(200).start()
            }
            return
        }
        
        if (isInteracting) {
            titleBarView?.let { v ->
                v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(200).start()
            }
            return
        }
        
        val scale = if (shrink) {
            ThemeManager.getPillInactiveScale(displayContext, displayId) / 100f
        } else {
            1.0f
        }
        val alpha = if (shrink) 0.4f else 1.0f
        
        titleBarView?.let { v ->
            v.animate().scaleX(scale).scaleY(scale).alpha(alpha).setDuration(200).start()
        }
    }
    
    private var appDynamicColor: Int = Color.parseColor("#1A73E8") // Default Google Blue
    private var appIconBitmap: Bitmap? = null
    internal var currentDecorationColor: Int = Color.BLUE

    // System Colors
    private val systemAccentColor: Int by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                displayContext.getColor(android.R.color.system_accent1_600)
            } else {
                Color.parseColor("#4285F4")
            }
        } catch (e: Exception) { Color.BLUE }
    }
    private val systemSurfaceColor: Int by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                displayContext.getColor(android.R.color.system_neutral1_900)
            } else { Color.parseColor("#1A1A1A") }
        } catch (e: Throwable) { Color.parseColor("#1A1A1A") }
    }
    private val systemSurfaceVariantColor: Int by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                displayContext.getColor(android.R.color.system_neutral1_800)
            } else { Color.parseColor("#2D2D2D") }
        } catch (e: Throwable) { Color.parseColor("#2D2D2D") }
    }

    init {
        extractAppTheme()
        createTitleBar()
        createVisualFrame()
        createResizeStrips()
    }

    fun updateFocus(focused: Boolean, topOccluders: List<Rect>) {
        var changed = false
        if (isFocused != focused) {
            isFocused = focused
            changed = true
        }
        if (occluders != topOccluders) {
            occluders = topOccluders
            changed = true
        }
        
        if (changed) {
            updateColors()
            titleBarView?.invalidate()
            frameView?.invalidate()
            leftStrip?.invalidate()
            rightStrip?.invalidate()
            bottomStrip?.invalidate()
        }
    }

    fun setDockMode(docked: Boolean) {
        if (isDocked != docked) {
            if (docked) {
                savePreDockedRect()
            }
            isDocked = docked
            FreeformOverlayService.setTaskDocked(taskId, docked)
            updateLayouts()
            FreeformOverlayService.requestRefresh()
        }
    }

    fun snapToLeft(propagate: Boolean = true) = snapToSide("Left", propagate)
    fun snapToRight(propagate: Boolean = true) = snapToSide("Right", propagate)
    fun snapToTop(propagate: Boolean = true) = snapToSide("Top", propagate)
    fun snapToBottom(propagate: Boolean = true) = snapToSide("Bottom", propagate)

    private fun snapToSide(side: String, propagate: Boolean) {
        if (propagate) {
            val opposite = when(side) {
                "Left" -> "Right"
                "Right" -> "Left"
                "Top" -> "Bottom"
                "Bottom" -> "Top"
                else -> ""
            }
            if (opposite.isNotEmpty()) {
                val paired = FreeformOverlayService.getPairedTask(taskId)
                if (paired != null) {
                    when (opposite) {
                        "Left" -> paired.snapToLeft(false)
                        "Right" -> paired.snapToRight(false)
                        "Top" -> paired.snapToTop(false)
                        "Bottom" -> paired.snapToBottom(false)
                    }
                }
            }
        }
        val safe = getSafeAreaRect()
        val gap = (ThemeManager.getBorderWidth(displayContext) * density).toInt() / 2
        val hW = safe.left + safe.width() / 2
        val hH = safe.top + safe.height() / 2
        val usePill = ThemeManager.usePillForSnapped(displayContext)
        val snapTop = if (usePill) safe.top else safe.top + titleBarHeight
        
        isMaximized = false; savePreDockedRect(); setDockMode(true)
        when (side) {
            "Left" -> ShellExecutor.resizeTask(taskId, safe.left, snapTop, hW - gap, safe.bottom)
            "Right" -> ShellExecutor.resizeTask(taskId, hW + gap, snapTop, safe.right, safe.bottom)
            "Top" -> ShellExecutor.resizeTask(taskId, safe.left, snapTop, safe.right, hH - gap)
            "Bottom" -> ShellExecutor.resizeTask(taskId, safe.left, hH + gap, safe.right, safe.bottom)
        }
        updateLayouts()
    }

    internal fun updateColors() {
        val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
        val shouldShowPill = isMaximized || (isDocked && usePillForSnapped)
        
        val isDark = isColorDark(appDynamicColor)
        val contentColor = if (isDark) Color.WHITE else Color.BLACK
        val buttonBg = if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(60, 0, 0, 0)

        titleBarView?.let { root ->
            val themeMode = ThemeManager.getThemeMode(displayContext)
            val isSystemDark = (displayContext.resources.configuration.uiMode and 
                             android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                             android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            val isDarkMode = when(themeMode) { 1 -> false; 2 -> true; else -> isSystemDark }
            
            val isDynamicTheme = ThemeManager.getWindowTheme(displayContext) == 1
            val baseColor = if (isDynamicTheme) appDynamicColor else systemAccentColor
            
            // Determine final background color
            // Focused: Use vibrant App Dynamic Color
            // Unfocused: Use a more neutral, theme-appropriate version of the app color or system surface
            val finalBgColor = if (isFocused) {
                baseColor
            } else {
                // Mix app color with surface color for unfocused state
                // Use 90% surface to keep it clean and avoid "muddy" colors (especially for yellow icons)
                val surface = if (isDarkMode) Color.parseColor("#1E1E1E") else Color.parseColor("#F5F5F5")
                blendColors(baseColor, surface, 0.9f) 
            }
            
            currentDecorationColor = finalBgColor

            val isDark = isColorDark(finalBgColor)
            val contentColor = if (isDark) Color.WHITE else Color.BLACK
            val buttonBg = if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(60, 0, 0, 0)

            val bg = GradientDrawable().apply {
                if (shouldShowPill) {
                    cornerRadius = root.height / 2f
                    setColor(finalBgColor)
                } else {
                    cornerRadius = 0f
                    setColor(finalBgColor)
                    val r = ThemeManager.getRoundness(displayContext) * density
                    cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                }
            }
            root.background = bg
            
            // Update Title Text and Icons
            for (i in 0 until (root as LinearLayout).childCount) {
                val child = root.getChildAt(i)
                if (child is TextView) {
                    child.setTextColor(contentColor)
                } else if (child is ImageView) {
                    // Update App Icon (child 0)
                    if (i == 0) {
                        child.setImageBitmap(appIconBitmap)
                    } else {
                        // Action buttons
                        child.setColorFilter(contentColor)
                        (child.background as? GradientDrawable)?.setColor(buttonBg)
                    }
                }
            }
        }
        
        frameView?.let { v ->
            v.invalidate()
        }
    }

    private fun endInteraction() {
        isInteracting = false
        lastInteractionTime = System.currentTimeMillis()
        updateLayouts()
        updateColors()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isInteracting) {
                updateLayouts()
            }
        }, 510)
    }

    fun updateFromSystem(l: Int, t: Int, w: Int, h: Int, forcedTitleTop: Int? = null) {
        if (!isInteracting) {
            winL = l
            winW = w
            winH = h
            
            // winT should ALWAYS match the system's reported top (t)
            // The decoration will handle its own safe area clamping
            winT = t
            
            // Stable Auto-detection: Only trigger if we are not already in a manual state
            val safe = getSafeAreaRect()
            val isFullWidth = Math.abs(w - safe.width()) < (10 * density)
            val isFullHeight = Math.abs(h - safe.height()) < (10 * density)
            
            if (isFullWidth && isFullHeight) {
                isMaximized = true
                isDocked = false
            } else if (!isMaximized && !isDocked) {
                // Requirement: Don't auto-dock immediately after user finished moving the window
                // give them 2 seconds to see the freeform state.
                val recentlyInteracted = (System.currentTimeMillis() - lastInteractionTime) < 2000
                
                if (!recentlyInteracted) {
                    // If it's snapped to an edge and matches half/quarter size, mark as docked
                    val isSnapped = Math.abs(l - safe.left) < 5 || Math.abs(t - safe.top) < 5 || 
                                   Math.abs((l + w) - safe.right) < 5 || Math.abs((t + h) - safe.bottom) < 5
                    if (isSnapped) {
                        savePreDockedRect()
                        setDockMode(true)
                    }
                }
            }

            updateLayouts()
        }
    }

    private fun extractAppTheme() {
        // Use smart cache to avoid heavy extraction
        val cached = iconCache.get(packageName)
        if (cached != null) {
            appIconBitmap = cached.first
            appDynamicColor = cached.second
            return
        }

        val pm = context.packageManager
        try {
            val info = pm.getApplicationInfo(packageName, 0)
            val icon = pm.getApplicationIcon(info)
            
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            icon.setBounds(0, 0, 64, 64)
            icon.draw(canvas)
            appIconBitmap = bitmap
            
            // Requirement: Advanced Palette Analysis for "Majorly used color"
            val palette = Palette.from(bitmap).generate()
            
            // Priority: Dominant -> Vibrant -> Muted
            var rawColor = palette.getDominantColor(
                palette.getVibrantColor(
                    palette.getMutedColor(systemAccentColor)
                )
            )
            
            // Requirement: Avoid white/near-white title bars unless strictly necessary
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(rawColor, hsl)
            
            if (hsl[1] < 0.15f && hsl[2] > 0.8f) {
                // If it's too white/neutral, use a darker "Surface"
                val isDarkMode = (displayContext.resources.configuration.uiMode and 
                                 android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                                 android.content.res.Configuration.UI_MODE_NIGHT_YES
                rawColor = if (isDarkMode) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
            } else {
                // Professional Normalization (HSL)
                hsl[1] = hsl[1].coerceIn(0.2f, 0.6f) 
                hsl[2] = hsl[2].coerceIn(0.2f, 0.5f) 
                rawColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
            }
            
            appDynamicColor = rawColor
            
            // Save to cache
            iconCache.put(packageName, Pair(bitmap, appDynamicColor))
        } catch (e: Exception) {
            appDynamicColor = systemAccentColor
            try {
                val fallbackDrawable = pm.defaultActivityIcon
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                fallbackDrawable.setBounds(0, 0, 64, 64)
                fallbackDrawable.draw(canvas)
                appIconBitmap = bitmap
            } catch (ex: Exception) {}
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    fun show() {
        extractAppTheme()
        if (titleBarView != null) return
        try {
            if (frameView?.parent == null) 
                windowManager.addView(frameView, createParams(winW + (borderWidth * 2), winH + titleBarHeight + borderWidth, winL - borderWidth, winT - titleBarHeight, false))
            if (titleBarView?.parent == null) 
                windowManager.addView(titleBarView, createParams(winW, titleBarHeight, winL, winT - titleBarHeight, true))
            if (leftStrip?.parent == null) 
                windowManager.addView(leftStrip, createParams(touchStripWidth, winH, winL - touchStripWidth/2, winT, true))
            if (rightStrip?.parent == null) 
                windowManager.addView(rightStrip, createParams(touchStripWidth, winH, winL + winW - touchStripWidth/2, winT, true))
            if (bottomStrip?.parent == null) 
                windowManager.addView(bottomStrip, createParams(winW, touchStripWidth, winL, winT + winH - touchStripWidth/2, true))
            triggerPillInteraction()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing windows", e)
        }
    }

    fun bringToFront() {
        if (isInteracting) return
        updateLayouts()
        
        activeSnapMenu?.let { menu ->
            if (menu.parent != null) {
                try {
                    val lp = menu.layoutParams as WindowManager.LayoutParams
                    windowManager.updateViewLayout(menu, lp)
                } catch (e: Exception) {}
            }
        }
    }

    fun hide() {
        shrinkHandler.removeCallbacks(shrinkRunnable)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                activeSnapMenu?.let { if (it.parent != null) windowManager.removeView(it); activeSnapMenu = null }
                titleBarView?.let { if (it.parent != null) windowManager.removeView(it) }
                frameView?.let { if (it.parent != null) windowManager.removeView(it) }
                leftStrip?.let { if (it.parent != null) windowManager.removeView(it) }
                rightStrip?.let { if (it.parent != null) windowManager.removeView(it) }
                bottomStrip?.let { if (it.parent != null) windowManager.removeView(it) }
            } catch (e: Exception) {}
        }
    }

    private fun createParams(w: Int, h: Int, x: Int, y: Int, touchable: Boolean): WindowManager.LayoutParams {
        val safe = getSafeAreaRect()
        val clampedX = x.coerceIn(0, realMetrics.widthPixels - w)
        val clampedY = y.coerceIn(safe.top, realMetrics.heightPixels - h)
        
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or 
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (touchable) flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = clampedX; this.y = clampedY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            windowAnimations = 0
        }
    }

    internal fun updateLayouts() {
        try {
            val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
            val shouldShowPill = isMaximized || (isDocked && usePillForSnapped)

            // Auto-correct task bounds if Pill Mode was toggled while docked
            if (isDocked && lastShouldShowPill != null && lastShouldShowPill != shouldShowPill && !isInteracting) {
                val safe = getSafeAreaRect()
                if (shouldShowPill) {
                    if (Math.abs(winT - (safe.top + titleBarHeight)) < (10 * density)) {
                        ShellExecutor.resizeTask(taskId, winL, safe.top, winL + winW, winT + winH)
                    }
                } else {
                    if (Math.abs(winT - safe.top) < (10 * density)) {
                        ShellExecutor.resizeTask(taskId, winL, safe.top + titleBarHeight, winL + winW, winT + winH)
                    }
                }
            }
            lastShouldShowPill = shouldShowPill
            
            val safe = getSafeAreaRect()
            val decorY = (winT - titleBarHeight).coerceIn(safe.top, safe.bottom - titleBarHeight)
            
            // Requirement: If resizing, draw the translucent boundary guide dynamically, but keep the headers and input handlers completely stationary to avoid input dropouts or glitchy visual jumps!
            if (isInteracting && isResizing) {
                frameView?.let { 
                    val realtime = ThemeManager.realtimeResize(displayContext)
                    if (realtime) {
                        // When real-time resize is enabled, hide the translucent outline!
                        updateWindow(it, 0, 0, -1000, -1000, false)
                    } else {
                        val fw = winW + (borderWidth * 2)
                        val fh = winH + titleBarHeight + borderWidth
                        val fx = winL - borderWidth
                        val fy = decorY - borderWidth
                        updateWindow(it, fw, fh, fx, fy, false)
                        it.invalidate()
                    }
                }
                
                if (preShouldShowPill) {
                    titleBarView?.let { v ->
                        val tv = v.findViewById<TextView>(textViewId)
                        val isPhoneScreen = displayId == 0
                        val hideText = if (isPhoneScreen) {
                            preInteractW < (130 * density).toInt()
                        } else {
                            preInteractW < (250 * density).toInt()
                        }
                        tv?.visibility = if (hideText) View.GONE else View.VISIBLE
                        
                        val label = tv?.text?.toString() ?: ""
                        val textW = if (hideText) 0 else (label.length * 10 * density).toInt()
                        val buttonsW = (80 * density).toInt() 
                        val contentW = textW + buttonsW + (24 * density).toInt()
                        val minW = if (hideText) (72 * density).toInt() else (110 * density).toInt()
                        val isQuarterSnap = isDocked && preInteractW < (realMetrics.widthPixels * 0.6f)
                        val maxRatio = if (isPhoneScreen) 0.9f else (if (isQuarterSnap) 0.7f else 0.4f)
                        val maxW = (preInteractW * maxRatio).toInt()
                        val pillW = contentW.coerceIn(minW, Math.max(minW, maxW))
                        val pillH = (40 * density).toInt()
                        val pillX = preInteractL + (preInteractW - pillW) / 2
                        val pillY = (preInteractT + (4 * density).toInt()).coerceIn(safe.top + (4 * density).toInt(), safe.bottom - pillH)
                        
                        updateWindow(v, pillW, pillH, pillX, pillY, true)
                    }
                    leftStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    rightStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    bottomStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                } else {
                    val preDecorY = (preInteractT - titleBarHeight).coerceIn(safe.top, safe.bottom - titleBarHeight)
                    titleBarView?.let { updateWindow(it, preInteractW, titleBarHeight, preInteractL, preDecorY, true) }
                    
                    leftStrip?.let { updateWindow(it, touchStripWidth, preInteractH, preInteractL - touchStripWidth/2, preInteractT, true) }
                    rightStrip?.let { updateWindow(it, touchStripWidth, preInteractH, preInteractL + preInteractW - touchStripWidth/2, preInteractT, true) }
                    bottomStrip?.let { updateWindow(it, preInteractW, touchStripWidth, preInteractL, preInteractT + preInteractH - touchStripWidth/2, true) }
                    
                    frameView?.let { 
                        val fw = winW + (borderWidth * 2)
                        val fh = winH + titleBarHeight + borderWidth
                        val fx = winL - borderWidth
                        val fy = (winT - titleBarHeight).coerceIn(safe.top, safe.bottom - titleBarHeight) - borderWidth
                        updateWindow(it, fw, fh, fx, fy, false)
                        it.invalidate()
                    }
                }
                
                updateColors()
                return
            }

            if (shouldShowPill) {
                titleBarView?.let { v ->
                    val tv = v.findViewById<TextView>(textViewId)
                    val isPhoneScreen = displayId == 0
                    val hideText = if (isPhoneScreen) {
                        winW < (130 * density).toInt()
                    } else {
                        winW < (250 * density).toInt()
                    }
                    tv?.visibility = if (hideText) View.GONE else View.VISIBLE
                    
                    val label = tv?.text?.toString() ?: ""
                    
                    // Requirement: Content-Aware Pill Width with 40% Max Limit (90% on phone)
                    // Base width on text length + buttons + padding
                    val textW = if (hideText) 0 else (label.length * 10 * density).toInt()
                    val buttonsW = (80 * density).toInt() 
                    val contentW = textW + buttonsW + (24 * density).toInt()
                    
                    val isQuarterSnap = isDocked && winW < (realMetrics.widthPixels * 0.6f)
                    val maxRatio = if (isPhoneScreen) 0.9f else (if (isQuarterSnap) 0.7f else 0.4f)
                    val maxW = (winW * maxRatio).toInt()
                    val minW = if (hideText) (72 * density).toInt() else (110 * density).toInt()
                    val pillW = contentW.coerceIn(minW, Math.max(minW, maxW))
                    
                    val pillH = (40 * density).toInt() // Increased height for better rounding look
                    val pillX = winL + (winW - pillW) / 2
                    val pillY = (winT + (4 * density).toInt()).coerceIn(safe.top + (4 * density).toInt(), safe.bottom - pillH)
                    
                    updateWindow(v, pillW, pillH, pillX, pillY, true)
                }
                
                // Hide other elements by setting size to 0
                frameView?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                leftStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                rightStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                bottomStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
            } else {
                // STANDARD MODE: Show full title bar and frame
                frameView?.let { 
                    val recentlyInteracted = (System.currentTimeMillis() - lastInteractionTime) < 500
                    if (isDocked || recentlyInteracted) {
                        updateWindow(it, 0, 0, -1000, -1000, false)
                    } else {
                        val fw = winW + (borderWidth * 2)
                        val fh = winH + titleBarHeight + borderWidth
                        val fx = winL - borderWidth
                        val fy = decorY - borderWidth
                        updateWindow(it, fw, fh, fx, fy, false)
                        it.invalidate() // Force redraw of the border
                    }
                }
                titleBarView?.let { updateWindow(it, winW, titleBarHeight, winL, decorY, true) }
                
                leftStrip?.let { updateWindow(it, touchStripWidth, winH, winL - touchStripWidth/2, winT, true) }
                rightStrip?.let { updateWindow(it, touchStripWidth, winH, winL + winW - touchStripWidth/2, winT, true) }
                bottomStrip?.let { updateWindow(it, winW, touchStripWidth, winL, winT + winH - touchStripWidth/2, true) }
            }
            
            // Ensure snap menu stays on top if it's showing for this specific task
            if (activeSnapMenuTaskId == taskId) {
                activeSnapMenu?.let { menu ->
                    if (menu.parent != null) {
                        try {
                            val lp = menu.layoutParams as WindowManager.LayoutParams
                            windowManager.updateViewLayout(menu, lp)
                        } catch (e: Exception) {}
                    }
                }
            }
            
            updateColors() // Apply dynamic branding
            updatePillShrink(isPillShrunk)
        } catch (e: Exception) {}
    }

    private fun updateWindow(v: View, w: Int, h: Int, x: Int, y: Int, touchable: Boolean = true) {
        try {
            if (w <= 0 || h <= 0) {
                if (v.visibility != View.GONE) {
                    v.visibility = View.GONE
                }
                return
            }
            if (v.visibility != View.VISIBLE) {
                v.visibility = View.VISIBLE
            }
            
            if (v.parent != null) {
                val lp = v.layoutParams as WindowManager.LayoutParams
                var changed = false
                if (lp.width != w) { lp.width = w; changed = true }
                if (lp.height != h) { lp.height = h; changed = true }
                if (lp.x != x) { lp.x = x; changed = true }
                if (lp.y != y) { lp.y = y; changed = true }
                if (lp.windowAnimations != 0) { lp.windowAnimations = 0; changed = true }
                
                val newFlags = if (touchable) {
                    (lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    (lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
                }
                if (lp.flags != newFlags) {
                    lp.flags = newFlags
                    changed = true
                }
                
                if (changed) {
                    windowManager.updateViewLayout(v, lp)
                }
            } else {
                windowManager.addView(v, createParams(w, h, x, y, touchable))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating overlay window", e)
        }
    }

    internal fun applyBounds(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastShellTime < 150) return
        lastShellTime = now
        
        resizeJob?.cancel()
        resizeJob = CoroutineScope(Dispatchers.IO).launch {
            if (!force) delay(16) 
            ShellExecutor.resizeTask(taskId, winL, winT, winL + winW, winT + winH)
        }
    }

    private fun createTitleBar() {
        val root = object : LinearLayout(displayContext) {
            override fun draw(canvas: Canvas) { applyMaskAndDraw(this, canvas) { super.draw(it) } }
        }.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        
        val tv = TextView(displayContext).apply {
            id = textViewId
            val pm = context.packageManager
            val appLabel = try {
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                var name = packageName.substringAfterLast(".")
                if (name.lowercase() == "root" || name.lowercase() == "main") {
                    val parts = packageName.split(".")
                    if (parts.size >= 2) name = parts[parts.size - 2]
                }
                name.replaceFirstChar { it.uppercase() }
            }
            text = appLabel
            textSize = 10f; setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        root.apply {
            val p = (12 * density).toInt()
            setPadding(p, 0, p, 0)
        }
        
        // 1. Dynamic App Icon
        val iconView = ImageView(displayContext).apply {
            setImageBitmap(appIconBitmap)
            val iconSize = (18 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { 
                marginEnd = (8 * density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(iconView)
        
        // 2. Title Text
        root.addView(tv)
        
        // 3. Buttons
        val snapButton = ImageView(displayContext).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            val p = (6 * density).toInt()
            setPadding(p, p, p, p)
            val size = (26 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (8 * density).toInt() }
            background = GradientDrawable().apply { 
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#444444"))
            }
            setOnClickListener { 
                triggerPillInteraction()
                showSnapMenu(it) 
            }
        }
        
        val close = ImageView(displayContext).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            val p = (5 * density).toInt()
            setPadding(p, p, p, p)
            val size = (26 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply { 
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E81123")) 
            }
            setOnClickListener { 
                triggerPillInteraction()
                ShellExecutor.forceStopApp(packageName, taskId)
                onClose()
            }
        }
        
        root.addView(snapButton)
        root.addView(close)
        
        var lx = 0f; var ly = 0f
        var startX = 0f; var startY = 0f
        var hasMovedThreshold = false
        var wasDockedOnDown = false
        var targetSide: String? = null
        
        root.setOnTouchListener { _, event ->
            triggerPillInteraction()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { 
                    isInteracting = true
                    hasMovedThreshold = false
                    wasDockedOnDown = isDocked
                    targetSide = null
                    
                    preInteractL = winL
                    preInteractT = winT
                    preInteractW = winW
                    preInteractH = winH
                    val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
                    preShouldShowPill = isMaximized || (isDocked && usePillForSnapped)
                    
                    ShellExecutor.moveTaskToFront(taskId)
                    FreeformOverlayService.requestRefresh()
                    
                    dismissActiveSnapMenu(windowManager)
                    
                    lx = event.rawX; ly = event.rawY
                    startX = event.rawX; startY = event.rawY
                    updateLayouts()
                    updateColors()
                    true 
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val threshold = 10 * density
                    
                    if (dist > threshold && !hasMovedThreshold) {
                        hasMovedThreshold = true
                        if (wasDockedOnDown) {
                            val displayW = realMetrics.widthPixels
                            val displayH = realMetrics.heightPixels
                            val safe = getSafeAreaRect()
                            
                            var restored = false
                            preDockedRect?.let { prev ->
                                if (prev.width() > 0 && prev.height() > 0 && prev.width() <= displayW && prev.height() <= displayH) {
                                    val isTooLarge = prev.width() > (displayW * 0.8f) || prev.height() > (displayH * 0.8f)
                                    if (!isTooLarge) {
                                        winW = prev.width()
                                        winH = prev.height()
                                        restored = true
                                    }
                                }
                            }
                            
                            if (!restored) {
                                val minW = (120 * density).toInt().coerceAtMost(350)
                                val minH = (80 * density).toInt().coerceAtMost(200)
                                winW = (displayW * 0.5f).toInt().coerceAtLeast(minW)
                                winH = (displayH * 0.5f).toInt().coerceAtLeast(minH)
                            }
                            
                            // Center around current touch cursor
                            winL = (event.rawX - winW / 2).toInt().coerceIn(safe.left, safe.right - winW)
                            winT = (event.rawY - titleBarHeight / 2).toInt().coerceIn(safe.top + titleBarHeight, safe.bottom - 50)
                            
                            setDockMode(false)
                            applyBounds(true)
                            lx = event.rawX; ly = event.rawY
                        }
                    }
                    
                    if (wasDockedOnDown && !hasMovedThreshold) {
                        return@setOnTouchListener true
                    }
                    
                    if (isMaximized) {
                        isMaximized = false
                        preMaximizedRect?.let {
                            winL = it.left; winT = it.top; winW = it.width(); winH = it.height()
                        } ?: run {
                            winW = 800; winH = 600
                        }
                        winL = (event.rawX - winW / 2).toInt()
                        winT = (event.rawY - 20).toInt()
                        lx = event.rawX; ly = event.rawY
                        updateLayouts()
                    }

                    val safe = getSafeAreaRect()
                    
                    var newL = winL + (event.rawX - lx).toInt()
                    var newT = winT + (event.rawY - ly).toInt()
                    
                    val minT = safe.top + titleBarHeight
                    newL = newL.coerceIn(safe.left - winW + 100, safe.right - 100)
                    newT = newT.coerceIn(minT, safe.bottom - 50)
                    
                    winL = newL
                    winT = newT
                    
                    // Enforce Snap Preview Guide checking near edges
                    val snapThreshold = 30 * density
                    val gap = (ThemeManager.getBorderWidth(displayContext) * density).toInt() / 2
                    val hW = safe.left + safe.width() / 2
                    val hH = safe.top + safe.height() / 2
                    val usePill = ThemeManager.usePillForSnapped(displayContext)
                    val snapTop = if (usePill) safe.top else safe.top + titleBarHeight
                    
                    var snapTargetRect: Rect? = null
                    targetSide = null
                    
                    if (event.rawY < safe.top + snapThreshold) {
                        snapTargetRect = Rect(safe.left, safe.top, safe.right, safe.bottom)
                        targetSide = "TopFull"
                    } else if (event.rawX < safe.left + snapThreshold) {
                        snapTargetRect = Rect(safe.left, snapTop, hW - gap, safe.bottom)
                        targetSide = "Left"
                    } else if (event.rawX > safe.right - snapThreshold) {
                        snapTargetRect = Rect(hW + gap, snapTop, safe.right, safe.bottom)
                        targetSide = "Right"
                    } else if (event.rawY > safe.bottom - snapThreshold) {
                        snapTargetRect = Rect(safe.left, hH + gap, safe.right, safe.bottom)
                        targetSide = "Bottom"
                    }
                    
                    if (snapTargetRect != null) {
                        FreeformOverlayService.showSnapGuide(displayId, snapTargetRect)
                    } else {
                        FreeformOverlayService.hideSnapGuide()
                    }
                    
                    lx = event.rawX; ly = event.rawY
                    updateLayouts()
                    applyBounds(false)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    FreeformOverlayService.hideSnapGuide()
                    
                    if (wasDockedOnDown && !hasMovedThreshold) {
                        isInteracting = false
                        updateLayouts()
                        updateColors()
                        return@setOnTouchListener true
                    }
                    
                    val safe = getSafeAreaRect()
                    isInteracting = false
                    
                    if (targetSide != null) {
                        when (targetSide) {
                            "TopFull" -> {
                                preMaximizedRect = Rect(winL, winT, winL + winW, winT + winH)
                                isMaximized = true
                                ShellExecutor.resizeTask(taskId, safe.left, safe.top, safe.right, safe.bottom)
                            }
                            "Left" -> snapToLeft()
                            "Right" -> snapToRight()
                            "Bottom" -> snapToBottom()
                        }
                    } else {
                        applyBounds(true)
                    }
                    
                    ShellExecutor.moveTaskToFront(taskId)
                    endInteraction()
                    true
                }
                else -> false
            }
        }
        titleBarView = root
        root.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    triggerPillInteraction()
                }
            }
            false
        }
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun createVisualFrame() {
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = borderWidth.toFloat()
            isAntiAlias = true
        }
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        frameView = object : View(displayContext) {
            override fun onDraw(canvas: Canvas) {
                val themeMode = ThemeManager.getThemeMode(displayContext)
                val isDarkMode = when(themeMode) {
                    1 -> false
                    2 -> true
                    else -> (displayContext.resources.configuration.uiMode and 
                             android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                             android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                
                val accent = currentDecorationColor
                val isDarkModeActual = if (isFocused) isColorDark(accent) else isDarkMode
                
                val unfocusedBorder = if (isDarkMode) Color.parseColor("#40FFFFFF") else Color.parseColor("#40000000")
                paint.color = if (isFocused) accent else unfocusedBorder
                paint.alpha = if (isFocused) 255 else 100
                
                val bWidth = ThemeManager.getBorderWidth(displayContext) * density
                paint.strokeWidth = if (isDocked) 2 * density else bWidth
                
                val w = width.toFloat(); val h = height.toFloat()
                val rBase = ThemeManager.getRoundness(displayContext) * density
                val r = if (isDocked) rBase / 2 else rBase

                if (isInteracting) {
                    fillPaint.color = accent
                    fillPaint.alpha = 40
                    canvas.drawRoundRect(0f, 0f, w, h, r, r, fillPaint)
                }
                // 2. Draw Dynamic Border
                val screenH = realMetrics.heightPixels
                val isNearBottom = (winT + winH) >= (screenH - (2 * density).toInt())
                
                val path = android.graphics.Path()
                val rTop = 16 * density
                val rBot = if (isNearBottom) 0f else 12 * density
                
                // Start from top-left curve
                path.moveTo(0f, rTop)
                path.quadTo(0f, 0f, rTop, 0f)
                // Top edge to top-right curve
                path.lineTo(w - rTop, 0f)
                path.quadTo(w, 0f, w, rTop)
                // Right edge down
                path.lineTo(w, h - rBot)
                
                if (!isNearBottom) {
                    // Close the bottom with curves
                    path.quadTo(w, h, w - rBot, h)
                    path.lineTo(rBot, h)
                    path.quadTo(0f, h, 0f, h - rBot)
                } else {
                    // Just go to bottom-right corner, then across to bottom-left
                    path.lineTo(w, h)
                    path.lineTo(0f, h)
                }
                // Back up to top-left curve start
                path.lineTo(0f, rTop)
                
                canvas.drawPath(path, paint)
            }
            
            override fun draw(canvas: Canvas) { applyMaskAndDraw(this, canvas) { super.draw(it) } }
        }.apply {
            // Requirement: Professional System Elevation
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    val rBase = ThemeManager.getRoundness(view.context) * density
                    val r = if (isDocked) rBase / 2 else rBase
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            clipToOutline = true // Ensure content doesn't leak out of rounded corners
            
            // Toggle shadow via elevation
            elevation = if (ThemeManager.showShadows(displayContext) && !isDocked && !isMaximized) 20 * density else 0f
            
            setOnClickListener {
                if (isDocked) {
                    onMinimize()
                }
            }
        }
    }

    private fun createResizeStrips() {
        leftStrip = object : View(displayContext) {
            override fun draw(canvas: Canvas) { applyMaskAndDraw(this, canvas) { super.draw(it) } }
        }.apply { 
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.argb(1, 0, 0, 0)) // Captures mouse pointer inputs perfectly while remaining invisible
            setOnTouchListener { _, e -> handleResize(e, true, false, false) } 
        }
        
        rightStrip = object : View(displayContext) {
            override fun draw(canvas: Canvas) { applyMaskAndDraw(this, canvas) { super.draw(it) } }
        }.apply { 
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.argb(1, 0, 0, 0))
            setOnTouchListener { _, e -> handleResize(e, false, true, false) } 
        }
        
        bottomStrip = object : View(displayContext) {
            override fun draw(canvas: Canvas) { applyMaskAndDraw(this, canvas) { super.draw(it) } }
        }.apply { 
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.argb(1, 0, 0, 0))
            setOnTouchListener { _, e -> handleResize(e, false, false, true) } 
        }
    }

    private fun handleResize(event: MotionEvent, left: Boolean, right: Boolean, bottom: Boolean): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> { 
                isInteracting = true
                isResizing = true
                preInteractL = winL
                preInteractT = winT
                preInteractW = winW
                preInteractH = winH
                val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
                preShouldShowPill = isMaximized || (isDocked && usePillForSnapped)
                
                setDockMode(false) // Reset docked state immediately when manually resizing via borders
                ShellExecutor.moveTaskToFront(taskId)
                FreeformOverlayService.requestRefresh()
                rLx = event.rawX; rLy = event.rawY
                updateColors()
                updateLayouts()
                true 
            }
            MotionEvent.ACTION_MOVE -> {
                val screenW = realMetrics.widthPixels
                val screenH = realMetrics.heightPixels
                
                val dx = (event.rawX - rLx).toInt(); val dy = (event.rawY - rLy).toInt()
                val minW = (120 * density).toInt().coerceAtMost(350)
                val minH = (80 * density).toInt().coerceAtMost(200)
                if (left) { 
                    val oldL = winL
                    winL = (winL + dx).coerceIn(0, (oldL + winW) - minW)
                    winW -= (winL - oldL)
                }
                if (right) { winW = (winW + dx).coerceIn(minW, screenW - winL) }
                if (bottom) { winH = (winH + dy).coerceIn(minH, screenH - winT) }
                
                rLx = event.rawX; rLy = event.rawY
                updateLayouts()
                updateColors()
                if (ThemeManager.realtimeResize(displayContext)) {
                    applyBounds(false)
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { 
                isResizing = false
                applyBounds(true)
                endInteraction()
                true
            }
            else -> false
        }
    }

    private fun savePreDockedRect() {
        if (!isDocked) {
            preDockedRect = Rect(winL, winT, winL + winW, winT + winH)
        }
    }

    private fun getSafeAreaRect(): Rect {
        val pos = ThemeManager.getDockPosition(displayContext, displayId)
        val size = ThemeManager.getDockSize(displayContext, displayId)
        val rect = Rect(0, 0, realMetrics.widthPixels, realMetrics.heightPixels)
        
        // Add system status bar height for primary display if no custom top dock
        if (displayId == 0 && pos != 1) {
            val resourceId = displayContext.resources.getIdentifier("status_bar_height", "dimen", "android")
            var statusBarH = 0
            if (resourceId > 0) {
                statusBarH = displayContext.resources.getDimensionPixelSize(resourceId)
            }
            
            // Fallback if resource is 0 or suspiciously small
            if (statusBarH < (20 * density).toInt()) {
                statusBarH = (36 * density).toInt() 
            }
            rect.top += statusBarH
        }

        when (pos) {
            1 -> rect.top += size
            2 -> rect.bottom -= size
            3 -> rect.left += size
            4 -> rect.right -= size
        }
        return rect
    }

    private fun showSnapMenu(anchor: View) {
        val wasActiveForThisTask = (activeSnapMenuTaskId == taskId)
        
        // Always dismiss any showing snap menu first
        dismissActiveSnapMenu(windowManager)
        
        // If it was already showing for this task, we toggled it off and are done
        if (wasActiveForThisTask) {
            return
        }

        val dm = android.util.DisplayMetrics()
        displayContext.display?.getRealMetrics(dm)
        
        val menu = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            val p = (12 * density).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF1A1A1A"))
                cornerRadius = 16 * density
                setStroke((1.5 * density).toInt(), Color.parseColor("#33FFFFFF"))
            }
            elevation = 20 * density
            alpha = 0f
            animate().alpha(1f).setDuration(200).start()
        }

        fun addMultiSnap(zones: List<Triple<Rect, String, (Int, Int, Int, Int) -> Unit>>) {
            val container = FrameLayout(displayContext).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (100 * density).toInt(), 
                    (70 * density).toInt()
                ).apply { bottomMargin = (8 * density).toInt() }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2D2D2D"))
                    cornerRadius = 8 * density
                }
            }

            val iconW = (100 * density).toInt()
            val iconH = (70 * density).toInt()

            for (zone in zones) {
                val relRect = zone.first
                val action = zone.third
                
                val zoneView = View(displayContext).apply {
                    isClickable = true
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener { 
                        action(0, 0, 0, 0)
                        windowManager.removeView(menu)
                    }
                    setOnTouchListener { v, event ->
                        when(event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                v.background = GradientDrawable().apply {
                                    setColor(Color.argb(80, 66, 133, 244))
                                    cornerRadius = 4 * density
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.setBackgroundResource(android.R.drawable.list_selector_background)
                            }
                        }
                        false
                    }
                }
                
                val lp = FrameLayout.LayoutParams(
                    (relRect.width() * iconW / 100),
                    (relRect.height() * iconH / 100)
                ).apply {
                    leftMargin = (relRect.left * iconW / 100)
                    topMargin = (relRect.top * iconH / 100)
                }
                container.addView(zoneView, lp)
                
                val border = View(displayContext).apply {
                    background = GradientDrawable().apply {
                        setStroke(1, Color.parseColor("#33FFFFFF"))
                        cornerRadius = 4 * density
                    }
                }
                container.addView(border, lp)
            }
            menu.addView(container)
        }

        val safe = getSafeAreaRect()
        val sW = safe.width(); val sH = safe.height()
        val titleH = titleBarHeight
        val gap = (ThemeManager.getBorderWidth(displayContext) * density).toInt() / 2

        val hH = safe.top + sH / 2
        val hW = safe.left + sW / 2

        addMultiSnap(listOf(Triple(Rect(5, 5, 95, 95), if (isMaximized) "Restore" else "Full", { _, _, _, _ ->
            if (isMaximized) {
                isMaximized = false
                preMaximizedRect?.let {
                    winL = it.left; winT = it.top; winW = it.width(); winH = it.height()
                    ShellExecutor.resizeTask(taskId, winL, winT, winL + winW, winT + winH)
                }
            } else {
                preMaximizedRect = Rect(winL, winT, winL + winW, winT + winH)
                isMaximized = true
                ShellExecutor.resizeTask(taskId, safe.left, safe.top, safe.right, safe.bottom)
            }
            updateLayouts()
        })))

        // Requirement: Safe Tablet Mode (Calculated width to trigger Tablet UI)
        if (ThemeManager.useTabletMode(displayContext)) {
            addMultiSnap(listOf(Triple(Rect(25, 25, 75, 75), "Tablet UI", { _, _, _, _ ->
                isMaximized = false; setDockMode(false)
                // Chrome/Android Tablet UI triggers at 600dp
                val tabletWidthPx = (600 * density).toInt()
                val tabletHeightPx = (winH).coerceAtLeast((450 * density).toInt())
                
                // Center it horizontally
                winW = tabletWidthPx
                winH = tabletHeightPx
                winL = (realMetrics.widthPixels - winW) / 2
                // winT stays same or shifts if too high
                winT = winT.coerceAtLeast(safe.top + titleBarHeight)
                
                ShellExecutor.resizeTask(taskId, winL, winT, winL + winW, winT + winH)
                updateLayouts()
            })))
        }

        val usePill = ThemeManager.usePillForSnapped(displayContext)
        val snapTop = if (usePill) safe.top else safe.top + titleH

        addMultiSnap(listOf(
            Triple(Rect(5, 5, 48, 95), "Left", { _, _, _, _ -> snapToLeft() }),
            Triple(Rect(52, 5, 95, 95), "Right", { _, _, _, _ -> snapToRight() })
        ))

        addMultiSnap(listOf(
            Triple(Rect(5, 5, 95, 48), "Top", { _, _, _, _ -> snapToTop() }),
            Triple(Rect(5, 52, 95, 95), "Bottom", { _, _, _, _ -> snapToBottom() })
        ))

        addMultiSnap(listOf(
            Triple(Rect(5, 5, 48, 48), "TL", { _, _, _, _ -> 
                isMaximized = false; savePreDockedRect(); setDockMode(true)
                ShellExecutor.resizeTask(taskId, safe.left, snapTop, hW - gap, hH - gap) 
                updateLayouts()
            }),
            Triple(Rect(52, 5, 95, 48), "TR", { _, _, _, _ -> 
                isMaximized = false; savePreDockedRect(); setDockMode(true)
                ShellExecutor.resizeTask(taskId, hW + gap, snapTop, safe.right, hH - gap) 
                updateLayouts()
            }),
            Triple(Rect(5, 52, 48, 95), "BL", { _, _, _, _ -> 
                isMaximized = false; savePreDockedRect(); setDockMode(true)
                ShellExecutor.resizeTask(taskId, safe.left, hH + gap, hW - gap, safe.bottom) 
                updateLayouts()
            }),
            Triple(Rect(52, 52, 95, 95), "BR", { _, _, _, _ -> 
                isMaximized = false; savePreDockedRect(); setDockMode(true)
                ShellExecutor.resizeTask(taskId, hW + gap, hH + gap, safe.right, safe.bottom) 
                updateLayouts()
            })
        ))

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)

        // Requirement: Use TYPE_APPLICATION_OVERLAY to ensure it is added to the system overlay layer successfully without requiring accessibility token validation
        val params = WindowManager.LayoutParams(
            (124 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorLoc[0] + (anchor.width - (124 * density).toInt()) / 2
            y = anchorLoc[1] + anchor.height + (4 * density).toInt()
        }
        
        try {
            activeSnapMenu = menu
            activeSnapMenuTaskId = taskId
            
            windowManager.addView(menu, params)
            menu.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    dismissActiveSnapMenu(windowManager)
                    true
                } else false
            }
        } catch (e: Exception) {}
    }

    private fun applyMaskAndDraw(v: View, canvas: Canvas, drawAction: (Canvas) -> Unit) {
        if (!isFocused && occluders.isNotEmpty()) {
            canvas.save()
            for (oc in occluders) {
                val l = (oc.left - winL).toFloat()
                val t = (oc.top - winT + titleBarHeight).toFloat()
                val r = (oc.right - winL).toFloat()
                val b = (oc.bottom - winT + titleBarHeight).toFloat()
                
                if (r > 0 && b > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val path = android.graphics.Path()
                        val radius = 12 * density
                        path.addRoundRect(l, t, r, b, radius, radius, android.graphics.Path.Direction.CW)
                        canvas.clipOutPath(path)
                    } else {
                        canvas.clipOutRect(l, t, r, b)
                    }
                }
            }
            drawAction(canvas)
            canvas.restore()
        } else {
            drawAction(canvas)
        }
    }
    
    companion object {
        // Smart Icon Cache (PackageName -> <Bitmap, Color>)
        // Max 50 icons to save memory
        private val iconCache = LruCache<String, Pair<Bitmap, Int>>(50)
        
        internal var activeSnapMenu: View? = null
        internal var activeSnapMenuTaskId: Int = -1
        
        fun dismissActiveSnapMenu(windowManager: WindowManager) {
            activeSnapMenu?.let { menu ->
                try {
                    if (menu.parent != null) {
                        windowManager.removeView(menu)
                    }
                } catch (e: Exception) {}
            }
            activeSnapMenu = null
            activeSnapMenuTaskId = -1
        }
    }
}
