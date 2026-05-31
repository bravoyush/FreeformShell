package com.example.freeformshell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.content.res.ColorStateList
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.content.Intent

@SuppressLint("ClickableViewAccessibility")
class ScreenRecordControllerOverlay(
    private val context: Context,
    private val displayId: Int
) {
    private val TAG = "ScreenRecordControllerOverlay"
    
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
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Theme Colors
    private val accentColor = ThemeManager.getAccentColor(context, Color.parseColor("#8E2DE2"))
    private val isDark = ThemeManager.getThemeMode(context) != 1
    
    private val controllerStyle = run {
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        prefs.getString("pref_controller_style", "obsidian") ?: "obsidian"
    }
    // UI Layout
    private val pillHeight = (60 * density).toInt()
    private val buttonSize = (44 * density).toInt()
    private val iconPadding = (10 * density).toInt()
    
    // Member references for full cross-view coordination
    private var recordBtnViewRef: RecordButtonView? = null
    private var dockMicButtonRef: ImageView? = null
    private var dockFacecamButtonRef: ImageView? = null
    private var popupMicToggleRef: TextView? = null
    private var popupFacecamToggleRef: TextView? = null
    private var dotPulseAnimator: ValueAnimator? = null
    
    private val settingsCard by lazy { buildSettingsPopup() }
    private var isSettingsOpen = false
    
    private val buttonsContainer = LinearLayout(displayContext).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val paddingH = (8 * density).toInt()
        val paddingV = (4 * density).toInt()
        setPadding(paddingH, paddingV, paddingH, paddingV)
    }

    private val scrollDock = android.widget.HorizontalScrollView(displayContext).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            pillHeight
        )
        addView(buttonsContainer)
    }

    private val dockPillView = GlassPillView(displayContext).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            pillHeight
        )
        addView(scrollDock)
    }
    
    private val touchSlop = ViewConfiguration.get(displayContext).scaledTouchSlop

    private inner class DraggablePillContainer(ctx: Context) : LinearLayout(ctx) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            elevation = 16 * density
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - initialTouchX).toInt()
                    val dy = (ev.rawY - initialTouchY).toInt()
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isDragging) {
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = initialX + dx
                        params.y = initialY - dy // Invert Y inside gravity BOTTOM layouts!
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (e: Exception) {}
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        val metrics = displayContext.resources.displayMetrics
                        val borderThreshold = 40 * density
                        val touchX = event.rawX
                        if (touchX < borderThreshold || touchX > (metrics.widthPixels - borderThreshold)) {
                            collapse()
                        }
                    }
                }
                return true
            }
            return super.onTouchEvent(event)
        }
    }

    private val rootView = DraggablePillContainer(displayContext).apply {
        addView(dockPillView)
    }
    
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = (100 * density).toInt()
        windowAnimations = android.R.style.Animation_Dialog
    }
    
    // Tiny collapsed dot overlay
    private val collapsedSize = (36 * density).toInt()
    private val collapsedView = FrameLayout(displayContext).apply {
        val container = CollapsedGlassDotView(displayContext).apply {
            layoutParams = FrameLayout.LayoutParams(collapsedSize, collapsedSize)
        }
        addView(container)
        elevation = 20 * density

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                expand()
            }
            true
        }
    }
    
    private val collapsedParams = WindowManager.LayoutParams(
        collapsedSize,
        collapsedSize,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        x = (20 * density).toInt()
        y = (100 * density).toInt()
        windowAnimations = android.R.style.Animation_Toast
    }
    
    // Timer Overlay
    private val timerTextView = TextView(displayContext).apply {
        setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        text = "00:00"
        gravity = Gravity.CENTER
        visibility = View.GONE
        setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
    }
    
    // State indicators
    private var isMicMuted = false
    private var isFacecamEnabled = false
    private var isCollapsed = false
    
    private var snipOverlay: SnippingOverlay? = null
    private var facecamOverlay: FacecamOverlay? = null
    
    // Gesture variables
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    
    // Auto collapse recording idle timers
    private val AUTO_COLLAPSE_DELAY = 4000L // 4 seconds
    private val autoCollapseRunnable = Runnable {
        if (ScreenRecordManager.isRecordingActive() && !isCollapsed) {
            collapse()
        }
    }
    
    private fun resetAutoCollapseTimer() {
        handler.removeCallbacks(autoCollapseRunnable)
        if (ScreenRecordManager.isRecordingActive() && !isCollapsed) {
            handler.postDelayed(autoCollapseRunnable, AUTO_COLLAPSE_DELAY)
        }
    }
    
    init {
        setupButtons()
    }
    
    private fun createButton(iconDrawable: Drawable, isAccent: Boolean = false, onClick: (View) -> Unit): ImageView {
        val imageView = ImageView(displayContext).apply {
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                leftMargin = (4 * density).toInt()
                rightMargin = (4 * density).toInt()
            }
            val p = (11 * density).toInt()
            setPadding(p, p, p, p)
            setImageDrawable(iconDrawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            
            val iconColor = if (isAccent) accentColor else (if (isDark) Color.WHITE else Color.DKGRAY)
            setColorFilter(iconColor)
            
            // Custom button background styling engine based on active Controller Theme Mode
            val customBg = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val cx = bounds.centerX().toFloat()
                    val cy = bounds.centerY().toFloat()
                    val r = bounds.width() / 2f
                    if (r <= 0) return
                    
                    if (controllerStyle == "solid_minimal") {
                        val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#E6E1E5")
                            style = Paint.Style.FILL
                        }
                        canvas.drawCircle(cx, cy, r, solidPaint)
                        return
                    }
                    
                    if (controllerStyle == "liquid_glass") {
                        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = RadialGradient(cx - r * 0.25f, cy - r * 0.25f, r * 0.8f,
                                Color.argb(if (isDark) 45 else 75, 255, 255, 255), Color.TRANSPARENT,
                                Shader.TileMode.CLAMP)
                        }
                        canvas.drawCircle(cx, cy, r, glossPaint)
                        
                        val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = accentColor.adjustAlpha(if (isDark) 0.35f else 0.55f)
                            style = Paint.Style.STROKE
                            strokeWidth = 1f * density
                        }
                        canvas.drawCircle(cx, cy, r - 1f, rimStrokePaint)
                        
                        val specCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = RadialGradient(cx - r * 0.2f, cy - r * 0.2f, r * 0.3f,
                                Color.argb(if (isDark) 60 else 90, 255, 255, 255), Color.TRANSPARENT,
                                Shader.TileMode.CLAMP)
                        }
                        canvas.drawCircle(cx, cy, r, specCore)
                        return
                    }
                    
                    val insetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(cx, cy, r,
                            intArrayOf(Color.TRANSPARENT, Color.argb(if (isDark) 35 else 15, 0, 0, 0)),
                            floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP)
                    }
                    canvas.drawCircle(cx, cy, r, insetPaint)
                    
                    val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(cx - r * 0.2f, cy - r * 0.2f, r * 0.4f,
                            Color.argb(if (isDark) 40 else 60, 255, 255, 255), Color.TRANSPARENT,
                            Shader.TileMode.CLAMP)
                    }
                    canvas.drawCircle(cx, cy, r, specPaint)
                }

                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: ColorFilter?) {}
                @Suppress("DEPRECATION")
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
            }
            
            val content = if (isAccent) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (controllerStyle == "solid_minimal") {
                        setColor(accentColor.adjustAlpha(0.15f))
                        setStroke((1f * density).toInt(), accentColor.adjustAlpha(0.35f))
                    } else {
                        setColor(accentColor.adjustAlpha(0.25f))
                        setStroke((1f * density).toInt(), accentColor.adjustAlpha(0.5f))
                    }
                }
            } else {
                customBg
            }
            
            background = RippleDrawable(
                ColorStateList.valueOf(accentColor.adjustAlpha(0.35f)),
                content,
                null
            )
            
            setOnClickListener {
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onClick(this)
                }.start()
            }
        }
        return imageView
    }
    
    private fun createButton(iconResId: Int, isAccent: Boolean = false, onClick: (View) -> Unit): ImageView {
        val drawable = displayContext.getDrawable(iconResId) ?: ColorDrawable(Color.TRANSPARENT)
        return createButton(drawable, isAccent, onClick)
    }
    
    private fun Int.adjustAlpha(factor: Float): Int {
        val alpha = Math.round(Color.alpha(this) * factor)
        val red = Color.red(this)
        val green = Color.green(this)
        val blue = Color.blue(this)
        return Color.argb(alpha, red, green, blue)
    }
    
    private fun setupButtons() {
        // Button 1: Programmatic Cog settings Quick Dialog
        val btnSettings = createButton(CogIconDrawable(isDark, if (isDark) Color.WHITE else Color.DKGRAY)) {
            toggleSettingsPopup()
        }
        buttonsContainer.addView(btnSettings)
        
        // Button 2: Microphone Toggle
        val btnMic = createButton(android.R.drawable.ic_btn_speak_now) { v ->
            isMicMuted = !isMicMuted
            val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("pref_screenrecord_mic", !isMicMuted).apply()
            
            syncDockMicIconColor(v as? ImageView)
            resetAutoCollapseTimer()
        }.also { dockMicButtonRef = it }
        buttonsContainer.addView(btnMic)
        
        // Button 3: Morphing Premium Record Button
        val recBtnView = RecordButtonView(displayContext).apply {
            val recSize = buttonSize + (8 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(recSize, recSize).apply {
                leftMargin = (6 * density).toInt()
                rightMargin = (6 * density).toInt()
            }
        }
        recordBtnViewRef = recBtnView
        buttonsContainer.addView(recBtnView)
        
        // Add dynamic recording timer
        buttonsContainer.addView(timerTextView)
        
        // Button 4: Floating Facecam View Toggle
        val btnFacecam = createButton(android.R.drawable.presence_video_online) { v ->
            isFacecamEnabled = !isFacecamEnabled
            syncDockFacecamIconColor(v as? ImageView)
            resetAutoCollapseTimer()
        }.also { dockFacecamButtonRef = it }
        buttonsContainer.addView(btnFacecam)
        
        // Button 5: Snipping screenshot tool
        val btnSnipping = createButton(android.R.drawable.ic_menu_crop) {
            triggerSnippingTool()
        }
        buttonsContainer.addView(btnSnipping)
        
        // Button 6: EXIT close controller button
        val btnClose = createButton(CloseIconDrawable(Color.parseColor("#FF5A5A"))) {
            val intent = Intent(context, FreeformOverlayService::class.java).apply {
                action = "ACTION_STOP_CAPTURE_CONTROL"
            }
            context.startService(intent)
        }
        buttonsContainer.addView(btnClose)
    }
    
    // ─── Settings Popup Assembly ─────────────────────────────────────────────
    private fun buildSettingsPopup(): View {
        val card = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (18 * density).toInt()
            setPadding(padding, padding, padding, padding)
            
            // Premium background: deep dark obsidian slate with a dynamic violet border
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * density
                setColor(Color.parseColor("#12121A"))
                setStroke((1.5f * density).toInt(), accentColor.adjustAlpha(0.6f))
            }
            elevation = 32 * density
            visibility = View.GONE
            
            layoutParams = LinearLayout.LayoutParams(
                (310 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }
        
        // Premium Header with custom Cog Icon
        val headerRow = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        val headerIcon = ImageView(displayContext).apply {
            setImageDrawable(CogIconDrawable(true, Color.parseColor("#8E2DE2")))
            layoutParams = LinearLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt())
            setColorFilter(accentColor)
        }
        val headerText = TextView(displayContext).apply {
            text = "Capture Settings"
            setTextColor(Color.WHITE)
            textSize = 13f
            letterSpacing = 0.04f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (8 * density).toInt()
            }
        }
        headerRow.addView(headerIcon)
        headerRow.addView(headerText)
        card.addView(headerRow)
        card.addView(createSmallDivider())
        
        val presetLabel = TextView(displayContext).apply {
            text = "Quality Preset"
            setTextColor(Color.parseColor("#9EA0A5"))
            textSize = 10f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }
        card.addView(presetLabel)
        
        val chipRow = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (2 * density).toInt(), 0, (4 * density).toInt())
        }
        
        val presets = listOf("high" to "High", "medium" to "Med", "low_size" to "Low", "advanced" to "Adv")
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        var currentPreset = prefs.getString("pref_capture_preset", "high") ?: "high"
        
        val advancedContainer = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (currentPreset == "advanced") View.VISIBLE else View.GONE
        }
        
        val presetButtons = mutableListOf<TextView>()
        for ((presetVal, label) in presets) {
            val chip = TextView(displayContext).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                
                val isSelected = presetVal == currentPreset
                updatePresetChipStyle(this, isSelected)
                
                setOnClickListener {
                    currentPreset = presetVal
                    prefs.edit().putString("pref_capture_preset", presetVal).apply()
                    presetButtons.forEachIndexed { idx, btn ->
                        updatePresetChipStyle(btn, presets[idx].first == currentPreset)
                    }
                    advancedContainer.visibility = if (currentPreset == "advanced") View.VISIBLE else View.GONE
                    
                    try {
                        windowManager.updateViewLayout(rootView, params)
                    } catch (e: Exception) {}
                    
                    Toast.makeText(context, "Preset changed to: $label", Toast.LENGTH_SHORT).show()
                    resetAutoCollapseTimer()
                }
            }
            chipRow.addView(chip)
            presetButtons.add(chip)
        }
        card.addView(chipRow)
        
        // Assemble Advanced container sub-layout
        val resLabel = TextView(displayContext).apply {
            text = "Custom Resolution"
            setTextColor(Color.parseColor("#9EA0A5"))
            textSize = 9.5f
            letterSpacing = 0.05f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }
        advancedContainer.addView(resLabel)
        
        val resChipRow = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val resolutions = listOf("Native", "1080p", "720p", "480p")
        var currentRes = prefs.getString("pref_screenrecord_resolution", "Native") ?: "Native"
        val resButtons = mutableListOf<TextView>()
        
        for (res in resolutions) {
            val chip = TextView(displayContext).apply {
                text = res
                textSize = 9.5f
                gravity = Gravity.CENTER
                
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    leftMargin = (3 * density).toInt()
                    rightMargin = (3 * density).toInt()
                }
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                
                val isSelected = res == currentRes
                updateAdvChipStyle(this, isSelected)
                
                setOnClickListener {
                    currentRes = res
                    prefs.edit().putString("pref_screenrecord_resolution", res).apply()
                    resButtons.forEach { btn ->
                        updateAdvChipStyle(btn, btn.text == currentRes)
                    }
                    resetAutoCollapseTimer()
                }
            }
            resChipRow.addView(chip)
            resButtons.add(chip)
        }
        advancedContainer.addView(resChipRow)
        
        val brLabel = TextView(displayContext).apply {
            text = "Custom Bitrate"
            setTextColor(Color.parseColor("#9EA0A5"))
            textSize = 9.5f
            letterSpacing = 0.05f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }
        advancedContainer.addView(brLabel)
        
        val brChipRow = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val bitrates = listOf(20 to "20M", 12 to "12M", 8 to "8M", 4 to "4M")
        var currentBitrate = prefs.getInt("pref_screenrecord_bitrate", 8)
        val brButtons = mutableListOf<TextView>()
        
        for ((brVal, label) in bitrates) {
            val chip = TextView(displayContext).apply {
                text = label
                textSize = 9.5f
                gravity = Gravity.CENTER
                
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    leftMargin = (3 * density).toInt()
                    rightMargin = (3 * density).toInt()
                }
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                
                val isSelected = brVal == currentBitrate
                updateAdvChipStyle(this, isSelected)
                
                setOnClickListener {
                    currentBitrate = brVal
                    prefs.edit().putInt("pref_screenrecord_bitrate", brVal).apply()
                    brButtons.forEachIndexed { idx, btn ->
                        val itemVal = bitrates[idx].first
                        updateAdvChipStyle(btn, itemVal == currentBitrate)
                    }
                    resetAutoCollapseTimer()
                }
            }
            brChipRow.addView(chip)
            brButtons.add(chip)
        }
        advancedContainer.addView(brChipRow)
        
        card.addView(advancedContainer)
        card.addView(createSmallDivider())
        
        val togglesLabel = TextView(displayContext).apply {
            text = "Devices & Peripherals"
            setTextColor(Color.parseColor("#9EA0A5"))
            textSize = 10f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setPadding(0, (4 * density).toInt(), 0, (6 * density).toInt())
        }
        card.addView(togglesLabel)
        
        val togglesRow = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        val micToggle = TextView(displayContext).apply {
            text = if (isMicMuted) "🎤 Muted" else "🎤 Mic On"
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            background = createToggleButtonBackground(isMicMuted)
            setTextColor(if (isMicMuted) Color.parseColor("#FF5A5A") else Color.GREEN)
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                rightMargin = (6 * density).toInt()
            }
            
            setOnClickListener {
                isMicMuted = !isMicMuted
                prefs.edit().putBoolean("pref_screenrecord_mic", !isMicMuted).apply()
                text = if (isMicMuted) "🎤 Muted" else "🎤 Mic On"
                background = createToggleButtonBackground(isMicMuted)
                setTextColor(if (isMicMuted) Color.parseColor("#FF5A5A") else Color.GREEN)
                
                syncDockMicIconColor(dockMicButtonRef)
                resetAutoCollapseTimer()
            }
        }.also { popupMicToggleRef = it }
        togglesRow.addView(micToggle)
        
        val facecamToggle = TextView(displayContext).apply {
            text = if (isFacecamEnabled) "👤 Facecam On" else "👤 Facecam Off"
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            background = createToggleButtonBackground(!isFacecamEnabled)
            setTextColor(if (isFacecamEnabled) Color.GREEN else Color.GRAY)
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                leftMargin = (6 * density).toInt()
            }
            
            setOnClickListener {
                isFacecamEnabled = !isFacecamEnabled
                text = if (isFacecamEnabled) "👤 Facecam On" else "👤 Facecam Off"
                background = createToggleButtonBackground(!isFacecamEnabled)
                setTextColor(if (isFacecamEnabled) Color.GREEN else Color.GRAY)
                
                syncDockFacecamIconColor(dockFacecamButtonRef)
                resetAutoCollapseTimer()
            }
        }.also { popupFacecamToggleRef = it }
        togglesRow.addView(facecamToggle)
        togglesRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        card.addView(togglesRow)
        
        return card
    }
    
    private fun createSmallDivider(): View = View(displayContext).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (1 * density).toInt()
        ).apply {
            topMargin = (8 * density).toInt()
            bottomMargin = (8 * density).toInt()
        }
        setBackgroundColor(Color.argb(20, 255, 255, 255))
    }
    
    private fun updatePresetChipStyle(tv: TextView, isSelected: Boolean) {
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10 * density
            if (isSelected) {
                setColor(accentColor.adjustAlpha(0.25f))
                setStroke((1.5f * density).toInt(), accentColor.adjustAlpha(0.8f))
            } else {
                setColor(Color.argb(15, 255, 255, 255))
                setStroke((1f * density).toInt(), Color.argb(30, 255, 255, 255))
            }
        }
        tv.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#A0A5B0"))
        tv.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            leftMargin = (3 * density).toInt()
            rightMargin = (3 * density).toInt()
        }
        tv.setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
    }
    
    private fun updateAdvChipStyle(tv: TextView, isSelected: Boolean) {
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            if (isSelected) {
                setColor(accentColor.adjustAlpha(0.25f))
                setStroke((1.5f * density).toInt(), accentColor.adjustAlpha(0.8f))
            } else {
                setColor(Color.argb(15, 255, 255, 255))
                setStroke((1f * density).toInt(), Color.argb(30, 255, 255, 255))
            }
        }
        tv.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#A0A5B0"))
    }
    
    private fun createToggleButtonBackground(isActiveStateMuted: Boolean): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * density
            if (isActiveStateMuted) {
                setColor(Color.argb(20, 255, 90, 90))
                setStroke((1.5f * density).toInt(), Color.argb(100, 255, 90, 90))
            } else {
                setColor(Color.argb(20, 0, 255, 100))
                setStroke((1.5f * density).toInt(), Color.argb(100, 0, 255, 100))
            }
        }
    }
    
    private fun toggleSettingsPopup() {
        isSettingsOpen = !isSettingsOpen
        if (isSettingsOpen) {
            if (settingsCard.parent == null) {
                rootView.addView(settingsCard, 0)
            }
            settingsCard.visibility = View.VISIBLE
            settingsCard.alpha = 0f
            settingsCard.scaleY = 0.8f
            settingsCard.animate()
                .alpha(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            settingsCard.animate()
                .alpha(0f)
                .scaleY(0.8f)
                .setDuration(180)
                .withEndAction {
                    settingsCard.visibility = View.GONE
                    rootView.removeView(settingsCard)
                }
                .start()
        }
        
        try {
            windowManager.updateViewLayout(rootView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update layout during settings toggle", e)
        }
        resetAutoCollapseTimer()
    }
    
    private fun syncDockMicIconColor(imageView: ImageView?) {
        if (isMicMuted) {
            imageView?.setColorFilter(Color.parseColor("#80FF3B30"))
        } else {
            imageView?.setColorFilter(if (isDark) Color.WHITE else Color.DKGRAY)
        }
        
        popupMicToggleRef?.apply {
            text = if (isMicMuted) "🎤 Muted" else "🎤 Mic On"
            background = createToggleButtonBackground(isMicMuted)
            setTextColor(if (isMicMuted) Color.parseColor("#FF5A5A") else Color.GREEN)
        }
    }
    
    private fun syncDockFacecamIconColor(imageView: ImageView?) {
        if (isFacecamEnabled) {
            imageView?.setColorFilter(accentColor)
            showFacecam()
        } else {
            imageView?.setColorFilter(if (isDark) Color.WHITE else Color.DKGRAY)
            hideFacecam()
        }
        
        popupFacecamToggleRef?.apply {
            text = if (isFacecamEnabled) "👤 Facecam On" else "👤 Facecam Off"
            background = createToggleButtonBackground(!isFacecamEnabled)
            setTextColor(if (isFacecamEnabled) Color.GREEN else Color.GRAY)
        }
    }
    
    private fun syncDockMicIconFilter() {
        syncDockMicIconColor(dockMicButtonRef)
    }
    
    private fun syncDockFacecamIconFilter() {
        syncDockFacecamIconColor(dockFacecamButtonRef)
    }
    
    private fun startDotPulseAnimation() {
        dotPulseAnimator?.cancel()
        if (!ScreenRecordManager.isRecordingActive()) {
            collapsedView.alpha = 1f
            return
        }
        
        dotPulseAnimator = ValueAnimator.ofFloat(0.35f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                collapsedView.alpha = animator.animatedValue as Float
            }
            start()
        }
    }
    
    private fun stopDotPulseAnimation() {
        dotPulseAnimator?.cancel()
        dotPulseAnimator = null
        collapsedView.alpha = 1f
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
    
    private fun toggleRecording() {
        if (ScreenRecordManager.isRecordingActive()) {
            ScreenRecordManager.stopRecording()
        } else {
            recordBtnViewRef?.isRecording = true
            timerTextView.visibility = View.VISIBLE
            
            ScreenRecordManager.startRecording(context, displayId, object : ScreenRecordManager.RecordStatusCallback {
                override fun onStart() {
                    Toast.makeText(context, "Recording...", Toast.LENGTH_SHORT).show()
                    resetAutoCollapseTimer()
                }
                
                override fun onTick(durationSec: Int) {
                    val m = durationSec / 60
                    val s = durationSec % 60
                    timerTextView.text = String.format("%02d:%02d", m, s)
                    
                    if (durationSec % 2 == 0) {
                        dockPillView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(400).start()
                    } else {
                        dockPillView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()
                    }
                }
                
                override fun onStop(videoFile: File?, audioFile: File?) {
                    recordBtnViewRef?.isRecording = false
                    timerTextView.visibility = View.GONE
                    dockPillView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    handler.removeCallbacks(autoCollapseRunnable)
                }
                
                override fun onError(error: String) {
                    recordBtnViewRef?.isRecording = false
                    timerTextView.visibility = View.GONE
                    handler.removeCallbacks(autoCollapseRunnable)
                }
            })
        }
    }
    
    private fun triggerSnippingTool() {
        if (snipOverlay != null) return
        collapse()
        
        Toast.makeText(context, "Drag to crop regional screenshot", Toast.LENGTH_SHORT).show()
        
        val displayInfo = DisplayInfo(
            id = displayId,
            name = "", // Name doesn't matter here
            width = displayContext.resources.displayMetrics.widthPixels,
            height = displayContext.resources.displayMetrics.heightPixels
        )
        
        snipOverlay = SnippingOverlay(context, displayId, displayInfo) { croppedFile ->
            expand()
            snipOverlay = null
            if (croppedFile != null) {
                Toast.makeText(context, "Capture saved", Toast.LENGTH_SHORT).show()
            }
        }
        snipOverlay?.show()
    }
    
    private fun showFacecam() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Please grant Camera permission in Settings", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (facecamOverlay != null) return
        facecamOverlay = FacecamOverlay(context, displayId)
        facecamOverlay?.show()
    }
    
    private fun hideFacecam() {
        facecamOverlay?.hide()
        facecamOverlay = null
    }
    
    private fun setupSwipeGesture() {
    }
    
    fun collapse() {
        if (isCollapsed) return
        isCollapsed = true
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (rootView.parent != null) {
                    windowManager.removeView(rootView)
                }
                if (collapsedView.parent == null) {
                    windowManager.addView(collapsedView, collapsedParams)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collapsing overlay", e)
            }
        }
    }
    
    fun expand() {
        if (!isCollapsed) return
        isCollapsed = false
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (collapsedView.parent != null) {
                    windowManager.removeView(collapsedView)
                }
                if (rootView.parent == null) {
                    windowManager.addView(rootView, params)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error expanding overlay", e)
            }
        }
    }
    
    fun handleKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return false
        
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        
        val recMod = prefs.getString("pref_shortcut_record_mod", "Ctrl+Alt") ?: "Ctrl+Alt"
        val recKey = prefs.getString("pref_shortcut_record_key", "R") ?: "R"
        
        val capMod = prefs.getString("pref_shortcut_screenshot_mod", "Ctrl+Alt") ?: "Ctrl+Alt"
        val capKey = prefs.getString("pref_shortcut_screenshot_key", "S") ?: "S"
        
        val cropMod = prefs.getString("pref_shortcut_crop_mod", "Ctrl+Alt") ?: "Ctrl+Alt"
        val cropKey = prefs.getString("pref_shortcut_crop_key", "C") ?: "C"
        
        if (matchesShortcut(event, recMod, recKey)) {
            toggleRecording()
            return true
        }
        if (matchesShortcut(event, capMod, capKey)) {
            ScreenRecordManager.takeScreenshot(context, displayId) { file ->
                if (file != null) {
                    Toast.makeText(context, "Screenshot captured", Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }
        if (matchesShortcut(event, cropMod, cropKey)) {
            triggerSnippingTool()
            return true
        }
        return false
    }
    
    private fun matchesShortcut(event: android.view.KeyEvent, mod: String, charKey: String): Boolean {
        val ctrlRequired = mod.contains("Ctrl", ignoreCase = true)
        val altRequired = mod.contains("Alt", ignoreCase = true)
        val shiftRequired = mod.contains("Shift", ignoreCase = true)
        
        val ctrlMatch = event.isCtrlPressed == ctrlRequired
        val altMatch = event.isAltPressed == altRequired
        val shiftMatch = event.isShiftPressed == shiftRequired
        
        val keyCodeChar = android.view.KeyEvent.keyCodeToString(event.keyCode)
            .removePrefix("KEYCODE_")
            .uppercase(java.util.Locale.US)
            
        val keyMatch = keyCodeChar == charKey.uppercase(java.util.Locale.US)
        
        return ctrlMatch && altMatch && shiftMatch && keyMatch
    }
    
    // Caustic Shimmer ValueAnimator for GlassPillView
    private var shimmerAnimator: ValueAnimator? = null
    
    private fun startShimmerAnimation() {
        if (controllerStyle == "solid_minimal" || controllerStyle == "obsidian") {
            return
        }
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(-0.3f, 1.3f).apply {
            duration = 4500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                dockPillView.let {
                    it.specularCenterX = progress
                    it.invalidate()
                }
            }
            start()
        }
    }
    
    private fun stopShimmerAnimation() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
    }

    fun show() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (rootView.parent == null) {
                    windowManager.addView(rootView, params)
                    startShimmerAnimation()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing controller", e)
            }
        }
    }
    
    fun hide() {
        hideFacecam()
        snipOverlay?.hide()
        snipOverlay = null
        stopShimmerAnimation()
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (rootView.parent != null) {
                    windowManager.removeView(rootView)
                }
                if (collapsedView.parent != null) {
                    windowManager.removeView(collapsedView)
                }
            } catch (e: Exception) {}
        }
    }

    private inner class GlassPillView(ctx: Context) : LinearLayout(ctx) {
        private val cornerRadius = pillHeight / 2f
        private val clipPath = Path()
        
        var specularCenterX = 0.5f
        
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        init {
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_HARDWARE, null)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxW = (displayContext.resources.displayMetrics.widthPixels - (32 * density).toInt()).coerceAtMost((340 * density).toInt())
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            
            val newWidthSpec = when (widthMode) {
                MeasureSpec.EXACTLY -> {
                    MeasureSpec.makeMeasureSpec(widthSize.coerceAtMost(maxW), MeasureSpec.EXACTLY)
                }
                MeasureSpec.AT_MOST -> {
                    MeasureSpec.makeMeasureSpec(widthSize.coerceAtMost(maxW), MeasureSpec.AT_MOST)
                }
                else -> {
                    MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST)
                }
            }
            super.onMeasure(newWidthSpec, heightMeasureSpec)
        }
        
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildShaders(w.toFloat(), h.toFloat())
            
            clipPath.reset()
            clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 
                cornerRadius, cornerRadius, Path.Direction.CW)
        }
        
        private fun rebuildShaders(w: Float, h: Float) {
            if (controllerStyle == "solid_minimal") {
                // Minimal Solid: Pure flat color palette for ultra-efficient rendering
                basePaint.shader = null
                basePaint.color = if (isDark) Color.parseColor("#1D1B20") else Color.parseColor("#F3EDF7")
                
                rimPaint.shader = null
                rimPaint.color = if (isDark) Color.parseColor("#49454F") else Color.parseColor("#CAC4D0")
                rimPaint.strokeWidth = 1f * density
                rimPaint.style = Paint.Style.STROKE
                return
            }
            
            if (controllerStyle == "obsidian") {
                // Obsidian Classic: Translucent dark background with simple outline stroke
                basePaint.shader = null
                basePaint.color = Color.argb(if (isDark) 215 else 235, 20, 20, 25)
                
                rimPaint.shader = null
                rimPaint.color = Color.argb(if (isDark) 80 else 120, 255, 255, 255)
                rimPaint.strokeWidth = 1f * density
                rimPaint.style = Paint.Style.STROKE
                return
            }
            
            // Liquid Glass Premium: Specular 3D gradients and glowing refracting border
            basePaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                if (isDark) intArrayOf(
                    Color.argb(200, 20, 20, 25),
                    Color.argb(220, 10, 10, 15),
                    Color.argb(240, 2, 2, 4)
                ) else intArrayOf(
                    Color.argb(230, 255, 255, 255),
                    Color.argb(210, 245, 245, 248),
                    Color.argb(225, 235, 235, 240)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            
            innerShadowPaint.shader = LinearGradient(
                0f, 0f, w * 0.15f, 0f,
                Color.argb(if (isDark) 90 else 45, 0, 0, 0),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            
            specularPaint.shader = LinearGradient(
                0f, 0f, 0f, h * 0.4f,
                intArrayOf(
                    Color.argb(0, 255, 255, 255),
                    Color.argb(if (isDark) 70 else 100, 255, 255, 255),
                    Color.argb(if (isDark) 30 else 45, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.15f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            
            rimPaint.shader = LinearGradient(
                w * 0.2f, 0f, w * 0.8f, 0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(if (isDark) 140 else 180, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            rimPaint.strokeWidth = 1.5f * density
            rimPaint.style = Paint.Style.STROKE
        }
        
        override fun dispatchDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) {
                super.dispatchDraw(canvas)
                return
            }
            
            canvas.save()
            canvas.clipPath(clipPath)
            
            if (controllerStyle == "solid_minimal") {
                // Minimal Solid: Draw flat color with simple, solid material border (highly efficient)
                canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, basePaint)
                canvas.drawRoundRect(0.5f * density, 0.5f * density, w - 0.5f * density, h - 0.5f * density, cornerRadius, cornerRadius, rimPaint)
                canvas.restore()
                super.dispatchDraw(canvas)
                return
            }
            
            if (controllerStyle == "obsidian") {
                // Obsidian Glassmorphic: Draw translucent obsidian dark glass background and soft outline border
                canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, basePaint)
                canvas.drawRoundRect(0.5f * density, 0.5f * density, w - 0.5f * density, h - 0.5f * density, cornerRadius, cornerRadius, rimPaint)
                canvas.restore()
                super.dispatchDraw(canvas)
                return
            }
            
            // Liquid Glass Premium: High-fidelity multi-paint shiny glossy material
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, basePaint)
            
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, innerShadowPaint)
            canvas.save()
            canvas.scale(-1f, 1f, w / 2f, h / 2f)
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, innerShadowPaint)
            canvas.restore()
            
            val bottomShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, h, 0f, h * 0.7f,
                    Color.argb(if (isDark) 70 else 35, 0, 0, 0),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bottomShadow)
            
            canvas.drawRoundRect(0f, 0f, w, h * 0.4f, cornerRadius, cornerRadius, specularPaint)
            
            val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    w * (specularCenterX - 0.2f), 0f, w * (specularCenterX + 0.2f), h,
                    intArrayOf(Color.TRANSPARENT, Color.argb(if (isDark) 35 else 55, 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, shimmerPaint)
            
            val accentReflect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.85f, h * 0.8f, w * 0.3f,
                    accentColor.adjustAlpha(0.12f),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, accentReflect)
            
            canvas.drawLine(w * 0.2f, 1f * density, w * 0.8f, 1f * density, rimPaint)
            
            canvas.restore()
            super.dispatchDraw(canvas)
        }
    }

    private inner class RecordButtonView(ctx: Context) : View(ctx) {
        var isRecording = false
            set(value) {
                field = value
                animateMorph()
            }
            
        private var morphProgress = 0f
        private var pulseScale = 1f
        
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        
        private val rectF = RectF()
        
        private var morphAnimator: ValueAnimator? = null
        private var pulseAnimator: ValueAnimator? = null
        
        init {
            setOnClickListener {
                animateTap {
                    toggleRecording()
                }
            }
        }
        
        private fun animateMorph() {
            morphAnimator?.cancel()
            val targetProgress = if (isRecording) 1f else 0f
            morphAnimator = ValueAnimator.ofFloat(morphProgress, targetProgress).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    morphProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            
            pulseAnimator?.cancel()
            if (isRecording) {
                pulseAnimator = ValueAnimator.ofFloat(1f, 1.15f).apply {
                    duration = 800
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener {
                        pulseScale = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                pulseScale = 1f
                invalidate()
            }
        }
        
        private fun animateTap(onEnd: () -> Unit) {
            animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                    onEnd()
                }.start()
            }.start()
        }
        
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            if (w <= 0 || h <= 0) return
            
            ringPaint.color = if (isRecording) accentColor.adjustAlpha(0.8f) else Color.argb(180, 255, 255, 255)
            val outerRadius = (minOf(w, h) / 2f) - 2f * density
            canvas.drawCircle(cx, cy, outerRadius, ringPaint)
            
            if (isRecording) {
                val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f * density
                    color = accentColor.adjustAlpha(0.4f * (2f - pulseScale))
                }
                canvas.drawCircle(cx, cy, outerRadius * pulseScale, pulsePaint)
            }
            
            val baseRadius = outerRadius - 4f * density
            val sizeFactor = 1f - (0.15f * morphProgress)
            val currentRadius = baseRadius * sizeFactor
            
            rectF.set(cx - currentRadius, cy - currentRadius, cx + currentRadius, cy + currentRadius)
            
            val targetCorner = 6f * density
            val cornerRadius = currentRadius * (1f - morphProgress) + targetCorner * morphProgress
            
            paint.shader = if (isRecording) {
                LinearGradient(0f, rectF.top, 0f, rectF.bottom,
                    accentColor, accentColor.adjustAlpha(0.7f), Shader.TileMode.CLAMP)
            } else {
                LinearGradient(0f, rectF.top, 0f, rectF.bottom,
                    Color.parseColor("#FF4E50"), Color.parseColor("#F9D423"), Shader.TileMode.CLAMP)
            }
            
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
            
            if (!isRecording) {
                val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx - currentRadius * 0.2f, cy - currentRadius * 0.2f, currentRadius * 0.6f,
                        Color.argb(80, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(cx - currentRadius * 0.2f, cy - currentRadius * 0.2f, currentRadius * 0.5f, specPaint)
            }
        }
    }

    private inner class CollapsedGlassDotView(ctx: Context) : View(ctx) {
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = w / 2f
            if (r <= 0) return
            
            basePaint.shader = LinearGradient(0f, 0f, 0f, h,
                if (isDark) intArrayOf(Color.argb(200, 30, 30, 35), Color.argb(240, 5, 5, 8))
                else intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(225, 235, 235, 240)),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(r, r, r - 2f * density, basePaint)
            
            val pulse = if (ScreenRecordManager.isRecordingActive()) {
                (1f + 0.15f * Math.sin(System.currentTimeMillis() / 200.0).toFloat())
            } else 1f
            
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(r, r, r * 0.4f * pulse,
                    accentColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(r, r, r * 0.5f * pulse, corePaint)
            
            val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(r - r * 0.2f, r - r * 0.2f, r * 0.4f,
                    intArrayOf(Color.argb(90, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(r, r, r - 2f * density, specPaint)
            
            ringPaint.color = Color.argb(180, 255, 255, 255)
            canvas.drawCircle(r, r, r - 1.5f * density, ringPaint)
            
            if (ScreenRecordManager.isRecordingActive() && visibility == VISIBLE && parent != null) {
                postInvalidateDelayed(50)
            }
        }
    }
}

// ─── Custom Vector Cogwheel Gear Icon Drawable ───────────────────────────────
class CogIconDrawable(private val isDark: Boolean, private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@CogIconDrawable.color
        style = Paint.Style.FILL
    }
    
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        val r = minOf(w, h) / 2f
        if (r <= 0) return

        canvas.save()
        val outerRadius = r * 0.5f
        val innerRadius = r * 0.28f
        val toothLength = r * 0.18f
        
        val path = Path()
        val numTeeth = 8
        val angleStep = (2.0 * Math.PI / numTeeth).toFloat()
        val halfToothAngle = angleStep * 0.22f
        
        for (i in 0 until numTeeth) {
            val angle = i * angleStep
            
            val x1 = cx + (outerRadius - 1f) * Math.cos((angle - halfToothAngle).toDouble()).toFloat()
            val y1 = cy + (outerRadius - 1f) * Math.sin((angle - halfToothAngle).toDouble()).toFloat()
            
            val x2 = cx + (outerRadius + toothLength) * Math.cos((angle - halfToothAngle * 0.6f).toDouble()).toFloat()
            val y2 = cy + (outerRadius + toothLength) * Math.sin((angle - halfToothAngle * 0.6f).toDouble()).toFloat()
            
            val x3 = cx + (outerRadius + toothLength) * Math.cos((angle + halfToothAngle * 0.6f).toDouble()).toFloat()
            val y3 = cy + (outerRadius + toothLength) * Math.sin((angle + halfToothAngle * 0.6f).toDouble()).toFloat()
            
            val x4 = cx + (outerRadius - 1f) * Math.cos((angle + halfToothAngle).toDouble()).toFloat()
            val y4 = cy + (outerRadius - 1f) * Math.sin((angle + halfToothAngle).toDouble()).toFloat()
            
            if (i == 0) {
                path.moveTo(x1, y1)
            } else {
                path.lineTo(x1, y1)
            }
            path.lineTo(x2, y2)
            path.lineTo(x3, y3)
            path.lineTo(x4, y4)
            
            val nextAngle = (i + 1) * angleStep
            val nextX1 = cx + (outerRadius - 1f) * Math.cos((nextAngle - halfToothAngle).toDouble()).toFloat()
            val nextY1 = cy + (outerRadius - 1f) * Math.sin((nextAngle - halfToothAngle).toDouble()).toFloat()
            path.lineTo(nextX1, nextY1)
        }
        path.close()
        
        path.addCircle(cx, cy, innerRadius, Path.Direction.CCW)
        
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// ─── Custom Close Outline Cross Icon Drawable ─────────────────────────────
class CloseIconDrawable(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@CloseIconDrawable.color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        val r = minOf(w, h) / 2f
        if (r <= 0) return

        val size = r * 0.35f
        paint.strokeWidth = 2.5f * bounds.width() / 44f
        
        canvas.drawLine(cx - size, cy - size, cx + size, cy + size, paint)
        canvas.drawLine(cx + size, cy - size, cx - size, cy + size, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
