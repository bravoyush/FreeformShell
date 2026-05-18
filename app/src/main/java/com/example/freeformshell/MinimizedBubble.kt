package com.example.freeformshell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.Color

@SuppressLint("ClickableViewAccessibility")
class MinimizedBubble(
    private val context: Context,
    private val taskId: Int,
    private val packageName: String,
    private val displayId: Int,
    private val onRestore: (Int) -> Unit
) {
    private val displayContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = dm.getDisplay(displayId) ?: dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    } else {
        context
    }

    private val windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = displayContext.resources.displayMetrics.density
    
    private val view: ImageView = ImageView(context).apply {
        val size = (56 * density).toInt()
        layoutParams = WindowManager.LayoutParams(size, size)
        
        try {
            val icon = context.packageManager.getApplicationIcon(packageName)
            setImageDrawable(icon)
        } catch (e: Exception) {
            setImageResource(android.R.drawable.sym_def_app_icon)
        }
        
        val p = (8 * density).toInt()
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke((2 * density).toInt(), Color.LTGRAY)
        }
        elevation = 10 * density
    }

    private val params = WindowManager.LayoutParams(
        (56 * density).toInt(), (56 * density).toInt(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 100
        y = 300
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    init {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onRestore(taskId)
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun show() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (view.parent == null) {
                    windowManager.addView(view, params)
                }
            } catch (e: Exception) {
                Log.e("MinimizedBubble", "Error showing bubble", e)
            }
        }
    }

    fun hide() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (view.parent != null) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {}
        }
    }
}
