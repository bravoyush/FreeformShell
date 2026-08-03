package com.example.freeformshell

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val HISTORY_SIZE = 30

@Composable
fun ProcessInspectorWindow(
    windowState: InspectorWindowState,
    isCompact: Boolean,
    onClose: () -> Unit
) {
    val proc = windowState.process
    if (isCompact) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                ProcessInspectorContent(proc, onClose)
            }
        }
    } else {
        // Desktop floating window with move and resize support
        var offset by remember { mutableStateOf(windowState.position) }
        var size by remember { mutableStateOf(windowState.size) }

        Box(
            modifier = Modifier
                .offset(offset.x, offset.y)
                .width(size.width)
                .height(size.height)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .shadow(12.dp, RoundedCornerShape(12.dp))
        ) {
            ProcessInspectorContent(
                proc = proc, 
                onClose = onClose,
                onHeaderDrag = { dragAmount ->
                    offset = DpOffset(offset.x + dragAmount.x.dp, offset.y + dragAmount.y.dp)
                    windowState.position = offset
                }
            )
            
            // Resize Handle (Bottom-Right)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            size = DpSize(
                                (size.width + dragAmount.x.dp).coerceAtLeast(400.dp),
                                (size.height + dragAmount.y.dp).coerceAtLeast(300.dp)
                            )
                            windowState.size = size
                        }
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    Icons.Default.FilterList, 
                    null,
                    modifier = Modifier.size(12.dp).align(Alignment.BottomEnd).padding(4.dp).rotate(45f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun ProcessInspectorActivityContent(
    proc: ProcessItem,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        ProcessInspectorContent(proc, onClose)
    }
}

