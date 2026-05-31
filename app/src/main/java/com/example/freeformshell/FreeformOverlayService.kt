package com.example.freeformshell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.core.app.NotificationCompat
import android.widget.TextView
import android.widget.LinearLayout
import android.view.animation.OvershootInterpolator
import java.util.HashMap

class FreeformOverlayService : Service() {

    private val displayShells = java.util.concurrent.ConcurrentHashMap<Int, DisplayShell>()

    fun getDisplayShell(displayId: Int): DisplayShell {
        return displayShells.computeIfAbsent(displayId) { DisplayShell(it) }
    }

    val overlays: Map<Int, DragResizeOverlay>
        get() {
            val all = mutableMapOf<Int, DragResizeOverlay>()
            displayShells.values.forEach { shell ->
                all.putAll(shell.overlays)
            }
            return all
        }
    private val bubbles = java.util.concurrent.ConcurrentHashMap<Int, MinimizedBubble>()
    private val minimizedTasks = java.util.concurrent.CopyOnWriteArraySet<Int>()
    private val minimizedTimestamps = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    @Volatile private var lastMinimizationTimestamp: Long = 0L
    internal val dockedTasks = java.util.concurrent.CopyOnWriteArraySet<Int>()
    internal val dockedPackages = java.util.concurrent.CopyOnWriteArraySet<String>()
    private val minimizedTaskData = java.util.concurrent.ConcurrentHashMap<Int, TaskBounds>()
    private val activeHandles = java.util.concurrent.ConcurrentHashMap<String, SplitResizeHandle>()
    @Volatile var sortedTaskIds: List<Int> = emptyList()
    private var lastSplitHandleStateHash: String = ""
    @Volatile private var isMonitoring = false
    private val refreshRunnable = Runnable { monitorTasks() }
    private val correctedTaskIds = java.util.concurrent.CopyOnWriteArraySet<Int>()
    // Cache of all freeform tasks we've ever seen — survives across monitor cycles
    internal val knownFreeformTasks = java.util.concurrent.ConcurrentHashMap<Int, Pair<String, String?>>() // taskId -> Pair(Pkg, Component)
    private val gracePeriodTasks = java.util.concurrent.ConcurrentHashMap<Int, Int>() // taskId -> missedCycles
    private val visibilityGracePeriodTasks = java.util.concurrent.ConcurrentHashMap<Int, Int>() // taskId -> missedVisibilityCycles
    private val missingTaskCycles = java.util.concurrent.ConcurrentHashMap<Int, Int>() // taskId -> missedCycles
    private val recentlyClosedTaskIds = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastTopTaskId = -1
    private var lastTopPackage: String? = null
    @Volatile var currentFocusedPackage: String = ""
    private var systemDefaultDensity = -1
    private val TAG = "FreeformOverlayService"
    private var recordControllerOverlay: ScreenRecordControllerOverlay? = null

    private val pendingDpiReversions = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private val dpiConfirmationContainers = java.util.concurrent.ConcurrentHashMap<Int, FrameLayout>()
    private val dpiConfirmationRunnables = java.util.concurrent.ConcurrentHashMap<Int, Runnable>()

    private val pendingResolutionReversions = java.util.concurrent.ConcurrentHashMap<Int, Pair<Int, Int>>()
    private val resolutionConfirmationContainers = java.util.concurrent.ConcurrentHashMap<Int, FrameLayout>()
    private val resolutionConfirmationRunnables = java.util.concurrent.ConcurrentHashMap<Int, Runnable>()

    private val pendingRefreshReversions = java.util.concurrent.ConcurrentHashMap<Int, Pair<Int, Int>>()
    private val refreshConfirmationContainers = java.util.concurrent.ConcurrentHashMap<Int, FrameLayout>()
    private val refreshConfirmationRunnables = java.util.concurrent.ConcurrentHashMap<Int, Runnable>()

