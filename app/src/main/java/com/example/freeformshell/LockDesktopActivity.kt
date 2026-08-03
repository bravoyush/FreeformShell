package com.example.freeformshell

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Zero-UI launcher shortcut activity.
 * Appears in the app drawer as "Lock Desktop".
 * Tapping it immediately deploys the desktop lock screen and finishes.
 */
class LockDesktopActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!ThemeManager.isModuleLockEnabled(this)) {
            android.widget.Toast.makeText(this, "Lockscreen module is disabled! Enable it in Companion settings.", android.widget.Toast.LENGTH_LONG).show()
            overridePendingTransition(0, 0)
            finish()
            return
        }

        // If service is already running, call directly — fastest path
        val svc = FreeformOverlayService.getInstance()
        if (svc != null) {
            svc.triggerLockDesktop()
        } else {
            // Service not running: start it with the lock action
            startService(Intent(this, FreeformOverlayService::class.java).apply {
                action = "ACTION_LOCK_DESKTOP"
            })
        }

        // Suppress enter/exit animation for a seamless feel
        overridePendingTransition(0, 0)
        finish()
    }
}
