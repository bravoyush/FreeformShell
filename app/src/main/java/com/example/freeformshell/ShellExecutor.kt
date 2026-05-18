package com.example.freeformshell

import android.util.Log
import rikka.shizuku.Shizuku

object ShellExecutor {
    private const val TAG = "ShellExecutor"

    var lastFullLog: String = ""
        private set

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
        exec("cmd activity task resize $taskId $left $top $right $bottom")
    }

    fun moveTaskToFront(taskId: Int) {
        // Double trigger: use both activity manager command and task resize command which sometimes triggers focus
        exec("cmd activity task movetotop $taskId")
        // Also try am start with the task id if available (Android 10+)
        exec("am start-activity --task $taskId")
    }

    fun relaunchFreeformTask(taskId: Int, component: String) {
        // Force relaunch into freeform mode using the specific component and task ID
        // This is much more effective than just movetotop on Sony devices
        exec("am start-activity --task $taskId --windowingMode 5 -n $component --activity-brought-to-front")
    }

    fun moveTaskToBack(taskId: Int) {
        exec("cmd activity task move-to-back $taskId")
    }

    fun forceStopApp(packageName: String, taskId: Int = -1) {
        exec("am force-stop $packageName")
        if (taskId != -1) {
            exec("cmd activity task remove $taskId")
        }
    }
}
