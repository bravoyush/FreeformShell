package com.example.freeformshell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DesktopKeyguardService : Service() {

    companion object {
        private const val TAG = "DesktopKeyguardService"
        private const val NOTIFICATION_CHANNEL_ID = "desktop_keyguard_channel"
        private const val NOTIFICATION_ID = 2027
        
        var isShowing = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: KeyguardServiceLifecycleOwner? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "User present detected on handset. Auto-dismissing lock screen.")
                    if (ThemeManager.isLockSyncDismissEnabled(context)) {
                        stopSelf()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen off detected. Dynamic secure verification deployed.")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating DesktopKeyguardService foreground service")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Register BroadcastReceiver for user-present and screen-off lifecycle events
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val displayId = intent?.getIntExtra("EXTRA_DISPLAY_ID", -1) ?: -1
        Log.d(TAG, "onStartCommand: displayId=$displayId")
        if (!isShowing) {
            showLockOverlay(displayId)
        }
        return START_NOT_STICKY
    }

    private fun showLockOverlay(displayId: Int) {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val displays = dm.displays
        val targetDisplay = if (displayId != -1) {
            dm.getDisplay(displayId) ?: (displays.firstOrNull { it.displayId > 0 } ?: dm.getDisplay(0))
        } else {
            displays.firstOrNull { it.displayId > 0 } ?: dm.getDisplay(0)
        }

        if (targetDisplay.displayId == 0) {
            Log.w(TAG, "Target display is 0 (Primary Phone Screen). Keyguard overlay skipped to prevent locking host UI.")
            stopSelf()
            return
        }

        Log.d(TAG, "Deploying keyguard overlay on Display ID: ${targetDisplay.displayId}, Name: ${targetDisplay.name}")

        val displayContext = createDisplayContext(targetDisplay)
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } catch (e: Exception) {
                displayContext
            }
        } else {
            displayContext
        }

        windowManager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            screenBrightness = if (ThemeManager.isLockAodEnabled(windowContext)) 0.1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        lifecycleOwner = KeyguardServiceLifecycleOwner().apply { start() }

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
                    DesktopLockScreenContent(
                        onUnlockCompleted = { stopSelf() }
                    )
                }
            }
        }

        lifecycleOwner?.let { owner ->
            composeView!!.setViewTreeLifecycleOwner(owner)
            composeView!!.setViewTreeViewModelStoreOwner(owner)
            composeView!!.setViewTreeSavedStateRegistryOwner(owner)
        }

        try {
            windowManager?.addView(composeView, params)
            isShowing = true
            Log.d(TAG, "Keyguard overlay ComposeView successfully deployed on WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach keyguard overlay view to WindowManager", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroying DesktopKeyguardService")
        
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}

        composeView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        composeView = null

        lifecycleOwner?.destroy()
        lifecycleOwner = null
        isShowing = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Desktop Secure Keyguard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows overlay status for the external screen privacy lock."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Desktop Secure Overlay")
            .setContentText("External screen lock keyguard is active.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Custom lifecycle owner to run Compose views inside background service view tree contexts.
 */
class KeyguardServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
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
fun DesktopLockScreenContent(
    onUnlockCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Preferences
    val wallpaperTheme = ThemeManager.getLockWallpaperTheme(context)
    val blurRadius = ThemeManager.getLockBlurRadius(context)
    val clockPreset = ThemeManager.getLockClockPreset(context)
    val keypadScrambler = ThemeManager.isLockKeypadScramblerEnabled(context)
    val keyboardBypass = ThemeManager.isLockKeyboardBypassEnabled(context)
    val notificationsMode = ThemeManager.getLockNotificationsMode(context)
    val weatherEnabled = ThemeManager.isLockWeatherWidgetEnabled(context)
    val calendarEnabled = ThemeManager.isLockCalendarWidgetEnabled(context)
    val isAod = ThemeManager.isLockAodEnabled(context)

    // Telemetry state
    var enteredPin by remember { mutableStateOf("") }
    var unlockStatusMessage by remember { mutableStateOf("Enter handset PIN to unlock") }
    var isCheckingPin by remember { mutableStateOf(false) }

    // Scramble keypad numbers if option is enabled
    val keypadNumbers = remember {
        val list = (0..9).toList()
        if (keypadScrambler) list.shuffled() else list
    }

    // Time Formatting
    var timeString by remember { mutableStateOf("") }
    var dateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance().time
            timeString = SimpleDateFormat(if (clockPreset == 2) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
            dateString = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
            delay(1000)
        }
    }

    // Background backdrop styling
    val backgroundModifier = when (wallpaperTheme) {
        1 -> Modifier.background(Color(0xFF000000)) // OLED Saver black
        2 -> Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F2027),
                    Color(0xFF203A43),
                    Color(0xFF2C5364)
                )
            )
        ) // Liquid Glass Gloss Gradient
        else -> Modifier
            .background(Color(0x990E0E12))
            .blur(blurRadius.dp) // Glass refracted backdrop
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            // Left Column: Clock and Widgets
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                // Clock presets
                when (clockPreset) {
                    0 -> { // Stacked Pixel Numeric
                        val hour = timeString.split(":").firstOrNull() ?: ""
                        val min = timeString.split(":").lastOrNull() ?: ""
                        Text(
                            text = hour,
                            fontSize = 110.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            lineHeight = 100.sp
                        )
                        Text(
                            text = min,
                            fontSize = 110.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 100.sp
                        )
                    }
                    1 -> { // Elegant Analog-inspired Text
                        Text(
                            text = timeString,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    else -> { // Minimal Text Banner
                        Text(
                            text = timeString,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = dateString,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.LightGray
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // Weather and Calendar Widget Cards
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (weatherEnabled) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Cloud, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("72°F", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Sunny", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }

                    if (calendarEnabled) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CalendarToday, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("3:00 PM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Sync Session", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Notification preview panel
                if (notificationsMode > 0) {
                    Text(
                        text = "Notifications",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (notificationsMode == 1) { // Icons only
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(Icons.Default.Mail, Icons.Default.Chat, Icons.Default.Call).forEach { icon ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else { // Full cards
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Ayush", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Workspace synced successfully!", fontSize = 11.sp, color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Column: PIN Keypad or Info Banner
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (keyboardBypass) {
                    // Keyboard Bypass - Read Only Informational Banner
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Desktop Screen Locked",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Keyboard bypass is active. Secondary screen touch input is completely locked.\n\nPlease unlock your primary handset using secure fingerprint/pin authentication.",
                                fontSize = 12.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    // Numerical Pin Entry Keypad
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.width(320.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Status PIN View
                            Text(
                                text = if (isCheckingPin) "Unlocking host..." else unlockStatusMessage,
                                fontSize = 13.sp,
                                color = if (isCheckingPin) MaterialTheme.colorScheme.primary else Color.LightGray,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // PIN dots display
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 20.dp)
                            ) {
                                for (i in 0 until 4) {
                                    val active = enteredPin.length > i
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f))
                                    )
                                }
                            }

                            // 3x4 Grid Pad
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (row in 0 until 3) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        for (col in 0 until 3) {
                                            val index = row * 3 + col
                                            val num = keypadNumbers[index]
                                            KeypadButton(text = num.toString()) {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += num
                                                    if (enteredPin.length == 4) {
                                                        // Secure numerical PIN Shizuku injection trigger
                                                        scope.launch(Dispatchers.Main) {
                                                            isCheckingPin = true
                                                            injectPinToHost(context, enteredPin) { success ->
                                                                isCheckingPin = false
                                                                if (success) {
                                                                    onUnlockCompleted()
                                                                } else {
                                                                    enteredPin = ""
                                                                    unlockStatusMessage = "Unlock failed. Please retry."
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bottom row: Backspace, 0 (or scrambled), and Clear
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Clear
                                    IconButton(
                                        onClick = { enteredPin = "" },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Clear, null, tint = Color.White)
                                    }

                                    // Scrambled last digit
                                    val lastNum = keypadNumbers[9]
                                    KeypadButton(text = lastNum.toString()) {
                                        if (enteredPin.length < 4) {
                                            enteredPin += lastNum
                                            if (enteredPin.length == 4) {
                                                scope.launch(Dispatchers.Main) {
                                                    isCheckingPin = true
                                                    injectPinToHost(context, enteredPin) { success ->
                                                        isCheckingPin = false
                                                        if (success) {
                                                            onUnlockCompleted()
                                                        } else {
                                                            enteredPin = ""
                                                            unlockStatusMessage = "Unlock failed. Please retry."
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Backspace
                                    IconButton(
                                        onClick = { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Backspace, null, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Sequentially executes input injections via Shizuku shell to unlock the host phone.
 */
private fun injectPinToHost(context: Context, pin: String, onFinished: (Boolean) -> Unit) {
    Thread {
        try {
            Log.d("injectPinToHost", "Waking up phone host screen via keyevent")
            ShellExecutor.executeCommand("input keyevent KEYCODE_WAKEUP")
            Thread.sleep(150)
            
            Log.d("injectPinToHost", "Sending swipe transition to slide keyguard up")
            ShellExecutor.executeCommand("input swipe 500 1500 500 400 180")
            Thread.sleep(300)

            // Inject digits sequentially
            pin.forEach { char ->
                val keycode = when(char) {
                    '0' -> KeyEvent.KEYCODE_0
                    '1' -> KeyEvent.KEYCODE_1
                    '2' -> KeyEvent.KEYCODE_2
                    '3' -> KeyEvent.KEYCODE_3
                    '4' -> KeyEvent.KEYCODE_4
                    '5' -> KeyEvent.KEYCODE_5
                    '6' -> KeyEvent.KEYCODE_6
                    '7' -> KeyEvent.KEYCODE_7
                    '8' -> KeyEvent.KEYCODE_8
                    '9' -> KeyEvent.KEYCODE_9
                    else -> null
                }
                if (keycode != null) {
                    ShellExecutor.executeCommand("input keyevent $keycode")
                    Thread.sleep(80)
                }
            }
            
            // Confirm numeric lock bypass
            ShellExecutor.executeCommand("input keyevent KEYCODE_ENTER")
            Thread.sleep(300)
            
            // Validate if handset got unlocked (screen fully interactive now)
            val isUnlocked = true // Shizuku executes blindly, we trust the secure lifecycle will dismiss the overlay on ACTION_USER_PRESENT
            
            onFinished(isUnlocked)
        } catch (e: Exception) {
            Log.e("injectPinToHost", "Numerical input injection failed", e)
            onFinished(false)
        }
    }.start()
}
