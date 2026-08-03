package com.example.freeformshell

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile

data class ProcessSnapshot(
    val pid: Int,
    val timestamp: Long,
    val cpuUsage: Float,
    val memUsageMb: Float,
    val threads: Int,
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val cpuHistory: List<Float>,
    val memHistory: List<Float>,
    val netHistory: List<Float>,
    val diskReadBytesPerSec: Long = 0,
    val diskWriteBytesPerSec: Long = 0,
    val diskHistory: List<Float> = emptyList(),
    val threadHistory: List<Float> = emptyList()
)

data class DetailedProcessInfo(
    val pid: Int,
    val ppid: Int = 0,
    val uid: Int = 0,
    val user: String = "Unknown",
    val name: String = "Unknown",
    val packageName: String = "Unknown",
    val priority: Int = 0,
    val nice: Int = 0,
    val schedGroup: String = "Unknown",
    val state: String = "Unknown",
    val oomAdj: String = "Unknown",
    val cpuAffinity: String = "Unknown",
    val isForeground: Boolean = false,
    val runtime: String = "Unknown",
    val isNative: Boolean = false,
    val selinuxContext: String = "Unknown",
    val cgroup: String = "Unknown"
)

data class DetailedPackageInfo(
    val packageName: String,
    val label: String = "Unknown",
    val version: String = "Unknown",
    val versionCode: Long = 0,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,
    val abi: String = "Unknown",
    val installer: String = "Unknown",
    val installDate: String = "Unknown",
    val updateDate: String = "Unknown",
    val packagePath: String = "Unknown",
    val dataDir: String = "Unknown",
    val cacheDir: String = "Unknown",
    val nativeLibraryDir: String = "Unknown",
    val splitApks: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val sharedLibraries: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
    val signingInfo: String = "Unknown"
)

data class ServiceInfo(
    val activities: List<String> = emptyList(),
    val foregroundServices: List<String> = emptyList(),
    val backgroundServices: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val boundServices: List<String> = emptyList(),
    val activityCount: Int = 0,
    val serviceCount: Int = 0
)

data class DiagnosticsInfo(
    val shizukuStatus: String = "Disconnected",
    val permissionsGranted: Boolean = false,
    val selinuxMode: String = "Enforcing",
    val batteryOptimization: String = "Enabled",
    val appStandbyBucket: String = "Unknown",
    val workProfile: Boolean = false,
    val openFileDescriptors: Int = 0,
    val batteryUsageEstimate: String = "N/A",
    val capabilities: Map<String, Boolean> = emptyMap()
)

object ProcessInspectorEngine {
    private const val TAG = "ProcessInspectorEngine"
    private const val HISTORY_SIZE = 30

    private val _snapshotMap = mutableMapOf<Int, MutableStateFlow<ProcessSnapshot?>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val cpuHistoryMap = mutableMapOf<Int, MutableList<Float>>()
    private val memHistoryMap = mutableMapOf<Int, MutableList<Float>>()
    private val netHistoryMap = mutableMapOf<Int, MutableList<Float>>()

    init {
        // Shared polling loop that consumes SystemMonitorEngine.statsFlow
        scope.launch {
            SystemMonitorEngine.statsFlow.collect { stats ->
                if (stats == null) return@collect
                
                stats.processes.forEach { proc ->
                    val flow = _snapshotMap[proc.pid] ?: return@forEach
                    
                    val cpuHistory = cpuHistoryMap.getOrPut(proc.pid) { mutableListOf() }
                    cpuHistory.add(proc.cpuPercent)
                    if (cpuHistory.size > HISTORY_SIZE) cpuHistory.removeAt(0)
                    
                    val memHistory = memHistoryMap.getOrPut(proc.pid) { mutableListOf() }
                    memHistory.add(proc.memoryMb)
                    if (memHistory.size > HISTORY_SIZE) memHistory.removeAt(0)
                    
                    val netKB = (proc.rxSpeed + proc.txSpeed).toFloat() / 1024f
                    val netHistory = netHistoryMap.getOrPut(proc.pid) { mutableListOf() }
                    netHistory.add(netKB)
                    if (netHistory.size > HISTORY_SIZE) netHistory.removeAt(0)

                    flow.value = ProcessSnapshot(
                        pid = proc.pid,
                        timestamp = System.currentTimeMillis(),
                        cpuUsage = proc.cpuPercent,
                        memUsageMb = proc.memoryMb,
                        threads = proc.threads,
                        rxBytesPerSec = proc.rxSpeed,
                        txBytesPerSec = proc.txSpeed,
                        cpuHistory = cpuHistory.toList(),
                        memHistory = memHistory.toList(),
                        netHistory = netHistory.toList()
                    )
                }
            }
        }
    }

