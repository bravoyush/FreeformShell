package com.example.freeformshell

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FreeformAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FreeformAccessibility"
        @Volatile
        var instance: FreeformAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "FreeformAccessibilityService connected! Instance token ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Lightweight accessibility service stub for WindowManager TYPE_ACCESSIBILITY_OVERLAY elevation
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "FreeformAccessibilityService destroyed.")
    }
}
