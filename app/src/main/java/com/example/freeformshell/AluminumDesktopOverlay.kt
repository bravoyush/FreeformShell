package com.example.freeformshell

import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.media.session.MediaController
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.service.notification.StatusBarNotification
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt



fun getDynamicColorScheme(context: Context): ColorScheme {
    val themeMode = ThemeManager.getThemeMode(context)
    val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val useDarkMode = when (themeMode) {
        1 -> false // Light
        2 -> true  // Dark
        else -> isSystemDark // Auto
    }
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkMode -> darkColorScheme()
        else -> lightColorScheme()
    }
}

class AluminumDesktopOverlay(private val context: Context, val displayId: Int) {
    private val tag = "AluminumDesktopOverlay"
    private val handler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var isShowing = false

    private var isExpandedState = false
    private val persistenceRunnable = object : Runnable {
        private var fastCycles = 0
        override fun run() {
            if (isExpandedState && isShowing) {
                bringToFront()
                if (fastCycles < 10) {
                    fastCycles++
                    handler.postDelayed(this, 50)
                } else {
                    handler.postDelayed(this, 200)
                }
            } else {
                fastCycles = 0
            }
        }
    }
    var collapseDropdown: (() -> Unit)? = null

    fun setWindowFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val oldFlags = params.flags
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (oldFlags != params.flags) {
            val runnable = Runnable {
                try {
                    wm.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to update layout flags", e)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runnable.run()
            } else {
                handler.post(runnable)
            }
            Log.d(tag, "Updated window focusable flag: focusable=$focusable")
        }
    }

    private fun registerTouchableRegionListener(view: View) {
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
                    val touchableRegion = touchableRegionField.get(insetsInfo) as android.graphics.Region

                    val density = view.context.resources.displayMetrics.density
                    val sbHeight = (ThemeManager.getStatusBarHeight(view.context) * density).toInt()
                    val region = android.graphics.Region()

                    // If dropdown is expanded, include the ENTIRE view so clicks outside the card can be handled in Compose
                    if (isExpandedState) {
                        region.union(android.graphics.Rect(0, 0, view.width, view.height))
                    } else {
                        // Status bar area is only touchable when collapsed
                        region.union(android.graphics.Rect(0, 0, view.width, sbHeight))
                    }

                    touchableRegion.set(region)
                    setTouchableInsetsMethod.invoke(insetsInfo, 3) // TOUCHABLE_INSETS_REGION
                }
                null
            }

            val addListenerMethod = view.viewTreeObserver.javaClass.getMethod(
                "addOnComputeInternalInsetsListener",
                listenerClass
            )
            addListenerMethod.invoke(view.viewTreeObserver, proxy)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register touchable region listener", e)
        }
    }

    fun show() {
        val runnable = Runnable {
            if (isShowing) return@Runnable
            try {
                Log.d(tag, "Showing status bar overlay for Display ID: $displayId")

                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(displayId) ?: run {
                    Log.e(tag, "Display $displayId not found, aborting show status bar")
                    return@Runnable
                }
                val displayContext = context.createDisplayContext(display)
                val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                    } catch (e: Exception) {
                        displayContext
                    }
                } else {
                    displayContext
                }

                val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm

                lifecycleOwner = ServiceLifecycleOwner().apply { start() }

                val view = ComposeView(windowContext).apply {
                    setContent {
                        val context = LocalContext.current
                        var themeMode by remember { mutableStateOf(ThemeManager.getThemeMode(context)) }
                        var configurationTick by remember { mutableStateOf(0) }

                        DisposableEffect(context) {
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(c: Context, intent: Intent) {
                                    themeMode = ThemeManager.getThemeMode(context)
                                    configurationTick++
                                }
                            }
                            val filter = IntentFilter("com.example.freeformshell.THEME_CHANGED").apply {
                                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                            }
                            safeRegisterReceiver(context, receiver, filter)
                            onDispose {
                                try {
                                    context.unregisterReceiver(receiver)
                                } catch (e: Exception) {}
                            }
                        }

                        val colorScheme = remember(themeMode, configurationTick) { getDynamicColorScheme(context) }
                        val statusBarHeight = remember(configurationTick) { ThemeManager.getStatusBarHeight(context) }

                        LaunchedEffect(statusBarHeight, configurationTick) {
                            updateWindowSize(isExpandedState)
                        }

                        MaterialTheme(colorScheme = colorScheme) {
                            StatusBarContent(displayId = displayId, overlay = this@AluminumDesktopOverlay, configurationTick = configurationTick) { expanded ->
                                updateWindowSize(expanded)
                            }
                        }
                    }
                }

                view.setViewTreeLifecycleOwner(lifecycleOwner)
                view.setViewTreeViewModelStoreOwner(lifecycleOwner)
                view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

                composeView = view
                registerTouchableRegionListener(view)

                // Watch for clicks outside of the touchable region to collapse the dropdown
                view.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                        collapseDropdown?.invoke()
                    }
                    false
                }

                val density = displayContext.resources.displayMetrics.density
                val dm = android.util.DisplayMetrics()
                display.getRealMetrics(dm)
                val screenWidth = dm.widthPixels
                val dockPos = ThemeManager.getDockPosition(context, displayId)
                val dockSize = ThemeManager.getDockSize(context, displayId)

                val accessibilityService = FreeformAccessibilityService.instance
                val targetWm = if (accessibilityService != null) {
                    try {
                        val dispCtx = accessibilityService.createDisplayContext(display)
                        dispCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    } catch (e: Exception) {
                        wm
                    }
                } else {
                    wm
                }
                windowManager = targetWm

                val params = WindowManager.LayoutParams().apply {
                    type = if (accessibilityService != null) {
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    }
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.TOP or Gravity.START

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        fitInsetsTypes = 0
                    }

                    if (dockPos == 3) { // Left
                        x = dockSize
                        width = screenWidth - dockSize
                    } else if (dockPos == 4) { // Right
                        x = 0
                        width = screenWidth - dockSize
                    } else {
                        x = 0
                        width = WindowManager.LayoutParams.MATCH_PARENT
                    }
                    height = (ThemeManager.getStatusBarHeight(context) * density).toInt()
                }

                try {
                    targetWm.addView(view, params)
                    Log.d(tag, "Added status bar overlay on display $displayId with type ${params.type} (Accessibility: ${accessibilityService != null})")
                } catch (e: Exception) {
                    Log.w(tag, "Failed to add overlay with type ${params.type}, falling back to TYPE_APPLICATION_OVERLAY", e)
                    try {
                        targetWm.removeViewImmediate(view)
                    } catch (ignored: Exception) {}
                    params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    wm.addView(view, params)
                }
                isShowing = true
                if (displayId == 0) {
                    view.post {
                        val token = view.windowToken
                        if (token != null && FreeformOverlayService.primaryDisplayToken == null) {
                            FreeformOverlayService.primaryDisplayToken = token
                            Log.d(tag, "Harvested primaryDisplayToken from AluminumDesktopOverlay on display 0: $token")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to show Aluminum OS status bar overlay on display $displayId", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            handler.post(runnable)
        }
    }

    private fun updateWindowSize(expanded: Boolean) {
        val runnable = Runnable {
            try {
                val wm = windowManager ?: return@Runnable
                val view = composeView ?: return@Runnable
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return@Runnable

                isExpandedState = expanded
                if (expanded) {
                    // Force the window to the absolute front using the Dim-Behind logic 
                    // AND by removing NOT_FOCUSABLE. This combination is the strongest Z-order signal.
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    params.dimAmount = 0.01f // Effectively invisible but forces Z-order
                    
                    FreeformOverlayService.getInstance()?.ensureSystemUiOnTop(displayId)
                    handler.removeCallbacks(persistenceRunnable)
                    handler.post(persistenceRunnable)
                    
                    view.requestFocus()
                } else {
                    // Restore original flags
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                    params.dimAmount = 0f

                    handler.removeCallbacks(persistenceRunnable)
                }

                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(displayId) ?: run {
                    Log.e(tag, "Display $displayId not found, aborting updateWindowSize")
                    return@Runnable
                }
                val displayContext = context.createDisplayContext(display)
                val dm = android.util.DisplayMetrics()
                display.getRealMetrics(dm)
                val screenWidth = dm.widthPixels
                val screenHeight = dm.heightPixels

                val dockPos = ThemeManager.getDockPosition(context, displayId)
                val dockSize = ThemeManager.getDockSize(context, displayId)
                val density = displayContext.resources.displayMetrics.density

                if (expanded) {
                    if (dockPos == 2) { // Bottom
                        params.x = 0
                        params.y = 0
                        params.width = WindowManager.LayoutParams.MATCH_PARENT
                        params.height = screenHeight - dockSize
                    } else if (dockPos == 3) { // Left
                        params.x = dockSize
                        params.y = 0
                        params.width = screenWidth - dockSize
                        params.height = WindowManager.LayoutParams.MATCH_PARENT
                    } else if (dockPos == 4) { // Right
                        params.x = 0
                        params.y = 0
                        params.width = screenWidth - dockSize
                        params.height = WindowManager.LayoutParams.MATCH_PARENT
                    } else {
                        params.x = 0
                        params.y = 0
                        params.width = WindowManager.LayoutParams.MATCH_PARENT
                        params.height = WindowManager.LayoutParams.MATCH_PARENT
                    }
                } else {
                    params.y = 0
                    if (dockPos == 3) { // Left
                        params.x = dockSize
                        params.width = screenWidth - dockSize
                    } else if (dockPos == 4) { // Right
                        params.x = 0
                        params.width = screenWidth - dockSize
                    } else {
                        params.x = 0
                        params.width = WindowManager.LayoutParams.MATCH_PARENT
                    }
                    params.height = (ThemeManager.getStatusBarHeight(context) * density).toInt()
                }
                params.gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    params.fitInsetsTypes = 0
                }
                wm.updateViewLayout(view, params)
                view.requestLayout()
                Log.d(tag, "Updated overlay window layout size: expanded=$expanded")
            } catch (e: Exception) {
                Log.e(tag, "Failed to update overlay window size", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            handler.post(runnable)
        }
    }

    fun hide() {
        val runnable = Runnable {
            if (!isShowing) return@Runnable
            try {
                Log.d(tag, "Hiding status bar overlay for Display ID: $displayId")
                composeView?.let { view ->
                    windowManager?.removeViewImmediate(view)
                }
                lifecycleOwner?.destroy()
            } catch (e: Exception) {
                Log.e(tag, "Failed to hide status bar overlay on display $displayId", e)
            } finally {
                composeView = null
                lifecycleOwner = null
                windowManager = null
                isShowing = false
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            handler.post(runnable)
        }
    }

    fun bringToFront() {
        val runnable = Runnable {
            if (!isShowing) return@Runnable
            val view = composeView ?: return@Runnable
            val wm = windowManager ?: return@Runnable
            try {
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return@Runnable
                wm.updateViewLayout(view, params)
                Log.d(tag, "bringToFront: Re-applied layout for status bar on display $displayId")
            } catch (e: Exception) {
                Log.e(tag, "bringToFront: Failed to update view layout", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            handler.post(runnable)
        }
    }
}

fun safeRegisterReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter): Intent? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    } catch (e: Exception) {
        val action = if (filter.countActions() > 0) filter.getAction(0) else "unknown"
        Log.e("AluminumStatusBar", "Failed to register receiver for action: $action", e)
        null
    }
}

fun triggerAiAssistant(context: Context, displayId: Int) {
    Thread {
        val options = android.app.ActivityOptions.makeBasic().apply {
            launchDisplayId = displayId
            try {
                val method = this.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(this, 5) // 5 = WINDOWING_MODE_FREEFORM
            } catch (e: Exception) {
                Log.e("AluminumStatusBar", "Failed to set launch windowing mode to freeform for AI assistant", e)
            }
        }.toBundle()

        try {
            val customPackage = ThemeManager.getCustomAiAppPackage(context)
            if (customPackage.isNotEmpty()) {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(customPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent, options)
                    return@Thread
                }
            }
        } catch (e: Exception) {
            Log.e("AluminumStatusBar", "Failed to launch custom AI app: $e")
        }

        try {
            // ACTION_ASSIST is the standard intent to invoke system assistant overlay (like Gemini on display)
            val intent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
        } catch (e: Exception) {
            try {
                // Fallback to voice command
                val intentVoice = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intentVoice, options)
            } catch (e2: Exception) {
                try {
                    // Fallback to keyevent 219 (KEYCODE_ASSIST)
                    ShellExecutor.executeCommand("input keyevent 219")
                } catch (e3: Exception) {
                    Log.e("AluminumStatusBar", "Failed to trigger voice assistant", e3)
                }
            }
        }
    }.start()
}

