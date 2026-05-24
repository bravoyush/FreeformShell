package com.example.freeformshell

import android.graphics.Rect
import android.util.Log

data class AppTask(val taskId: Int, val packageName: String, val activityName: String? = null, val isVisible: Boolean = true, val isFreeform: Boolean = false)
data class TaskBounds(
    val bounds: Rect, 
    val displayId: Int, 
    val windowingMode: Int, 
    val packageName: String? = null,
    val activityName: String? = null, 
    val isVisible: Boolean = true
)

data class CombinedTaskState(val tasks: List<AppTask>, val boundsMap: Map<Int, TaskBounds>)

object TaskManager {
    private const val TAG = "TaskManager"

    private val taskLeashes = java.util.concurrent.ConcurrentHashMap<Int, android.view.SurfaceControl>()
    private var isOrganizerRegistered = false
    private val registrationLock = Any()

    fun getTaskLeash(taskId: Int): android.view.SurfaceControl? {
        registerTaskOrganizerIfNeeded()
        return taskLeashes[taskId]
    }

    private fun registerTaskOrganizerIfNeeded() {
        if (isOrganizerRegistered) return
        synchronized(registrationLock) {
            if (isOrganizerRegistered) return
            try {
                if (rikka.shizuku.Shizuku.pingBinder()) {
                    val atmBinder = rikka.shizuku.SystemServiceHelper.getSystemService("activity_task")
                    val shizukuBinder = rikka.shizuku.ShizukuBinderWrapper(atmBinder)
                    val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                    val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                    val activityTaskManager = asInterfaceMethod.invoke(null, shizukuBinder)
                    
                    val getTaskOrganizerControllerMethod = activityTaskManager.javaClass.getMethod("getTaskOrganizerController")
                    val rawController = getTaskOrganizerControllerMethod.invoke(activityTaskManager)
                    
                    if (rawController != null) {
                        val controllerBinder = rawController as? android.os.IBinder
                            ?: (rawController.javaClass.getMethod("asBinder").invoke(rawController) as android.os.IBinder)
                        val wrappedControllerBinder = rikka.shizuku.ShizukuBinderWrapper(controllerBinder)
                        
                        val controllerStubClass = Class.forName("android.window.ITaskOrganizerController\$Stub")
                        val controllerAsInterfaceMethod = controllerStubClass.getMethod("asInterface", android.os.IBinder::class.java)
                        val controller = controllerAsInterfaceMethod.invoke(null, wrappedControllerBinder)
                        
                        // Retrieve transaction codes reflectively to support Android 11 to 15+
                        val iTaskOrganizerStubClass = Class.forName("android.window.ITaskOrganizer\$Stub")
                        
                        val onTaskAppearedField = iTaskOrganizerStubClass.getDeclaredField("TRANSACTION_onTaskAppeared").apply { isAccessible = true }
                        val onTaskAppearedCode = onTaskAppearedField.get(null) as Int
                        
                        val onTaskVanishedField = iTaskOrganizerStubClass.getDeclaredField("TRANSACTION_onTaskVanished").apply { isAccessible = true }
                        val onTaskVanishedCode = onTaskVanishedField.get(null) as Int
                        
                        // Create our custom Binder implementing ITaskOrganizer
                        val myBinder = object : android.os.Binder() {
                            override fun getInterfaceDescriptor(): String {
                                return "android.window.ITaskOrganizer"
                            }
                            
                            override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                                try {
                                    if (code == onTaskAppearedCode) {
                                        data.enforceInterface("android.window.ITaskOrganizer")
                                        
                                        // Read RunningTaskInfo
                                        val hasTaskInfo = data.readInt() != 0
                                        val taskInfo = if (hasTaskInfo) {
                                            android.app.ActivityManager.RunningTaskInfo.CREATOR.createFromParcel(data)
                                        } else {
                                            null
                                        }
                                        
                                        // Read SurfaceControl
                                        val hasSurfaceControl = data.readInt() != 0
                                        val leash = if (hasSurfaceControl) {
                                            android.view.SurfaceControl.CREATOR.createFromParcel(data)
                                        } else {
                                            null
                                        }
                                        
                                        if (taskInfo != null && leash != null) {
                                            taskLeashes[taskInfo.taskId] = leash
                                            Log.d(TAG, "onTaskAppeared callback: taskId=${taskInfo.taskId}, leash=$leash")
                                        }
                                        reply?.writeNoException()
                                        return true
                                    } else if (code == onTaskVanishedCode) {
                                        data.enforceInterface("android.window.ITaskOrganizer")
                                        
                                        // Read RunningTaskInfo
                                        val hasTaskInfo = data.readInt() != 0
                                        val taskInfo = if (hasTaskInfo) {
                                            android.app.ActivityManager.RunningTaskInfo.CREATOR.createFromParcel(data)
                                        } else {
                                            null
                                        }
                                        
                                        if (taskInfo != null) {
                                            taskLeashes.remove(taskInfo.taskId)
                                            Log.d(TAG, "onTaskVanished callback: taskId=${taskInfo.taskId}")
                                        }
                                        reply?.writeNoException()
                                        return true
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in TaskOrganizer transact", e)
                                }
                                return super.onTransact(code, data, reply, flags)
                            }
                        }
                        
                        // Register using the controller: registerOrganizer(ITaskOrganizer organizer)
                        val registerOrganizerMethod = controller.javaClass.getMethod(
                            "registerOrganizer",
                            Class.forName("android.window.ITaskOrganizer")
                        )
                        
                        val initialTasks = registerOrganizerMethod.invoke(controller, myBinder) as? List<*>
                        if (initialTasks != null) {
                            for (infoObj in initialTasks) {
                                if (infoObj == null) continue
                                try {
                                    val getTaskInfoMethod = infoObj.javaClass.getMethod("getTaskInfo")
                                    val getLeashMethod = infoObj.javaClass.getMethod("getLeash")
                                    val taskInfo = getTaskInfoMethod.invoke(infoObj) as android.app.ActivityManager.RunningTaskInfo
                                    val leash = getLeashMethod.invoke(infoObj) as android.view.SurfaceControl
                                    taskLeashes[taskInfo.taskId] = leash
                                    Log.d(TAG, "Initial task loaded: taskId=${taskInfo.taskId}, leash=$leash")
                                } catch (ex: Exception) {
                                    Log.e(TAG, "Error processing initial task info", ex)
                                }
                            }
                        }
                        
                        isOrganizerRegistered = true
                        Log.d(TAG, "Successfully registered ITaskOrganizer dynamically via Shizuku!")
                    } else {
                        Log.w(TAG, "ITaskOrganizerController is null!")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register dynamic TaskOrganizer", e)
            }
        }
    }

    init {
        ShellExecutor.bypassHiddenApiRestrictions()
    }

    private fun isTaskVisible(taskInfo: Any): Boolean {
        // 1. Try to call isVisible() method
        try {
            val method = taskInfo.javaClass.getMethod("isVisible")
            return method.invoke(taskInfo) as Boolean
        } catch (e: Exception) {}

        // 2. Try to get isVisible field
        try {
            val field = taskInfo.javaClass.getField("isVisible")
            return field.get(taskInfo) as Boolean
        } catch (e: Exception) {}

        // 3. Fallback: check if standard TaskInfo has the field
        try {
            val field = taskInfo.javaClass.getDeclaredField("isVisible").apply { isAccessible = true }
            return field.get(taskInfo) as Boolean
        } catch (e: Exception) {}

        return true // Default fallback
    }

    fun getCombinedTaskState(): CombinedTaskState {
        val foundTasks = mutableListOf<AppTask>()
        val boundsMap = mutableMapOf<Int, TaskBounds>()

        try {
            if (rikka.shizuku.Shizuku.pingBinder()) {
                val atmBinder = rikka.shizuku.SystemServiceHelper.getSystemService("activity_task")
                val shizukuBinder = rikka.shizuku.ShizukuBinderWrapper(atmBinder)
                val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                val activityTaskManager = asInterfaceMethod.invoke(null, shizukuBinder)

                val getTasksMethod = activityTaskManager.javaClass.getMethod(
                    "getTasks",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val rawTasks = getTasksMethod.invoke(activityTaskManager, 100, false, false, -1) as List<*>

                for (taskObj in rawTasks) {
                    if (taskObj == null) continue
                    val taskClass = taskObj.javaClass

                    val taskId = taskClass.getField("taskId").getInt(taskObj)
                    if (taskId <= 0) continue

                    val topActivity = taskClass.getField("topActivity").get(taskObj) as? android.content.ComponentName
                    val realActivity = taskClass.getField("realActivity").get(taskObj) as? android.content.ComponentName

                    val packageName = topActivity?.packageName ?: realActivity?.packageName ?: ""
                    if (packageName.isEmpty()) continue

                    val activityName = topActivity?.className ?: realActivity?.className

                    val isVisible = isTaskVisible(taskObj)

                    // Get bounds and windowingMode from configuration
                    val configuration = taskClass.getField("configuration").get(taskObj)
                    val windowConfiguration = configuration.javaClass.getField("windowConfiguration").get(configuration)

                    val bounds = windowConfiguration.javaClass.getMethod("getBounds").invoke(windowConfiguration) as Rect
                    val windowingMode = windowConfiguration.javaClass.getMethod("getWindowingMode").invoke(windowConfiguration) as Int

                    val isFreeform = (windowingMode == 5)
                    val displayId = try {
                        taskClass.getField("displayId").getInt(taskObj)
                    } catch (e: Exception) {
                        0
                    }

                    foundTasks.add(AppTask(taskId, packageName, activityName, isVisible = isVisible, isFreeform = isFreeform))
                    boundsMap[taskId] = TaskBounds(bounds, displayId, windowingMode, packageName, activityName, isVisible)
                }

                if (foundTasks.isNotEmpty()) {
                    return CombinedTaskState(foundTasks, boundsMap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query tasks via direct Binder call, falling back to dumpsys", e)
        }
        
        val idRegex = "(?i)(?:Task.*#|Task=|taskId=)(\\d+)".toRegex()
        val pkgRegex = "(?i)(?:realActivity=|A=[0-9]+:|packageName=)([a-zA-Z0-9._]+)".toRegex()

        try {
            // 1. Primary source: dumpsys activity activities (Filtered for speed)
            val activitiesOut = ShellExecutor.exec("dumpsys activity activities | grep -E \"Task|bounds|mBounds|mode|windowingMode|visible|mVisible|mResumed|state=RESUMED|Display|displayId|ACTIVITY|ActivityRecord|realActivity|A=|packageName\"")
            if (!activitiesOut.contains("Unknown command", ignoreCase = true)) {
                val lines = activitiesOut.split("\n")
                var currentTaskId = -1
                var currentPkg = ""
                var isVisible = false
                var isFreeform = false
                var currentDisplayId = 0
                var currentActivity: String? = null

                for (line in lines) {
                    val trimmed = line.trim()
                    
                    if (trimmed.contains("Display #") || trimmed.contains("displayId=")) {
                        val displayMatch = "(?i)(?:Display #|displayId=)(\\d+)".toRegex().find(trimmed)
                        if (displayMatch != null) {
                            currentDisplayId = displayMatch.groupValues[1].toInt()
                        }
                    }

                    val idMatch = idRegex.find(trimmed)
                    if (idMatch != null) {
                        if (currentTaskId != -1 && currentPkg.isNotEmpty()) {
                            foundTasks.add(AppTask(currentTaskId, currentPkg, currentActivity, isVisible = isVisible, isFreeform = isFreeform))
                        }
                        currentTaskId = idMatch.groupValues[1].toInt()
                        currentPkg = ""
                        isVisible = false
                        isFreeform = false
                        currentActivity = null
                    }

                    if (currentTaskId != -1) {
                        val pkgMatch = pkgRegex.find(trimmed)
                        if (pkgMatch != null && currentPkg.isEmpty()) {
                            currentPkg = pkgMatch.groupValues[1]
                        }

                        val actRecordMatch = "ActivityRecord\\{[a-fA-F0-9]+\\s+u[0-9]+\\s+([^\\s}]+)".toRegex().find(trimmed)
                        if (actRecordMatch != null) {
                            val fullComponent = actRecordMatch.groupValues[1]
                            currentActivity = fullComponent
                            if (currentPkg.isEmpty()) {
                                currentPkg = fullComponent.substringBefore("/")
                            }
                        }

                        if (trimmed.contains("ACTIVITY", true)) {
                            // Capture the full component name for relaunching
                            val compMatch = "ACTIVITY\\s+([^\\s]+)".toRegex().find(trimmed)
                            if (compMatch != null) {
                                currentActivity = compMatch.groupValues[1]
                            }
                        }
                        
                        if (trimmed.contains("visible=true", true) || 
                            trimmed.contains("mVisible=true", true) || 
                            trimmed.contains("mResumed=true", true) ||
                            trimmed.contains("state=RESUMED", true)) {
                            isVisible = true
                        }
                        
                        if (trimmed.contains("mode=freeform", true) || 
                            trimmed.contains("windowingMode=5", true)) {
                            isFreeform = true
                        }

                        if (trimmed.contains("bounds", true) || trimmed.contains("mBounds", true)) {
                            val rectMatch = "Rect\\(\\s*(-?\\d+),\\s*(-?\\d+)\\s*-\\s*(-?\\d+),\\s*(-?\\d+)\\s*\\)".toRegex().find(trimmed)
                            val arrayMatch = "\\[\\s*(-?\\d+),\\s*(-?\\d+)\\s*\\]\\[\\s*(-?\\d+),\\s*(-?\\d+)\\s*\\]".toRegex().find(trimmed)
                            val match = rectMatch ?: arrayMatch
                            if (match != null) {
                                try {
                                    val l = match.groupValues[1].toInt()
                                    val t = match.groupValues[2].toInt()
                                    val r = match.groupValues[3].toInt()
                                    val b = match.groupValues[4].toInt()
                                    val rect = Rect(l, t, r, b)
                                    if (rect.width() > 0 && rect.height() > 0 && !boundsMap.containsKey(currentTaskId)) {
                                        boundsMap[currentTaskId] = TaskBounds(rect, currentDisplayId, if (isFreeform) 5 else 1, currentPkg, currentActivity, isVisible)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Rect parsing error", e)
                                }
                            }
                        }
                    }
                }
                if (currentTaskId != -1 && currentPkg.isNotEmpty()) {
                    foundTasks.add(AppTask(currentTaskId, currentPkg, currentActivity, isVisible = isVisible, isFreeform = isFreeform))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse task state", e)
        }

        val finalTasks = foundTasks
            .distinctBy { it.taskId }
            .filter { it.taskId > 0 }

        val finalBoundsMap = boundsMap.toMutableMap()
        for (task in finalTasks) {
            val boundsInfo = finalBoundsMap[task.taskId]
            if (boundsInfo != null) {
                finalBoundsMap[task.taskId] = boundsInfo.copy(
                    isVisible = task.isVisible,
                    packageName = task.packageName,
                    activityName = task.activityName
                )
            }
        }

        return CombinedTaskState(finalTasks, finalBoundsMap)
    }

    fun getRecentTasks(): List<AppTask> = getCombinedTaskState().tasks
    fun getAllTaskBounds(): Map<Int, TaskBounds> = getCombinedTaskState().boundsMap

    fun minimizeTaskSurface(taskId: Int): Boolean {
        try {
            val leash = getTaskLeash(taskId)
            if (leash != null && leash.isValid) {
                val transaction = android.view.SurfaceControl.Transaction()
                val hideMethod = transaction.javaClass.getMethod("hide", android.view.SurfaceControl::class.java)
                val setAlphaMethod = transaction.javaClass.getMethod("setAlpha", android.view.SurfaceControl::class.java, Float::class.javaPrimitiveType)
                val setPositionMethod = transaction.javaClass.getMethod("setPosition", android.view.SurfaceControl::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                
                hideMethod.invoke(transaction, leash)
                setAlphaMethod.invoke(transaction, leash, 0f)
                setPositionMethod.invoke(transaction, leash, 100000f, 100000f)
                transaction.apply()
                
                Log.d(TAG, "Successfully minimized task surface reflectively for taskId $taskId")
                return true
            } else {
                Log.w(TAG, "Cannot minimize task surface: leash is null or invalid for taskId $taskId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error minimizing task surface reflectively for taskId $taskId", e)
        }
        return false
    }

    fun restoreTaskSurface(taskId: Int): Boolean {
        try {
            val leash = getTaskLeash(taskId)
            if (leash != null && leash.isValid) {
                val transaction = android.view.SurfaceControl.Transaction()
                val showMethod = transaction.javaClass.getMethod("show", android.view.SurfaceControl::class.java)
                val setAlphaMethod = transaction.javaClass.getMethod("setAlpha", android.view.SurfaceControl::class.java, Float::class.javaPrimitiveType)
                val setPositionMethod = transaction.javaClass.getMethod("setPosition", android.view.SurfaceControl::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                
                showMethod.invoke(transaction, leash)
                setAlphaMethod.invoke(transaction, leash, 1f)
                setPositionMethod.invoke(transaction, leash, 0f, 0f)
                transaction.apply()
                
                Log.d(TAG, "Successfully restored task surface reflectively for taskId $taskId")
                return true
            } else {
                Log.w(TAG, "Cannot restore task surface: leash is null or invalid for taskId $taskId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring task surface reflectively for taskId $taskId", e)
        }
        return false
    }

    fun getFocusedPackage(): String {
        try {
            val state = getCombinedTaskState()
            for (task in state.tasks) {
                if (task.isVisible && task.packageName.isNotEmpty() && task.packageName != "com.example.freeformshell") {
                    return task.packageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get focused package from task state", e)
        }

        try {
            val res = ShellExecutor.executeCommandWithResult("dumpsys window")
            val out = res.first
            if (out.isEmpty()) return ""
            
            val lines = out.split("\n")
            val regex = "(?i)(?:mCurrentFocus|mFocusedWindow|mFocusedApp).*?\\{[a-fA-F0-9]+\\s+u[0-9]+\\s+([^/\\s}]+)".toRegex()
            
            for (line in lines) {
                if (line.contains("mCurrentFocus") || line.contains("mFocusedWindow") || line.contains("mFocusedApp")) {
                    val match = regex.find(line)
                    if (match != null) {
                        val pkg = match.groupValues[1].trim()
                        if (pkg.isNotEmpty() && pkg != "com.example.freeformshell" && pkg != "null") {
                            return pkg
                        }
                    }
                }
            }
            
            return ""
        } catch (e: Exception) {
            return ""
        }
    }
}
