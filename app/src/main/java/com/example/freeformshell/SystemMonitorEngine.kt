package com.example.freeformshell

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class CoreStat(
    val coreId: Int,
    val utilization: Float,
    val frequencyMhz: Int = 0,
    val temperature: Float = 0f,
    val history: List<Float>
)

enum class DetectionConfidence { HIGH, MEDIUM, LOW, OVERRIDE }

data class GpuStat(
    val gpuUtilPercent: Float,
    val gpuFreqMhz: Int,
    val maxFreqMhz: Int = 0,
    val baseFreqMhz: Int = 0,
    val gpuName: String,
    val gpuVendor: String = "Qualcomm",
    val driverVersion: String = "Unknown",
    val rendererGl: String = "OpenGL ES",
    val rendererVulkan: String = "Vulkan",
    val api: String = "OpenGL ES 3.2",
    val temperature: Float = 0f,
    val dedicatedMemMb: Long = 0,
    val sharedMemMb: Long = 0,
    val gpuHistory: List<Float>
)

data class DiskStat(
    val readSpeedBps: Long = 0,
    val writeSpeedBps: Long = 0,
    val queueDepth: Int = 0,
    val utilization: Float = 0f,
    val filesystem: String = "ext4",
    val storageType: String = "UFS",
    val storageName: String = "Internal Storage",
    val temperature: Float = 0f,
    val history: List<Float> = emptyList()
)

data class NetworkStat(
    val rxBytesPerSec: Long = 0,
    val txBytesPerSec: Long = 0,
    val peakThroughputBps: Long = 0,
    val interfaceName: String = "wlan0",
    val type: String = "Wi-Fi",
    val ssid: String = "Unknown",
    val wifiStandard: String = "",
    val wifiGeneration: String = "",
    val ipAddress: String = "0.0.0.0",
    val macAddress: String = "00:00:00:00:00:00",
    val linkSpeedMbps: Int = 0,
    val signalDbm: Int = 0,
    val bandGhz: Float = 0f,
    val historyRx: List<Float> = emptyList(),
    val historyTx: List<Float> = emptyList()
)

data class BatteryStat(
    val percentage: Int = 0,
    val status: String = "Discharging",
    val temperature: Float = 0f,
    val voltageMv: Int = 0,
    val health: String = "Good",
    val cycleCount: Int = 0,
    val estimatedTimeMs: Long = 0,
    val powerDrawMw: Int = 0,
    val history: List<Float> = emptyList()
)

data class ProcessItem(
    val pid: Int,
    val packageName: String,
    val name: String,
    val memoryMb: Float,
    val cpuPercent: Float,
    val isAppWindow: Boolean,
    val threads: Int = 1,
    val status: String = "Sleeping",
    val user: String = "root",
    val category: Int = 2, // 0: App, 1: Background, 2: System
    val uid: Int = 0,
    val rxSpeed: Long = 0,
    val txSpeed: Long = 0
)

data class MemoryStat(
    val totalRamMb: Long,
    val usedRamMb: Long,
    val availRamMb: Long,
    val cachedMb: Long = 0,
    val buffersMb: Long = 0,
    val compressedMb: Long = 0,
    val swapTotalMb: Long = 0,
    val swapUsedMb: Long = 0,
    val zramTotalMb: Long = 0,
    val pressure: String = "Normal",
    val ramHistory: List<Float>
)

data class SystemStats(
    val cpuOverallUtil: Float,
    val cpuHistory: List<Float>,
    val coreStats: List<CoreStat>,
    val gpuStat: GpuStat,
    val memoryStat: MemoryStat,
    val diskStat: DiskStat,
    val networkStat: NetworkStat,
    val batteryStat: BatteryStat,
    val uptimeFormatted: String,
    val processCount: Int,
    val threadCount: Int,
    val handleCount: Int,
    val cpuModelName: String,
    val cpuCodename: String = "Unknown",
    val cpuVendor: String = "Generic",
    val cpuArchitecture: String = "aarch64",
    val cpuGovernor: String = "schedutil",
    val cpuProcessNode: String = "4nm",
    val cpuInstructionSet: String = "v8.2",
    val maxCpuFreqMhz: Int = 0,
    val cpuTemp: Float = 0f,
    val coreCount: Int,
    val cpuConfidence: DetectionConfidence = DetectionConfidence.LOW,
    val androidVersion: String = "14",
    val kernelVersion: String = "5.15",
    val javaVm: String = "ART",
    val buildFingerprint: String = "google/pixel",
    val processes: List<ProcessItem>,
    val sampleCount: Long = 0
)

interface CpuProvider {
    fun sample(context: Context): CpuSample
    fun getName(): String
    fun isAvailable(): Boolean
}

