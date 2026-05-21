package com.example.freeformshell

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedWriter
import android.os.Handler
import android.os.Looper

object ShellExecutor {
    private const val TAG = "ShellExecutor"

    private var cachedActivityTaskManager: Any? = null
    private var cachedActivityManager: Any? = null
    private val lock = Any()
    
    // Cached reflective Method objects
    private var cachedResizeTaskMethod: java.lang.reflect.Method? = null
    private var cachedMoveTaskToFrontMethod: java.lang.reflect.Method? = null
    private var cachedStartActivityFromRecentsMethod: java.lang.reflect.Method? = null
    private var cachedMoveTaskToBackMethod: java.lang.reflect.Method? = null
    private var cachedRemoveTaskMethod: java.lang.reflect.Method? = null
    private var cachedSetFocusedRootTaskMethod: java.lang.reflect.Method? = null
    private var cachedSetFocusedTaskMethod: java.lang.reflect.Method? = null
    private var cachedForceStopPackageMethod: java.lang.reflect.Method? = null

    init {
        bypassHiddenApiRestrictions()
        ShizukuLifecycleManager.register(object : ShizukuLifecycleManager.ConnectionCallback {
            override fun onConnected() {
                Log.d(TAG, "Shizuku reconnected. Ready to initialize shell.")
            }
            override fun onDisconnected() {
                Log.w(TAG, "Shizuku disconnected. Closing persistent shell.")
                closePersistentShell()
                clearAtmCache()
            }
        })
    }

