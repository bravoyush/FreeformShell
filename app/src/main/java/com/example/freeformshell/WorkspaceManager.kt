package com.example.freeformshell

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

data class WorkspaceApp(val packageName: String, val component: String?, val bounds: Rect)
data class WorkspaceGroup(val apps: List<WorkspaceApp>, val displayId: Int, val timestamp: Long = System.currentTimeMillis())

object WorkspaceManager {
    private const val PREFS_NAME = "workspaces"
    private const val KEY_HISTORY = "history"
    private const val KEY_FAVORITE = "favorite"

    fun saveCurrentToHistory(context: Context, displayId: Int, tasks: List<AppTask>, boundsMap: Map<Int, TaskBounds>) {
        val baseBlacklist = emptySet<String>()
        val apps = tasks.filter { task ->
            task.isFreeform &&
            !baseBlacklist.any { task.packageName.lowercase().contains(it) } &&
            !FreeformOverlayService.isBlacklisted(context, task.packageName)
        }.mapNotNull { task ->
            val bounds = boundsMap[task.taskId]?.bounds ?: return@mapNotNull null
            WorkspaceApp(task.packageName, task.activityName, bounds)
        }
        if (apps.isEmpty()) return
        
        val newGroup = WorkspaceGroup(apps, displayId)
        val history = getHistory(context).toMutableList()
        
        // Don't save duplicates (same app list)
        if (history.any { it.apps.map { a -> a.packageName }.toSet() == apps.map { a -> a.packageName }.toSet() }) return
        
        history.add(0, newGroup)
        val limited = history.take(8)
        
        saveHistory(context, limited)
    }

    fun setFavorite(context: Context, group: WorkspaceGroup) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAVORITE, groupToJson(group).toString()).apply()
        notifyWidgetUpdate(context)
    }

    fun removeFavorite(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_FAVORITE).apply()
        notifyWidgetUpdate(context)
    }

    fun getFavorite(context: Context): WorkspaceGroup? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FAVORITE, null) ?: return null
        return jsonToGroup(JSONObject(json))
    }

    fun getHistory(context: Context): List<WorkspaceGroup> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, "[]")
        val array = JSONArray(json)
        val result = mutableListOf<WorkspaceGroup>()
        for (i in 0 until array.length()) {
            result.add(jsonToGroup(array.getJSONObject(i)))
        }
        return result
    }

    fun saveHistory(context: Context, history: List<WorkspaceGroup>) {
        val array = JSONArray()
        history.forEach { array.put(groupToJson(it)) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        notifyWidgetUpdate(context)
    }

    private fun notifyWidgetUpdate(context: Context) {
        try {
            val intent = android.content.Intent(context, FreeformWidget::class.java).apply {
                action = "ACTION_REFRESH"
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {}
    }

    fun groupToJson(group: WorkspaceGroup): JSONObject {
        val obj = JSONObject()
        obj.put("displayId", group.displayId)
        obj.put("timestamp", group.timestamp)
        val appsArray = JSONArray()
        group.apps.forEach { app ->
            val appObj = JSONObject()
            appObj.put("pkg", app.packageName)
            appObj.put("comp", app.component)
            appObj.put("l", app.bounds.left)
            appObj.put("t", app.bounds.top)
            appObj.put("r", app.bounds.right)
            appObj.put("b", app.bounds.bottom)
            appsArray.put(appObj)
        }
        obj.put("apps", appsArray)
        return obj
    }

    fun jsonToGroup(obj: JSONObject): WorkspaceGroup {
        val displayId = obj.getInt("displayId")
        val timestamp = obj.getLong("timestamp")
        val appsArray = obj.getJSONArray("apps")
        val apps = mutableListOf<WorkspaceApp>()
        for (i in 0 until appsArray.length()) {
            val appObj = appsArray.getJSONObject(i)
            apps.add(WorkspaceApp(
                appObj.getString("pkg"),
                appObj.optString("comp", ""),
                Rect(appObj.getInt("l"), appObj.getInt("t"), appObj.getInt("r"), appObj.getInt("b"))
            ))
        }
        return WorkspaceGroup(apps, displayId, timestamp)
    }
}