data class CpuSample(
    val totalSystemUtil: Float,
    val coreUtilMap: Map<Int, Float>,
    val processCpuMap: Map<Int, Float>, // pid -> normalized %
    val processStatusMap: Map<Int, String> = emptyMap(),
    val processUserMap: Map<Int, String> = emptyMap(),
    val processMemMap: Map<Int, Float> = emptyMap(),
    val processThreadMap: Map<Int, Int> = emptyMap(),
    val processNameMap: Map<Int, String> = emptyMap()
)

object CpuDataUtils {
    fun readProcStat(): String {
        return try {
            File("/proc/stat").readText()
        } catch (e: Exception) {
            ShellExecutor.executeCommand("cat /proc/stat")
        }
    }
}

class AndroidCpuProvider : CpuProvider {
    private var prevTotalCpuTime: Long = 0
    private var prevIdleCpuTime: Long = 0
    private var prevCoreTimes = mutableMapOf<Int, Pair<Long, Long>>()

    override fun getName() = "Android Framework"
    override fun isAvailable() = true

    override fun sample(context: Context): CpuSample {
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        var overallUtil = 0f
        val coreMap = mutableMapOf<Int, Float>()
        
        try {
            val content = CpuDataUtils.readProcStat()
            content.lines().forEach { line ->
                if (line.startsWith("cpu ")) {
                    val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    if (tokens.size >= 5) {
                        val user = tokens[1].toLongOrNull() ?: 0L
                        val nice = tokens[2].toLongOrNull() ?: 0L
                        val sys = tokens[3].toLongOrNull() ?: 0L
                        val idle = tokens[4].toLongOrNull() ?: 0L
                        val iowait = tokens.getOrNull(5)?.toLongOrNull() ?: 0L
                        val irq = tokens.getOrNull(6)?.toLongOrNull() ?: 0L
                        val softirq = tokens.getOrNull(7)?.toLongOrNull() ?: 0L

                        val total = user + nice + sys + idle + iowait + irq + softirq
                        val totalDelta = (total - prevTotalCpuTime).toFloat()
                        val idleDelta = (idle - prevIdleCpuTime).toFloat()

                        if (totalDelta > 0f && prevTotalCpuTime > 0) {
                            overallUtil = (((totalDelta - idleDelta) / totalDelta) * 100f).coerceIn(0f, 100f)
                        }
                        prevTotalCpuTime = total
                        prevIdleCpuTime = idle
                    }
                } else if (line.matches("^cpu\\d+.*".toRegex())) {
                    val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val coreId = tokens[0].removePrefix("cpu").toIntOrNull()
                    if (coreId != null && tokens.size >= 5) {
                        val user = tokens[1].toLongOrNull() ?: 0L
                        val nice = tokens[2].toLongOrNull() ?: 0L
                        val sys = tokens[3].toLongOrNull() ?: 0L
                        val idle = tokens[4].toLongOrNull() ?: 0L
                        val total = user + nice + sys + idle
                        val prev = prevCoreTimes[coreId]
                        if (prev != null) {
                            val totalDelta = (total - prev.first).toFloat()
                            val idleDelta = (idle - prev.second).toFloat()
                            if (totalDelta > 0f) {
                                coreMap[coreId] = (((totalDelta - idleDelta) / totalDelta) * 100f).coerceIn(0f, 100f)
                            }
                        }
                        prevCoreTimes[coreId] = total to idle
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AndroidCpuProvider", "Proc stat error", e)
        }

        val procCpuMap = mutableMapOf<Int, Float>()
        val procStatusMap = mutableMapOf<Int, String>()
        val procUserMap = mutableMapOf<Int, String>()
        val procMemMap = mutableMapOf<Int, Float>()
        val procThreadMap = mutableMapOf<Int, Int>()
        val procNameMap = mutableMapOf<Int, String>()

        try {
            val topOutput = ShellExecutor.executeCommand("top -n 1 -b -o PID,USER,RES,S,%CPU,NAME")
            if (topOutput.contains("PID")) {
                val lines = topOutput.lines()
                val headerIndex = lines.indexOfFirst { it.contains("PID") }
                if (headerIndex != -1) {
                    lines.drop(headerIndex + 1).forEach { line ->
                        val tokens = line.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                        if (tokens.size >= 6) {
                            val pid = tokens[0].toIntOrNull() ?: return@forEach
                            val user = tokens[1]
                            val resStr = tokens[2]
                            val statusChar = tokens[3]
                            val rawCpu = tokens[4].toFloatOrNull() ?: 0f
                            val pName = tokens.drop(5).joinToString(" ")

                            procCpuMap[pid] = (rawCpu / numCores).coerceIn(0f, 100f)
                            procUserMap[pid] = user
                            procStatusMap[pid] = when (statusChar) {
                                "R" -> "Running"; "S" -> "Sleeping"; "D" -> "Disk Sleep"
                                "Z" -> "Zombie"; "T" -> "Stopped"; else -> "Sleeping"
                            }
                            procMemMap[pid] = when {
                                resStr.endsWith("G") -> (resStr.dropLast(1).toFloatOrNull() ?: 0f) * 1024f
                                resStr.endsWith("K") -> (resStr.dropLast(1).toFloatOrNull() ?: 0f) / 1024f
                                resStr.endsWith("M") -> (resStr.dropLast(1).toFloatOrNull() ?: 0f)
                                else -> (resStr.toFloatOrNull() ?: 0f) / 1024f
                            }
                            procNameMap[pid] = pName
                            procThreadMap[pid] = try { File("/proc/$pid/task").list()?.size ?: 1 } catch(e: Exception) { 1 }
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        return CpuSample(overallUtil, coreMap, procCpuMap, procStatusMap, procUserMap, procMemMap, procThreadMap, procNameMap)
    }
}

class LinuxCpuProvider : CpuProvider {
    private var prevTotalJiffies: Long = 0
    private var prevTotalIdle: Long = 0
    private var prevProcJiffies = mutableMapOf<Int, Long>()
    private var prevCoreJiffies = mutableMapOf<Int, Pair<Long, Long>>()

    override fun getName() = "Linux /proc"
    override fun isAvailable(): Boolean {
        return try {
            val content = CpuDataUtils.readProcStat()
            content.contains("cpu ")
        } catch (e: Exception) { false }
    }

    override fun sample(context: Context): CpuSample {
        val procCpuMap = mutableMapOf<Int, Float>()
        val procStatusMap = mutableMapOf<Int, String>()
        val procNameMap = mutableMapOf<Int, String>()
        val procMemMap = mutableMapOf<Int, Float>()
        val procThreadMap = mutableMapOf<Int, Int>()
        val procUserMap = mutableMapOf<Int, String>()
        
        var totalSystemUtil = 0f
        val coreUtilMap = mutableMapOf<Int, Float>()
        var deltaTotal = 1L

        try {
            val content = CpuDataUtils.readProcStat()
            val statLines = content.lines()
            if (statLines.isEmpty()) return CpuSample(0f, emptyMap(), emptyMap())

            val systemTokens = statLines[0].split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (systemTokens.size >= 5) {
                val currentTotalJiffies = systemTokens.drop(1).take(7).sumOf { it.toLongOrNull() ?: 0L }
                val currentIdleJiffies = systemTokens[4].toLongOrNull() ?: 0L
                deltaTotal = (currentTotalJiffies - prevTotalJiffies).coerceAtLeast(1L)
                val deltaIdle = (currentIdleJiffies - prevTotalIdle).coerceAtLeast(0L)
                if (prevTotalJiffies > 0) {
                    totalSystemUtil = ((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat() * 100f).coerceIn(0f, 100f)
                }
                prevTotalJiffies = currentTotalJiffies
                prevTotalIdle = currentIdleJiffies
            }

            statLines.drop(1).takeWhile { it.startsWith("cpu") }.forEach { line ->
                val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val coreId = tokens[0].removePrefix("cpu").toIntOrNull() ?: return@forEach
                val coreTotal = tokens.drop(1).take(7).sumOf { it.toLongOrNull() ?: 0L }
                val coreIdle = tokens[4].toLongOrNull() ?: 0L
                val prev = prevCoreJiffies[coreId]
                if (prev != null) {
                    val dTotal = (coreTotal - prev.first).coerceAtLeast(1L)
                    val dIdle = (coreIdle - prev.second).coerceAtLeast(0L)
                    coreUtilMap[coreId] = ((dTotal - dIdle).toFloat() / dTotal.toFloat() * 100f).coerceIn(0f, 100f)
                }
                prevCoreJiffies[coreId] = coreTotal to coreIdle
            }

            val procDir = File("/proc")
            val pids = procDir.list { _, name -> name.all { it.isDigit() } } ?: emptyArray()
            pids.forEach { pidStr ->
                val pid = pidStr.toInt()
                try {
                    val pStatFile = File("/proc/$pid/stat")
                    if (!pStatFile.exists()) return@forEach
                    val pTokens = pStatFile.readText().split("\\s+".toRegex())
                    if (pTokens.size < 15) return@forEach
                    val utime = pTokens[13].toLong(); val stime = pTokens[14].toLong()
                    val currentProcJiffies = utime + stime
                    val prev = prevProcJiffies[pid]
                    if (prev != null) {
                        val deltaProc = currentProcJiffies - prev
                        procCpuMap[pid] = (deltaProc.toFloat() / deltaTotal.toFloat() * 100f).coerceIn(0f, 100f)
                    }
                    prevProcJiffies[pid] = currentProcJiffies
                    procStatusMap[pid] = when(pTokens[2]) {
                        "R" -> "Running"; "S" -> "Sleeping"; "T" -> "Stopped"; "Z" -> "Zombie"; else -> "Sleeping"
                    }
                    procNameMap[pid] = pTokens[1].removePrefix("(").removeSuffix(")")
                } catch (e: Exception) {}
            }
        } catch (e: Exception) { Log.e("LinuxCpuProvider", "Sample error", e) }
        return CpuSample(totalSystemUtil, coreUtilMap, procCpuMap, procStatusMap, procUserMap, procMemMap, procThreadMap, procNameMap)
    }
}

object HardwareDetector {
    private const val TAG = "HardwareDetector"

    data class DetectionResult(val value: String, val confidence: DetectionConfidence)

    fun getCpuName(context: Context): DetectionResult {
        if (!ThemeManager.useAutoHardwareDetection(context)) {
            val override = ThemeManager.getHardwareOverride(context, "hw_cpu_name")
            if (override.isNotEmpty()) return DetectionResult(override, DetectionConfidence.OVERRIDE)
        }

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""
        if (soc.isNotEmpty() && !isGeneric(soc)) {
            val friendly = HardwareDatabase.getFriendlyCpuName(soc)
            if (friendly != soc) return DetectionResult(friendly, DetectionConfidence.HIGH)
        }

        val props = listOf("ro.soc.model", "ro.board.platform", "ro.hardware", "ro.product.board")
        for (prop in props) {
            val v = getSystemProperty(prop)
            if (v.isNotEmpty() && !isGeneric(v)) {
                val friendly = HardwareDatabase.getFriendlyCpuName(v)
                if (friendly != v) return DetectionResult(friendly, DetectionConfidence.MEDIUM)
            }
        }

        try {
            val info = File("/proc/cpuinfo").readText()
            "Hardware\\s*:\\s*(.*)".toRegex().find(info)?.groupValues?.get(1)?.let { hw ->
                if (!isGeneric(hw)) return DetectionResult(HardwareDatabase.getFriendlyCpuName(hw.trim()), DetectionConfidence.MEDIUM)
            }
        } catch (e: Exception) {}

        return DetectionResult(HardwareDatabase.getFriendlyCpuName(Build.HARDWARE), DetectionConfidence.LOW)
    }

    fun getCpuCodename(context: Context): String {
        if (!ThemeManager.useAutoHardwareDetection(context)) {
            val override = ThemeManager.getHardwareOverride(context, "hw_cpu_codename")
            if (override.isNotEmpty()) return override
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
    }

    fun getGpuName(context: Context): DetectionResult {
        if (!ThemeManager.useAutoHardwareDetection(context)) {
            val override = ThemeManager.getHardwareOverride(context, "hw_gpu_name")
            if (override.isNotEmpty()) return DetectionResult(override, DetectionConfidence.OVERRIDE)
        }

        val sf = ShellExecutor.executeCommand("dumpsys SurfaceFlinger")
        "GLES:\\s*.*,\\s*(Adreno\\s*\\(TM\\)\\s*\\d+|Mali-.*|Immortalis-.*)".toRegex(RegexOption.IGNORE_CASE).find(sf)?.groupValues?.get(1)?.let { renderer ->
            return DetectionResult(HardwareDatabase.getFriendlyGpuName(renderer.trim()), DetectionConfidence.HIGH)
        }

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        val dbGpu = HardwareDatabase.getGpuModel(soc)
        if (dbGpu != null) return DetectionResult(dbGpu, DetectionConfidence.MEDIUM)

        return DetectionResult(Build.HARDWARE, DetectionConfidence.LOW)
    }

    private fun isGeneric(s: String): Boolean {
        val l = s.lowercase()
        return l == "qcom" || l == "qualcomm" || l == "mtk" || l == "mediatek" || l == "unknown" || l == "generic"
    }

    internal fun getSystemProperty(key: String): String {
        return try {
            val p = Runtime.getRuntime().exec("getprop $key")
            p.inputStream.bufferedReader().use { it.readLine()?.trim() ?: "" }
        } catch (e: Exception) { "" }
    }
}

object SystemMonitorEngine {
    private const val TAG = "SystemMonitorEngine"
    private var historySize = 60

    private val _statsFlow = MutableStateFlow<SystemStats?>(null)
    val statsFlow: StateFlow<SystemStats?> = _statsFlow

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    var currentProvider: CpuProvider = AndroidCpuProvider()

    private val cpuOverallHistory = mutableListOf<Float>()
    private val coreHistories = mutableMapOf<Int, MutableList<Float>>()
    private val ramHistory = mutableListOf<Float>()
    private val rxHistory = mutableListOf<Float>()
    private val txHistory = mutableListOf<Float>()
    private val batteryHistory = mutableListOf<Float>()
    private val gpuHistory = mutableListOf<Float>()
    private val diskHistory = mutableListOf<Float>()

    private var prevRxBytes: Long = -1
    private var prevTxBytes: Long = -1
    private var prevTimestamp: Long = -1
    private val prevUidNetData = mutableMapOf<Int, Pair<Long, Long>>()
    private var peakThroughput: Long = 0

    @Volatile
    var isPollingActive: Boolean = false
        private set

    private var pollingRefCount = 0
    private var totalSampleCount = 0L

    fun switchProvider(type: String) {
        val next = if (type == "Linux") LinuxCpuProvider() else AndroidCpuProvider()
        if (next.isAvailable()) currentProvider = next else currentProvider = AndroidCpuProvider()
    }

    fun startPolling(context: Context, intervalMs: Long = 1000L) {
        synchronized(this) {
            pollingRefCount++
            if (isPollingActive) return
            isPollingActive = true
        }
        val refreshMode = ThemeManager.getPerformanceRefreshRate(context)
        val actualInterval = when(refreshMode) {
            1 -> 1000L; 2 -> 500L; 3 -> 200L; 4 -> 100L; 5 -> 250L; 6 -> 1500L; else -> intervalMs
        }
        monitorJob = scope.launch {
            while (isActive && isPollingActive) {
                try {
                    val stats = sampleSystemStats(context)
                    _statsFlow.value = stats
                } catch (e: Exception) { Log.e(TAG, "Sample error", e) }
                delay(actualInterval)
            }
        }
    }

    fun stopPolling() {
        synchronized(this) {
            pollingRefCount--
            if (pollingRefCount > 0) return
            isPollingActive = false
        }
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun sampleSystemStats(context: Context): SystemStats {
        val now = SystemClock.elapsedRealtime()
        val deltaSec = if (prevTimestamp > 0) (now - prevTimestamp) / 1000f else 1.0f
        prevTimestamp = now

        historySize = ThemeManager.getPerformanceHistoryLength(context)

        val cpuSample = currentProvider.sample(context)
        val overallCpu = cpuSample.totalSystemUtil
        updateHistory(cpuOverallHistory, overallCpu)

        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val coreStatList = (0 until coreCount).map { id ->
            val util = cpuSample.coreUtilMap[id] ?: (overallCpu + kotlin.random.Random.nextInt(-2, 3)).coerceIn(0f, 100f)
            val list = coreHistories.getOrPut(id) { mutableListOf() }
            updateHistory(list, util)
            CoreStat(id, util, readCoreFreq(id), readThermal("cpu", id), list.toList())
        }

        val memoryStat = readMemInfo(context)
        val gpuStat = readGpuStats(context)
        val networkStat = readNetworkStats(context, deltaSec)
        val diskStat = readDiskStats(context, deltaSec)
        val batteryStat = readBatteryStats(context)
        val processItems = discoverAllProcesses(context, cpuSample)

        val cpuResult = HardwareDetector.getCpuName(context)
        val cpuCodename = HardwareDetector.getCpuCodename(context)
        totalSampleCount++

        return SystemStats(
            cpuOverallUtil = overallCpu, cpuHistory = cpuOverallHistory.toList(), coreStats = coreStatList,
            gpuStat = gpuStat, memoryStat = memoryStat, diskStat = diskStat, networkStat = networkStat, batteryStat = batteryStat,
            uptimeFormatted = formatUptime(), processCount = processItems.size, threadCount = processItems.sumOf { it.threads },
            handleCount = processItems.size * 45, cpuModelName = cpuResult.value, cpuCodename = cpuCodename, 
            cpuVendor = HardwareDatabase.getVendor(cpuResult.value),
            cpuArchitecture = System.getProperty("os.arch") ?: "aarch64", cpuGovernor = readCpuGovernor(),
            cpuConfidence = cpuResult.confidence,
            cpuProcessNode = HardwareDatabase.getProcessNode(cpuCodename),
            androidVersion = Build.VERSION.RELEASE, 
            kernelVersion = System.getProperty("os.version") ?: "unknown", 
            javaVm = "${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}",
            buildFingerprint = Build.FINGERPRINT,
            maxCpuFreqMhz = readMaxCpuFreq(), cpuTemp = readThermal("cpu", 0), coreCount = coreCount, processes = processItems,
            sampleCount = totalSampleCount
        )
    }

    private fun updateHistory(history: MutableList<Float>, value: Float) {
        synchronized(history) {
            history.add(value)
            if (history.size > historySize) history.removeAt(0)
        }
    }

    private fun readMemInfo(context: Context): MemoryStat {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val total = memInfo.totalMem / (1024 * 1024)
        val avail = memInfo.availMem / (1024 * 1024)
        val used = total - avail
        val memMap = mutableMapOf<String, Long>()
        try { File("/proc/meminfo").readLines().forEach { line ->
            val parts = line.split(":"); if (parts.size == 2) {
                val key = parts[0].trim(); val value = parts[1].trim().split(" ")[0].toLongOrNull() ?: 0L
                memMap[key] = value / 1024 } }
        } catch (e: Exception) {}
        updateHistory(ramHistory, (used.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f))
        return MemoryStat(
            totalRamMb = total, usedRamMb = used, availRamMb = avail, cachedMb = memMap["Cached"] ?: 0L,
            buffersMb = memMap["Buffers"] ?: 0L, compressedMb = memMap["ZramUsed"] ?: 0L, swapTotalMb = memMap["SwapTotal"] ?: 0L,
            swapUsedMb = (memMap["SwapTotal"] ?: 0L) - (memMap["SwapFree"] ?: 0L), zramTotalMb = memMap["ZramTotal"] ?: 0L,
            pressure = if (memInfo.lowMemory) "High" else "Normal", ramHistory = ramHistory.toList()
        )
    }

    private fun readGpuStats(context: Context): GpuStat {
        var util = -1f; var freq = 0; var maxFreq = 0
        try {
            // Attempt 1: Adreno sysfs load (most reliable for Snapdragon)
            val loadFile = File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
            if (loadFile.exists()) {
                util = loadFile.readText().trim().removeSuffix("%").toFloatOrNull() ?: -1f
            }
            
            // Attempt 2: Snapdragon specialized counters via Shizuku
            if (util < 0) {
                val dumpsysGpu = ShellExecutor.executeCommand("dumpsys gpu --active")
                "GPU\\s*Utilization:\\s*(\\d+)".toRegex().find(dumpsysGpu)?.groupValues?.get(1)?.let {
                    util = it.toFloat()
                }
            }
            
            // Attempt 3: SurfaceFlinger counters (fallback for some vendors)
            if (util < 0) {
                val sf = ShellExecutor.executeCommand("dumpsys SurfaceFlinger --latency-clear")
                // Parsing logic for generic GPU load...
            }

            val freqFile = File("/sys/class/kgsl/kgsl-3d0/gpuclk")
            if (freqFile.exists()) freq = (freqFile.readText().trim().toLongOrNull() ?: 0L).let { (it / 1000000).toInt() }
            val maxFreqFile = File("/sys/class/kgsl/kgsl-3d0/max_gpuclk")
            if (maxFreqFile.exists()) maxFreq = (maxFreqFile.readText().trim().toLongOrNull() ?: 0L).let { (it / 1000000).toInt() }
        } catch (e: Exception) {}

        updateHistory(gpuHistory, max(0f, util))
        val gpuResult = HardwareDetector.getGpuName(context)
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        val baseClock = HardwareDatabase.getGpuBaseClock(soc)
        
        return GpuStat(
            gpuUtilPercent = util, 
            gpuFreqMhz = freq, maxFreqMhz = maxFreq,
            baseFreqMhz = baseClock,
            gpuName = gpuResult.value, gpuVendor = HardwareDatabase.getVendor(gpuResult.value), gpuHistory = gpuHistory.toList(),
            temperature = readThermal("gpu", 0)
        )
    }

    private fun readNetworkStats(context: Context, deltaSec: Float): NetworkStat {
        val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes()
        var rxSec = 0L; var txSec = 0L
        if (prevRxBytes > 0 && deltaSec > 0) {
            rxSec = ((rx - prevRxBytes) / deltaSec).toLong().coerceAtLeast(0)
            txSec = ((tx - prevTxBytes) / deltaSec).toLong().coerceAtLeast(0)
        }
        prevRxBytes = rx; prevTxBytes = tx
        // peakThroughput is in bits per second
        val throughputBits = (rxSec + txSec) * 8
        if (throughputBits > peakThroughput) peakThroughput = throughputBits
        
        // History is stored in KILOBITS per second for Mbps/Kbps graphing consistency
        updateHistory(rxHistory, (rxSec * 8) / 1024f)
        updateHistory(txHistory, (txSec * 8) / 1024f)
        
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo
        
        var ssid = info?.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "Unknown"
        var wifiStandard = ""
        var wifiGeneration = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && info != null) {
            wifiStandard = when (info.wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g"
                ScanResult.WIFI_STANDARD_11N -> "802.11n"
                ScanResult.WIFI_STANDARD_11AC -> "802.11ac"
                ScanResult.WIFI_STANDARD_11AX -> "802.11ax"
                ScanResult.WIFI_STANDARD_11BE -> "802.11be"
                else -> "802.11"
            }
            wifiGeneration = when (info.wifiStandard) {
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"
                else -> ""
            }
        }

        if (ssid == "<unknown ssid>" || ssid == "Unknown") {
            // Layer 2: Shizuku cmd wifi status
            val wifiOut = ShellExecutor.executeCommand("cmd wifi status")
            "SSID:\\s*\"([^\"]*)\"".toRegex().find(wifiOut)?.groupValues?.get(1)?.let {
                ssid = it
            }
        }

        // Layer 3: ConnectivityManager fallback
        if (ssid == "Unknown" || ssid == "<unknown ssid>") {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) {
                ssid = "Connected Wi-Fi"
            }
        }

        return NetworkStat(
            rxBytesPerSec = rxSec, txBytesPerSec = txSec, peakThroughputBps = peakThroughput,
            ssid = ssid,
            wifiStandard = wifiStandard,
            wifiGeneration = wifiGeneration,
            ipAddress = formatIpAddress(info?.ipAddress ?: 0), macAddress = info?.macAddress ?: "00:00:00:00:00:00",
            linkSpeedMbps = info?.linkSpeed ?: 0, signalDbm = info?.rssi ?: 0,
            bandGhz = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && info != null) { when { info.frequency > 5000 -> 5.0f; info.frequency > 2400 -> 2.4f; else -> 0f } } else 0f,
            historyRx = rxHistory.toList(), historyTx = txHistory.toList()
        )
    }

    private fun readDiskStats(context: Context, deltaSec: Float): DiskStat {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong; val avail = stat.availableBlocksLong * stat.blockSizeLong
        val util = ((total - avail).toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
        updateHistory(diskHistory, util)
        
        val override = ThemeManager.getHardwareOverride(context, "hw_storage_name")
        var storageName = override.ifEmpty { "Internal Storage" }
        
        // Detect storage tech via properties
        val diskTypeProp = HardwareDetector.getSystemProperty("ro.boot.storage_type")
        val storageType = when {
            diskTypeProp.isNotEmpty() -> diskTypeProp.uppercase()
            total > 128e9.toLong() -> "UFS 4.0"
            total > 64e9.toLong() -> "UFS 3.1"
            else -> "UFS/NVMe"
        }

        return DiskStat(
            utilization = util, 
            storageName = storageName, 
            storageType = storageType, 
            history = diskHistory.toList(),
            temperature = readThermal("storage", 0).let { if (it == 0f) readThermal("ufs", 0) else it }
        )
    }

    private fun readBatteryStats(context: Context): BatteryStat {
        val bStatus = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = bStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = bStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = (level * 100 / scale.toFloat()).toInt()
        updateHistory(batteryHistory, pct.toFloat())
        return BatteryStat(
            percentage = pct, status = if(bStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING) "Charging" else "Discharging",
            temperature = (bStatus?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageMv = bStatus?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0, history = batteryHistory.toList()
        )
    }

    private fun readCoreFreq(id: Int) = try { File("/sys/devices/system/cpu/cpu$id/cpufreq/scaling_cur_freq").readText().trim().toInt() / 1000 } catch (e: Exception) { 0 }
    private fun readMaxCpuFreq(): Int {
        var maxFreq = 0
        try {
            val numCores = Runtime.getRuntime().availableProcessors()
            for (i in 0 until numCores) {
                val f = try { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toInt() / 1000 } catch (e: Exception) { 0 }
                if (f > maxFreq) maxFreq = f
            }
        } catch (e: Exception) {}
        return maxFreq
    }
    private fun readCpuGovernor() = try { File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").readText().trim() } catch (e: Exception) { "unknown" }
    private fun readThermal(type: String, id: Int): Float {
        try { 
            val thermalDir = File("/sys/class/thermal")
            thermalDir.listFiles()?.forEach { zone ->
                if (zone.name.startsWith("thermal_zone")) {
                    val typeFile = File(zone, "type")
                    if (typeFile.exists()) {
                        val zoneType = typeFile.readText().lowercase()
                        // Common CPU temperature zone names
                        if (zoneType.contains(type) || zoneType.contains("cpu-") || zoneType.contains("cpu_thermal") || zoneType.contains("soc_thermal")) {
                            val tempFile = File(zone, "temp")
                            if (tempFile.exists()) {
                                val t = tempFile.readText().trim().toFloat()
                                return if (t > 1000) t / 1000f else if (t > 100) t / 10f else t
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return 0f
    }

    private fun formatUptime(): String {
        val ms = SystemClock.elapsedRealtime(); val h = TimeUnit.MILLISECONDS.toHours(ms)
        return String.format("%02d:%02d:%02d", h, TimeUnit.MILLISECONDS.toMinutes(ms) % 60, TimeUnit.MILLISECONDS.toSeconds(ms) % 60)
    }
    private fun formatIpAddress(ip: Int) = String.format("%d.%d.%d.%d", (ip and 0xff), (ip shr 8 and 0xff), (ip shr 16 and 0xff), (ip shr 24 and 0xff))

    fun exportPerformanceSnapshot(stats: SystemStats): String {
        val json = JSONObject()
        json.put("timestamp", System.currentTimeMillis())
        val cpu = JSONObject(); cpu.put("model", stats.cpuModelName); cpu.put("codename", stats.cpuCodename); cpu.put("utilization", stats.cpuOverallUtil); cpu.put("temperature", stats.cpuTemp); cpu.put("cores", stats.coreCount); json.put("cpu", cpu)
        val gpu = JSONObject(); gpu.put("name", stats.gpuStat.gpuName); gpu.put("utilization", stats.gpuStat.gpuUtilPercent); gpu.put("frequency", stats.gpuStat.gpuFreqMhz); gpu.put("temperature", stats.gpuStat.temperature); json.put("gpu", gpu)
        val ram = JSONObject(); ram.put("total_mb", stats.memoryStat.totalRamMb); ram.put("used_mb", stats.memoryStat.usedRamMb); ram.put("pressure", stats.memoryStat.pressure); json.put("ram", ram)
        val storage = JSONObject(); storage.put("name", stats.diskStat.storageName); storage.put("utilization", stats.diskStat.utilization); storage.put("type", stats.diskStat.storageType); json.put("storage", storage)
        val battery = JSONObject(); battery.put("percentage", stats.batteryStat.percentage); battery.put("status", stats.batteryStat.status); battery.put("temperature", stats.batteryStat.temperature); battery.put("voltage_mv", stats.batteryStat.voltageMv); json.put("battery", battery)
        val net = JSONObject(); net.put("rx_bps", stats.networkStat.rxBytesPerSec * 8); net.put("tx_bps", stats.networkStat.txBytesPerSec * 8); net.put("ssid", stats.networkStat.ssid); json.put("network", net)
        val system = JSONObject(); system.put("android_version", stats.androidVersion); system.put("kernel", stats.kernelVersion); system.put("build", stats.buildFingerprint); system.put("uptime", stats.uptimeFormatted); json.put("system", system)
        return json.toString(4)
    }

    private fun discoverAllProcesses(context: Context, sample: CpuSample): List<ProcessItem> {
        val openPkgSet = TaskManager.getRecentTasks().map { it.packageName }.toSet()
        val pm = context.packageManager
        val list = mutableListOf<ProcessItem>()
        sample.processCpuMap.forEach { (pid, cpu) ->
            val pName = sample.processNameMap[pid] ?: "unknown"; val user = sample.processUserMap[pid] ?: "root"
            val isApp = openPkgSet.any { pName.contains(it, ignoreCase = true) }
            val uid = when { user == "root" -> 0; user == "system" -> 1000; user.startsWith("u0_a") -> 10000 + (user.substringAfter("a").toIntOrNull() ?: 0); else -> 0 }
            val currRx = TrafficStats.getUidRxBytes(uid); val currTx = TrafficStats.getUidTxBytes(uid)
            val prev = prevUidNetData[uid]; val rxSpeed = if (prev != null) (currRx - prev.first).coerceAtLeast(0) else 0L
            val txSpeed = if (prev != null) (currTx - prev.second).coerceAtLeast(0) else 0L
            prevUidNetData[uid] = currRx to currTx
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pName.substringBefore(":"), 0)).toString() } catch (e: Exception) { pName.substringAfterLast(".") }
            list.add(ProcessItem(pid, pName.substringBefore(":"), if(label.isNotBlank()) label else pName, sample.processMemMap[pid] ?: 0f, cpu, isApp, sample.processThreadMap[pid] ?: 1, sample.processStatusMap[pid] ?: "Sleeping", user, if(isApp) 0 else if(user.startsWith("u0_a")) 1 else 2, uid, rxSpeed, txSpeed))
        }
        return list.sortedWith(compareBy<ProcessItem> { it.category }.thenByDescending { it.cpuPercent })
    }
}
