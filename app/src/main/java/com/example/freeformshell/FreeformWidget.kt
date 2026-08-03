package com.example.freeformshell

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class FreeformWidget : AppWidgetProvider() {
    
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            
            val widgetEnabled = ThemeManager.isModuleWidgetEnabled(context)
            if (widgetEnabled) {
                views.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget__rescueall, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_disabled_text, android.view.View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                views.setViewVisibility(R.id.widget__rescueall, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_disabled_text, android.view.View.VISIBLE)
            }
            
            // Set up list view
            val serviceIntent = Intent(context, FreeformWidgetService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            
            // Set up "Rescue All" button
            val rescueIntent = Intent(context, FreeformOverlayService::class.java).apply {
                action = "ACTION_SHOW_ALL"
            }
            val rescuePending = PendingIntent.getForegroundService(context, 10 + appWidgetId, rescueIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget__rescueall, rescuePending)
            
            // Set up "Refresh" button
            val refreshIntent = Intent(context, FreeformWidget::class.java).apply {
                action = "ACTION_REFRESH"
            }
            val refreshPending = PendingIntent.getBroadcast(context, 11 + appWidgetId, refreshIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)
            
            // Set up list item click template (Target service directly to bypass background restrictions)
            val restoreIntent = Intent(context, FreeformOverlayService::class.java).apply {
                action = "ACTION_LAUNCH_WORKSPACE"
            }
            val restorePending = PendingIntent.getForegroundService(context, 12 + appWidgetId, restoreIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setPendingIntentTemplate(R.id.widget_list, restorePending)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            "ACTION_REFRESH" -> {
                val mgr = AppWidgetManager.getInstance(context)
                val cn = ComponentName(context, FreeformWidget::class.java)
                mgr.notifyAppWidgetViewDataChanged(mgr.getAppWidgetIds(cn), R.id.widget_list)
            }
        }
    }
}
