package com.example.freeformshell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object ComponentToggleManager {
    private const val TAG = "ComponentToggleManager"

    /**
     * Enables or disables a list of components dynamically at runtime.
     */
    fun setComponentsEnabled(context: Context, classes: List<Class<*>>, enabled: Boolean) {
        val pm = context.packageManager
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        for (cls in classes) {
            try {
                val cn = ComponentName(context, cls)
                // Set the enabled state at the OS manifest level.
                // DONT_KILL_APP prevents the main companion app from force-closing during the swap.
                pm.setComponentEnabledSetting(
                    cn,
                    state,
                    PackageManager.DONT_KILL_APP
                )
                Log.d(TAG, "Component ${cls.simpleName} set enabled state to: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle component state for class: ${cls.name}", e)
            }
        }
    }

    /**
     * Syncs all manifest components, keeping them enabled so they never disappear or hide
     * from the launcher/widget list, but triggering widget updates dynamically.
     */
    fun syncAllModules(context: Context) {
        val lockEnabled = ThemeManager.isModuleLockEnabled(context)
        val widgetEnabled = ThemeManager.isModuleWidgetEnabled(context)
        val mirrorEnabled = ThemeManager.isModuleMirrorEnabled(context)

        Log.d(TAG, "syncAllModules: lock=$lockEnabled, widget=$widgetEnabled, mirror=$mirrorEnabled")

        // Natively force-enable all manifest components so they are NEVER hidden from launcher/widget list
        val allComponents = listOf(
            DesktopKeyguardService::class.java,
            DesktopKeyguardActivity::class.java,
            LockScreenNotificationListener::class.java,
            LockDesktopActivity::class.java,
            FreeformTaskManagerWidget::class.java,
            FreeformTaskManagerWidgetService::class.java,
            FreeformWidget::class.java,
            FreeformWidgetService::class.java,
            FreeformQuickToolboxWidget::class.java,
            PhoneMirrorActivity::class.java,
            PhoneMirrorService::class.java,
            PhoneMirrorOverlayService::class.java
        )
        setComponentsEnabled(context, allComponents, true)

        // Refresh all widgets to reflect their current status
        try {
            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
            
            // Refresh Task Manager Widget
            val taskCn = ComponentName(context, FreeformTaskManagerWidget::class.java)
            val taskIds = mgr.getAppWidgetIds(taskCn)
            if (taskIds.isNotEmpty()) {
                val intent = Intent(context, FreeformTaskManagerWidget::class.java).apply {
                    action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, taskIds)
                }
                context.sendBroadcast(intent)
            }
            
            // Refresh Freeform Widget
            val freeformCn = ComponentName(context, FreeformWidget::class.java)
            val freeformIds = mgr.getAppWidgetIds(freeformCn)
            if (freeformIds.isNotEmpty()) {
                val intent = Intent(context, FreeformWidget::class.java).apply {
                    action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, freeformIds)
                }
                context.sendBroadcast(intent)
            }
            
            // Refresh Quick Toolbox Widget
            val toolboxCn = ComponentName(context, FreeformQuickToolboxWidget::class.java)
            val toolboxIds = mgr.getAppWidgetIds(toolboxCn)
            if (toolboxIds.isNotEmpty()) {
                val intent = Intent(context, FreeformQuickToolboxWidget::class.java).apply {
                    action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, toolboxIds)
                }
                context.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast updates to widgets", e)
        }
    }
}
