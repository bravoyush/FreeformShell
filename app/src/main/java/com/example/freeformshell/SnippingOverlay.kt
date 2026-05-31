package com.example.freeformshell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.io.FileOutputStream

@SuppressLint("ViewConstructor")
class SnippingOverlay(
    private val context: Context,
    private val displayId: Int,
    private val displayInfo: DisplayInfo,
    private val onComplete: (File?) -> Unit
) {
    private val TAG = "SnippingOverlay"
    
    private val displayContext: Context = run {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
        val dContext = context.createDisplayContext(targetDisplay)
        // Avoid calling createWindowContext synchronously on secondary/virtual displays to prevent system IPC deadlocks!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayId == 0) {
            try {
                dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } catch (e: Exception) {
                Log.w(TAG, "createWindowContext failed for display $displayId, falling back to DisplayContext", e)
                dContext
            }
        } else {
            dContext
        }
    }
    
    private val windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = displayContext.resources.displayMetrics.density
    
    // Theme Colors
    private val accentColor = ThemeManager.getAccentColor(context, Color.parseColor("#8E2DE2"))
    private val isDark = ThemeManager.getThemeMode(context) != 1
    
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDragging = false
    private var isSelectionActive = false
    
    private val cropRect = RectF()
    
    private val paintDark = Paint().apply {
        color = Color.parseColor("#B3000000") // 70% opacity dark overlay
        style = Paint.Style.FILL
    }
    
    private val paintClear = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }
    
    private val paintBorder = Paint().apply {
        color = accentColor
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 10f * density), 0f)
    }
    
    // Root container to hold both the canvas and the control bar
    private val rootContainer = FrameLayout(displayContext)
    
    private val canvasView = object : View(displayContext) {
        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Draw dark screen overlay
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintDark)
            
            if (isSelectionActive || isDragging) {
                // Clear the crop cutout area
                canvas.drawRect(cropRect, paintClear)
                // Draw outline glow
                canvas.drawRect(cropRect, paintBorder)
                
                // Draw selection corners handles
                drawHandles(canvas)
            }
        }
        
        private fun drawHandles(canvas: Canvas) {
            val hPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                setShadowLayer(4 * density, 0f, 0f, Color.BLACK)
            }
            val r = 6 * density
            canvas.drawCircle(cropRect.left, cropRect.top, r, hPaint)
            canvas.drawCircle(cropRect.right, cropRect.top, r, hPaint)
            canvas.drawCircle(cropRect.left, cropRect.bottom, r, hPaint)
            canvas.drawCircle(cropRect.right, cropRect.bottom, r, hPaint)
        }
    }
    
    // Control Bar (Liquid Glass Style)
    private val controlBar = LinearLayout(displayContext).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = (8 * density).toInt()
        setPadding(p, p, p, p)
        visibility = View.GONE
        
        background = createGlassDrawable()
        
        elevation = 20 * density
    }
    
    private fun createGlassDrawable(): Drawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * density
            
            val alpha = if (isDark) 200 else 220
            val baseColor = if (isDark) Color.BLACK else Color.WHITE
            setColor(Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)))
        }

        val shine = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * density
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 150 * density
            setGradientCenter(0.2f, 0.2f)
            colors = intArrayOf(
                Color.argb(50, 255, 255, 255),
                Color.TRANSPARENT
            )
        }

        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * density
            setStroke((1.5 * density).toInt(), Color.argb(120, 255, 255, 255))
        }

        return LayerDrawable(arrayOf(base, shine, border))
    }
    
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )
    
    init {
        // canvasView must have MATCH_PARENT so it fills the overlay and has non-zero dimensions
        val canvasLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootContainer.addView(canvasView, canvasLp)
        setupControlBar()
        setupTouchListener()
    }
    
    private fun setupControlBar() {
        // 1. Full Screen Button
        val btnFull = createControlButton(FullscreenIconDrawable()) {
            val metrics = displayContext.resources.displayMetrics
            executeCropScreenshot(Rect(0, 0, metrics.widthPixels, metrics.heightPixels))
        }
        
        // 2. Confirm Button (Accent checkmark)
        val btnConfirm = createControlButton(ConfirmIconDrawable(), isAccent = true) {
            if (isSelectionActive) {
                val rect = Rect(
                    cropRect.left.toInt(),
                    cropRect.top.toInt(),
                    cropRect.right.toInt(),
                    cropRect.bottom.toInt()
                )
                executeCropScreenshot(rect)
            }
        }
        
        // 3. Cancel Button
        val btnCancel = createControlButton(CancelIconDrawable()) {
            hide()
            onComplete(null)
        }
        
        controlBar.addView(btnFull)
        controlBar.addView(btnConfirm)
        controlBar.addView(btnCancel)
        
        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (24 * density).toInt()
        }
        rootContainer.addView(controlBar, barParams)
    }

    // ─── Programmatic icon drawables (no android.R.drawable to avoid OEM crashes) ──
    private abstract inner class BaseIcon : android.graphics.drawable.Drawable() {
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        protected fun p(color: Int = Color.WHITE, width: Float = 2f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; strokeWidth = width * density
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
    }

    private inner class FullscreenIconDrawable : BaseIcon() {
        override fun draw(c: Canvas) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            val p = p()
            // Four corner L-shapes indicating fullscreen expand
            val s = w * 0.22f
            // Top-left
            c.drawLine(w*.1f, h*.1f+s, w*.1f, h*.1f, p); c.drawLine(w*.1f, h*.1f, w*.1f+s, h*.1f, p)
            // Top-right
            c.drawLine(w*.9f, h*.1f+s, w*.9f, h*.1f, p); c.drawLine(w*.9f, h*.1f, w*.9f-s, h*.1f, p)
            // Bottom-left
            c.drawLine(w*.1f, h*.9f-s, w*.1f, h*.9f, p); c.drawLine(w*.1f, h*.9f, w*.1f+s, h*.9f, p)
            // Bottom-right
            c.drawLine(w*.9f, h*.9f-s, w*.9f, h*.9f, p); c.drawLine(w*.9f, h*.9f, w*.9f-s, h*.9f, p)
        }
    }

    private inner class ConfirmIconDrawable : BaseIcon() {
        override fun draw(c: Canvas) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            val chk = Path().apply {
                moveTo(w * .18f, h * .52f); lineTo(w * .42f, h * .72f); lineTo(w * .82f, h * .28f)
            }
            c.drawPath(chk, p(accentColor, 3f))
        }
    }

    private inner class CancelIconDrawable : BaseIcon() {
        override fun draw(c: Canvas) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            c.drawLine(w*.28f, h*.28f, w*.72f, h*.72f, p(Color.parseColor("#FF453A"), 2.5f))
            c.drawLine(w*.72f, h*.28f, w*.28f, h*.72f, p(Color.parseColor("#FF453A"), 2.5f))
        }
    }

    private fun createControlButton(icon: android.graphics.drawable.Drawable, isAccent: Boolean = false, onClick: () -> Unit): ImageView {
        return ImageView(displayContext).apply {
            val size = (52 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (6 * density).toInt()
            }
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            setImageDrawable(icon)
            
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isAccent) Color.argb(60, 0, 122, 255) else Color.argb(40, 255, 255, 255))
            }
            
            setOnClickListener {
                animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onClick()
                }.start()
            }
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        canvasView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    currentX = startX
                    currentY = startY
                    isDragging = false
                    // Hide control bar while adjusting
                    controlBar.visibility = View.GONE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentX = event.x
                    currentY = event.y
                    
                    if (Math.abs(currentX - startX) > 5 || Math.abs(currentY - startY) > 5) {
                        isDragging = true
                        isSelectionActive = true
                    }
                    
                    if (isDragging) {
                        cropRect.set(
                            Math.min(startX, currentX),
                            Math.min(startY, currentY),
                            Math.max(startX, currentX),
                            Math.max(startY, currentY)
                        )
                        canvasView.invalidate()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        Log.d(TAG, "ACTION_UP: showing controlBar, cropRect=$cropRect")
                        controlBar.post {
                            controlBar.visibility = View.VISIBLE
                            controlBar.bringToFront()
                        }
                        // Remove FLAG_NOT_FOCUSABLE so bar buttons can receive touch
                        try {
                            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                            windowManager.updateViewLayout(rootContainer, params)
                        } catch (e: Exception) {
                            Log.w(TAG, "updateViewLayout failed, ignoring", e)
                        }
                    } else if (!isSelectionActive) {
                        // Tap without drag => cancel
                        hide()
                        onComplete(null)
                    } else {
                        // Tap on existing selection => show bar again
                        controlBar.post {
                            controlBar.visibility = View.VISIBLE
                            controlBar.bringToFront()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun executeCropScreenshot(rect: Rect) {
        if (rect.width() <= 10 || rect.height() <= 10) {
            hide()
            onComplete(null)
            return
        }
        
        // Capture view dimensions BEFORE calling hide()
        val canvasWidth = canvasView.width.let { if (it > 0) it else displayInfo.width }
        val canvasHeight = canvasView.height.let { if (it > 0) it else displayInfo.height }
        Log.d(TAG, "executeCropScreenshot: rect=$rect, canvasSize=${canvasWidth}x${canvasHeight}, displayInfo=${displayInfo.width}x${displayInfo.height}")
        
        hide()
        // Wait for overlay to disappear to avoid capturing the overlay itself
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            ScreenRecordManager.takeScreenshotToTempFile(context, displayId) { tempFile ->
                if (tempFile == null || !tempFile.exists()) {
                    onComplete(null)
                    return@takeScreenshotToTempFile
                }
                
                Thread {
                    try {
                        val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                        if (bitmap == null) {
                            tempFile.delete()
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onComplete(null)
                            }
                            return@Thread
                        }
                        
                        // Scale rect from display pixels to actual bitmap pixels
                        val scaleX = bitmap.width.toFloat() / canvasWidth.coerceAtLeast(1)
                        val scaleY = bitmap.height.toFloat() / canvasHeight.coerceAtLeast(1)
                        
                        // Clamp coordinates to strictly positive bounds within the source bitmap
                        val scaledLeft = (rect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
                        val scaledTop = (rect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
                        val scaledWidth = (rect.width() * scaleX).toInt().coerceIn(1, bitmap.width - scaledLeft)
                        val scaledHeight = (rect.height() * scaleY).toInt().coerceIn(1, bitmap.height - scaledTop)
                        
                        Log.d(TAG, "Crop mapping: rect=$rect -> scaled(left=$scaledLeft, top=$scaledTop, w=$scaledWidth, h=$scaledHeight) on bitmap(${bitmap.width}x${bitmap.height})")
                        
                        val finalBitmap = Bitmap.createBitmap(bitmap, scaledLeft, scaledTop, scaledWidth, scaledHeight)
                        
                        // Clean up temp file immediately
                        tempFile.delete()
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                Log.d(TAG, "Launching AnnotationOverlay for displayId $displayId with bitmap: ${finalBitmap.width}x${finalBitmap.height}")
                                val privateDir = context.getExternalFilesDir("Captures") ?: File(context.filesDir, "Captures")
                                if (!privateDir.exists()) {
                                    privateDir.mkdirs()
                                }
                                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                val finalScreenshotFile = File(privateDir, "screenshot_${timestamp}_display${displayId}.png")
                                
                                val annotator = AnnotationOverlay(context, displayId, finalBitmap, finalScreenshotFile, onComplete)
                                annotator.show()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to instantiate or show AnnotationOverlay", e)
                                onComplete(null)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing snip image", e)
                        tempFile.delete()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onComplete(null)
                        }
                    }
                }.start()
            }
        }, 150)
    }
    
    fun show() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (rootContainer.parent == null) {
                    windowManager.addView(rootContainer, params)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing crop selection overlay", e)
            }
        }
    }
    
    fun hide() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (rootContainer.parent != null) {
                    windowManager.removeView(rootContainer)
                }
            } catch (e: Exception) {}
        }
    }
}
