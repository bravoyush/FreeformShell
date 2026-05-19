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
