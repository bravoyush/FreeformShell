package com.example.freeformshell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class KeyguardStage { AMBIENT, AUTHENTICATION }

class DesktopKeyguardActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DesktopKeyguardActivity"

        @Volatile
        var isShowing = false
            private set

        val displayKeyListeners = java.util.concurrent.ConcurrentHashMap<Int, (KeyEvent) -> Boolean>()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "User present detected on handset. Auto-dismissing lock screen activity.")
                    if (ThemeManager.isLockSyncDismissEnabled(context)) {
                        finish()
                    }
                }
            }
        }
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.freeformshell.ACTION_DISMISS_KEYGUARD") {
                Log.d(TAG, "Dismiss signal received. Finishing lock screen activity.")
                finish()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isNavKey = keyCode == KeyEvent.KEYCODE_BACK ||
                       keyCode == KeyEvent.KEYCODE_ESCAPE ||
                       keyCode == KeyEvent.KEYCODE_DPAD_UP

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "dispatchKeyEvent: Intercepted KEYCODE_BACK, swallowing event to prevent keyguard bypass")
            val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { display?.displayId ?: 0 } catch (e: Exception) { 0 }
            } else {
                0
            }
            displayKeyListeners[displayId]?.let { listener ->
                listener(event)
            }
            return true // Always swallow back key to enforce keyguard lock security!
        }

        val allowed = ThemeManager.isLockKeyboardAllowed(this)
        Log.d(TAG, "dispatchKeyEvent: allowed=$allowed, isNavKey=$isNavKey, keyCode=$keyCode, action=${event.action}")
        if (!allowed && !isNavKey) {
            Log.d(TAG, "dispatchKeyEvent: Keyboard allowed is false, swallowing non-navigation key event")
            return true // Swallow all non-navigation physical keypresses
        }
        val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { display?.displayId ?: 0 } catch (e: Exception) { 0 }
        } else {
            0
        }
        displayKeyListeners[displayId]?.let { listener ->
            if (listener(event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true

        // Register back press callback to bulletproof keyguard lock screen against system back gesture dismissal on Android 12+
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "onBackPressedDispatcher: Intercepted back press to prevent keyguard bypass")
                val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { display?.displayId ?: 0 } catch (e: Exception) { 0 }
                } else {
                    0
                }
                // Dispatch BACK press to the keyguard stage listener
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
                displayKeyListeners[displayId]?.let { listener ->
                    listener(downEvent)
                    listener(upEvent)
                }
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Force the task to stay in fullscreen mode and cover the entire display area
        try {
            val tId = taskId
            val dId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { display?.displayId ?: 0 } catch (e: Exception) { 0 }
            } else { 0 }
            
            Log.d(TAG, "onCreate: Forcing task $tId on display $dId into FULLSCREEN windowing mode")
            ShellExecutor.setTaskWindowingMode(tId, 1, true) // 1 = WINDOWING_MODE_FULLSCREEN
            
            // On secondary displays, we often need to explicitly set bounds to (0,0,W,H) 
            // to bypass the system's "Work Area" (taskbar) constraints.
            if (dId != 0) {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val d = dm.getDisplay(dId)
                if (d != null) {
                    val metrics = android.util.DisplayMetrics()
                    d.getRealMetrics(metrics)
                    Log.d(TAG, "onCreate: Resizing task $tId to cover full real display ${metrics.widthPixels}x${metrics.heightPixels}")
                    ShellExecutor.resizeTask(tId, 0, 0, metrics.widthPixels, metrics.heightPixels)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Failed to force task windowing mode or bounds", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )
        
        // Remove FLAG_FULLSCREEN and FLAG_LAYOUT_IN_SCREEN as they are deprecated and 
        // can cause the system to force a standard "safe" layout on external displays.
        // We rely on setDecorFitsSystemWindows(false) and InsetsController instead.

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_EXPORTED)
            registerReceiver(dismissReceiver, IntentFilter("com.example.freeformshell.ACTION_DISMISS_KEYGUARD"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
            registerReceiver(dismissReceiver, IntentFilter("com.example.freeformshell.ACTION_DISMISS_KEYGUARD"))
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DesktopLockScreenContent(onUnlockCompleted = {
                    Log.d(TAG, "onUnlockCompleted: Sending ACTION_DESKTOP_UNLOCKED broadcast")
                    sendBroadcast(Intent("com.example.freeformshell.ACTION_DESKTOP_UNLOCKED").apply {
                        setPackage(packageName)
                    })
                    finish()
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Activity resumed, hasWindowFocus=${hasWindowFocus()}")
        // Force the task to stay in fullscreen mode and cover the entire display area
        try {
            val tId = taskId
            val dId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { display?.displayId ?: 0 } catch (e: Exception) { 0 }
            } else { 0 }
            
            Log.d(TAG, "onResume: Forcing task $tId on display $dId into FULLSCREEN")
            ShellExecutor.setTaskWindowingMode(tId, 1, true)
            
            if (dId != 0) {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val d = dm.getDisplay(dId)
                if (d != null) {
                    val metrics = android.util.DisplayMetrics()
                    d.getRealMetrics(metrics)
                    ShellExecutor.resizeTask(tId, 0, 0, metrics.widthPixels, metrics.heightPixels)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume: Failed to force task windowing mode or bounds", e)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged: hasFocus=$hasFocus")
        if (hasFocus) {
            // Force the task to stay in fullscreen mode and cover the entire display area
            try {
                val tId = taskId
                val dId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { display?.displayId ?: 0 } catch (e: Exception) { 0 }
                } else { 0 }
                
                ShellExecutor.setTaskWindowingMode(tId, 1, true)
                
                if (dId != 0) {
                    val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                    val d = dm.getDisplay(dId)
                    if (d != null) {
                        val metrics = android.util.DisplayMetrics()
                        d.getRealMetrics(metrics)
                        ShellExecutor.resizeTask(tId, 0, 0, metrics.widthPixels, metrics.heightPixels)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onWindowFocusChanged: Failed to force task windowing mode or bounds", e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    window.setDecorFitsSystemWindows(false)
                    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    
                    window.insetsController?.let { c ->
                        c.hide(
                            android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars() or
                            android.view.WindowInsets.Type.systemBars()
                        )
                        c.systemBarsBehavior =
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not hide system bars: ${e.message}")
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Keyguard Activity dismissed")
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(dismissReceiver) } catch (e: Exception) {}
        isShowing = false
    }
}

@Composable
fun DesktopLockScreenContent(onUnlockCompleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStage by remember { mutableStateOf(KeyguardStage.AMBIENT) }

    var enteredPin by remember { mutableStateOf("") }
    var statusMsg  by remember { mutableStateOf("") }
    var isError    by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    val shakeX     = remember { androidx.compose.animation.core.Animatable(0f) }

    val keypadScramble = ThemeManager.isLockKeypadScramblerEnabled(context)

    fun checkAndSubmit(pin: String) {
        isChecking = true
        Log.d("DesktopKeyguardActivity", "checkAndSubmit: pin=$pin")
        scope.launch {
            delay(180)
            val saved = ThemeManager.getDesktopLockPin(context)
            Log.d("DesktopKeyguardActivity", "checkAndSubmit: saved=$saved, matches=${pin == saved}")
            isChecking = false
            if (pin == saved) {
                onUnlockCompleted()
            } else {
                isError = true
                statusMsg = "Incorrect PIN"
                shakeX.snapTo(0f)
                listOf(10f, -10f, 8f, -8f, 4f, -4f, 0f).forEach { shakeX.animateTo(it, tween(55)) }
                delay(350)
                enteredPin = ""
                isError = false
                statusMsg = ""
            }
        }
    }

    fun appendDigit(d: String) {
        if (isChecking || enteredPin.length >= 4) {
            Log.d("DesktopKeyguardActivity", "appendDigit ignored: isChecking=$isChecking, len=${enteredPin.length}")
            return
        }
        enteredPin += d
        Log.d("DesktopKeyguardActivity", "appendDigit: enteredPin is now $enteredPin")
        if (enteredPin.length == 4) {
            checkAndSubmit(enteredPin)
        }
    }

    val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try { context.display?.displayId ?: 0 } catch (e: Exception) { 0 }
    } else {
        0
    }

    DisposableEffect(Unit) {
        val myListener: (KeyEvent) -> Boolean = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val digit = when (event.keyCode) {
                    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> "0"
                    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> "1"
                    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> "2"
                    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> "3"
                    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> "4"
                    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> "5"
                    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> "6"
                    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> "7"
                    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> "8"
                    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> "9"
                    else -> null
                }
                if (digit != null) {
                    Log.d("DesktopKeyguardActivity", "Direct key listener parsed digit: $digit on display $displayId")
                    if (currentStage == KeyguardStage.AMBIENT) {
                        currentStage = KeyguardStage.AUTHENTICATION
                    }
                    appendDigit(digit)
                    true
                } else if (event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
                    Log.d("DesktopKeyguardActivity", "Direct key listener parsed delete on display $displayId")
                    if (currentStage == KeyguardStage.AMBIENT) {
                        currentStage = KeyguardStage.AUTHENTICATION
                    }
                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                    true
                } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    Log.d("DesktopKeyguardActivity", "Direct key listener parsed UP arrow (swipe up shortcut) on display $displayId")
                    if (currentStage == KeyguardStage.AMBIENT) {
                        currentStage = KeyguardStage.AUTHENTICATION
                    }
                    true
                } else if (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK) {
                    Log.d("DesktopKeyguardActivity", "Direct key listener parsed ESCAPE/BACK (back shortcut) on display $displayId")
                    if (currentStage == KeyguardStage.AUTHENTICATION) {
                        currentStage = KeyguardStage.AMBIENT
                        enteredPin = "" // clear digits on exit
                    }
                    true
                } else {
                    false
                }
            } else {
                // Ensure key releases of BACK, ESCAPE, and DPAD_UP are also consumed to prevent system dispatching
                event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_DPAD_UP
            }
        }
        DesktopKeyguardActivity.displayKeyListeners[displayId] = myListener
        onDispose {
            if (DesktopKeyguardActivity.displayKeyListeners[displayId] === myListener) {
                DesktopKeyguardActivity.displayKeyListeners.remove(displayId)
            }
        }
    }

    val accentColor = remember {
        val raw = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getColor(android.R.color.system_accent1_200)
            else
                ThemeManager.getAccentColor(context, android.graphics.Color.parseColor("#A8C7FA"))
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#A8C7FA")
        }
        Color(raw)
    }

    val wallpaperTheme = ThemeManager.getLockWallpaperTheme(context)
    val blurRadius = ThemeManager.getLockBlurRadius(context)

    val customWallpaperBitmap = remember(wallpaperTheme) {
        if (wallpaperTheme == 1) {
            val file = File(context.filesDir, "lock_wallpaper.jpg")
            if (file.exists()) {
                try {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    Log.e("DesktopKeyguard", "Failed loading custom wallpaper", e)
                    null
                }
            } else null
        } else null
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Backdrop rendering
        when (wallpaperTheme) {
            1 -> { // Custom blurred gallery image
                if (customWallpaperBitmap != null) {
                    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0f) {
                        Modifier.blur(blurRadius.dp)
                    } else {
                        Modifier
                    }
                    Image(
                        bitmap = customWallpaperBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().then(blurModifier),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                }
            }
            2 -> { // OLED Black
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
            else -> { // Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF06060D), Color(0xFF0C0C1B), Color(0xFF06060D))
                            )
                        )
                )
            }
        }

        // Active Stage Layout
        AnimatedContent(
            targetState = currentStage,
            transitionSpec = {
                if (targetState == KeyguardStage.AUTHENTICATION) {
                    (slideInVertically(tween(480, easing = EaseOutCubic)) { it } +
                     fadeIn(tween(360))) togetherWith
                    (slideOutVertically(tween(480, easing = EaseOutCubic)) { -it } +
                     fadeOut(tween(260)))
                } else {
                    (slideInVertically(tween(480, easing = EaseOutCubic)) { -it } +
                     fadeIn(tween(360))) togetherWith
                    (slideOutVertically(tween(480, easing = EaseOutCubic)) { it } +
                     fadeOut(tween(260)))
                }
            },
            label = "LockStage"
        ) { stage ->
            when (stage) {
                KeyguardStage.AMBIENT -> PixelAmbientScreen(
                    accentColor = accentColor,
                    onUnlockRequested = { currentStage = KeyguardStage.AUTHENTICATION }
                )
                KeyguardStage.AUTHENTICATION -> PixelPinScreen(
                    accentColor = accentColor,
                    enteredPin = enteredPin,
                    onPinChanged = { enteredPin = it },
                    statusMsg = statusMsg,
                    isError = isError,
                    isChecking = isChecking,
                    shakeX = shakeX,
                    appendDigit = { appendDigit(it) },
                    onBackRequested = { currentStage = KeyguardStage.AMBIENT },
                    onUnlockCompleted = onUnlockCompleted
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Ambient Screen View Supporting Clocks, Sliding Layouts, Notifications & Music
// -----------------------------------------------------------------------------

@Composable
fun PixelAmbientScreen(accentColor: Color, onUnlockRequested: () -> Unit) {
    val context = LocalContext.current
    var timeHour   by remember { mutableStateOf("--") }
    var timeMin    by remember { mutableStateOf("--") }
    var timeStr    by remember { mutableStateOf("00:00") }
    var dateStr    by remember { mutableStateOf("") }
    var batteryPct by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    val clockPreset = ThemeManager.getLockClockPreset(context)
    val notificationsMode = ThemeManager.getLockNotificationsMode(context)
    val musicStyle = ThemeManager.getLockMusicControlStyle(context)
    
    val notifications = ActiveNotificationState.notifications
    val showNotificationsPanel = notificationsMode == 2 && notifications.isNotEmpty()

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance().time
            timeHour = SimpleDateFormat("HH", Locale.getDefault()).format(now)
            timeMin  = SimpleDateFormat("mm", Locale.getDefault()).format(now)
            timeStr  = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            dateStr  = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
            try {
                val bs  = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val lvl = bs?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scl = bs?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (lvl >= 0) batteryPct = (lvl * 100f / scl).toInt()
                val st = bs?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                             st == BatteryManager.BATTERY_STATUS_FULL
            } catch (_: Exception) {}
            delay(1000)
        }
    }

    val inf = rememberInfiniteTransition(label = "ambient")
    val bounce by inf.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(950, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bounce"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectDragGestures { _, d -> if (d.y < -20f) onUnlockRequested() } }
            .clickable { onUnlockRequested() }
    ) {
        // Music visualizer backdrop (Style 3)
        if (musicStyle == 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AudioVisualizerBehindClock(accentColor)
            }
        }

        // Top-right battery indicator
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.size(17.dp)
            )
            Text("$batteryPct%", fontSize = 13.sp, color = Color.White.copy(alpha = 0.50f),
                fontWeight = FontWeight.Medium)
        }

        // Main dynamic split view: Left side clock + Right side notifications
        val clockWeight by animateFloatAsState(
            targetValue = if (showNotificationsPanel) 0.5f else 1f,
            animationSpec = tween(600, easing = EaseInOutCubic),
            label = "clockWeight"
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 120.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Panel (Clock)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(clockWeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Clock Layouts
                when (clockPreset) {
                    0 -> { // Thin stacked clock
                        Text(
                            text = timeHour, fontSize = 148.sp, fontWeight = FontWeight.Thin,
                            color = Color.White, letterSpacing = (-4).sp, lineHeight = 136.sp
                        )
                        Text(
                            text = timeMin, fontSize = 148.sp, fontWeight = FontWeight.Thin,
                            color = accentColor, letterSpacing = (-4).sp, lineHeight = 136.sp
                        )
                    }
                    1 -> { // Google Pixel style stacked bold clock
                        Text(
                            text = timeHour, fontSize = 142.sp, fontWeight = FontWeight.Black,
                            color = Color.White, letterSpacing = (-2).sp, lineHeight = 130.sp
                        )
                        Text(
                            text = timeMin, fontSize = 142.sp, fontWeight = FontWeight.Black,
                            color = accentColor, letterSpacing = (-2).sp, lineHeight = 130.sp
                        )
                    }
                    2 -> { // Nothing OS custom Canvas dot-matrix clock
                        NothingClock(timeStr = timeStr, accentColor = accentColor)
                    }
                    3 -> { // Modern analog clock face
                        ElegantAnalogClock(accentColor = accentColor)
                    }
                    else -> { // None (No clock)
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Date
                Text(
                    text = dateStr, fontSize = 17.sp, fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.70f), letterSpacing = 0.2.sp
                )

                // Icons Preview Mode (if selected and not showing full panel)
                if (notificationsMode == 1 && notifications.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        notifications.take(5).forEach { sbn ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val drawable = try {
                                    context.packageManager.getApplicationIcon(sbn.packageName)
                                } catch (_: Exception) {
                                    null
                                }
                                if (drawable != null) {
                                    Image(
                                        bitmap = drawable.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.Notifications, null, tint = accentColor, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        if (notifications.size > 5) {
                            Text(
                                text = "+${notifications.size - 5}",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Music Controls Style 1 (Below Clock)
                if (musicStyle == 0 && ActiveMediaState.title.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    LockMusicCard(accentColor = accentColor, modifier = Modifier.width(320.dp))
                }
            }

            // Right Panel (Full Notifications + Music Style 2)
            AnimatedVisibility(
                visible = showNotificationsPanel,
                enter = slideInHorizontally(tween(600, easing = EaseOutCubic)) { it } + fadeIn(tween(400)),
                exit = slideOutHorizontally(tween(600, easing = EaseInCubic)) { it } + fadeOut(tween(300)),
                modifier = Modifier
                    .fillMaxHeight()
                    .weight((1f - clockWeight).coerceAtLeast(0.001f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    // Music Controls Style 2 (Above Notifications in split panel)
                    if (musicStyle == 1 && ActiveMediaState.title.isNotEmpty()) {
                        LockMusicCard(accentColor = accentColor)
                        Spacer(Modifier.height(10.dp))
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LockNotificationList(accentColor = accentColor)
                    }
                }
            }
        }

        // Bottom: bouncing swipe-up hint
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .clickable { onUnlockRequested() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.ExpandLess, null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(26.dp).offset(y = bounce.dp)
            )
            Text(
                "Swipe up to unlock", fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.38f),
                letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Composable Clocks and Components
// -----------------------------------------------------------------------------

@Composable
fun NothingClock(timeStr: String, accentColor: Color) {
    val dotMatrices = remember {
        mapOf(
            '0' to listOf(
                " ### ",
                "#   #",
                "#  ##",
                "# # #",
                "##  #",
                "#   #",
                " ### "
            ),
            '1' to listOf(
                "  #  ",
                " ##  ",
                "  #  ",
                "  #  ",
                "  #  ",
                "  #  ",
                " ### "
            ),
            '2' to listOf(
                " ### ",
                "#   #",
                "    #",
                "  ## ",
                " #   ",
                "#    ",
                "#####"
            ),
            '3' to listOf(
                " ### ",
                "#   #",
                "    #",
                "  ## ",
                "    #",
                "#   #",
                " ### "
            ),
            '4' to listOf(
                "   # ",
                "  ## ",
                " # # ",
                "#  # ",
                "#####",
                "   # ",
                "   # "
            ),
            '5' to listOf(
                "#####",
                "#    ",
                "#### ",
                "    #",
                "    #",
                "#   #",
                " ### "
            ),
            '6' to listOf(
                "  ## ",
                " #   ",
                "#### ",
                "#   #",
                "#   #",
                "#   #",
                " ### "
            ),
            '7' to listOf(
                "#####",
                "    #",
                "   # ",
                "  #  ",
                " #   ",
                " #   ",
                " #   "
            ),
            '8' to listOf(
                " ### ",
                "#   #",
                "#   #",
                " ### ",
                "#   #",
                "#   #",
                " ### "
            ),
            '9' to listOf(
                " ### ",
                "#   #",
                "#   #",
                " ####",
                "    #",
                "   # ",
                " ##  "
            ),
            ':' to listOf(
                "     ",
                "  #  ",
                "     ",
                "     ",
                "  #  ",
                "     ",
                "     "
            )
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        timeStr.forEach { char ->
            val matrix = dotMatrices[char] ?: dotMatrices['0']!!
            Canvas(modifier = Modifier.size(width = 30.dp, height = 42.dp)) {
                val dotSize = 4f
                val spacingX = size.width / 5
                val spacingY = size.height / 7
                for (row in 0 until 7) {
                    for (col in 0 until 5) {
                        if (matrix[row][col] == '#') {
                            drawCircle(
                                color = if (char == ':') Color.White else accentColor,
                                radius = dotSize,
                                center = androidx.compose.ui.geometry.Offset(
                                    x = col * spacingX + spacingX / 2,
                                    y = row * spacingY + spacingY / 2
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ElegantAnalogClock(accentColor: Color) {
    var time by remember { mutableStateOf(Calendar.getInstance()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            time = Calendar.getInstance()
            delay(50)
        }
    }

    val hour = time.get(Calendar.HOUR)
    val minute = time.get(Calendar.MINUTE)
    val second = time.get(Calendar.SECOND)
    val milli = time.get(Calendar.MILLISECOND)

    val secondSweep = second + milli / 1000f
    val minuteSweep = minute + secondSweep / 60f
    val hourSweep = hour + minuteSweep / 12f

    Canvas(modifier = Modifier.size(200.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2

        drawCircle(
            color = Color.White.copy(alpha = 0.04f),
            radius = radius,
            center = center
        )

        for (i in 0 until 12) {
            val angle = i * 30 * (Math.PI / 180)
            val isMajor = i % 3 == 0
            val tickLength = if (isMajor) 14f else 8f
            val strokeWidth = if (isMajor) 3.5f else 1.5f
            val startX = center.x + (radius - tickLength) * Math.sin(angle).toFloat()
            val startY = center.y - (radius - tickLength) * Math.cos(angle).toFloat()
            val endX = center.x + radius * Math.sin(angle).toFloat()
            val endY = center.y - radius * Math.cos(angle).toFloat()

            drawLine(
                color = if (isMajor) Color.White else Color.White.copy(alpha = 0.35f),
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(endX, endY),
                strokeWidth = strokeWidth
            )
        }

        val hourAngle = hourSweep * 30 * (Math.PI / 180)
        val hourHandLength = radius * 0.48f
        val hourEndX = center.x + hourHandLength * Math.sin(hourAngle).toFloat()
        val hourEndY = center.y - hourHandLength * Math.cos(hourAngle).toFloat()
        drawLine(
            color = Color.White,
            start = center,
            end = androidx.compose.ui.geometry.Offset(hourEndX, hourEndY),
            strokeWidth = 7f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        val minAngle = minuteSweep * 6 * (Math.PI / 180)
        val minHandLength = radius * 0.72f
        val minEndX = center.x + minHandLength * Math.sin(minAngle).toFloat()
        val minEndY = center.y - minHandLength * Math.cos(minAngle).toFloat()
        drawLine(
            color = Color.White.copy(alpha = 0.8f),
            start = center,
            end = androidx.compose.ui.geometry.Offset(minEndX, minEndY),
            strokeWidth = 4.5f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        val secAngle = secondSweep * 6 * (Math.PI / 180)
        val secHandLength = radius * 0.82f
        val secEndX = center.x + secHandLength * Math.sin(secAngle).toFloat()
        val secEndY = center.y - secHandLength * Math.cos(secAngle).toFloat()
        drawLine(
            color = accentColor,
            start = center,
            end = androidx.compose.ui.geometry.Offset(secEndX, secEndY),
            strokeWidth = 2f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        drawCircle(
            color = Color.Black,
            radius = 5f,
            center = center
        )
        drawCircle(
            color = accentColor,
            radius = 2.5f,
            center = center
        )
    }
}

@Composable
fun AudioVisualizerBehindClock(accentColor: Color) {
    val isPlaying = ActiveMediaState.isPlaying
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    if (isPlaying) {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp).graphicsLayer(alpha = 0.12f)) {
            val barCount = 48
            val barWidth = size.width / barCount
            for (i in 0 until barCount) {
                val sinVal1 = Math.sin((i * 0.22 + phase).toDouble()).toFloat()
                val sinVal2 = Math.cos((i * 0.33 - phase * 1.4).toDouble()).toFloat()
                val heightPercent = ((sinVal1 + sinVal2) / 2f + 1f) / 2f
                val barHeight = size.height * 0.15f + size.height * 0.65f * heightPercent
                val x = i * barWidth + barWidth * 0.1f
                val y = size.height - barHeight
                
                drawRoundRect(
                    color = accentColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
                )
            }
        }
    }
}

@Composable
fun LockNotificationList(accentColor: Color) {
    val notifications = ActiveNotificationState.notifications
    
    if (notifications.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No notifications",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.3f),
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(notifications, key = { it.key }) { sbn ->
                NotificationCard(sbn = sbn, accentColor = accentColor)
            }
        }
    }
}

@Composable
fun NotificationCard(sbn: StatusBarNotification, accentColor: Color) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val title = remember(sbn) {
        try {
            sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: sbn.packageName
        } catch (e: Exception) {
            sbn.packageName
        }
    }
    val text = remember(sbn) {
        try {
            sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    val bigText = remember(sbn) {
        try {
            sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val iconBitmap = remember(sbn.packageName) {
                        try {
                            val drawable = context.packageManager.getApplicationIcon(sbn.packageName)
                            drawable.toBitmap().asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.Default.Notifications, null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    if (!isExpanded && text.isNotEmpty()) {
                        Text(
                            text = text,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }
                
                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = bigText.ifEmpty { text },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LockMusicCard(accentColor: Color, modifier: Modifier = Modifier) {
    val isPlaying = ActiveMediaState.isPlaying
    val title = ActiveMediaState.title
    val artist = ActiveMediaState.artist
    val albumArt = ActiveMediaState.albumArt
    val transport = ActiveMediaState.activeController?.transportControls

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = artist,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { transport?.skipToPrevious() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = {
                        if (isPlaying) transport?.pause() else transport?.play()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .background(accentColor, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { transport?.skipToNext() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Stage 2 — PIN authentication
// -----------------------------------------------------------------------------

@Composable
fun PixelPinScreen(
    accentColor: Color,
    enteredPin: String,
    onPinChanged: (String) -> Unit,
    statusMsg: String,
    isError: Boolean,
    isChecking: Boolean,
    shakeX: androidx.compose.animation.core.Animatable<Float, *>,
    appendDigit: (String) -> Unit,
    onBackRequested: () -> Unit,
    onUnlockCompleted: () -> Unit
) {
    val context        = LocalContext.current
    val keypadScramble = ThemeManager.isLockKeypadScramblerEnabled(context)
    val keyboardBypass = ThemeManager.isLockKeyboardBypassEnabled(context)
    val keypadNums     = remember { if (keypadScramble) (0..9).toList().shuffled() else (0..9).toList() }

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBackRequested, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.60f))
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (keyboardBypass) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, tint = accentColor, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Desktop Secured", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Authenticate on your phone to unlock",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center, lineHeight = 20.sp
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.offset(x = shakeX.value.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        statusMsg.ifEmpty { "Enter PIN" }, fontSize = 13.sp,
                        color = if (isError) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.50f),
                        fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (i in 0 until 4) {
                            val filled = i < enteredPin.length
                            val dotScale by animateFloatAsState(
                                targetValue = if (filled) 1f else 0.62f,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                                label = "dot$i"
                            )
                            Box(
                                Modifier.size(13.dp).scale(dotScale).clip(CircleShape)
                                    .background(if (filled) accentColor else Color.White.copy(alpha = 0.20f))
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (row in 0 until 3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (col in 0 until 3) {
                                    val n = keypadNums[row * 3 + col]
                                    PixelKeypadButton(n.toString(), accentColor) { appendDigit(n.toString()) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PixelKeypadActionButton(onClick = { onPinChanged("") }) {
                                Icon(Icons.Default.Clear, null,
                                    tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
                            }
                            val z = keypadNums[9]
                            PixelKeypadButton(z.toString(), accentColor) { appendDigit(z.toString()) }
                            PixelKeypadActionButton(
                                onClick = { if (enteredPin.isNotEmpty()) onPinChanged(enteredPin.dropLast(1)) }
                            ) {
                                Icon(Icons.Default.Backspace, null,
                                    tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PixelKeypadButton(text: String, accentColor: Color, onClick: () -> Unit) {
    val source  = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale   by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "btnScale"
    )
    Box(
        modifier = Modifier
            .size(62.dp).scale(scale).clip(CircleShape)
            .background(Color.White.copy(alpha = if (pressed) 0.20f else 0.10f))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Normal, color = Color.White)
    }
}

@Composable
fun PixelKeypadActionButton(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val source  = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale   by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "actScale"
    )
    Box(
        modifier = Modifier
            .size(62.dp).scale(scale).clip(CircleShape)
            .background(Color.White.copy(alpha = if (pressed) 0.14f else 0.07f))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}
