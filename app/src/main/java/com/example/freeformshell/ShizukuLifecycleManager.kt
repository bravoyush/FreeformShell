package com.example.freeformshell

import android.os.Handler
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuLifecycleManager {
    private const val TAG = "ShizukuLifecycle"
    private val handler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    interface ConnectionCallback {
        fun onConnected()
        fun onDisconnected()
    }

    private val callbacks = java.util.concurrent.CopyOnWriteArrayList<ConnectionCallback>()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku Binder received successfully!")
        handler.post {
            callbacks.forEach { it.onConnected() }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.e(TAG, "Shizuku Binder has died!")
        handler.post {
            callbacks.forEach { it.onDisconnected() }
        }
    }

    fun register(callback: ConnectionCallback) {
        callbacks.add(callback)
        if (!isRegistered) {
            try {
                Shizuku.addBinderReceivedListener(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                isRegistered = true
                Log.d(TAG, "Lifecycle listeners registered with Shizuku.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register Shizuku listeners", e)
            }
        }
        // Immediate initial check
        if (Shizuku.pingBinder()) {
            callback.onConnected()
        } else {
            callback.onDisconnected()
        }
    }

    fun unregister(callback: ConnectionCallback) {
        callbacks.remove(callback)
    }
}
