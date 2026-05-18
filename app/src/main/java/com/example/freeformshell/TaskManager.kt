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

    fun getCombinedTaskState(): CombinedTaskState {
        val foundTasks = mutableListOf<AppTask>()
        val boundsMap = mutableMapOf<Int, TaskBounds>()
        
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
            .sortedWith(compareByDescending<AppTask> { it.isVisible }.thenByDescending { it.isFreeform })
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
}
