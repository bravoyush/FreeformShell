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

class FreeformOverlayService : Service() {

    private var guideView: View? = null

    internal val overlays = java.util.concurrent.ConcurrentHashMap<Int, DragResizeOverlay>()
    private val bubbles = java.util.concurrent.ConcurrentHashMap<Int, MinimizedBubble>()
    private val minimizedTasks = java.util.concurrent.CopyOnWriteArraySet<Int>()
    internal val dockedTasks = java.util.concurrent.CopyOnWriteArraySet<Int>()
    private val dockedPackages = java.util.concurrent.CopyOnWriteArraySet<String>()
    private val minimizedTaskData = java.util.concurrent.ConcurrentHashMap<Int, TaskBounds>()
    private val activeHandles = java.util.concurrent.ConcurrentHashMap<String, SplitResizeHandle>()
    // Cache of all freeform tasks we've ever seen — survives across monitor cycles
    private val knownFreeformTasks = java.util.concurrent.ConcurrentHashMap<Int, Pair<String, String?>>() // taskId -> Pair(Pkg, Component)
    private val gracePeriodTasks = java.util.concurrent.ConcurrentHashMap<Int, Int>() // taskId -> missedCycles
    private val missingTaskCycles = java.util.concurrent.ConcurrentHashMap<Int, Int>() // taskId -> missedCycles
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastTopTaskId = -1
    private var lastTopPackage: String? = null
    private var systemDefaultDensity = -1
    private val TAG = "FreeformOverlayService"

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            monitorTasks()
            handler.postDelayed(this, 2500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_SHOW_ALL" -> bringAllToFront()
            "ACTION_EXIT" -> stopSelf()
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
            else -> {
                isRunning = true
                handler.post(monitorRunnable)
            }
        }
        return START_STICKY
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
            val autoSnap = ThemeManager.getWorkspaceAutoSnap(this@FreeformOverlayService)
            handler.post { Toast.makeText(this, "Restoring workspace...", Toast.LENGTH_SHORT).show() }
            Thread {
                ShellExecutor.executeCommand("cmd activity set-resizable 1")
                group.apps.forEachIndexed { index, app ->
                    val component = app.component ?: (packageManager.getLaunchIntentForPackage(app.packageName)?.component?.flattenToShortString())
                    if (component != null) {
                        if (autoSnap) {
                            FreeformOverlayService.setIntendedBounds(app.packageName, app.bounds, resolvedDisplayId)
                        }
                        ShellExecutor.relaunchFreeformTask(-1, component) // Use -1 to force new launch if needed
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

        isRunning = true
        handler.post(monitorRunnable)
    }

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            // Safety Valve: Stop monitoring or other lightweight cleanup
            lastTopPackage = null
        }
    }

