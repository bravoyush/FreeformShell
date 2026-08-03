package com.example.freeformshell

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.util.Log

class WorkspaceShortcutActivity : Activity() {
    private val TAG = "WorkspaceShortcutActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupJson = intent.getStringExtra("EXTRA_GROUP_JSON")
        if (groupJson != null) {
            try {
                // Forward deployment request directly to FreeformOverlayService
                val serviceIntent = Intent(this, FreeformOverlayService::class.java).apply {
                    action = "ACTION_LAUNCH_WORKSPACE"
                    putExtra("EXTRA_GROUP_JSON", groupJson)
                    putExtra("EXTRA_TARGET_DISPLAY", intent.getIntExtra("EXTRA_TARGET_DISPLAY", -1))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                
                Toast.makeText(this, "Restoring Saved Workspace...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Workspace launch request sent to service.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch overlay service", e)
                Toast.makeText(this, "Failed to restore workspace: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Error: Workspace data is missing or corrupt.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Missing EXTRA_GROUP_JSON extra in launching shortcut intent.")
        }

        finish()
    }
}
