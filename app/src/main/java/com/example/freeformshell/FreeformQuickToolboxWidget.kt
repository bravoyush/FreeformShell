package com.example.freeformshell

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.hardware.display.DisplayManager
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast

class FreeformQuickToolboxWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_toolbox)
            val pm = context.packageManager

            // 1. Setup Active App Tracker Card with Premium Dynamic Background Tinting
            try {
                // Get the most active freeform task
                val activeTasks = TaskManager.getRecentTasks().filter { it.isFreeform && it.packageName != "com.example.freeformshell" }
                if (activeTasks.isNotEmpty()) {
                    val topTask = activeTasks[0]
                    val appLabel = try {
                        val appInfo = pm.getApplicationInfo(topTask.packageName, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        topTask.packageName.substringAfterLast('.')
                    }
                    
                    views.setTextViewText(R.id.widget_bento_active_app_name, appLabel)
                    views.setTextViewText(R.id.widget_bento_active_app_id, "Task ID: ${topTask.taskId}")
                    
                    // Decode icon safely
                    try {
                        val drawable = pm.getApplicationIcon(topTask.packageName)
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
                        views.setImageViewBitmap(R.id.widget_bento_active_app_icon, bitmap)

                        // 3. Dynamic Dominant Color Tinting
                        // Scale the icon down to 1x1 to extract its dominant color
                        val pixelBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
                        val dominantColor = pixelBitmap.getPixel(0, 0)
                        pixelBitmap.recycle()

                        // Generate a modern, semi-transparent 30% alpha tint of this dominant color
                        val alphaColor = (dominantColor and 0x00FFFFFF) or 0x4D000000
                        views.setColorStateList(
                            R.id.widget_bento_active_app_root,
                            "setBackgroundTintList",
                            ColorStateList.valueOf(alphaColor)
                        )
                    } catch (e: Exception) {
                        views.setImageViewResource(R.id.widget_bento_active_app_icon, android.R.drawable.sym_def_app_icon)
                        // Slate gray/warm deep amber fallback
                        views.setColorStateList(
                            R.id.widget_bento_active_app_root,
                            "setBackgroundTintList",
                            ColorStateList.valueOf(Color.parseColor("#4D3E2E20"))
                        )
                    }
                } else {
                    views.setTextViewText(R.id.widget_bento_active_app_name, "None Active")
                    views.setTextViewText(R.id.widget_bento_active_app_id, "Task ID: N/A")
                    views.setImageViewResource(R.id.widget_bento_active_app_icon, android.R.drawable.sym_def_app_icon)
                    
                    // Slate gray/warm deep amber fallback
                    views.setColorStateList(
                        R.id.widget_bento_active_app_root,
                        "setBackgroundTintList",
                        ColorStateList.valueOf(Color.parseColor("#4D3E2E20"))
                    )
                }
            } catch (e: Exception) {
                Log.e("FreeformQuickToolbox", "Error setting active tracker", e)
            }

            // 2. Setup Connected Screens Display Shell Toggle Status Globally
            try {
                val globalEnabled = ThemeManager.isGlobalOverlayEnabled(context)
                val statusText = if (globalEnabled) "Overlays: ENABLED" else "Overlays: DISABLED"
                views.setTextViewText(R.id.widget_bento_displays_status, statusText)
            } catch (e: Exception) {
                Log.e("FreeformQuickToolbox", "Error setting screen status", e)
            }

            // 3. Bind Pending Intent for Close All
            val closeAllIntent = Intent(context, FreeformQuickToolboxWidget::class.java).apply {
                action = "ACTION_CLOSE_ALL"
            }
            val closeAllPending = PendingIntent.getBroadcast(
                context, 400 + appWidgetId, closeAllIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_bento_btn_close_all, closeAllPending)

            // 4. Bind Pending Intent for Restart Overlay
            val restartIntent = Intent(context, FreeformQuickToolboxWidget::class.java).apply {
                action = "ACTION_RESTART_SHELL"
            }
            val restartPending = PendingIntent.getBroadcast(
                context, 500 + appWidgetId, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_bento_btn_restart_shell, restartPending)

            // 5. Bind Pending Intent for Toggle Screen Overlays
            val toggleIntent = Intent(context, FreeformQuickToolboxWidget::class.java).apply {
                action = "ACTION_TOGGLE_DISPLAY"
            }
            val togglePending = PendingIntent.getBroadcast(
                context, 600 + appWidgetId, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_bento_btn_toggle_displays, togglePending)

            // 6. Bind Pending Intent for Open Companion Console
            val openConsoleIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openConsolePending = PendingIntent.getActivity(
                context, 700 + appWidgetId, openConsoleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_bento_btn_open_console, openConsolePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("FreeformQuickToolbox", "onReceive: ${intent.action}")
        when (intent.action) {
            "ACTION_CLOSE_ALL" -> {
                Toast.makeText(context, "Force closing companion app and overlays...", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        // 1. Terminate all active freeform tasks
                        val activeTasks = TaskManager.getRecentTasks().filter { it.isFreeform && it.packageName != "com.example.freeformshell" }
                        for (task in activeTasks) {
                            ShellExecutor.removeTask(task.taskId)
                        }
                        
                        // 2. Stop both overlay services cleanly
                        val stopOverlayIntent = Intent(context, FreeformOverlayService::class.java).apply {
                            action = "ACTION_EXIT"
                        }
                        context.startForegroundService(stopOverlayIntent)

                        val stopSimpleIntent = Intent(context, SimpleOverlayService::class.java).apply {
                            action = "ACTION_EXIT"
                        }
                        context.startForegroundService(stopSimpleIntent)

                        // 3. Dismiss active status bar notifications using NotificationManager
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(1) // FreeformOverlayService notification ID
                        nm.cancel(12345) // SimpleOverlayService notification ID

                        // Wait a short moment for clean service onDestroy calls
                        Thread.sleep(600)

                        // Notify updates to widgets before exit
                        triggerRefresh(context)

                        // 4. Force close the persistent companion app process
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } catch (e: Exception) {
                        Log.e("FreeformQuickToolbox", "Error force closing companion app", e)
                    }
                }.start()
            }
            "ACTION_RESTART_SHELL" -> {
                Toast.makeText(context, "Relaunching FGS shell overlays...", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        // Stop overlay service cleanly
                        val stopIntent = Intent(context, FreeformOverlayService::class.java).apply {
                            action = "ACTION_EXIT"
                        }
                        context.startForegroundService(stopIntent)
                        
                        Thread.sleep(600)
                        
                        // Start overlay service again if global overlays are enabled
                        if (ThemeManager.isGlobalOverlayEnabled(context)) {
                            val startIntent = Intent(context, FreeformOverlayService::class.java)
                            context.startForegroundService(startIntent)
                        }
                        
                        // Notify updates to both widgets
                        triggerRefresh(context)
                    } catch (e: Exception) {
                        Log.e("FreeformQuickToolbox", "Error restarting overlay shell", e)
                    }
                }.start()
            }
            "ACTION_TOGGLE_DISPLAY" -> {
                Thread {
                    try {
                        val currentState = ThemeManager.isGlobalOverlayEnabled(context)
                        val newState = !currentState
                        ThemeManager.setGlobalOverlayEnabled(context, newState)
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "Global overlays: ${if (newState) "ENABLED" else "DISABLED"}", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Restart overlays to apply settings instantly
                        val restartIntent = Intent(context, FreeformQuickToolboxWidget::class.java).apply {
                            action = "ACTION_RESTART_SHELL"
                        }
                        context.sendBroadcast(restartIntent)
                    } catch (e: Exception) {
                        Log.e("FreeformQuickToolbox", "Error toggling global overlays", e)
                    }
                }.start()
            }
        }
    }

    private fun triggerRefresh(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        
        // Refresh Task Manager widget list
        val taskCn = ComponentName(context, FreeformTaskManagerWidget::class.java)
        val taskIds = mgr.getAppWidgetIds(taskCn)
        mgr.notifyAppWidgetViewDataChanged(taskIds, R.id.widget_task_list)
        
        // Refresh Bento widget itself
        val bentoCn = ComponentName(context, FreeformQuickToolboxWidget::class.java)
        val bentoIds = mgr.getAppWidgetIds(bentoCn)
        if (bentoIds.isNotEmpty()) {
            val updateIntent = Intent(context, FreeformQuickToolboxWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, bentoIds)
            }
            context.sendBroadcast(updateIntent)
        }
    }
}
