package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class FreeformTaskManagerWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FreeformTaskManagerWidgetFactory(applicationContext)
    }
}

class FreeformTaskManagerWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var tasks = mutableListOf<AppTask>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            val activeService = FreeformOverlayService.getInstance()
            if (activeService != null) {
                // Pull directly from the active service's known tasks!
                tasks = activeService.knownFreeformTasks.entries.map { (id, pair) ->
                    AppTask(id, pair.first, pair.second, isVisible = true, isFreeform = true)
                }.filter { it.packageName != "com.example.freeformshell" }.toMutableList()
                Log.d("TaskManagerWidgetFactory", "onDataSetChanged: loaded ${tasks.size} active tasks from service")
            } else {
                // Fallback to TaskManager.getRecentTasks()
                val allTasks = TaskManager.getRecentTasks()
                tasks = allTasks.filter { it.isFreeform && it.packageName != "com.example.freeformshell" }.toMutableList()
                Log.d("TaskManagerWidgetFactory", "onDataSetChanged: loaded ${tasks.size} active tasks from fallback")
            }
        } catch (e: Exception) {
            Log.e("TaskManagerWidgetFactory", "Error fetching active tasks", e)
        }
    }

    override fun onDestroy() {
        tasks.clear()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= tasks.size) {
            return RemoteViews(context.packageName, R.layout.widget_task_item)
        }
        
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)
        
        // Resolve a user-friendly application label
        val pm = context.packageManager
        val appLabel = try {
            val appInfo = pm.getApplicationInfo(task.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            task.packageName.substringAfterLast('.')
        }
        
        views.setTextViewText(R.id.widget_task_app_name, appLabel)
        views.setTextViewText(R.id.widget_task_id, "Task ID: ${task.taskId}")
        
        // Load Application Icon safely for RemoteViews
        try {
            val drawable = pm.getApplicationIcon(task.packageName)
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val b = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
            }
            views.setImageViewBitmap(R.id.widget_task_app_icon, bitmap)
        } catch (e: Exception) {
            views.setImageViewResource(R.id.widget_task_app_icon, android.R.drawable.sym_def_app_icon)
        }
        
        // Bind Bring to Front fill-in intent
        val fillInBring = Intent().apply {
            putExtra("EXTRA_BENTO_ACTION", "BRING_TO_FRONT")
            putExtra("EXTRA_TASK_ID", task.taskId)
        }
        views.setOnClickFillInIntent(R.id.widget_task_btn_bring_front, fillInBring)
        
        // Bind Close fill-in intent
        val fillInClose = Intent().apply {
            putExtra("EXTRA_BENTO_ACTION", "CLOSE_TASK")
            putExtra("EXTRA_TASK_ID", task.taskId)
        }
        views.setOnClickFillInIntent(R.id.widget_task_btn_close, fillInClose)
        
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = if (position < tasks.size) tasks[position].taskId.toLong() else position.toLong()
    override fun hasStableIds(): Boolean = true
}