@Composable
fun StatusBarContent(displayId: Int, overlay: AluminumDesktopOverlay, configurationTick: Int, onToggleExpand: (Boolean) -> Unit) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var showNotificationsDetail by remember { mutableStateOf(false) }
    var isInputActive by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded, isInputActive) {
        overlay.setWindowFocusable(isExpanded && isInputActive)
    }

    // Set collapse callback in overlay
    LaunchedEffect(Unit) {
        overlay.collapseDropdown = {
            if (isExpanded) {
                isExpanded = false
                isInputActive = false
                onToggleExpand(false)
            }
        }
    }

    val showBatteryPercent = remember(configurationTick) { ThemeManager.showBatteryPercent(context) }
    val isQuickSettingsEnabled = remember(configurationTick) { ThemeManager.isQuickSettingsEnabled(context) }
    val statusBarHeight = remember(configurationTick) { ThemeManager.getStatusBarHeight(context) }
    val scale = (statusBarHeight / 36f).coerceIn(0.5f, 2.0f)
    val isSplitClickBentoEnabled = remember(configurationTick) { ThemeManager.isSplitClickBentoEnabled(context) }

    // Time & Date Updates
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(configurationTick) {
        while (true) {
            val formatMode = ThemeManager.getClockFormat(context)
            val is24h = when (formatMode) {
                0 -> false
                1 -> true
                else -> DateFormat.is24HourFormat(context)
            }
            val showAmPm = ThemeManager.getClockShowAmPm(context)
            val timePart = when {
                is24h -> "HH:mm"
                showAmPm -> "h:mm a"
                else -> "h:mm"
            }
            val dateStyle = ThemeManager.getClockDateStyle(context)
            val datePart = when (dateStyle) {
                0 -> "" // Hide Date
                1 -> " M/d/yyyy" // Short (Numbers only, e.g. 6/6/2026)
                2 -> " MMM d" // Medium (No day name, e.g. Jun 6)
                3 -> " EEE, MMM d" // Long (Short day name, e.g. Sat, Jun 6)
                4 -> " EEEE, MMMM d" // Full (Long day name, e.g. Saturday, June 6)
                else -> " EEE, MMM d"
            }
            val formatStr = if (datePart.isEmpty()) timePart else "$timePart$datePart"
            val sdf = SimpleDateFormat(formatStr, Locale.getDefault())
            timeText = sdf.format(Date())
            delay(1000)
        }
    }

    // Battery Level & Charging State
    var batteryPercent by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    val batteryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPercent = (level * 100) / scale
                }
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
    }

    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager }
    var isWifiOn by remember {
        mutableStateOf(
            try {
                wifiManager?.isWifiEnabled ?: false
            } catch (e: Exception) {
                false
            }
        )
    }
    DisposableEffect(context) {
        val filter = IntentFilter(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                isWifiOn = try {
                    wifiManager?.isWifiEnabled ?: false
                } catch (e: Exception) {
                    false
                }
            }
        }
        safeRegisterReceiver(context, receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val bluetoothAdapter = remember {
        try {
            android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            null
        }
    }
    var isBluetoothOn by remember {
        mutableStateOf(
            try {
                bluetoothAdapter?.isEnabled ?: false
            } catch (e: Exception) {
                false
            }
        )
    }
    DisposableEffect(context) {
        val filter = IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR)
                isBluetoothOn = state == android.bluetooth.BluetoothAdapter.STATE_ON
            }
        }
        safeRegisterReceiver(context, receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var currentRingerMode by remember {
        mutableStateOf(
            try {
                audioManager.ringerMode
            } catch (e: Exception) {
                AudioManager.RINGER_MODE_NORMAL
            }
        )
    }
    DisposableEffect(context) {
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                currentRingerMode = try {
                    audioManager.ringerMode
                } catch (e: Exception) {
                    AudioManager.RINGER_MODE_NORMAL
                }
            }
        }
        safeRegisterReceiver(context, receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val isSilentModeActive = currentRingerMode == AudioManager.RINGER_MODE_SILENT || currentRingerMode == AudioManager.RINGER_MODE_VIBRATE
    val isVibrateOnly = currentRingerMode == AudioManager.RINGER_MODE_VIBRATE

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        safeRegisterReceiver(context, batteryReceiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {}
        }
    }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isPowerSaveMode by remember {
        mutableStateOf(
            try {
                powerManager.isPowerSaveMode
            } catch (e: Exception) {
                false
            }
        )
    }
    DisposableEffect(context) {
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                isPowerSaveMode = try {
                    powerManager.isPowerSaveMode
                } catch (e: Exception) {
                    false
                }
            }
        }
        safeRegisterReceiver(context, receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Dynamic connectivity status for status bar icon
    var connectivityType by remember { mutableStateOf("none") }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val activeNetwork = cm.activeNetwork
                    val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
                    connectivityType = when {
                        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
                        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                        else -> "none"
                    }
                } catch (e: Exception) {
                    connectivityType = "none"
                }
            }
            delay(5000)
        }
    }

    // Screen recording state
    var isScreenRecordingOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isScreenRecordingOn = ScreenRecordManager.isRecordingActive()
            delay(1000)
        }
    }

    val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val themeMode = remember(configurationTick) { ThemeManager.getThemeMode(context) }
    val useDarkMode = when (themeMode) {
        1 -> false // Light
        2 -> true  // Dark
        else -> isSystemDark // Auto
    }
    val statusBarBg = Color.Transparent
    val contentColor = Color.White

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Transparent background under the dropdown card (no dimming/scrim)
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = false
                        onToggleExpand(false)
                    }
            ) {
                // Quick Settings Dropdown Card positioned under status bar on the right
                QuickSettingsDropdown(
                    displayId = displayId,
                    showNotificationsDetail = showNotificationsDetail,
                    onShowNotificationsDetailChange = { showNotificationsDetail = it },
                    configurationTick = configurationTick,
                    onDismiss = {
                        isExpanded = false
                        onToggleExpand(false)
                    },
                    onInputActiveChanged = { isInputActive = it },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (statusBarHeight + 6).dp, end = 16.dp)
                )
            }
        }

        // Flat top status bar (statusBarHeight.dp height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight.dp)
                .background(statusBarBg)
                .padding(horizontal = (12 * scale).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Digital Clock & Date
            Text(
                text = timeText,
                color = contentColor,
                fontSize = (13 * scale).sp,
                fontWeight = FontWeight.SemiBold
            )

            // Right side: Sparkle, Notification Bell, and Ethernet/Battery Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)
            ) {
                // 1. Sparkle (AI) Icon
                Box(
                    modifier = Modifier
                        .size((32 * scale).dp)
                        .clip(CircleShape)
                        .clickable {
                            triggerAiAssistant(context, displayId)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SparkleIcon,
                        contentDescription = "AI Sparkle",
                        tint = contentColor,
                        modifier = Modifier.size((16 * scale).dp)
                    )
                }

                // 2. Notification Bell + mini app icon chips
                var isNotifHovered by remember { mutableStateOf(false) }
                val showNotifBackdrop = if (isExpanded) {
                    showNotificationsDetail
                } else {
                    isNotifHovered
                }
                val notifBackdropBg = if (showNotifBackdrop) {
                    if (useDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }

                val notifBackdropHeight = (28 * scale).coerceAtLeast(20f)
                val notifBackdropRadius = notifBackdropHeight / 2
                Row(
                    modifier = Modifier
                        .height(notifBackdropHeight.dp)
                        .clip(RoundedCornerShape(notifBackdropRadius.dp))
                        .background(notifBackdropBg)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                        isNotifHovered = true
                                    } else if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                                        isNotifHovered = false
                                    }
                                }
                            }
                        }
                        .clickable {
                            if (isExpanded && showNotificationsDetail) {
                                isExpanded = false
                                onToggleExpand(false)
                            } else {
                                isExpanded = true
                                showNotificationsDetail = true
                                onToggleExpand(true)
                            }
                        }
                        .padding(horizontal = (6 * scale).dp, vertical = (2 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((2 * scale).dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = contentColor,
                            modifier = Modifier.size((15 * scale).dp)
                        )
                        if (ActiveNotificationState.notifications.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size((5 * scale).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFCC00))
                            )
                        }
                    }

                    // Show up to 3 distinct app icon chips in the status bar
                    val chipPackages = remember(ActiveNotificationState.notifications) {
                        ActiveNotificationState.notifications
                            .map { it.packageName }
                            .distinct()
                            .take(3)
                    }
                    chipPackages.forEach { pkgName ->
                        NotificationAppChip(
                            packageName = pkgName,
                            size = (12 * scale).dp,
                            context = androidx.compose.ui.platform.LocalContext.current
                        )
                    }
                }

                Spacer(modifier = Modifier.width((4 * scale).dp))

                if (isScreenRecordingOn) {
                    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = (4 * scale).dp)
                            .size((10 * scale).dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alpha))
                    )
                }

                // 3. Status Bar Backdrop Icons Container (hover/active show, dynamic icons)
                var isHovered by remember { mutableStateOf(false) }
                val showBackdrop = if (isExpanded) {
                    !showNotificationsDetail && isQuickSettingsEnabled
                } else {
                    isHovered && isQuickSettingsEnabled
                }
                val backdropBg = if (showBackdrop) {
                    if (useDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }

                val clickModifier = if (isQuickSettingsEnabled) {
                    Modifier.clickable {
                        if (isExpanded && !showNotificationsDetail) {
                            isExpanded = false
                            onToggleExpand(false)
                        } else {
                            isExpanded = true
                            showNotificationsDetail = false
                            onToggleExpand(true)
                        }
                    }
                } else {
                    Modifier
                }

                val backdropHeight = (28 * scale).coerceAtLeast(20f)
                val backdropRadius = backdropHeight / 2
                Row(
                    modifier = Modifier
                        .height(backdropHeight.dp)
                        .clip(RoundedCornerShape(backdropRadius.dp))
                        .background(backdropBg)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                        isHovered = true
                                    } else if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                                        isHovered = false
                                    }
                                }
                            }
                        }
                        .then(clickModifier)
                        .padding(horizontal = (8 * scale).dp, vertical = (2 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((6 * scale).dp)
                ) {
                    // Connectivity status: ethernet (if connected), else Wi-Fi (if active)
                    if (connectivityType == "ethernet") {
                        Icon(
                            imageVector = EthernetIcon,
                            contentDescription = "Ethernet",
                            tint = contentColor,
                            modifier = Modifier.size((13 * scale).dp)
                        )
                    } else if (connectivityType == "wifi" || isWifiOn) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi",
                            tint = contentColor,
                            modifier = Modifier.size((13 * scale).dp)
                        )
                    }

                    // Bluetooth status icon
                    if (isBluetoothOn) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth",
                            tint = contentColor,
                            modifier = Modifier.size((13 * scale).dp)
                        )
                    }

                    // Silent/Vibrate mode status icon
                    if (isSilentModeActive) {
                        Icon(
                            imageVector = if (isVibrateOnly) Icons.Default.Vibration else Icons.Default.VolumeOff,
                            contentDescription = "Silent Mode",
                            tint = contentColor,
                            modifier = Modifier.size((13 * scale).dp)
                        )
                    }

                    // Battery percentage box
                    BatteryPill(
                        batteryPercent = batteryPercent,
                        isCharging = isCharging,
                        isPowerSaveMode = isPowerSaveMode,
                        contentColor = contentColor,
                        showPercent = showBatteryPercent,
                        scale = scale
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuickSettingsDropdown(
    displayId: Int,
    showNotificationsDetail: Boolean,
    onShowNotificationsDetailChange: (Boolean) -> Unit,
    configurationTick: Int,
    onDismiss: () -> Unit,
    onInputActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifPanelScale = remember(configurationTick) { ThemeManager.getNotifPanelScale(context) }
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val scaledDensity = remember(currentDensity, notifPanelScale) {
        object : androidx.compose.ui.unit.Density {
            override val density: Float = currentDensity.density * notifPanelScale
            override val fontScale: Float = currentDensity.fontScale * notifPanelScale
        }
    }
    val launchOptions = remember(displayId) {
        android.app.ActivityOptions.makeBasic().apply {
            launchDisplayId = displayId
            try {
                val method = this.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(this, 5) // 5 = WINDOWING_MODE_FREEFORM
            } catch (e: Exception) {
                Log.e("AluminumStatusBar", "Failed to set launch windowing mode to freeform", e)
            }
        }.toBundle()
    }
    val isSplitClickBentoEnabled = remember(configurationTick) { ThemeManager.isSplitClickBentoEnabled(context) }
    val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val savedThemeMode = remember { ThemeManager.getThemeMode(context) }
    var currentThemeMode by remember { mutableStateOf(if (savedThemeMode == 0) (if (isSystemDark) 2 else 1) else savedThemeMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val mode = ThemeManager.getThemeMode(context)
                currentThemeMode = if (mode == 0) (if (isSystemDark) 2 else 1) else mode
            }
        }
        safeRegisterReceiver(context, receiver, IntentFilter("com.example.freeformshell.THEME_CHANGED"))

        val openReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (!showNotificationsDetail) {
                    onShowNotificationsDetailChange(true)
                }
            }
        }
        safeRegisterReceiver(context, openReceiver, IntentFilter("com.example.freeformshell.OPEN_NOTIFS"))

        onDispose {
            try {
                context.unregisterReceiver(receiver)
                context.unregisterReceiver(openReceiver)
            } catch (e: Exception) {}
        }
    }

    var showBtListDetail by remember { mutableStateOf(false) }

    var isCharging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        safeRegisterReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Audio Manager setup
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // Register volume updates
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                currentVol = try {
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                } catch (e: Exception) {
                    0
                }
            }
        }
        safeRegisterReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Brightness setup
    var currentBrightness by remember { mutableStateOf(128) }
    LaunchedEffect(Unit) {
        currentBrightness = try {
            android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
    }

    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager }
    var isWifiOn by remember {
        mutableStateOf(
            try {
                wifiManager?.isWifiEnabled ?: false
            } catch (e: Exception) {
                false
            }
        )
    }
    DisposableEffect(context) {
        val filter = IntentFilter(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                isWifiOn = try {
                    wifiManager?.isWifiEnabled ?: false
                } catch (e: Exception) {
                    false
                }
            }
        }
        safeRegisterReceiver(context, receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val bluetoothAdapter = remember {
        try {
            android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            null
        }
    }
    var isBtOn by remember {
        mutableStateOf(
            try {
                bluetoothAdapter?.isEnabled ?: false
            } catch (e: Exception) {
                false
            }
        )
    }
    DisposableEffect(context) {
        val filter = IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR)
                isBtOn = state == android.bluetooth.BluetoothAdapter.STATE_ON
            }
        }
        safeRegisterReceiver(context, receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Wi-Fi SSID query (periodic)
    var wifiSsid by remember { mutableStateOf("") }
    LaunchedEffect(isWifiOn) {
        while (true) {
            if (isWifiOn) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val out = ShellExecutor.executeCommand("dumpsys wifi | grep 'mWifiInfo'").trim()
                        val ssidMatch = Regex("SSID: \"?([^\",]+)").find(out)
                        val ssid = ssidMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                        wifiSsid = if (ssid == "<unknown ssid>" || ssid.isBlank()) "" else ssid
                    } catch (e: Exception) {
                        wifiSsid = ""
                    }
                }
            } else {
                wifiSsid = ""
            }
            delay(3000)
        }
    }

    // Bluetooth connected device name query
    var btDeviceName by remember { mutableStateOf("") }
    LaunchedEffect(isBtOn) {
        if (isBtOn) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val out = ShellExecutor.executeCommand("dumpsys bluetooth_manager | grep 'name:' | head -1").trim()
                    val nameMatch = Regex("name:\\s*(.+)").find(out)
                    btDeviceName = nameMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                } catch (e: Exception) {
                    btDeviceName = ""
                }
            }
        } else {
            btDeviceName = ""
        }
    }

    // Ringer Mode setup (three modes: Sound, Vibrate, Silent)
    var currentRingerMode by remember {
        mutableStateOf(
            try {
                audioManager.ringerMode
            } catch (e: Exception) {
                AudioManager.RINGER_MODE_NORMAL
            }
        )
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    currentRingerMode = try {
                        audioManager.ringerMode
                    } catch (e: Exception) {
                        AudioManager.RINGER_MODE_NORMAL
                    }
                }
            }
        }
        safeRegisterReceiver(context, receiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Screen Recording State
    var isRecording by remember { mutableStateOf(ScreenRecordManager.isRecordingActive()) }
    var recordingDuration by remember { mutableStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                isRecording = ScreenRecordManager.isRecordingActive()
                recordingDuration = ScreenRecordManager.getRecordingDuration()
                delay(1000)
            }
        }
    }

    // Wi-Fi Detail Sub-panel State
    var showWifiListDetail by remember { mutableStateOf(false) }

    // Auto-rotate State
    var isAutoRotateOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val out = ShellExecutor.executeCommand("settings get system accelerometer_rotation").trim()
                isAutoRotateOn = out == "1"
            } catch (e: Exception) {}
        }
    }

    var activeTiles by remember(configurationTick) { mutableStateOf(ThemeManager.getActiveTiles(context)) }
    var showEditTilesDetail by remember { mutableStateOf(false) }

    val draftLayouts = remember(configurationTick) { mutableStateListOf<TileLayout>() }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var resizingKey by remember { mutableStateOf<String?>(null) }
    var currentPageIndex by remember { mutableStateOf(0) }

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var draggedLibraryTile by remember { mutableStateOf<QSAvailableTile?>(null) }
    var libraryDragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var libraryDragCurrentOffset by remember { mutableStateOf(Offset.Zero) }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val statusBarHeight = remember(configurationTick) { ThemeManager.getStatusBarHeight(context) }

    val cardWidth = when {
        screenWidthDp < 360 -> (screenWidthDp - 24).dp
        screenWidthDp < 480 -> (screenWidthDp * 0.88f).dp
        screenWidthDp < 720 -> 380.dp
        else -> 420.dp
    }

    val isLandscapeOrWide = screenWidthDp >= 700
    val scrollState = rememberScrollState()

    fun isPointInsideCoordinates(point: Offset, coords: LayoutCoordinates?): Boolean {
        val layoutCoords = coords ?: return false
        if (!layoutCoords.isAttached) return false
        val size = layoutCoords.size
        val rootCoords = rootCoordinates ?: return false
        val localPoint = layoutCoords.localPositionOf(rootCoords, point)
        return localPoint.x >= 0 && localPoint.x <= size.width &&
                localPoint.y >= 0 && localPoint.y <= size.height
    }

    LaunchedEffect(showEditTilesDetail) {
        if (showEditTilesDetail) {
            draftLayouts.clear()
            draftLayouts.addAll(ThemeManager.getTileLayouts(context))
            selectedKey = null
            draggingKey = null
            dragOffset = Offset.Zero
            resizingKey = null
            currentPageIndex = 0
        } else {
            onInputActiveChanged(false)
        }
    }

    var showActiveAppsDetail by remember { mutableStateOf(false) }
    var thirdPartyTiles by remember { mutableStateOf<List<QSAvailableTile>>(emptyList()) }
    var activeAppsList by remember { mutableStateOf<List<ActiveAppInfo>>(emptyList()) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var isDndOn by remember { mutableStateOf(false) }
    var isHotspotOn by remember { mutableStateOf(false) }
    var isAirplaneModeOn by remember { mutableStateOf(false) }
    var isLocationOn by remember { mutableStateOf(false) }
    var isExtraDimOn by remember { mutableStateOf(false) }
    var isWirelessDebugOn by remember { mutableStateOf(false) }
    var isNfcOn by remember { mutableStateOf(false) }

    val localHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    DisposableEffect(context) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                isFlashlightOn = enabled
            }
        }
        try {
            cameraManager.registerTorchCallback(callback, localHandler)
        } catch (e: Exception) {}
        onDispose {
            try {
                cameraManager.unregisterTorchCallback(callback)
            } catch (e: Exception) {}
        }
    }

    fun toggleFlashlight() {
        Thread {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, !isFlashlightOn)
            } catch (e: Exception) {
                Log.e("AluminumQS", "Failed to toggle torch", e)
            }
        }.start()
    }

    LaunchedEffect(Unit) {
        while (true) {
            val zen = android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
            isDndOn = zen != 0
            delay(2000)
        }
    }

    fun toggleDnd() {
        val nextVal = if (isDndOn) 0 else 1
        isDndOn = !isDndOn
        Thread {
            try {
                ShellExecutor.executeCommand("settings put global zen_mode $nextVal")
            } catch (e: Exception) {}
        }.start()
    }

    DisposableEffect(context) {
        val filter = IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val state = intent.getIntExtra("wifi_state", 11)
                isHotspotOn = state == 13 || state == 113
            }
        }
        safeRegisterReceiver(context, receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    fun toggleHotspot() {
        val nextState = !isHotspotOn
        isHotspotOn = nextState
        Thread {
            try {
                if (nextState) {
                    ShellExecutor.executeCommand("cmd connectivity hotspot start")
                    ShellExecutor.executeCommand("cmd wifi start-softap")
                } else {
                    ShellExecutor.executeCommand("cmd connectivity hotspot stop")
                    ShellExecutor.executeCommand("cmd wifi stop-softap")
                }
            } catch (e: Exception) {}
        }.start()
    }

    LaunchedEffect(Unit) {
        while (true) {
            isAirplaneModeOn = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            delay(2000)
        }
    }

    fun toggleAirplaneMode() {
        val nextVal = if (isAirplaneModeOn) 0 else 1
        isAirplaneModeOn = !isAirplaneModeOn
        Thread {
            try {
                ShellExecutor.executeCommand("settings put global airplane_mode_on $nextVal")
                ShellExecutor.executeCommand("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ${nextVal == 1}")
            } catch (e: Exception) {}
        }.start()
    }

    LaunchedEffect(Unit) {
        while (true) {
            isLocationOn = android.provider.Settings.Secure.getInt(context.contentResolver, android.provider.Settings.Secure.LOCATION_MODE, 0) != 0
            delay(2000)
        }
    }

    fun toggleLocation() {
        val nextVal = if (isLocationOn) 0 else 3
        isLocationOn = !isLocationOn
        Thread {
            try {
                ShellExecutor.executeCommand("settings put secure location_mode $nextVal")
            } catch (e: Exception) {}
        }.start()
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val res = ShellExecutor.executeCommand("settings get secure reduce_bright_colors_activated")
                isExtraDimOn = res.trim() == "1"
            } catch (e: Exception) {
                try {
                    isExtraDimOn = android.provider.Settings.Secure.getInt(context.contentResolver, "reduce_bright_colors_activated", 0) == 1
                } catch (ex: Exception) {
                    isExtraDimOn = false
                }
            }
            delay(2000)
        }
    }

    fun toggleExtraDim() {
        val nextVal = if (isExtraDimOn) 0 else 1
        isExtraDimOn = !isExtraDimOn
        Thread {
            try {
                ShellExecutor.executeCommand("settings put secure reduce_bright_colors_activated $nextVal")
            } catch (e: Exception) {}
        }.start()
    }

    LaunchedEffect(Unit) {
        while (true) {
            isWirelessDebugOn = android.provider.Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
            delay(2000)
        }
    }

    fun toggleWirelessDebugging() {
        val nextVal = if (isWirelessDebugOn) 0 else 1
        isWirelessDebugOn = !isWirelessDebugOn
        Thread {
            try {
                ShellExecutor.executeCommand("settings put global adb_wifi_enabled $nextVal")
            } catch (e: Exception) {}
        }.start()
    }

    LaunchedEffect(Unit) {
        while (true) {
            isNfcOn = try {
                val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
                nfcAdapter?.isEnabled == true
            } catch (e: Exception) {
                false
            }
            delay(2000)
        }
    }

    fun toggleNfc() {
        val nextState = !isNfcOn
        isNfcOn = nextState
        Thread {
            try {
                if (nextState) {
                    ShellExecutor.executeCommand("svc nfc enable")
                } else {
                    ShellExecutor.executeCommand("svc nfc disable")
                }
            } catch (e: Exception) {}
        }.start()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val intent = Intent("android.service.quicksettings.action.QS_TILE")
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    pm.queryIntentServices(intent, 0)
                }
                val tilesList = resolveInfos.mapNotNull { info ->
                    val serviceInfo = info.serviceInfo ?: return@mapNotNull null
                    val pkg = serviceInfo.packageName
                    val name = serviceInfo.name
                    val key = "$pkg/$name"
                    val label = info.loadLabel(pm).toString()

                    var customIcon: ImageBitmap? = null
                    try {
                        val drawable = info.loadIcon(pm)
                        if (drawable != null) {
                            customIcon = drawable.toBitmap(96, 96).asImageBitmap()
                        }
                    } catch (e: Exception) {
                        Log.e("AluminumQS", "Failed to load icon for $key", e)
                    }

                    QSAvailableTile(
                        key = key,
                        label = label,
                        icon = null,
                        customIcon = customIcon
                    )
                }
                withContext(Dispatchers.Main) {
                    thirdPartyTiles = tilesList
                }
            } catch (e: Exception) {
                Log.e("AluminumQS", "Error querying third-party tiles", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val list = getActiveAppsNatively(context)
            activeAppsList = list
            delay(3000)
        }
    }

    fun stopActiveApp(packageName: String) {
        Thread {
            try {
                ShellExecutor.executeCommand("am force-stop $packageName")
                val newList = getActiveAppsNatively(context)
                activeAppsList = newList
            } catch (e: Exception) {
                Log.e("ActiveApps", "Failed to force-stop $packageName", e)
            }
        }.start()
    }

    fun toggleAutoRotate() {
        isAutoRotateOn = !isAutoRotateOn
        Thread {
            val valStr = if (isAutoRotateOn) "1" else "0"
            ShellExecutor.executeCommand("settings put system accelerometer_rotation $valStr")
        }.start()
    }

    fun toggleScreenRecord() {
        if (isRecording) {
            ScreenRecordManager.stopRecording()
            isRecording = false
        } else {
            ScreenRecordManager.startRecording(context, displayId, object : ScreenRecordManager.RecordStatusCallback {
                override fun onStart() {}
                override fun onTick(durationSec: Int) {
                    recordingDuration = durationSec
                }
                override fun onStop(videoFile: java.io.File?, audioFile: java.io.File?) {
                    isRecording = false
                }
                override fun onError(error: String) {
                    isRecording = false
                }
            })
            isRecording = true
        }
    }


    // semantic solid color styling (one-to-one premium copy of reference image 1)
    val cardBg = MaterialTheme.colorScheme.surface
    val activeTileBg = MaterialTheme.colorScheme.primary
    val activeTileContent = MaterialTheme.colorScheme.onPrimary
    val inactiveTileBg = MaterialTheme.colorScheme.surfaceVariant
    val inactiveTileContent = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val contentColor = MaterialTheme.colorScheme.onSurface
    val isDark = currentThemeMode == 2 || (currentThemeMode == 0 && isSystemDark)

    val libraryContent = @Composable {
        if (showEditTilesDetail) {
            TileLibraryCard(
                availableTiles = remember(thirdPartyTiles) { getBuiltInTiles() + thirdPartyTiles },
                draftLayouts = draftLayouts,
                currentPageIndex = currentPageIndex,
                onNavigateToPage = { currentPageIndex = it },
                cardBg = cardBg,
                contentColor = contentColor,
                activeTileBg = activeTileBg,
                inactiveTileBg = inactiveTileBg,
                cardWidth = if (isLandscapeOrWide) 300.dp else cardWidth,
                cardHeight = if (isLandscapeOrWide) 480.dp else 260.dp,
                getLocalPos = { coords ->
                    rootCoordinates?.localPositionOf(coords, Offset.Zero) ?: Offset.Zero
                },
                onLibraryDragStart = { tile, startPos ->
                    draggedLibraryTile = tile
                    libraryDragStartOffset = startPos
                    libraryDragCurrentOffset = Offset.Zero
                },
                onLibraryDragUpdate = { offset ->
                    libraryDragCurrentOffset = offset
                },
                onLibraryDragEnd = { tile, offset ->
                    val finalDropPoint = libraryDragStartOffset + offset
                    val inEditor = isPointInsideCoordinates(finalDropPoint, editorCoordinates)
                    if (tile != null && inEditor) {
                        val defaultW = if (tile.key == "music") 4 else 2
                        val defaultH = 1
                        val slot = findFirstAvailableSlot(defaultW, defaultH, currentPageIndex, draftLayouts)
                        if (slot != null) {
                            draftLayouts.add(TileLayout(tile.key, slot.first, slot.second, defaultW, defaultH, currentPageIndex))
                        } else {
                            val otherPage = if (currentPageIndex == 0) 1 else 0
                            val slotOther = findFirstAvailableSlot(defaultW, defaultH, otherPage, draftLayouts)
                            if (slotOther != null) {
                                draftLayouts.add(TileLayout(tile.key, slotOther.first, slotOther.second, defaultW, defaultH, otherPage))
                                currentPageIndex = otherPage
                            } else {
                                Toast.makeText(context, "No space available on any page!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    draggedLibraryTile = null
                },
                onInputActiveChanged = onInputActiveChanged
            )
        }
    }

    val mainPanelContent = @Composable {
        Card(
            modifier = Modifier
                .width(cardWidth)
                .onGloballyPositioned { editorCoordinates = it }
                .clickable(enabled = false) {} // block click propagation
                .shadow(16.dp, shape = RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                val isSubPanelOpen = showWifiListDetail || showBtListDetail || showNotificationsDetail || showEditTilesDetail || showActiveAppsDetail
                if (!isSubPanelOpen) {
                    // A. Top Header Row (Pencil, Settings Gear, Dark Theme Toggle, Power, Caret)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Toggles",
                            tint = contentColor,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { showEditTilesDetail = true }
                                .padding(6.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = contentColor,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onDismiss()
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent, launchOptions)
                                }
                                .padding(6.dp)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Dynamic theme mode toggle pill
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(inactiveTileBg)
                                .clickable {
                                    val newMode = if (isDark) 1 else 2
                                    Thread {
                                        val cmd = if (isDark) "cmd uimode night no" else "cmd uimode night yes"
                                        ShellExecutor.executeCommand(cmd)
                                    }.start()
                                    ThemeManager.setThemeMode(context, newMode)
                                    currentThemeMode = newMode
                                    try {
                                        FreeformOverlayService.requestRefresh()
                                    } catch (e: Exception) {}
                                }
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = inactiveTileContent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDark) "Dark theme" else "Light theme",
                                color = inactiveTileContent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(inactiveTileBg)
                                .clickable {
                                    Thread {
                                        ShellExecutor.executeCommand("input keyevent 26")
                                    }.start()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Power Menu",
                                tint = inactiveTileContent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(inactiveTileBg)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Collapse Settings",
                                tint = inactiveTileContent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                if (showWifiListDetail) {
                    WifiSubPanel(
                        isWifiOn = isWifiOn,
                        onToggleWifi = { enabled ->
                            isWifiOn = enabled
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val cmd = if (enabled) "svc wifi enable" else "svc wifi disable"
                                    ShellExecutor.executeWifiCommand(cmd)
                                } catch (e: Exception) {
                                    Log.e("AluminumQS", "Error toggling Wi-Fi", e)
                                }
                            }
                        },
                        wifiSsid = wifiSsid,
                        onBack = { showWifiListDetail = false },
                        cardBg = cardBg,
                        contentColor = contentColor,
                        activeTileBg = activeTileBg,
                        activeTileContent = activeTileContent,
                        inactiveTileBg = inactiveTileBg,
                        inactiveTileContent = inactiveTileContent,
                        launchOptions = launchOptions,
                        onInputActiveChanged = onInputActiveChanged
                    )
                } else if (showBtListDetail) {
                    BtSubPanel(
                        isBtOn = isBtOn,
                        onToggleBt = { enabled ->
                            isBtOn = enabled
                            Thread {
                                val cmd = if (enabled) "svc bluetooth enable" else "svc bluetooth disable"
                                ShellExecutor.executeCommand(cmd)
                            }.start()
                        },
                        onBack = { showBtListDetail = false },
                        cardBg = cardBg,
                        contentColor = contentColor,
                        activeTileBg = activeTileBg,
                        activeTileContent = activeTileContent,
                        inactiveTileBg = inactiveTileBg,
                        inactiveTileContent = inactiveTileContent,
                        launchOptions = launchOptions
                    )
                } else if (showNotificationsDetail) {
                    NotificationSubPanel(
                        onBack = { onShowNotificationsDetailChange(false) },
                        cardBg = cardBg,
                        contentColor = contentColor,
                        activeTileBg = activeTileBg,
                        inactiveTileBg = inactiveTileBg,
                        onDismissDropdown = onDismiss,
                        launchOptions = launchOptions,
                        onInputActiveChanged = onInputActiveChanged
                    )
                } else if (showEditTilesDetail) {
                    EditTilesSubPanel(
                        activeTiles = activeTiles,
                        onUpdateActiveTiles = { newList ->
                            activeTiles = newList
                            ThemeManager.setActiveTiles(context, newList)
                        },
                        availableTiles = remember(thirdPartyTiles) { getBuiltInTiles() + thirdPartyTiles },
                        onBack = { showEditTilesDetail = false },
                        cardBg = cardBg,
                        contentColor = contentColor,
                        activeTileBg = activeTileBg,
                        inactiveTileBg = inactiveTileBg,
                        context = context,
                        draftLayouts = draftLayouts,
                        selectedKey = selectedKey,
                        onSelectedKeyChange = { selectedKey = it },
                        draggingKey = draggingKey,
                        onDraggingKeyChange = { draggingKey = it },
                        dragOffset = dragOffset,
                        onDragOffsetChange = { dragOffset = it },
                        resizingKey = resizingKey,
                        onResizingKeyChange = { resizingKey = it },
                        currentPageIndex = currentPageIndex,
                        onCurrentPageIndexChange = { currentPageIndex = it }
                    )
                } else if (showActiveAppsDetail) {
                    ActiveAppsSubPanel(
                        activeApps = activeAppsList,
                        onStopApp = { pkg ->
                            stopActiveApp(pkg)
                        },
                        onBack = { showActiveAppsDetail = false },
                        cardBg = cardBg,
                        contentColor = contentColor,
                        activeTileBg = activeTileBg,
                        inactiveTileBg = inactiveTileBg,
                        context = context
                    )
                } else {

                    // B. Sliders Group (Thick pill brightness and volume sliders)
                    // Brightness Slider + Gear side button (circular shape when inactive)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        AluminumSlider(
                            value = currentBrightness.toFloat(),
                            onValueChange = {
                                currentBrightness = it.toInt()
                                Thread {
                                    ShellExecutor.executeCommand("settings put system screen_brightness ${it.toInt()}")
                                }.start()
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f).height(44.dp),
                            activeColor = activeTileBg,
                            inactiveColor = inactiveTileBg,
                            thumbColor = activeTileContent,
                            iconStart = Icons.Default.WbSunny,
                            activeContentColor = activeTileContent,
                            inactiveContentColor = inactiveTileContent
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(inactiveTileBg)
                                .clickable {
                                    onDismiss()
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent, launchOptions)
                                    } catch (e: Exception) {}
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Display Settings",
                                tint = inactiveTileContent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Volume Slider + 3-Dot Settings button (circular shape when inactive)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        AluminumSlider(
                            value = currentVol.toFloat(),
                            onValueChange = {
                                currentVol = it.toInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                            },
                            valueRange = 0f..maxVol.toFloat(),
                            modifier = Modifier.weight(1f).height(44.dp),
                            activeColor = activeTileBg,
                            inactiveColor = inactiveTileBg,
                            thumbColor = activeTileContent,
                            iconStart = Icons.Default.VolumeUp,
                            iconEnd = Icons.Default.MusicNote,
                            activeContentColor = activeTileContent,
                            inactiveContentColor = inactiveTileContent
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(inactiveTileBg)
                                .clickable {
                                    onDismiss()
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent, launchOptions)
                                    } catch (e: Exception) {}
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Sound Settings",
                                tint = inactiveTileContent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (activeAppsList.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        ActiveAppsPill(
                            activeCount = activeAppsList.size,
                            contentColor = contentColor,
                            inactiveTileBg = inactiveTileBg,
                            onClick = { showActiveAppsDetail = true }
                        )
                    }

                    // Standalone smart media player carousel
                    SmartMediaPlayerCarousel(
                        displayId = displayId,
                        contentColor = contentColor,
                        activeTileBg = activeTileBg,
                        inactiveTileBg = inactiveTileBg
                    )

                    Spacer(Modifier.height(12.dp))

                    // Dynamic multi-page grid of activeTiles (5x4 limit)
                    val tileLayouts = remember(activeTiles) { ThemeManager.getTileLayouts(context).filter { it.key != "music" } }
                    val maxPage = remember(tileLayouts) { tileLayouts.maxOfOrNull { it.page } ?: 0 }
                    val pageCount = (maxPage + 1).coerceAtLeast(1)
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState { pageCount }
                    val scope = rememberCoroutineScope()
                    val gap = 8.dp
                    val calculatedCellWidth = (cardWidth - 40.dp - gap * 3) / 4
                    val cellHeight = (calculatedCellWidth * 0.65f).coerceIn(48.dp, 60.dp)

                    val pageHeights = remember(tileLayouts, pageCount) {
                        (0 until pageCount).map { pageIndex ->
                            val maxRow = tileLayouts.filter { it.page == pageIndex }.maxOfOrNull { it.row + it.h } ?: 1
                            maxRow.coerceIn(1, 5)
                        }
                    }

                    val currentOccupiedRows = pageHeights.getOrNull(pagerState.currentPage) ?: 1
                    val animatedGridRows by animateFloatAsState(
                        targetValue = currentOccupiedRows.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "GridHeightAnimation"
                    )
                    val totalHeight = cellHeight * animatedGridRows + gap * (animatedGridRows - 1f)

                    val isScrollEnabled = remember(context) { ThemeManager.isScrollPageSwitchingEnabled(context) }
                    val gridScrollModifier = if (isScrollEnabled && pageCount > 1) {
                        Modifier.pointerInput(pageCount) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Scroll) {
                                        val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        if (deltaY != 0f) {
                                            val targetPage = if (deltaY > 0f) {
                                                (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                            } else {
                                                (pagerState.currentPage - 1).coerceAtLeast(0)
                                            }
                                            if (targetPage != pagerState.currentPage) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(targetPage)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }

                    Column(modifier = Modifier.fillMaxWidth().then(gridScrollModifier)) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val cellWidth = (maxWidth - gap * 3) / 4

                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth().height(totalHeight)
                            ) { pageIndex ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    tileLayouts.filter { it.page == pageIndex }.forEach { layout ->
                                        val x = (cellWidth + gap) * layout.col
                                        val y = (cellHeight + gap) * layout.row
                                        val wPx = cellWidth * layout.w + gap * (layout.w - 1)
                                        val hPx = cellHeight * layout.h + gap * (layout.h - 1)

                                        Box(
                                            modifier = Modifier
                                                .offset(x = x, y = y)
                                                .size(width = wPx, height = hPx)
                                        ) {
                                            androidx.compose.runtime.CompositionLocalProvider(LocalTileSize provides Pair(layout.w, layout.h)) {
                                                if (layout.key == "music") {
                                                    MusicTileItem(
                                                        cardBg = cardBg,
                                                        contentColor = contentColor,
                                                        activeTileBg = activeTileBg,
                                                        inactiveTileBg = inactiveTileBg,
                                                        isDark = isDark,
                                                        w = layout.w,
                                                        h = layout.h
                                                    )
                                                } else {
                                                    RenderQSGridTile(
                                                        tileKey = layout.key,
                                                        isDark = isDark,
                                                        isWifiOn = isWifiOn,
                                                        wifiSsid = wifiSsid,
                                                        isBtOn = isBtOn,
                                                        btDeviceName = btDeviceName,
                                                        currentRingerMode = currentRingerMode,
                                                        audioManager = audioManager,
                                                        isFlashlightOn = isFlashlightOn,
                                                        toggleFlashlight = { toggleFlashlight() },
                                                        isAutoRotateOn = isAutoRotateOn,
                                                        toggleAutoRotate = { toggleAutoRotate() },
                                                        isRecording = isRecording,
                                                        recordingDuration = recordingDuration,
                                                        toggleScreenRecord = { toggleScreenRecord() },
                                                        isDndOn = isDndOn,
                                                        toggleDnd = { toggleDnd() },
                                                        isHotspotOn = isHotspotOn,
                                                        toggleHotspot = { toggleHotspot() },
                                                        isAirplaneModeOn = isAirplaneModeOn,
                                                        toggleAirplaneMode = { toggleAirplaneMode() },
                                                        isLocationOn = isLocationOn,
                                                        toggleLocation = { toggleLocation() },
                                                        isExtraDimOn = isExtraDimOn,
                                                        toggleExtraDim = { toggleExtraDim() },
                                                        isWirelessDebugOn = isWirelessDebugOn,
                                                        toggleWirelessDebug = { toggleWirelessDebugging() },
                                                        isNfcOn = isNfcOn,
                                                        toggleNfc = { toggleNfc() },
                                                        thirdPartyTiles = thirdPartyTiles,
                                                        onDismiss = onDismiss,
                                                        launchOptions = launchOptions,
                                                        onShowWifiListDetail = { showWifiListDetail = true },
                                                        onShowBtListDetail = { showBtListDetail = true },
                                                        displayId = displayId
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (pageCount > 1) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(pageCount) { index ->
                                    val isSelected = pagerState.currentPage == index
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(if (isSelected) 6.dp else 4.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) activeTileBg else contentColor.copy(alpha = 0.4f))
                                            .clickable {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // D. Bottom Controls Row (Battery Saver, Mic, Camera, Notifications, Theme toggle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Battery Saver Toggle
                            var isSaverOn by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val out = ShellExecutor.executeCommand("settings get global low_power").trim()
                                        isSaverOn = out == "1"
                                    } catch (e: Exception) {}
                                }
                            }
                            val saverShape = if (isSaverOn) RoundedCornerShape(14.dp) else CircleShape
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(saverShape)
                                    .background(if (isSaverOn) activeTileBg else inactiveTileBg)
                                    .clickable(enabled = !isCharging) {
                                        isSaverOn = !isSaverOn
                                        Thread {
                                            val valStr = if (isSaverOn) "1" else "0"
                                            ShellExecutor.executeCommand("settings put global low_power $valStr")
                                        }.start()
                                    }
                                    .graphicsLayer { alpha = if (isCharging) 0.4f else 1.0f },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatterySaver,
                                    contentDescription = "Battery Saver",
                                    tint = if (isSaverOn) activeTileContent else inactiveTileContent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Mic Access Toggle
                            var isMicMuted by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                isMicMuted = am?.isMicrophoneMute ?: false
                            }
                            val isMicActive = !isMicMuted
                            val micShape = if (isMicActive) RoundedCornerShape(14.dp) else CircleShape
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(micShape)
                                    .background(if (isMicActive) activeTileBg else inactiveTileBg)
                                    .clickable {
                                        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                        isMicMuted = !isMicMuted
                                        am?.isMicrophoneMute = isMicMuted
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Microphone",
                                    tint = if (isMicActive) activeTileContent else inactiveTileContent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Camera Access Toggle
                            var isCamActive by remember { mutableStateOf(true) }
                            val camShape = if (isCamActive) RoundedCornerShape(14.dp) else CircleShape
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(camShape)
                                    .background(if (isCamActive) activeTileBg else inactiveTileBg)
                                    .clickable {
                                        isCamActive = !isCamActive
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCamActive) Icons.Default.CameraAlt else Icons.Default.NoPhotography,
                                    contentDescription = "Camera Access",
                                    tint = if (isCamActive) activeTileContent else inactiveTileContent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Chat/Notification Bubble Button
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(inactiveTileBg)
                                    .clickable {
                                        onShowNotificationsDetailChange(true)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = "Notifications",
                                    tint = inactiveTileContent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Theme contrast circular toggle (Far Right) (behaves as system dark mode toggle)
                        val contrastShape = if (isDark) RoundedCornerShape(14.dp) else CircleShape
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(contrastShape)
                                .background(if (isDark) activeTileBg else inactiveTileBg)
                                .clickable {
                                    val newMode = if (isDark) 1 else 2
                                    Thread {
                                        val cmd = if (isDark) "cmd uimode night no" else "cmd uimode night yes"
                                        ShellExecutor.executeCommand(cmd)
                                    }.start()
                                    ThemeManager.setThemeMode(context, newMode)
                                    currentThemeMode = newMode
                                    try {
                                        FreeformOverlayService.requestRefresh()
                                    } catch (e: Exception) {}
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contrast,
                                contentDescription = "Contrast Toggle",
                                tint = if (isDark) activeTileContent else inactiveTileContent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    } // close mainPanelContent lambda

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaledDensity) {
        Box(
            modifier = modifier.onGloballyPositioned { rootCoordinates = it }
        ) {
            if (isLandscapeOrWide) {
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    libraryContent()
                    mainPanelContent()
                }
            } else {
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .heightIn(max = (screenHeightDp - statusBarHeight - 32).dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    mainPanelContent()
                    libraryContent()
                }
            }

            // Floating drag preview
            if (draggedLibraryTile != null) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (libraryDragStartOffset.x + libraryDragCurrentOffset.x).roundToInt(),
                                (libraryDragStartOffset.y + libraryDragCurrentOffset.y).roundToInt()
                            )
                        }
                ) {
                    Card(
                        modifier = Modifier
                            .size(width = 150.dp, height = 46.dp)
                            .shadow(12.dp, shape = RoundedCornerShape(23.dp)),
                        colors = CardDefaults.cardColors(containerColor = inactiveTileBg),
                        shape = RoundedCornerShape(23.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(activeTileBg.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val tileInfo = draggedLibraryTile!!
                                if (tileInfo.customIcon != null) {
                                    Image(
                                        bitmap = tileInfo.customIcon,
                                        contentDescription = tileInfo.label,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = tileInfo.icon ?: Icons.Default.Settings,
                                        contentDescription = tileInfo.label,
                                        tint = activeTileBg,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = draggedLibraryTile!!.label,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Drag to add",
                                    color = contentColor.copy(alpha = 0.6f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

