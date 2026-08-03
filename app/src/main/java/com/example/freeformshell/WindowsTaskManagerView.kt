package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class InspectorWindowState(
    val process: ProcessItem,
    var position: DpOffset = DpOffset(50.dp, 50.dp),
    var size: DpSize = DpSize(600.dp, 500.dp),
    val windowId: String = UUID.randomUUID().toString()
)

// ==========================================
// THEME & CONSTANTS
// ==========================================

@Composable
fun DynamicTaskManagerTheme(
    isDark: Boolean = isSystemInDarkTheme(), // FIX: Default to system theme if not specified
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = Color(0xFF00A2ED),
            onPrimary = Color.White,
            surface = Color(0xFF1C1C1C),
            onSurface = Color(0xFFE3E3E3), // Soft white for primary text
            surfaceContainerLowest = Color(0xFF0F0F0F),
            surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF242424),
            surfaceContainerHigh = Color(0xFF2D2D2D),
            outlineVariant = Color(0xFF3F3F3F),
            error = Color(0xFFF44336),
            tertiary = Color(0xFF9E9E9E) // Muted gray for secondary text
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0078D4),
            onPrimary = Color.White,
            surface = Color(0xFFFFFFFF), // FIX: Pure white for light surface
            onSurface = Color(0xFF1A1A1A), // FIX: Near black for light primary text
            surfaceContainerLowest = Color(0xFFF9F9F9), // FIX: Very light gray
            surfaceContainerLow = Color(0xFFF3F3F3),
            surfaceContainer = Color(0xFFEEEEEE),
            surfaceContainerHigh = Color(0xFFE5E5E5),
            outlineVariant = Color(0xFFD1D1D1),
            error = Color(0xFFD32F2F),
            tertiary = Color(0xFF616161) // FIX: Muted gray for secondary text
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface),
            titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface),
            labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface),
            bodyMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface),
            bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = colorScheme.onSurfaceVariant),
            labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
        ),
        content = content
    )
}

private val DEFAULT_PROCESS_STATUSES = setOf<String>()

private enum class ProcessSortColumn { NAME, STATUS, CPU, MEMORY, THREADS, NETWORK }

// ==========================================
// MAIN VIEW
// ==========================================

@Composable
fun WindowsTaskManagerView(
    modifier: Modifier = Modifier,
    onCloseWindow: (() -> Unit)? = null
) {
    val systemInDark = isSystemInDarkTheme()
    var isDarkTheme by rememberSaveable { mutableStateOf(systemInDark) }
    
    // Sync with system theme changes automatically
    LaunchedEffect(systemInDark) {
        isDarkTheme = systemInDark
    }
    val context = LocalContext.current

    // Experimental Settings
    var independentInspectors by rememberSaveable { mutableStateOf(false) }
    var enhancedMultiWindow by rememberSaveable { mutableStateOf(ThemeManager.isEnhancedMultiWindowEnabled(context)) }
    var perfTooltipEnabled by rememberSaveable { mutableStateOf(ThemeManager.isPerformanceTooltipEnabled(context)) }
    
    DynamicTaskManagerTheme(isDark = isDarkTheme) {
        var isExpandedSidebar by rememberSaveable { mutableStateOf(false) }
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        var searchQuery by rememberSaveable { mutableStateOf("") }
        var selectedProcess by remember { mutableStateOf<ProcessItem?>(null) }
        var performanceMetricTab by rememberSaveable { mutableIntStateOf(0) }
        var isPerCoreCpuView by rememberSaveable { mutableStateOf(false) }
        var uiScale by rememberSaveable { mutableFloatStateOf(1.0f) }
        
        // Confirmation dialog state
        var processToEnd by remember { mutableStateOf<ProcessItem?>(null) }
        
        // Global Performance Context Menu State
        var showPerfMenu by remember { mutableStateOf(false) }
        var perfMenuOffset by remember { mutableStateOf(Offset.Zero) }
        var perfMenuSection by remember { mutableStateOf("") }
        var perfMenuMode by remember { mutableStateOf(PerfGraphMode.OVERALL) }

        // Multi-Window Process Inspectors
        val inspectorWindows = remember { mutableStateListOf<InspectorWindowState>() }

        val stats by SystemMonitorEngine.statsFlow.collectAsState()

        DisposableEffect(Unit) {
            SystemMonitorEngine.startPolling(context, 500L)
            onDispose { 
                SystemMonitorEngine.stopPolling() 
                if (!independentInspectors) {
                    inspectorWindows.clear()
                }
            }
        }
        
        val internalOnCloseWindow: () -> Unit = {
            if (!independentInspectors) {
                inspectorWindows.clear()
                context.sendBroadcast(Intent("com.example.freeformshell.ACTION_CLOSE_PROPERTIES"))
            }
            onCloseWindow?.invoke()
        }

        val currentDensity = LocalDensity.current
        val customDensity = remember(currentDensity, uiScale) {
            Density(
                density = currentDensity.density * uiScale,
                fontScale = currentDensity.fontScale * uiScale
            )
        }

        CompositionLocalProvider(LocalDensity provides customDensity) {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .systemBarsPadding() 
            ) {
                val isCompact = this.maxWidth < 600.dp
                val sidebarWidth by animateDpAsState(
                    targetValue = if (isExpandedSidebar) 200.dp else 48.dp,
                    label = "sidebarWidth"
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WindowHeaderBar(onCloseWindow = internalOnCloseWindow)

                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (!isCompact) {
                                TaskManagerSidebar(
                                    selectedTab = selectedTab,
                                    onTabSelect = { selectedTab = it },
                                    isExpanded = isExpandedSidebar,
                                    width = sidebarWidth,
                                    onToggleExpand = { isExpandedSidebar = !isExpandedSidebar }
                                )
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        when (selectedTab) {
                                            0 -> ProcessesTabContent(
                                                stats = stats,
                                                searchQuery = searchQuery,
                                                onSearchChange = { searchQuery = it },
                                                selectedProcess = selectedProcess,
                                                onSelectProcess = { selectedProcess = it },
                                                onShowProperties = { proc ->
                                                    val myMode = TaskManager.getMyWindowingMode()
                                                    if (myMode == 5) {
                                                        val intent = Intent(context, ProcessInspectorActivity::class.java).apply {
                                                            data = Uri.parse("process://${proc.pid}")
                                                            putExtra("extra_pid", proc.pid)
                                                            putExtra("extra_package_name", proc.packageName)
                                                            putExtra("extra_name", proc.name)
                                                            putExtra("extra_user", proc.user)
                                                            putExtra("extra_threads", proc.threads)
                                                            putExtra("extra_cpu", proc.cpuPercent)
                                                            putExtra("extra_mem", proc.memoryMb)
                                                            putExtra("extra_is_dark", isDarkTheme)
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                                        }
                                                        
                                                        val options = android.app.ActivityOptions.makeBasic()
                                                        try {
                                                            val setLaunchWindowingModeMethod = options.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                                                            setLaunchWindowingModeMethod.invoke(options, 5)
                                                            val dm = context.resources.displayMetrics
                                                            val width = (dm.widthPixels * 0.55f).toInt()
                                                            val height = (dm.heightPixels * 0.6f).toInt()
                                                            val left = (dm.widthPixels - width) / 2
                                                            val top = (dm.heightPixels - height) / 2
                                                            options.setLaunchBounds(android.graphics.Rect(left, top, left + width, top + height))
                                                        } catch (e: Exception) {}
                                                        context.startActivity(intent, options.toBundle())
                                                    } else {
                                                        if (inspectorWindows.none { it.process.pid == proc.pid }) {
                                                            val lastWindow = inspectorWindows.lastOrNull()
                                                            val newPos = if (lastWindow != null) {
                                                                var nextX = lastWindow.position.x + 28.dp
                                                                var nextY = lastWindow.position.y + 28.dp
                                                                if (nextX > 400.dp) nextX = 50.dp
                                                                if (nextY > 400.dp) nextY = 50.dp
                                                                DpOffset(nextX, nextY)
                                                            } else {
                                                                DpOffset(50.dp, 50.dp)
                                                            }
                                                            inspectorWindows.add(InspectorWindowState(process = proc, position = newPos))
                                                        }
                                                    }
                                                },
                                                onEndTask = { proc -> processToEnd = proc },
                                                isCompact = isCompact
                                            )
                                            1 -> PerformanceTabContent(
                                                stats = stats,
                                                selectedMetric = performanceMetricTab,
                                                onSelectMetric = { performanceMetricTab = it },
                                                isPerCoreCpuView = isPerCoreCpuView,
                                                onTogglePerCoreView = { isPerCoreCpuView = it },
                                                isCompact = isCompact,
                                                onShowMenu = { offset, section, mode ->
                                                    perfMenuOffset = offset
                                                    perfMenuSection = section
                                                    perfMenuMode = mode
                                                    showPerfMenu = true
                                                }
                                            )
                                            2 -> StartupAppsTabContent(context)
                                            else -> SettingsTabContent(
                                                uiScale = uiScale, 
                                                onScaleChange = { uiScale = it },
                                                isDarkTheme = isDarkTheme,
                                                onThemeChange = { isDarkTheme = it },
                                                independentInspectors = independentInspectors,
                                                onIndependentInspectorsChange = { independentInspectors = it },
                                                enhancedMultiWindow = enhancedMultiWindow,
                                                onEnhancedMultiWindowChange = { 
                                                    enhancedMultiWindow = it
                                                    ThemeManager.setEnhancedMultiWindowEnabled(context, it)
                                                },
                                                perfTooltipEnabled = perfTooltipEnabled,
                                                onPerfTooltipChange = {
                                                    perfTooltipEnabled = it
                                                    ThemeManager.setPerformanceTooltipEnabled(context, it)
                                                }
                                            )
                                        }
                                    }

                                    if (isCompact) {
                                        DesktopInspiredBottomNav(
                                            selectedTab = selectedTab,
                                            onTabSelect = { selectedTab = it }
                                        )
                                    }
                                }
                                
                                if (!isCompact) {
                                    Box(Modifier.fillMaxSize()) {
                                        inspectorWindows.forEach { windowState ->
                                            ProcessInspectorWindow(
                                                windowState = windowState,
                                                isCompact = false,
                                                onClose = { inspectorWindows.remove(windowState) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (processToEnd != null) {
                    EndTaskConfirmationDialog(
                        proc = processToEnd!!,
                        onConfirm = {
                            try {
                                ShellExecutor.forceStopApp(processToEnd!!.packageName, processToEnd!!.pid)
                                Toast.makeText(context, "Ended task: ${processToEnd!!.name}", Toast.LENGTH_SHORT).show()
                                if (selectedProcess?.pid == processToEnd!!.pid) selectedProcess = null
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to end task: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            processToEnd = null
                        },
                        onDismiss = { processToEnd = null }
                    )
                }
                
                if (isCompact) {
                    inspectorWindows.forEach { windowState ->
                        ProcessInspectorWindow(
                            windowState = windowState,
                            isCompact = true,
                            onClose = { inspectorWindows.remove(windowState) }
                        )
                    }
                }

                // PERFORMANCE CONTEXT MENU (Unified Root-level Popup)
                Windows11PerformanceContextMenu(
                    expanded = showPerfMenu,
                    onDismissRequest = { showPerfMenu = false },
                    currentMode = if (isPerCoreCpuView && perfMenuSection == "CPU") PerfGraphMode.LOGICAL else PerfGraphMode.OVERALL,
                    onModeChange = { newMode ->
                        isPerCoreCpuView = newMode == PerfGraphMode.LOGICAL
                    },
                    stats = stats ?: return@BoxWithConstraints,
                    offset = perfMenuOffset,
                    section = perfMenuSection
                )
            }
        }
    }
}


@Composable
private fun DesktopInspiredBottomNav(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Default.GridView, "Processes", selectedTab == 0) { onTabSelect(0) }
            BottomNavItem(Icons.Default.ShowChart, "Performance", selectedTab == 1) { onTabSelect(1) }
            BottomNavItem(Icons.Default.RocketLaunch, "Startup", selectedTab == 2) { onTabSelect(2) }
            BottomNavItem(Icons.Default.Settings, "Settings", selectedTab == 3) { onTabSelect(3) }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val indicatorWidth by animateDpAsState(if (isSelected) 24.dp else 0.dp, label = "indicator")

    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun EndTaskConfirmationDialog(
    proc: ProcessItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End Task", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    buildAnnotatedString {
                        append("Closing ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(proc.name)
                        }
                        append(" may cause unsaved work to be lost or may affect system stability.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text("Are you sure you want to continue?", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("End task")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(360.dp)
    )
}



// ==========================================
// UI COMPONENTS - LAYOUT & TOOLBAR
// ==========================================

@Composable
private fun WindowHeaderBar(onCloseWindow: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Dns, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            "Task Manager",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(Modifier.weight(1f))
        if (onCloseWindow != null) {
            IconButton(onClick = onCloseWindow, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TaskManagerSidebar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    isExpanded: Boolean,
    width: Dp,
    onToggleExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            .padding(vertical = 8.dp)
    ) {
        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        ) {
            Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurface)
        }

        SidebarNavItem(Icons.Default.GridView, "Processes", selectedTab == 0, isExpanded) { onTabSelect(0) }
        SidebarNavItem(Icons.Default.ShowChart, "Performance", selectedTab == 1, isExpanded) { onTabSelect(1) }
        SidebarNavItem(Icons.Default.RocketLaunch, "Startup apps", selectedTab == 2, isExpanded) { onTabSelect(2) }
        
        Spacer(Modifier.weight(1f))
        
        SidebarNavItem(Icons.Default.Settings, "Settings", selectedTab == 3, isExpanded) { onTabSelect(3) }
    }
}

@Composable
private fun SidebarNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.CenterStart
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterStart)
            )
        }
        
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                null, 
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (isExpanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

// ==========================================
// PROCESSES TAB CONTENT
// ==========================================

@Composable
private fun ProcessesTabContent(
    stats: SystemStats?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedProcess: ProcessItem?,
    onSelectProcess: (ProcessItem?) -> Unit,
    onShowProperties: (ProcessItem) -> Unit,
    onEndTask: (ProcessItem) -> Unit,
    isCompact: Boolean
) {
    var nameWidth by rememberSaveable { mutableFloatStateOf(280f) }
    var statusWidth by rememberSaveable { mutableFloatStateOf(100f) }
    var cpuWidth by rememberSaveable { mutableFloatStateOf(80f) }
    var memWidth by rememberSaveable { mutableFloatStateOf(90f) }
    var threadWidth by rememberSaveable { mutableFloatStateOf(80f) }
    var netWidth by rememberSaveable { mutableFloatStateOf(80f) }

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val pinnedPids = remember { mutableStateListOf<Int>() }
    var sortColumn by rememberSaveable { mutableStateOf<ProcessSortColumn?>(ProcessSortColumn.CPU) }
    var sortAscending by rememberSaveable { mutableStateOf(false) }

    fun onHeaderClick(column: ProcessSortColumn) {
        if (sortColumn == column) sortAscending = !sortAscending
        else {
            sortColumn = column
            sortAscending = column == ProcessSortColumn.NAME || column == ProcessSortColumn.STATUS
        }
    }

    val horizontalScrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        ProcessToolbar(
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            selectedProcess = selectedProcess,
            onEndTask = { selectedProcess?.let(onEndTask) },
            isCompact = isCompact
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = 8.dp)) {
            val windowWidth = this.maxWidth
            val totalTableWidth = (nameWidth + statusWidth + cpuWidth + memWidth + threadWidth + netWidth).dp
            val effectiveWidth = if (totalTableWidth < windowWidth) windowWidth else totalTableWidth

            Column(modifier = Modifier.fillMaxSize().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))) {
                
                Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                    Column(modifier = Modifier.width(effectiveWidth).fillMaxHeight()) {
                        ProcessTableHeader(
                            stats = stats,
                            nameWidth = nameWidth,
                            statusWidth = statusWidth,
                            cpuWidth = cpuWidth,
                            memWidth = memWidth,
                            threadWidth = threadWidth,
                            netWidth = netWidth,
                            sortColumn = sortColumn,
                            sortAscending = sortAscending,
                            onHeaderClick = ::onHeaderClick,
                            onWidthChange = { col, w ->
                                when(col) {
                                    "name" -> nameWidth = w
                                    "status" -> statusWidth = w
                                    "cpu" -> cpuWidth = w
                                    "mem" -> memWidth = w
                                    "threads" -> threadWidth = w
                                    "net" -> netWidth = w
                                }
                            },
                            isCompact = isCompact
                        )

                        val allProcesses = stats?.processes ?: emptyList()
                        val filtered = remember(allProcesses, searchQuery, sortColumn, sortAscending, pinnedPids.size) {
                            val base = allProcesses.filter { 
                                it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) 
                            }
                            
                            val (pinned, unpinned) = base.partition { it.pid in pinnedPids }
                            
                            fun sortList(list: List<ProcessItem>): List<ProcessItem> {
                                val sorted = when (sortColumn) {
                                    ProcessSortColumn.NAME -> list.sortedBy { it.name.lowercase() }
                                    ProcessSortColumn.STATUS -> list.sortedBy { it.status }
                                    ProcessSortColumn.CPU -> list.sortedByDescending { it.cpuPercent }
                                    ProcessSortColumn.MEMORY -> list.sortedByDescending { it.memoryMb }
                                    ProcessSortColumn.THREADS -> list.sortedByDescending { it.threads }
                                    ProcessSortColumn.NETWORK -> list.sortedByDescending { it.rxSpeed + it.txSpeed }
                                    else -> list
                                }
                                return if (sortAscending) sorted.reversed() else sorted
                            }

                            sortList(pinned) + sortList(unpinned)
                        }

                        val maxCpu = (filtered.maxOfOrNull { it.cpuPercent } ?: 1f).coerceAtLeast(1f)
                        val maxMem = (filtered.maxOfOrNull { it.memoryMb } ?: 1f).coerceAtLeast(1f)
                        val maxThreads = (filtered.maxOfOrNull { it.threads } ?: 1).coerceAtLeast(1)

                        val apps = filtered.filter { it.category == 0 }
                        val background = filtered.filter { it.category == 1 }
                        val system = filtered.filter { it.category == 2 }

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            if (apps.isNotEmpty()) {
                                item { TableSectionHeader("Apps (${apps.size})") }
                                renderProcessGroups(
                                    processes = apps,
                                    expandedGroups = expandedGroups,
                                    pinnedPids = pinnedPids,
                                    selectedProcess = selectedProcess,
                                    onSelectProcess = onSelectProcess,
                                    onShowProperties = onShowProperties,
                                    onEndTask = onEndTask,
                                    nameWidth = nameWidth,
                                    statusWidth = statusWidth,
                                    cpuWidth = cpuWidth,
                                    memWidth = memWidth,
                                    threadWidth = threadWidth,
                                    netWidth = netWidth,
                                    isCompact = isCompact,
                                    maxCpu = maxCpu,
                                    maxMem = maxMem,
                                    maxThreads = maxThreads
                                )
                            }
                            if (background.isNotEmpty()) {
                                item { TableSectionHeader("Background processes (${background.size})") }
                                renderProcessGroups(
                                    processes = background,
                                    expandedGroups = expandedGroups,
                                    pinnedPids = pinnedPids,
                                    selectedProcess = selectedProcess,
                                    onSelectProcess = onSelectProcess,
                                    onShowProperties = onShowProperties,
                                    onEndTask = onEndTask,
                                    nameWidth = nameWidth,
                                    statusWidth = statusWidth,
                                    cpuWidth = cpuWidth,
                                    memWidth = memWidth,
                                    threadWidth = threadWidth,
                                    netWidth = netWidth,
                                    isCompact = isCompact,
                                    maxCpu = maxCpu,
                                    maxMem = maxMem,
                                    maxThreads = maxThreads
                                )
                            }
                            if (system.isNotEmpty()) {
                                item { TableSectionHeader("Android processes (${system.size})") }
                                renderProcessGroups(
                                    processes = system,
                                    expandedGroups = expandedGroups,
                                    pinnedPids = pinnedPids,
                                    selectedProcess = selectedProcess,
                                    onSelectProcess = onSelectProcess,
                                    onShowProperties = onShowProperties,
                                    onEndTask = onEndTask,
                                    nameWidth = nameWidth,
                                    statusWidth = statusWidth,
                                    cpuWidth = cpuWidth,
                                    memWidth = memWidth,
                                    threadWidth = threadWidth,
                                    netWidth = netWidth,
                                    isCompact = isCompact,
                                    maxCpu = maxCpu,
                                    maxMem = maxMem,
                                    maxThreads = maxThreads
                                )
                            }
                        }
                    }
                }
                
                // Horizontal Scrollbar (Only show if columns exceed width)
                if (totalTableWidth > windowWidth) {
                    WindowsStyleHorizontalScrollbar(state = horizontalScrollState)
                }
            }
            
            // Universal scrollbar for both desktop and mobile
            WindowsStyleScrollbar(
                state = lazyListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}



