package com.example.freeformshell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class ProcessInspectorActivity : ComponentActivity() {
    
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.freeformshell.ACTION_CLOSE_PROPERTIES") {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val pid = intent.getIntExtra("extra_pid", -1)
        val packageName = intent.getStringExtra("extra_package_name") ?: ""
        val name = intent.getStringExtra("extra_name") ?: "Process"
        val user = intent.getStringExtra("extra_user") ?: "root"
        val threads = intent.getIntExtra("extra_threads", 1)
        val cpu = intent.getFloatExtra("extra_cpu", 0f)
        val mem = intent.getFloatExtra("extra_mem", 0f)
        val initialDark = intent.getBooleanExtra("extra_is_dark", false)
        
        if (pid == -1) {
            finish()
            return
        }

        val proc = ProcessItem(
            pid = pid,
            packageName = packageName,
            name = name,
            cpuPercent = cpu,
            memoryMb = mem,
            isAppWindow = false,
            threads = threads,
            user = user
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, IntentFilter("com.example.freeformshell.ACTION_CLOSE_PROPERTIES"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, IntentFilter("com.example.freeformshell.ACTION_CLOSE_PROPERTIES"))
        }

        setContent {
            var isDarkTheme by remember { mutableStateOf(initialDark) }
            
            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == "com.example.freeformshell.THEME_CHANGED") {
                            context?.let { ctx ->
                                val mode = ThemeManager.getThemeMode(ctx)
                                if (mode == 1) isDarkTheme = false
                                else if (mode == 2) isDarkTheme = true
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, IntentFilter("com.example.freeformshell.THEME_CHANGED"), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(receiver, IntentFilter("com.example.freeformshell.THEME_CHANGED"))
                }
                onDispose { 
                    try { unregisterReceiver(receiver) } catch (e: Exception) {}
                }
            }

            DynamicTaskManagerTheme(isDark = isDarkTheme) {
                ProcessInspectorActivityContent(
                    proc = proc,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {}
    }
}