    companion object {
        private var instance: FreeformOverlayService? = null

        fun requestRefresh() {
            instance?.monitorTasks()
        }

        internal val intendedBounds = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Rect>()
        internal val intendedDisplayId = java.util.concurrent.ConcurrentHashMap<String, Int>()
        private val manualBlacklist = java.util.concurrent.CopyOnWriteArraySet<String>()

        fun setIntendedBounds(packageName: String, rect: android.graphics.Rect, displayId: Int = 0) {
            intendedBounds[packageName] = rect
            intendedDisplayId[packageName] = displayId
        }

        fun toggleBlacklist(packageName: String) {
            val lower = packageName.lowercase()
            if (manualBlacklist.contains(lower)) {
                manualBlacklist.remove(lower)
            } else {
                manualBlacklist.add(lower)
            }
        }

        fun isBlacklisted(packageName: String): Boolean {
            val lower = packageName.lowercase()
            return manualBlacklist.any { lower.contains(it) }
        }

        fun showDockGuide(displayId: Int, pos: Int, size: Int) {
            instance?.updateDockGuide(displayId, pos, size)
        }

        fun hideDockGuide() {
            instance?.removeDockGuide()
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
            val density = inst.resources.displayMetrics.density
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

    private val baseBlacklist = setOf(
        "fallback",
        "taskbar",
        "launcher",
        "systemui",
        "freeformshell",
        "documentsui",
        "externalstorage"
    )

    private fun monitorTasks() {
        Thread {
            try {
                val state = TaskManager.getCombinedTaskState()
                val tasks = state.tasks
                val taskBoundsMap = state.boundsMap
                val activeTaskIds = mutableSetOf<Int>()
                
                // Register any freeform tasks we find into the cache
                for ((taskId, info) in taskBoundsMap) {
                    if (info.windowingMode == 5 && info.packageName != null) {
                        knownFreeformTasks[taskId] = Pair(info.packageName, info.activityName)
                    }
                }
                
                // Build a merged task list: start with detected tasks, then add
                // any cached freeform tasks that weren't detected this cycle
                val detectedTaskIds = tasks.map { it.taskId }.toSet()
                
                // Safety valve: Track missed cycles for cached tasks to clean up closed/killed apps
                for (cachedId in knownFreeformTasks.keys) {
                    if (cachedId !in detectedTaskIds) {
                        val missed = missingTaskCycles.getOrDefault(cachedId, 0)
                        if (missed >= 2) { 
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

                val mergedTasks = tasks.toMutableList()
                for ((cachedId, cachedData) in knownFreeformTasks) {
                    if (cachedId !in detectedTaskIds) {
                        mergedTasks.add(AppTask(cachedId, cachedData.first, cachedData.second))
                    }
                }

                // Track top-most non-blacklisted task to sync Z-order and clipping
                val nonBlacklistedTasks = mergedTasks.filter { t ->
                    val low = t.packageName.lowercase()
                    !baseBlacklist.any { low.contains(it) } && !isBlacklisted(t.packageName)
                }
                
                val topTask = nonBlacklistedTasks.firstOrNull()
                val currentTopTaskId = topTask?.taskId ?: -1
                
                val forceRelayer = currentTopTaskId != lastTopTaskId
                lastTopTaskId = currentTopTaskId

                val taskDecorBounds = mutableMapOf<Int, android.graphics.Rect>()
                val density = resources.displayMetrics.density
                val borderWidth = (4 * density).toInt()
                val titleBarHeight = (40 * density).toInt()

                for (task in mergedTasks) {
                    val taskInfo = taskBoundsMap[task.taskId]
                    var bounds = taskInfo?.bounds ?: intendedBounds[task.packageName]
                    if (bounds != null) {
                        val displayId = taskInfo?.displayId ?: intendedDisplayId[task.packageName] ?: 0
                        val safe = getSafeAreaRect(displayId)
                        
                        // Requirement: Title bar MUST stay below notification panel (safe.top)
                        // If bounds.top - titleBarHeight is less than safe.top, we shift the decoration down
                        val decorTop = (bounds.top - titleBarHeight).coerceAtLeast(safe.top)
                        
                        taskDecorBounds[task.taskId] = android.graphics.Rect(
                            bounds.left - borderWidth,
                            decorTop,
                            bounds.right + borderWidth,
                            bounds.bottom + borderWidth
                        )
                    }
                }

                for (i in mergedTasks.indices.reversed()) {
                    val task = mergedTasks[i]
                    
                    val taskInfo = taskBoundsMap[task.taskId]
                    if (taskInfo == null) {
                        // Task bounds temporarily missing (e.g. recents gesture)
                        // Keep existing overlays alive with their last known state
                        if (overlays.containsKey(task.taskId) || knownFreeformTasks.containsKey(task.taskId)) {
                            activeTaskIds.add(task.taskId)
                        }
                        continue
                    }
                    val windowMode = taskInfo.windowingMode
                    var displayId = taskInfo.displayId
                    val rawBounds = taskInfo.bounds

                    // If intended for a specific display but OS says 0, trust our intent initially
                    if (displayId == 0 && intendedDisplayId.containsKey(task.packageName)) {
                        displayId = intendedDisplayId[task.packageName] ?: 0
                    }
                    
                    val isFreeformReported = windowMode == 5
                    val isIntendedFreeform = intendedBounds.containsKey(task.packageName)
                    
                    // Pinning Logic with Grace Period:
                    // If the OS reports mode=1 (fullscreen), we give it a grace period
                    // of 3 cycles before we hide the overlay, in case it's a transient state.
                    if (isFreeformReported || isIntendedFreeform) {
                        gracePeriodTasks[task.taskId] = 0 // Reset grace period
                    } else {
                        val currentGrace = gracePeriodTasks.getOrDefault(task.taskId, 0)
                        if (currentGrace < 3) {
                            gracePeriodTasks[task.taskId] = currentGrace + 1
                        } else {
                            handler.post { hideOverlay(task.taskId) }
                            continue
                        }
                    }
                    
                    val isFreeform = (gracePeriodTasks.getOrDefault(task.taskId, 0) < 3) || isFreeformReported || isIntendedFreeform
                    
                    if (!isFreeform) {
                        handler.post { hideOverlay(task.taskId) }
                        continue
                    }

                    Log.v(TAG, "Task ${task.taskId} (${task.packageName}): mode=$windowMode, display=$displayId, visible=${taskInfo?.isVisible}")

                    // Requirement: Don't show overlay if minimized (Bubble Mode)
                    if (minimizedTasks.contains(task.taskId)) {
                        handler.post { hideOverlay(task.taskId) }
                        continue
                    }

                    // Requirement: Don't show overlay if app is not visible on the screen
                    val isTaskVisible = taskInfo?.isVisible ?: true
                    val isAndroid12 = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                    val bypassVisibility = isAndroid12 && isFreeform
                    if (!isTaskVisible && !bypassVisibility) {
                        handler.post { hideOverlay(task.taskId) }
                        continue
                    }

                    // Occlusion Check: If there is a top-most non-blacklisted task on the same display that is fullscreen/maximized, hide the overlay for all other tasks on this display
                    val displayTasks = nonBlacklistedTasks.filter { (taskBoundsMap[it.taskId]?.displayId ?: 0) == displayId }
                    val topTaskOnDisplay = displayTasks.firstOrNull()
                    var isCoveredByFullscreen = false
                    if (topTaskOnDisplay != null && topTaskOnDisplay.taskId != task.taskId) {
                        val topInfo = taskBoundsMap[topTaskOnDisplay.taskId]
                        val topOverlay = overlays[topTaskOnDisplay.taskId]
                        val isTopMaximized = topOverlay?.isMaximized == true
                        val isTopFullscreen = topInfo?.windowingMode == 1
                        
                        val dm = getRealMetricsForDisplay(displayId)
                        val topBounds = topInfo?.bounds
                        val isTopHuge = topBounds != null && topBounds.width() >= (dm.widthPixels * 0.95f) && topBounds.height() >= (dm.heightPixels * 0.95f)
                        
                        if (isTopMaximized || isTopFullscreen || isTopHuge) {
                            isCoveredByFullscreen = true
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
                    
                    // Occluders for THIS task are all tasks that appear BEFORE it in the 'tasks' list
                    val currentOccluders = tasks.take(i).mapNotNull { taskDecorBounds[it.taskId] }
                    
                    // Blacklist checks
                    val lowPkg = task.packageName.lowercase()
                    if (baseBlacklist.any { lowPkg.contains(it) } || isBlacklisted(task.packageName)) {
                        continue
                    }

                    var bounds = taskInfo?.bounds
                    if (bounds == null) bounds = intendedBounds[task.packageName]
                    if (bounds == null) bounds = android.graphics.Rect(100, 150, 900, 1150)

                    // Requirement: On Primary Display (0), if top is 0, it overlaps notification.
                    // Force a shift down if it's reported at 0 but we have a safe area.
                    val safe = getSafeAreaRect(displayId)
                    val existingOverlay = overlays[task.taskId]
                    val isInteracting = existingOverlay?.isInteracting ?: false

                    if (displayId == 0 && bounds.top < safe.top && !isInteracting) {
                        val shift = safe.top - bounds.top
                        val newBounds = android.graphics.Rect(bounds.left, safe.top, bounds.right, bounds.bottom + shift)
                        bounds = newBounds
                        // Trigger a shell resize to match our visual correction
                        val tid = task.taskId
                        if (tid != -1) {
                            Thread { ShellExecutor.resizeTask(tid, bounds.left, bounds.top, bounds.right, bounds.bottom) }.start()
                        }
                    }

                    activeTaskIds.add(task.taskId)
                    handler.post {
                        val existing = overlays[task.taskId]
                        val isFocus = task.taskId == currentTopTaskId
                        
                        if (existing == null || existing.currentDisplayId != displayId) {
                            existing?.hide()
                            val displayId = taskInfo?.displayId ?: 0
                            val safe = getSafeAreaRect(displayId)
                            val decorTop = (bounds.top - titleBarHeight).coerceAtLeast(safe.top)
                            
                            val overlay = DragResizeOverlay(this@FreeformOverlayService, task.taskId, task.packageName, displayId, 
                                onMinimize = { if (isDocked) restoreTask(task.taskId) else minimizeTask(task.taskId) },
                                onClose = { 
                                    knownFreeformTasks.remove(task.taskId)
                                    hideOverlay(task.taskId)
                                    // Actually close the app
                                    Thread { ShellExecutor.forceStopApp(task.packageName, task.taskId) }.start()
                                }
                            )
                            overlay.show()
                            overlay.setDockMode(isDocked)
                            overlay.setActivityName(taskInfo.activityName)
                            overlay.updateFromSystem(bounds.left, bounds.top, bounds.width(), bounds.height(), forcedTitleTop = decorTop)
                            overlay.updateFocus(isFocus, currentOccluders)
                            overlays[task.taskId] = overlay
                        } else {
                            val displayId = taskInfo?.displayId ?: 0
                            val safe = getSafeAreaRect(displayId)
                            val decorTop = (bounds.top - titleBarHeight).coerceAtLeast(safe.top)
                            
                            existing.setDockMode(isDocked)
                            existing.setActivityName(taskInfo.activityName)
                            if (!existing.isInteracting) {
                                // Update title bar position manually if it's hitting the safe area
                                existing.updateFromSystem(bounds.left, bounds.top, bounds.width(), bounds.height(), forcedTitleTop = decorTop)
                                existing.updateFocus(isFocus, currentOccluders)
                                if (forceRelayer) existing.bringToFront()
                            }
                        }
                        
                        // Clear intended bounds once successfully attached to a task
                        if (isFreeformReported) {
                            intendedBounds.remove(task.packageName)
                            intendedDisplayId.remove(task.packageName)
                        }
                    }
                }
                
                handler.post {
                    val toRemove = overlays.keys.filter { it !in activeTaskIds && it !in minimizedTasks }
                    toRemove.forEach { hideOverlay(it) }
                    
                    // Clean up bubbles for tasks that no longer exist in recents
                    val deadBubbles = bubbles.keys.filter { id -> mergedTasks.none { it.taskId == id } }
                    deadBubbles.forEach { id ->
                        bubbles[id]?.hide()
                        bubbles.remove(id)
                        minimizedTasks.remove(id)
                    }

                    // Save current layout to history by display if anything is open
                    val freeformTasks = mergedTasks.filter { it.isFreeform }
                    if (freeformTasks.isNotEmpty()) {
                        val tasksByDisplay = freeformTasks.groupBy { task ->
                            taskBoundsMap[task.taskId]?.displayId ?: 0
                        }
                        tasksByDisplay.forEach { (displayId, displayTasks) ->
                            WorkspaceManager.saveCurrentToHistory(this@FreeformOverlayService, displayId, displayTasks, taskBoundsMap)
                        }
                    }
                    
                    updateSplitHandles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Monitor loop error", e)
            }
        }.start()
    }

    private fun minimizeTask(taskId: Int) {
        val task = TaskManager.getAllTaskBounds()[taskId]
        val pkg = task?.packageName
        val activity = task?.activityName
        
        if (pkg != null && activity != null) {
            minimizedTaskData[taskId] = task
            
            val prefs = getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
            val useBubbles = prefs.getBoolean("bubble_mode", false)

            if (useBubbles) {
                // MODERN MINIMIZE: Move task to back
                ShellExecutor.moveTaskToBack(taskId)
                minimizedTasks.add(taskId)
                
                // Show floating bubble to restore it later
                handler.post {
                    if (!bubbles.containsKey(taskId)) {
                        val bubble = MinimizedBubble(this, taskId, pkg, task.displayId) {
                            restoreTask(it)
                        }
                        bubbles[taskId] = bubble
                        bubble.show()
                    }
                    hideOverlay(taskId)
                }
            } else {
                // DOCK MODE: Scale down to side
                val dockIndex = dockedTasks.size
                val dm = resources.displayMetrics
                val dockW = 260
                val dockH = 340
                val dockX = dm.widthPixels - dockW - 40
                val dockY = 150 + (dockIndex * (dockH + 40))
                
                // Ensure freeform and front before docking
                ShellExecutor.executeCommand("am start -n $activity --windowingMode 5 --activity-reorder-to-front")
                
                handler.postDelayed({
                    ShellExecutor.executeCommand("cmd activity task resize $taskId $dockX $dockY ${dockX + dockW} ${dockY + dockH}")
                    dockedTasks.add(taskId)
                    monitorTasks()
                }, 300)
            }
        }
        
        handler.postDelayed({ monitorTasks() }, 200)
    }

    private fun restoreTask(taskId: Int) {
        val data = minimizedTaskData[taskId]
        val activity = data?.activityName
        val bounds = data?.bounds

        if (activity != null) {
            // Force re-order to front and ensure freeform mode
            ShellExecutor.executeCommand("am start -n $activity --windowingMode 5 --activity-reorder-to-front")
            
            // Restore previous window size
            if (bounds != null) {
                handler.postDelayed({
                    ShellExecutor.executeCommand("cmd activity task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}")
                }, 500)
            }
        }
        
        minimizedTasks.remove(taskId)
        dockedTasks.remove(taskId)
        minimizedTaskData.remove(taskId)
        bubbles[taskId]?.hide()
        bubbles.remove(taskId)
        monitorTasks()
    }

    private fun hideOverlay(taskId: Int) {
        knownFreeformTasks.remove(taskId)
        overlays[taskId]?.hide()
        overlays.remove(taskId)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        isRunning = false
        instance = null
        handler.removeCallbacks(monitorRunnable)
        
        getSharedPreferences("freeform_theme_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        
        try { unregisterReceiver(screenOffReceiver) } catch (e: Exception) {}
            
        overlays.values.forEach { it.hide() }
        overlays.clear()
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
            .setContentTitle("FreeformShell Active")
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
        if (pos == 0 || size == 0) {
            removeDockGuide()
            return
        }

        handler.post {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
            val displayContext = createDisplayContext(targetDisplay)
            val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            if (guideView == null) {
                guideView = View(this).apply {
                    // Semi-transparent blue for better visibility
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
            
            try {
                wm.addView(guideView, params)
            } catch (e: Exception) {}
        }
    }

    private fun removeDockGuide() {
        handler.post {
            guideView?.let {
                val wm = it.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                if (it.parent != null) wm.removeView(it)
                guideView = null
            }
        }
    }

    private fun updateSnapGuide(displayId: Int, rect: android.graphics.Rect) {
        handler.post {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val targetDisplay = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
            val displayContext = createDisplayContext(targetDisplay)
            val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            if (guideView == null) {
                guideView = View(this).apply {
                    background = GradientDrawable().apply {
                        setColor(android.graphics.Color.argb(60, 66, 133, 244))
                        setStroke((3 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#4285F4"))
                        cornerRadius = 16 * resources.displayMetrics.density
                    }
                }
            }

            if (guideView?.parent != null) {
                try {
                    wm.removeView(guideView)
                } catch (e: Exception) {}
            }

            val params = WindowManager.LayoutParams(
                rect.width(),
                rect.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = rect.left
                y = rect.top
            }
            
            try {
                wm.addView(guideView, params)
            } catch (e: Exception) {}
        }
    }

    private fun removeSnapGuide() {
        handler.post {
            guideView?.let { v ->
                if (v.parent != null) {
                    val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    try {
                        wm.removeView(v)
                    } catch (e: Exception) {}
                }
            }
            guideView = null
        }
    }

    private fun findHandleConfigs(displayId: Int): List<HandleConfig> {
        val docked = overlays.values.filter { it.currentDisplayId == displayId && it.isDocked }
        if (docked.isEmpty()) return emptyList()
        
        val safe = getSafeAreaRect(displayId)
        val density = resources.displayMetrics.density
        val threshold = (60 * density).toInt().coerceAtLeast(150)
        
        val configs = mutableListOf<HandleConfig>()
        val pairedHorizontalIds = mutableSetOf<Int>()
        val pairedVerticalIds = mutableSetOf<Int>()
        
        // 1. Search for PAIRED split configurations
        val midMinX = safe.left + safe.width() * 0.2f
        val midMaxX = safe.left + safe.width() * 0.8f
        
        val leftTasks = docked.filter { 
            Math.abs(it.winL - safe.left) < threshold && (it.winL + it.winW) in midMinX.toInt()..midMaxX.toInt()
        }
        val rightTasks = docked.filter { 
            Math.abs((it.winL + it.winW) - safe.right) < threshold && it.winL in midMinX.toInt()..midMaxX.toInt()
        }
        
        for (lt in leftTasks) {
            for (rt in rightTasks) {
                if (lt != rt) {
                    val overlapTop = Math.max(lt.winT, rt.winT)
                    val overlapBottom = Math.min(lt.winT + lt.winH, rt.winT + rt.winH)
                    if (overlapBottom - overlapTop > 100 * density) {
                        // Check if they are almost in contact (gap is within 24dp)
                        val contactThreshold = (24 * density).toInt()
                        val horizontalGap = rt.winL - (lt.winL + lt.winW)
                        if (horizontalGap in -contactThreshold..contactThreshold) {
                            val splitCoord = (lt.winL + lt.winW + rt.winL) / 2
                            configs.add(HandleConfig(true, lt, rt, splitCoord, null, 0))
                            pairedHorizontalIds.add(lt.taskId)
                            pairedHorizontalIds.add(rt.taskId)
                        }
                    }
                }
            }
        }
        
        val midMinY = safe.top + safe.height() * 0.2f
        val midMaxY = safe.top + safe.height() * 0.8f
        
        val topTasks = docked.filter { 
            Math.abs(it.winT - (safe.top + it.titleBarHeight)) < threshold && (it.winT + it.winH) in midMinY.toInt()..midMaxY.toInt()
        }
        val bottomTasks = docked.filter { 
            Math.abs((it.winT + it.winH) - safe.bottom) < threshold && it.winT in midMinY.toInt()..midMaxY.toInt()
        }
        
        for (tt in topTasks) {
            for (bt in bottomTasks) {
                if (tt != bt) {
                    val overlapLeft = Math.max(tt.winL, bt.winL)
                    val overlapRight = Math.min(tt.winL + tt.winW, bt.winL + bt.winW)
                    if (overlapRight - overlapLeft > 100 * density) {
                        // Check if they are almost in contact (gap is within 24dp)
                        val contactThreshold = (24 * density).toInt()
                        val verticalGap = bt.winT - (tt.winT + tt.winH)
                        if (verticalGap in -contactThreshold..contactThreshold) {
                            val splitCoord = (tt.winT + tt.winH + bt.winT) / 2
                            configs.add(HandleConfig(false, tt, bt, splitCoord, null, 0))
                            pairedVerticalIds.add(tt.taskId)
                            pairedVerticalIds.add(bt.taskId)
                        }
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
        
        return configs
    }

    private fun updateSplitHandles() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        val validKeys = mutableSetOf<String>()
        
        for (display in displays) {
            val displayId = display.displayId
            val configs = findHandleConfigs(displayId)
            
            for (config in configs) {
                val key = if (config.task2 != null) {
                    "paired_${config.task1.taskId}_${config.task2.taskId}_${config.isVertical}"
                } else {
                    "single_${config.task1.taskId}_${config.dockSide}"
                }
                validKeys.add(key)
                
                val existing = activeHandles[key]
                if (existing != null) {
                    if (existing.isDragging) continue
                    existing.syncPosition(config.splitCoord)
                } else {
                    val handle = SplitResizeHandle(
                        this, 
                        createDisplayContext(display), 
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
        
    private var startX = 0f
    private var startY = 0f
    private var startCoord = 0
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
                    showCapsule(true)
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
                            
                            if (Math.abs((t.preInteractL + t.preInteractW) - startCoord) < contactThreshold) {
                                activeLeftTasks.add(t)
                            } else if (Math.abs(t.preInteractL - startCoord) < contactThreshold) {
                                activeRightTasks.add(t)
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
                            
                            if (Math.abs((t.preInteractT + t.preInteractH) - startCoord) < contactThreshold) {
                                activeTopTasks.add(t)
                            } else if (Math.abs(t.preInteractT - startCoord) < contactThreshold) {
                                activeBottomTasks.add(t)
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
                        val currentCoord = if (isVertical) event.rawX.toInt() else event.rawY.toInt()
                        resizeSplit(currentCoord)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    showCapsule(false)
                    
                    val allDocked = service.overlays.values.filter { 
                        it.currentDisplayId == task1.currentDisplayId && it.isDocked 
                    }
                    for (t in allDocked) {
                        t.isResizing = false
                        t.isInteracting = false
                        t.applyBounds(true)
                        t.updateLayouts()
                    }
                    
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
                
                val constrainedX = currentCoord.coerceIn(minX, maxX)
                
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
                
                val constrainedY = currentCoord.coerceIn(minY, maxY)
                
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
                    val constrainedX = currentCoord.coerceIn(minX, maxX)
                    
                    task1.winW = constrainedX - task1.winL
                    task1.updateLayouts()
                    if (realtime) task1.applyBounds(false)
                    updateHandlePosition(constrainedX)
                } else if (dockSide == 2) { // Right docked, handle is on the Left
                    val minX = safe.left + (100 * density).toInt()
                    val maxX = (task1.winL + task1.winW) - (350 * density).toInt()
                    val constrainedX = currentCoord.coerceIn(minX, maxX)
                    
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
                    val constrainedY = currentCoord.coerceIn(minY, maxY)
                    
                    task1.winH = constrainedY - task1.winT
                    task1.updateLayouts()
                    if (realtime) task1.applyBounds(false)
                    updateHandlePosition(constrainedY)
                } else if (dockSide == 4) { // Bottom docked, handle is on the Top
                    val minY = safe.top + (100 * density).toInt()
                    val maxY = (task1.winT + task1.winH) - (200 * density).toInt()
                    val constrainedY = currentCoord.coerceIn(minY, maxY)
                    
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

    fun syncPosition(coord: Int) {
        if (!isDragging) {
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
