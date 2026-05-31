package com.example.freeformshell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.Camera
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.FrameLayout

@SuppressLint("ViewConstructor")
@Suppress("DEPRECATION")
class FacecamOverlay(
    private val context: Context,
    private val displayId: Int
) {
    private val TAG = "FacecamOverlay"
    
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
    
    private val size = (140 * density).toInt() 
    
    // Theme Colors
    private val accentColor = ThemeManager.getAccentColor(context, Color.parseColor("#8E2DE2"))
    
    private var camera: Camera? = null
    private val textureView = TextureView(displayContext)
    
    private val container = FrameLayout(displayContext).apply {
        elevation = 16 * density
        
        // Circular clipping with premium glass border
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.BLACK) 
            setStroke((2.5 * density).toInt(), accentColor)
        }
        
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        clipToOutline = true
    }
    
    private val params = WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (40 * density).toInt()
        y = (150 * density).toInt()
    }
    
    // Drag coordinates
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    init {
        setupTextureView()
        setupDragGesture()
    }
    
    private fun setupTextureView() {
        container.addView(textureView)
        
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                startCamera(surface)
            }
            
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                stopCamera()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragGesture() {
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (e: Exception) {}
                    true
                }
                else -> false
            }
        }
    }
    
    private fun startCamera(surface: SurfaceTexture) {
        Thread {
            try {
                // Find front facing camera index
                var frontCameraId = -1
                val cameraInfo = Camera.CameraInfo()
                for (i in 0 until Camera.getNumberOfCameras()) {
                    Camera.getCameraInfo(i, cameraInfo)
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        frontCameraId = i
                        break
                    }
                }
                
                // Fallback to primary camera if front not available
                val camId = if (frontCameraId != -1) frontCameraId else 0
                camera = Camera.open(camId)
                
                camera?.let { cam ->
                    cam.setPreviewTexture(surface)
                    
                    // Set preview orientation for comfortable display (usually 90 or 270 deg)
                    cam.setDisplayOrientation(90)
                    
                    val parameters = cam.parameters
                    // Optimize camera parameters for fast preview flow
                    parameters.setPreviewSize(320, 240)
                    try {
                        cam.parameters = parameters
                    } catch (e: Exception) {}
                    
                    cam.startPreview()
                    Log.d(TAG, "Facecam camera preview started successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting facecam camera preview", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Camera busy or permission denied", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun stopCamera() {
        try {
            camera?.stopPreview()
            camera?.release()
        } catch (e: Exception) {}
        camera = null
    }
    
    fun show() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (container.parent == null) {
                    windowManager.addView(container, params)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing facecam overlay", e)
            }
        }
    }
    
    fun hide() {
        stopCamera()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (container.parent != null) {
                    windowManager.removeView(container)
                }
            } catch (e: Exception) {}
        }
    }
}