@Composable
private fun WindowsStyleScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = state.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return

    // FIX: Auto-hide scrollbar after inactivity
    var showScrollbar by remember { mutableStateOf(false) }
    
    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        showScrollbar = true
        kotlinx.coroutines.delay(2000)
        showScrollbar = false
    }

    val alpha by animateFloatAsState(if (showScrollbar) 1f else 0f, label = "scrollbarAlpha")
    if (alpha <= 0f) return

    val totalItemsCount = layoutInfo.totalItemsCount
    val scrollbarHeightFraction = if (totalItemsCount > 0) {
        visibleItemsInfo.size.toFloat() / totalItemsCount
    } else 1f
    
    val scrollbarOffsetFraction = if (totalItemsCount > 0) {
        state.firstVisibleItemIndex.toFloat() / totalItemsCount
    } else 0f

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.width(12.dp).padding(vertical = 2.dp)) {
        val barHeight = (this.maxHeight * scrollbarHeightFraction).coerceAtLeast(24.dp)
        val barOffset = (this.maxHeight - barHeight) * scrollbarOffsetFraction

        Box(
            modifier = Modifier
                .offset(y = barOffset)
                .width(6.dp)
                .height(barHeight)
                .align(Alignment.TopCenter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * alpha))
                .pointerInput(totalItemsCount) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            // Rough estimation for pixel scroll based on item counts
                            state.scrollBy(dragAmount.y * (totalItemsCount / visibleItemsInfo.size.toFloat()) * 2f)
                        }
                    }
                }
        )
    }
}

@Composable
private fun WindowsStyleHorizontalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier
) {
    val scrollValue = state.value.toFloat()
    val maxScroll = state.maxValue.toFloat()
    if (maxScroll <= 0) return

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(12.dp).padding(horizontal = 2.dp)) {
        val viewWidth = this.constraints.maxWidth.toFloat()
        val totalWidth = viewWidth + maxScroll
        val barWidthFraction = viewWidth / totalWidth
        val barWidth = (this.maxWidth * barWidthFraction).coerceAtLeast(40.dp)
        val barOffsetFraction = scrollValue / maxScroll
        val barOffset = (this.maxWidth - barWidth) * barOffsetFraction

        Box(
            modifier = Modifier
                .offset(x = barOffset)
                .height(6.dp)
                .width(barWidth)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                .pointerInput(maxScroll) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaFraction = dragAmount.x / viewWidth
                        val scrollDelta = deltaFraction * totalWidth
                        coroutineScope.launch {
                            state.scrollBy(scrollDelta)
                        }
                    }
                }
        )
    }
}