    fun startInspecting(pid: Int, context: Context): StateFlow<ProcessSnapshot?> {
        return _snapshotMap.getOrPut(pid) { MutableStateFlow(null) }
    }

    fun stopInspecting(pid: Int) {
        // No-op for now, clean up if needed
    }

    fun getDetailedProcessInfo(pid: Int): DetailedProcessInfo {
        return try {
            val stat = File("/proc/$pid/stat").readText().split(" ")
            val statusLines = File("/proc/$pid/status").readLines()
            val ppid = stat[3].toInt()
            val priority = stat[17].toInt()
            val nice = stat[18].toInt()
            
            val uidLine = statusLines.find { it.startsWith("Uid:") }?.split("\t")?.get(1) ?: "0"
            val uid = uidLine.toInt()
            
            val oomAdj = try { File("/proc/$pid/oom_score_adj").readText().trim() } catch(e: Exception) { "N/A" }
            val cgroup = try { File("/proc/$pid/cgroup").readLines().firstOrNull()?.substringAfterLast("/") ?: "N/A" } catch(e: Exception) { "N/A" }

            val state = when(stat[2]) {
                "R" -> "Running"
                "S" -> "Sleeping"
                "D" -> "Disk Sleep"
                "Z" -> "Zombie"
                "T" -> "Stopped"
                else -> "Unknown"
            }

            DetailedProcessInfo(
                pid = pid,
                ppid = ppid,
                uid = uid,
                state = state,
                priority = priority,
                nice = nice,
                oomAdj = oomAdj,
                cgroup = cgroup,
                selinuxContext = try { File("/proc/$pid/attr/current").readText().trim() } catch(e: Exception) { "Unknown" }
            )
        } catch (e: Exception) {
            DetailedProcessInfo(pid = pid)
        }
    }

    fun getDetailedPackageInfo(context: Context, packageName: String): DetailedPackageInfo {
        val pm = context.packageManager
        return try {
            val pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS)
            val ai = pm.getApplicationInfo(packageName, 0)
            
            DetailedPackageInfo(
                packageName = packageName,
                label = pm.getApplicationLabel(ai).toString(),
                version = pi.versionName ?: "Unknown",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong(),
                targetSdk = ai.targetSdkVersion,
                minSdk = if (Build.VERSION.SDK_INT >= 24) pi.applicationInfo.minSdkVersion else 0,
                packagePath = ai.sourceDir,
                dataDir = ai.dataDir,
                cacheDir = if (Build.VERSION.SDK_INT >= 24) context.cacheDir.absolutePath else "Unknown",
                installer = pm.getInstallerPackageName(packageName) ?: "System",
                permissions = pi.requestedPermissions?.toList() ?: emptyList()
            )
        } catch (e: Exception) {
            DetailedPackageInfo(packageName = packageName)
        }
    }

    fun getDiagnosticsInfo(context: Context, pid: Int): DiagnosticsInfo {
        val shizukuActive = rikka.shizuku.Shizuku.pingBinder()
        
        val fdCount = try {
            File("/proc/$pid/fd").list()?.size ?: 0
        } catch (e: Exception) { 0 }

        val standbyBucket = if (Build.VERSION.SDK_INT >= 28) {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                "Bucket: ${usm.appStandbyBucket}"
            } catch (e: Exception) { "N/A (No Permission)" }
        } else "N/A"

        return DiagnosticsInfo(
            shizukuStatus = if (shizukuActive) "Connected" else "Disconnected",
            permissionsGranted = true,
            selinuxMode = try { ShellExecutor.executeCommand("getenforce").trim() } catch(e: Exception) { "Unknown" },
            openFileDescriptors = fdCount,
            appStandbyBucket = standbyBucket,
            capabilities = mapOf(
                "Shizuku Connected" to shizukuActive,
                "Live CPU Monitoring" to true,
                "Live Memory Monitoring" to true,
                "Network Monitoring" to true,
                "Process Information" to true,
                "Package Information" to true,
                "File Descriptor Tracking" to (fdCount > 0)
            )
        )
    }

    fun getServiceInfo(context: Context, packageName: String): ServiceInfo {
        val pm = context.packageManager
        return try {
            val pi = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
            ServiceInfo(
                activities = pi.activities?.map { it.name } ?: emptyList(),
                foregroundServices = emptyList(), // Requires active scanning
                backgroundServices = pi.services?.map { it.name } ?: emptyList(),
                receivers = pi.receivers?.map { it.name } ?: emptyList(),
                providers = pi.providers?.map { it.name } ?: emptyList(),
                activityCount = pi.activities?.size ?: 0,
                serviceCount = pi.services?.size ?: 0
            )
        } catch (e: Exception) {
            ServiceInfo()
        }
    }
}
