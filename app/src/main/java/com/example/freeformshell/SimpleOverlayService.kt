package com.example.freeformshell

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class SimpleOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val TAG = "SimpleOverlayService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_EXIT") {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "overlay created")
        createNotificationChannel()
        startForeground(12345, createNotification())
        showOverlay()
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val widthPx = (300 * density).toInt()
        val heightPx = (120 * density).toInt()

        Log.d(TAG, "Calculated size: ${widthPx}x${heightPx} (density: $density)")

        val brightRed = 0xFFFF0000.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(brightRed)
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            visibility = View.VISIBLE
        }

        val textView = TextView(this).apply {
            text = "FREEFORM SHELL ACTIVE"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(textView)

        val button = Button(this).apply {
            text = "TEST RESIZE"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
            }
            setOnClickListener {
                Log.d(TAG, "button clicked")
                val cmd = "cmd activity task resize 1057 100 100 900 1400"
                val result = ShellExecutor.exec(cmd)
                Log.d(TAG, "shell command executed: $cmd")
                Log.d(TAG, "shell output: $result")
                
                if (result.contains("Error") || result.contains("Fail")) {
                    Toast.makeText(this@SimpleOverlayService, "Shell Failure: $result", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SimpleOverlayService, "Shell Success", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(button)

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        try {
            windowManager?.addView(root, params)
            overlayView = root
            Log.d(TAG, "overlay attached to window manager")
            
            root.post {
                Log.d(TAG, "Actual overlay view size: ${root.width}x${root.height}, visibility: ${root.visibility}")
            }
            
            Toast.makeText(this, "Overlay UI Started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to add overlay view", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "simple_overlay_channel",
                "Simple Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "simple_overlay_channel")
            .setContentTitle("Freeform Overlay Running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "overlay service destroyed")
        overlayView?.let { 
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "overlay view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
        }
    }
}