@Composable
private fun ProcessToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedProcess: ProcessItem?,
    onEndTask: () -> Unit,
    isCompact: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Processes",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(Modifier.width(16.dp))

        val searchWidth = if (isCompact) Modifier.weight(1f) else Modifier.width(320.dp)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search processes", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
            modifier = searchWidth.height(34.dp),
            shape = RoundedCornerShape(4.dp),
            textStyle = TextStyle(fontSize = 12.sp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onEndTask,
            enabled = selectedProcess != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            ),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("End task", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProcessTableHeader(
    stats: SystemStats?,
    nameWidth: Float,
    statusWidth: Float,
    cpuWidth: Float,
    memWidth: Float,
    threadWidth: Float,
    netWidth: Float,
    sortColumn: ProcessSortColumn?,
    sortAscending: Boolean,
    onHeaderClick: (ProcessSortColumn) -> Unit,
    onWidthChange: (String, Float) -> Unit,
    isCompact: Boolean
) {
    val overallCpu = stats?.cpuOverallUtil?.toInt() ?: 0
    val overallRam = stats?.memoryStat?.ramHistory?.lastOrNull()?.toInt() ?: 0

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(32.dp))
            
            HeaderCell("Name", nameWidth - 32f, ProcessSortColumn.NAME, sortColumn, sortAscending, { onHeaderClick(ProcessSortColumn.NAME) }, { onWidthChange("name", it) })
            HeaderCell("Status", statusWidth, ProcessSortColumn.STATUS, sortColumn, sortAscending, { onHeaderClick(ProcessSortColumn.STATUS) }, { onWidthChange("status", it) })
            
            MetricHeaderCell("CPU", "$overallCpu%", cpuWidth, ProcessSortColumn.CPU, sortColumn, sortAscending, { onHeaderClick(ProcessSortColumn.CPU) }, { onWidthChange("cpu", it) })
            MetricHeaderCell("Memory", "$overallRam%", memWidth, ProcessSortColumn.MEMORY, sortColumn, sortAscending, { onHeaderClick(ProcessSortColumn.MEMORY) }, { onWidthChange("mem", it) })
            
            MetricHeaderCell("Threads", (stats?.threadCount ?: 0).toString(), threadWidth, ProcessSortColumn.THREADS, sortColumn, sortAscending, { onHeaderClick(ProcessSortColumn.THREADS) }, { onWidthChange("threads", it) })
            MetricHeaderCell("Network", "0%", netWidth, ProcessSortColumn.NETWORK, sortColumn, sortAscending, { onHeaderClick(ProcessSortColumn.NETWORK) }, { onWidthChange("net", it) })
            
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderCell(
    label: String,
    width: Float,
    column: ProcessSortColumn,
    activeColumn: ProcessSortColumn?,
    ascending: Boolean,
    onClick: () -> Unit,
    onWidthChange: (Float) -> Unit
) {
    val isActive = column == activeColumn
    Row(modifier = Modifier.width(width.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                if (isActive) {
                    Icon(
                        if (ascending) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        DraggableColumnDivider(onWidthChange = onWidthChange, currentWidthDp = width, minWidthDp = 60f)
    }
}

@Composable
private fun MetricHeaderCell(
    label: String,
    value: String,
    width: Float,
    column: ProcessSortColumn,
    activeColumn: ProcessSortColumn?,
    ascending: Boolean,
    onClick: () -> Unit,
    onWidthChange: (Float) -> Unit
) {
    val isActive = column == activeColumn
    Row(modifier = Modifier.width(width.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Icon(
                        if (ascending) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        DraggableColumnDivider(onWidthChange = onWidthChange, currentWidthDp = width, minWidthDp = 40f)
    }
}

@Composable
private fun TableSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

// ==========================================
// PROCESS ROW & RENDERER
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProcessRow(
    proc: ProcessItem,
    maxCpu: Float,
    maxMem: Float,
    maxThreads: Int,
    isSelected: Boolean,
    isGroupHeader: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    isChild: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onShowProperties: (ProcessItem) -> Unit = {},
    onClick: () -> Unit,
    onEndTask: (ProcessItem) -> Unit,
    nameWidth: Float,
    statusWidth: Float,
    cpuWidth: Float,
    memWidth: Float,
    threadWidth: Float,
    netWidth: Float,
    isCompact: Boolean
) {
    val context = LocalContext.current
    val cpuPct = (proc.cpuPercent / maxCpu) * 100f
    val memPct = (proc.memoryMb / maxMem) * 100f
    val threadPct = (proc.threads.toFloat() / maxThreads) * 100f
    
    val statusLabel = proc.status.takeIf { it.trim().lowercase() !in DEFAULT_PROCESS_STATUSES } ?: ""

    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    else -> Color.Transparent
                }
            )
            .pointerInput(isGroupHeader, proc.pid) {
                detectTapGestures(
                    onTap = { if (isGroupHeader) onToggleExpand() else onClick() },
                    onLongPress = { showContextMenu = true },
                    onPress = { offset ->
                    }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.buttons.isSecondaryPressed) {
                            showContextMenu = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        if (isSelected) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.width(nameWidth.dp).padding(start = if (isChild) 48.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGroupHeader) {
                    Icon(
                        if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                        null,
                        modifier = Modifier.size(16.dp).clickable { onToggleExpand() }
                    )
                } else if (!isChild) {
                    Spacer(Modifier.width(16.dp))
                }
                
                if (isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        null,
                        modifier = Modifier.size(12.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AppIcon(proc.packageName, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    proc.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val statusColor = when (proc.status.lowercase()) {
                "running" -> Color(0xFF4CAF50)
                "sleeping" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                "stopped", "zombie" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            }

            Text(
                proc.status,
                modifier = Modifier.width(statusWidth.dp).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1
            )

            HeatmapCell(String.format("%.1f%%", proc.cpuPercent), cpuPct, cpuWidth)
            HeatmapCell(String.format("%.0f MB", proc.memoryMb), memPct, memWidth)

            HeatmapCell(proc.threads.toString(), threadPct, threadWidth)
            
            val netSpeedTotal = proc.rxSpeed + proc.txSpeed
            val netPct = (netSpeedTotal.toFloat() / (1024 * 1024)).coerceIn(0f, 1f) * 100f 
            HeatmapCell(formatNetworkSpeed(proc.rxSpeed, proc.txSpeed), netPct, netWidth)

            // Fill remaining space to the right
            Spacer(Modifier.weight(1f))
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)).align(Alignment.BottomCenter))

        Windows11ContextMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            onEndTask = { onEndTask(proc); showContextMenu = false },
            onTogglePin = { onTogglePin(); showContextMenu = false },
            isPinned = isPinned,
            onProperties = { onShowProperties(proc); showContextMenu = false },
            isMultiWindowSupported = ThemeManager.isMultiWindowSupported(context, proc.packageName),
            onToggleMultiWindow = {
                ThemeManager.toggleMultiWindowWhitelist(context, proc.packageName)
                showContextMenu = false
                // Trigger refresh if needed
            },
            onLaunchDisplay = { 
                try {
                    val myMode = TaskManager.getMyWindowingMode()
                    val tasks = TaskManager.getRecentTasks()
                    val task = tasks.find { it.packageName == proc.packageName }
                    
                    if (task != null) {
                        ShellExecutor.moveTaskToFront(task.taskId)
                        // If we are in Freeform, ensure the target is also in freeform if it's not already
                        if (myMode == 5) {
                            ShellExecutor.setTaskWindowingMode(task.taskId, 5, true)
                        }
                        Toast.makeText(context, "Switching to ${proc.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(proc.packageName)
                        if (launchIntent != null) {
                            if (myMode == 5) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                // Standard way to request Freeform is via ActivityOptions, but ShellExecutor relaunch is better
                                ShellExecutor.relaunchFreeformTask(-1, proc.packageName + "/" + launchIntent.component?.className)
                            } else {
                                context.startActivity(launchIntent)
                            }
                        } else {
                            Toast.makeText(context, "Cannot switch: no active task or launch intent", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Switch failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                showContextMenu = false 
            },
            onAppDetails = {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", proc.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
                showContextMenu = false
            },
            onSearchOnline = {
                try {
                    val query = "${proc.name} ${proc.packageName}".trim()
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                }
                showContextMenu = false
            }
        )
    }
}

@Composable
private fun HeatmapCell(text: String, percentage: Float, width: Float) {
    val pct = percentage.coerceIn(0f, 100f)
    
    // FIX: Professional muted red palette inspired by Windows Task Manager
    val (cellColor, bgAlpha) = when {
        pct < 1f -> Color(0xFFC62828) to 0.02f
        pct < 10f -> Color(0xFFC62828) to 0.12f
        pct < 30f -> Color(0xFFB71C1C) to 0.35f
        pct < 60f -> Color(0xFF880E4F) to 0.60f
        else -> Color(0xFF4A0E0E) to 0.85f
    }

    Box(
        modifier = Modifier
            .width(width.dp)
            .fillMaxHeight()
            .background(cellColor.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (bgAlpha > 0.45f) Color.White else MaterialTheme.colorScheme.onSurface
        )
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
    return if (rx + tx > 0) "↓${format(rx)} ↑${format(tx)}" else "0 B/s"
}

private fun LazyListScope.renderProcessGroups(
    processes: List<ProcessItem>,
    expandedGroups: SnapshotStateMap<String, Boolean>,
    pinnedPids: SnapshotStateList<Int>,
    selectedProcess: ProcessItem?,
    onSelectProcess: (ProcessItem?) -> Unit,
    onShowProperties: (ProcessItem) -> Unit,
    onEndTask: (ProcessItem) -> Unit,
    nameWidth: Float,
    statusWidth: Float,
    cpuWidth: Float,
    memWidth: Float,
    threadWidth: Float,
    netWidth: Float,
    isCompact: Boolean,
    maxCpu: Float,
    maxMem: Float,
    maxThreads: Int
) {
    val groups = processes.groupBy { it.packageName }
    groups.forEach { (pkg, list) ->
        if (list.size > 1) {
            val isExpanded = expandedGroups[pkg] ?: false
            item(key = "group_$pkg") {
                val totalCpu = list.sumOf { it.cpuPercent.toDouble() }.toFloat()
                val totalMem = list.sumOf { it.memoryMb.toDouble() }.toFloat()
                val totalThreads = list.sumOf { it.threads }
                val isPinned = list.any { it.pid in pinnedPids }
                ProcessRow(
                    proc = list[0].copy(name = "${list[0].name} (${list.size})", cpuPercent = totalCpu, memoryMb = totalMem, threads = totalThreads),
                    maxCpu = maxCpu, maxMem = maxMem, maxThreads = maxThreads,
                    isSelected = false, isGroupHeader = true, isExpanded = isExpanded,
                    onToggleExpand = { expandedGroups[pkg] = !isExpanded },
                    isPinned = isPinned,
                    onTogglePin = { 
                        if (isPinned) list.forEach { pinnedPids.remove(it.pid) }
                        else list.forEach { if (it.pid !in pinnedPids) pinnedPids.add(it.pid) }
                    },
                    onShowProperties = onShowProperties,
                    onClick = { }, onEndTask = onEndTask,
                    nameWidth = nameWidth, statusWidth = statusWidth, cpuWidth = cpuWidth, memWidth = memWidth, threadWidth = threadWidth, netWidth = netWidth,
                    isCompact = isCompact
                )
            }
            if (isExpanded) {
                items(list, key = { it.pid }) { subProc ->
                    val isPinned = subProc.pid in pinnedPids
                    ProcessRow(
                        proc = subProc, maxCpu = maxCpu, maxMem = maxMem, maxThreads = maxThreads,
                        isSelected = (selectedProcess?.pid == subProc.pid),
                        isChild = true,
                        isPinned = isPinned,
                        onTogglePin = { if (isPinned) pinnedPids.remove(subProc.pid) else pinnedPids.add(subProc.pid) },
                        onShowProperties = onShowProperties,
                        onClick = { onSelectProcess(if (selectedProcess?.pid == subProc.pid) null else subProc) },
                        onEndTask = onEndTask,
                        nameWidth = nameWidth, statusWidth = statusWidth, cpuWidth = cpuWidth, memWidth = memWidth, threadWidth = threadWidth, netWidth = netWidth,
                        isCompact = isCompact
                    )
                }
            }
        } else {
            item(key = list[0].pid) {
                val proc = list[0]
                val isPinned = proc.pid in pinnedPids
                ProcessRow(
                    proc = proc, maxCpu = maxCpu, maxMem = maxMem, maxThreads = maxThreads,
                    isSelected = (selectedProcess?.pid == proc.pid),
                    isPinned = isPinned,
                    onTogglePin = { if (isPinned) pinnedPids.remove(proc.pid) else pinnedPids.add(proc.pid) },
                    onShowProperties = onShowProperties,
                    onClick = { onSelectProcess(if (selectedProcess?.pid == proc.pid) null else proc) },
                    onEndTask = onEndTask,
                    nameWidth = nameWidth, statusWidth = statusWidth, cpuWidth = cpuWidth, memWidth = memWidth, threadWidth = threadWidth, netWidth = netWidth,
                    isCompact = isCompact
                )
            }
        }
    }
}

// ==========================================
// COLUMN RESIZING HELPER
// ==========================================

@Composable
private fun DraggableColumnDivider(
    onWidthChange: (Float) -> Unit,
    currentWidthDp: Float,
    minWidthDp: Float = 40f,
    maxWidthDp: Float = 600f
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    val latestWidth by rememberUpdatedState(currentWidthDp)
    val latestOnWidthChange by rememberUpdatedState(onWidthChange)

    Box(
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.x.toDp().value }
                        val next = (latestWidth + deltaDp).coerceIn(minWidthDp, maxWidthDp)
                        latestOnWidthChange(next)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    }
}

// ==========================================
// CONTEXT MENU
// ==========================================

@Composable
private fun Windows11ContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onEndTask: () -> Unit,
    onLaunchDisplay: () -> Unit,
    onAppDetails: () -> Unit,
    onSearchOnline: () -> Unit,
    onTogglePin: () -> Unit,
    isPinned: Boolean,
    onProperties: () -> Unit,
    onToggleMultiWindow: () -> Unit,
    isMultiWindowSupported: Boolean
) {
    if (expanded) {
        Popup(
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .width(220.dp)
                    .padding(4.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    ContextMenuItem("End task", Icons.Default.Block, MaterialTheme.colorScheme.error, onEndTask)
                    ContextMenuItem(if (isPinned) "Unpin Process" else "Pin Process", Icons.Default.PushPin, MaterialTheme.colorScheme.onSurface, onTogglePin)
                    ContextMenuItem(if (isMultiWindowSupported) "Disable Multi-Window" else "Enable Multi-Window", Icons.Default.Layers, MaterialTheme.colorScheme.onSurface, onToggleMultiWindow)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ContextMenuItem("Properties...", Icons.Default.Info, MaterialTheme.colorScheme.onSurface, onProperties)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ContextMenuItem("Switch to", Icons.Default.OpenInNew, MaterialTheme.colorScheme.onSurface, onLaunchDisplay)
                    ContextMenuItem("App details", Icons.Default.Settings, MaterialTheme.colorScheme.onSurface, onAppDetails)
                    ContextMenuItem("Search online", Icons.Default.Search, MaterialTheme.colorScheme.onSurface, onSearchOnline)
                }
            }
        }
    }
}


@Composable
private fun ContextMenuItem(
    text: String, 
    icon: ImageVector, 
    tint: Color, 
    onClick: () -> Unit,
    hasSubmenu: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint, modifier = Modifier.weight(1f))
        if (hasSubmenu) {
            Icon(Icons.Default.ChevronRight, null, tint = tint.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        }
    }
}

// ==========================================
// PERFORMANCE, STARTUP & SETTINGS TABS
// ==========================================

// ==========================================
// PERFORMANCE TAB CONTENT - WINDOWS 11 REDESIGN
// ==========================================

private enum class PerfGraphMode { OVERALL, LOGICAL, CORE_GRID, KERNEL }

@Composable
private fun PerformanceTabContent(
    stats: SystemStats?,
    selectedMetric: Int,
    onSelectMetric: (Int) -> Unit,
    isPerCoreCpuView: Boolean,
    onTogglePerCoreView: (Boolean) -> Unit,
    isCompact: Boolean,
    onShowMenu: (Offset, String, PerfGraphMode) -> Unit
) {
    val context = LocalContext.current
    val mode = if (isPerCoreCpuView) PerfGraphMode.LOGICAL else PerfGraphMode.OVERALL

    if (isCompact) {
        // MOBILE LAYOUT: Native Top-to-Bottom Flow with Bottom Metric Strip
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            // 1. Main Detail Panel (Scrollable content)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                stats?.let { s ->
                    // Wrap the detail view in a scrollable column for mobile reachability
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when (selectedMetric) {
                            0 -> CpuDetailView(s, mode, onShowMenu, isCompact)
                            1 -> MemoryDetailView(s, onShowMenu, isCompact)
                            2 -> GpuDetailView(s, onShowMenu, isCompact)
                            3 -> DiskDetailView(s, onShowMenu, isCompact)
                            4 -> NetworkDetailView(s, onShowMenu, isCompact)
                            5 -> BatteryDetailView(s, onShowMenu, isCompact)
                        }
                    }
                }
            }

            // 2. Horizontal Metric Strip at Bottom (Reachability)
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp) // Reduced height for mobile
                    .drawBehind {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, 0f), // Draw line at top of strip
                            end = Offset(size.width, 0f),
                            strokeWidth = 1f
                        )
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    stats?.let { s ->
                        MetricSidebarItem(
                            title = "CPU",
                            subtitle = "${s.cpuOverallUtil.toInt()}%",
                            isSelected = selectedMetric == 0,
                            history = s.cpuHistory,
                            onClick = { onSelectMetric(0) },
                            sampleCount = s.sampleCount,
                            isCompact = true
                        )
                        MetricSidebarItem(
                            title = "Memory",
                            subtitle = "${(s.memoryStat.usedRamMb * 100 / s.memoryStat.totalRamMb)}%",
                            isSelected = selectedMetric == 1,
                            history = s.memoryStat.ramHistory,
                            onClick = { onSelectMetric(1) },
                            sampleCount = s.sampleCount,
                            isCompact = true
                        )
                        MetricSidebarItem(
                            title = "GPU",
                            subtitle = if (s.gpuStat.gpuUtilPercent >= 0) "${s.gpuStat.gpuUtilPercent.toInt()}%" else "${s.gpuStat.temperature.toInt()}°C",
                            isSelected = selectedMetric == 2,
                            history = s.gpuStat.gpuHistory,
                            onClick = { onSelectMetric(2) },
                            sampleCount = s.sampleCount,
                            isCompact = true
                        )
                        MetricSidebarItem(
                            title = "Disk",
                            subtitle = "${s.diskStat.utilization.toInt()}%",
                            isSelected = selectedMetric == 3,
                            history = s.diskStat.history,
                            onClick = { onSelectMetric(3) },
                            sampleCount = s.sampleCount,
                            isCompact = true
                        )
                        MetricSidebarItem(
                            title = "Wi-Fi",
                            subtitle = formatNetworkSpeed(s.networkStat.rxBytesPerSec, 0).removePrefix("↓"),
                            isSelected = selectedMetric == 4,
                            history = s.networkStat.historyRx,
                            onClick = { onSelectMetric(4) },
                            sampleCount = s.sampleCount,
                            isCompact = true
                        )
                        MetricSidebarItem(
                            title = "Battery",
                            subtitle = "${s.batteryStat.percentage}%",
                            isSelected = selectedMetric == 5,
                            history = s.batteryStat.history,
                            onClick = { onSelectMetric(5) },
                            sampleCount = s.sampleCount,
                            isCompact = true
                        )
                    }
                }
            }
        }
    } else {
        // DESKTOP LAYOUT
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            // PERFORMANCE SIDEBAR
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .drawBehind {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    }
                    .verticalScroll(rememberScrollState())
            ) {
                stats?.let { s ->
                    val sortedFreqs = s.coreStats.map { it.frequencyMhz }.sortedDescending()
                    val f1 = sortedFreqs.getOrNull(0) ?: 0
                    val f2 = sortedFreqs.getOrNull(1) ?: f1
                    val refreshRate = ThemeManager.getPerformanceRefreshRate(context)
                    val samplesPer2S = when(refreshRate) { 1->2L; 2->4L; 3->10L; 4->20L; 5->8L; 6->1L; else->2L }.coerceAtLeast(1L)
                    val displayFreq = if ((s.sampleCount / samplesPer2S) % 2 == 1L) f2 else f1
                    
                    MetricSidebarItem(
                        title = "CPU",
                        subtitle = "${s.cpuOverallUtil.toInt()}% ${if (displayFreq >= 1000) String.format("%.2f GHz", displayFreq/1000f) else "$displayFreq MHz"}",
                        isSelected = selectedMetric == 0,
                        history = s.cpuHistory,
                        onClick = { onSelectMetric(0) },
                        sampleCount = s.sampleCount
                    )
                    MetricSidebarItem(
                        title = "Memory",
                        subtitle = "${String.format("%.1f", s.memoryStat.usedRamMb / 1024f)}/${String.format("%.1f", s.memoryStat.totalRamMb / 1024f)} GB (${(s.memoryStat.usedRamMb * 100 / s.memoryStat.totalRamMb)}%)",
                        isSelected = selectedMetric == 1,
                        history = s.memoryStat.ramHistory,
                        onClick = { onSelectMetric(1) },
                        sampleCount = s.sampleCount
                    )
                    MetricSidebarItem(
                        title = "GPU",
                        subtitle = "${s.gpuStat.gpuName} (${if (s.gpuStat.gpuUtilPercent >= 0) "${s.gpuStat.gpuUtilPercent.toInt()}%" else "${s.gpuStat.temperature.toInt()}°C"})",
                        isSelected = selectedMetric == 2,
                        history = s.gpuStat.gpuHistory,
                        onClick = { onSelectMetric(2) },
                        sampleCount = s.sampleCount
                    )
                    MetricSidebarItem(
                        title = s.diskStat.storageName,
                        subtitle = "${s.diskStat.utilization.toInt()}%",
                        isSelected = selectedMetric == 3,
                        history = s.diskStat.history,
                        onClick = { onSelectMetric(3) },
                        sampleCount = s.sampleCount
                    )
                    MetricSidebarItem(
                        title = "Wi-Fi",
                        subtitle = "R: ${formatNetworkSpeed(s.networkStat.rxBytesPerSec, 0).removePrefix("↓")} S: ${formatNetworkSpeed(0, s.networkStat.txBytesPerSec).removePrefix("↑")}",
                        isSelected = selectedMetric == 4,
                        history = s.networkStat.historyRx,
                        onClick = { onSelectMetric(4) },
                        sampleCount = s.sampleCount
                    )
                    MetricSidebarItem(
                        title = "Battery",
                        subtitle = "${s.batteryStat.percentage}% (${s.batteryStat.status})",
                        isSelected = selectedMetric == 5,
                        history = s.batteryStat.history,
                        onClick = { onSelectMetric(5) },
                        sampleCount = s.sampleCount
                    )
                }
            }

            // DETAIL PANEL
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                stats?.let { s ->
                    when (selectedMetric) {
                        0 -> CpuDetailView(s, mode, onShowMenu, isCompact)
                        1 -> MemoryDetailView(s, onShowMenu, isCompact)
                        2 -> GpuDetailView(s, onShowMenu, isCompact)
                        3 -> DiskDetailView(s, onShowMenu, isCompact)
                        4 -> NetworkDetailView(s, onShowMenu, isCompact)
                        5 -> BatteryDetailView(s, onShowMenu, isCompact)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSidebarItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    history: List<Float>,
    onClick: () -> Unit,
    sampleCount: Long = 0L,
    isCompact: Boolean = false
) {
    val context = LocalContext.current
    val historySeconds = remember(history.size) { ThemeManager.getPerformanceHistoryLength(context) }
    
    val content = @Composable {
        Row(
            modifier = Modifier
                .then(if (isCompact) Modifier.width(130.dp) else Modifier.fillMaxWidth()) // Slightly narrower width for horizontal strip
                .height(if (isCompact) 72.dp else 60.dp) // Taller but fits in 80dp strip comfortably
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Graph
            Box(modifier = Modifier.size(if (isCompact) 42.dp else 50.dp, 32.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(2.dp)).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) {
                val baseColor = when (title) {
                    "CPU" -> MaterialTheme.colorScheme.primary
                    "Memory" -> Color(0xFF7C4DFF)
                    "GPU" -> Color(0xFFFF4081)
                    "Wi-Fi" -> Color(0xFFFF9800)
                    "Battery" -> Color(0xFFFBC02D)
                    else -> MaterialTheme.colorScheme.primary
                }
                
                TrendLineGraph(
                    section = if (title == "Wi-Fi") "Sidebar" else title,
                    historyData = history, 
                    strokeColor = if (isSelected) baseColor else baseColor.copy(alpha = 0.4f), 
                    modifier = Modifier.fillMaxSize().padding(2.dp), 
                    drawFill = false, 
                    historySize = historySeconds,
                    displayUnit = if (title == "Wi-Fi") " Kbps" else if (title == "Memory") " GB" else "%",
                    unitFactor = 1f,
                    sampleCount = sampleCount
                )
            }
            
            Spacer(Modifier.width(if (isCompact) 6.dp else 12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val labelColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Text(title, style = if (isCompact) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (isCompact) {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            content()
        }
    } else {
        Surface(
            onClick = onClick,
            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun CpuDetailView(stats: SystemStats, mode: PerfGraphMode, onShowMenu: (Offset, String, PerfGraphMode) -> Unit, isCompact: Boolean) {
    var componentOffset by remember { mutableStateOf(Offset.Zero) }
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // RESPONSIVE HEADER
        if (isCompact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("CPU", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stats.cpuModelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${stats.maxCpuFreqMhz / 1000f} GHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("CPU", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(stats.cpuModelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${stats.maxCpuFreqMhz / 1000f} GHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 240.dp else 0.dp) // Provide fixed height on mobile scroll view
            .then(if (!isCompact) Modifier.weight(1f) else Modifier)
            .onGloballyPositioned { componentOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset -> onShowMenu(componentOffset + offset, "CPU", mode) }
                )
            }
        ) {
            when (mode) {
                PerfGraphMode.OVERALL -> {
                    PerformanceGraph(
                        section = "CPU",
                        history = stats.cpuHistory, 
                        historySecondary = null,
                        color = MaterialTheme.colorScheme.primary, 
                        colorSecondary = Color.Transparent,
                        label = "% Utilization", 
                        unit = "%",
                        maxVal = 100f,
                        drawMovingGrid = true,
                        sampleCount = stats.sampleCount
                    )
                }
                PerfGraphMode.LOGICAL -> {
                    val coreCount = stats.coreCount
                    val finalCols = if (isCompact) 4 else 4 // User might prefer grid even on mobile
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(finalCols),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false
                    ) {
                        items(stats.coreStats) { core ->
                            LogicalCoreCard(core, historySize = stats.cpuHistory.size, sampleCount = stats.sampleCount)
                        }
                    }
                }
                else -> {
                    PerformanceGraph(
                        section = "CPU",
                        history = stats.cpuHistory, 
                        historySecondary = null,
                        color = MaterialTheme.colorScheme.primary, 
                        colorSecondary = Color.Transparent,
                        label = "% Utilization", 
                        unit = "%",
                        maxVal = 100f,
                        sampleCount = stats.sampleCount
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        CpuStatsGrid(stats, isCompact)
    }
}

@Composable
private fun LogicalCoreCard(core: CoreStat, historySize: Int, sampleCount: Long = 0L) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(2.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .padding(2.dp)
    ) {
        TrendLineGraph(
            section = "CPU",
            historyData = core.history,
            strokeColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
            drawFill = true,
            historySize = historySize,
            displayUnit = "%",
            unitFactor = 1f,
            sampleCount = sampleCount
        )
        
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (core.frequencyMhz >= 1000) "${String.format("%.2f", core.frequencyMhz / 1000f)} GHz" else "${core.frequencyMhz} MHz",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${core.utilization.toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun MemoryDetailView(stats: SystemStats, onShowMenu: (Offset, String, PerfGraphMode) -> Unit, isCompact: Boolean) {
    var componentOffset by remember { mutableStateOf(Offset.Zero) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // UNIFIED HEADER
        if (isCompact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Memory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("System RAM", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${String.format("%.1f", stats.memoryStat.totalRamMb / 1024f)} GB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("Memory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text("System RAM", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${String.format("%.1f", stats.memoryStat.totalRamMb / 1024f)} GB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 240.dp else 0.dp)
            .then(if (!isCompact) Modifier.weight(1f) else Modifier)
            .onGloballyPositioned { componentOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset -> onShowMenu(componentOffset + offset, "Memory", PerfGraphMode.OVERALL) })
            }
        ) {
            val totalGb = stats.memoryStat.totalRamMb / 1024f
            PerformanceGraph(
                section = "Memory",
                history = stats.memoryStat.ramHistory.map { (it / 100f) * totalGb },
                historySecondary = null,
                color = Color(0xFF7C4DFF),
                colorSecondary = Color.Transparent,
                label = "Memory Usage",
                unit = " GB",
                maxVal = totalGb,
                sampleCount = stats.sampleCount
            )
        }
        Spacer(Modifier.height(16.dp))
        MemoryStatsGrid(stats, isCompact)
    }
}

@Composable
private fun MemoryStatsGrid(stats: SystemStats, isCompact: Boolean) {
    // Column 1: Live Primary Metrics
    val liveMetrics = listOf(
        "In use" to "${String.format("%.1f", stats.memoryStat.usedRamMb / 1024f)} GB",
        "Available" to "${String.format("%.1f", stats.memoryStat.availRamMb / 1024f)} GB"
    )

    // Column 2: System Data
    val systemMetrics = listOf(
        "Committed" to "${stats.memoryStat.usedRamMb}/${stats.memoryStat.totalRamMb} MB",
        "Cached" to "${stats.memoryStat.cachedMb} MB",
        "Buffers" to "${stats.memoryStat.buffersMb} MB"
    )

    // Column 3: Structured Technical Grid
    val techSpecs = listOf(
        "Compressed:" to "${stats.memoryStat.compressedMb} MB",
        "Swap total:" to "${stats.memoryStat.swapTotalMb} MB",
        "Swap used:" to "${stats.memoryStat.swapUsedMb} MB",
        "ZRAM total:" to "${stats.memoryStat.zramTotalMb} MB",
        "Pressure:" to stats.memoryStat.pressure
    )

    if (isCompact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    liveMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    systemMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                techSpecs.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.width(120.dp)) {
                liveMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
            }

            Column(modifier = Modifier.width(120.dp)) {
                systemMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(techSpecs) { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun GpuDetailView(stats: SystemStats, onShowMenu: (Offset, String, PerfGraphMode) -> Unit, isCompact: Boolean) {
    var componentOffset by remember { mutableStateOf(Offset.Zero) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // RESPONSIVE HEADER
        if (isCompact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("GPU 0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stats.gpuStat.gpuName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (stats.gpuStat.baseFreqMhz > 0) "${stats.gpuStat.baseFreqMhz} MHz" else stats.gpuStat.gpuVendor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("GPU 0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(stats.gpuStat.gpuName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(if (stats.gpuStat.baseFreqMhz > 0) "${stats.gpuStat.baseFreqMhz} MHz" else stats.gpuStat.gpuVendor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 240.dp else 0.dp)
            .then(if (!isCompact) Modifier.weight(1f) else Modifier)
            .onGloballyPositioned { componentOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset -> onShowMenu(componentOffset + offset, "GPU", PerfGraphMode.OVERALL) })
            }
        ) {
            if (stats.gpuStat.gpuUtilPercent < 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("GPU Utilization unsupported by device", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Optional: could show temperature graph here if needed, but for now we follow request
                }
            } else {
                PerformanceGraph(
                    section = "GPU",
                    history = stats.gpuStat.gpuHistory,
                    historySecondary = null,
                    color = Color(0xFFFF4081),
                    colorSecondary = Color.Transparent,
                    label = "% Utilization",
                    unit = "%",
                    maxVal = 100f,
                    sampleCount = stats.sampleCount
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        GpuStatsGrid(stats, isCompact)
    }
}

@Composable
private fun GpuStatsGrid(stats: SystemStats, isCompact: Boolean) {
    // Column 1: Live Primary Metrics
    val liveMetrics = listOf(
        "Utilization" to if (stats.gpuStat.gpuUtilPercent >= 0) "${stats.gpuStat.gpuUtilPercent.toInt()}%" else "Unsupported",
        "Core clock" to "${stats.gpuStat.gpuFreqMhz} MHz",
        "Temperature" to "${stats.gpuStat.temperature.toInt()}°C"
    )

    // Column 2: Clock Data
    val clockMetrics = listOf(
        "Max clock" to "${stats.gpuStat.maxFreqMhz} MHz",
        "Base clock" to if (stats.gpuStat.baseFreqMhz > 0) "${stats.gpuStat.baseFreqMhz} MHz" else "Unknown"
    )

    // Column 3: Structured Technical Grid
    val techSpecs = listOf(
        "Vendor:" to stats.gpuStat.gpuVendor,
        "Driver version:" to stats.gpuStat.driverVersion.take(15),
        "API Support:" to stats.gpuStat.api,
        "GL Renderer:" to stats.gpuStat.rendererGl.substringBefore(" ")
    )

    if (isCompact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    liveMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    clockMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                techSpecs.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.width(120.dp)) {
                liveMetrics.forEach { (label, value) ->
                    StatItem(label, value, isLarge = true)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Column(modifier = Modifier.width(120.dp)) {
                clockMetrics.forEach { (label, value) ->
                    StatItem(label, value, isLarge = true)
                    Spacer(Modifier.height(8.dp))
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(techSpecs) { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiskDetailView(stats: SystemStats, onShowMenu: (Offset, String, PerfGraphMode) -> Unit, isCompact: Boolean) {
    var componentOffset by remember { mutableStateOf(Offset.Zero) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // UNIFIED HEADER
        if (isCompact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Disk 0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stats.diskStat.storageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stats.diskStat.storageType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("Disk 0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(stats.diskStat.storageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(stats.diskStat.storageType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 240.dp else 0.dp)
            .then(if (!isCompact) Modifier.weight(1f) else Modifier)
            .onGloballyPositioned { componentOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset -> onShowMenu(componentOffset + offset, "Disk", PerfGraphMode.OVERALL) })
            }
        ) {
            PerformanceGraph(
                section = "Disk",
                history = stats.diskStat.history,
                historySecondary = null,
                color = Color(0xFF00E676),
                colorSecondary = Color.Transparent,
                label = "% Active time",
                unit = "%",
                maxVal = 100f,
                sampleCount = stats.sampleCount
            )
        }
        Spacer(Modifier.height(16.dp))
        DiskStatsGrid(stats, isCompact)
    }
}

@Composable
private fun DiskStatsGrid(stats: SystemStats, isCompact: Boolean) {
    // Column 1: Live Primary Metrics
    val liveMetrics = listOf(
        "Active time" to "${stats.diskStat.utilization.toInt()}%",
        "Read speed" to formatNetworkSpeed(stats.diskStat.readSpeedBps, 0).removePrefix("↓"),
        "Write speed" to formatNetworkSpeed(0, stats.diskStat.writeSpeedBps).removePrefix("↑")
    )

    // Column 2: Technical Grid
    val techSpecs = listOf(
        "Type:" to stats.diskStat.storageType,
        "Filesystem:" to stats.diskStat.filesystem,
        "Temperature:" to if (stats.diskStat.temperature > 0) "${stats.diskStat.temperature}°C" else "Unknown"
    )

    if (isCompact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            liveMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                techSpecs.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.width(150.dp)) {
                liveMetrics.forEach { (label, value) ->
                    StatItem(label, value, isLarge = true)
                    Spacer(Modifier.height(8.dp))
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(techSpecs) { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkDetailView(stats: SystemStats, onShowMenu: (Offset, String, PerfGraphMode) -> Unit, isCompact: Boolean) {
    var componentOffset by remember { mutableStateOf(Offset.Zero) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // UNIFIED HEADER
        if (isCompact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Wi-Fi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stats.networkStat.ssid, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${stats.networkStat.bandGhz} GHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("Wi-Fi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(stats.networkStat.ssid, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${stats.networkStat.bandGhz} GHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 240.dp else 0.dp)
            .then(if (!isCompact) Modifier.weight(1f) else Modifier)
            .onGloballyPositioned { componentOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset -> onShowMenu(componentOffset + offset, "Wi-Fi", PerfGraphMode.OVERALL) })
            }
        ) {
            PerformanceGraph(
                section = "Wi-Fi",
                history = stats.networkStat.historyRx, 
                historySecondary = stats.networkStat.historyTx, 
                color = Color(0xFFFF9800), // Distinct Orange/Amber for Wi-Fi
                colorSecondary = Color(0xFFFF9800).copy(alpha = 0.7f),
                label = "Throughput",
                unit = " Kbps",
                maxVal = (stats.networkStat.peakThroughputBps / 1024f).coerceAtLeast(100f),
                sampleCount = stats.sampleCount
            )
        }
        Spacer(Modifier.height(16.dp))
        NetworkStatsGrid(stats, isCompact)
    }
}

@Composable
private fun NetworkStatsGrid(stats: SystemStats, isCompact: Boolean) {
    val context = LocalContext.current
    val useBits = ThemeManager.useBitsForNetwork(context)
    
    // Convert engine data (Kilobits) to the user's preferred format
    fun formatSingle(kbps: Float): String {
        val value = if (useBits) kbps else kbps / 8f
        val suffix = if (useBits) "bps" else "B/s"
        return when {
            value >= 1024 * 1024 -> String.format("%.1f G%s", value / (1024 * 1024), suffix)
            value >= 1024 -> String.format("%.1f M%s", value / 1024, suffix)
            value >= 1 -> String.format("%.1f K%s", value, suffix)
            else -> String.format("%.0f %s", value * 1024, if (useBits) "bps" else "B/s")
        }
    }

    // Convert raw byte counters for the labels
    val sendSpeed = formatSingle(stats.networkStat.txBytesPerSec * 8f / 1024f)
    val receiveSpeed = formatSingle(stats.networkStat.rxBytesPerSec * 8f / 1024f)

    // Column 2: Connection Specs
    val connType = if (stats.networkStat.wifiGeneration.isNotEmpty()) {
        "${stats.networkStat.wifiGeneration} (${stats.networkStat.wifiStandard})"
    } else if (stats.networkStat.wifiStandard.isNotEmpty()) {
        stats.networkStat.wifiStandard
    } else {
        stats.networkStat.type
    }

    val techSpecs = listOf(
        "Adapter name:" to "Wi-Fi",
        "SSID:" to stats.networkStat.ssid,
        "Connection type:" to connType,
        "IPv4 address:" to stats.networkStat.ipAddress,
        "IPv6 address:" to "Not available",
        "MAC address:" to stats.networkStat.macAddress,
        "Signal strength:" to getSignalStrengthText(stats.networkStat.signalDbm),
        "Link speed:" to "${stats.networkStat.linkSpeedMbps} Mbps"
    )

    if (isCompact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Send", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sendSpeed, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Receive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(receiveSpeed, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                techSpecs.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else {
        // Column 1: Live Primary Metrics (Windows Style Indicators)
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.width(140.dp)) {
                // Send (Dashed Indicator)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(32.dp)
                            .drawBehind {
                                val strokeWidth = size.width
                                val dashLength = 4.dp.toPx()
                                val gapLength = 4.dp.toPx()
                                var y = 0f
                                while (y < size.height) {
                                    drawLine(
                                        color = Color(0xFFFF9800),
                                        start = Offset(size.width / 2, y),
                                        end = Offset(size.width / 2, (y + dashLength).coerceAtMost(size.height)),
                                        strokeWidth = strokeWidth
                                    )
                                    y += dashLength + gapLength
                                }
                            }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Send", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sendSpeed, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Receive (Solid Indicator)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(2.dp).height(32.dp).background(Color(0xFFFF9800)))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Receive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(receiveSpeed, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(Modifier.width(32.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(techSpecs) { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun getSignalStrengthText(dbm: Int): String {
    return when {
        dbm > -50 -> "Excellent"; dbm > -60 -> "Good"; dbm > -70 -> "Fair"; else -> "Poor"
    }
}

@Composable
private fun BatteryDetailView(stats: SystemStats, onShowMenu: (Offset, String, PerfGraphMode) -> Unit, isCompact: Boolean) {
    var componentOffset by remember { mutableStateOf(Offset.Zero) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // UNIFIED HEADER
        if (isCompact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Battery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stats.batteryStat.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stats.batteryStat.health, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("Battery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(stats.batteryStat.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(stats.batteryStat.health, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 240.dp else 0.dp)
            .then(if (!isCompact) Modifier.weight(1f) else Modifier)
            .onGloballyPositioned { componentOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset -> onShowMenu(componentOffset + offset, "Battery", PerfGraphMode.OVERALL) })
            }
        ) {
            PerformanceGraph(
                section = "Battery",
                history = stats.batteryStat.history,
                historySecondary = null,
                color = Color(0xFFFBC02D), // Gold/Yellow
                colorSecondary = Color.Transparent,
                label = "Charge Level",
                unit = "%",
                maxVal = 100f,
                sampleCount = stats.sampleCount
            )
        }
        Spacer(Modifier.height(16.dp))
        BatteryStatsGrid(stats, isCompact)
    }
}

@Composable
private fun BatteryStatsGrid(stats: SystemStats, isCompact: Boolean) {
    // Column 1: Live Primary Metrics
    val liveMetrics = listOf(
        "Percentage" to "${stats.batteryStat.percentage}%",
        "Power draw" to if (stats.batteryStat.powerDrawMw != 0) "${stats.batteryStat.powerDrawMw} mW" else "Unknown",
        "Status" to stats.batteryStat.status
    )

    // Column 2: Health & Technical Data
    val techSpecs = listOf(
        "Health:" to stats.batteryStat.health,
        "Voltage:" to "${stats.batteryStat.voltageMv} mV",
        "Temperature:" to "${stats.batteryStat.temperature}°C",
        "Cycle count:" to if (stats.batteryStat.cycleCount > 0) stats.batteryStat.cycleCount.toString() else "Unknown",
        "Estimated time:" to "Calculating..."
    )

    if (isCompact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            liveMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                techSpecs.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.width(130.dp)) {
                liveMetrics.forEach { (label, value) ->
                    StatItem(label, value, isLarge = true)
                    Spacer(Modifier.height(8.dp))
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(techSpecs) { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: DetectionConfidence) {
    val color = when(confidence) {
        DetectionConfidence.HIGH -> Color(0xFF4CAF50)
        DetectionConfidence.MEDIUM -> Color(0xFFFFC107)
        DetectionConfidence.OVERRIDE -> MaterialTheme.colorScheme.primary
        else -> Color.Gray
    }
    val text = when(confidence) {
        DetectionConfidence.HIGH -> "✓ High Confidence"; DetectionConfidence.MEDIUM -> "Medium Confidence"
        DetectionConfidence.OVERRIDE -> "Manual Override"; else -> "Low Confidence"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 8.sp, color = color.copy(alpha = 0.8f))
    }
}

@Composable
private fun CpuStatsGrid(stats: SystemStats, isCompact: Boolean) {
    val context = LocalContext.current
    // Alternating Logic: Switch between 1st and 2nd highest frequency every 2 seconds
    val sortedFreqs = stats.coreStats.map { it.frequencyMhz }.sortedDescending()
    val f1 = sortedFreqs.getOrNull(0) ?: 0
    val f2 = sortedFreqs.getOrNull(1) ?: f1
    
    val refreshRate = ThemeManager.getPerformanceRefreshRate(context)
    val samplesPer2S = when(refreshRate) { 1->2L; 2->4L; 3->10L; 4->20L; 5->8L; 6->1L; else->2L }.coerceAtLeast(1L)
    val useSecond = (stats.sampleCount / samplesPer2S) % 2 == 1L
    val currentFreq = if (useSecond) f2 else f1

    // Column 1: Live Primary Metrics
    val liveMetrics = listOf(
        "Utilization" to "${stats.cpuOverallUtil.toInt()}%",
        "Speed" to if (currentFreq >= 1000) "${String.format("%.2f", currentFreq / 1000f)} GHz" else "$currentFreq MHz",
        "Up time" to stats.uptimeFormatted
    )
    
    // Column 2: System Totals
    val totalMetrics = listOf(
        "Processes" to stats.processCount.toString(),
        "Threads" to stats.threadCount.toString(),
        "Handles" to stats.handleCount.toString()
    )
    
    // Column 3: Hardware Specs & Technical Data (Reordered to match Windows Task Manager style)
    val hwSpecs = listOf(
        "Base speed:" to "${String.format("%.2f", stats.maxCpuFreqMhz / 1000f)}GHz",
        "Cores:" to stats.coreCount.toString(),
        "Logical processors:" to stats.coreCount.toString(),
        "Process node:" to stats.cpuProcessNode,
        "Instruction set:" to stats.cpuInstructionSet,
        "CPU Temp:" to "${stats.cpuTemp.toInt()}°C",
        "Kernel version:" to stats.kernelVersion.substringBefore(" "),
        "Java VM:" to stats.javaVm.substringBefore("-")
    )

    if (isCompact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    liveMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    totalMetrics.forEach { (label, value) -> StatItem(label, value, isLarge = true); Spacer(Modifier.height(8.dp)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Single Column for Tech Specs on Mobile to avoid wrapping
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hwSpecs.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            // Column 1
            Column(modifier = Modifier.width(120.dp)) {
                liveMetrics.forEach { (label, value) ->
                    StatItem(label, value, isLarge = true)
                    Spacer(Modifier.height(8.dp))
                }
            }
            
            // Column 2
            Column(modifier = Modifier.width(120.dp)) {
                totalMetrics.forEach { (label, value) ->
                    StatItem(label, value, isLarge = true)
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Column 3: Structured Technical Grid (Restored Data)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(hwSpecs) { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, isLarge: Boolean = false) {
    Column {
        Text(value, style = if (isLarge) MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PerformanceGraph(
    section: String,
    history: List<Float>,
    historySecondary: List<Float>? = null, // Support for dual lines
    color: Color,
    colorSecondary: Color = Color.Transparent,
    label: String,
    unit: String,
    maxVal: Float = 100f,
    drawMovingGrid: Boolean = false,
    sampleCount: Long = 0L
) {
    val context = LocalContext.current
    val historySeconds = remember { ThemeManager.getPerformanceHistoryLength(context) }
    val useBits = remember { ThemeManager.useBitsForNetwork(context) }
    
    // Dynamic Scaling Logic
    val dynamicMax = remember(history, historySecondary, maxVal, useBits, section) {
        if (section != "Wi-Fi") {
            maxVal
        } else {
            val peak1 = history.takeLast(historySeconds).maxOrNull() ?: 0f
            val peak2 = historySecondary?.takeLast(historySeconds)?.maxOrNull() ?: 0f
            val rawPeak = kotlin.math.max(peak1, peak2)
            
            // Convert to selected unit system if needed (Engine stores in Kilobits)
            val peak = if (!useBits && unit.contains("bps")) rawPeak / 8f else rawPeak

            val baseline = 100f
            val target = (peak * 1.25f).coerceAtLeast(baseline)
            val steps = listOf(1f, 10f, 20f, 50f, 100f, 250f, 500f, 1000f, 2500f, 5000f, 10000f, 25000f, 50000f, 100000f)
            steps.firstOrNull { it >= target } ?: target
        }
    }
    
    val animatedMax by animateFloatAsState(
        targetValue = dynamicMax,
        animationSpec = tween(durationMillis = 1000), label = "graphScale"
    )

    // Dynamic Unit Selection (bps/Kbps/Mbps/Gbps or B/s/KB/s/MB/s/GB/s)
    val dynamicUnitData = remember(animatedMax, unit, useBits) {
        if (unit.contains("bps")) {
            val b = if (useBits) "b" else ""
            val s = if (useBits) "" else "B"
            val baseSuffix = if (useBits) "ps" else "/s"
            
            when {
                animatedMax >= 1024 * 1024f -> Triple(animatedMax / (1024 * 1024f), " G${b}${s}${baseSuffix}", 1024 * 1024f)
                animatedMax >= 1024f -> Triple(animatedMax / 1024f, " M${b}${s}${baseSuffix}", 1024f)
                else -> Triple(animatedMax, " K${b}${s}${baseSuffix}", 1f)
            }
        } else {
            Triple(animatedMax, unit, 1f)
        }
    }
    val displayMax = dynamicUnitData.first
    val displayUnit = dynamicUnitData.second
    val unitFactor = dynamicUnitData.third

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            if (label.isNotEmpty()) {
                Text(
                    text = label, 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }
            // AXIS LABEL
            Text(
                text = if (unit == "%") "100%" else "${displayMax.toInt()}${displayUnit.trim()}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp, bottom = 18.dp, end = 2.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(2.dp))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            ) {
                TrendLineGraph(
                    section = section,
                    historyData = if (useBits || !unit.contains("bps")) history else history.map { it / 8f },
                    historyDataSecondary = if (historySecondary != null) (if (useBits || !unit.contains("bps")) historySecondary else historySecondary.map { it / 8f }) else null,
                    strokeColor = color,
                    strokeColorSecondary = colorSecondary,
                    modifier = Modifier.fillMaxSize(), 
                    drawFill = true, 
                    maxVal = animatedMax,
                    historySize = historySeconds, 
                    displayUnit = displayUnit,
                    drawMovingGrid = drawMovingGrid,
                    sampleCount = sampleCount
                )
            }

            Text(
                "0", 
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp)
            )

            // Legend Overlay (Updated to handle dual lines)
            if (historySecondary == null || section == "Wi-Fi") {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer(alpha = 0.4f)
                        .padding(end = 48.dp, top = 20.dp), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val currentValRaw = history.lastOrNull() ?: 0f
                    
                    val (valStr, unitStr) = if (unit == "%") {
                        String.format("%.0f", currentValRaw) to "%"
                    } else if (unit == " GB") {
                        String.format("%.1f", currentValRaw) to " GB"
                    } else {
                        val currentValBase = if (useBits || !unit.contains("bps")) currentValRaw else currentValRaw / 8f
                        val b = if (useBits) "b" else ""
                        val s = if (useBits) "" else "B"
                        val baseSuffix = if (useBits) "ps" else "/s"
                        when {
                            currentValBase >= 1024 * 1024 -> String.format("%.1f", currentValBase / (1024 * 1024)) to " G${b}${s}${baseSuffix}"
                            currentValBase >= 1024 -> String.format("%.1f", currentValBase / 1024) to " M${b}${s}${baseSuffix}"
                            currentValBase >= 1 -> String.format("%.1f", currentValBase) to " K${b}${s}${baseSuffix}"
                            else -> String.format("%.0f", currentValBase * 1024) to " ${b}${s}${baseSuffix}"
                        }
                    }

                    Text("$valStr$unitStr", style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp), fontWeight = FontWeight.Bold, color = color)
                    Text("Current", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun Windows11PerformanceContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    currentMode: PerfGraphMode,
    onModeChange: (PerfGraphMode) -> Unit,
    stats: SystemStats,
    offset: Offset = Offset.Zero,
    section: String = ""
) {
    if (expanded) {
        val context = LocalContext.current
        val density = LocalDensity.current
        var openSubmenu by remember { mutableStateOf<String?>(null) }
        val itemOffsets = remember { mutableStateMapOf<String, Float>() }
        
        // Mobile-aware scaling and edge detection
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val isNearRightEdge = offset.x > (screenWidthPx * 0.5f) // Simplify detection for submenus

        // Ensure submenu closes if expanded state resets
        LaunchedEffect(expanded) { if (!expanded) openSubmenu = null }

        Popup(
            onDismissRequest = onDismissRequest, 
            offset = IntOffset(with(density) { offset.x.toInt() }, with(density) { offset.y.toInt() }),
            properties = PopupProperties(focusable = true, clippingEnabled = false)
        ) {
            // Root container - Row for desktop (side-by-side), can be Column for mobile if very narrow
            Row(
                modifier = Modifier.wrapContentSize(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = if (isNearRightEdge) Arrangement.End else Arrangement.Start
            ) {
                // 1. SUBMENU (Show to LEFT if near right edge)
                if (openSubmenu != null && isNearRightEdge) {
                    PerformanceSubmenu(
                        submenu = openSubmenu!!,
                        currentMode = currentMode,
                        onModeChange = onModeChange,
                        onDismissRequest = onDismissRequest,
                        itemY = itemOffsets[openSubmenu!!] ?: 0f,
                        density = density,
                        context = context
                    )
                }

                // 2. MAIN MENU
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.width(200.dp) // Slightly narrower for mobile
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (section == "CPU") {
                            ContextMenuItem(
                                text = "Change graph to", 
                                icon = Icons.Default.ShowChart, 
                                tint = MaterialTheme.colorScheme.onSurface, 
                                onClick = { openSubmenu = if (openSubmenu == "graph") null else "graph" }, 
                                hasSubmenu = true, 
                                isSelected = openSubmenu == "graph",
                                modifier = Modifier.onGloballyPositioned { 
                                    itemOffsets["graph"] = it.positionInParent().y 
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                        
                        if (section == "Wi-Fi" || section == "Disk") {
                            ContextMenuItem(
                                text = "Show speed in", 
                                icon = Icons.Default.Speed, 
                                tint = MaterialTheme.colorScheme.onSurface, 
                                onClick = { openSubmenu = if (openSubmenu == "units") null else "units" }, 
                                hasSubmenu = true, 
                                isSelected = openSubmenu == "units",
                                modifier = Modifier.onGloballyPositioned { 
                                    itemOffsets["units"] = it.positionInParent().y 
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }

                        if (section == "Wi-Fi") {
                            ContextMenuItem("Open Wi-Fi properties", Icons.Default.Info, MaterialTheme.colorScheme.onSurface, { 
                                try { val intent = Intent(Settings.ACTION_WIFI_SETTINGS); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) } catch (e: Exception) {}
                                onDismissRequest() 
                            })
                            ContextMenuItem("Network & Internet settings", Icons.Default.Settings, MaterialTheme.colorScheme.onSurface, { 
                                try { val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) } catch (e: Exception) {}
                                onDismissRequest() 
                            })
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                        
                        ContextMenuItem(
                            text = "Sampling history", 
                            icon = Icons.Default.History, 
                            tint = MaterialTheme.colorScheme.onSurface, 
                            onClick = { openSubmenu = if (openSubmenu == "history") null else "history" }, 
                            hasSubmenu = true, 
                            isSelected = openSubmenu == "history",
                            modifier = Modifier.onGloballyPositioned { 
                                itemOffsets["history"] = it.positionInParent().y 
                            }
                        )

                        ContextMenuItem(
                            text = "Update speed", 
                            icon = Icons.Default.Speed, 
                            tint = MaterialTheme.colorScheme.onSurface, 
                            onClick = { openSubmenu = if (openSubmenu == "speed") null else "speed" }, 
                            hasSubmenu = true, 
                            isSelected = openSubmenu == "speed",
                            modifier = Modifier.onGloballyPositioned { 
                                itemOffsets["speed"] = it.positionInParent().y 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        
                        ContextMenuItem("Copy hardware info", Icons.Default.ContentCopy, MaterialTheme.colorScheme.onSurface, { 
                            val info = "CPU: ${stats.cpuModelName}\nGPU: ${stats.gpuStat.gpuName}\nRAM: ${stats.memoryStat.totalRamMb} MB"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Hardware Info", info))
                            Toast.makeText(context, "Hardware info copied", Toast.LENGTH_SHORT).show()
                            onDismissRequest() 
                        })
                        
                        ContextMenuItem("Export snapshot", Icons.Default.FileDownload, MaterialTheme.colorScheme.onSurface, { 
                            val snapshot = SystemMonitorEngine.exportPerformanceSnapshot(stats)
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, snapshot) }
                            context.startActivity(Intent.createChooser(intent, "Share Performance Snapshot"))
                            onDismissRequest() 
                        })

                        // THIRD-PARTY HARDWARE INFO APPS
                        val hwApps = listOf(
                            "CPU-Z" to "com.cpuid.cpu_z",
                            "DevCheck" to "flar2.devcheck",
                            "Device Info HW" to "ru.andr7e.deviceinfohw",
                            "AIDA64" to "com.finalwire.aida64",
                            "Device Info" to "com.ytheekshana.deviceinfo",
                            "CPU X" to "com.abs.cpu_z_advance",
                            "G-CPU" to "com.insideinc.gcpu"
                        )
                        val availableApps = remember(hwApps) {
                            hwApps.filter { (_, pkg) ->
                                try {
                                    context.packageManager.getPackageInfo(pkg, 0)
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        }

                        if (availableApps.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            availableApps.forEach { (name, pkg) ->
                                ContextMenuItem("Open $name", Icons.Default.Launch, MaterialTheme.colorScheme.onSurface, {
                                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                    onDismissRequest()
                                })
                            }
                        }
                    }
                }

                // 3. SUBMENU (Show to RIGHT if not near edge)
                if (openSubmenu != null && !isNearRightEdge) {
                    PerformanceSubmenu(
                        submenu = openSubmenu!!,
                        currentMode = currentMode,
                        onModeChange = onModeChange,
                        onDismissRequest = onDismissRequest,
                        itemY = itemOffsets[openSubmenu!!] ?: 0f,
                        density = density,
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceSubmenu(
    submenu: String,
    currentMode: PerfGraphMode,
    onModeChange: (PerfGraphMode) -> Unit,
    onDismissRequest: () -> Unit,
    itemY: Float,
    density: Density,
    context: Context
) {
    // Add the column's top padding (4.dp) to align with the item correctly
    val topPaddingPx = itemY + with(density) { 4.dp.toPx() }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .width(180.dp)
            .padding(top = with(density) { topPaddingPx.toDp() })
            .heightIn(max = 240.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp).verticalScroll(rememberScrollState())) {
            when (submenu) {
                "graph" -> {
                    SubmenuItem("Overall utilization", currentMode == PerfGraphMode.OVERALL) { onModeChange(PerfGraphMode.OVERALL); onDismissRequest() }
                    SubmenuItem("Logical processors", currentMode == PerfGraphMode.LOGICAL) { onModeChange(PerfGraphMode.LOGICAL); onDismissRequest() }
                }
                "units" -> {
                    val isBits = ThemeManager.useBitsForNetwork(context)
                    SubmenuItem("Megabits (Mbps)", isBits) { ThemeManager.setUseBitsForNetwork(context, true); onDismissRequest() }
                    SubmenuItem("Megabytes (MB/s)", !isBits) { ThemeManager.setUseBitsForNetwork(context, false); onDismissRequest() }
                }
                "history" -> {
                    val currentHistory = ThemeManager.getPerformanceHistoryLength(context)
                    listOf(15, 30, 60, 90, 120, 180, 300).forEach { sec ->
                        SubmenuItem("$sec Seconds", currentHistory == sec) { ThemeManager.setPerformanceHistoryLength(context, sec); onDismissRequest() }
                    }
                }
                "speed" -> {
                    val currentRate = ThemeManager.getPerformanceRefreshRate(context)
                    val rates = listOf(
                        "High (250ms)" to 5,
                        "Normal (500ms)" to 2,
                        "Fast (200ms)" to 3,
                        "Real-time (100ms)" to 4,
                        "Low (1s)" to 1,
                        "Very Low (1.5s)" to 6
                    )
                    rates.forEach { (label, value) ->
                        SubmenuItem(label, currentRate == value) { 
                            ThemeManager.setPerformanceRefreshRate(context, value)
                            SystemMonitorEngine.stopPolling()
                            SystemMonitorEngine.startPolling(context)
                            onDismissRequest()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(4.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent).clickable { onClick() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        if (isSelected) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TrendLineGraph(
    section: String,
    historyData: List<Float>,
    historyDataSecondary: List<Float>? = null, // Added support for second line
    strokeColor: Color,
    strokeColorSecondary: Color = Color.Transparent, // Added support for second line color
    modifier: Modifier = Modifier,
    drawFill: Boolean = false,
    maxVal: Float = 100f,
    historySize: Int = 60,
    displayUnit: String = "%",
    unitFactor: Float = 1f,
    drawMovingGrid: Boolean = false,
    sampleCount: Long = 0L
) {
    var tooltipPos by remember { mutableStateOf<Offset?>(null) }
    var showTooltip by remember { mutableStateOf(false) }
    var lastPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    
    // Dynamic Max for Wi-Fi Sidebar
    val actualMax = remember(maxVal, historyData, historyDataSecondary, section) {
        if (section == "Sidebar") {
            val p1 = historyData.takeLast(historySize).maxOrNull() ?: 0f
            val p2 = historyDataSecondary?.takeLast(historySize)?.maxOrNull() ?: 0f
            val peak = kotlin.math.max(p1, p2)
            val target = (peak * 1.25f).coerceAtLeast(100f)
            val steps = listOf(1f, 10f, 20f, 50f, 100f, 250f, 500f, 1000f, 2500f, 5000f, 10000f, 25000f, 50000f, 100000f)
            steps.firstOrNull { it >= target } ?: target
        } else maxVal
    }

    val tooltipAlpha by animateFloatAsState(
        targetValue = if (showTooltip && tooltipPos != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing), 
        label = "tooltipAlpha"
    )
    
    BoxWithConstraints(modifier = modifier
        .pointerInput(historyData.size, historySize) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(); val change = event.changes.first()
                    if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) tooltipPos = change.position
                    else if (event.type == PointerEventType.Exit) tooltipPos = null
                }
            }
        }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(tooltipPos, widthPx, heightPx) {
            if (tooltipPos != null) {
                val startIdx = kotlin.math.max(0, historyData.size - historySize)
                val displayData = historyData.drop(startIdx)
                val displayData2 = historyDataSecondary?.drop(startIdx) ?: emptyList()
                
                var isOnArea = false
                if (displayData.isNotEmpty()) {
                    val segmentWidth = if (historySize > 1) widthPx / (historySize - 1) else widthPx
                    val dataIdx = (tooltipPos!!.x / segmentWidth).toInt().coerceIn(0, displayData.size - 1)
                    val value1 = displayData.getOrNull(dataIdx) ?: 0f
                    val value2 = displayData2.getOrNull(dataIdx) ?: 0f
                    val maxValue = kotlin.math.max(value1, value2)
                    
                    val lineY = heightPx - ((maxValue / actualMax).coerceIn(0f, 1f) * heightPx)
                    
                    // Trigger if mouse is at or below the top line (filled area)
                    val tolerance = with(density) { 5.dp.toPx() }
                    if (tooltipPos!!.y >= lineY - tolerance) {
                        isOnArea = true
                    }
                }

                if (isOnArea) {
                    showTooltip = true; lastPos = tooltipPos!!
                } else {
                    val dx = tooltipPos!!.x - lastPos.x
                    val dy = tooltipPos!!.y - lastPos.y
                    val isSignificantMove = (dx * dx + dy * dy) > 200
                    if (isSignificantMove) {
                        showTooltip = false; lastPos = tooltipPos!!
                        kotlinx.coroutines.delay(100); if (tooltipPos != null) showTooltip = true
                    }
                }
            } else showTooltip = false
        }
        
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val path1 = Path()
                    val fillPath1 = Path()
                    val path2 = Path()
                    val fillPath2 = Path()
                    val gridPath = Path()
                    
                    val canvasWidth = this.size.width
                    val canvasHeight = this.size.height
                    
                    val stepX = canvasWidth / (historySize.toFloat() - 1f)
                    val displayData1 = historyData.takeLast(historySize)
                    val displayData2 = historyDataSecondary?.takeLast(historySize)
                    val startPadding = historySize - displayData1.size

                    // Build Main Graph (Receive)
                    if (displayData1.size >= 2) {
                        displayData1.forEachIndexed { i, valPct ->
                            val x = (i + startPadding) * stepX
                            val y = canvasHeight - ((valPct / actualMax).coerceIn(0f, 1f) * canvasHeight)
                            if (i == 0) {
                                path1.moveTo(x, y)
                                fillPath1.moveTo(x, canvasHeight)
                                fillPath1.lineTo(x, y)
                            } else {
                                path1.lineTo(x, y)
                                fillPath1.lineTo(x, y)
                            }
                            if (i == displayData1.size - 1) {
                                fillPath1.lineTo(x, canvasHeight)
                                fillPath1.close()
                            }
                        }
                    }

                    // Build Secondary Graph (Send)
                    if (displayData2 != null && displayData2.size >= 2) {
                        displayData2.forEachIndexed { i, valPct ->
                            val x = (i + startPadding) * stepX
                            val y = canvasHeight - ((valPct / actualMax).coerceIn(0f, 1f) * canvasHeight)
                            if (i == 0) {
                                path2.moveTo(x, y)
                                fillPath2.moveTo(x, canvasHeight)
                                fillPath2.lineTo(x, y)
                            } else {
                                path2.lineTo(x, y)
                                fillPath2.lineTo(x, y)
                            }
                            if (i == displayData2.size - 1) {
                                fillPath2.lineTo(x, canvasHeight)
                                fillPath2.close()
                            }
                        }
                    }

                    // Build Vertical Grid (Synchronized and Moving)
                    val horizontalGridLines = if (section == "Sidebar") 3 else 5
                    val cellHeight = canvasHeight / horizontalGridLines
                    val cellWidth = cellHeight // SQUARES
                    
                    // Points per cell width
                    val pointsPerCell = if (stepX > 0) cellWidth / stepX else 10f
                    
                    // Calculate offset for moving grid. 
                    val globalOffset = if (sampleCount > 0) {
                        ((sampleCount - 1) % pointsPerCell)
                    } else 0f

                    var currentX = canvasWidth - (globalOffset * stepX)
                    while (currentX >= 0) {
                        gridPath.moveTo(currentX, 0f)
                        gridPath.lineTo(currentX, canvasHeight)
                        currentX -= cellWidth
                    }

                    onDrawBehind {
                        // 1. Horizontal static grid
                        for (i in 0..horizontalGridLines) {
                            val y = canvasHeight * (i.toFloat() / horizontalGridLines)
                            drawLine(
                                color = strokeColor.copy(alpha = 0.12f),
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 1.2.dp.toPx()
                            )
                        }

                        // 2. Vertical moving grid
                        drawPath(
                            path = gridPath,
                            color = strokeColor.copy(alpha = 0.08f),
                            style = Stroke(width = 1.2.dp.toPx())
                        )

                        // 3. Graph Fills (Receive Only)
                        if (drawFill) {
                            if (displayData1.size >= 2) {
                                drawPath(
                                    path = fillPath1,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(strokeColor.copy(alpha = 0.15f), strokeColor.copy(alpha = 0.01f)),
                                        startY = 0f,
                                        endY = canvasHeight
                                    )
                                )
                            }
                        }

                        // 4. Main Lines
                        if (displayData2 != null && displayData2.size >= 2) {
                            drawPath(
                                path = path2,
                                color = strokeColor, // Same color as receive
                                style = Stroke(
                                    width = 1.2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(10f, 10f),
                                        phase = 0f
                                    )
                                )
                            )
                        }
                        if (displayData1.size >= 2) {
                            drawPath(
                                path = path1,
                                color = strokeColor,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
        )

        if (tooltipAlpha > 0f) {
            val isTooltipEnabled = ThemeManager.isPerformanceTooltipEnabled(LocalContext.current)
            tooltipPos?.let { pos ->
                if (isTooltipEnabled) {
                    val startIdx = kotlin.math.max(0, historyData.size - historySize)
                    val displayData1 = historyData.drop(startIdx)
                    val displayData2 = historyDataSecondary?.drop(startIdx)
                    if (displayData1.isNotEmpty()) {
                        val segmentWidth = widthPx / (historySize - 1)
                        val dataIdx = (pos.x / segmentWidth).toInt().coerceIn(0, displayData1.size - 1)
                        val value1 = displayData1.getOrNull(dataIdx) ?: 0f
                        val value2 = displayData2?.getOrNull(dataIdx) ?: 0f
                        
                        // Check if hovering over filled area (below the top line)
                        val ratio = (kotlin.math.max(value1, value2) / actualMax).coerceIn(0f, 1f)
                        val canvasHeightPx = constraints.maxHeight.toFloat()
                        val lineY = canvasHeightPx - (ratio * canvasHeightPx)
                        
                        if (pos.y >= lineY) {
                            PerformanceTooltip(
                                section = section,
                                value = value1,
                                value2 = if (historyDataSecondary != null) value2 else null,
                                avg = displayData1.average().toFloat(),
                                max = displayData1.maxOrNull() ?: 0f,
                                offset = pos,
                                displayUnit = displayUnit,
                                unitFactor = unitFactor,
                                alpha = tooltipAlpha
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun PerformanceTooltip(
    section: String,
    value: Float,
    value2: Float? = null, // Support for dual-line tooltips
    avg: Float,
    max: Float,
    offset: Offset,
    displayUnit: String,
    unitFactor: Float,
    alpha: Float
) {
    Surface(
        modifier = Modifier
            .offset { IntOffset(offset.x.toInt() + 24, offset.y.toInt() - 96) }
            .shadow(8.dp, RoundedCornerShape(4.dp))
            .graphicsLayer(alpha = alpha),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            when (section) {
                "Wi-Fi" -> {
                    TooltipRow("Receive", value, displayUnit, unitFactor)
                    if (value2 != null) TooltipRow("Send", value2, displayUnit, unitFactor)
                }
                "Battery" -> {
                    TooltipRow("Level", value, displayUnit, unitFactor)
                }
                "Memory" -> {
                    TooltipRow("In use", value, displayUnit, unitFactor)
                    TooltipRow("Average", avg, displayUnit, unitFactor)
                    TooltipRow("Maximum", max, displayUnit, unitFactor)
                }
                else -> {
                    TooltipRow("Current", value, displayUnit, unitFactor)
                    TooltipRow("Average", avg, displayUnit, unitFactor)
                    TooltipRow("Maximum", max, displayUnit, unitFactor)
                }
            }
        }
    }
}

@Composable
private fun TooltipRow(label: String, value: Float, displayUnit: String, unitFactor: Float) {
    Row(modifier = Modifier.width(125.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        val convertedVal = if (unitFactor > 0) value / unitFactor else value
        val displayValStr = if (displayUnit == "%") "${convertedVal.toInt()}%" 
                          else if (displayUnit == " GB") String.format("%.1f GB", convertedVal)
                          else String.format("%.1f%s", convertedVal, displayUnit)

        Text(
            text = displayValStr,
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Bold, 
            maxLines = 1
        )
    }
}

@Composable
private fun StartupAppsTabContent(context: Context) {
    val pm = context.packageManager
    val bootApps = remember {
        try {
            val intent = Intent(Intent.ACTION_BOOT_COMPLETED); val receivers = pm.queryBroadcastReceivers(intent, 0)
            receivers.mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val label = pm.getApplicationLabel(appInfo).toString(); val pkg = appInfo.packageName; Pair(pkg, label)
            }.distinctBy { it.first }.sortedBy { it.second }
        } catch (e: Exception) { emptyList<Pair<String, String>>() }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Startup apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Card(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            val lazyListState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    items(bootApps) { (pkg, label) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                WindowsStyleScrollbar(state = lazyListState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    uiScale: Float, 
    onScaleChange: (Float) -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    independentInspectors: Boolean,
    onIndependentInspectorsChange: (Boolean) -> Unit,
    enhancedMultiWindow: Boolean,
    onEnhancedMultiWindowChange: (Boolean) -> Unit,
    perfTooltipEnabled: Boolean,
    onPerfTooltipChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            val scrollState = rememberScrollState()
            Column(Modifier.padding(16.dp).verticalScroll(scrollState)) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark Theme", modifier = Modifier.weight(1f)); Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
                }
                Spacer(Modifier.height(24.dp))
                Text("UI Scaling", style = MaterialTheme.typography.titleSmall); Slider(value = uiScale, onValueChange = onScaleChange, valueRange = 0.75f..2.0f)
                Text("Scale: ${String.format("%.2f", uiScale)}x")
                Spacer(Modifier.height(24.dp))
                Text("Experimental", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Independent Properties Windows", style = MaterialTheme.typography.bodyMedium)
                            Text("When enabled, Process Properties windows remain open after Task Manager is closed. This allows using Process Inspector as a standalone monitoring tool.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = independentInspectors, onCheckedChange = onIndependentInspectorsChange)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enhanced Multi-Window Support", style = MaterialTheme.typography.bodyMedium)
                            Text("Allow apps like Chrome to have multiple independent title bars for separate windows. Smart defaults applied for Shell and Chrome.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enhancedMultiWindow, onCheckedChange = onEnhancedMultiWindowChange)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Performance Graph Tooltips", style = MaterialTheme.typography.bodyMedium)
                            Text("Show floating information when hovering over performance graphs in the Performance tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = perfTooltipEnabled, onCheckedChange = onPerfTooltipChange)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Hardware Identification", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp))
                    val mContext = LocalContext.current; var useAutoHw by remember { mutableStateOf(ThemeManager.useAutoHardwareDetection(mContext)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use Automatic Detection", modifier = Modifier.weight(1f)); Switch(checked = useAutoHw, onCheckedChange = { useAutoHw = it; ThemeManager.setUseAutoHardwareDetection(mContext, it) })
                    }
                    if (!useAutoHw) {
                        Spacer(Modifier.height(8.dp))
                        listOf("CPU Name" to "hw_cpu_name", "CPU Codename" to "hw_cpu_codename", "GPU Name" to "hw_gpu_name", "Storage Name" to "hw_storage_name", "Device Name" to "hw_device_name").forEach { (label, key) ->
                            var v by remember { mutableStateOf(ThemeManager.getHardwareOverride(mContext, key)) }
                            OutlinedTextField(value = v, onValueChange = { v = it; ThemeManager.setHardwareOverride(mContext, key, it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textStyle = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { useAutoHw = true; ThemeManager.setUseAutoHardwareDetection(mContext, true) }, modifier = Modifier.padding(top = 8.dp)) { Text("Reset to Auto Detect") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Column {
                        Text("CPU Monitoring Engine", style = MaterialTheme.typography.bodyMedium)
                        Text("Android (Recommended): Framework APIs, stable.\nLinux /proc (Experimental): Kernel jiffies, desktop-like.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            val androidAvailable = remember { AndroidCpuProvider().isAvailable() }; val linuxAvailable = remember { LinuxCpuProvider().isAvailable() }
                            Column {
                                Row {
                                    FilterChip(selected = SystemMonitorEngine.currentProvider is AndroidCpuProvider, onClick = { SystemMonitorEngine.switchProvider("Android") }, label = { Text("Android") })
                                    Spacer(Modifier.width(8.dp))
                                    FilterChip(selected = SystemMonitorEngine.currentProvider is LinuxCpuProvider, onClick = { SystemMonitorEngine.switchProvider("Linux") }, label = { Text("Linux /proc") }, enabled = linuxAvailable)
                                }
                                Text(text = if (SystemMonitorEngine.currentProvider is LinuxCpuProvider) { if (linuxAvailable) "Status: Linux backend active" else "Status: Linux backend unavailable (falling back)" } else "Status: Android backend active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