@Composable
private fun ProcessInspectorContent(
    proc: ProcessItem,
    onClose: () -> Unit,
    onHeaderDrag: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
) {
    val context = LocalContext.current
    val snapshot by ProcessInspectorEngine.startInspecting(proc.pid, context).collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current
    
    val tabs = listOf("General", "Resources", "Process", "APK", "Services", "Diagnostics")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (onHeaderDrag != null) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onHeaderDrag(dragAmount)
                            }
                        }
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(proc.packageName, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    proc.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "PID ${proc.pid} | ${proc.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close")
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> GeneralTab(proc)
                1 -> ResourcesTab(proc.pid, proc.packageName, snapshot)
                2 -> ProcessTab(proc.pid)
                3 -> ApkTab(context, proc.packageName)
                4 -> ServicesTab(proc.packageName)
                5 -> DiagnosticsTab(proc.pid)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { 
                val report = generateProcessReport(proc, snapshot)
                clipboardManager.setText(AnnotatedString(report))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Information")
            }

            Row {
                TextButton(onClick = { /* Export */ }) {
                    Icon(Icons.Default.IosShare, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onClose) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun GeneralTab(proc: ProcessItem) {
    val context = LocalContext.current
    val packageInfo = remember(proc.packageName) { ProcessInspectorEngine.getDetailedPackageInfo(context, proc.packageName) }
    val processInfo = remember(proc.pid) { ProcessInspectorEngine.getDetailedProcessInfo(proc.pid) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            InfoSection("Identification") {
                InfoRow("Application Name", packageInfo.label)
                InfoRow("Package Name", packageInfo.packageName)
                InfoRow("Process Name", proc.name)
                InfoRow("PID", proc.pid.toString())
                InfoRow("UID", processInfo.uid.toString())
                InfoRow("User", proc.user)
            }
        }
        item {
            InfoSection("Version Information") {
                InfoRow("Version", packageInfo.version)
                InfoRow("Version Code", packageInfo.versionCode.toString())
                InfoRow("Target SDK", packageInfo.targetSdk.toString())
                InfoRow("Min SDK", packageInfo.minSdk.toString())
            }
        }
        item {
            InfoSection("Environment") {
                InfoRow("ABI", packageInfo.abi)
                InfoRow("Installer", packageInfo.installer)
                InfoRow("Install Date", packageInfo.installDate)
                InfoRow("Update Date", packageInfo.updateDate)
            }
        }
    }
}

@Composable
private fun ResourcesTab(pid: Int, packageName: String, snapshot: ProcessSnapshot?) {
    val context = LocalContext.current
    val diag = remember(pid) { ProcessInspectorEngine.getDiagnosticsInfo(context, pid) }
    val serviceInfo = remember(packageName) { ProcessInspectorEngine.getServiceInfo(context, packageName) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            InfoSection("Live Metrics") {
                if (snapshot != null) {
                    MetricResourceRow("CPU", "${String.format("%.1f", snapshot.cpuUsage)}%", snapshot.cpuHistory, Color(0xFF00A2ED))
                    MetricResourceRow("Memory", "${String.format("%.1f", snapshot.memUsageMb)} MB", snapshot.memHistory, Color(0xFF9E9E9E))
                    MetricResourceRow("Network", formatNetworkSpeed(snapshot.rxBytesPerSec, snapshot.txBytesPerSec), snapshot.netHistory, Color(0xFF4CAF50))
                    MetricResourceRow("Threads", snapshot.threads.toString(), snapshot.threadHistory, Color(0xFFFF9800))
                } else {
                    Text("Loading live data...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            InfoSection("Summary Counts") {
                InfoRow("Activities", serviceInfo.activityCount.toString())
                InfoRow("Services", serviceInfo.serviceCount.toString())
                InfoRow("Open FDs", diag.openFileDescriptors.toString())
            }
        }
    }
}

@Composable
private fun ProcessTab(pid: Int) {
    val processInfo = remember(pid) { ProcessInspectorEngine.getDetailedProcessInfo(pid) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            InfoSection("Process Details") {
                InfoRow("Parent PID", processInfo.ppid.toString())
                InfoRow("Priority", processInfo.priority.toString())
                InfoRow("Nice Value", processInfo.nice.toString())
                InfoRow("State", processInfo.state)
                InfoRow("SELinux Context", processInfo.selinuxContext)
            }
        }
    }
}

@Composable
private fun ApkTab(context: Context, packageName: String) {
    val packageInfo = remember(packageName) { ProcessInspectorEngine.getDetailedPackageInfo(context, packageName) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            InfoSection("Paths") {
                InfoRow("Source Dir", packageInfo.packagePath)
                InfoRow("Data Dir", packageInfo.dataDir)
            }
        }
        item {
            InfoSection("Permissions (${packageInfo.permissions.size})") {
                for (perm in packageInfo.permissions.take(10)) {
                    Text(perm.substringAfterLast("."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (packageInfo.permissions.size > 10) {
                    Text("... and ${packageInfo.permissions.size - 10} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ServicesTab(packageName: String) {
    val context = LocalContext.current
    val serviceInfo = remember(packageName) { ProcessInspectorEngine.getServiceInfo(context, packageName) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            InfoSection("Activities (${serviceInfo.activityCount})") {
                for (act in serviceInfo.activities.take(5)) {
                    Text(act.substringAfterLast("."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            InfoSection("Services (${serviceInfo.serviceCount})") {
                for (svc in serviceInfo.backgroundServices.take(5)) {
                    Text(svc.substringAfterLast("."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsTab(pid: Int) {
    val context = LocalContext.current
    val diag = remember(pid) { ProcessInspectorEngine.getDiagnosticsInfo(context, pid) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            InfoSection("Capabilities") {
                for (entry in diag.capabilities.entries) {
                    val name = entry.key
                    val supported = entry.value
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(
                            if (supported) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            tint = if (supported) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            InfoSection("Android Diagnostics") {
                InfoRow("Shizuku Status", diag.shizukuStatus)
                InfoRow("SELinux Mode", diag.selinuxMode)
                InfoRow("Battery Optimization", diag.batteryOptimization)
                InfoRow("Open File Descriptors", diag.openFileDescriptors.toString())
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val clipboardManager = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { clipboardManager.setText(AnnotatedString(value)) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
        )
    }
}

@Composable
private fun MetricResourceRow(label: String, value: String, history: List<Float>, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(100.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Box(modifier = Modifier.weight(1f).height(40.dp)) {
            TrendLineGraph(history, color, Modifier.fillMaxSize())
        }
    }
}

private fun formatNetworkSpeed(rx: Long, tx: Long): String {
    fun format(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB/s", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format("%.1f KB/s", bytes / 1024f)
            else -> "$bytes B/s"
        }
    }
    return "↓ ${format(rx)}\n↑ ${format(tx)}"
}

@Composable
private fun TrendLineGraph(historyData: List<Float>, strokeColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (historyData.size < 2) return@Canvas
        val path = androidx.compose.ui.graphics.Path()
        val stepX = size.width / (HISTORY_SIZE - 1)
        val maxVal = (historyData.maxOrNull() ?: 1f).coerceAtLeast(1f)
        
        historyData.forEachIndexed { i, valPct ->
            val x = i * stepX
            val y = size.height - ((valPct / maxVal) * size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, strokeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

private fun generateProcessReport(proc: ProcessItem, snapshot: ProcessSnapshot?): String {
    return """
        Application: ${proc.name}
        Package: ${proc.packageName}
        PID: ${proc.pid}
        CPU: ${String.format("%.1f", proc.cpuPercent)}%
        Memory: ${String.format("%.1f", proc.memoryMb)} MB
        Network: ${snapshot?.let { formatNetworkSpeed(it.rxBytesPerSec, it.txBytesPerSec) } ?: "N/A"}
        Threads: ${proc.threads}
    """.trimIndent()
}
