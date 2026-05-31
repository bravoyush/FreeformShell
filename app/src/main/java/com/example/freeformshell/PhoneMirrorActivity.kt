package com.example.freeformshell

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import rikka.shizuku.Shizuku

class PhoneMirrorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            MirrorShortcutHelper.updatePinnedMirrorShortcutIfExist(this)
        } catch (e: Exception) {}
        
        title = "Mirror (${MirrorShortcutHelper.getFriendlyDeviceName(Build.MODEL)})"
        
        try {
            if (Shizuku.pingBinder()) {
                val intent = Intent(this, PhoneMirrorOverlayService::class.java).apply {
                    val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        this@PhoneMirrorActivity.display?.displayId ?: 0
                    } else {
                        0
                    }
                    putExtra("EXTRA_DISPLAY_ID", displayId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "Starting Phone Mirror Window...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Shizuku is not running! Please start Shizuku first.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start Mirror overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        finish()
    }
}
