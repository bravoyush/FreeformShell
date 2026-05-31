package com.example.freeformshell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.view.KeyEvent
import androidx.compose.ui.text.style.TextAlign

class PhoneMirrorOverlayService : Service() {

    private val TAG = "PhoneMirrorOverlay"
    private val NOTIFICATION_CHANNEL_ID = "phone_mirror_overlay_channel"
    private val NOTIFICATION_ID = 2026

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    private lateinit var params: WindowManager.LayoutParams
    private var density = 1.0f
    private var currentDisplayId = -1

    override fun onBind(intent: Intent?): IBinder? = null

    private var isInitialized = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: intent=$intent")
        if (!isInitialized) {
            val displayId = intent?.getIntExtra("EXTRA_DISPLAY_ID", -1) ?: -1
            initializeOverlay(displayId)
            isInitialized = true
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating PhoneMirrorOverlayService")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun initializeOverlay(displayId: Int) {
        Log.d(TAG, "Initializing overlay window for displayId: $displayId")
        // Version-aware External Display Context Resolver
        val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val displays = dm.displays
        val targetDisplay = if (displayId != -1) {
            dm.getDisplay(displayId) ?: (displays.firstOrNull { it.displayId > 0 } ?: dm.getDisplay(0))
        } else {
            displays.firstOrNull { it.displayId > 0 } ?: dm.getDisplay(0)
        }
        currentDisplayId = targetDisplay.displayId
        Log.d(TAG, "Selected target display for phone mirror: ID=${targetDisplay.displayId}, Name=${targetDisplay.name}")

        val displayContext = createDisplayContext(targetDisplay)
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } catch (e: Exception) {
                Log.w(TAG, "createWindowContext failed for display ${targetDisplay.displayId}, falling back to displayContext", e)
                displayContext
            }
        } else {
            displayContext
        }

        windowManager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        density = windowContext.resources.displayMetrics.density

        // Setup WindowManager parameters for premium resizable floating overlay
        val initialWidth = (320 * density).toInt()
        val initialHeight = (640 * density).toInt()

        params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (150 * density).toInt()
            y = (100 * density).toInt()
        }

        lifecycleOwner = ServiceLifecycleOwner().apply { start() }

        composeView = ComposeView(windowContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF8E2DE2),
                        surface = Color(0xFF0E0E12),
                        onSurface = Color.White
                    )
                ) {
                    FloatingMirrorWindow(
                        displayId = currentDisplayId,
                        onClose = { stopSelf() },
                        onMove = { dx, dy -> moveWindow(dx, dy) },
                        onResize = { dw, dh -> resizeWindow(dw, dh) },
                        onMoveToExternalDisplay = {
                            val target = dm.displays.firstOrNull { it.displayId > 0 }
                            if (target != null) {
                                val success = switchToDisplay(target.displayId)
                                if (success) {
                                    android.widget.Toast.makeText(windowContext, "Moved Mirror window to ${target.name}", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(windowContext, "Failed to move mirror window", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(windowContext, "No external display detected. Please connect HDMI or scrcpy virtual display first.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }

        // Decorate the ComposeView view tree owners to enable compose inside service context safely
        lifecycleOwner?.let { owner ->
            composeView!!.setViewTreeLifecycleOwner(owner)
            composeView!!.setViewTreeViewModelStoreOwner(owner)
            composeView!!.setViewTreeSavedStateRegistryOwner(owner)
        }

        try {
            windowManager?.addView(composeView, params)
            Log.d(TAG, "Overlay ComposeView successfully added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach overlay window ComposeView", e)
            stopSelf()
        }
    }

    private fun switchToDisplay(displayId: Int): Boolean {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val target = dm.getDisplay(displayId) ?: return false
        
        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view during display switch", e)
            }
        }
        
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        composeView = null
        
        initializeOverlay(displayId)
        return true
    }

    private fun moveWindow(dx: Int, dy: Int) {
        params.x += dx
        params.y += dy
        try {
            windowManager?.updateViewLayout(composeView, params)
        } catch (e: Exception) {}
    }

    private fun resizeWindow(dw: Int, dh: Int) {
        val minWidth = (240 * density).toInt()
        val minHeight = (480 * density).toInt()
        val maxWidth = (900 * density).toInt()
        val maxHeight = (1800 * density).toInt()

        params.width = (params.width + dw).coerceIn(minWidth, maxWidth)
        params.height = (params.height + dh).coerceIn(minHeight, maxHeight)
        try {
            windowManager?.updateViewLayout(composeView, params)
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroying PhoneMirrorOverlayService")
        
        PhoneMirrorManager.stopMirroring()

        composeView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        composeView = null

        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Interactive Phone Mirroring Window",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows overlay status for the interactive phone mirror."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Interactive Phone Mirroring")
            .setContentText("Floating overlay controller screen is active.")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Custom lifecycle owner to run Compose views inside background service view tree contexts.
 */
class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    init {
        controller.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
}

@Composable
fun FloatingMirrorWindow(
    displayId: Int,
    onClose: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onMoveToExternalDisplay: () -> Unit
) {
    val context = LocalContext.current

    // Observe active state of mirroring stream
    var isMirroring by remember { mutableStateOf(PhoneMirrorManager.isMirroring) }
    DisposableEffect(Unit) {
        val cb = { state: Boolean -> isMirroring = state }
        PhoneMirrorManager.registerCallback(cb)
        onDispose { PhoneMirrorManager.unregisterCallback(cb) }
    }

    // Query physical metrics to center mirror stream aspect ratio correctly
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
    val primaryDisplay = dm.getDisplay(0)
    val realMetrics = android.util.DisplayMetrics()
    primaryDisplay?.getRealMetrics(realMetrics)
    val primaryWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else 1080
    val primaryHeight = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else 2400
    val primaryAspectRatio = primaryWidth.toFloat() / primaryHeight.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE60E0E12))
            .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(14.dp))
    ) {
        if (displayId == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE94057),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "External Display Required",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Phone mirroring is designed for external displays or scrcpy virtual displays. Starting mirroring on the phone screen itself can cause an infinite loop.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onMoveToExternalDisplay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8E2DE2)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Move to External Display", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onClose,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Just Exit", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- Premium Glass Title Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF16161C))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = Color(0xFF8E2DE2),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mirror (${MirrorShortcutHelper.getFriendlyDeviceName(Build.MODEL)})",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Circular Glass close button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // --- Borderless aspect-correct stream rendering canvas ---
            Box(
                modifier = Modifier
                    .fillModifier()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Always render the TextureView container to trigger surface callbacks
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(primaryAspectRatio)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        try {
                                            st.setDefaultBufferSize(primaryWidth, primaryHeight)
                                        } catch (e: Exception) {
                                            Log.e("PhoneMirrorOverlay", "Failed to set surface default size", e)
                                        }
                                        PhoneMirrorManager.startMirroring(
                                            ctx,
                                            Surface(st),
                                            w,
                                            h
                                        )
                                    }

                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        PhoneMirrorManager.stopMirroring()
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }

                                var startX = 0f
                                var startY = 0f
                                var startTime = 0L

                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN -> {
                                            startX = event.x
                                            startY = event.y
                                            startTime = System.currentTimeMillis()
                                        }
                                        MotionEvent.ACTION_UP -> {
                                            val endX = event.x
                                            val endY = event.y
                                            val endTime = System.currentTimeMillis()
                                            val dx = endX - startX
                                            val dy = endY - startY
                                            val duration = endTime - startTime
                                            val distance = Math.hypot(dx.toDouble(), dy.toDouble())

                                            if (distance > 24 && duration > 100) {
                                                PhoneMirrorManager.translateAndInjectSwipe(
                                                    ctx,
                                                    startX, startY, endX, endY,
                                                    v.width.toFloat(), v.height.toFloat(),
                                                    duration.toInt().coerceIn(100, 1000)
                                                )
                                            } else {
                                                PhoneMirrorManager.translateAndInjectTap(
                                                    ctx,
                                                    endX, endY,
                                                    v.width.toFloat(), v.height.toFloat()
                                                )
                                            }
                                        }
                                    }
                                    true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Show elegant overlay when NOT mirroring yet
                if (!isMirroring) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E0E12))
                            .padding(24.dp)
                    ) {
                        if (PhoneMirrorManager.isPausedForRecording) {
                            Icon(
                                imageVector = Icons.Default.PauseCircleFilled,
                                contentDescription = null,
                                tint = Color(0xFF8E2DE2),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Mirror Stream Paused",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Paused during active screen recording to prevent system conflicts.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        } else {
                            CircularProgressIndicator(
                                color = Color(0xFF8E2DE2),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connecting to phone display...",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { PhoneMirrorManager.triggerManualReconnect() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8E2DE2).copy(alpha = 0.3f),
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8E2DE2)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Connection", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // --- Glassmorphic bottom Navigation Control Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFF16161C))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PhoneMirrorManager.injectKeyEvent(KeyEvent.KEYCODE_BACK) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { PhoneMirrorManager.injectKeyEvent(KeyEvent.KEYCODE_HOME) }) {
                    Icon(Icons.Default.Circle, contentDescription = "Home", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { PhoneMirrorManager.injectKeyEvent(KeyEvent.KEYCODE_APP_SWITCH) }) {
                    Icon(Icons.Default.Square, contentDescription = "Recents", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { PhoneMirrorManager.injectKeyEvent(KeyEvent.KEYCODE_POWER) }) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = Color(0xFFE94057), modifier = Modifier.size(18.dp))
                }
            }
        }

        // --- Elegant Diagonal Resize handle in the bottom-right corner ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(20.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                },
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(10.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(bottomEnd = 2.dp)
                    )
            )
        }
    }
}

// Extends Compose Modifier to make layout centering easy
fun Modifier.fillModifier(): Modifier = this.fillMaxWidth().fillMaxHeight()