    private fun getActivityTaskManager(): Any? {
        synchronized(lock) {
            if (cachedActivityTaskManager != null) return cachedActivityTaskManager
            try {
                if (Shizuku.pingBinder()) {
                    val atmBinder = rikka.shizuku.SystemServiceHelper.getSystemService("activity_task")
                    val shizukuBinder = rikka.shizuku.ShizukuBinderWrapper(atmBinder)
                    val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                    val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                    cachedActivityTaskManager = asInterfaceMethod.invoke(null, shizukuBinder)
                    return cachedActivityTaskManager
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve IActivityTaskManager binder proxy", e)
            }
            return null
        }
    }

    private fun getActivityManager(): Any? {
        synchronized(lock) {
            if (cachedActivityManager != null) return cachedActivityManager
            try {
                if (Shizuku.pingBinder()) {
                    val amBinder = rikka.shizuku.SystemServiceHelper.getSystemService("activity")
                    val shizukuBinder = rikka.shizuku.ShizukuBinderWrapper(amBinder)
                    val stubClass = Class.forName("android.app.IActivityManager\$Stub")
                    val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                    cachedActivityManager = asInterfaceMethod.invoke(null, shizukuBinder)
                    return cachedActivityManager
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve IActivityManager binder proxy", e)
            }
            return null
        }
    }

    private fun clearAtmCache() {
        synchronized(lock) {
            cachedActivityTaskManager = null
            cachedActivityManager = null
            cachedResizeTaskMethod = null
            cachedMoveTaskToFrontMethod = null
            cachedStartActivityFromRecentsMethod = null
            cachedMoveTaskToBackMethod = null
            cachedRemoveTaskMethod = null
            cachedSetFocusedRootTaskMethod = null
            cachedSetFocusedTaskMethod = null
            cachedForceStopPackageMethod = null
        }
    }

    @JvmStatic
    fun bypassHiddenApiRestrictions(context: android.content.Context? = null) {
        // When a context is provided, respect the user-controlled compat toggle.
        // When called from init{} (no context yet), apply the bypass unconditionally
        // since the default for this fix is ON and we cannot read prefs without a context.
        if (context != null && !CompatibilityManager.isHiddenApiBypassEnabled(context)) {
            Log.d(TAG, "Hidden API bypass is disabled via Compatibility Settings — skipping.")
            return
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L")
                Log.d(TAG, "Successfully bypassed Hidden API restrictions using LSPosed!")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to bypass Hidden API restrictions using LSPosed", e)
        }
    }

    var lastFullLog: String = ""
        private set

    private var persistentProcess: Process? = null
    private var persistentWriter: BufferedWriter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var activeOverlaysCount = 0
    private val idleRunnable = Runnable { closePersistentShell() }

    @Synchronized
    private fun initPersistentShell() {
        if (persistentProcess != null) return
        Log.d(TAG, "Initializing persistent shell process...")
        try {
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "Shizuku binder not available, cannot start persistent shell")
                return
            }
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh"),
                null,
                null
            ) as Process
            
            persistentProcess = process
            persistentWriter = process.outputStream.bufferedWriter()
            
            // Start background threads to drain process output streams so they don't block
            Thread {
                try {
                    val reader = process.inputStream.bufferedReader()
                    while (true) {
                        reader.readLine() ?: break
                    }
                } catch (e: Exception) {}
            }.start()
            
            Thread {
                try {
                    val reader = process.errorStream.bufferedReader()
                    while (true) {
                        reader.readLine() ?: break
                    }
                } catch (e: Exception) {}
            }.start()
            
            Log.d(TAG, "Persistent shell successfully initialized!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize persistent shell", e)
            closePersistentShell()
        }
    }

    @Synchronized
    private fun closePersistentShell() {
        Log.d(TAG, "Closing persistent shell process...")
        try {
            persistentWriter?.close()
        } catch (e: Exception) {}
        persistentWriter = null
        
        try {
            persistentProcess?.destroy()
        } catch (e: Exception) {}
        persistentProcess = null
    }

    fun notifyOverlayCountChanged(count: Int) {
        handler.post {
            activeOverlaysCount = count
            Log.d(TAG, "Active overlays count changed: $activeOverlaysCount")
            handler.removeCallbacks(idleRunnable)
            if (activeOverlaysCount <= 0) {
                // Schedule termination of persistent shell after 10 seconds of idle inactivity
                handler.postDelayed(idleRunnable, 10000)
            } else {
                // Ensure shell is initialized if overlays are active
                Thread { initPersistentShell() }.start()
            }
        }
    }

    private fun triggerShellInteraction() {
        handler.post {
            handler.removeCallbacks(idleRunnable)
            if (activeOverlaysCount <= 0) {
                activeOverlaysCount = 1
            }
            if (persistentProcess == null) {
                Thread { initPersistentShell() }.start()
            } else {
                // Refresh the idle timer if active
                handler.postDelayed(idleRunnable, 10000)
            }
        }
    }

    fun exec(command: String): String {
        val res = executeCommandWithResult(command)
        val result = if (res.third != 0) "Error (${res.third}): ${res.second}" else if (res.first.isEmpty()) "OK" else res.first
        
        // Optimize: Don't log periodic dumpsys successes to the UI log to save memory/string copying
        val shouldLog = res.third != 0 || !command.contains("dumpsys activity")
        if (shouldLog) {
            lastFullLog = "[CMD] $command\n[OUT] $result\n" + lastFullLog.take(4000)
        }
        
        return result
    }

    data class CommandResult(val first: String, val second: String, val third: Int)

    @Synchronized
    fun executeCommandWithResult(command: String): CommandResult {
        Log.d(TAG, "Shell: $command")
        if (!Shizuku.pingBinder()) {
            return CommandResult("", "Fail: Shizuku binder not available", -1)
        }
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val output = process.inputStream.bufferedReader().readText()
            val errors = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroy()
            
            CommandResult(output, errors, exitCode)
        } catch (e: Exception) {
            Log.e(TAG, "Fail", e)
            CommandResult("", "Fail: ${e.message}", -1)
        }
    }

    fun executeCommand(command: String) = exec(command)

    fun resizeTask(taskId: Int, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            val manager = getActivityTaskManager()
            if (manager != null) {
                val method = synchronized(lock) {
                    if (cachedResizeTaskMethod == null) {
                        cachedResizeTaskMethod = manager.javaClass.getMethod(
                            "resizeTask",
                            Int::class.javaPrimitiveType,
                            android.graphics.Rect::class.java,
                            Int::class.javaPrimitiveType
                        )
                    }
                    cachedResizeTaskMethod!!
                }
                val bounds = android.graphics.Rect(left, top, right, bottom)
                method.invoke(manager, taskId, bounds, 1) // 1 = RESIZE_MODE_USER
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resize task via direct Binder call, falling back to shell", e)
            clearAtmCache()
        }

        triggerShellInteraction()
        val writer = persistentWriter
        if (writer != null) {
            try {
                writer.write("cmd activity task resize $taskId $left $top $right $bottom\n")
                writer.flush()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing task resize to persistent shell, resetting shell", e)
                closePersistentShell()
            }
        }
        exec("cmd activity task resize $taskId $left $top $right $bottom")
    }

    fun injectTap(x: Int, y: Int) {
        triggerShellInteraction()
        val writer = persistentWriter
        if (writer != null) {
            try {
                writer.write("input tap $x $y\n")
                writer.flush()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing input tap to persistent shell, resetting shell", e)
                closePersistentShell()
            }
        }
        exec("input tap $x $y")
    }

    fun moveTaskToFront(taskId: Int) {
        try {
            val manager = getActivityTaskManager()
            if (manager != null) {
                val method = synchronized(lock) {
                    if (cachedMoveTaskToFrontMethod == null) {
                        cachedMoveTaskToFrontMethod = manager.javaClass.getMethod(
                            "moveTaskToFront",
                            Class.forName("android.app.IApplicationThread"),
                            String::class.java,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            android.os.Bundle::class.java
                        )
                    }
                    cachedMoveTaskToFrontMethod!!
                }
                method.invoke(manager, null, "com.android.shell", taskId, 0, null)
                
                // Direct focus injection for lag-free active focus transfer on Android 14
                setFocusedRootTaskWithManager(manager, taskId)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move task to front via direct Binder call, falling back to shell", e)
            clearAtmCache()
        }

        triggerShellInteraction()
        val writer = persistentWriter
        if (writer != null) {
            try {
                writer.write("cmd activity task movetotop $taskId\n")
                writer.write("am start-activity --task $taskId\n")
                writer.flush()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing movetotop to persistent shell, resetting shell", e)
                closePersistentShell()
            }
        }
        exec("cmd activity task movetotop $taskId")
        exec("am start-activity --task $taskId")
    }

    private fun setFocusedRootTaskWithManager(activityTaskManager: Any?, taskId: Int) {
        if (activityTaskManager == null) return
        try {
            val method = synchronized(lock) {
                if (cachedSetFocusedRootTaskMethod == null) {
                    cachedSetFocusedRootTaskMethod = activityTaskManager.javaClass.getMethod(
                        "setFocusedRootTask",
                        Int::class.javaPrimitiveType
                    )
                }
                cachedSetFocusedRootTaskMethod!!
            }
            method.invoke(activityTaskManager, taskId)
            Log.d(TAG, "Successfully focused root task $taskId via direct Binder call")
        } catch (e: NoSuchMethodException) {
            try {
                val method = synchronized(lock) {
                    if (cachedSetFocusedTaskMethod == null) {
                        cachedSetFocusedTaskMethod = activityTaskManager.javaClass.getMethod(
                            "setFocusedTask",
                            Int::class.javaPrimitiveType
                        )
                    }
                    cachedSetFocusedTaskMethod!!
                }
                method.invoke(activityTaskManager, taskId)
                Log.d(TAG, "Successfully focused task $taskId via setFocusedTask")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed focusing task via both setFocusedRootTask and setFocusedTask", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting focused root task", e)
        }
    }

    fun relaunchFreeformTask(taskId: Int, component: String) {
        try {
            val manager = getActivityTaskManager()
            if (manager != null) {
                val options = android.app.ActivityOptions.makeBasic()
                try {
                    val setLaunchWindowingModeMethod = options.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    setLaunchWindowingModeMethod.invoke(options, 5) // 5 = WINDOWING_MODE_FREEFORM
                } catch (e: Exception) {}

                val method = synchronized(lock) {
                    if (cachedStartActivityFromRecentsMethod == null) {
                        cachedStartActivityFromRecentsMethod = manager.javaClass.getMethod(
                            "startActivityFromRecents",
                            Int::class.javaPrimitiveType,
                            android.os.Bundle::class.java
                        )
                    }
                    cachedStartActivityFromRecentsMethod!!
                }
                method.invoke(manager, taskId, options.toBundle())
                
                // Ensure task gets immediate focus after launching
                setFocusedRootTaskWithManager(manager, taskId)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to relaunch task via direct Binder call, falling back to shell", e)
            clearAtmCache()
        }

        triggerShellInteraction()
        val writer = persistentWriter
        if (writer != null) {
            try {
                writer.write("am start-activity --task $taskId --windowingMode 5 -n $component --activity-brought-to-front\n")
                writer.flush()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing relaunch to persistent shell, resetting shell", e)
                closePersistentShell()
            }
        }
        exec("am start-activity --task $taskId --windowingMode 5 -n $component --activity-brought-to-front")
    }

    fun moveTaskToBack(taskId: Int) {
        try {
            val manager = getActivityTaskManager()
            if (manager != null) {
                val method = synchronized(lock) {
                    if (cachedMoveTaskToBackMethod == null) {
                        cachedMoveTaskToBackMethod = manager.javaClass.getMethod(
                            "moveTaskToBack",
                            Int::class.javaPrimitiveType
                        )
                    }
                    cachedMoveTaskToBackMethod!!
                }
                method.invoke(manager, taskId)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move task to back via direct Binder call, falling back to shell", e)
            clearAtmCache()
        }

        triggerShellInteraction()
        val writer = persistentWriter
        if (writer != null) {
            try {
                writer.write("cmd activity task move-to-back $taskId\n")
                writer.flush()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing move-to-back to persistent shell, resetting shell", e)
                closePersistentShell()
            }
        }
        exec("cmd activity task move-to-back $taskId")
    }

    fun removeTask(taskId: Int) {
        try {
            val manager = getActivityTaskManager()
            if (manager != null) {
                val method = synchronized(lock) {
                    if (cachedRemoveTaskMethod == null) {
                        cachedRemoveTaskMethod = manager.javaClass.getMethod(
                            "removeTask",
                            Int::class.javaPrimitiveType
                        )
                    }
                    cachedRemoveTaskMethod!!
                }
                method.invoke(manager, taskId)
                Log.d(TAG, "Successfully removed task $taskId via direct Binder call")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove task via direct Binder call, falling back to shell", e)
            clearAtmCache()
        }

        triggerShellInteraction()
        val writer = persistentWriter
        if (writer != null) {
            try {
                writer.write("cmd activity task remove $taskId\n")
                writer.flush()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing task remove to persistent shell, resetting shell", e)
                closePersistentShell()
            }
        }
        exec("cmd activity task remove $taskId")
    }

    fun forceStopApp(packageName: String, taskId: Int = -1) {
        try {
            if (rikka.shizuku.Shizuku.pingBinder()) {
                // 1. Force stop app via IActivityManager
                try {
                    val amManager = getActivityManager()
                    if (amManager != null) {
                        val forceStopMethod = synchronized(lock) {
                            if (cachedForceStopPackageMethod == null) {
                                cachedForceStopPackageMethod = amManager.javaClass.getMethod(
                                    "forceStopPackage",
                                    String::class.java,
                                    Int::class.javaPrimitiveType
                                )
                            }
                            cachedForceStopPackageMethod!!
                        }
                        forceStopMethod.invoke(amManager, packageName, -2) // -2 = UserHandle.USER_CURRENT
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to forceStopPackage via Binder", e)
                    clearAtmCache()
                }

                // 2. Remove task via IActivityTaskManager
                if (taskId != -1) {
                    try {
                        val atmManager = getActivityTaskManager()
                        if (atmManager != null) {
                            val removeTaskMethod = synchronized(lock) {
                                if (cachedRemoveTaskMethod == null) {
                                    cachedRemoveTaskMethod = atmManager.javaClass.getMethod(
                                        "removeTask",
                                        Int::class.javaPrimitiveType
                                    )
                                }
                                cachedRemoveTaskMethod!!
                            }
                            removeTaskMethod.invoke(atmManager, taskId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to removeTask via Binder", e)
                        clearAtmCache()
                    }
                }
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed force stop via direct Binder calls, falling back to shell", e)
            clearAtmCache()
        }

        triggerShellInteraction()
        val writer = persistentWriter
        if (writer != null) {
            try {
                writer.write("am force-stop $packageName\n")
                if (taskId != -1) {
                    writer.write("cmd activity task remove $taskId\n")
                }
                writer.flush()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing force-stop to persistent shell, resetting shell", e)
                closePersistentShell()
            }
        }
        exec("am force-stop $packageName")
        if (taskId != -1) {
            exec("cmd activity task remove $taskId")
        }
    }
}
