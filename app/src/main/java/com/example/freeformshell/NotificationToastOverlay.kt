package com.example.freeformshell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

class ToastLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
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

class NotificationToastOverlay(
    private val context: Context,
    val sbn: StatusBarNotification,
    private val displayId: Int
) {
    companion object {
        private const val TAG = "NotificationToastOverlay"
        
        // Maps displayId -> currently active toast overlay
        private val activeToasts = ConcurrentHashMap<Int, NotificationToastOverlay>()

        // Cache to prevent duplicate spam (Package + Title + Text)
        private val lastToastContent = ConcurrentHashMap<Int, String>()
        // Map to track pending show tasks for debouncing
        private val pendingToasts = ConcurrentHashMap<Int, Runnable>()

        fun showToast(context: Context, sbn: StatusBarNotification) {
            val handler = Handler(Looper.getMainLooper())
            
            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val contentFingerprint = "${sbn.packageName}|$title|$text"

            handler.post {
                try {
                    Log.d(TAG, "showToast: Received sbn.key=${sbn.key}")
                    if (!shouldShowToast(sbn)) return@post

                    val service = FreeformOverlayService.getInstance() ?: return@post
                    val displayIds = service.getEnabledDisplayIds()
                    
                    val cooldownEnabled = ThemeManager.isNotificationCooldownEnabled(service, sbn.packageName)

                    for (id in displayIds) {
                        if (cooldownEnabled) {
                            // 1. Content Hashing: Skip if identical to last shown on this display
                            if (lastToastContent[id] == contentFingerprint) {
                                Log.d(TAG, "showToast: Skipping duplicate content for display $id")
                                continue
                            }

                            // 2. Debouncing: Cancel any pending toast for this display
                            pendingToasts.remove(id)?.let { handler.removeCallbacks(it) }

                            val showRunnable = Runnable {
                                Log.d(TAG, "showToast: Executing delayed show for display $id")
                                lastToastContent[id] = contentFingerprint
                                activeToasts[id]?.dismiss()
                                
                                val toast = NotificationToastOverlay(service, sbn, id)
                                activeToasts[id] = toast
                                toast.show()
                                pendingToasts.remove(id)
                            }

                            // Use 500ms delay for debouncing to catch "seeking" bursts
                            pendingToasts[id] = showRunnable
                            handler.postDelayed(showRunnable, 500)
                        } else {
                            // Cooldown disabled: show immediately
                            Log.d(TAG, "showToast: Cooldown disabled for ${sbn.packageName}, showing immediately on $id")
                            activeToasts[id]?.dismiss()
                            val toast = NotificationToastOverlay(service, sbn, id)
                            activeToasts[id] = toast
                            toast.show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling notification toast debounce", e)
                }
            }
        }

        fun dismissToast(key: String) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                try {
                    Log.d(TAG, "dismissToast: Attempting to dismiss toast for key=$key")
                    activeToasts.forEach { (displayId, toast) ->
                        if (toast.sbn.key == key) {
                            Log.d(TAG, "dismissToast: Found matching toast on display $displayId, dismissing it")
                            toast.dismiss {
                                activeToasts.remove(displayId, toast)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error dismissing notification toast for key: $key", e)
                }
            }
        }

        fun dismissAll() {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                Log.d(TAG, "dismissAll: Dismissing all active toasts")
                activeToasts.forEach { (displayId, toast) ->
                    toast.dismiss {
                        activeToasts.remove(displayId, toast)
                    }
                }
            }
        }

        fun bringToFront(displayId: Int) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                activeToasts[displayId]?.bringToFront()
            }
        }

        private fun shouldShowToast(sbn: StatusBarNotification): Boolean {
            val notification = sbn.notification
            // Filter out group summary
            if ((notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) {
                Log.d(TAG, "shouldShowToast: False - Group summary flag set for sbn.key=${sbn.key}")
                return false
            }
            
            // Filter out ongoing / foreground service notifications
            val flags = notification.flags
            val isOngoing = (flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0 ||
                            (flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0
            if (isOngoing) {
                val isSyncApp = sbn.packageName == "com.microsoft.appmanager" || 
                                sbn.packageName == "org.kde.kdeconnect_tp"
                if (isSyncApp) {
                    val extras = notification.extras
                    val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
                    val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
                    
                    val isStatusNotif = title.contains("link to windows") || 
                                        title.contains("kde connect") ||
                                        text.contains("connected to") || 
                                        text.contains("synced with") ||
                                        text.contains("persistent service")
                    if (isStatusNotif) {
                        Log.d(TAG, "shouldShowToast: False - Ongoing status/persistent notification for sync app sbn.key=${sbn.key}")
                        return false
                    } else {
                        Log.d(TAG, "shouldShowToast: True - Allowing ongoing mirrored notification for sync app sbn.key=${sbn.key}")
                    }
                } else {
                    Log.d(TAG, "shouldShowToast: False - Ongoing/Foreground flag set (flags=$flags) for sbn.key=${sbn.key}")
                    return false
                }
            }
            
            // Filter out progress updates (only if max progress > 0 or it is indeterminate)
            val extras = notification.extras
            val progressMax = extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0)
            val isIndeterminate = extras.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            if (progressMax > 0 || isIndeterminate) {
                Log.d(TAG, "shouldShowToast: False - Progress update (max=$progressMax, ind=$isIndeterminate) for sbn.key=${sbn.key}")
                return false
            }
            
            // Check if it has a title or text
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            if (title.isEmpty() && text.isEmpty()) {
                Log.d(TAG, "shouldShowToast: False - Empty title and text for sbn.key=${sbn.key}")
                return false
            }
            
            Log.d(TAG, "shouldShowToast: True for sbn.key=${sbn.key}")
            return true
        }
    }

    private val displayContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = dm.getDisplay(displayId) 
            if (display != null) {
                context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } else {
                context
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create display/window context for displayId $displayId", e)
            context
        }
    } else {
        context
    }

    private val windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var lifecycleOwner: ToastLifecycleOwner? = null
    private var windowParams: WindowManager.LayoutParams? = null
    
    private val isVisibleState = mutableStateOf(false)
    private var isDismissing = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        val service = FreeformOverlayService.getInstance()
        if (service != null && !service.getDisplayShell(displayId).isEnabled()) {
            Log.d(TAG, "show: Display $displayId is not enabled according to DisplayShell. Skipping toast.")
            return
        }

        val density = displayContext.resources.displayMetrics.density
        val screenWidthPx = displayContext.resources.displayMetrics.widthPixels
        val windowWidthPx = minOf((370 * density).toInt(), screenWidthPx - (32 * density).toInt())

        var topPaddingPx = 0
        var bottomPaddingPx = 0
        var leftPaddingPx = 0
        var rightPaddingPx = 0

        // 1. Status bar padding
        val desktopThemeStyle = ThemeManager.getDesktopThemeStyle(displayContext)
        val showStatusBar = ThemeManager.showStatusBar(displayContext)
        if (desktopThemeStyle == 1 && showStatusBar) {
            topPaddingPx = (36 * density).toInt()
        } else if (displayId == 0) {
            val resourceId = displayContext.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                topPaddingPx = displayContext.resources.getDimensionPixelSize(resourceId)
            } else {
                topPaddingPx = (36 * density).toInt()
            }
        }

        // 2. Taskbar/Dock padding
        val dockPos = ThemeManager.getDockPosition(displayContext, displayId)
        val dockSize = ThemeManager.getDockSize(displayContext, displayId)
        when (dockPos) {
            1 -> topPaddingPx = maxOf(topPaddingPx, dockSize)
            2 -> bottomPaddingPx = maxOf(bottomPaddingPx, dockSize)
            3 -> leftPaddingPx = maxOf(leftPaddingPx, dockSize)
            4 -> rightPaddingPx = maxOf(rightPaddingPx, dockSize)
        }

        val position = ThemeManager.getWindowsNotifsPosition(displayContext)
        val marginPx = (16 * density).toInt()

        val params = WindowManager.LayoutParams(
            windowWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (position) {
                0 -> Gravity.BOTTOM or Gravity.END      // Bottom-Right
                1 -> Gravity.BOTTOM or Gravity.START    // Bottom-Left
                2 -> Gravity.TOP or Gravity.END         // Top-Right
                3 -> Gravity.TOP or Gravity.START       // Top-Left
                4 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL // Top-Center
                5 -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL // Bottom-Center
                else -> Gravity.BOTTOM or Gravity.END
            }
            x = when (position) {
                0 -> marginPx + rightPaddingPx
                1 -> marginPx + leftPaddingPx
                2 -> marginPx + rightPaddingPx
                3 -> marginPx + leftPaddingPx
                else -> 0
            }
            y = when (position) {
                0, 1 -> marginPx + bottomPaddingPx
                2, 3 -> marginPx + topPaddingPx
                4 -> marginPx + topPaddingPx
                5 -> marginPx + bottomPaddingPx
                else -> marginPx
            }
            if (displayId != 0) {
                FreeformOverlayService.primaryDisplayToken?.let {
                    this.token = it
                    Log.d(TAG, "show: Assigned primaryDisplayToken to secondary display toast params: $it")
                }
            }
        }

        windowParams = params
        lifecycleOwner = ToastLifecycleOwner().apply { start() }
        
        composeView = ComposeView(displayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            isFocusable = true
            isFocusableInTouchMode = true
            setContent {
                val colorScheme = getDynamicColorScheme(displayContext)

                MaterialTheme(
                    colorScheme = colorScheme
                ) {
                    val visible by remember { isVisibleState }
                    val enterAnim = when (position) {
                        0, 2 -> slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()
                        1, 3 -> slideInHorizontally(initialOffsetX = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()
                        4 -> slideInVertically(initialOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()
                        5 -> slideInVertically(initialOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()
                        else -> slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()
                    }
                    val exitAnim = when (position) {
                        0, 2 -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                        1, 3 -> slideOutHorizontally(targetOffsetX = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                        4 -> slideOutVertically(targetOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                        5 -> slideOutVertically(targetOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                        else -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = enterAnim,
                        exit = exitAnim
                    ) {
                        ToastCard(
                            sbn = sbn,
                            displayId = displayId,
                            onDismissRequested = { dismiss() },
                            onReplyingChanged = { setFocusable(it) }
                        )
                    }
                }
            }
        }

        lifecycleOwner?.let { owner ->
            composeView!!.setViewTreeLifecycleOwner(owner)
            composeView!!.setViewTreeViewModelStoreOwner(owner)
            composeView!!.setViewTreeSavedStateRegistryOwner(owner)
        }

        try {
            windowManager.addView(composeView, params)
            isVisibleState.value = true
            Log.d(TAG, "Notification toast overlay successfully added to WindowManager on display: $displayId")
            if (displayId == 0 && FreeformOverlayService.primaryDisplayToken == null) {
                composeView?.post {
                    val token = composeView?.windowToken
                    if (token != null && FreeformOverlayService.primaryDisplayToken == null) {
                        FreeformOverlayService.primaryDisplayToken = token
                        Log.d(TAG, "Harvested primaryDisplayToken from NotificationToastOverlay on display 0: $token")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add notification toast overlay to WindowManager", e)
        }
    }

    fun dismiss(onDone: (() -> Unit)? = null) {
        if (isDismissing) return
        isDismissing = true
        Handler(Looper.getMainLooper()).post {
            isVisibleState.value = false
            Handler(Looper.getMainLooper()).postDelayed({
                removeView()
                onDone?.invoke()
            }, 400) // Wait for exit animation to complete
        }
    }

    private fun removeView() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
        composeView = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        
        // Remove from static mapping if still exists
        if (activeToasts[displayId] == this) {
            activeToasts.remove(displayId, this)
        }
    }

    fun setFocusable(focusable: Boolean) {
        val view = composeView ?: return
        val params = windowParams ?: return
        val newParams = WindowManager.LayoutParams().apply { copyFrom(params) }
        
        if (focusable) {
            newParams.flags = newParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            newParams.flags = newParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            newParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            
            // Set token if secondary display
            if (displayId != 0 && newParams.token == null) {
                FreeformOverlayService.primaryDisplayToken?.let {
                    newParams.token = it
                    Log.d(TAG, "setFocusable: Dynamically assigned primaryDisplayToken on reply: $it")
                }
            }
        } else {
            newParams.flags = newParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            newParams.flags = newParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
            newParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
        
        if (params.flags != newParams.flags || params.token != newParams.token || params.softInputMode != newParams.softInputMode) {
            try {
                windowParams = newParams
                windowManager.updateViewLayout(view, newParams)
                Log.d(TAG, "setFocusable: Updated window layout with flags=${newParams.flags} focusable=$focusable")
                if (focusable) {
                    view.isFocusable = true
                    view.isFocusableInTouchMode = true
                    view.post {
                        val requested = view.requestFocus()
                        Log.d(TAG, "setFocusable: requestFocus returned $requested")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "setFocusable: Failed to update view layout flags", e)
            }
        }
    }

    fun bringToFront() {
        val view = composeView ?: return
        val params = windowParams ?: return
        try {
            windowManager.updateViewLayout(view, params)
            Log.d(TAG, "bringToFront: Re-applied layout for toast on display $displayId")
        } catch (e: Exception) {
            Log.e(TAG, "bringToFront: Failed to update view layout", e)
        }
    }
}

@Composable
fun ToastCard(
    sbn: StatusBarNotification,
    displayId: Int,
    onDismissRequested: () -> Unit,
    onReplyingChanged: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val extras = sbn.notification.extras
    val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
    val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
    val text = bigText?.takeIf { it.isNotBlank() }
        ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

    val pm = context.packageManager
    val appLabel = remember(sbn.packageName) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
    }

    // Resolving Theme Accent Color & Dark Mode status
    val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val isDarkTheme = ThemeManager.getThemeMode(context) == 2 || (ThemeManager.getThemeMode(context) == 0 && isSystemDark)
    val accentColor = MaterialTheme.colorScheme.primary

    // Timer and Hover/Reply logic
    var remainingTimeMs by remember { mutableStateOf(4000) }
    var isHovered by remember { mutableStateOf(false) }
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isReplying) {
        onReplyingChanged(isReplying)
        if (isReplying) {
            // Repeatedly request focus and show keyboard over the first 500ms
            // to ensure it binds once the WindowManager layout update completes.
            for (i in 1..3) {
                delay(150)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                    Log.d("ToastCard", "Requested focus and keyboard show, iteration $i")
                } catch (e: Exception) {
                    Log.e("ToastCard", "Failed to request focus in iteration $i", e)
                }
            }
        }
    }

    val isTimerActive = !isHovered && !isReplying
    LaunchedEffect(isTimerActive) {
        if (isTimerActive) {
            while (remainingTimeMs > 0) {
                delay(100)
                remainingTimeMs -= 100
            }
            onDismissRequested()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter) {
                            isHovered = true
                        } else if (event.type == PointerEventType.Exit) {
                            isHovered = false
                        }
                    }
                }
            }
            .clickable {
                try {
                    val launchOptions = android.app.ActivityOptions.makeBasic().apply {
                        launchDisplayId = displayId
                        try {
                            val method = this.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                            method.invoke(this, 5) // WINDOWING_MODE_FREEFORM
                        } catch (e: Exception) {}
                    }.toBundle()
                    
                    sbn.notification.contentIntent?.send(context, 0, null, null, null, null, launchOptions)
                    onDismissRequested()
                } catch (e: Exception) {
                    Log.e("ToastCard", "Failed to launch intent", e)
                }
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = if (isDarkTheme) {
                            listOf(
                                accentColor.copy(alpha = 0.08f),
                                Color(0xFF0C0C12).copy(alpha = 0.92f)
                            )
                        } else {
                            listOf(
                                accentColor.copy(alpha = 0.05f),
                                Color(0xFFF3F3F7).copy(alpha = 0.95f)
                            )
                        }
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Accented Circle Icon Badge Frame
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accentColor.copy(alpha = 0.18f), CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        NotificationIconResolver(
                            context = context,
                            sbn = sbn,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = appLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isDarkTheme) accentColor else accentColor.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDismissRequested,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title and Body Content
                Column(
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    if (title.isNotEmpty()) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Action Buttons or Direct Reply Input Field
                val actions = sbn.notification.actions
                if (isReplying) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Type a reply...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                              )
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          IconButton(
                              onClick = {
                                  if (replyText.isNotBlank()) {
                                      try {
                                          val action = actions?.firstOrNull { act -> 
                                              act.remoteInputs?.any { it.allowFreeFormInput } == true 
                                          }
                                          if (action != null) {
                                              val remoteInput = action.remoteInputs.first { it.allowFreeFormInput }
                                              val bundle = android.os.Bundle().apply {
                                                  putCharSequence(remoteInput.resultKey, replyText)
                                              }
                                              val intent = android.content.Intent().apply {
                                                  android.app.RemoteInput.addResultsToIntent(action.remoteInputs, this, bundle)
                                              }
                                              
                                              val launchOptions = android.app.ActivityOptions.makeBasic().apply {
                                                  launchDisplayId = displayId
                                                  try {
                                                      val method = this.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                                                      method.invoke(this, 5) // WINDOWING_MODE_FREEFORM
                                                  } catch (e: Exception) {}
                                              }.toBundle()
                                              
                                              action.actionIntent.send(context, 0, intent, null, null, null, launchOptions)
                                              
                                              replyText = ""
                                              isReplying = false
                                              onDismissRequested()
                                          }
                                      } catch (e: Exception) {
                                          Log.e("ToastCard", "Failed to send reply", e)
                                      }
                                  }
                              },
                              modifier = Modifier
                                  .size(32.dp)
                                  .background(accentColor, CircleShape)
                          ) {
                              Icon(
                                  imageVector = Icons.AutoMirrored.Filled.Send,
                                  contentDescription = "Send",
                                  tint = if ((0.299f * accentColor.red + 0.587f * accentColor.green + 0.114f * accentColor.blue) > 0.5f) Color(0xFF1C1B1F) else Color.White,
                                  modifier = Modifier.size(14.dp)
                              )
                          }
                      }
                  } else if (actions != null && actions.isNotEmpty()) {
                      Spacer(modifier = Modifier.height(12.dp))
                      Row(
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          modifier = Modifier.fillMaxWidth()
                      ) {
                          actions.take(2).forEach { action ->
                              val hasRemoteInput = action.remoteInputs?.any { it.allowFreeFormInput } == true
                              // Dynamic action text color based on luminance check
                              val isLightAccent = (0.299f * accentColor.red + 0.587f * accentColor.green + 0.114f * accentColor.blue) > 0.5f
                              val actionContentColor = if (isLightAccent) Color(0xFF1C1B1F) else Color.White
                              
                              Button(
                                  onClick = {
                                      if (hasRemoteInput) {
                                          isReplying = true
                                      } else {
                                          try {
                                              val launchOptions = android.app.ActivityOptions.makeBasic().apply {
                                                  launchDisplayId = displayId
                                                  try {
                                                      val method = this.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                                                      method.invoke(this, 5) // WINDOWING_MODE_FREEFORM
                                                  } catch (e: Exception) {}
                                              }.toBundle()

                                              action.actionIntent.send(context, 0, null, null, null, null, launchOptions)
                                              onDismissRequested()
                                          } catch (e: Exception) {
                                              Log.e("ToastCard", "Error performing action", e)
                                          }
                                      }
                                  },
                                  shape = RoundedCornerShape(50), // Pill button shape
                                  colors = ButtonDefaults.buttonColors(
                                      containerColor = accentColor,
                                      contentColor = actionContentColor
                                  ),
                                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                  modifier = Modifier.height(30.dp)
                              ) {
                                  Text(
                                      text = action.title.toString(),
                                      fontSize = 11.sp,
                                      fontWeight = FontWeight.Bold
                                  )
                              }
                          }
                      }
                  }
              }

              // Animated Countdown Progress Bar at the bottom
              val progress = (remainingTimeMs.toFloat() / 4000f).coerceIn(0f, 1f)
              LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier
                      .align(Alignment.BottomCenter)
                      .fillMaxWidth()
                      .height(2.dp),
                  color = accentColor,
                  trackColor = Color.Transparent
              )
          }
      }
}