    private val refreshRateOverlays = java.util.concurrent.ConcurrentHashMap<Int, android.view.View>()

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            monitorTasks()
            val delay = if (overlays.isNotEmpty()) 1000L else 3000L
            handler.postDelayed(this, delay)
        }
    }

    private var fastPollCount = 0
    private val fastPollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (fastPollCount < 10) {
                monitorTasks()
                fastPollCount++
                handler.postDelayed(this, 80)
            }
        }
    }

    fun triggerFastPollingSequence() {
        handler.removeCallbacks(fastPollRunnable)
        fastPollCount = 0
        handler.post(fastPollRunnable)
    }

    fun reloadActiveAppThemes() {
        handler.post {
            for (overlay in overlays.values) {
                overlay.reloadAppTheme()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun notifyHandleDragStateChanged(draggingHandle: SplitResizeHandle, dragging: Boolean) {
        activeHandles.values.forEach { handle ->
            if (handle != draggingHandle) {
                handle.isTemporarilyHidden = dragging
            }
        }
    }

    fun setSplitHandlesHidden(hidden: Boolean) {
        activeHandles.values.forEach { handle ->
            if (!handle.isDragging) {
                handle.isTemporarilyHidden = hidden
            }
        }
    }

    fun elevateOverlayToTop(taskId: Int) {
        handler.post {
            val target = overlays[taskId] ?: return@post
            target.bringToFront()
            for ((tid, overlay) in overlays) {
                overlay.updateFocus(tid == taskId, emptyList())
            }
            requestRefresh()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_SHOW_ALL" -> bringAllToFront()
            "ACTION_EXIT" -> stopSelf()
            "ACTION_START_CAPTURE_CONTROL" -> {
                val displayId = intent.getIntExtra("displayId", 0)
                showCaptureControlOverlay(displayId)
            }
            "ACTION_STOP_CAPTURE_CONTROL" -> {
                hideCaptureControlOverlay()
            }
            "ACTION_RELAUNCH_SINGLE" -> {
                val taskId = intent.getIntExtra("EXTRA_TASK_ID", -1)
                val component = intent.getStringExtra("EXTRA_COMPONENT")
                if (taskId != -1 && component != null) {
                    relaunchSingleTask(taskId, component)
                }
            }
            "ACTION_LAUNCH_WORKSPACE" -> {
                val groupJson = intent.getStringExtra("EXTRA_GROUP_JSON")
                val targetDisplay = intent.getIntExtra("EXTRA_TARGET_DISPLAY", -1)
                if (groupJson != null) {
                    launchWorkspaceFromJson(groupJson, targetDisplay)
                }
            }
            "ACTION_DPI_CONFIRMATION" -> {
                val displayId = intent.getIntExtra("displayId", -1)
                val targetDpi = intent.getIntExtra("targetDpi", -1)
                val originalDpi = intent.getIntExtra("originalDpi", -1)
                if (displayId != -1 && targetDpi != -1 && originalDpi != -1) {
                    showDpiConfirmationOverlay(displayId, targetDpi, originalDpi)
                }
            }
            "ACTION_RESOLUTION_CONFIRMATION" -> {
                val displayId = intent.getIntExtra("displayId", -1)
                val targetW = intent.getIntExtra("targetW", -1)
                val targetH = intent.getIntExtra("targetH", -1)
                val originalW = intent.getIntExtra("originalW", -1)
                val originalH = intent.getIntExtra("originalH", -1)
                if (displayId != -1 && targetW != -1 && targetH != -1 && originalW != -1 && originalH != -1) {
                    showResolutionConfirmationOverlay(displayId, targetW, targetH, originalW, originalH)
                }
            }
            "ACTION_REFRESH_RATE_CONFIRMATION" -> {
                val displayId = intent.getIntExtra("displayId", -1)
                val targetModeId = intent.getIntExtra("targetModeId", -1)
                val targetFps = intent.getIntExtra("targetFps", -1)
                val originalModeId = intent.getIntExtra("originalModeId", -1)
                val originalFps = intent.getIntExtra("originalFps", -1)
                if (displayId != -1 && targetModeId != -1 && targetFps != -1 && originalModeId != -1 && originalFps != -1) {
                    showRefreshRateConfirmationOverlay(displayId, targetModeId, targetFps, originalModeId, originalFps)
                }
            }
            else -> {
                isRunning = true
                handler.post(monitorRunnable)
                initRefreshRateOverlays()
            }
        }
        return START_STICKY
    }

    private fun showCaptureControlOverlay(displayId: Int) {
        handler.post {
            recordControllerOverlay?.hide()
            recordControllerOverlay = ScreenRecordControllerOverlay(this, displayId)
            recordControllerOverlay?.show()
        }
    }

    private fun hideCaptureControlOverlay() {
        handler.post {
            recordControllerOverlay?.hide()
            recordControllerOverlay = null
        }
    }

    private fun bringAllToFront() {
        handler.post { Toast.makeText(this, "Forcing rescue of freeform windows...", Toast.LENGTH_SHORT).show() }
        Thread {
            Log.d(TAG, "Bringing all known freeform tasks to front forcefully")
            val state = TaskManager.getCombinedTaskState()
            
            // Priority 1: Currently detected freeform tasks
            val currentFreeform = state.tasks.filter { it.isFreeform }
            currentFreeform.forEach { task ->
                if (task.activityName != null) {
                    ShellExecutor.relaunchFreeformTask(task.taskId, task.activityName)
                } else {
                    ShellExecutor.moveTaskToFront(task.taskId)
                }
                Thread.sleep(250) 
            }
            
            // Priority 2: Tasks in our cache that might be hidden
            knownFreeformTasks.forEach { (taskId, data) ->
                if (currentFreeform.none { it.taskId == taskId }) {
                    val component = data.second
                    if (component != null) {
                        ShellExecutor.relaunchFreeformTask(taskId, component)
                    } else {
                        ShellExecutor.moveTaskToFront(taskId)
                    }
                    Thread.sleep(250)
                }
            }
        }.start()
    }

    private fun relaunchSingleTask(taskId: Int, component: String) {
        handler.post { Toast.makeText(this, "Restoring window...", Toast.LENGTH_SHORT).show() }
        Thread {
            ShellExecutor.relaunchFreeformTask(taskId, component)
        }.start()
    }

    private fun launchWorkspaceFromJson(json: String, forceDisplayId: Int = -1) {
        try {
            val group = WorkspaceManager.jsonToGroup(org.json.JSONObject(json))
            val resolvedDisplayId = if (forceDisplayId != -1) forceDisplayId else group.displayId
            handler.post { Toast.makeText(this, "Restoring workspace...", Toast.LENGTH_SHORT).show() }
            Thread {
                ShellExecutor.executeCommand("cmd activity set-resizable 1")
                group.apps.forEachIndexed { index, app ->
                    val component = app.component ?: (packageManager.getLaunchIntentForPackage(app.packageName)?.component?.flattenToShortString())
                    if (component != null) {
                        // Always set intended bounds to restore exact size & bounds coordinates!
                        FreeformOverlayService.setIntendedBounds(app.packageName, app.bounds, resolvedDisplayId)
                        
                        // Automatically update dockedPackages based on isSnapped!
                        if (app.isSnapped) {
                            dockedPackages.add(app.packageName)
                        } else {
                            dockedPackages.remove(app.packageName)
                        }

                        ShellExecutor.relaunchFreeformTask(-1, component, resolvedDisplayId)
                        Thread.sleep(600)
                    }
                }
                // Force a monitor refresh immediately after launching all
                handler.post { monitorTasks() }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch workspace", e)
        }
    }

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post {
            overlays.values.forEach { it.updateColors() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Service Created")
        createNotificationChannel()
        startForeground(1, createNotification())
        
        getSharedPreferences("freeform_theme_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)

        val filter = android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        // Deploy keyguard lock screen overlays on startup if phone is already locked
        try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (km.isKeyguardLocked) {
                Log.d(TAG, "Handset is locked on service startup. Deploying keyguard overlays on external displays.")
                val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                dm.displays.forEach { display ->
                    if (display.displayId > 0) {
                        val keyguardIntent = android.content.Intent(this, DesktopKeyguardService::class.java).apply {
                            putExtra("EXTRA_DISPLAY_ID", display.displayId)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(keyguardIntent)
                        } else {
                            startService(keyguardIntent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check keyguard status on startup", e)
        }

        isRunning = true
        
        // Register JVM Shutdown Hook for recovery on sudden crash or VM exit
        Runtime.getRuntime().addShutdownHook(Thread {
            val pending = HashMap(pendingDpiReversions)
            for ((displayId, originalDpi) in pending) {
                try {
                    // Try direct command execution synchronously
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "wm density $originalDpi -d $displayId"))
                    process.waitFor()
                } catch (e: Exception) {
                    Log.e("FreeformOverlayService", "Shutdown hook failed to revert density for display $displayId", e)
                }
            }
        })

        // Synchronize animator scales on startup based on user preference
        Thread {
            try {
                val value = ThemeManager.instantResizeNoAnim(this@FreeformOverlayService)
                val scale = if (value) "0" else "1"
                ShellExecutor.executeCommand("settings put global window_animation_scale $scale")
                ShellExecutor.executeCommand("settings put global transition_animation_scale $scale")
                ShellExecutor.executeCommand("settings put global animator_duration_scale $scale")
            } catch (e: Exception) {}
        }.start()
        
        loadMinimizedTasksFromPrefs()
        if (CompatibilityManager.isHybridLeashMinimizationEnabled(this)) {
            Toast.makeText(
                this,
                "Notice: Click or interact with desktop background windows once to initialize their title bars.",
                Toast.LENGTH_LONG
            ).show()
        }
        handler.post(monitorRunnable)
    }

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            // Safety Valve: Stop monitoring or other lightweight cleanup
            lastTopPackage = null
            
            context?.let { ctx ->
                Log.d("FreeformOverlayService", "Handset screen off. Deploying lock screen overlays on secondary displays.")
                val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                dm.displays.forEach { display ->
                    if (display.displayId > 0) {
                        val keyguardIntent = android.content.Intent(ctx, DesktopKeyguardService::class.java).apply {
                            putExtra("EXTRA_DISPLAY_ID", display.displayId)
                        }
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ctx.startForegroundService(keyguardIntent)
                            } else {
                                ctx.startService(keyguardIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("FreeformOverlayService", "Failed to start DesktopKeyguardService on screen off", e)
                        }
                    }
                }
            }
        }
    }

    companion object {
        @Volatile var primaryDisplayToken: android.os.IBinder? = null
        private var instance: FreeformOverlayService? = null
        @JvmField var isSplitResizingActive = false

        fun getInstance(): FreeformOverlayService? = instance

        private val forceCloseList = java.util.concurrent.CopyOnWriteArraySet<String>()
        private var isForceCloseLoaded = false

        private fun ensureForceCloseLoaded(context: Context) {
            if (isForceCloseLoaded) return
            try {
                val prefs = context.applicationContext.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
                val saved = prefs.getStringSet("force_close_list", emptySet()) ?: emptySet()
                forceCloseList.clear()
                forceCloseList.addAll(saved.map { it.lowercase() })
                isForceCloseLoaded = true
                Log.d("FreeformOverlayService", "Force close list loaded: $forceCloseList")
            } catch (e: Exception) {
                Log.e("FreeformOverlayService", "Failed to load force close list", e)
            }
        }

        @JvmStatic
        fun toggleForceClose(context: Context, packageName: String) {
            ensureForceCloseLoaded(context)
            val lower = packageName.lowercase()
            if (forceCloseList.contains(lower)) {
                forceCloseList.remove(lower)
            } else {
                forceCloseList.add(lower)
            }
            try {
                val prefs = context.applicationContext.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("force_close_list", forceCloseList).apply()
                Log.d("FreeformOverlayService", "Force close list saved: $forceCloseList")
            } catch (e: Exception) {
                Log.e("FreeformOverlayService", "Failed to save force close list", e)
            }
        }

        @JvmStatic
        fun shouldForceClose(context: Context, packageName: String): Boolean {
            ensureForceCloseLoaded(context)
            val lower = packageName.lowercase()
            return forceCloseList.contains(lower)
        }

        fun requestRefresh() {
            instance?.let { inst ->
                inst.handler.removeCallbacks(inst.refreshRunnable)
                inst.handler.postDelayed(inst.refreshRunnable, 300)
            }
        }

        internal val intendedBounds = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Rect>()
        internal val intendedDisplayId = java.util.concurrent.ConcurrentHashMap<String, Int>()
        private val manualBlacklist = java.util.concurrent.CopyOnWriteArraySet<String>()
        private var isLoaded = false

        fun setIntendedBounds(packageName: String, rect: android.graphics.Rect, displayId: Int = 0) {
            intendedBounds[packageName] = rect
            intendedDisplayId[packageName] = displayId
        }

        private fun ensureLoaded(context: Context) {
            if (isLoaded) return
            try {
                val prefs = context.applicationContext.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
                val saved = prefs.getStringSet("manual_blacklist", emptySet()) ?: emptySet()
                manualBlacklist.clear()
                manualBlacklist.addAll(saved.map { it.lowercase() })
                isLoaded = true
                Log.d("FreeformOverlayService", "Manual blacklist loaded: $manualBlacklist")
            } catch (e: Exception) {
                Log.e("FreeformOverlayService", "Failed to load manual blacklist", e)
            }
        }

        fun toggleBlacklist(context: Context, packageName: String) {
            ensureLoaded(context)
            val lower = packageName.lowercase()
            if (manualBlacklist.contains(lower)) {
                manualBlacklist.remove(lower)
            } else {
                manualBlacklist.add(lower)
            }
            try {
                val prefs = context.applicationContext.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("manual_blacklist", manualBlacklist).apply()
                Log.d("FreeformOverlayService", "Manual blacklist saved: $manualBlacklist")
            } catch (e: Exception) {
                Log.e("FreeformOverlayService", "Failed to save manual blacklist", e)
            }
            
            // Trigger overlay check if running
            instance?.monitorTasks()
        }

        fun isBlacklisted(context: Context, packageName: String): Boolean {
            ensureLoaded(context)
            val lower = packageName.lowercase()
            return manualBlacklist.contains(lower) // Exact match for zero collateral damage!
        }

        fun showDockGuide(displayId: Int, pos: Int, size: Int) {
            instance?.updateDockGuide(displayId, pos, size)
        }

        fun hideDockGuide() {
            instance?.removeDockGuide()
        }

        fun showSensitivityGuide(displayId: Int, sizeDp: Int) {
            instance?.updateSensitivityGuide(displayId, sizeDp)
        }

        fun hideSensitivityGuide() {
            instance?.removeSensitivityGuide()
        }

        fun showSnapGuide(displayId: Int, rect: android.graphics.Rect) {
            instance?.updateSnapGuide(displayId, rect)
        }

        fun hideSnapGuide() {
            instance?.removeSnapGuide()
        }

        fun setTaskDocked(taskId: Int, docked: Boolean) {
            val inst = instance ?: return
            if (docked) {
                inst.dockedTasks.add(taskId)
                val pkg = inst.overlays[taskId]?.packageName ?: inst.knownFreeformTasks[taskId]?.first
                if (pkg != null) inst.dockedPackages.add(pkg)
            } else {
                inst.dockedTasks.remove(taskId)
                val pkg = inst.overlays[taskId]?.packageName ?: inst.knownFreeformTasks[taskId]?.first
                if (pkg != null) inst.dockedPackages.remove(pkg)
            }
        }

        fun getPairedTask(taskId: Int): DragResizeOverlay? {
            val inst = instance ?: return null
            val overlay = inst.overlays[taskId] ?: return null
            val displayId = overlay.currentDisplayId
            
            // Turn off automatic window snapping switching if there are more than 2 apps docked on this display
            val totalDockedOnDisplay = inst.overlays.values.count { it.currentDisplayId == displayId && it.isDocked }
            if (totalDockedOnDisplay > 2) return null
            
            val docked = inst.overlays.values.filter { it.currentDisplayId == displayId && it.isDocked && it.taskId != taskId }
            if (docked.isEmpty()) return null
            
            val safe = inst.getSafeAreaRect(displayId)
            val metrics = inst.getRealMetricsForDisplay(displayId)
            val density = metrics.density
            val threshold = (60 * density).toInt().coerceAtLeast(150)
            val contactThreshold = (32 * density).toInt()
            
            for (other in docked) {
                val leftTask = if (overlay.winL < other.winL) overlay else other
                val rightTask = if (overlay.winL < other.winL) other else overlay
                
                val isLeftDock = Math.abs(leftTask.winL - safe.left) < threshold
                val isRightDock = Math.abs((rightTask.winL + rightTask.winW) - safe.right) < threshold
                val horizontalGap = rightTask.winL - (leftTask.winL + leftTask.winW)
                
                if (isLeftDock && isRightDock && horizontalGap in -contactThreshold..contactThreshold) {
                    return other
                }
                
                val topTask = if (overlay.winT < other.winT) overlay else other
                val bottomTask = if (overlay.winT < other.winT) other else overlay
                
                val isTopDock = Math.abs(topTask.winT - safe.top) < threshold || Math.abs(topTask.winT - (safe.top + topTask.titleBarHeight)) < threshold
                val isBottomDock = Math.abs((bottomTask.winT + bottomTask.winH) - safe.bottom) < threshold
                val verticalGap = bottomTask.winT - (topTask.winT + topTask.winH)
                
                if (isTopDock && isBottomDock && verticalGap in -contactThreshold..contactThreshold) {
                    return other
                }
            }
            return null
        }
    }

    internal fun getSafeAreaRect(displayId: Int): android.graphics.Rect {
        val pos = ThemeManager.getDockPosition(this, displayId)
        val size = ThemeManager.getDockSize(this, displayId)
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics()
        targetDisplay.getRealMetrics(metrics)
        val rect = android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        
        // Add system status bar height for primary display if no custom top dock
        if (displayId == 0 && pos != 1) {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            var statusBarH = 0
            if (resourceId > 0) {
                statusBarH = resources.getDimensionPixelSize(resourceId)
            }
            if (statusBarH < (24 * resources.displayMetrics.density).toInt()) {
                statusBarH = (36 * resources.displayMetrics.density).toInt()
            }
            rect.top += statusBarH
        }

        when (pos) {
            1 -> rect.top += size
            2 -> rect.bottom -= size
            3 -> rect.left += size
            4 -> rect.right -= size
        }
        return rect
    }

    private fun getRealMetricsForDisplay(displayId: Int): android.util.DisplayMetrics {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics()
        targetDisplay?.getRealMetrics(metrics)
        return metrics
    }

    private val baseBlacklist = emptySet<String>()

    private fun monitorTasks() {
        if (isMonitoring) return
        if (overlays.values.any { it.isInteracting }) return
        isMonitoring = true
        Thread {
            try {
                val nowTime = System.currentTimeMillis()
                recentlyClosedTaskIds.entries.removeIf { nowTime - it.value > 3000 }

                val state = TaskManager.getCombinedTaskState()
                val tasks = state.tasks
                val taskBoundsMap = state.boundsMap
                val focusedPackage = TaskManager.getFocusedPackage()
                currentFocusedPackage = focusedPackage
                Log.d(TAG, "Active focusedPackage: '$focusedPackage'")
                val activeTaskIds = mutableSetOf<Int>()
                
                // Register any freeform tasks we find into the cache
                for ((taskId, info) in taskBoundsMap) {
                    if (recentlyClosedTaskIds.containsKey(taskId)) continue
                    if (info.windowingMode == 5 && info.packageName != null) {
                        knownFreeformTasks[taskId] = Pair(info.packageName, info.activityName)
                    }
                }
                
                // Build a merged task list: start with detected tasks, then add
                // any cached freeform tasks that weren't detected this cycle
                val filteredTasks = tasks.filter { !recentlyClosedTaskIds.containsKey(it.taskId) }
                val detectedTaskIds = filteredTasks.map { it.taskId }.toSet()

                // Intercept newly detected tasks that have pending matching bounds in intendedBounds and apply them instantly
                for (task in filteredTasks) {
                    val intended = intendedBounds[task.packageName]
                    if (intended != null) {
                        Log.d(TAG, "Intercepted task ${task.taskId} for ${task.packageName}. Restoring intended bounds: $intended")
                        ShellExecutor.resizeTask(task.taskId, intended.left, intended.top, intended.right, intended.bottom)
                        val info = taskBoundsMap[task.taskId]
                        if (info != null) {
                            info.bounds.set(intended)
                        }
                        intendedBounds.remove(task.packageName)
                    }
                }
                
                // Safety valve: Track missed cycles for cached tasks to clean up closed/killed apps
                for (cachedId in knownFreeformTasks.keys) {
                    if (recentlyClosedTaskIds.containsKey(cachedId)) {
                        knownFreeformTasks.remove(cachedId)
                        missingTaskCycles.remove(cachedId)
                        handler.post { hideOverlay(cachedId) }
                        continue
                    }
                    if (cachedId !in detectedTaskIds) {
                        val missed = missingTaskCycles.getOrDefault(cachedId, 0)
                        if (missed >= 1) { 
                            knownFreeformTasks.remove(cachedId)
                            missingTaskCycles.remove(cachedId)
                            handler.post { hideOverlay(cachedId) }
                        } else {
                            missingTaskCycles[cachedId] = missed + 1
                        }
                    } else {
                        missingTaskCycles[cachedId] = 0
                    }
                }

                val mergedTasks = filteredTasks.toMutableList()
                for ((cachedId, cachedData) in knownFreeformTasks) {
                    if (cachedId !in detectedTaskIds && !recentlyClosedTaskIds.containsKey(cachedId)) {
                        mergedTasks.add(AppTask(cachedId, cachedData.first, cachedData.second))
                    }
                }

                // Track top-most non-blacklisted task to sync Z-order and clipping
                val nonBlacklistedTasks = mergedTasks.filter { t ->
                    val low = t.packageName.lowercase()
                    !baseBlacklist.any { low.contains(it) } && !isBlacklisted(this@FreeformOverlayService, t.packageName)
                }
                sortedTaskIds = nonBlacklistedTasks.map { it.taskId }
                
                val topTask = nonBlacklistedTasks.firstOrNull()
                val currentTopTaskId = topTask?.taskId ?: -1
                
                val forceRelayer = currentTopTaskId != lastTopTaskId
                lastTopTaskId = currentTopTaskId

                val density = resources.displayMetrics.density
                val borderWidth = (4 * density).toInt()
                val titleBarHeight = (40 * density).toInt()

                // Group merged tasks by display
                val tasksByDisplay = mergedTasks.groupBy { task ->
                    taskBoundsMap[task.taskId]?.displayId ?: intendedDisplayId[task.packageName] ?: 0
                }

                // Explicit display transition interceptor to hard prune cross-display leaks
                for ((taskId, overlay) in overlays) {
                    val currentDisplayId = overlay.currentDisplayId
                    val targetDisplayId = taskBoundsMap[taskId]?.displayId ?: intendedDisplayId[overlay.packageName] ?: 0
                    if (currentDisplayId != targetDisplayId) {
                        Log.d(TAG, "Explicitly pruning stale cross-display overlay for task $taskId on display $currentDisplayId (transitioned to $targetDisplayId)")
                        handler.post {
                            getDisplayShell(currentDisplayId).hideOverlay(taskId)
                        }
                    }
                }

                val allActiveDisplayIds = tasksByDisplay.keys + displayShells.keys
                for (displayId in allActiveDisplayIds) {
                    val shell = getDisplayShell(displayId)
                    val displayTasks = tasksByDisplay[displayId] ?: emptyList()
                    
                    // FETCH PER-DISPLAY SCALE METRICS
                    val metrics = getRealMetricsForDisplay(displayId)
                    val dDensity = metrics.density
                    val dBorderWidth = (4 * dDensity).toInt()
                    val dTitleBarHeight = (40 * dDensity).toInt()

                    shell.update(
                        displayTasks = displayTasks,
                        taskBoundsMap = taskBoundsMap,
                        focusedPackage = focusedPackage,
                        currentTopTaskId = currentTopTaskId,
                        density = dDensity,
                        borderWidth = dBorderWidth,
                        titleBarHeight = dTitleBarHeight,
                        activeTaskIds = activeTaskIds,
                        mergedTasks = mergedTasks,
                        forceRelayer = forceRelayer
                    )
                }

                handler.post {
                    val toRemove = overlays.keys.filter { it !in activeTaskIds && it !in minimizedTasks }
                    toRemove.forEach { hideOverlay(it) }
                    
                    // Duplicate package overlay prevention across all shells
                    val activeOverlays = overlays.values.toList()
                    val groups = activeOverlays.groupBy { it.packageName }
                    groups.forEach { (pkg, list) ->
                        if (list.size > 1) {
                            val sortedByActivity = list.sortedBy { overlay ->
                                val index = mergedTasks.indexOfFirst { it.taskId == overlay.taskId }
                                if (index != -1) index else Int.MAX_VALUE
                            }
                            val keep = sortedByActivity.first()
                            sortedByActivity.drop(1).forEach { stale ->
                                Log.d(TAG, "Removing duplicate overlay for package $pkg (stale taskId ${stale.taskId})")
                                hideOverlay(stale.taskId)
                            }
                        }
                    }
                    
                    // Clean up bubbles for tasks that no longer exist in recents
                    val deadBubbles = bubbles.keys.filter { id -> mergedTasks.none { it.taskId == id } }
                    if (deadBubbles.isNotEmpty()) {
                        deadBubbles.forEach { id ->
                            bubbles[id]?.hide()
                            bubbles.remove(id)
                            minimizedTasks.remove(id)
                            minimizedTimestamps.remove(id)
                        }
                        saveMinimizedTasksToPrefs()
                    }
                    ShellExecutor.notifyOverlayCountChanged(overlays.size)

                    // Save current layout to history by display if anything is open
                    val freeformTasks = mergedTasks.filter { it.isFreeform }
                    if (freeformTasks.isNotEmpty()) {
                        val tByDisplay = freeformTasks.groupBy { task ->
                            taskBoundsMap[task.taskId]?.displayId ?: 0
                        }
                        tByDisplay.forEach { (displayId, displayTasks) ->
                            WorkspaceManager.saveCurrentToHistory(this@FreeformOverlayService, displayId, displayTasks, taskBoundsMap)
                        }
                    }
                    
                    updateSplitHandles()

                    // Automatically broadcast refresh to all widgets when monitor cycle completes!
                    try {
                        val tIntent = android.content.Intent(this@FreeformOverlayService, FreeformTaskManagerWidget::class.java).apply {
                            action = "ACTION_REFRESH"
                        }
                        sendBroadcast(tIntent)
                        
                        val wIntent = android.content.Intent(this@FreeformOverlayService, FreeformWidget::class.java).apply {
                            action = "ACTION_REFRESH"
                        }
                        sendBroadcast(wIntent)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to broadcast widget refresh", ex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Monitor loop error", e)
            } finally {
                isMonitoring = false
            }
        }.start()
    }

    private fun saveMinimizedTasksToPrefs() {
        try {
            val prefs = getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
            val idStrings = minimizedTasks.map { it.toString() }.toSet()
            prefs.edit().putStringSet("persisted_minimized_tasks", idStrings).apply()
            
            val editor = prefs.edit()
            for (taskId in minimizedTasks) {
                val data = minimizedTaskData[taskId]
                if (data != null) {
                    val rectStr = "${data.bounds.left},${data.bounds.top},${data.bounds.right},${data.bounds.bottom}"
                    editor.putString("minimized_data_bounds_$taskId", rectStr)
                    editor.putInt("minimized_data_display_$taskId", data.displayId)
                    editor.putInt("minimized_data_winmode_$taskId", data.windowingMode)
                    editor.putString("minimized_data_pkg_$taskId", data.packageName ?: "")
                    editor.putString("minimized_data_act_$taskId", data.activityName ?: "")
                }
            }
            editor.apply()
            Log.d(TAG, "Successfully saved ${minimizedTasks.size} minimized tasks to preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving minimized tasks to preferences", e)
        }
    }

    private fun loadMinimizedTasksFromPrefs() {
        try {
            val prefs = getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
            val idStrings = prefs.getStringSet("persisted_minimized_tasks", emptySet()) ?: emptySet()
            minimizedTasks.clear()
            minimizedTaskData.clear()
            
            for (idStr in idStrings) {
                val taskId = idStr.toIntOrNull() ?: continue
                minimizedTasks.add(taskId)
                minimizedTimestamps[taskId] = System.currentTimeMillis() // Cooldown protection active on start
                
                val rectStr = prefs.getString("minimized_data_bounds_$taskId", null)
                if (rectStr != null) {
                    val parts = rectStr.split(",")
                    if (parts.size == 4) {
                        val left = parts[0].toIntOrNull() ?: 0
                        val top = parts[1].toIntOrNull() ?: 0
                        val right = parts[2].toIntOrNull() ?: 0
                        val bottom = parts[3].toIntOrNull() ?: 0
                        
                        val displayId = prefs.getInt("minimized_data_display_$taskId", 0)
                        val windowingMode = prefs.getInt("minimized_data_winmode_$taskId", 5)
                        val pkg = prefs.getString("minimized_data_pkg_$taskId", null)
                        val act = prefs.getString("minimized_data_act_$taskId", null)
                        
                        minimizedTaskData[taskId] = TaskBounds(
                            bounds = android.graphics.Rect(left, top, right, bottom),
                            displayId = displayId,
                            windowingMode = windowingMode,
                            packageName = pkg,
                            activityName = act,
                            isVisible = false
                        )
                    }
                }
            }
            Log.d(TAG, "Successfully loaded ${minimizedTasks.size} minimized tasks from preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading minimized tasks from preferences", e)
        }
    }

    private fun minimizeTask(taskId: Int) {
        Log.d(TAG, "minimizeTask called for taskId: $taskId")
        val task = TaskManager.getAllTaskBounds()[taskId]
        val pkg = task?.packageName ?: overlays[taskId]?.packageName ?: knownFreeformTasks[taskId]?.first
        val activity = task?.activityName ?: overlays[taskId]?.packageName?.let { knownFreeformTasks[taskId]?.second }

        Log.d(TAG, "minimizeTask resolved pkg: $pkg, activity: $activity")

        // Store whatever data we have in minimizedTaskData
        if (task != null) {
            minimizedTaskData[taskId] = task
        } else if (pkg != null) {
            minimizedTaskData[taskId] = TaskBounds(
                bounds = overlays[taskId]?.let { android.graphics.Rect(it.winL, it.winT, it.winL + it.winW, it.winT + it.winH) } ?: android.graphics.Rect(100, 100, 800, 800),
                displayId = overlays[taskId]?.currentDisplayId ?: 0,
                windowingMode = 5,
                packageName = pkg,
                activityName = activity,
                isVisible = false
            )
        }

        minimizedTasks.add(taskId)
        minimizedTimestamps[taskId] = System.currentTimeMillis()
        lastMinimizationTimestamp = System.currentTimeMillis()
        
        val isLeashMinimizationEnabled = CompatibilityManager.isHybridLeashMinimizationEnabled(this)
        val surfaceMinimized = if (isLeashMinimizationEnabled) {
            TaskManager.minimizeTaskSurface(taskId)
        } else {
            false
        }
        
        if (!surfaceMinimized) {
            Log.w(TAG, "Leash minimization not enabled or failed, falling back to moveTaskToBack")
            ShellExecutor.moveTaskToBack(taskId)
        } else {
            // Concurrently invoke OS-level moveTaskToBack asynchronously to handle focus yield and Recents stack integration
            ShellExecutor.moveTaskToBack(taskId)
        }

        val prefs = getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
        val useBubbles = prefs.getBoolean("bubble_mode", false)

        if (useBubbles && pkg != null) {
            val displayId = task?.displayId ?: overlays[taskId]?.currentDisplayId ?: 0
            handler.post {
                if (!bubbles.containsKey(taskId)) {
                    val bubble = MinimizedBubble(this, taskId, pkg, displayId) {
                        restoreTask(it)
                    }
                    bubbles[taskId] = bubble
                    bubble.show()
                }
                hideOverlay(taskId)
            }
        } else {
            handler.post {
                hideOverlay(taskId)
            }
        }
        
        saveMinimizedTasksToPrefs()
        handler.postDelayed({ monitorTasks() }, 200)
    }

    private fun restoreTask(taskId: Int) {
        Log.d(TAG, "restoreTask called for taskId: $taskId")
        val data = minimizedTaskData[taskId]
        val activity = data?.activityName ?: knownFreeformTasks[taskId]?.second
        val bounds = data?.bounds

        // 1. Instantly restore leash position and visibility in screen space
        if (CompatibilityManager.isHybridLeashMinimizationEnabled(this)) {
            TaskManager.restoreTaskSurface(taskId)
        }

        // 2. Instant high-performance reflective restoration:
        Thread {
            ShellExecutor.setTaskWindowingMode(taskId, 5, true) // 5 = WINDOWING_MODE_FREEFORM, true = bring to front
            
            if (bounds != null) {
                // Restore bounds with a tiny delay to ensure windowing mode change has completed
                try {
                    Thread.sleep(150)
                } catch (e: Exception) {}
                ShellExecutor.resizeTask(taskId, bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
        }.start()

        // Fallback: If reflection is blocked or failed, also trigger intent-based relaunch if activity name is available
        if (activity != null) {
            handler.postDelayed({
                // Only run fallback if the task is still not the top task or has not been restored
                val currentTop = sortedTaskIds.firstOrNull()
                if (currentTop != taskId) {
                    Log.d(TAG, "Running restoreTask activity intent fallback for $activity")
                    Thread {
                        ShellExecutor.executeCommand("am start -n $activity --windowingMode 5 --activity-reorder-to-front")
                    }.start()
                }
            }, 400)
        }
        
        minimizedTasks.remove(taskId)
        minimizedTimestamps.remove(taskId)
        saveMinimizedTasksToPrefs()
        dockedTasks.remove(taskId)
        minimizedTaskData.remove(taskId)
        handler.post {
            bubbles[taskId]?.hide()
            bubbles.remove(taskId)
        }
        monitorTasks()
    }

    private fun hideOverlay(taskId: Int) {
        recentlyClosedTaskIds[taskId] = System.currentTimeMillis()
        knownFreeformTasks.remove(taskId)
        correctedTaskIds.remove(taskId)
        visibilityGracePeriodTasks.remove(taskId)
        displayShells.values.forEach { shell ->
            if (shell.overlays.containsKey(taskId)) {
                shell.hideOverlay(taskId)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        isRunning = false
        instance = null
        handler.removeCallbacks(monitorRunnable)
        
        recordControllerOverlay?.hide()
        recordControllerOverlay = null
        
        getSharedPreferences("freeform_theme_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        
        try { unregisterReceiver(screenOffReceiver) } catch (e: Exception) {}
            
        // Revert any pending unconfirmed DPIs synchronously on service destruction
        val pending = HashMap(pendingDpiReversions)
        for ((displayId, originalDpi) in pending) {
            try {
                ShellExecutor.executeCommand("wm density $originalDpi -d $displayId")
                ThemeManager.setDensity(this, displayId, originalDpi)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore density in onDestroy for display $displayId", e)
            }
            removeDpiConfirmationOverlay(displayId)
        }
        pendingDpiReversions.clear()

        // Revert any pending unconfirmed resolutions synchronously on service destruction
        val pendingRes = HashMap(pendingResolutionReversions)
        for ((displayId, originalSize) in pendingRes) {
            try {
                ShellExecutor.executeCommand("wm size ${originalSize.first}x${originalSize.second} -d $displayId")
                ThemeManager.setWidth(this, displayId, originalSize.first)
                ThemeManager.setHeight(this, displayId, originalSize.second)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore resolution in onDestroy for display $displayId", e)
            }
            removeResolutionConfirmationOverlay(displayId)
        }
        pendingResolutionReversions.clear()

        // Revert any pending unconfirmed refresh rates synchronously on service destruction
        val pendingRefresh = HashMap(pendingRefreshReversions)
        for ((displayId, originalRate) in pendingRefresh) {
            try {
                val originalModeId = originalRate.first
                val originalFps = originalRate.second
                ThemeManager.setRefreshRate(this, displayId, originalFps)
                ThemeManager.setRefreshRateMode(this, displayId, originalModeId)
                if (originalModeId > 0) {
                    applyPersistentRefreshRate(displayId, originalModeId)
                } else {
                    removePersistentRefreshRate(displayId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore refresh rate in onDestroy for display $displayId", e)
            }
            removeRefreshConfirmationOverlay(displayId)
        }
        pendingRefreshReversions.clear()

        // Remove all persistent transparent refresh rate overlays on service destruction
        val activeOverlays = ArrayList(refreshRateOverlays.keys)
        for (displayId in activeOverlays) {
            removePersistentRefreshRate(displayId)
        }
        refreshRateOverlays.clear()

        displayShells.values.forEach { it.clear() }
        displayShells.clear()
        ShellExecutor.notifyOverlayCountChanged(0)
        activeHandles.values.forEach { it.hide() }
        activeHandles.clear()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "freeform_overlay_channel",
                "Freeform Window Controller",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val showAllIntent = Intent(this, FreeformOverlayService::class.java).apply { action = "ACTION_SHOW_ALL" }
        val showAllPending = PendingIntent.getService(this, 1, showAllIntent, PendingIntent.FLAG_IMMUTABLE)

        val exitIntent = Intent(this, FreeformOverlayService::class.java).apply { action = "ACTION_EXIT" }
        val exitPending = PendingIntent.getService(this, 2, exitIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "freeform_overlay_channel")
            .setContentTitle("Freeform Beta Active")
            .setContentText("Tap 'Force Rescue' if windows are hidden")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_view, "Force Rescue", showAllPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", exitPending)
            .setOngoing(true)
            .build()
    }

    private fun updateDockGuide(displayId: Int, pos: Int, size: Int) {
        getDisplayShell(displayId).updateDockGuide(pos, size)
    }

    private fun removeDockGuide() {
        displayShells.values.forEach { it.removeDockGuide() }
    }

    private fun updateSensitivityGuide(displayId: Int, sizeDp: Int) {
        getDisplayShell(displayId).updateSensitivityGuide(sizeDp)
    }

    private fun removeSensitivityGuide() {
        displayShells.values.forEach { it.removeSensitivityGuide() }
    }

    private fun updateSnapGuide(displayId: Int, rect: android.graphics.Rect) {
        getDisplayShell(displayId).updateSnapGuide(rect)
    }

    private fun removeSnapGuide() {
        displayShells.values.forEach { it.removeSnapGuide() }
    }

    private fun getDisplayContext(displayId: Int): Context {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
        val dContext = createDisplayContext(targetDisplay)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { 
                dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) 
            } catch (e: Exception) { 
                Log.w(TAG, "createWindowContext failed for display $displayId, falling back to DisplayContext with harvested token mapping", e)
                dContext 
            }
        } else {
            dContext
        }
    }

    private fun removeDpiConfirmationOverlay(displayId: Int) {
        dpiConfirmationRunnables.remove(displayId)?.let { handler.removeCallbacks(it) }
        dpiConfirmationContainers.remove(displayId)?.let { container ->
            try {
                val displayContext = getDisplayContext(displayId)
                val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(container)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove DPI confirmation overlay", e)
            }
        }
    }

    private fun revertDpi(displayId: Int, originalDpi: Int) {
        removeDpiConfirmationOverlay(displayId)
        pendingDpiReversions.remove(displayId)
        Thread {
            ShellExecutor.executeCommand("wm density $originalDpi -d $displayId")
            ThemeManager.setDensity(this, displayId, originalDpi)
        }.start()
    }

    private fun showDpiConfirmationOverlay(displayId: Int, targetDpi: Int, originalDpi: Int) {
        // Cancel any existing overlays/timers for this display
        removeDpiConfirmationOverlay(displayId)

        // Store the reversion mapping for safety
        pendingDpiReversions[displayId] = originalDpi

        // Trigger target density change immediately via shell execution
        Thread {
            ShellExecutor.executeCommand("wm density $targetDpi -d $displayId")
            ThemeManager.setDensity(this, displayId, targetDpi)
        }.start()

        handler.post {
            val context = getDisplayContext(displayId)
            val density = context.resources.displayMetrics.density
            fun dp(value: Float): Int = (value * density).toInt()

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val container = FrameLayout(context)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                dimAmount = 0.5f
                gravity = Gravity.CENTER
            }

            val card = FrameLayout(context).apply {
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#E6121212")) // 90% black transparency
                    cornerRadius = dp(20f).toFloat()
                    setStroke(dp(1.5f), Color.argb(46, 255, 255, 255)) // 18% opacity white border
                }
                background = bg
                val cardPadding = dp(24f)
                setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            }

            val cardParams = FrameLayout.LayoutParams(
                dp(320f),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            container.addView(card, cardParams)

            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            card.addView(contentLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))

            val titleText = TextView(context).apply {
                text = "Confirm Display Settings"
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            contentLayout.addView(titleText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16f)
            })

            val messageText = TextView(context).apply {
                text = "Do you want to keep this display density?\nReverting in 15 seconds."
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 14f
                gravity = Gravity.CENTER
            }
            contentLayout.addView(messageText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24f)
            })

            val buttonsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }
            contentLayout.addView(buttonsRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            val revertButton = TextView(context).apply {
                text = "Revert"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                
                val btnBg = GradientDrawable().apply {
                    setColor(Color.argb(38, 255, 255, 255)) // 15% white
                    cornerRadius = dp(20f).toFloat()
                }
                background = btnBg
                isClickable = true
                isFocusable = true
                
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            btnBg.setColor(Color.argb(76, 255, 255, 255))
                            v.background = btnBg
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            btnBg.setColor(Color.argb(38, 255, 255, 255))
                            v.background = btnBg
                        }
                    }
                    false
                }
                
                setOnClickListener {
                    revertDpi(displayId, originalDpi)
                }
            }

            val keepButton = TextView(context).apply {
                text = "Keep"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A73E8")) // Google Blue
                    cornerRadius = dp(20f).toFloat()
                }
                background = btnBg
                isClickable = true
                isFocusable = true
                
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            btnBg.setColor(Color.parseColor("#1557B0"))
                            v.background = btnBg
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            btnBg.setColor(Color.parseColor("#1A73E8"))
                            v.background = btnBg
                        }
                    }
                    false
                }
                
                setOnClickListener {
                    pendingDpiReversions.remove(displayId)
                    removeDpiConfirmationOverlay(displayId)
                }
            }

            revertButton.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            keepButton.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))

            val revertParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                rightMargin = dp(6f)
            }
            
            val keepParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                leftMargin = dp(6f)
            }
            
            buttonsRow.addView(revertButton, revertParams)
            buttonsRow.addView(keepButton, keepParams)

            // Premium Scale-in and alpha enter animation
            card.alpha = 0f
            card.scaleX = 0.8f
            card.scaleY = 0.8f
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

            // 15-second countdown timer
            var remainingSeconds = 15
            val countdownRunnable = object : Runnable {
                override fun run() {
                    remainingSeconds--
                    if (remainingSeconds <= 0) {
                        revertDpi(displayId, originalDpi)
                    } else {
                        messageText.text = "Do you want to keep this display density?\nReverting in $remainingSeconds seconds."
                        handler.postDelayed(this, 1000)
                    }
                }
            }

            dpiConfirmationRunnables[displayId] = countdownRunnable
            dpiConfirmationContainers[displayId] = container

            try {
                wm.addView(container, params)
                handler.postDelayed(countdownRunnable, 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add DPI confirmation overlay to WindowManager", e)
                pendingDpiReversions.remove(displayId)
                dpiConfirmationRunnables.remove(displayId)
            }
        }
    }

    private fun removeResolutionConfirmationOverlay(displayId: Int) {
        resolutionConfirmationRunnables.remove(displayId)?.let { handler.removeCallbacks(it) }
        resolutionConfirmationContainers.remove(displayId)?.let { container ->
            try {
                val displayContext = getDisplayContext(displayId)
                val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(container)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove Resolution confirmation overlay", e)
            }
        }
    }

    private fun revertResolution(displayId: Int, originalW: Int, originalH: Int) {
        removeResolutionConfirmationOverlay(displayId)
        pendingResolutionReversions.remove(displayId)
        Thread {
            ShellExecutor.executeCommand("wm size ${originalW}x${originalH} -d $displayId")
            ThemeManager.setWidth(this, displayId, originalW)
            ThemeManager.setHeight(this, displayId, originalH)
        }.start()
    }

    private fun showResolutionConfirmationOverlay(displayId: Int, targetW: Int, targetH: Int, originalW: Int, originalH: Int) {
        removeResolutionConfirmationOverlay(displayId)
        pendingResolutionReversions[displayId] = Pair(originalW, originalH)

        Thread {
            ShellExecutor.executeCommand("wm size ${targetW}x${targetH} -d $displayId")
            ThemeManager.setWidth(this, displayId, targetW)
            ThemeManager.setHeight(this, displayId, targetH)
        }.start()

        handler.post {
            val context = getDisplayContext(displayId)
            val density = context.resources.displayMetrics.density
            fun dp(value: Float): Int = (value * density).toInt()

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val container = FrameLayout(context)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                dimAmount = 0.5f
                gravity = Gravity.CENTER
            }

            val card = FrameLayout(context).apply {
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#E6121212"))
                    cornerRadius = dp(20f).toFloat()
                    setStroke(dp(1.5f), Color.argb(46, 255, 255, 255))
                }
                background = bg
                val cardPadding = dp(24f)
                setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            }

            val cardParams = FrameLayout.LayoutParams(
                dp(320f),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            container.addView(card, cardParams)

            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            card.addView(contentLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))

            val titleText = TextView(context).apply {
                text = "Confirm Resolution"
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            contentLayout.addView(titleText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16f)
            })

            val messageText = TextView(context).apply {
                text = "Do you want to keep this resolution?\nReverting in 15 seconds."
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 14f
                gravity = Gravity.CENTER
            }
            contentLayout.addView(messageText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24f)
            })

            val buttonsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }
            contentLayout.addView(buttonsRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            val revertButton = TextView(context).apply {
                text = "Revert"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                
                val btnBg = GradientDrawable().apply {
                    setColor(Color.argb(38, 255, 255, 255))
                    cornerRadius = dp(20f).toFloat()
                }
                background = btnBg
                isClickable = true
                isFocusable = true
                
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            btnBg.setColor(Color.argb(76, 255, 255, 255))
                            v.background = btnBg
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            btnBg.setColor(Color.argb(38, 255, 255, 255))
                            v.background = btnBg
                        }
                    }
                    false
                }
                
                setOnClickListener {
                    revertResolution(displayId, originalW, originalH)
                }
            }

            val keepButton = TextView(context).apply {
                text = "Keep"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A73E8"))
                    cornerRadius = dp(20f).toFloat()
                }
                background = btnBg
                isClickable = true
                isFocusable = true
                
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            btnBg.setColor(Color.parseColor("#1557B0"))
                            v.background = btnBg
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            btnBg.setColor(Color.parseColor("#1A73E8"))
                            v.background = btnBg
                        }
                    }
                    false
                }
                
                setOnClickListener {
                    pendingResolutionReversions.remove(displayId)
                    removeResolutionConfirmationOverlay(displayId)
                }
            }

            revertButton.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            keepButton.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))

            val revertParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                rightMargin = dp(6f)
            }
            
            val keepParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                leftMargin = dp(6f)
            }
            
            buttonsRow.addView(revertButton, revertParams)
            buttonsRow.addView(keepButton, keepParams)

            card.alpha = 0f
            card.scaleX = 0.8f
            card.scaleY = 0.8f
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

            var remainingSeconds = 15
            val countdownRunnable = object : Runnable {
                override fun run() {
                    remainingSeconds--
                    if (remainingSeconds <= 0) {
                        revertResolution(displayId, originalW, originalH)
                    } else {
                        messageText.text = "Do you want to keep this resolution?\nReverting in $remainingSeconds seconds."
                        handler.postDelayed(this, 1000)
                    }
                }
            }

            resolutionConfirmationRunnables[displayId] = countdownRunnable
            resolutionConfirmationContainers[displayId] = container

            try {
                wm.addView(container, params)
                handler.postDelayed(countdownRunnable, 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Resolution confirmation overlay", e)
                resolutionConfirmationContainers.remove(displayId)
                resolutionConfirmationRunnables.remove(displayId)
            }
        }
    }

    private fun removeRefreshConfirmationOverlay(displayId: Int) {
        refreshConfirmationRunnables.remove(displayId)?.let { handler.removeCallbacks(it) }
        refreshConfirmationContainers.remove(displayId)?.let { container ->
            try {
                val displayContext = getDisplayContext(displayId)
                val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(container)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove Refresh Rate confirmation overlay", e)
            }
        }
    }

    private fun revertRefreshRate(displayId: Int, originalModeId: Int, originalFps: Int) {
        removeRefreshConfirmationOverlay(displayId)
        pendingRefreshReversions.remove(displayId)
        ThemeManager.setRefreshRate(this, displayId, originalFps)
        ThemeManager.setRefreshRateMode(this, displayId, originalModeId)
        if (originalModeId > 0) {
            applyPersistentRefreshRate(displayId, originalModeId)
        } else {
            removePersistentRefreshRate(displayId)
        }
    }

    private fun showRefreshRateConfirmationOverlay(displayId: Int, targetModeId: Int, targetFps: Int, originalModeId: Int, originalFps: Int) {
        removeRefreshConfirmationOverlay(displayId)
        pendingRefreshReversions[displayId] = Pair(originalModeId, originalFps)

        handler.post {
            val context = getDisplayContext(displayId)
            val density = context.resources.displayMetrics.density
            fun dp(value: Float): Int = (value * density).toInt()

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val container = FrameLayout(context)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                dimAmount = 0.5f
                gravity = Gravity.CENTER
                preferredDisplayModeId = targetModeId
            }

            val card = FrameLayout(context).apply {
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#E6121212"))
                    cornerRadius = dp(20f).toFloat()
                    setStroke(dp(1.5f), Color.argb(46, 255, 255, 255))
                }
                background = bg
                val cardPadding = dp(24f)
                setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            }

            val cardParams = FrameLayout.LayoutParams(
                dp(320f),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            container.addView(card, cardParams)

            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            card.addView(contentLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))

            val titleText = TextView(context).apply {
                text = "Confirm Refresh Rate"
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            contentLayout.addView(titleText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16f)
            })

            val messageText = TextView(context).apply {
                text = "Do you want to keep this refresh rate?\nReverting in 15 seconds."
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 14f
                gravity = Gravity.CENTER
            }
            contentLayout.addView(messageText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24f)
            })

            val buttonsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }
            contentLayout.addView(buttonsRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            val revertButton = TextView(context).apply {
                text = "Revert"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                
                val btnBg = GradientDrawable().apply {
                    setColor(Color.argb(38, 255, 255, 255))
                    cornerRadius = dp(20f).toFloat()
                }
                background = btnBg
                isClickable = true
                isFocusable = true
                
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            btnBg.setColor(Color.argb(76, 255, 255, 255))
                            v.background = btnBg
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            btnBg.setColor(Color.argb(38, 255, 255, 255))
                            v.background = btnBg
                        }
                    }
                    false
                }
                
                setOnClickListener {
                    revertRefreshRate(displayId, originalModeId, originalFps)
                }
            }

            val keepButton = TextView(context).apply {
                text = "Keep"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A73E8"))
                    cornerRadius = dp(20f).toFloat()
                }
                background = btnBg
                isClickable = true
                isFocusable = true
                
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            btnBg.setColor(Color.parseColor("#1557B0"))
                            v.background = btnBg
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            btnBg.setColor(Color.parseColor("#1A73E8"))
                            v.background = btnBg
                        }
                    }
                    false
                }
                
                setOnClickListener {
                    pendingRefreshReversions.remove(displayId)
                    removeRefreshConfirmationOverlay(displayId)
                    ThemeManager.setRefreshRate(this@FreeformOverlayService, displayId, targetFps)
                    ThemeManager.setRefreshRateMode(this@FreeformOverlayService, displayId, targetModeId)
                    applyPersistentRefreshRate(displayId, targetModeId)
                }
            }

            revertButton.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            keepButton.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))

            val revertParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                rightMargin = dp(6f)
            }
            
            val keepParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                leftMargin = dp(6f)
            }
            
            buttonsRow.addView(revertButton, revertParams)
            buttonsRow.addView(keepButton, keepParams)

            card.alpha = 0f
            card.scaleX = 0.8f
            card.scaleY = 0.8f
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

            var remainingSeconds = 15
            val countdownRunnable = object : Runnable {
                override fun run() {
                    remainingSeconds--
                    if (remainingSeconds <= 0) {
                        revertRefreshRate(displayId, originalModeId, originalFps)
                    } else {
                        messageText.text = "Do you want to keep this refresh rate?\nReverting in $remainingSeconds seconds."
                        handler.postDelayed(this, 1000)
                    }
                }
            }

            refreshConfirmationRunnables[displayId] = countdownRunnable
            refreshConfirmationContainers[displayId] = container

            try {
                wm.addView(container, params)
                handler.postDelayed(countdownRunnable, 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Refresh Rate confirmation overlay", e)
                refreshConfirmationContainers.remove(displayId)
                refreshConfirmationRunnables.remove(displayId)
            }
        }
    }

    private fun applyPersistentRefreshRate(displayId: Int, modeId: Int) {
        removePersistentRefreshRate(displayId)
        if (modeId <= 0) return
        
        handler.post {
            try {
                val context = getDisplayContext(displayId)
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = View(context)
                
                val params = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = 0
                    y = 0
                    preferredDisplayModeId = modeId
                }
                
                wm.addView(view, params)
                refreshRateOverlays[displayId] = view
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply persistent refresh rate overlay for display $displayId", e)
            }
        }
    }

    private fun removePersistentRefreshRate(displayId: Int) {
        refreshRateOverlays.remove(displayId)?.let { view ->
            try {
                val context = getDisplayContext(displayId)
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove persistent refresh rate overlay", e)
            }
        }
    }

    private fun initRefreshRateOverlays() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.displays.forEach { d ->
            val savedModeId = ThemeManager.getRefreshRateMode(this, d.displayId, 0)
            if (savedModeId > 0) {
                applyPersistentRefreshRate(d.displayId, savedModeId)
            }
        }
    }

    inner class DisplayShell(val displayId: Int) {
        val overlays = java.util.concurrent.ConcurrentHashMap<Int, DragResizeOverlay>()
        private var guideView: View? = null
        private var sensitivityGuideView: SnappingSensitivityGuideView? = null
        private var snapGuideView: View? = null

        fun isEnabled(): Boolean {
            if (!ThemeManager.isGlobalOverlayEnabled(this@FreeformOverlayService)) {
                return false
            }
            return ThemeManager.isDisplayShellEnabled(this@FreeformOverlayService, displayId)
        }

        fun update(
            displayTasks: List<AppTask>,
            taskBoundsMap: Map<Int, TaskBounds>,
            focusedPackage: String,
            currentTopTaskId: Int,
            density: Float,
            borderWidth: Int,
            titleBarHeight: Int,
            activeTaskIds: MutableSet<Int>,
            mergedTasks: List<AppTask>,
            forceRelayer: Boolean
        ) {
            if (!isEnabled()) {
                clear()
                return
            }

            val taskDecorBounds = mutableMapOf<Int, android.graphics.Rect>()
            val titleBarRects = mutableMapOf<Int, android.graphics.Rect>()

            for (task in displayTasks) {
                val taskInfo = taskBoundsMap[task.taskId]
                var bounds = taskInfo?.bounds ?: intendedBounds[task.packageName]
                if (bounds != null) {
                    val safe = getSafeAreaRect(displayId)
                    val isDocked = dockedTasks.contains(task.taskId) || dockedPackages.contains(task.packageName)
                    val usePill = ThemeManager.usePillForSnapped(this@FreeformOverlayService)
                    val isMaximized = overlays[task.taskId]?.isMaximized == true
                    val shouldShowPill = isMaximized || (isDocked && usePill)
                    
                    val decorTop = if (shouldShowPill) bounds.top else (bounds.top - titleBarHeight).coerceAtLeast(safe.top - titleBarHeight)
                    
                    taskDecorBounds[task.taskId] = android.graphics.Rect(
                        bounds.left - borderWidth,
                        bounds.top,
                        bounds.right + borderWidth,
                        bounds.bottom + borderWidth
                    )
                    
                    titleBarRects[task.taskId] = android.graphics.Rect(
                        bounds.left - borderWidth,
                        decorTop,
                        bounds.right + borderWidth,
                        bounds.top
                    )
                }
            }

            val nonBlacklistedTasks = displayTasks.filter { t ->
                val low = t.packageName.lowercase()
                !baseBlacklist.any { low.contains(it) } && !isBlacklisted(this@FreeformOverlayService, t.packageName)
            }
            val displaySortedTaskIds = nonBlacklistedTasks.map { it.taskId }

            for (i in displayTasks.indices.reversed()) {
                val task = displayTasks[i]
                val taskInfo = taskBoundsMap[task.taskId] ?: continue
                val windowMode = taskInfo.windowingMode
                
                val isFreeformReported = windowMode == 5
                val isIntendedFreeform = intendedBounds.containsKey(task.packageName)
                
                if (isFreeformReported || isIntendedFreeform) {
                    gracePeriodTasks[task.taskId] = 0
                } else {
                    if (gracePeriodTasks.containsKey(task.taskId)) {
                        val currentGrace = gracePeriodTasks.getOrDefault(task.taskId, 0)
                        if (currentGrace < 3) {
                            gracePeriodTasks[task.taskId] = currentGrace + 1
                        } else {
                            handler.post { hideOverlay(task.taskId) }
                            continue
                        }
                    } else {
                        handler.post { hideOverlay(task.taskId) }
                        continue
                    }
                }
                
                val isFreeform = (gracePeriodTasks.getOrDefault(task.taskId, 3) < 3) || isFreeformReported || isIntendedFreeform
                if (!isFreeform) {
                    handler.post { hideOverlay(task.taskId) }
                    continue
                }

                if (minimizedTasks.contains(task.taskId)) {
                    val minimizeTime = minimizedTimestamps[task.taskId] ?: 0L
                    val timeSinceMinimize = System.currentTimeMillis() - minimizeTime
                    // Prevent unminimizing due to race conditions during the initial transition period (1.5 seconds)
                    if (timeSinceMinimize > 1500) {
                        val isTopTask = task.taskId == currentTopTaskId
                        val isLeashMinimizationEnabled = CompatibilityManager.isHybridLeashMinimizationEnabled(this@FreeformOverlayService)
                        
                        if (isLeashMinimizationEnabled) {
                            // Check if this task became top/visible due to a very recent minimization of another task
                            val timeSinceLastMinimization = System.currentTimeMillis() - lastMinimizationTimestamp
                            if (timeSinceLastMinimization < 2500) {
                                // Focus shift was automatic because another task was minimized, not a direct user activation.
                                // Keep it minimized and hide overlay.
                                handler.post { hideOverlay(task.taskId) }
                                continue
                            }
                            
                            if (isTopTask) { // Only unminimize automatically if it becomes the top-focused task
                                Log.d(TAG, "Minimized task ${task.taskId} detected as active (top task). Unminimizing natively!")
                                minimizedTasks.remove(task.taskId)
                                minimizedTimestamps.remove(task.taskId)
                                saveMinimizedTasksToPrefs()
                                
                                // Restore compositor surface first
                                TaskManager.restoreTaskSurface(task.taskId)
                                
                                // Convert task back to freeform (5) and restore bounds instantly!
                                val savedBounds = minimizedTaskData[task.taskId]?.bounds
                                minimizedTaskData.remove(task.taskId)
                                
                                Thread {
                                    ShellExecutor.setTaskWindowingMode(task.taskId, 5, true)
                                    if (savedBounds != null) {
                                        try {
                                            Thread.sleep(150)
                                        } catch (e: Exception) {}
                                        ShellExecutor.resizeTask(task.taskId, savedBounds.left, savedBounds.top, savedBounds.right, savedBounds.bottom)
                                    }
                                }.start()

                                handler.post {
                                    bubbles[task.taskId]?.hide()
                                    bubbles.remove(task.taskId)
                                }
                            } else {
                                handler.post { hideOverlay(task.taskId) }
                                continue
                            }
                        } else {
                            // Legacy minimization: checks both isTopTask and taskInfo.isVisible, and doesn't manipulate task leashes
                            if (isTopTask || taskInfo.isVisible) {
                                Log.d(TAG, "Minimized task ${task.taskId} detected as active/visible (legacy). Unminimizing natively!")
                                minimizedTasks.remove(task.taskId)
                                minimizedTimestamps.remove(task.taskId)
                                saveMinimizedTasksToPrefs()
                                
                                // Convert task back to freeform (5) and restore bounds instantly!
                                val savedBounds = minimizedTaskData[task.taskId]?.bounds
                                minimizedTaskData.remove(task.taskId)
                                
                                Thread {
                                    ShellExecutor.setTaskWindowingMode(task.taskId, 5, true)
                                    if (savedBounds != null) {
                                        try {
                                            Thread.sleep(150)
                                        } catch (e: Exception) {}
                                        ShellExecutor.resizeTask(task.taskId, savedBounds.left, savedBounds.top, savedBounds.right, savedBounds.bottom)
                                    }
                                }.start()

                                handler.post {
                                    bubbles[task.taskId]?.hide()
                                    bubbles.remove(task.taskId)
                                }
                            } else {
                                handler.post { hideOverlay(task.taskId) }
                                continue
                            }
                        }
                    } else {
                        handler.post { hideOverlay(task.taskId) }
                        continue
                    }
                }

                val isTaskVisible = taskInfo.isVisible
                val bypassVisibility = CompatibilityManager.isAndroid12VisibilityBypassEnabled(this@FreeformOverlayService) && isFreeform
                val isFreeformWindow = windowMode == 5
                
                val actualVisible = if (isTaskVisible || bypassVisibility) {
                    visibilityGracePeriodTasks[task.taskId] = 0
                    true
                } else {
                    val currentGrace = visibilityGracePeriodTasks.getOrDefault(task.taskId, 0)
                    if (currentGrace < 3) {
                        visibilityGracePeriodTasks[task.taskId] = currentGrace + 1
                        true
                    } else {
                        false
                    }
                }
                
                if (!actualVisible) {
                    handler.post { hideOverlay(task.taskId) }
                    continue
                }

                var isCoveredByFullscreen = false
                val currentTaskIndex = nonBlacklistedTasks.indexOfFirst { it.taskId == task.taskId }
                if (currentTaskIndex > 0) {
                    for (j in 0 until currentTaskIndex) {
                        val aboveTask = nonBlacklistedTasks[j]
                        val aboveInfo = taskBoundsMap[aboveTask.taskId]
                        val aboveOverlay = overlays[aboveTask.taskId]
                        val isAboveMaximized = aboveOverlay?.isMaximized == true
                        val isAboveFullscreen = aboveInfo?.windowingMode == 1
                        
                        val dmMetrics = getRealMetricsForDisplay(displayId)
                        val aboveBounds = aboveInfo?.bounds
                        val isAboveHuge = aboveBounds != null && 
                                          aboveBounds.width() >= (dmMetrics.widthPixels * 0.95f) && 
                                          aboveBounds.height() >= (dmMetrics.heightPixels * 0.95f)
                        
                        if (isAboveMaximized || isAboveFullscreen || isAboveHuge) {
                            isCoveredByFullscreen = true
                            break
                        }
                    }
                }
                
                if (isCoveredByFullscreen) {
                    handler.post { hideOverlay(task.taskId) }
                    continue
                }

                val isDocked = dockedTasks.contains(task.taskId) || dockedPackages.contains(task.packageName)
                if (isDocked && !dockedTasks.contains(task.taskId)) {
                    dockedTasks.add(task.taskId)
                }
                
                val currentOccluders = mutableListOf<DragResizeOverlay.OccluderInfo>()
                if (currentTaskIndex > 0) {
                    for (j in 0 until currentTaskIndex) {
                        val aboveTask = nonBlacklistedTasks[j]
                        val aboveOverlay = overlays[aboveTask.taskId]
                        if (aboveOverlay != null) {
                            currentOccluders.addAll(aboveOverlay.getOcclusionRegions())
                        } else {
                            // Fallback to estimation
                            val aboveInfo = taskBoundsMap[aboveTask.taskId]
                            var bounds = aboveInfo?.bounds ?: intendedBounds[aboveTask.packageName]
                            if (bounds != null) {
                                val safe = getSafeAreaRect(displayId)
                                val isDockedAbove = dockedTasks.contains(aboveTask.taskId) || dockedPackages.contains(aboveTask.packageName)
                                val usePill = ThemeManager.usePillForSnapped(this@FreeformOverlayService)
                                val shouldShowPill = isDockedAbove && usePill
                                
                                val rBase = ThemeManager.getRoundness(this@FreeformOverlayService) * density
                                val winRadius = if (isDockedAbove) rBase / 2f else rBase
                                
                                if (shouldShowPill) {
                                    val useW = bounds.width()
                                    val pillW = (110 * density).toInt().coerceAtMost(useW)
                                    val pillH = (40 * density).toInt()
                                    val pillX = bounds.left + (useW - pillW) / 2
                                    val pillY = (bounds.top + (4 * density).toInt()).coerceIn(safe.top + (4 * density).toInt(), safe.bottom - pillH)
                                    val pillRect = android.graphics.Rect(pillX, pillY, pillX + pillW, pillY + pillH)
                                    currentOccluders.add(DragResizeOverlay.OccluderInfo(pillRect, pillH / 2f))
                                    
                                    val bodyRect = android.graphics.Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
                                    currentOccluders.add(DragResizeOverlay.OccluderInfo(bodyRect, winRadius))
                                } else {
                                    val fw = bounds.width() + (borderWidth * 2)
                                    val fh = bounds.height() + titleBarHeight + borderWidth
                                    val fx = bounds.left - borderWidth
                                    val decorY = (bounds.top - titleBarHeight).coerceIn(safe.top - titleBarHeight, safe.bottom - titleBarHeight)
                                    val fy = decorY - borderWidth
                                    
                                    val totalRect = android.graphics.Rect(fx, fy, fx + fw, fy + fh)
                                    currentOccluders.add(DragResizeOverlay.OccluderInfo(totalRect, winRadius))
                                }
                            }
                        }
                    }
                }
                
                val lowPkg = task.packageName.lowercase()
                if (baseBlacklist.any { lowPkg.contains(it) } || isBlacklisted(this@FreeformOverlayService, task.packageName)) {
                    continue
                }

                var bounds = taskInfo.bounds

                val safe = getSafeAreaRect(displayId)
                val usePill = ThemeManager.usePillForSnapped(this@FreeformOverlayService)
                val shouldShowPill = (overlays[task.taskId]?.isMaximized == true) || (isDocked && usePill)
                val minTop = if (shouldShowPill) safe.top else safe.top + titleBarHeight
                
                if (displayId == 0 && bounds.top < minTop && !(overlays[task.taskId]?.isInteracting ?: false)) {
                    val shift = minTop - bounds.top
                    val newBounds = android.graphics.Rect(bounds.left, minTop, bounds.right, bounds.bottom + shift)
                    bounds = newBounds
                    val tid = task.taskId
                    if (tid != -1 && !correctedTaskIds.contains(tid)) {
                        correctedTaskIds.add(tid)
                        Thread { ShellExecutor.resizeTask(tid, bounds.left, bounds.top, bounds.right, bounds.bottom) }.start()
                    }
                }

                activeTaskIds.add(task.taskId)
                val isFocus = task.taskId == currentTopTaskId

                handler.post {
                    // Prevent cross-display overlay leaks: if another display shell currently holds an overlay
                    // for this taskId, explicitly hide and prune it.
                    displayShells.values.forEach { otherShell ->
                        if (otherShell.displayId != displayId && otherShell.overlays.containsKey(task.taskId)) {
                            Log.d(TAG, "Pruning cross-display overlay leak for task ${task.taskId} from display ${otherShell.displayId}")
                            otherShell.hideOverlay(task.taskId)
                        }
                    }

                    val existing = overlays[task.taskId]
                    if (existing == null || existing.currentDisplayId != displayId) {
                        existing?.hide()
                        val decorTop = (bounds.top - titleBarHeight).coerceAtLeast(safe.top - titleBarHeight)
                        
                        val overlay = DragResizeOverlay(this@FreeformOverlayService, task.taskId, task.packageName, displayId, 
                            onMinimize = { if (isDocked) restoreTask(task.taskId) else minimizeTask(task.taskId) },
                            onClose = { 
                                knownFreeformTasks.remove(task.taskId)
                                hideOverlay(task.taskId)
                                Thread { 
                                    if (shouldForceClose(this@FreeformOverlayService, task.packageName)) {
                                        ShellExecutor.forceStopApp(task.packageName, task.taskId)
                                    } else {
                                        ShellExecutor.removeTask(task.taskId)
                                    }
                                }.start()
                            }
                        )
                        overlay.show()
                        overlay.setDockMode(isDocked)
                        overlay.setActivityName(taskInfo.activityName)
                        overlay.updateFromSystem(bounds.left, bounds.top, bounds.width(), bounds.height(), forcedTitleTop = decorTop)
                        overlay.updateFocus(isFocus, currentOccluders)
                        overlays[task.taskId] = overlay
                    } else {
                        val decorTop = (bounds.top - titleBarHeight).coerceAtLeast(safe.top - titleBarHeight)
                        val now = System.currentTimeMillis()
                        if (existing.isInteracting && (now - existing.lastInteractionTime) > 3000) {
                            existing.isInteracting = false
                        }
                        
                        val recentlyInteracted = (now - existing.lastInteractionTime) < 2000
                        if (!existing.isInteracting && !recentlyInteracted) {
                            existing.setDockMode(isDocked)
                        }
                        existing.setActivityName(taskInfo.activityName)
                        val shouldUpdate = !existing.isInteracting || !isFocus
                        if (shouldUpdate) {
                            existing.updateFromSystem(bounds.left, bounds.top, bounds.width(), bounds.height(), forcedTitleTop = decorTop)
                            existing.updateFocus(isFocus, currentOccluders)
                            if (forceRelayer) existing.bringToFront()
                        }
                    }
                    
                    if (isFreeformReported) {
                        intendedBounds.remove(task.packageName)
                        intendedDisplayId.remove(task.packageName)
                    }
                }
            }

            handler.post {
                for (i in displaySortedTaskIds.indices.reversed()) {
                    val tid = displaySortedTaskIds[i]
                    overlays[tid]?.bringToFront()
                }
            }
        }

        fun hideOverlay(taskId: Int) {
            overlays[taskId]?.hide()
            overlays.remove(taskId)
            ShellExecutor.notifyOverlayCountChanged(this@FreeformOverlayService.overlays.size)
        }

        fun clear() {
            overlays.values.forEach { it.hide() }
            overlays.clear()
            removeDockGuide()
            removeSensitivityGuide()
            removeSnapGuide()
        }

        fun removeDockGuide() {
            handler.post {
                guideView?.let {
                    if (it.parent != null) {
                        val wm = it.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        try { wm.removeViewImmediate(it) } catch (e: Exception) {}
                    }
                    guideView = null
                }
            }
        }

        fun removeSensitivityGuide() {
            handler.post {
                sensitivityGuideView?.let {
                    if (it.parent != null) {
                        val wm = it.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        try { wm.removeViewImmediate(it) } catch (e: Exception) {}
                    }
                    sensitivityGuideView = null
                }
            }
        }

        fun removeSnapGuide() {
            handler.post {
                snapGuideView?.let {
                    if (it.parent != null) {
                        val wm = it.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        try { wm.removeViewImmediate(it) } catch (e: Exception) {}
                    }
                    snapGuideView = null
                }
            }
        }

        fun updateDockGuide(pos: Int, size: Int) {
            if (pos == 0 || size == 0) {
                removeDockGuide()
                return
            }
            handler.post {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
                val dContext = createDisplayContext(targetDisplay)
                val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) } catch (e: Exception) { dContext }
                } else {
                    dContext
                }
                val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                if (guideView == null) {
                    guideView = View(this@FreeformOverlayService).apply {
                        setBackgroundColor(android.graphics.Color.argb(150, 66, 133, 244))
                    }
                }
                if (guideView?.parent != null) wm.removeView(guideView)
                val params = WindowManager.LayoutParams(
                    if (pos == 3 || pos == 4) size else WindowManager.LayoutParams.MATCH_PARENT,
                    if (pos == 1 || pos == 2) size else WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = when (pos) {
                        1 -> Gravity.TOP
                        2 -> Gravity.BOTTOM
                        3 -> Gravity.LEFT
                        4 -> Gravity.RIGHT
                        else -> Gravity.TOP
                    }
                }
                try { wm.addView(guideView, params) } catch (e: Exception) {}
            }
        }

        fun updateSensitivityGuide(sizeDp: Int) {
            handler.post {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
                val dContext = createDisplayContext(targetDisplay)
                val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) } catch (e: Exception) { dContext }
                } else {
                    dContext
                }
                val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val density = dContext.resources.displayMetrics.density
                val sizePx = (sizeDp * density).toInt()
                
                if (sensitivityGuideView == null) {
                    sensitivityGuideView = SnappingSensitivityGuideView(this@FreeformOverlayService, density)
                }
                sensitivityGuideView?.sizePx = sizePx
                if (sensitivityGuideView?.parent != null) {
                    try { wm.removeViewImmediate(sensitivityGuideView) } catch (e: Exception) {}
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                try { wm.addView(sensitivityGuideView, params) } catch (e: Exception) {}
            }
        }

        fun updateSnapGuide(rect: android.graphics.Rect) {
            handler.post {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
                val dContext = createDisplayContext(targetDisplay)
                val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) } catch (e: Exception) { dContext }
                } else {
                    dContext
                }
                val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                if (snapGuideView == null) {
                    snapGuideView = View(this@FreeformOverlayService).apply {
                        val drawable = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.argb(40, 66, 133, 244))
                            setStroke((3 * dContext.resources.displayMetrics.density).toInt(), android.graphics.Color.argb(180, 66, 133, 244))
                            cornerRadius = 16 * dContext.resources.displayMetrics.density
                        }
                        background = drawable
                    }
                }
                
                val params = WindowManager.LayoutParams(
                    rect.width(),
                    rect.height(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    x = rect.left
                    y = rect.top
                    gravity = Gravity.TOP or Gravity.LEFT
                }
                
                try {
                    if (snapGuideView?.parent == null) {
                        wm.addView(snapGuideView, params)
                    } else {
                        wm.updateViewLayout(snapGuideView, params)
                    }
                } catch (e: Exception) {
                    Log.e("FreeformOverlayService", "Failed to update snap guide layout", e)
                }
            }
        }
    }

    private fun findHandleConfigs(displayId: Int): List<HandleConfig> {
        val docked = overlays.values.filter { it.currentDisplayId == displayId && it.isDocked }
        if (docked.isEmpty()) return emptyList()
        
        val safe = getSafeAreaRect(displayId)
        val metrics = getRealMetricsForDisplay(displayId)
        val density = metrics.density
        val threshold = (60 * density).toInt().coerceAtLeast(150)
        
        val configs = mutableListOf<HandleConfig>()
        val pairedHorizontalIds = mutableSetOf<Int>()
        val pairedVerticalIds = mutableSetOf<Int>()
        
        val contactThreshold = (24 * density).toInt()
        val minOverlap = (100 * density)

        // 1. UNIVERSAL ADJACENCY GEOMETRY LOGIC (Any number of side-by-side or stacked apps)
        for (i in docked.indices) {
            for (j in docked.indices) {
                if (i == j) continue
                val a = docked[i]
                val b = docked[j]

                // Check side-by-side adjacency: a is on the left, b is on the right
                val horizGap = b.winL - (a.winL + a.winW)
                if (horizGap in -contactThreshold..contactThreshold) {
                    val overlapTop = Math.max(a.winT, b.winT)
                    val overlapBottom = Math.min(a.winT + a.winH, b.winT + b.winH)
                    if (overlapBottom - overlapTop > minOverlap) {
                        val splitCoord = (a.winL + a.winW + b.winL) / 2
                        configs.add(HandleConfig(true, a, b, splitCoord, null, 0))
                        pairedHorizontalIds.add(a.taskId)
                        pairedHorizontalIds.add(b.taskId)
                    }
                }

                // Check top-bottom adjacency: a is on top, b is on bottom
                val vertGap = b.winT - (a.winT + a.winH)
                if (vertGap in -contactThreshold..contactThreshold) {
                    val overlapLeft = Math.max(a.winL, b.winL)
                    val overlapRight = Math.min(a.winL + a.winW, b.winL + b.winW)
                    if (overlapRight - overlapLeft > minOverlap) {
                        val splitCoord = (a.winT + a.winH + b.winT) / 2
                        configs.add(HandleConfig(false, a, b, splitCoord, null, 0))
                        pairedVerticalIds.add(a.taskId)
                        pairedVerticalIds.add(b.taskId)
                    }
                }
            }
        }
        
        // 2. Search for SINGLE docked windows (not paired with anything else on their docked edge)
        for (task in docked) {
            val touchLeft = Math.abs(task.winL - safe.left) < threshold
            val touchRight = Math.abs(task.winL + task.winW - safe.right) < threshold
            val touchTop = Math.abs(task.winT - safe.top) < threshold || Math.abs(task.winT - (safe.top + task.titleBarHeight)) < threshold
            val touchBottom = Math.abs(task.winT + task.winH - safe.bottom) < threshold

            var isLeft = false
            var isRight = false
            var isTop = false
            var isBottom = false

            val widthDocked = task.winW < safe.width() - (24 * density).toInt()
            val heightDocked = task.winH < safe.height() - (24 * density).toInt()

            if (task.taskId !in pairedHorizontalIds) {
                if (touchLeft && widthDocked) {
                    isLeft = true
                }
                if (touchRight && widthDocked) {
                    isRight = true
                }
            }
            if (task.taskId !in pairedVerticalIds) {
                if (touchTop && heightDocked) {
                    isTop = true
                }
                if (touchBottom && heightDocked) {
                    isBottom = true
                }
            }
            
            if (isLeft) {
                val coord = task.winL + task.winW
                configs.add(HandleConfig(true, task, null, coord, task.currentDecorationColor, 1))
            }
            if (isRight) {
                val coord = task.winL
                configs.add(HandleConfig(true, task, null, coord, task.currentDecorationColor, 2))
            }
            if (isTop) {
                val coord = task.winT + task.winH
                configs.add(HandleConfig(false, task, null, coord, task.currentDecorationColor, 3))
            }
            if (isBottom) {
                val coord = task.winT
                configs.add(HandleConfig(false, task, null, coord, task.currentDecorationColor, 4))
            }
        }
        
        return configs.distinctBy { 
            if (it.task2 != null) {
                val id1 = Math.min(it.task1.taskId, it.task2.taskId)
                val id2 = Math.max(it.task1.taskId, it.task2.taskId)
                "paired_${id1}_${id2}_${it.isVertical}"
            } else {
                "single_${it.task1.taskId}_${it.dockSide}"
            }
        }
    }

    private fun updateSplitHandles() {
        val stateBuilder = StringBuilder()
        for (overlay in overlays.values) {
            stateBuilder.append("${overlay.taskId}:${overlay.isDocked}:${overlay.winL},${overlay.winT},${overlay.winW},${overlay.winH};")
        }
        val currentStateHash = stateBuilder.toString()
        if (currentStateHash == lastSplitHandleStateHash) {
            return
        }
        lastSplitHandleStateHash = currentStateHash

        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        val validKeys = mutableSetOf<String>()
        
        for (display in displays) {
            val displayId = display.displayId
            val configs = findHandleConfigs(displayId)
            
            for (config in configs) {
                val key = if (config.task2 != null) {
                    val id1 = Math.min(config.task1.taskId, config.task2.taskId)
                    val id2 = Math.max(config.task1.taskId, config.task2.taskId)
                    "paired_${id1}_${id2}_${config.isVertical}"
                } else {
                    "single_${config.task1.taskId}_${config.dockSide}"
                }
                validKeys.add(key)
                
                val existing = activeHandles[key]
                if (existing != null) {
                    if (existing.isDragging) continue
                    existing.syncPosition(config.splitCoord)
                } else {
                    val dContext = createDisplayContext(display)
                    val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            dContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                        } catch (e: Exception) {
                            dContext
                        }
                    } else {
                        dContext
                    }
                    val handle = SplitResizeHandle(
                        this, 
                        windowContext, 
                        config.isVertical, 
                        config.task1, 
                        config.task2, 
                        config.customColor, 
                        config.dockSide
                    )
                    handle.show(config.splitCoord)
                    activeHandles[key] = handle
                }
            }
        }
        
        // Remove active handles that are no longer valid
        val toRemove = activeHandles.keys.filter { it !in validKeys }
        for (key in toRemove) {
            activeHandles[key]?.hide()
            activeHandles.remove(key)
        }
    }
}

