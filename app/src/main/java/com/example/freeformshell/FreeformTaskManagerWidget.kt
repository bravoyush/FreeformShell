package com.example.freeformshell

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews

class FreeformTaskManagerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_task_manager)
            
            // Set up list view adapter service
            val serviceIntent = Intent(context, FreeformTaskManagerWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
            
            // Set up refresh button
            val refreshIntent = Intent(context, FreeformTaskManagerWidget::class.java).apply {
                action = "ACTION_REFRESH"
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, 100 + appWidgetId, refreshIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_task_refresh, refreshPending)
            
            // Set up open companion app button
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context, 200 + appWidgetId, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_task_btn_open_app, openAppPending)
            
            // Set up click template for task items
            val itemClickIntent = Intent(context, FreeformTaskManagerWidget::class.java).apply {
                action = "ACTION_ITEM_CLICK"
            }
            val itemClickPending = PendingIntent.getBroadcast(
                context, 300 + appWidgetId, itemClickIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setPendingIntentTemplate(R.id.widget_task_list, itemClickPending)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            "ACTION_REFRESH" -> {
                val mgr = AppWidgetManager.getInstance(context)
                val cn = ComponentName(context, FreeformTaskManagerWidget::class.java)
                val ids = mgr.getAppWidgetIds(cn)
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
                
                // Also trigger Quick Toolbox update to synchronize Active Tracker
                val toolboxCn = ComponentName(context, FreeformQuickToolboxWidget::class.java)
                val toolboxIds = mgr.getAppWidgetIds(toolboxCn)
                if (toolboxIds.isNotEmpty()) {
                    val toolboxIntent = Intent(context, FreeformQuickToolboxWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, toolboxIds)
                    }
                    context.sendBroadcast(toolboxIntent)
                }
            }
            "ACTION_ITEM_CLICK" -> {
                val actionType = intent.getStringExtra("EXTRA_BENTO_ACTION")
                val taskId = intent.getIntExtra("EXTRA_TASK_ID", -1)
                Log.d("FreeformTaskManagerWidget", "ACTION_ITEM_CLICK: action=$actionType taskId=$taskId")
                if (taskId > 0 && actionType != null) {
                    Thread {
                        try {
                            if (actionType == "BRING_TO_FRONT") {
                                Log.d("FreeformTaskManagerWidget", "Bringing task $taskId to front")
                                ShellExecutor.moveTaskToFront(taskId)
                            } else if (actionType == "CLOSE_TASK") {
                                Log.d("FreeformTaskManagerWidget", "Removing task $taskId")
                                ShellExecutor.removeTask(taskId)
                            }
                            
                            // Trigger full refresh broadcast
                            val refreshIntent = Intent(context, FreeformTaskManagerWidget::class.java).apply {
                                action = "ACTION_REFRESH"
                            }
                            context.sendBroadcast(refreshIntent)
                        } catch (e: Exception) {
                            Log.e("FreeformTaskManagerWidget", "Error operating task: $taskId", e)
                        }
                    }.start()
                }
            }
        }
    }
}
