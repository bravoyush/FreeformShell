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
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

@SuppressLint("ViewConstructor")
class AnnotationOverlay(
    private val context: Context,
    private val displayId: Int,
    private val sourceBitmap: Bitmap,
    private val targetFile: File,
    private val onComplete: (File?) -> Unit
) {
    private val TAG = "AnnotationOverlay"

    private val displayContext: Context = run {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
        val dContext = context.createDisplayContext(targetDisplay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } catch (e: Exception) {
                dContext
            }
        } else dContext
    }

    private val windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = displayContext.resources.displayMetrics.density
    private val accentColor = ThemeManager.getAccentColor(context, Color.parseColor("#007AFF"))
    private val isDark = ThemeManager.getThemeMode(context) != 1
    private val controllerStyle = run {
        context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
            .getString("pref_controller_style", "obsidian") ?: "obsidian"
    }

    // ─── Tool & Shape Enums ──────────────────────────────────────────────────
    enum class Tool { PEN, MARKER, HIGHLIGHTER, SHAPES, ERASER }
    enum class ShapeType { RECTANGLE, CIRCLE, LINE, ARROW, TRIANGLE }

    private var activeTool = Tool.PEN
    private var activeShape = ShapeType.RECTANGLE
    private var activeColor = Color.parseColor("#FF3B30")

    private val penWidth      = 4f * density
    private val markerWidth   = 12f * density
    private val highlightWidth = 28f * density
    private val shapeWidth    = 3.5f * density
    private val eraserWidth   = 32f * density

    // ─── Draw Actions ────────────────────────────────────────────────────────
    sealed class DrawAction {
        data class PathAction(
            val path: Path, val color: Int, val strokeWidth: Float, val alpha: Int = 255,
            val strokeCap: Paint.Cap = Paint.Cap.ROUND
        ) : DrawAction()
        data class ShapeAction(
            val shapeType: ShapeType,
            val sx: Float, val sy: Float, val ex: Float, val ey: Float,
            val color: Int, val strokeWidth: Float
        ) : DrawAction()
        data class EraserAction(val path: Path, val strokeWidth: Float) : DrawAction()
    }

    private val drawActions = mutableListOf<DrawAction>()
    private val redoStack   = mutableListOf<DrawAction>()

    // ─── Layout Containers ───────────────────────────────────────────────────
    private val rootContainer = FrameLayout(displayContext).apply {
        setBackgroundColor(Color.argb(185, 8, 8, 12))
    }

    private val canvasView = AnnotationCanvasView(displayContext)

    // Button references for state updates
    private val toolButtons  = mutableMapOf<Tool, ImageView>()
    private val colorChips   = mutableMapOf<Int, FrameLayout>()
    private var shapesPopup: LinearLayout? = null
    private var undoBtnRef: ImageView? = null
    private var redoBtnRef: ImageView? = null
    private var colorPaletteRef: LinearLayout? = null

    private val overlayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    // Must be declared BEFORE init{} to avoid NPE during buildUI()
    private val paletteColors = listOf(
        Color.parseColor("#FF3B30"), // Red
        Color.parseColor("#FF9500"), // Orange
        Color.parseColor("#FFCC00"), // Yellow
        Color.parseColor("#30D158"), // Green
        Color.parseColor("#007AFF"), // Blue
        Color.parseColor("#BF5AF2"), // Purple
        Color.parseColor("#FFFFFF"), // White
        Color.parseColor("#000000")  // Black
    )

    init {
        try {
            Log.d(TAG, "Initializing AnnotationOverlay for displayId=$displayId bitmap=${sourceBitmap.width}x${sourceBitmap.height}")
            val canvasParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(0, 0, 0, (168 * density).toInt())
            }
            rootContainer.addView(canvasView, canvasParams)
            buildUI()
            Log.d(TAG, "AnnotationOverlay initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Init error", e)
        }
    }

    // ─── UI Assembly ─────────────────────────────────────────────────────────
    private fun buildUI() {
        // Color palette (above toolbar)
        val palette = buildColorPalette()
        colorPaletteRef = palette
        val paletteParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (102 * density).toInt()
        }
        rootContainer.addView(palette, paletteParams)

        // Shapes popup (above toolbar, initially GONE)
        val popup = buildShapesPopup()
        shapesPopup = popup
        val popupParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (102 * density).toInt()
        }
        rootContainer.addView(popup, popupParams)
        popup.visibility = View.GONE

        // Main toolbar
        val toolbar = buildMainToolbar()
        val toolbarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (36 * density).toInt()
        }
        rootContainer.addView(toolbar, toolbarParams)

        updateToolHighlights()
        updateColorHighlights()
        updateUndoRedoState()
    }

    // ─── Color Palette ───────────────────────────────────────────────────────

    private fun buildColorPalette(): LinearLayout {
        return LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = (8 * density).toInt()
            setPadding(p, (6 * density).toInt(), p, (6 * density).toInt())
            background = createPillBackground()
            elevation = 24 * density

            for (c in paletteColors) {
                val outerSize = (34 * density).toInt()
                val chipSize  = (24 * density).toInt()
                val outer = FrameLayout(displayContext).apply {
                    layoutParams = LinearLayout.LayoutParams(outerSize, outerSize).apply {
                        leftMargin = (2 * density).toInt()
                        rightMargin = (2 * density).toInt()
                    }
                }
                val chip = View(displayContext).apply {
                    layoutParams = FrameLayout.LayoutParams(chipSize, chipSize, Gravity.CENTER)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        if (c == Color.WHITE) setStroke((1 * density).toInt(), Color.LTGRAY)
                    }
                }
                outer.addView(chip)
                outer.setOnClickListener {
                    activeColor = c
                    updateColorHighlights()
                }
                addView(outer)
                colorChips[c] = outer
            }
        }
    }

    // ─── Shapes Popup ────────────────────────────────────────────────────────
    private fun buildShapesPopup(): LinearLayout {
        val shapes = listOf(
            ShapeType.RECTANGLE to RectShapeIcon(),
            ShapeType.CIRCLE    to CircleShapeIcon(),
            ShapeType.LINE      to LineIcon(),
            ShapeType.ARROW     to ArrowIcon(),
            ShapeType.TRIANGLE  to TriangleIcon()
        )
        return LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = (8 * density).toInt()
            setPadding(p, (6 * density).toInt(), p, (6 * density).toInt())
            background = createPillBackground()
            elevation = 32 * density
            for ((shape, icon) in shapes) {
                addView(createIconButton(icon) {
                    activeShape = shape
                    shapesPopup?.visibility = View.GONE
                    canvasView.invalidate()
                })
            }
        }
    }

    // ─── Main Toolbar ────────────────────────────────────────────────────────
    private fun buildMainToolbar(): LinearLayout {
        return LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = (8 * density).toInt()
            setPadding(p, (6 * density).toInt(), p, (6 * density).toInt())
            background = createPillBackground()
            elevation = 24 * density

            // PEN
            addView(createIconButton(PenIcon()) {
                activeTool = Tool.PEN; onToolSelected()
            }.also { toolButtons[Tool.PEN] = it })

            // MARKER
            addView(createIconButton(MarkerIcon()) {
                activeTool = Tool.MARKER; onToolSelected()
            }.also { toolButtons[Tool.MARKER] = it })

            // HIGHLIGHTER
            addView(createIconButton(HighlighterIcon()) {
                activeTool = Tool.HIGHLIGHTER; onToolSelected()
            }.also { toolButtons[Tool.HIGHLIGHTER] = it })

            // SHAPES (toggle popup)
            addView(createIconButton(ShapesIcon()) {
                activeTool = Tool.SHAPES
                shapesPopup?.visibility = if (shapesPopup?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                updateToolHighlights()
            }.also { toolButtons[Tool.SHAPES] = it })

            // ERASER
            addView(createIconButton(EraserIcon()) {
                activeTool = Tool.ERASER; onToolSelected()
            }.also { toolButtons[Tool.ERASER] = it })

            addView(createDivider())

            // UNDO
            addView(createIconButton(UndoIcon()) {
                if (drawActions.isNotEmpty()) {
                    redoStack.add(drawActions.removeAt(drawActions.size - 1))
                    canvasView.invalidate(); updateUndoRedoState()
                }
            }.also { undoBtnRef = it })

            // REDO
            addView(createIconButton(RedoIcon()) {
                if (redoStack.isNotEmpty()) {
                    drawActions.add(redoStack.removeAt(redoStack.size - 1))
                    canvasView.invalidate(); updateUndoRedoState()
                }
            }.also { redoBtnRef = it })

            // CLEAR
            addView(createIconButton(TrashIcon()) {
                if (drawActions.isNotEmpty()) {
                    redoStack.addAll(drawActions)
                    drawActions.clear()
                    canvasView.invalidate(); updateUndoRedoState()
                }
            })

            addView(createDivider())

            // SAVE
            addView(createIconButton(SaveIcon()) { flattenAndSave() })

            // CANCEL
            addView(createIconButton(CancelIcon()) { hide(); onComplete(null) })
        }
    }

    private fun onToolSelected() {
        shapesPopup?.visibility = View.GONE
        updateToolHighlights()
    }

    // ─── State Updates ───────────────────────────────────────────────────────
    private fun updateToolHighlights() {
        for ((tool, btn) in toolButtons) {
            val selected = tool == activeTool
            (btn.background as? GradientDrawable)?.setColor(
                if (selected) Color.argb(100, 255, 255, 255) else Color.TRANSPARENT
            )
        }
    }

    private fun updateColorHighlights() {
        for ((c, outer) in colorChips) {
            val selected = c == activeColor
            outer.background = if (selected) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(40, 255, 255, 255))
                setStroke((2 * density).toInt(), Color.WHITE)
            } else null
        }
    }

    private fun updateUndoRedoState() {
        undoBtnRef?.alpha = if (drawActions.isNotEmpty()) 1f else 0.35f
        redoBtnRef?.alpha = if (redoStack.isNotEmpty()) 1f else 0.35f
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun createDivider(): View = View(displayContext).apply {
        layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), (22 * density).toInt()).apply {
            leftMargin  = (5 * density).toInt()
            rightMargin = (5 * density).toInt()
        }
        setBackgroundColor(Color.argb(60, 255, 255, 255))
    }

    private fun createIconButton(icon: Drawable, onClick: () -> Unit): ImageView {
        return ImageView(displayContext).apply {
            val size = (38 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                leftMargin  = (2 * density).toInt()
                rightMargin = (2 * density).toInt()
            }
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            setImageDrawable(icon)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (8 * density)
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener {
                animate().scaleX(0.82f).scaleY(0.82f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    onClick()
                }.start()
            }
        }
    }

    private fun createPillBackground(): Drawable {
        if (controllerStyle == "solid_minimal") {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(if (isDark) Color.parseColor("#1D1B20") else Color.parseColor("#F3EDF7"))
                setStroke((1f * density).toInt(), if (isDark) Color.parseColor("#49454F") else Color.parseColor("#CAC4D0"))
            }
        }
        if (controllerStyle == "obsidian") {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(Color.argb(if (isDark) 220 else 240, 18, 18, 22))
                setStroke((1f * density).toInt(), Color.argb(if (isDark) 80 else 120, 255, 255, 255))
            }
        }
        // Liquid Glass
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * density
            colors = if (isDark) intArrayOf(Color.argb(200, 20, 20, 26), Color.argb(225, 8, 8, 12))
            else intArrayOf(Color.argb(225, 255, 255, 255), Color.argb(210, 240, 240, 245))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        val shine = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * density
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 180 * density
            setGradientCenter(0.2f, 0.2f)
            colors = intArrayOf(Color.argb(55, 255, 255, 255), Color.TRANSPARENT)
        }
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * density
            setStroke((1.5f * density).toInt(), Color.argb(if (isDark) 130 else 170, 255, 255, 255))
        }
        return LayerDrawable(arrayOf(base, shine, border))
    }

    // ─── Save / Flatten ──────────────────────────────────────────────────────
    private fun flattenAndSave() {
        Toast.makeText(context, "Saving…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val result = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val rc = Canvas(result)

                val cvW = canvasView.width.coerceAtLeast(1).toFloat()
                val cvH = canvasView.height.coerceAtLeast(1).toFloat()
                val sx = result.width / cvW
                val sy = result.height / cvH

                // saveLayer so eraser CLEAR works
                val layerCount = rc.saveLayer(0f, 0f, result.width.toFloat(), result.height.toFloat(), null)

                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                for (action in drawActions) {
                    rc.save()
                    rc.scale(sx, sy)
                    applyAction(rc, action, p)
                    rc.restore()
                }
                rc.restoreToCount(layerCount)

                var finalFileSaved = targetFile
                try {
                    val out = FileOutputStream(targetFile)
                    result.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush(); out.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save annotated screenshot to private storage", e)
                    throw e
                }

                // Export from app-private storage to public/user-chosen storage via SAF tree or MediaStore
                try {
                    val exported = ScreenRecordManager.exportFileToUserStorage(context, targetFile)
                    if (exported != null) {
                        finalFileSaved = exported
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to export image to public directory, falling back to local app captures", e)
                }

                Log.d(TAG, "Saved annotated screenshot → ${finalFileSaved.name}")
                ScreenRecordManager.sendCaptureNotification(context, finalFileSaved, false)

                android.os.Handler(android.os.Looper.getMainLooper()).post { hide(); onComplete(finalFileSaved) }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post { hide(); onComplete(null) }
            }
        }.start()
    }

    private fun applyAction(canvas: Canvas, action: DrawAction, p: Paint) {
        when (action) {
            is DrawAction.PathAction -> {
                p.reset()
                p.isAntiAlias = true
                p.color = action.color
                p.alpha = action.alpha
                p.strokeWidth = action.strokeWidth
                p.style = Paint.Style.STROKE
                p.strokeCap  = action.strokeCap
                p.strokeJoin = Paint.Join.ROUND
                p.xfermode = null
                canvas.drawPath(action.path, p)
            }
            is DrawAction.ShapeAction -> {
                p.reset()
                p.isAntiAlias = true
                p.color = action.color
                p.strokeWidth = action.strokeWidth
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeJoin = Paint.Join.ROUND
                p.xfermode = null
                drawShape(canvas, action, p)
            }
            is DrawAction.EraserAction -> {
                p.reset()
                p.isAntiAlias = true
                p.strokeWidth = action.strokeWidth
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeJoin = Paint.Join.ROUND
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                canvas.drawPath(action.path, p)
                p.xfermode = null
            }
        }
    }

    private fun drawShape(canvas: Canvas, action: DrawAction.ShapeAction, p: Paint) {
        val left   = minOf(action.sx, action.ex)
        val top    = minOf(action.sy, action.ey)
        val right  = maxOf(action.sx, action.ex)
        val bottom = maxOf(action.sy, action.ey)
        when (action.shapeType) {
            ShapeType.RECTANGLE -> canvas.drawRect(left, top, right, bottom, p)
            ShapeType.CIRCLE    -> canvas.drawOval(RectF(left, top, right, bottom), p)
            ShapeType.LINE      -> canvas.drawLine(action.sx, action.sy, action.ex, action.ey, p)
            ShapeType.ARROW     -> drawArrow(canvas, action.sx, action.sy, action.ex, action.ey, p)
            ShapeType.TRIANGLE  -> {
                val path = Path().apply {
                    moveTo((left + right) / 2, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                canvas.drawPath(path, p)
            }
        }
    }

    private fun drawArrow(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float, p: Paint) {
        canvas.drawLine(sx, sy, ex, ey, p)
        val angle = Math.atan2((ey - sy).toDouble(), (ex - sx).toDouble())
        val len   = 18 * density
        val wing  = Math.toRadians(30.0)
        val tp = Path().apply {
            moveTo(ex, ey)
            lineTo((ex - len * Math.cos(angle - wing)).toFloat(), (ey - len * Math.sin(angle - wing)).toFloat())
            moveTo(ex, ey)
            lineTo((ex - len * Math.cos(angle + wing)).toFloat(), (ey - len * Math.sin(angle + wing)).toFloat())
        }
        canvas.drawPath(tp, p)
    }

    // ─── Window Lifecycle ────────────────────────────────────────────────────
    fun show() {
        Log.d(TAG, "show()")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (rootContainer.parent == null) windowManager.addView(rootContainer, overlayParams)
            } catch (e: Exception) { Log.e(TAG, "show error", e) }
        }
    }

    fun hide() {
        Log.d(TAG, "hide()")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (rootContainer.parent != null) windowManager.removeView(rootContainer)
            } catch (e: Exception) { Log.e(TAG, "hide error", e) }
        }
    }

    // ─── Annotation Canvas View ──────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility", "ViewConstructor")
    private inner class AnnotationCanvasView(ctx: Context) : View(ctx) {
        private var startX = 0f
        private var startY = 0f
        private var currentX = 0f
        private var currentY = 0f
        private var isDragging = false

        private val currentPath = Path()
        private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            isClickable = true
            isFocusable = true
            setupTouchListener()
        }

        override fun onMeasure(wSpec: Int, hSpec: Int) {
            val dm = displayContext.resources.displayMetrics
            val margin = (32 * density).toInt()
            val maxW = (dm.widthPixels - margin * 2).coerceAtLeast(100)
            val maxH = (dm.heightPixels - (200 * density).toInt()).coerceAtLeast(100)

            val imgW = sourceBitmap.width.toFloat()
            val imgH = sourceBitmap.height.toFloat()

            var tW = maxW
            var tH = (tW * imgH / imgW).toInt()
            if (tH > maxH) { tH = maxH; tW = (tH * imgW / imgH).toInt() }

            setMeasuredDimension(tW.coerceAtLeast(100), tH.coerceAtLeast(100))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            // 1. Source bitmap
            canvas.drawBitmap(sourceBitmap, null, RectF(0f, 0f, w, h), null)

            // 2. Annotation layer (saveLayer so CLEAR eraser works)
            val layerCount = canvas.saveLayer(0f, 0f, w, h, null)

            for (action in drawActions) applyAction(canvas, action, drawPaint)

            // In-progress action
            if (isDragging) drawCurrentAction(canvas)

            canvas.restoreToCount(layerCount)
        }

        private fun drawCurrentAction(canvas: Canvas) {
            drawPaint.reset()
            drawPaint.isAntiAlias = true
            drawPaint.style = Paint.Style.STROKE
            drawPaint.strokeJoin = Paint.Join.ROUND

            when (activeTool) {
                Tool.PEN -> {
                    drawPaint.color = activeColor
                    drawPaint.strokeWidth = penWidth
                    drawPaint.strokeCap = Paint.Cap.ROUND
                    canvas.drawPath(currentPath, drawPaint)
                }
                Tool.MARKER -> {
                    drawPaint.color = activeColor
                    drawPaint.strokeWidth = markerWidth
                    drawPaint.strokeCap = Paint.Cap.SQUARE
                    canvas.drawPath(currentPath, drawPaint)
                }
                Tool.HIGHLIGHTER -> {
                    drawPaint.color = activeColor
                    drawPaint.alpha = 90
                    drawPaint.strokeWidth = highlightWidth
                    drawPaint.strokeCap = Paint.Cap.SQUARE
                    canvas.drawPath(currentPath, drawPaint)
                }
                Tool.SHAPES -> {
                    drawPaint.color = activeColor
                    drawPaint.strokeWidth = shapeWidth
                    drawPaint.strokeCap = Paint.Cap.ROUND
                    val fakeAction = DrawAction.ShapeAction(activeShape, startX, startY, currentX, currentY, activeColor, shapeWidth)
                    drawShape(canvas, fakeAction, drawPaint)
                }
                Tool.ERASER -> {
                    drawPaint.strokeWidth = eraserWidth
                    drawPaint.strokeCap = Paint.Cap.ROUND
                    drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    canvas.drawPath(currentPath, drawPaint)
                    drawPaint.xfermode = null
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setupTouchListener() {
            setOnTouchListener { _, event ->
                val x = event.x; val y = event.y
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = x; startY = y; currentX = x; currentY = y
                        isDragging = true
                        currentPath.reset()
                        currentPath.moveTo(x, y)
                        // Clear redo when new stroke begins
                        if (activeTool != Tool.SHAPES) redoStack.clear()
                        invalidate(); true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        currentX = x; currentY = y
                        if (activeTool != Tool.SHAPES) currentPath.lineTo(x, y)
                        invalidate(); true
                    }
                    MotionEvent.ACTION_UP -> {
                        currentX = x; currentY = y
                        isDragging = false
                        redoStack.clear() // clear redo on commit
                        when (activeTool) {
                            Tool.PEN -> drawActions.add(DrawAction.PathAction(Path(currentPath), activeColor, penWidth, 255, Paint.Cap.ROUND))
                            Tool.MARKER -> drawActions.add(DrawAction.PathAction(Path(currentPath), activeColor, markerWidth, 255, Paint.Cap.SQUARE))
                            Tool.HIGHLIGHTER -> drawActions.add(DrawAction.PathAction(Path(currentPath), activeColor, highlightWidth, 90, Paint.Cap.SQUARE))
                            Tool.SHAPES -> drawActions.add(DrawAction.ShapeAction(activeShape, startX, startY, x, y, activeColor, shapeWidth))
                            Tool.ERASER -> drawActions.add(DrawAction.EraserAction(Path(currentPath), eraserWidth))
                        }
                        updateUndoRedoState()
                        invalidate(); true
                    }
                    else -> false
                }
            }
        }
    }

    // ─── Programmatic Icon Drawables ─────────────────────────────────────────
    private abstract inner class BaseIcon : Drawable() {
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun iconPaint(color: Int = Color.WHITE, width: Float = 2f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; strokeWidth = width * density; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }

    private inner class PenIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint()
            c.rotate(-45f, w / 2, h / 2)
            c.drawLine(w * .3f, h * .15f, w * .3f, h * .75f, p)
            c.drawLine(w * .7f, h * .15f, w * .7f, h * .75f, p)
            c.drawLine(w * .3f, h * .15f, w * .7f, h * .15f, p)
            val tip = Path().apply { moveTo(w * .3f, h * .75f); lineTo(w * .5f, h * .9f); lineTo(w * .7f, h * .75f) }
            c.drawPath(tip, p)
        }
    }

    private inner class MarkerIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val thick = iconPaint(Color.WHITE, 4f)
            c.drawLine(w * .25f, h * .75f, w * .75f, h * .25f, thick)
            val thin = iconPaint(Color.WHITE, 1.5f)
            c.drawLine(w * .15f, h * .85f, w * .3f, h * .7f, thin)
        }
    }

    private inner class HighlighterIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFCC00"); alpha = 150
                strokeWidth = 8f * density; style = Paint.Style.STROKE
                strokeCap = Paint.Cap.BUTT
            }
            c.drawLine(w * .2f, h * .65f, w * .8f, h * .35f, hiPaint)
            val p = iconPaint(Color.WHITE, 1.5f)
            c.drawLine(w * .25f, h * .75f, w * .75f, h * .25f, p)
        }
    }

    private inner class ShapesIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint()
            c.drawRect(w * .1f, h * .35f, w * .65f, h * .85f, p)
            c.drawOval(RectF(w * .4f, h * .1f, w * .9f, h * .6f), p)
        }
    }

    private inner class EraserIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint()
            val path = Path().apply {
                moveTo(w * .15f, h * .75f); lineTo(w * .5f, h * .25f)
                lineTo(w * .85f, h * .6f); lineTo(w * .5f, h * .85f); close()
            }
            c.drawPath(path, p)
            c.drawLine(w * .15f, h * .75f, w * .5f, h * .85f, iconPaint(Color.WHITE, 3f))
        }
    }

    private inner class RectShapeIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            c.drawRect(w * .15f, h * .2f, w * .85f, h * .8f, iconPaint())
        }
    }

    private inner class CircleShapeIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            c.drawOval(RectF(w * .1f, h * .1f, w * .9f, h * .9f), iconPaint())
        }
    }

    private inner class LineIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            c.drawLine(w * .1f, h * .9f, w * .9f, h * .1f, iconPaint())
        }
    }

    private inner class ArrowIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint()
            c.drawLine(w * .15f, h * .85f, w * .85f, h * .15f, p)
            val tp = Path().apply {
                moveTo(w * .85f, h * .15f)
                lineTo(w * .5f, h * .2f)
                moveTo(w * .85f, h * .15f)
                lineTo(w * .8f, h * .5f)
            }
            c.drawPath(tp, p)
        }
    }

    private inner class TriangleIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val path = Path().apply {
                moveTo(w * .5f, h * .1f); lineTo(w * .9f, h * .9f); lineTo(w * .1f, h * .9f); close()
            }
            c.drawPath(path, iconPaint())
        }
    }

    private inner class UndoIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint()
            val rf = RectF(w * .2f, h * .25f, w * .8f, h * .75f)
            c.drawArc(rf, 150f, 200f, false, p)
            val tp = Path().apply { moveTo(w * .22f, h * .5f); lineTo(w * .08f, h * .5f); lineTo(w * .22f, h * .3f) }
            c.drawPath(tp, p)
        }
    }

    private inner class RedoIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint()
            val rf = RectF(w * .2f, h * .25f, w * .8f, h * .75f)
            c.drawArc(rf, -10f, 200f, false, p)
            val tp = Path().apply { moveTo(w * .78f, h * .5f); lineTo(w * .92f, h * .5f); lineTo(w * .78f, h * .3f) }
            c.drawPath(tp, p)
        }
    }

    private inner class TrashIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint()
            c.drawLine(w * .18f, h * .28f, w * .82f, h * .28f, p)
            c.drawRect(w * .35f, h * .18f, w * .65f, h * .28f, p)
            c.drawRect(w * .28f, h * .28f, w * .72f, h * .85f, p)
            c.drawLine(w * .44f, h * .42f, w * .44f, h * .72f, p)
            c.drawLine(w * .56f, h * .42f, w * .56f, h * .72f, p)
        }
    }

    private inner class SaveIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint(accentColor, 3f)
            val path = Path().apply {
                moveTo(w * .2f, h * .52f); lineTo(w * .42f, h * .72f); lineTo(w * .82f, h * .28f)
            }
            c.drawPath(path, p)
        }
    }

    private inner class CancelIcon : BaseIcon() {
        override fun draw(c: Canvas) {
            val (w, h) = bounds.width().f to bounds.height().f
            val p = iconPaint(Color.parseColor("#FF453A"), 2.5f)
            c.drawLine(w * .28f, h * .28f, w * .72f, h * .72f, p)
            c.drawLine(w * .72f, h * .28f, w * .28f, h * .72f, p)
        }
    }

    // Helper extension to keep icon drawing concise
    private val Int.f get() = this.toFloat()
}