data class HandleConfig(
    val isVertical: Boolean,
    val task1: DragResizeOverlay,
    val task2: DragResizeOverlay?,
    val splitCoord: Int,
    val customColor: Int?,
    val dockSide: Int // 1=Left, 2=Right, 3=Top, 4=Bottom (for single, 0 for paired)
)

class SplitResizeHandle(
    private val service: FreeformOverlayService,
    private val displayContext: Context,
    val isVertical: Boolean,
    val task1: DragResizeOverlay,
    val task2: DragResizeOverlay?,
    val customColor: Int? = null,
    val dockSide: Int = 0
) {
    private val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = displayContext.resources.displayMetrics.density
    
    private var handleView: FrameLayout? = null
    private var capsuleView: View? = null
    
    var isDragging = false
        private set
        
    var isTemporarilyHidden = false
        set(value) {
            field = value
            handleView?.visibility = if (value) View.GONE else View.VISIBLE
        }
        
    private var lastSplitTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var startCoord = 0
    private var currentSplitCoord = 0
    private val activeLeftTasks = mutableListOf<DragResizeOverlay>()
    private val activeRightTasks = mutableListOf<DragResizeOverlay>()
    private val activeTopTasks = mutableListOf<DragResizeOverlay>()
    private val activeBottomTasks = mutableListOf<DragResizeOverlay>()

    private fun getSmartBounds(coord: Int): WindowManager.LayoutParams {
        val touchThickness = (24 * density).toInt()
        val params = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        if (isVertical) {
            params.x = coord - touchThickness / 2
            params.width = touchThickness
            
            if (task2 != null) {
                // Paired vertical splitter: restrict to vertical overlap
                val overlapTop = Math.max(task1.winT, task2.winT)
                val overlapBottom = Math.min(task1.winT + task1.winH, task2.winT + task2.winH)
                params.y = overlapTop
                params.height = (overlapBottom - overlapTop).coerceAtLeast(100)
            } else {
                // Single vertical splitter: restrict to task height
                params.y = task1.winT
                params.height = task1.winH.coerceAtLeast(100)
            }
        } else {
            params.y = coord - touchThickness / 2
            params.height = touchThickness
            
            if (task2 != null) {
                // Paired horizontal splitter: restrict to horizontal overlap
                val overlapLeft = Math.max(task1.winL, task2.winL)
                val overlapRight = Math.min(task1.winL + task1.winW, task2.winL + task2.winW)
                params.x = overlapLeft
                params.width = (overlapRight - overlapLeft).coerceAtLeast(100)
            } else {
                // Single horizontal splitter: restrict to task width
                params.x = task1.winL
                params.width = task1.winW.coerceAtLeast(100)
            }
        }
        return params
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun show(initialCoord: Int) {
        currentSplitCoord = initialCoord
        val touchThickness = (24 * density).toInt()
        val defaultCapsuleThickness = (6 * density).toInt()
        val capsuleLength = (80 * density).toInt()
        
        val params = getSmartBounds(initialCoord)
        
        val capColor = customColor ?: Color.parseColor("#A0888888")
        capsuleView = View(displayContext).apply {
            background = GradientDrawable().apply {
                setColor(capColor)
                cornerRadius = 4 * density
            }
            alpha = 0.4f
        }

        val actualCapsuleLength = Math.min(capsuleLength, if (isVertical) params.height else params.width)
        handleView = object : FrameLayout(displayContext) {}.apply {
            val lp = FrameLayout.LayoutParams(
                if (isVertical) defaultCapsuleThickness else actualCapsuleLength,
                if (isVertical) actualCapsuleLength else defaultCapsuleThickness
            ).apply {
                gravity = Gravity.CENTER
            }
            addView(capsuleView, lp)
            visibility = if (isTemporarilyHidden) View.GONE else View.VISIBLE
        }

        handleView?.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    showCapsule(true)
                    true
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    if (!isDragging) {
                        showCapsule(false)
                    }
                    true
                }
                else -> false
            }
        }

        handleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    FreeformOverlayService.isSplitResizingActive = true
                    showCapsule(true)
                    service.notifyHandleDragStateChanged(this, true)
                    startX = event.rawX
                    startY = event.rawY
                    startCoord = if (isVertical) event.rawX.toInt() else event.rawY.toInt()
                    
                    activeLeftTasks.clear()
                    activeRightTasks.clear()
                    activeTopTasks.clear()
                    activeBottomTasks.clear()
                    
                    val allDocked = service.overlays.values.filter { 
                        it.currentDisplayId == task1.currentDisplayId && it.isDocked 
                    }
                    val contactThreshold = (32 * density).toInt()
                    
                    if (isVertical) {
                        for (t in allDocked) {
                            t.preInteractL = t.winL
                            t.preInteractT = t.winT
                            t.preInteractW = t.winW
                            t.preInteractH = t.winH
                            t.preShouldShowPill = t.isMaximized || (t.isDocked && ThemeManager.usePillForSnapped(displayContext))
                            t.isResizing = true
                            t.isInteracting = true
                            
                            val midpoint = t.preInteractL + t.preInteractW / 2
                            if (midpoint < currentSplitCoord) {
                                val distRightEdge = Math.abs((t.preInteractL + t.preInteractW) - currentSplitCoord)
                                if (distRightEdge < contactThreshold) {
                                    activeLeftTasks.add(t)
                                }
                            } else {
                                val distLeftEdge = Math.abs(t.preInteractL - currentSplitCoord)
                                if (distLeftEdge < contactThreshold) {
                                    activeRightTasks.add(t)
                                }
                            }
                        }
                    } else {
                        for (t in allDocked) {
                            t.preInteractL = t.winL
                            t.preInteractT = t.winT
                            t.preInteractW = t.winW
                            t.preInteractH = t.winH
                            t.preShouldShowPill = t.isMaximized || (t.isDocked && ThemeManager.usePillForSnapped(displayContext))
                            t.isResizing = true
                            t.isInteracting = true
                            
                            val midpoint = t.preInteractT + t.preInteractH / 2
                            if (midpoint < currentSplitCoord) {
                                val distBottomEdge = Math.abs((t.preInteractT + t.preInteractH) - currentSplitCoord)
                                if (distBottomEdge < contactThreshold) {
                                    activeTopTasks.add(t)
                                }
                            } else {
                                val distTopEdge = Math.abs(t.preInteractT - currentSplitCoord)
                                if (distTopEdge < contactThreshold) {
                                    activeBottomTasks.add(t)
                                }
                            }
                        }
                    }
                    
                    for (t in allDocked) {
                        t.updateLayouts()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastSplitTime >= 16) {
                            lastSplitTime = now
                            val currentCoord = if (isVertical) event.rawX.toInt() else event.rawY.toInt()
                            resizeSplit(currentCoord)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    FreeformOverlayService.isSplitResizingActive = false
                    showCapsule(false)
                    service.notifyHandleDragStateChanged(this, false)
                    
                    val currentCoord = if (isVertical) event.rawX.toInt() else event.rawY.toInt()
                    resizeSplit(currentCoord) // Apply exact final coordinate to avoid end of drag offset
                    
                    val allDocked = service.overlays.values.filter { 
                        it.currentDisplayId == task1.currentDisplayId && it.isDocked 
                    }
                    for (t in allDocked) {
                        t.isResizing = false
                        t.isInteracting = false
                        t.lastInteractionTime = System.currentTimeMillis()
                        t.applyBounds(false) // Just resize task, do not trigger duplicate pollings
                        t.updateLayouts()
                    }
                    service.triggerFastPollingSequence() // Trigger exactly one fast polling sequence
                    
                    activeLeftTasks.clear()
                    activeRightTasks.clear()
                    activeTopTasks.clear()
                    activeBottomTasks.clear()
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(handleView, params)
        } catch (e: Exception) {}
    }

    private fun resizeSplit(currentCoord: Int) {
        val gap = (ThemeManager.getBorderWidth(displayContext) * density).toInt() / 2
        val safe = service.getSafeAreaRect(task1.currentDisplayId)
        val realtime = ThemeManager.realtimeResize(displayContext)
        
        if (task2 != null) {
            // Paired split resizing
            if (isVertical) {
                var minX = safe.left + (150 * density).toInt()
                var maxX = safe.right - (150 * density).toInt()
                
                for (lt in activeLeftTasks) {
                    minX = Math.max(minX, lt.preInteractL + (120 * density).toInt() + gap)
                }
                for (rt in activeRightTasks) {
                    maxX = Math.min(maxX, (rt.preInteractL + rt.preInteractW) - (120 * density).toInt() - gap)
                }
                
                val constrainedX = currentCoord.coerceIn(minX, maxX.coerceAtLeast(minX))
                currentSplitCoord = constrainedX
                
                // Resize all left tasks
                for (lt in activeLeftTasks) {
                    lt.winW = constrainedX - gap - lt.winL
                    lt.updateLayouts()
                    if (realtime) lt.applyBounds(false)
                }
                
                // Resize all right tasks
                for (rt in activeRightTasks) {
                    val rightEdge = rt.preInteractL + rt.preInteractW
                    rt.winL = constrainedX + gap
                    rt.winW = rightEdge - rt.winL
                    rt.updateLayouts()
                    if (realtime) rt.applyBounds(false)
                }
                
                updateHandlePosition(constrainedX)
            } else {
                var minY = safe.top + (100 * density).toInt()
                var maxY = safe.bottom - (100 * density).toInt()
                
                for (tt in activeTopTasks) {
                    minY = Math.max(minY, tt.preInteractT + (80 * density).toInt() + gap)
                }
                for (bt in activeBottomTasks) {
                    maxY = Math.min(maxY, (bt.preInteractT + bt.preInteractH) - (80 * density).toInt() - gap)
                }
                
                val constrainedY = currentCoord.coerceIn(minY, maxY.coerceAtLeast(minY))
                currentSplitCoord = constrainedY
                
                // Resize all top tasks
                for (tt in activeTopTasks) {
                    tt.winH = constrainedY - gap - tt.winT
                    tt.updateLayouts()
                    if (realtime) tt.applyBounds(false)
                }
                
                // Resize all bottom tasks
                for (bt in activeBottomTasks) {
                    val bottomEdge = bt.preInteractT + bt.preInteractH
                    bt.winT = constrainedY + gap
                    bt.winH = bottomEdge - bt.winT
                    bt.updateLayouts()
                    if (realtime) bt.applyBounds(false)
                }
                
                updateHandlePosition(constrainedY)
            }
        } else {
            // Single task resizing based on dockSide
            if (isVertical) {
                if (dockSide == 1) { // Left docked, handle is on the Right
                    val minX = task1.winL + (350 * density).toInt()
                    val maxX = safe.right - (100 * density).toInt()
                    val constrainedX = currentCoord.coerceIn(minX, maxX.coerceAtLeast(minX))
                    currentSplitCoord = constrainedX
                    
                    task1.winW = constrainedX - task1.winL
                    task1.updateLayouts()
                    if (realtime) task1.applyBounds(false)
                    updateHandlePosition(constrainedX)
                } else if (dockSide == 2) { // Right docked, handle is on the Left
                    val minX = safe.left + (100 * density).toInt()
                    val maxX = (task1.winL + task1.winW) - (350 * density).toInt()
                    val constrainedX = currentCoord.coerceIn(minX, maxX.coerceAtLeast(minX))
                    currentSplitCoord = constrainedX
                    
                    val rightEdge = task1.winL + task1.winW
                    task1.winL = constrainedX
                    task1.winW = rightEdge - constrainedX
                    task1.updateLayouts()
                    if (realtime) task1.applyBounds(false)
                    updateHandlePosition(constrainedX)
                }
            } else {
                if (dockSide == 3) { // Top docked, handle is on the Bottom
                    val minY = task1.winT + (200 * density).toInt()
                    val maxY = safe.bottom - (100 * density).toInt()
                    val constrainedY = currentCoord.coerceIn(minY, maxY.coerceAtLeast(minY))
                    currentSplitCoord = constrainedY
                    
                    task1.winH = constrainedY - task1.winT
                    task1.updateLayouts()
                    if (realtime) task1.applyBounds(false)
                    updateHandlePosition(constrainedY)
                } else if (dockSide == 4) { // Bottom docked, handle is on the Top
                    val minY = safe.top + (100 * density).toInt()
                    val maxY = (task1.winT + task1.winH) - (200 * density).toInt()
                    val constrainedY = currentCoord.coerceIn(minY, maxY.coerceAtLeast(minY))
                    currentSplitCoord = constrainedY
                    
                    val bottomEdge = task1.winT + task1.winH
                    task1.winT = constrainedY
                    task1.winH = bottomEdge - constrainedY
                    task1.updateLayouts()
                    if (realtime) task1.applyBounds(false)
                    updateHandlePosition(constrainedY)
                }
            }
        }
    }

    private fun updateHandlePosition(coord: Int) {
        handleView?.let { v ->
            val lp = getSmartBounds(coord)
            try {
                wm.updateViewLayout(v, lp)
            } catch (e: Exception) {}
        }
    }

    private fun checkOverlapWithTopTasks(coord: Int): Boolean {
        if (isDragging) return false
        
        val lp = getSmartBounds(coord)
        val handleRect = android.graphics.Rect(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height)
        
        val task1Index = service.sortedTaskIds.indexOf(task1.taskId)
        val task2Index = task2?.let { service.sortedTaskIds.indexOf(it.taskId) } ?: Int.MAX_VALUE
        val lowestDockedIndex = Math.min(if (task1Index == -1) Int.MAX_VALUE else task1Index, if (task2Index == -1) Int.MAX_VALUE else task2Index)
        if (lowestDockedIndex == Int.MAX_VALUE) return false
        
        val myDisplayId = task1.currentDisplayId
        for (other in service.overlays.values) {
            if (other.currentDisplayId == myDisplayId && other.taskId != task1.taskId && other.taskId != task2?.taskId) {
                if (!other.isDocked) {
                    val otherIndex = service.sortedTaskIds.indexOf(other.taskId)
                    if (otherIndex != -1 && otherIndex < lowestDockedIndex) {
                        val otherRect = android.graphics.Rect(other.winL, other.winT, other.winL + other.winW, other.winT + other.winH)
                        if (android.graphics.Rect.intersects(handleRect, otherRect)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun syncPosition(coord: Int) {
        if (!isDragging) {
            currentSplitCoord = coord
            val overlapped = checkOverlapWithTopTasks(coord)
            handleView?.visibility = if (overlapped || isTemporarilyHidden) View.GONE else View.VISIBLE
            updateHandlePosition(coord)
        }
    }

    private fun showCapsule(show: Boolean) {
        val targetAlpha = if (show) 1f else 0.4f
        val defaultCapsuleThickness = (6 * density).toInt()
        val activeCapsuleThickness = (8 * density).toInt()
        val targetThickness = if (show) activeCapsuleThickness else defaultCapsuleThickness
        
        capsuleView?.alpha = targetAlpha
            
        capsuleView?.let { v ->
            val lp = v.layoutParams as FrameLayout.LayoutParams
            if (isVertical) {
                lp.width = targetThickness
            } else {
                lp.height = targetThickness
            }
            v.layoutParams = lp
        }
    }

    fun hide() {
        handleView?.let { v ->
            if (v.parent != null) {
                try {
                    wm.removeView(v)
                } catch (e: Exception) {}
            }
        }
        handleView = null
        capsuleView = null
    }
}

class SnappingSensitivityGuideView(context: Context, private val density: Float) : android.view.View(context) {
    var sizePx = 0
        set(value) {
            field = value
            invalidate()
        }
    
    private val fillPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val linePaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f * density
        isAntiAlias = true
    }
    
    private val cornerPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    private fun getAccentColor(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getColor(android.R.color.system_accent1_600)
            } else {
                ThemeManager.getAccentColor(context, android.graphics.Color.parseColor("#8E2DE2"))
            }
        } catch (e: Exception) {
            ThemeManager.getAccentColor(context, android.graphics.Color.parseColor("#8E2DE2"))
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        if (sizePx <= 0) return
        
        val w = width.toFloat()
        val h = height.toFloat()
        val s = sizePx.toFloat()
        
        val accent = getAccentColor()
        val r = android.graphics.Color.red(accent)
        val g = android.graphics.Color.green(accent)
        val b = android.graphics.Color.blue(accent)
        
        fillPaint.color = android.graphics.Color.argb(35, r, g, b)
        linePaint.color = android.graphics.Color.argb(160, r, g, b)
        cornerPaint.color = android.graphics.Color.argb(90, r, g, b)
        
        // 1. Draw edge snapping zone overlay around all four sides
        // Top edge band
        canvas.drawRect(0f, 0f, w, s, fillPaint)
        // Bottom edge band
        canvas.drawRect(0f, h - s, w, h, fillPaint)
        // Left edge band (excluding top/bottom to avoid double drawing)
        canvas.drawRect(0f, s, s, h - s, fillPaint)
        // Right edge band (excluding top/bottom)
        canvas.drawRect(w - s, s, w, h - s, fillPaint)
        
        // 2. Draw thin inner guideline border threshold
        canvas.drawRect(s, s, w - s, h - s, linePaint)
        
        // 3. Draw premium rounded-corner indicators for corner-snapping zones
        val cornerRadius = 12f * density
        
        // Top Left Corner
        canvas.drawRoundRect(0f, 0f, s, s, cornerRadius, cornerRadius, cornerPaint)
        // Top Right Corner
        canvas.drawRoundRect(w - s, 0f, w, s, cornerRadius, cornerRadius, cornerPaint)
        // Bottom Left Corner
        canvas.drawRoundRect(0f, h - s, s, h, cornerRadius, cornerRadius, cornerPaint)
        // Bottom Right Corner
        canvas.drawRoundRect(w - s, h - s, w, h, cornerRadius, cornerRadius, cornerPaint)
    }
}
