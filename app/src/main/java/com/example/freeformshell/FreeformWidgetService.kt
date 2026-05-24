package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class FreeformWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FreeformWidgetFactory(applicationContext)
    }
}

class FreeformWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var groups = mutableListOf<WorkspaceGroup>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            val history = WorkspaceManager.getHistory(context)
            val favorite = WorkspaceManager.getFavorite(context)
            
            groups = mutableListOf()
            favorite?.let { groups.add(it) }
            groups.addAll(history)
            
            groups = groups.take(8).toMutableList()
        } catch (e: Exception) {
            Log.e("WidgetFactory", "Error fetching workspaces", e)
        }
    }

    override fun onDestroy() {
        groups.clear()
    }

    override fun getCount(): Int = groups.size

    override fun getViewAt(position: Int): RemoteViews {
        val group = groups[position]
        val views = RemoteViews(context.packageName, R.layout.widget_group_item)
        
        val favorite = WorkspaceManager.getFavorite(context)
        val isFavorite = favorite != null && favorite.timestamp == group.timestamp
        
        val titleText = when {
            isFavorite -> "FAVORITE WORKSPACE"
            group.displayId == 0 -> "PHONE WORKSPACE"
            else -> "EXTERNAL DISPLAY ${group.displayId} WORKSPACE"
        }
        views.setTextViewText(R.id.group_title, titleText)
        
        val diffSec = (System.currentTimeMillis() - group.timestamp) / 1000
        val timeStr = when {
            diffSec < 60 -> "Just Now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            else -> "${diffSec / 3600}h ago"
        }
        views.setTextViewText(R.id.group_timestamp, timeStr)
        
        // Clear icons
        val iconIds = listOf(R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4)
        iconIds.forEach { views.setViewVisibility(it, android.view.View.GONE) }

        group.apps.take(4).forEachIndexed { i, app ->
            val iconId = iconIds[i]
            views.setViewVisibility(iconId, android.view.View.VISIBLE)
            
            // Try to load real icon
            try {
                val drawable = context.packageManager.getApplicationIcon(app.packageName)
                val bitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val b = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    b
                }
                views.setImageViewBitmap(iconId, bitmap)
            } catch (e: Exception) {
                views.setImageViewResource(iconId, android.R.drawable.sym_def_app_icon)
            }
        }
        
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val extDisplayId = dm.displays.firstOrNull { it.displayId > 0 }?.displayId ?: 1

        val fillInPhone = Intent().apply {
            putExtra("EXTRA_GROUP_JSON", WorkspaceManager.groupToJson(group).toString())
            putExtra("EXTRA_TARGET_DISPLAY", 0)
        }
        views.setOnClickFillInIntent(R.id.restore_phone, fillInPhone)

        val fillInExternal = Intent().apply {
            putExtra("EXTRA_GROUP_JSON", WorkspaceManager.groupToJson(group).toString())
            putExtra("EXTRA_TARGET_DISPLAY", extDisplayId)
        }
        views.setOnClickFillInIntent(R.id.restore_external, fillInExternal)

        val fillInDefault = Intent().apply {
            putExtra("EXTRA_GROUP_JSON", WorkspaceManager.groupToJson(group).toString())
            putExtra("EXTRA_TARGET_DISPLAY", group.displayId)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInDefault)
        
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
