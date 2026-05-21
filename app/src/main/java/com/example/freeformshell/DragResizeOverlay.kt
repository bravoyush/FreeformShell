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
import android.view.ViewTreeObserver
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
    
    var isActivelyDraggingOrResizing = false
        internal set(value) {
            if (field != value) {
                field = value
                FreeformOverlayService.getInstance()?.overlays?.values?.forEach { overlay ->
                    if (overlay.taskId != taskId) {
                        overlay.updateLayouts()
                    }
                }
            }
        }
    internal var preInteractL = 200
    internal var preInteractT = 200
    internal var preInteractW = 800
    internal var preInteractH = 1000
    internal var preShouldShowPill = false
    var isResizing = false
        internal set
    private var lastShouldShowPill: Boolean? = null
    private var isFocused = false
    var isHovered = false
        private set
    var isActuallyFocused = false
        private set
    internal var isDocked = false
    internal var isMaximized = false
    
    private var preMaximizedRect: Rect? = null
    private var preDockedRect: Rect? = null
    var occluders: List<Rect> = emptyList()
        private set
    private val titleBarClipPath = android.graphics.Path()
    private val frameClipPath = android.graphics.Path()
    private var lastCornerDetected: String? = null
    private var cornerDetectStartTime: Long = 0L
    private var currentEdgeZone: String? = null
    private var edgeHoldStartTime: Long = 0L
    private var pendingEdgeZone: String? = null
    private var pendingEdgeZoneStartTime: Long = 0L
    private var snapTargetRect: Rect? = null

    fun setActivityName(name: String?) {
        this.activityName = name
    }

    // Current window bounds in PIXELS
    internal var winL = 200
    internal var winT = 200
    internal var winW = 800
    internal var winH = 1000

    private var resizeJob: Job? = null
    private var lastShellTime = 0L
    internal val density = displayContext.resources.displayMetrics.density
    internal val titleBarHeight = (40 * density).toInt()
    private val borderWidth = (4 * density).toInt()
    private val touchStripWidth = (16 * density).toInt()
    private var lastManualResizeTime = 0L
    private var activeResizeLeft = false
    private var activeResizeRight = false
    private var activeResizeBottom = false
    private var isLeftHandleHovered = false
    private var isRightHandleHovered = false
    private var rLx = 0f
    private var rLy = 0f
    
    // The 5-window architecture: Bar, Frame, and 3 Resize Strips
    private var titleBarView: View? = null
    private var frameView: View? = null
    private var leftStrip: View? = null
    private var rightStrip: View? = null
    private var bottomStrip: View? = null
    private var bottomLeftHandle: View? = null
    private var bottomRightHandle: View? = null
    private val previewDrawable = android.graphics.drawable.GradientDrawable()
    
    internal var lastInteractionTime = 0L
    
    private var isPillShrunk = false
    private val shrinkHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val shrinkRunnable = Runnable {
        isHovered = false
        isInteracting = false
        updatePillShrink(true)
    }

    fun triggerPillInteraction(extendTimer: Boolean = true, delayMs: Long = 3000) {
        lastInteractionTime = System.currentTimeMillis()
        updatePillShrink(false)
        
        shrinkHandler.removeCallbacks(shrinkRunnable)
        if (extendTimer) {
            shrinkHandler.postDelayed(shrinkRunnable, delayMs)
        }
    }

    private var lastAppliedScale = 1.0f
    private var lastAppliedAlpha = 1.0f

    fun isCurrentlyShrunk(): Boolean {
        val globalEnabled = displayContext.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE).getBoolean("pill_auto_shrink_global", true)
        val displayEnabled = ThemeManager.getPillAutoShrink(displayContext, displayId)
        val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
        val shouldShowPill = isMaximized || (isDocked && usePillForSnapped)
        return globalEnabled && displayEnabled && shouldShowPill && !isInteracting && !isHovered && isPillShrunk
    }

    fun updatePillShrink(shrink: Boolean) {
        isPillShrunk = shrink
        val style = ThemeManager.getPillShrinkStyle(displayContext)
        titleBarView?.let { v ->
            if (style == 0) { // Scale Transform style
                val isShrunk = isCurrentlyShrunk()
                val targetScale = if (isShrunk) {
                    ThemeManager.getPillInactiveScale(displayContext, displayId) / 100f
                } else {
                    1.0f
                }
                val targetAlpha = if (isShrunk) 0.4f else 1.0f
                
                if (lastAppliedScale == targetScale && lastAppliedAlpha == targetAlpha) {
                    return@let
                }
                lastAppliedScale = targetScale
                lastAppliedAlpha = targetAlpha
                
                v.pivotX = v.width / 2f
                v.pivotY = v.height / 2f
                v.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    .setDuration(if (isShrunk) 400L else 200L)
                    .withLayer()
                    .start()
            } else { // Resizing style
                v.animate().cancel()
                v.scaleX = 1.0f
                v.scaleY = 1.0f
                v.alpha = 1.0f
                lastAppliedScale = 1.0f
                lastAppliedAlpha = 1.0f
            }
        }
        updateLayouts()
        updateColors()
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
        isActuallyFocused = focused
        
        var changed = false
        if (isFocused != focused) {
            isFocused = focused
            changed = true
        }
        
        if (changed && !focused) {
            isHovered = false
            isInteracting = false
            isActivelyDraggingOrResizing = false
        }
        
        val occludersChanged = occluders != topOccluders
        if (occludersChanged) {
            occluders = topOccluders
        }
        
        if (changed || occludersChanged) {
            updateColors()
            titleBarView?.invalidate()
            frameView?.invalidate()
            leftStrip?.invalidate()
            rightStrip?.invalidate()
            bottomStrip?.invalidate()
            
            titleBarView?.requestLayout()
            leftStrip?.requestLayout()
            rightStrip?.requestLayout()
            bottomStrip?.requestLayout()
            
            if (focused) {
                try {
                    titleBarView?.let { if (it.parent != null) windowManager.updateViewLayout(it, it.layoutParams) }
                    frameView?.let { if (it.parent != null) windowManager.updateViewLayout(it, it.layoutParams) }
                } catch (e: Exception) {
                    Log.e("DragResizeOverlay", "Failed to update Z-order", e)
                }
            }
            
            updateTouchableFlags()
        }
        
        // Focus changes (clicking) should NEVER unshrink the pill!
        // Only hover or touch interaction should expand the pill.
        // Background apps (unfocused) are always kept shrunk.
        if (!focused) {
            updatePillShrink(true)
        } else {
            updatePillShrink(isPillShrunk)
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
            if (docked) {
                triggerPillInteraction(extendTimer = true, delayMs = 1500)
            }
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
        val snapTop = safe.top
        
        val targetRect = when (side) {
            "Left" -> Rect(safe.left, snapTop, hW - gap, safe.bottom)
            "Right" -> Rect(hW + gap, snapTop, safe.right, safe.bottom)
            "Top" -> Rect(safe.left, snapTop, safe.right, hH - gap)
            "Bottom" -> Rect(safe.left, hH + gap, safe.right, safe.bottom)
            else -> null
        }
        if (targetRect != null) {
            winL = targetRect.left
            winT = targetRect.top
            winW = targetRect.width()
            winH = targetRect.height()
        }
        
        isMaximized = false; savePreDockedRect(); setDockMode(true)
        CoroutineScope(Dispatchers.IO).launch {
            if (targetRect != null) {
                ShellExecutor.resizeTask(taskId, targetRect.left, targetRect.top, targetRect.right, targetRect.bottom)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                FreeformOverlayService.getInstance()?.triggerFastPollingSequence()
            }
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
            var finalBgColor = if (isFocused) {
                baseColor
            } else {
                // Mix app color with surface color for unfocused state
                // Use 90% surface to keep it clean and avoid "muddy" colors (especially for yellow icons)
                val surface = if (isDarkMode) Color.parseColor("#1E1E1E") else Color.parseColor("#F5F5F5")
                blendColors(baseColor, surface, 0.9f) 
            }
            
            currentDecorationColor = finalBgColor

            if (isInteracting) {
                // Make title bar transparent/translucent during drag/resize for absolute visual elegance and zero distraction
                finalBgColor = Color.argb(128, Color.red(finalBgColor), Color.green(finalBgColor), Color.blue(finalBgColor))
            }

            val isDark = isColorDark(finalBgColor)
            val contentColor = if (isDark) Color.WHITE else Color.BLACK
            val buttonBg = if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(60, 0, 0, 0)

            val bg = GradientDrawable().apply {
                if (shouldShowPill) {
                    val style = ThemeManager.getPillShrinkStyle(displayContext)
                    val isShrunk = isCurrentlyShrunk()
                    val h = if (style == 1 && isShrunk) (8 * density).toInt() else (40 * density).toInt()
                    cornerRadius = h / 2f
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
            val hasShadows = ThemeManager.showShadows(displayContext)
            v.elevation = if (hasShadows && !isDocked && !isMaximized && !isInteracting) {
                if (isFocused) 24f * density else 8f * density
            } else {
                0f
            }
        }
    }

    private fun endInteraction() {
        isInteracting = false
        isActivelyDraggingOrResizing = false
        lastInteractionTime = System.currentTimeMillis()
        updateLayouts()
        updateColors()
        
        FreeformOverlayService.getInstance()?.triggerFastPollingSequence()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isInteracting) {
                updateLayouts()
            }
        }, 510)
    }

    fun updateFromSystem(l: Int, t: Int, w: Int, h: Int, forcedTitleTop: Int? = null) {
        if (!isInteracting) {
            val recentlyInteracted = (System.currentTimeMillis() - lastInteractionTime) < 400
            if (recentlyInteracted) {
                // Ignore stale bounds updates from system dumpsys right after user interaction
                return
            }
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
        if (titleBarView?.parent != null) return
        try {
            val initialDecorY = winT - titleBarHeight
            val initialFrameH = winH + titleBarHeight + borderWidth
            
            if (frameView?.parent == null) 
                windowManager.addView(frameView, createParams(winW + (borderWidth * 2), initialFrameH, winL - borderWidth, initialDecorY - borderWidth, false))
            if (titleBarView?.parent == null) 
                windowManager.addView(titleBarView, createParams(winW, titleBarHeight, winL, initialDecorY, true))
            if (leftStrip?.parent == null) 
                windowManager.addView(leftStrip, createParams(touchStripWidth, winH, winL - touchStripWidth/2, winT, true))
            if (rightStrip?.parent == null) 
                windowManager.addView(rightStrip, createParams(touchStripWidth, winH, winL + winW - touchStripWidth/2, winT, true))
            if (bottomStrip?.parent == null) 
                windowManager.addView(bottomStrip, createParams(winW, touchStripWidth, winL, winT + winH - touchStripWidth/2, true))
            
            val handleTouchSize = (48 * density).toInt()
            if (bottomLeftHandle?.parent == null) 
                windowManager.addView(bottomLeftHandle, createParams(handleTouchSize, handleTouchSize, winL - handleTouchSize/2, winT + winH - handleTouchSize/2, true))
            if (bottomRightHandle?.parent == null) 
                windowManager.addView(bottomRightHandle, createParams(handleTouchSize, handleTouchSize, winL + winW - handleTouchSize/2, winT + winH - handleTouchSize/2, true))
            
            titleBarView?.let { setupTouchRegionHelper(it) }
            leftStrip?.let { setupTouchRegionHelper(it) }
            rightStrip?.let { setupTouchRegionHelper(it) }
            bottomStrip?.let { setupTouchRegionHelper(it) }
            bottomLeftHandle?.let { setupTouchRegionHelper(it) }
            bottomRightHandle?.let { setupTouchRegionHelper(it) }
            
            updatePillShrink(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing windows", e)
        }
    }

    fun bringToFront() {
        if (isInteracting) return
        
        val views = listOfNotNull(frameView, titleBarView, leftStrip, rightStrip, bottomStrip, bottomLeftHandle, bottomRightHandle)
        for (v in views) {
            if (v.parent != null) {
                try {
                    val lp = v.layoutParams as WindowManager.LayoutParams
                    windowManager.updateViewLayout(v, lp)
                } catch (e: Exception) {}
            }
        }
        
        activeSnapMenu?.let { menu ->
            if (menu.parent != null) {
                try {
                    val lp = menu.layoutParams as WindowManager.LayoutParams
                    windowManager.updateViewLayout(menu, lp)
                } catch (e: Exception) {}
            }
        }
        updateLayouts()
    }

    fun pushToFrontWithoutRecreation() {
        val views = listOfNotNull(frameView, titleBarView, leftStrip, rightStrip, bottomStrip, bottomLeftHandle, bottomRightHandle)
        for (v in views) {
            if (v.parent != null) {
                try {
                    val lp = v.layoutParams as WindowManager.LayoutParams
                    windowManager.updateViewLayout(v, lp)
                } catch (e: Exception) {}
            }
        }
    }

    fun hide() {
        shrinkHandler.removeCallbacks(shrinkRunnable)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                cleanupTouchRegions()
                activeSnapMenu?.let { if (it.parent != null) windowManager.removeView(it); activeSnapMenu = null }
                titleBarView?.let { if (it.parent != null) windowManager.removeView(it) }
                frameView?.let { if (it.parent != null) windowManager.removeView(it) }
                leftStrip?.let { if (it.parent != null) windowManager.removeView(it) }
                rightStrip?.let { if (it.parent != null) windowManager.removeView(it) }
                bottomStrip?.let { if (it.parent != null) windowManager.removeView(it) }
                bottomLeftHandle?.let { if (it.parent != null) windowManager.removeView(it) }
                bottomRightHandle?.let { if (it.parent != null) windowManager.removeView(it) }
            } catch (e: Exception) {}
        }
    }

    private fun createParams(w: Int, h: Int, x: Int, y: Int, touchable: Boolean): WindowManager.LayoutParams {
        val safe = getSafeAreaRect()
        val clampedX = x.coerceIn(0, realMetrics.widthPixels - w)
        val clampedY = y.coerceIn(0, realMetrics.heightPixels - h)
        
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

    internal fun updateLayouts(updateVisuals: Boolean = true) {
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
            
            val useL = winL
            val useT = winT
            val useW = winW
            val useH = winH
            
            val safe = getSafeAreaRect()
            val decorY = (useT - titleBarHeight).coerceIn(safe.top - titleBarHeight, safe.bottom - titleBarHeight)
            
            // Requirement: If resizing, draw the translucent boundary guide dynamically, but keep the headers and input handlers completely stationary to avoid input dropouts or glitchy visual jumps!
            if (isInteracting && isResizing) {
                frameView?.let { 
                    val realtime = ThemeManager.realtimeResize(displayContext)
                    if (realtime) {
                        // When real-time resize is enabled, hide the translucent outline!
                        updateWindow(it, 0, 0, -1000, -1000, false)
                    } else {
                        // Optimizing redraw: Make frameView fullscreen and only use invalidate() for butter-smooth GPU rendering!
                        val screenW = realMetrics.widthPixels
                        val screenH = realMetrics.heightPixels
                        updateWindow(it, screenW, screenH, 0, 0, false)
                        it.elevation = 0f // Suspend heavy shadow overhead during resizing
                        it.clipToOutline = false // Suspend clipping overhead during resizing
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
                    bottomLeftHandle?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    bottomRightHandle?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                } else {
                    val preDecorY = (preInteractT - titleBarHeight).coerceIn(safe.top, safe.bottom - titleBarHeight)
                    titleBarView?.let { updateWindow(it, preInteractW, titleBarHeight, preInteractL, preDecorY, true) }
                    
                    leftStrip?.let { updateWindow(it, touchStripWidth, preInteractH, preInteractL - touchStripWidth/2, preInteractT, true) }
                    rightStrip?.let { updateWindow(it, touchStripWidth, preInteractH, preInteractL + preInteractW - touchStripWidth/2, preInteractT, true) }
                    bottomStrip?.let { updateWindow(it, preInteractW, touchStripWidth, preInteractL, preInteractT + preInteractH - touchStripWidth/2, true) }
                    
                    val handleTouchSize = (48 * density).toInt()
                    bottomLeftHandle?.let { updateWindow(it, handleTouchSize, handleTouchSize, preInteractL - handleTouchSize/2, preInteractT + preInteractH - handleTouchSize/2, true) }
                    bottomRightHandle?.let { updateWindow(it, handleTouchSize, handleTouchSize, preInteractL + preInteractW - handleTouchSize/2, preInteractT + preInteractH - handleTouchSize/2, true) }
                }
                
                if (updateVisuals) updateColors()
                return
            }

            if (shouldShowPill) {
                titleBarView?.let { v ->
                    val tv = v.findViewById<TextView>(textViewId)
                    val isPhoneScreen = displayId == 0
                    val hideText = if (isPhoneScreen) {
                        useW < (130 * density).toInt()
                    } else {
                        useW < (250 * density).toInt()
                    }
                    
                    val label = tv?.text?.toString() ?: ""
                    
                    // Requirement: Content-Aware Pill Width with 40% Max Limit (90% on phone)
                    // Base width on text length + buttons + padding
                    val textW = if (hideText) 0 else (label.length * 10 * density).toInt()
                    val buttonsW = (80 * density).toInt() 
                    val contentW = textW + buttonsW + (24 * density).toInt()
                    
                    val isQuarterSnap = isDocked && useW < (realMetrics.widthPixels * 0.6f)
                    val maxRatio = if (isPhoneScreen) 0.9f else (if (isQuarterSnap) 0.7f else 0.4f)
                    val maxW = (useW * maxRatio).toInt()
                    val minW = if (hideText) (72 * density).toInt() else (110 * density).toInt()
                    val pillW = contentW.coerceIn(minW, Math.max(minW, maxW))
                    
                    val pillH = (40 * density).toInt() // Increased height for better rounding look
                    
                    val style = ThemeManager.getPillShrinkStyle(displayContext)
                    val isShrunk = isCurrentlyShrunk()
                    val finalW = if (style == 1 && isShrunk) (60 * density).toInt() else pillW
                    val finalH = if (style == 1 && isShrunk) (8 * density).toInt() else pillH
                    
                    val pillX = useL + (useW - finalW) / 2
                    val pillY = (useT + (4 * density).toInt()).coerceIn(safe.top + (4 * density).toInt(), safe.bottom - finalH)
                    
                    val vg = v as android.view.ViewGroup
                    // Show/hide subviews smoothly
                    android.transition.TransitionManager.beginDelayedTransition(vg)
                    for (i in 0 until vg.childCount) {
                        val child = vg.getChildAt(i)
                        if (style == 1 && isShrunk) {
                            child.visibility = View.GONE
                        } else {
                            if (child.id == textViewId) {
                                child.visibility = if (hideText) View.GONE else View.VISIBLE
                            } else {
                                child.visibility = View.VISIBLE
                            }
                        }
                    }
                    
                    updateWindow(v, finalW, finalH, pillX, pillY, true)
                }
                
                // Hide other elements by setting size to 0
                frameView?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                val showExternalHandles = (ThemeManager.getPairedScalingGlobal(displayContext) || ThemeManager.getPairedScaling(displayContext, displayId)) && !isInteracting
                if (showExternalHandles) {
                    leftStrip?.let { updateWindow(it, touchStripWidth, useH, useL - touchStripWidth/2, useT, true) }
                    rightStrip?.let { updateWindow(it, touchStripWidth, useH, useL + useW - touchStripWidth/2, useT, true) }
                    bottomStrip?.let { updateWindow(it, useW, touchStripWidth, useL, useT + useH - touchStripWidth/2, true) }
                } else {
                    leftStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    rightStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    bottomStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                }
                bottomLeftHandle?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                bottomRightHandle?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
            } else {
                // STANDARD MODE: Show full title bar and frame
                if (isInteracting) {
                    // PERFORMANCE FIX: Skip updating secondary strips during active dragging, but keep border visible!
                    frameView?.let {
                        val fw = useW + (borderWidth * 2)
                        val fh = useH + titleBarHeight + borderWidth
                        val fx = useL - borderWidth
                        val fy = decorY - borderWidth
                        updateWindow(it, fw, fh, fx, fy, false)
                        it.invalidate()
                    }
                    leftStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    rightStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    bottomStrip?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    bottomLeftHandle?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                    bottomRightHandle?.let { updateWindow(it, 0, 0, -1000, -1000, false) }
                } else {
                    frameView?.let { 
                        if (isDocked) {
                            updateWindow(it, 0, 0, -1000, -1000, false)
                        } else {
                            val fw = useW + (borderWidth * 2)
                            val fh = useH + titleBarHeight + borderWidth
                            val fx = useL - borderWidth
                            val fy = decorY - borderWidth
                            updateWindow(it, fw, fh, fx, fy, false)
                            it.invalidate() // Force redraw of the border
                        }
                    }
                    val showLeft = !isResizing || activeResizeLeft
                    val showRight = !isResizing || activeResizeRight
                    val showBottom = !isResizing || activeResizeBottom
                    
                    leftStrip?.let { 
                        if (showLeft) updateWindow(it, touchStripWidth, useH, useL - touchStripWidth/2, useT, true) 
                        else updateWindow(it, 0, 0, -1000, -1000, false)
                    }
                    rightStrip?.let { 
                        if (showRight) updateWindow(it, touchStripWidth, useH, useL + useW - touchStripWidth/2, useT, true) 
                        else updateWindow(it, 0, 0, -1000, -1000, false)
                    }
                    bottomStrip?.let { 
                        if (showBottom) updateWindow(it, useW, touchStripWidth, useL, useT + useH - touchStripWidth/2, true) 
                        else updateWindow(it, 0, 0, -1000, -1000, false)
                    }
                    
                    val handleTouchSize = (48 * density).toInt()
                    bottomLeftHandle?.let { 
                        if (showLeft || showBottom) updateWindow(it, handleTouchSize, handleTouchSize, useL - handleTouchSize/2, useT + useH - handleTouchSize/2, true)
                        else updateWindow(it, 0, 0, -1000, -1000, false)
                    }
                    bottomRightHandle?.let { 
                        if (showRight || showBottom) updateWindow(it, handleTouchSize, handleTouchSize, useL + useW - handleTouchSize/2, useT + useH - handleTouchSize/2, true)
                        else updateWindow(it, 0, 0, -1000, -1000, false)
                    }
                }
                titleBarView?.let { updateWindow(it, useW, titleBarHeight, useL, decorY, true) }
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
            
            if (isInteracting) {
                pushToFrontWithoutRecreation()
            }
            
            if (updateVisuals) {
                updateColors() // Apply dynamic branding
            }
        } catch (e: Exception) {}
    }

    private fun isAnyOtherOverlayDragging(): Boolean {
        val service = FreeformOverlayService.getInstance() ?: return false
        return service.overlays.values.any { 
            it.taskId != taskId && it.currentDisplayId == currentDisplayId && it.isActivelyDraggingOrResizing 
        }
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
            
            val hideOnLauncher = ThemeManager.getHideOnLauncherActive(displayContext)
            val launcherPkg = ThemeManager.getDockLauncherPackage(displayContext)
            val isImmersion = if (isInteracting) {
                false // Bypasses slow shell execution queries during active touch resizing/moving gestures for flawless 120 FPS
            } else {
                val cachedFocus = FreeformOverlayService.getInstance()?.currentFocusedPackage ?: ""
                hideOnLauncher && launcherPkg.isNotEmpty() && cachedFocus == launcherPkg
            }
            
            // Fading down to faint 15% opacity if immersion is active, or 40% if actively interacting (dragging/moving)
            val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
            val shouldShowPill = isMaximized || (isDocked && usePillForSnapped)
            
            val isTitle = v == titleBarView
            val isStyle0 = isTitle && shouldShowPill && ThemeManager.getPillShrinkStyle(displayContext) == 0
            val baseAlpha = if (isTitle) {
                if (shouldShowPill) {
                    if (isStyle0 && isCurrentlyShrunk()) 0.4f else 1.0f
                } else {
                    ThemeManager.getTitleBarOpacity(displayContext) / 100f
                }
            } else {
                1.0f
            }
            
            val isHandle = v == bottomLeftHandle || v == bottomRightHandle
            val isThisHandleHovered = (v == bottomLeftHandle && isLeftHandleHovered) || (v == bottomRightHandle && isRightHandleHovered)
            val isThisHandleActive = (v == bottomLeftHandle && activeResizeLeft && activeResizeBottom) || 
                                     (v == bottomRightHandle && activeResizeRight && activeResizeBottom)
            
            val targetAlpha = if (isTitle && !shouldShowPill) {
                if (isAnyOtherOverlayDragging()) 0.40f else baseAlpha
            } else if (isImmersion) {
                0.15f
            } else if (isHandle) {
                // Show full opacity if hovered or active, otherwise semi-transparent
                if (isThisHandleActive || isThisHandleHovered) 1.0f else 0.35f
            } else if (isInteracting) {
                0.40f
            } else if (isAnyOtherOverlayDragging()) {
                0.40f
            } else {
                baseAlpha
            }
            
            if (v.alpha != targetAlpha) {
                // If it is Style 0 and target is baseAlpha, skip direct assignment
                // so property animations are completely unimpeded.
                if (!(isStyle0 && targetAlpha == baseAlpha)) {
                    v.alpha = targetAlpha
                    if (isTitle) {
                        lastAppliedAlpha = targetAlpha
                    }
                }
            }
            
            // Force not-touchable if immersion is active
            val actualTouchable = if (isImmersion) false else touchable
            
            if (v.parent != null) {
                val lp = v.layoutParams as WindowManager.LayoutParams
                var changed = false
                if (lp.width != w) { lp.width = w; changed = true }
                if (lp.height != h) { lp.height = h; changed = true }
                if (lp.x != x) { lp.x = x; changed = true }
                if (lp.y != y) { lp.y = y; changed = true }
                if (lp.windowAnimations != 0) { lp.windowAnimations = 0; changed = true }
                
                val newFlags = if (actualTouchable) {
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
                windowManager.addView(v, createParams(w, h, x, y, actualTouchable))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating overlay window", e)
        }
    }

    internal fun applyBounds(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastShellTime < 16) return
        lastShellTime = now
        
        resizeJob?.cancel()
        resizeJob = CoroutineScope(Dispatchers.IO).launch {
            if (!force) delay(8) // Minimal stabilization delay
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
            triggerPillInteraction(extendTimer = false)
            val action = event.action
            val toolType = event.getToolType(0)
            val isTouch = toolType == MotionEvent.TOOL_TYPE_FINGER
            
            Log.d("DragResizeOverlayTouch", "titleBarTouch: taskId=$taskId, action=${MotionEvent.actionToString(action)}, rawX=${event.rawX}, rawY=${event.rawY}, toolType=$toolType")
            
            when (action) {
                MotionEvent.ACTION_DOWN -> { 
                    isInteracting = true
                    hasMovedThreshold = false
                    wasDockedOnDown = isDocked
                    targetSide = null
                    
                    FreeformOverlayService.getInstance()?.elevateOverlayToTop(taskId)
                    
                    preInteractL = winL
                    preInteractT = winT
                    preInteractW = winW
                    preInteractH = winH
                    val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
                    preShouldShowPill = isMaximized || (isDocked && usePillForSnapped)
                    
                    // Finger Defer Fix: if enabled via CompatibilityManager, skip moveTaskToFront on
                    // finger ACTION_DOWN to prevent InputDispatcher ACTION_CANCEL killing the drag.
                    val deferFocusForTouch = CompatibilityManager.isFingerDeferFocusEnabled(displayContext)
                    if (!isTouch || !deferFocusForTouch) {
                        ShellExecutor.moveTaskToFront(taskId)
                    }
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
                        isActivelyDraggingOrResizing = true
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
                            
                            // Pin touch cursor at the exact same relative horizontal ratio on the title bar
                            val touchXRatio = ((startX - preInteractL).toFloat() / preInteractW.toFloat()).coerceIn(0f, 1f)
                            val maxL = (safe.right - winW).coerceAtLeast(safe.left)
                            winL = (event.rawX - winW * touchXRatio).toInt().coerceIn(safe.left, maxL)
                            
                            val minT = safe.top + titleBarHeight
                            val maxT = (safe.bottom - 50).coerceAtLeast(minT)
                            winT = (event.rawY - titleBarHeight / 2).toInt().coerceIn(minT, maxT)
                            
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
                        // Pin touch cursor at the exact same relative horizontal ratio on the title bar
                        val safe = getSafeAreaRect()
                        val touchXRatio = ((startX - preInteractL).toFloat() / preInteractW.toFloat()).coerceIn(0f, 1f)
                        val maxL = (safe.right - winW).coerceAtLeast(safe.left)
                        winL = (event.rawX - winW * touchXRatio).toInt().coerceIn(safe.left, maxL)
                        
                        val minT = safe.top + titleBarHeight
                        val maxT = (safe.bottom - 50).coerceAtLeast(minT)
                        winT = (event.rawY - titleBarHeight / 2).toInt().coerceIn(minT, maxT)
                        lx = event.rawX; ly = event.rawY
                        updateLayouts()
                    }

                    val safe = getSafeAreaRect()
                    
                    var newL = winL + (event.rawX - lx).toInt()
                    var newT = winT + (event.rawY - ly).toInt()
                    
                    val minT = safe.top + titleBarHeight
                    val maxL = (safe.right - 100).coerceAtLeast(safe.left - winW + 100)
                    newL = newL.coerceIn(safe.left - winW + 100, maxL)
                    val maxT = (safe.bottom - 50).coerceAtLeast(minT)
                    newT = newT.coerceIn(minT, maxT)
                    
                    winL = newL
                    winT = newT
                    
                    // Enforce Snap Preview Guide checking near edges
                    val gap = (ThemeManager.getBorderWidth(displayContext) * density).toInt() / 2
                    val hW = safe.left + safe.width() / 2
                    val hH = safe.top + safe.height() / 2
                    val snapTop = safe.top
                    
                    snapTargetRect = null
                    targetSide = null
                    
                    // --- ADVANCED WINDOW SNAPPING OVERHAUL ---
                    val otherDocked = FreeformOverlayService.getInstance()?.overlays?.values?.filter { 
                        it.currentDisplayId == displayId && it.isDocked && it.taskId != taskId 
                    } ?: emptyList()
                    val hasOtherDocked = otherDocked.isNotEmpty()

                    // Dynamically calculate which screen borders are currently touched by already snapped/docked windows
                    val touchThresh = gap * 4
                    val isLeftBorderTouched = otherDocked.any { it.winL <= safe.left + touchThresh }
                    val isRightBorderTouched = otherDocked.any { (it.winL + it.winW) >= safe.right - touchThresh }
                    val isTopBorderTouched = otherDocked.any { it.winT <= safe.top + titleBarHeight + touchThresh }
                    val isBottomBorderTouched = otherDocked.any { (it.winT + it.winH) >= safe.bottom - touchThresh }

                    // Fetch snapping sensitivity from ThemeManager (default to 100dp)
                    val snapSensitivity = ThemeManager.getSnapSensitivity(displayContext, displayId)
                    val generousThresh = snapSensitivity * density
                    val occupiedThresh = (snapSensitivity * 0.3f) * density
                    val cornerThresh = snapSensitivity * density

                    // Untouched borders use a very generous threshold for immediate and natural snap triggering.
                    // Already occupied/touched borders use a small threshold to avoid accidental overlaps.
                    
                    val leftThresh = if (isLeftBorderTouched) occupiedThresh else generousThresh
                    val rightThresh = if (isRightBorderTouched) occupiedThresh else generousThresh
                    val topThresh = if (isTopBorderTouched) occupiedThresh else generousThresh
                    val bottomThresh = if (isBottomBorderTouched) occupiedThresh else generousThresh

                    val isLandscape = safe.width() >= safe.height()
                    val cornerThreshX = generousThresh
                    val cornerThreshY = if (isLandscape) generousThresh else (40f * density)

                    val isNearTop = event.rawY < safe.top + cornerThreshY
                    val isNearBottom = event.rawY > safe.bottom - cornerThreshY || (winT + winH) >= safe.bottom - (20 * density).toInt()
                    val isNearLeft = event.rawX < safe.left + cornerThreshX
                    val isNearRight = event.rawX > safe.right - cornerThreshX

                    var detectedCorner: String? = null
                    if (isNearTop && isNearLeft) detectedCorner = "TopLeft"
                    else if (isNearTop && isNearRight) detectedCorner = "TopRight"
                    else if (isNearBottom && isNearLeft) detectedCorner = "BottomLeft"
                    else if (isNearBottom && isNearRight) detectedCorner = "BottomRight"

                    // Determine the candidate boundary edge or corner zone
                    val rawCandidateZone = if (detectedCorner != null) {
                        detectedCorner
                    } else if (event.rawY < safe.top + topThresh) {
                        "Top"
                    } else if (event.rawX < safe.left + leftThresh) {
                        "Left"
                    } else if (event.rawX > safe.right - rightThresh) {
                        "Right"
                    } else if (event.rawY > safe.bottom - bottomThresh || (winT + winH) >= safe.bottom - bottomThresh) {
                        "Bottom"
                    } else {
                        null
                    }

                    // --- DEBOUNCE / HYSTERESIS FILTER ---
                    val now = System.currentTimeMillis()
                    if (rawCandidateZone == currentEdgeZone) {
                        // Stable in current zone, clear pending
                        pendingEdgeZone = null
                        pendingEdgeZoneStartTime = 0L
                    } else {
                        if (currentEdgeZone == null) {
                            // Switch to new zone instantly if we had no active zone
                            currentEdgeZone = rawCandidateZone
                            edgeHoldStartTime = now
                            pendingEdgeZone = null
                            pendingEdgeZoneStartTime = 0L
                        } else {
                            // We have an active zone. Check if the raw candidate zone is already pending
                            if (rawCandidateZone == pendingEdgeZone) {
                                if (now - pendingEdgeZoneStartTime >= 120) { // 120ms stable window
                                    currentEdgeZone = rawCandidateZone
                                    edgeHoldStartTime = pendingEdgeZoneStartTime
                                    pendingEdgeZone = null
                                    pendingEdgeZoneStartTime = 0L
                                }
                            } else {
                                // Start tracking new pending zone
                                pendingEdgeZone = rawCandidateZone
                                pendingEdgeZoneStartTime = now
                            }
                        }
                    }

                    val elapsedEdgeTime = if (edgeHoldStartTime > 0L) System.currentTimeMillis() - edgeHoldStartTime else 0L

                    if (currentEdgeZone != null) {
                        val activeZone = currentEdgeZone!!
                        // --- CASE 1: CURSOR IS NEAR A CORNER (1/4th SNAP) ---
                        if (activeZone == "TopLeft" || activeZone == "TopRight" || 
                            activeZone == "BottomLeft" || activeZone == "BottomRight") {
                            // Show corner snap (1/4th screen) immediately!
                            when (activeZone) {
                                "TopLeft" -> {
                                    snapTargetRect = Rect(safe.left, snapTop, hW - gap, hH - gap)
                                    targetSide = "CornerTopLeft"
                                }
                                "TopRight" -> {
                                    snapTargetRect = Rect(hW + gap, snapTop, safe.right, hH - gap)
                                    targetSide = "CornerTopRight"
                                }
                                "BottomLeft" -> {
                                    snapTargetRect = Rect(safe.left, hH + gap, hW - gap, safe.bottom)
                                    targetSide = "CornerBottomLeft"
                                }
                                "BottomRight" -> {
                                    snapTargetRect = Rect(hW + gap, hH + gap, safe.right, safe.bottom)
                                    targetSide = "CornerBottomRight"
                                }
                            }
                        } else {
                            // --- CASE 2: CURSOR IS NEAR AN EDGE (LEFT/RIGHT/TOP/BOTTOM) ---
                            if (!hasOtherDocked) {
                                // If no other apps are snapped: trigger basic fullscreen/split immediately!
                                when (activeZone) {
                                    "Top" -> {
                                        snapTargetRect = Rect(safe.left, safe.top, safe.right, safe.bottom)
                                        targetSide = "TopFull"
                                    }
                                    "Left" -> {
                                        snapTargetRect = Rect(safe.left, snapTop, hW - gap, safe.bottom)
                                        targetSide = "Left"
                                    }
                                    "Right" -> {
                                        snapTargetRect = Rect(hW + gap, snapTop, safe.right, safe.bottom)
                                        targetSide = "Right"
                                    }
                                    "Bottom" -> {
                                        snapTargetRect = Rect(safe.left, hH + gap, safe.right, safe.bottom)
                                        targetSide = "Bottom"
                                    }
                                }
                            } else {
                                // Smart Tiling Grid Engine: Suggest exact, non-overlapping available gaps
                                // calculated by getAvailableSnapGaps() to ensure ZERO overlap with existing docked windows!
                                val gaps = getAvailableSnapGaps()
                                when (activeZone) {
                                    "Top" -> {
                                        val topGaps = gaps.filter { it.centerY() < hH }
                                        val bestTopGap = topGaps.minByOrNull { gapRect ->
                                            val dx = event.rawX - gapRect.centerX()
                                            val dy = event.rawY - gapRect.centerY()
                                            dx * dx + dy * dy
                                        }
                                        if (elapsedEdgeTime < 4000 && bestTopGap != null) {
                                            snapTargetRect = bestTopGap
                                            targetSide = "GapVertical"
                                        } else {
                                            // Transition into fullscreen size indication overlay after 4 seconds hold!
                                            snapTargetRect = Rect(safe.left, safe.top, safe.right, safe.bottom)
                                            targetSide = "TopFull"
                                        }
                                    }
                                    "Left" -> {
                                        val leftGaps = gaps.filter { it.centerX() < hW }
                                        val bestLeftGap = leftGaps.minByOrNull { gapRect ->
                                            val dx = event.rawX - gapRect.centerX()
                                            val dy = event.rawY - gapRect.centerY()
                                            dx * dx + dy * dy
                                        }
                                        if (elapsedEdgeTime < 4000 && bestLeftGap != null) {
                                            snapTargetRect = bestLeftGap
                                            targetSide = "GapHorizontal"
                                        } else {
                                            // Transition into split left size indication overlay after 4 seconds hold!
                                            snapTargetRect = Rect(safe.left, snapTop, hW - gap, safe.bottom)
                                            targetSide = "Left"
                                        }
                                    }
                                    "Right" -> {
                                        val rightGaps = gaps.filter { it.centerX() > hW }
                                        val bestRightGap = rightGaps.minByOrNull { gapRect ->
                                            val dx = event.rawX - gapRect.centerX()
                                            val dy = event.rawY - gapRect.centerY()
                                            dx * dx + dy * dy
                                        }
                                        if (elapsedEdgeTime < 4000 && bestRightGap != null) {
                                            snapTargetRect = bestRightGap
                                            targetSide = "GapHorizontal"
                                        } else {
                                            // Transition into split right size indication overlay after 4 seconds hold!
                                            snapTargetRect = Rect(hW + gap, snapTop, safe.right, safe.bottom)
                                            targetSide = "Right"
                                        }
                                    }
                                    "Bottom" -> {
                                        val bottomGaps = gaps.filter { it.centerY() > hH }
                                        val bestBottomGap = bottomGaps.minByOrNull { gapRect ->
                                            val dx = event.rawX - gapRect.centerX()
                                            val dy = event.rawY - gapRect.centerY()
                                            dx * dx + dy * dy
                                        }
                                        if (elapsedEdgeTime < 4000 && bestBottomGap != null) {
                                            snapTargetRect = bestBottomGap
                                            targetSide = "GapVertical"
                                        } else {
                                            // Transition into basic bottom split overlay after 4 seconds hold!
                                            snapTargetRect = Rect(safe.left, hH + gap, safe.right, safe.bottom)
                                            targetSide = "Bottom"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // --- END ADVANCED WINDOW SNAPPING OVERHAUL ---
                    
                    val target = snapTargetRect
                    if (target != null) {
                        FreeformOverlayService.showSnapGuide(displayId, target)
                    } else {
                        FreeformOverlayService.hideSnapGuide()
                    }
                    
                    lx = event.rawX; ly = event.rawY
                    updateLayouts(false)
                    applyBounds(false)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    FreeformOverlayService.hideSnapGuide()
                    
                    // Clear corner hold timer states
                    lastCornerDetected = null
                    cornerDetectStartTime = 0L
                    currentEdgeZone = null
                    edgeHoldStartTime = 0L
                    pendingEdgeZone = null
                    pendingEdgeZoneStartTime = 0L
                    
                    if (wasDockedOnDown && !hasMovedThreshold) {
                        isInteracting = false
                        triggerPillInteraction(extendTimer = true, delayMs = 3000)
                        updateLayouts()
                        updateColors()
                        return@setOnTouchListener true
                    }
                    
                    val safe = getSafeAreaRect()
                    isInteracting = false
                    
                    val usePill = ThemeManager.usePillForSnapped(displayContext)
                    val snapTop = if (usePill) safe.top else safe.top + titleBarHeight
                    val gap = (ThemeManager.getBorderWidth(displayContext) * density).toInt() / 2
                    val hW = safe.left + safe.width() / 2
                    val hH = safe.top + safe.height() / 2
                    
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
                            "GapHorizontal", "GapVertical" -> {
                                isMaximized = false
                                savePreDockedRect()
                                setDockMode(true)
                                val rect = snapTargetRect
                                if (rect != null) {
                                    ShellExecutor.resizeTask(taskId, rect.left, rect.top, rect.right, rect.bottom)
                                }
                            }
                            "CornerTopLeft" -> {
                                isMaximized = false; savePreDockedRect(); setDockMode(true)
                                ShellExecutor.resizeTask(taskId, safe.left, snapTop, hW - gap, hH - gap)
                            }
                            "CornerTopRight" -> {
                                isMaximized = false; savePreDockedRect(); setDockMode(true)
                                ShellExecutor.resizeTask(taskId, hW + gap, snapTop, safe.right, hH - gap)
                            }
                            "CornerBottomLeft" -> {
                                isMaximized = false; savePreDockedRect(); setDockMode(true)
                                ShellExecutor.resizeTask(taskId, safe.left, hH + gap, hW - gap, safe.bottom)
                            }
                            "CornerBottomRight" -> {
                                isMaximized = false; savePreDockedRect(); setDockMode(true)
                                ShellExecutor.resizeTask(taskId, hW + gap, hH + gap, safe.right, safe.bottom)
                            }
                        }
                    } else {
                        applyBounds(true)
                    }
                    
                    // CRITICAL FIX: Trigger focused transition now that the active touchscreen finger gesture is complete,
                    // or if it's a mouse click.
                    ShellExecutor.moveTaskToFront(taskId)
                    triggerPillInteraction(extendTimer = true, delayMs = 3000)
                    endInteraction()
                    true
                }
                else -> false
            }
        }
        titleBarView = root
        root.setOnHoverListener { _, event ->
            val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
            val shouldShowPill = isMaximized || (isDocked && usePillForSnapped)
            
            if (shouldShowPill) {
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                        isHovered = true
                        triggerPillInteraction(extendTimer = true, delayMs = 5000) // Fallback automatic shrink in 5s if EXIT is missed
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        if (isHovered) {
                            // Hover Phantom Exit Fix: validate pointer is really outside before accepting exit.
                            if (CompatibilityManager.isHoverPhantomExitCheckEnabled(displayContext)) {
                                val location = IntArray(2)
                                root.getLocationOnScreen(location)
                                val rect = Rect(location[0], location[1], location[0] + root.width, location[1] + root.height)
                                if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                                    return@setOnHoverListener true
                                }
                            }
                            
                            isHovered = false
                            triggerPillInteraction(extendTimer = true, delayMs = 150) // Premium snappy exit
                        }
                    }
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
        frameView = object : View(displayContext) {
            override fun onDraw(canvas: Canvas) {
                val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
                val shouldShowPill = isMaximized || (isDocked && usePillForSnapped)
                val themeMode = ThemeManager.getThemeMode(displayContext)
                val isDarkMode = when(themeMode) {
                    1 -> false
                    2 -> true
                    else -> (displayContext.resources.configuration.uiMode and 
                             android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                             android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                
                val accent = currentDecorationColor
                val unfocusedBorder = if (isDarkMode) Color.parseColor("#40FFFFFF") else Color.parseColor("#40000000")
                
                val bColor = if (isFocused) accent else unfocusedBorder
                val bAlpha = if (isFocused) 255 else 100
                
                val bWidth = ThemeManager.getBorderWidth(displayContext) * density
                val strokeW = if (isDocked) (2 * density).toInt() else bWidth.toInt()
                
                // Self-correcting layout synchronization: check if view size indicates fullscreen guide mode
                val useFullscreenGuide = width > winW + borderWidth * 3
                val drawL: Float
                val drawT: Float
                val drawW: Float
                val drawH: Float
                
                if (useFullscreenGuide) {
                    val safe = getSafeAreaRect()
                    val decorY = (winT - titleBarHeight).coerceIn(safe.top - titleBarHeight, safe.bottom - titleBarHeight)
                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    drawL = (winL - borderWidth).toFloat() - loc[0]
                    drawT = (decorY - borderWidth).toFloat() - loc[1]
                    drawW = (winW + borderWidth * 2).toFloat()
                    drawH = (winH + titleBarHeight + borderWidth).toFloat()
                } else {
                    drawL = 0f
                    drawT = 0f
                    drawW = width.toFloat()
                    drawH = height.toFloat()
                }
                
                val screenH = realMetrics.heightPixels
                val currentWinT = if (useFullscreenGuide) {
                    val safe = getSafeAreaRect()
                    (winT - titleBarHeight).coerceIn(safe.top - titleBarHeight, safe.bottom - titleBarHeight) + titleBarHeight
                } else {
                    winT
                }
                val isNearBottom = (currentWinT + winH) >= (screenH - (2 * density).toInt())
                
                // Configure our pre-allocated GradientDrawable
                previewDrawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                
                // Set dynamic translucent fill background if interacting
                if (isInteracting && ThemeManager.isDragTintEnabled(displayContext)) {
                    previewDrawable.setColor(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent)))
                } else {
                    previewDrawable.setColor(Color.TRANSPARENT)
                }
                
                // Set stroke border color with transparency factored in
                val strokeColorWithAlpha = Color.argb(
                    bAlpha,
                    Color.red(bColor),
                    Color.green(bColor),
                    Color.blue(bColor)
                )
                previewDrawable.setStroke(strokeW, strokeColorWithAlpha)
                
                // Dynamic corner radii matching individual rounded edges
                val rTop = 16 * density
                val rBot = if (isNearBottom) 0f else 12 * density
                previewDrawable.cornerRadii = floatArrayOf(
                    rTop, rTop, // Top-left
                    rTop, rTop, // Top-right
                    rBot, rBot, // Bottom-right
                    rBot, rBot  // Bottom-left
                )
                
                // Draw the dynamic shape instantly using highly optimized C++ GPU pipelines!
                previewDrawable.setBounds(
                    drawL.toInt(),
                    drawT.toInt(),
                    (drawL + drawW).toInt(),
                    (drawT + drawH).toInt()
                )
                previewDrawable.draw(canvas)
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
            clipToOutline = !isInteracting // Ensure content doesn't leak out of rounded corners
            
            // Toggle shadow via elevation
            elevation = if (ThemeManager.showShadows(displayContext) && !isDocked && !isMaximized && !isInteracting) 20 * density else 0f
            
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

        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f * density
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(3f * density, 0f, 1f * density, Color.parseColor("#60000000"))
        }

        bottomLeftHandle = object : View(displayContext) {
            override fun draw(canvas: Canvas) { applyMaskAndDraw(this, canvas) { super.draw(it) } }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val showVisual = ThemeManager.getVisualCornerHandlesGlobal(displayContext) && ThemeManager.getVisualCornerHandles(displayContext, displayId)
                if (!showVisual) return
                
                val cx = width / 2f
                val cy = height / 2f
                val armLength = 20f * density
                val r = 10f * density
                
                val path = Path().apply {
                    moveTo(cx, cy - armLength)
                    lineTo(cx, cy - r)
                    arcTo(cx, cy - 2 * r, cx + 2 * r, cy, 180f, -90f, false)
                    lineTo(cx + armLength, cy)
                }
                canvas.drawPath(path, handleStrokePaint)
            }
        }.apply { 
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.argb(1, 0, 0, 0))
            setOnTouchListener { _, e -> handleResize(e, true, false, true) } 
            setOnHoverListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> { isLeftHandleHovered = true; updateLayouts(false) }
                    MotionEvent.ACTION_HOVER_EXIT -> { isLeftHandleHovered = false; updateLayouts(false) }
                }
                false
            }
        }

        bottomRightHandle = object : View(displayContext) {
            override fun draw(canvas: Canvas) { applyMaskAndDraw(this, canvas) { super.draw(it) } }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val showVisual = ThemeManager.getVisualCornerHandlesGlobal(displayContext) && ThemeManager.getVisualCornerHandles(displayContext, displayId)
                if (!showVisual) return
                
                val cx = width / 2f
                val cy = height / 2f
                val armLength = 20f * density
                val r = 10f * density
                
                val path = Path().apply {
                    moveTo(cx, cy - armLength)
                    lineTo(cx, cy - r)
                    arcTo(cx - 2 * r, cy - 2 * r, cx, cy, 0f, 90f, false)
                    lineTo(cx - armLength, cy)
                }
                canvas.drawPath(path, handleStrokePaint)
            }
        }.apply { 
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.argb(1, 0, 0, 0))
            setOnTouchListener { _, e -> handleResize(e, false, true, true) } 
            setOnHoverListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> { isRightHandleHovered = true; updateLayouts(false) }
                    MotionEvent.ACTION_HOVER_EXIT -> { isRightHandleHovered = false; updateLayouts(false) }
                }
                false
            }
        }
    }

    private fun handleResize(event: MotionEvent, left: Boolean, right: Boolean, bottom: Boolean): Boolean {
        val action = event.action
        val toolType = if (event.pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        val isTouch = toolType == MotionEvent.TOOL_TYPE_FINGER
        
        Log.d("DragResizeOverlayTouch", "handleResizeTouch: taskId=$taskId, action=${MotionEvent.actionToString(action)}, left=$left, right=$right, bottom=$bottom, toolType=$toolType")
        
        return when (action) {
            MotionEvent.ACTION_DOWN -> { 
                isInteracting = true
                isResizing = true
                isActivelyDraggingOrResizing = true
                activeResizeLeft = left
                activeResizeRight = right
                activeResizeBottom = bottom
                
                FreeformOverlayService.getInstance()?.elevateOverlayToTop(taskId)
                
                // Hide all split handles during manual resize gestures to avoid visual overlap/distraction
                FreeformOverlayService.getInstance()?.setSplitHandlesHidden(true)
                
                preInteractL = winL
                preInteractT = winT
                preInteractW = winW
                preInteractH = winH
                val usePillForSnapped = ThemeManager.usePillForSnapped(displayContext)
                preShouldShowPill = isMaximized || (isDocked && usePillForSnapped)
                
                val pairedEnabled = ThemeManager.getPairedScalingGlobal(displayContext) || ThemeManager.getPairedScaling(displayContext, displayId)
                if (!pairedEnabled) {
                    setDockMode(false) // Reset docked state immediately when manually resizing via borders
                }
                
                // Finger Defer Fix: same guard as in the title bar drag handler.
                val deferFocusForTouch = CompatibilityManager.isFingerDeferFocusEnabled(displayContext)
                if (!isTouch || !deferFocusForTouch) {
                    ShellExecutor.moveTaskToFront(taskId)
                }
                FreeformOverlayService.requestRefresh()
                rLx = event.rawX; rLy = event.rawY
                updateColors()
                updateLayouts()
                
                true 
            }
            MotionEvent.ACTION_MOVE -> {
                val now = System.currentTimeMillis()
                if (now - lastManualResizeTime >= 16) {
                    lastManualResizeTime = now
                    val screenW = realMetrics.widthPixels
                    val screenH = realMetrics.heightPixels
                    
                    val dx = (event.rawX - rLx).toInt(); val dy = (event.rawY - rLy).toInt()
                    val minW = (120 * density).toInt().coerceAtMost(350)
                    val minH = (80 * density).toInt().coerceAtMost(200)
                    if (left) { 
                        val oldL = winL
                        val maxL = ((oldL + winW) - minW).coerceAtLeast(0)
                        winL = (winL + dx).coerceIn(0, maxL)
                        winW -= (winL - oldL)
                    }
                    if (right) { 
                        val maxW = (screenW - winL).coerceAtLeast(minW)
                        winW = (winW + dx).coerceIn(minW, maxW) 
                    }
                    if (bottom) { 
                        val maxH = (screenH - winT).coerceAtLeast(minH)
                        winH = (winH + dy).coerceIn(minH, maxH) 
                    }
                    
                    rLx = event.rawX; rLy = event.rawY
                    if (ThemeManager.realtimeResize(displayContext)) {
                        updateLayouts(false)
                        applyBounds(false)
                    } else {
                        updateLayouts(false)
                        updateColors()
                    }
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { 
                isResizing = false
                activeResizeLeft = false
                activeResizeRight = false
                activeResizeBottom = false
                
                // Show all split handles again once manual resize gesture is done
                FreeformOverlayService.getInstance()?.setSplitHandlesHidden(false)
                
                // Process the final touch coordinate to apply the exact ending location
                val screenW = realMetrics.widthPixels
                val screenH = realMetrics.heightPixels
                val dx = (event.rawX - rLx).toInt(); val dy = (event.rawY - rLy).toInt()
                val minW = (120 * density).toInt().coerceAtMost(350)
                val minH = (80 * density).toInt().coerceAtMost(200)
                if (left) { 
                    val oldL = winL
                    val maxL = ((oldL + winW) - minW).coerceAtLeast(0)
                    winL = (winL + dx).coerceIn(0, maxL)
                    winW -= (winL - oldL)
                }
                if (right) { 
                    val maxW = (screenW - winL).coerceAtLeast(minW)
                    winW = (winW + dx).coerceIn(minW, maxW) 
                }
                if (bottom) { 
                    val maxH = (screenH - winT).coerceAtLeast(minH)
                    winH = (winH + dy).coerceIn(minH, maxH) 
                }
                
                updateLayouts()
                updateColors()
                applyBounds(true)
                
                // CRITICAL FIX: Trigger focused transition now that the active touchscreen finger gesture is complete,
                // or if it's a mouse gesture.
                ShellExecutor.moveTaskToFront(taskId)
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

    private fun getAvailableSnapGaps(): List<Rect> {
        val safe = getSafeAreaRect()
        val density = displayContext.resources.displayMetrics.density
        val gap = (ThemeManager.getBorderWidth(displayContext) * density).toInt() / 2
        val snapTop = safe.top
        
        val gaps = mutableListOf<Rect>()
        
        // Find other docked tasks on this display
        val otherDocked = FreeformOverlayService.getInstance()?.overlays?.values?.filter { 
            it.currentDisplayId == displayId && it.isDocked && it.taskId != taskId 
        } ?: emptyList()
        
        if (otherDocked.isEmpty()) return emptyList()
        
        // Dynamic Tiling Grid Engine: Calculate exact non-overlapping horizontal and vertical gaps
        // defined by currently docked apps across distinct column divisions.
        val xCoords = (listOf(safe.left) + otherDocked.flatMap { listOf(it.winL, it.winL + it.winW) } + listOf(safe.right))
            .distinct()
            .sorted()
            
        for (i in 0 until xCoords.size - 1) {
            val colL = xCoords[i]
            val colR = xCoords[i + 1]
            if (colR - colL < 60 * density) continue // Ignore extremely thin columns
            
            // Find apps lying inside this column division horizontally
            val appsInCol = otherDocked.filter { 
                val center = it.winL + it.winW / 2
                center in colL..colR 
            }
            
            if (appsInCol.isEmpty()) {
                // Column is completely free!
                gaps.add(Rect(colL + gap, snapTop, colR - gap, safe.bottom))
            } else {
                // Column is partially occupied: check for vertical segment gaps
                val sortedApps = appsInCol.sortedBy { it.winT }
                var prevB = snapTop
                for (ap in sortedApps) {
                    val gapSize = ap.winT - prevB
                    if (gapSize > 60 * density) {
                        gaps.add(Rect(colL + gap, prevB + gap, colR - gap, ap.winT - gap))
                    }
                    prevB = ap.winT + ap.winH
                }
                val lastGap = safe.bottom - prevB
                if (lastGap > 60 * density) {
                    gaps.add(Rect(colL + gap, prevB + gap, colR - gap, safe.bottom - gap))
                }
            }
        }
        
        return gaps
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
        
        val safe = getSafeAreaRect()
        val sW = safe.width().coerceAtLeast(1)
        val sH = safe.height().coerceAtLeast(1)
        val displayAspectRatio = sW.toFloat() / sH.toFloat()

        // Dynamic, Aspect-Ratio-Preserving Preview Container Sizing
        val boundingBoxPx = (80 * density).toInt()
        val previewW: Int
        val previewH: Int
        if (displayAspectRatio >= 1.0f) {
            previewW = boundingBoxPx
            previewH = (boundingBoxPx / displayAspectRatio).toInt().coerceAtLeast((35 * density).toInt())
        } else {
            previewH = boundingBoxPx
            previewW = (boundingBoxPx * displayAspectRatio).toInt().coerceAtLeast((35 * density).toInt())
        }
        
        val isLandscape = displayAspectRatio >= 1.0f
        var totalPreviewItems = 0
        
        val menu = LinearLayout(displayContext).apply {
            orientation = if (isLandscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            totalPreviewItems++
            val container = FrameLayout(displayContext).apply {
                layoutParams = LinearLayout.LayoutParams(
                    previewW, 
                    previewH
                ).apply { 
                    if (isLandscape) {
                        bottomMargin = (8 * density).toInt()
                    } else {
                        rightMargin = (8 * density).toInt()
                    }
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2D2D2D"))
                    cornerRadius = 8 * density
                }
            }

            val iconW = previewW
            val iconH = previewH

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
        val snapTop = if (usePill) safe.top else safe.top + titleBarHeight

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

        // EXPERIMENTAL: Smart snap option showing available empty space zones
        val availableGaps = getAvailableSnapGaps()
        if (availableGaps.isNotEmpty()) {
            val smartZones = availableGaps.map { gapRect ->
                val relL = (((gapRect.left - safe.left).toFloat() / safe.width().toFloat()) * 90 + 5).toInt().coerceIn(5, 95)
                val relT = (((gapRect.top - safe.top).toFloat() / safe.height().toFloat()) * 90 + 5).toInt().coerceIn(5, 95)
                val relR = (((gapRect.right - safe.left).toFloat() / safe.width().toFloat()) * 90 + 5).toInt().coerceIn(5, 95)
                val relB = (((gapRect.bottom - safe.top).toFloat() / safe.height().toFloat()) * 90 + 5).toInt().coerceIn(5, 95)
                
                Triple(Rect(relL, relT, relR, relB), "Smart Snap", { _: Int, _: Int, _: Int, _: Int ->
                    isMaximized = false
                    savePreDockedRect()
                    setDockMode(true)
                    ShellExecutor.resizeTask(taskId, gapRect.left, gapRect.top, gapRect.right, gapRect.bottom)
                    updateLayouts()
                })
            }
            addMultiSnap(smartZones)
        }

        val service = FreeformOverlayService.getInstance()
        val globalEnabled = ThemeManager.getTiledSwapGlobal(displayContext)
        val displayEnabled = ThemeManager.getTiledSwap(displayContext, displayId)
        val dockedOverlays = service?.overlays?.values?.filter { it.currentDisplayId == displayId && it.isDocked && it.taskId != taskId } ?: emptyList()
        
        if (globalEnabled && displayEnabled && dockedOverlays.isNotEmpty()) {
            menu.addView(View(displayContext).apply {
                layoutParams = if (isLandscape) {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply {
                        topMargin = (8 * density).toInt()
                        bottomMargin = (8 * density).toInt()
                    }
                } else {
                    LinearLayout.LayoutParams(
                        (1 * density).toInt(),
                        (40 * density).toInt()
                    ).apply {
                        leftMargin = (8 * density).toInt()
                        rightMargin = (8 * density).toInt()
                    }
                }
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            })
            
            if (isLandscape) {
                menu.addView(TextView(displayContext).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (6 * density).toInt()
                    }
                    text = "SMART SWAP"
                    textSize = 9f
                    setTextColor(Color.parseColor("#80FFFFFF"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
            }
            
            totalPreviewItems++
            val swapContainer = FrameLayout(displayContext).apply {
                layoutParams = LinearLayout.LayoutParams(
                    previewW,
                    previewH
                ).apply {
                    if (isLandscape) {
                        bottomMargin = (4 * density).toInt()
                    } else {
                        rightMargin = (4 * density).toInt()
                    }
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF222222"))
                    cornerRadius = 8 * density
                    setStroke((1 * density).toInt(), Color.parseColor("#22FFFFFF"))
                }
            }
            
            val allDocked = (service?.overlays?.values?.filter { it.currentDisplayId == displayId && it.isDocked } ?: emptyList())
            val safe = getSafeAreaRect()
            val sW = safe.width().coerceAtLeast(1)
            val sH = safe.height().coerceAtLeast(1)
            
            val iconW = previewW
            val iconH = previewH
            
            for (task in allDocked) {
                val relL = (((task.winL - safe.left).toFloat() / sW.toFloat()) * 100).toInt().coerceIn(0, 100)
                val relT = (((task.winT - safe.top).toFloat() / sH.toFloat()) * 100).toInt().coerceIn(0, 100)
                val relR = ((((task.winL + task.winW) - safe.left).toFloat() / sW.toFloat()) * 100).toInt().coerceIn(0, 100)
                val relB = ((((task.winT + task.winH) - safe.top).toFloat() / sH.toFloat()) * 100).toInt().coerceIn(0, 100)
                
                val blockW = (relR - relL) * iconW / 100
                val blockH = (relB - relT) * iconH / 100
                val blockL = relL * iconW / 100
                val blockT = relT * iconH / 100
                
                val blockView = FrameLayout(displayContext).apply {
                    background = GradientDrawable().apply {
                        if (task.taskId == taskId) {
                            setColor(Color.argb(120, 66, 133, 244))
                            setStroke((1.5 * density).toInt(), Color.parseColor("#FF4285F4"))
                        } else {
                            setColor(Color.argb(80, 45, 45, 45))
                            setStroke((1 * density).toInt(), Color.parseColor("#44FFFFFF"))
                        }
                        cornerRadius = 4 * density
                    }
                    
                    val iconView = ImageView(displayContext).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        val paddingPx = (3 * density).toInt()
                        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                        try {
                            val pm = context.packageManager
                            val appIcon = pm.getApplicationIcon(task.packageName)
                            setImageDrawable(appIcon)
                        } catch (e: Exception) {
                            setImageResource(android.R.drawable.sym_def_app_icon)
                        }
                    }
                    addView(iconView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ).apply { gravity = Gravity.CENTER })
                }
                
                if (task.taskId != taskId) {
                    blockView.isClickable = true
                    blockView.setOnClickListener {
                        dismissActiveSnapMenu(windowManager)
                        
                        val tempL = winL; val tempT = winT; val tempW = winW; val tempH = winH
                        val tempDocked = isDocked
                        val tempMaximized = isMaximized
                        
                        winL = task.winL; winT = task.winT; winW = task.winW; winH = task.winH
                        isDocked = task.isDocked
                        isMaximized = task.isMaximized
                        
                        task.winL = tempL; task.winT = tempT; task.winW = tempW; task.winH = tempH
                        task.isDocked = tempDocked
                        task.isMaximized = tempMaximized
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            ShellExecutor.resizeTask(taskId, winL, winT, winL + winW, winT + winH)
                            ShellExecutor.resizeTask(task.taskId, task.winL, task.winT, task.winL + task.winW, task.winT + task.winH)
                            
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                FreeformOverlayService.getInstance()?.triggerFastPollingSequence()
                            }
                        }
                        updateLayouts()
                        task.updateLayouts()
                    }
                }
                
                val lp = FrameLayout.LayoutParams(
                    blockW.coerceAtLeast((8 * density).toInt()),
                    blockH.coerceAtLeast((8 * density).toInt())
                ).apply {
                    leftMargin = blockL
                    topMargin = blockT
                }
                swapContainer.addView(blockView, lp)
            }
            
            menu.addView(swapContainer)
        }

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)

        // Calculate layout menu width and height dynamically based on active aspect ratio
        val menuWidthPx: Int
        val menuHeightPx: Int
        if (isLandscape) {
            menuWidthPx = previewW + (24 * density).toInt()
            menuHeightPx = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            // Horizontal layout width calculation: number of cards * (card width + horizontal spacing) + horizontal padding + optional divider spacing
            val baseCardsWidth = totalPreviewItems * (previewW + (8 * density).toInt())
            val dividerOffset = if (globalEnabled && displayEnabled && dockedOverlays.isNotEmpty()) (18 * density).toInt() else 0
            val paddingWidth = (24 * density).toInt()
            menuWidthPx = (baseCardsWidth + dividerOffset + paddingWidth).coerceAtMost(realMetrics.widthPixels - (32 * density).toInt())
            menuHeightPx = previewH + (24 * density).toInt()
        }

        // Requirement: Use TYPE_APPLICATION_OVERLAY to ensure it is added to the system overlay layer successfully without requiring accessibility token validation
        val params = WindowManager.LayoutParams(
            menuWidthPx,
            menuHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (isLandscape) {
                x = anchorLoc[0] + (anchor.width - menuWidthPx) / 2
            } else {
                // Centered horizontally below the anchor view on portrait, safe-constrained to screen padding bounds
                x = (anchorLoc[0] + anchor.width / 2 - menuWidthPx / 2).coerceIn((16 * density).toInt(), realMetrics.widthPixels - menuWidthPx - (16 * density).toInt())
            }
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
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val viewX = loc[0]
            val viewY = loc[1]
            
            canvas.save()
            for (oc in occluders) {
                val l = (oc.left - viewX).toFloat()
                val t = (oc.top - viewY).toFloat()
                val r = (oc.right - viewX).toFloat()
                val b = (oc.bottom - viewY).toFloat()
                
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
    
    private val touchRegionListeners = java.util.concurrent.ConcurrentHashMap<View, Any>()

    private fun setupTouchRegionHelper(view: View) {
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                registerTouchableRegionListener(v)
            }
            override fun onViewDetachedFromWindow(v: View) {
                // Handled during hide()
            }
        }
        view.addOnAttachStateChangeListener(attachListener)
        if (view.isAttachedToWindow) {
            registerTouchableRegionListener(view)
        }
    }

    private fun registerTouchableRegionListener(view: View) {
        if (touchRegionListeners.containsKey(view)) return
        try {
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val insetsClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val touchableRegionField = insetsClass.getField("touchableRegion")
            val setTouchableInsetsMethod = insetsClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets") {
                    val insetsInfo = args[0]
                    if (!isFocused && occluders.isNotEmpty()) {
                        val touchableRegion = touchableRegionField.get(insetsInfo) as android.graphics.Region
                        val rect = android.graphics.Rect(0, 0, view.width, view.height)
                        val region = android.graphics.Region(rect)
                        
                        val loc = IntArray(2)
                        view.getLocationOnScreen(loc)
                        val viewX = loc[0]
                        val viewY = loc[1]
                        
                        for (oc in occluders) {
                            val l = oc.left - viewX
                            val t = oc.top - viewY
                            val r = oc.right - viewX
                            val b = oc.bottom - viewY
                            
                            if (r > 0 && b > 0) {
                                region.op(l, t, r, b, android.graphics.Region.Op.DIFFERENCE)
                            }
                        }
                        
                        touchableRegion.set(region)
                        setTouchableInsetsMethod.invoke(insetsInfo, 3) // TOUCHABLE_INSETS_REGION
                    } else {
                        setTouchableInsetsMethod.invoke(insetsInfo, 0) // TOUCHABLE_INSETS_FRAME (whole window frame is touchable)
                    }
                }
                null
            }
            
            val addMethod = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            addMethod.invoke(view.viewTreeObserver, proxy)
            touchRegionListeners[view] = proxy
        } catch (e: Exception) {
            Log.e("DragResizeOverlay", "Failed to setup touch region helper for view: $view", e)
        }
    }

    private fun cleanupTouchRegions() {
        for ((view, proxy) in touchRegionListeners) {
            try {
                val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
                val removeMethod = ViewTreeObserver::class.java.getMethod("removeOnComputeInternalInsetsListener", listenerClass)
                removeMethod.invoke(view.viewTreeObserver, proxy)
            } catch (e: Exception) {}
        }
        touchRegionListeners.clear()
    }
    
    private fun updateTouchableFlags() {
        val views = listOfNotNull(
            Pair(titleBarView, true),
            Pair(leftStrip, true),
            Pair(rightStrip, true),
            Pair(bottomStrip, true),
            Pair(bottomLeftHandle, true),
            Pair(bottomRightHandle, true)
        )
        
        for ((view, originallyTouchable) in views) {
            view?.let { v ->
                if (v.parent != null) {
                    try {
                        val lp = v.layoutParams as WindowManager.LayoutParams
                        val oldFlags = lp.flags
                        val oldAlpha = lp.alpha
                        
                        if (originallyTouchable) {
                            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        }
                        lp.alpha = 1.0f
                        
                        if (lp.flags != oldFlags || lp.alpha != oldAlpha) {
                            windowManager.updateViewLayout(v, lp)
                        }
                    } catch (e: Exception) {
                        Log.e("DragResizeOverlay", "Failed to update touchable flags", e)
                    }
                }
            }
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
