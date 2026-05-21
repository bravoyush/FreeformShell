package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.SharedPreferences
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

import android.hardware.display.DisplayManager
import android.view.Display
import android.widget.Toast

class MainActivity : ComponentActivity() {

    private val onPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Global Crash Interceptor to catch Compose hover & layout bugs on Android 12
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                if (throwable.message?.contains("ACTION_HOVER_EXIT") == true ||
                    throwable.stackTrace.any { it.className.contains("AndroidComposeView") }) {
                    Log.e("MainActivity", "Caught & intercepted Compose hover crash on thread ${thread.name}", throwable)
                } else {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }
            
            // Intercept Main Looper exceptions
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                while (true) {
                    try {
                        android.os.Looper.loop()
                    } catch (e: Throwable) {
                        if (e.message?.contains("ACTION_HOVER_EXIT") == true ||
                            e.stackTrace.any { it.className.contains("AndroidComposeView") }) {
                            Log.e("MainActivity", "Intercepted Main Looper Compose exception", e)
                        } else {
                            throw e
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to set crash interceptors", e)
        }

        Shizuku.addRequestPermissionResultListener(onPermissionResultListener)
        
        // Respect the App Launch Display setting
        val launchMode = ThemeManager.getAppLaunchDisplay(this)
        val displayId = display?.displayId ?: 0
        
        if (launchMode != 2) { // Not Auto
            val targetDisplayId = if (launchMode == 0) 0 else {
                // Find secondary display ID (usually 2 or higher on Sony/Multi-display)
                val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                dm.displays.firstOrNull { it.displayId != 0 }?.displayId ?: 0
            }
            
            if (displayId != targetDisplayId) {
                val isFreeform = try {
                    val config = resources.configuration
                    val method = config.javaClass.getDeclaredMethod("getWindowConfiguration")
                    val windowConfig = method.invoke(config)
                    val getWindowingMode = windowConfig.javaClass.getDeclaredMethod("getWindowingMode")
                    (getWindowingMode.invoke(windowConfig) as Int) == 5
                } catch (e: Exception) { false }

                if (!isFreeform) {
                    val targetIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId)
                    startActivity(targetIntent, options.toBundle())
                    finish()
                    return
                }
            }
        }
        
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(ThemeManager.getThemeMode(context)) }
            
            // Listen for theme changes
            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme_mode") {
                        themeMode = ThemeManager.getThemeMode(context)
                    }
                }
                val prefs = context.getSharedPreferences("freeform_theme_prefs", Context.MODE_PRIVATE)
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val isSystemDark = isSystemInDarkTheme()
            val useDarkMode = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> isSystemDark // Auto
            }

            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (useDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                useDarkMode -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRequestOverlayPermission = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        },
                        onRequestShizukuPermission = {
                            if (Shizuku.pingBinder()) {
                                Shizuku.requestPermission(0)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(onPermissionResultListener)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRequestOverlayPermission: () -> Unit,
    onRequestShizukuPermission: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isShizukuAvailable by remember { mutableStateOf(Shizuku.pingBinder()) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasShizukuPermission by remember { 
        mutableStateOf(isShizukuAvailable && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }
    
    DisposableEffect(Unit) {
        val callback = object : ShizukuLifecycleManager.ConnectionCallback {
            override fun onConnected() {
                isShizukuAvailable = true
                hasShizukuPermission = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            override fun onDisconnected() {
                isShizukuAvailable = false
                hasShizukuPermission = false
            }
        }
        ShizukuLifecycleManager.register(callback)
        onDispose {
            ShizukuLifecycleManager.unregister(callback)
        }
    }
    
    var tasks by remember { mutableStateOf<List<AppTask>>(emptyList()) }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var filteredApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var launchQueue by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    
    var availableDisplays by remember { mutableStateOf<List<DisplayInfo>>(emptyList()) }
    var selectedDisplayId by remember { mutableLongStateOf(0L) }.let { 
        // Use Long to match Display ID if needed, but Int is fine. Let's stick to Int.
        remember { mutableStateOf(0) }
    }
    
    var selectedAppToLaunch by remember { mutableStateOf<AppInfo?>(null) }
    val dm = context.resources.displayMetrics
    
    // Size logic moved to dynamic calculation
    var launchWidth by remember { mutableStateOf("") }
    var launchHeight by remember { mutableStateOf("") }

    var selectedTabIndex by remember { mutableStateOf(0) }

    var editingGroup by remember { mutableStateOf<WorkspaceGroup?>(null) }
    var isEditingFavorite by remember { mutableStateOf(false) }
    var refreshWorkspacesKey by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isShizukuAvailable = Shizuku.pingBinder()
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasShizukuPermission = isShizukuAvailable && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                scope.launch(Dispatchers.IO) {
                    val displayDump = ShellExecutor.exec("dumpsys display")
                    val newList = mutableListOf<DisplayInfo>()
                    val displayRegex = "Display\\s+#(\\d+):\\s+name=\"([^\"]+)\",.*?real\\s+(\\d+)x(\\d+),.*?density\\s+(\\d+)".toRegex()
                    displayRegex.findAll(displayDump).forEach { match ->
                        newList.add(DisplayInfo(
                            match.groupValues[1].toInt(), 
                            match.groupValues[2], 
                            match.groupValues[3].toInt(), 
                            match.groupValues[4].toInt(),
                            match.groupValues[5].toInt()
                        ))
                    }
                    if (newList.isEmpty()) {
                        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                        newList.addAll(dm.displays.map { d ->
                            val metrics = android.util.DisplayMetrics()
                            d.getRealMetrics(metrics)
                            DisplayInfo(d.displayId, d.name, metrics.widthPixels, metrics.heightPixels)
                        })
                    }
                    withContext(Dispatchers.Main) { availableDisplays = newList }
                }

                if (hasOverlayPermission) {
                    val intent = Intent(context, FreeformOverlayService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }

                scope.launch(Dispatchers.IO) {
                    val newTasks = TaskManager.getRecentTasks()
                    withContext(Dispatchers.Main) { tasks = newTasks }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    AppInfo(app.loadLabel(pm).toString(), app.packageName, null, isSystem)
                }
                .sortedBy { it.label }
            withContext(Dispatchers.Main) { 
                allApps = installedApps 
                filteredApps = installedApps
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, icon = { Icon(Icons.Default.Window, null) }, label = { Text("Windows") })
                NavigationBarItem(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, icon = { Icon(Icons.Default.Palette, null) }, label = { Text("Customization") })
                NavigationBarItem(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, icon = { Icon(Icons.Default.Security, null) }, label = { Text("Safe Area") })
                NavigationBarItem(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }, icon = { Icon(Icons.Default.Block, null) }, label = { Text("Blacklist") })
                NavigationBarItem(selected = selectedTabIndex == 4, onClick = { selectedTabIndex = 4 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                NavigationBarItem(selected = selectedTabIndex == 5, onClick = { selectedTabIndex = 5 }, icon = { Icon(Icons.Default.Build, null) }, label = { Text("Compat") })
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(onClick = { showSettingsDialog = true }, modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(Icons.Default.Info, "Info")
                    }
                    FloatingActionButton(onClick = { showLogDialog = true }) {
                        Icon(Icons.Default.List, "Logs")
                    }
                }
            }
        }
    ) { padding ->
        when (selectedTabIndex) {
            0 -> DashboardScreen(
                padding = padding,
                availableDisplays = availableDisplays,
                selectedDisplayId = selectedDisplayId,
                onSelectDisplay = { selectedDisplayId = it },
                tasks = tasks,
                isLoading = isLoading,
                isShizukuAvailable = isShizukuAvailable,
                hasOverlayPermission = hasOverlayPermission,
                launchQueue = launchQueue,
                onLaunchApp = { showAppPicker = true },
                onRefresh = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        val newTasks = TaskManager.getRecentTasks()
                        withContext(Dispatchers.Main) { 
                            tasks = newTasks 
                            isLoading = false
                        }
                    }
                },
                onClearQueue = { launchQueue = emptyList() },
                onLaunchQueue = {
                    val queue = launchQueue
                    launchQueue = emptyList()
                    val displayId = selectedDisplayId
                    val targetDisplay = availableDisplays.find { it.id == displayId }
                    val screenW = targetDisplay?.width ?: 1080
                    val screenH = targetDisplay?.height ?: 2400
                    
                    // Requirement: 50% of screen size
                    val w = screenW / 2
                    val h = screenH / 2
                    
                    scope.launch(Dispatchers.IO) {
                        ShellExecutor.executeCommand("cmd activity set-resizable 1")
                        queue.forEachIndexed { index, app ->
                            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            val component = intent?.component?.flattenToShortString() ?: return@forEachIndexed
                            
                            val left = 100 + (index * 80)
                            val top = 200 + (index * 80)
                            val right = left + w
                            val bottom = top + h
                            
                            FreeformOverlayService.setIntendedBounds(app.packageName, android.graphics.Rect(left, top, right, bottom), displayId)
                            val launchCmd = "am start-activity --display $displayId --windowingMode 5 --activity-brought-to-front -n $component"
                            ShellExecutor.executeCommand(launchCmd)
                            delay(1000)
                        }
                    }
                },
                editingGroup = editingGroup,
                onEditGroup = { group, isFav ->
                    editingGroup = group
                    isEditingFavorite = isFav
                },
                refreshWorkspacesKey = refreshWorkspacesKey,
                onRefreshWorkspaces = { refreshWorkspacesKey++ }
            )
            1 -> CustomizationScreen(padding)
            2 -> SafeAreaScreen(padding, availableDisplays)
            3 -> BlacklistScreen(padding)
            4 -> AppSettingsScreen(padding, availableDisplays)
            5 -> CompatibilityScreen(padding)
        }

        if (showLogDialog) {
            AlertDialog(onDismissRequest = { showLogDialog = false }, title = { Text("System Console Logs") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(Color.Black).padding(8.dp)) {
                        Text(text = ShellExecutor.lastFullLog.ifEmpty { "No logs captured yet." }, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                },
                confirmButton = { TextButton(onClick = { showLogDialog = false }) { Text("Close") } }
            )
        }

        if (showAppPicker) {
            AlertDialog(onDismissRequest = { showAppPicker = false }, title = { Text("Launch Application") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = searchQuery, 
                            onValueChange = { 
                                searchQuery = it
                                filteredApps = allApps.filter { app -> app.label.contains(it, ignoreCase = true) || app.packageName.contains(it, ignoreCase = true) }
                            }, 
                            label = { Text("Search apps") }, 
                            modifier = Modifier.fillMaxWidth(), 
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val isInQueue = launchQueue.any { it.packageName == app.packageName }
                                ListItem(
                                    leadingContent = { AppIcon(app.packageName) }, 
                                    headlineContent = { Text(app.label) }, 
                                    supportingContent = { Text(app.packageName) }, 
                                    trailingContent = {
                                        IconButton(onClick = {
                                            if (isInQueue) {
                                                launchQueue = launchQueue.filter { it.packageName != app.packageName }
                                            } else {
                                                launchQueue = launchQueue + app
                                            }
                                        }) {
                                            Icon(
                                                if (isInQueue) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline, 
                                                null,
                                                tint = if (isInQueue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    modifier = Modifier.clickable { 
                                        selectedAppToLaunch = app
                                        showAppPicker = false
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text("Cancel") } }
            )
        }

        if (selectedAppToLaunch != null) {
            AlertDialog(onDismissRequest = { selectedAppToLaunch = null }, title = { Text("Launch ${selectedAppToLaunch!!.label}") },
                text = {
                    Column {
                        val displayId = selectedDisplayId
                        val targetDisplay = availableDisplays.find { it.id == displayId }
                        val screenW = targetDisplay?.width ?: 1080
                        val screenH = targetDisplay?.height ?: 2400
                        
                        // Default to 50%
                        if (launchWidth.isEmpty()) launchWidth = (screenW / 2).toString()
                        if (launchHeight.isEmpty()) launchHeight = (screenH / 2).toString()

                        Text("Target Display: ${targetDisplay?.name ?: "Unknown"}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Window Size (px):")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = launchWidth, onValueChange = { launchWidth = it }, label = { Text("Width") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = launchHeight, onValueChange = { launchHeight = it }, label = { Text("Height") }, modifier = Modifier.weight(1f))
                        }
                        Text("Tip: Use 'Tablet UI' in the window snap menu to trigger desktop layouts safely.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val app = selectedAppToLaunch!!
                        val w = launchWidth.toIntOrNull() ?: 800
                        val h = launchHeight.toIntOrNull() ?: 1200
                        val displayId = selectedDisplayId
                        selectedAppToLaunch = null
                        launchWidth = "" // Reset for next launch
                        launchHeight = ""
                        
                        scope.launch(Dispatchers.IO) {
                            val pm = context.packageManager
                            val intent = pm.getLaunchIntentForPackage(app.packageName)
                            val component = intent?.component?.flattenToShortString() ?: return@launch
                            
                            val targetDisplay = availableDisplays.find { it.id == displayId }
                            val screenW = targetDisplay?.width ?: 1080
                            val screenH = targetDisplay?.height ?: 2400
                            val left = (screenW - w) / 2
                            val top = (screenH - h) / 2
                            val right = left + w
                            val bottom = top + h
                            
                            FreeformOverlayService.setIntendedBounds(app.packageName, android.graphics.Rect(left, top, right, bottom), displayId)
                            ShellExecutor.executeCommand("cmd activity set-resizable 1")
                            
                            val launchCmd = "am start-activity --display $displayId --windowingMode 5 --activity-brought-to-front -n $component"
                            ShellExecutor.executeCommand(launchCmd)
                            
                            delay(1000)
                            val match = TaskManager.getRecentTasks().find { it.packageName.contains(app.packageName) }
                            if (match != null) ShellExecutor.resizeTask(match.taskId, left, top, right, bottom)
                        }
                    }) { Text("Launch") }
                }
            )
        }

        if (editingGroup != null) {
            val group = editingGroup!!
            var appsList by remember(editingGroup) { mutableStateOf(group.apps) }
            
            AlertDialog(
                onDismissRequest = { editingGroup = null },
                title = { Text("Edit Workspace Layout", style = MaterialTheme.typography.titleLarge) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(appsList.size) { index ->
                            val app = appsList[index]
                            var leftStr by remember(app) { mutableStateOf(app.bounds.left.toString()) }
                            var topStr by remember(app) { mutableStateOf(app.bounds.top.toString()) }
                            var rightStr by remember(app) { mutableStateOf(app.bounds.right.toString()) }
                            var bottomStr by remember(app) { mutableStateOf(app.bounds.bottom.toString()) }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AppIcon(app.packageName, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(app.packageName.substringAfterLast("."), fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                                        }
                                        IconButton(onClick = {
                                            appsList = appsList.filterIndexed { i, _ -> i != index }
                                        }) {
                                            Icon(Icons.Default.Delete, "Remove App", tint = Color.Red)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("Window Position Coordinates:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedTextField(value = leftStr, onValueChange = { leftStr = it }, label = { Text("Left") }, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = topStr, onValueChange = { topStr = it }, label = { Text("Top") }, modifier = Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedTextField(value = rightStr, onValueChange = { rightStr = it }, label = { Text("Right") }, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = bottomStr, onValueChange = { bottomStr = it }, label = { Text("Bottom") }, modifier = Modifier.weight(1f))
                                    }
                                    
                                    LaunchedEffect(leftStr, topStr, rightStr, bottomStr) {
                                        val l = leftStr.toIntOrNull() ?: app.bounds.left
                                        val t = topStr.toIntOrNull() ?: app.bounds.top
                                        val r = rightStr.toIntOrNull() ?: app.bounds.right
                                        val b = bottomStr.toIntOrNull() ?: app.bounds.bottom
                                        appsList = appsList.mapIndexed { i, a ->
                                            if (i == index) WorkspaceApp(a.packageName, a.component, android.graphics.Rect(l, t, r, b)) else a
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val updatedGroup = WorkspaceGroup(appsList, group.displayId, group.timestamp)
                        if (isEditingFavorite) {
                            WorkspaceManager.setFavorite(context, updatedGroup)
                        } else {
                            val history = WorkspaceManager.getHistory(context).toMutableList()
                            val idx = history.indexOfFirst { it.timestamp == group.timestamp }
                            if (idx != -1) {
                                history[idx] = updatedGroup
                                WorkspaceManager.saveHistory(context, history)
                            }
                        }
                        editingGroup = null
                        refreshWorkspacesKey++
                        Toast.makeText(context, "Workspace updated successfully!", Toast.LENGTH_SHORT).show()
                    }) { Text("Save Changes") }
                },
                dismissButton = {
                    TextButton(onClick = { editingGroup = null }) { Text("Cancel") }
                }
            )
        }

        if (showSettingsDialog) {
            AlertDialog(onDismissRequest = { showSettingsDialog = false }, title = { Text("Permissions & Settings") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text("Shizuku Service") }, trailingContent = { StatusChip(label = "Bind", color = if (isShizukuAvailable) Color.Green else Color.Red) })
                        ListItem(headlineContent = { Text("Shizuku Permission") }, trailingContent = { StatusChip(label = "Granted", color = if (hasShizukuPermission) Color.Green else Color.Red) }, modifier = Modifier.clickable { if (!hasShizukuPermission && isShizukuAvailable) onRequestShizukuPermission() })
                        ListItem(headlineContent = { Text("Display Overlays") }, trailingContent = { StatusChip(label = "Granted", color = if (hasOverlayPermission) Color.Green else Color.Red) }, modifier = Modifier.clickable { if (!hasOverlayPermission) onRequestOverlayPermission() })
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Close") } }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    padding: PaddingValues,
    availableDisplays: List<DisplayInfo>,
    selectedDisplayId: Int,
    onSelectDisplay: (Int) -> Unit,
    tasks: List<AppTask>,
    isLoading: Boolean,
    isShizukuAvailable: Boolean,
    hasOverlayPermission: Boolean,
    launchQueue: List<AppInfo>,
    onLaunchApp: () -> Unit,
    onRefresh: () -> Unit,
    onClearQueue: () -> Unit,
    onLaunchQueue: () -> Unit,
    editingGroup: WorkspaceGroup?,
    onEditGroup: (WorkspaceGroup, Boolean) -> Unit,
    refreshWorkspacesKey: Int,
    onRefreshWorkspaces: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val enabledDisplays = availableDisplays.filter { display ->
        ThemeManager.isDisplayShellEnabled(context, display.id)
    }.ifEmpty { availableDisplays }
    
    LaunchedEffect(enabledDisplays) {
        if (enabledDisplays.none { it.id == selectedDisplayId }) {
            val fallbackId = enabledDisplays.firstOrNull()?.id ?: 0
            onSelectDisplay(fallbackId)
        }
    }
    
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Freeform Console", 
            style = MaterialTheme.typography.displaySmall, 
            fontWeight = FontWeight.ExtraBold, 
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Desktop experience for mobile", 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(label = "Shizuku", color = if (isShizukuAvailable) Color(0xFF4CAF50) else Color(0xFFF44336))
            StatusChip(label = "Overlay", color = if (hasOverlayPermission) Color(0xFF4CAF50) else Color(0xFFF44336))
        }
 
        Spacer(modifier = Modifier.height(16.dp))
 
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Target Display", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                enabledDisplays.forEach { display ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDisplay(display.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedDisplayId == display.id, onClick = { onSelectDisplay(display.id) })
                        Spacer(Modifier.width(8.dp))
                        DisplayShapeIcon(display)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(display.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("${display.width}x${display.height}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Text("Launch Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (launchQueue.isNotEmpty()) {
                    Text("Launch Queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Queue Contents:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            launchQueue.forEach { app ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    AppIcon(app.packageName, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            
                            Divider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            Text("Target Display:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            enabledDisplays.forEach { display ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onSelectDisplay(display.id) }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedDisplayId == display.id, onClick = { onSelectDisplay(display.id) })
                                    Text(display.name, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${display.width}x${display.height}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
 
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onLaunchQueue, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.RocketLaunch, null)
                                    Spacer(Modifier.width(8.dp))
                                    val targetName = enabledDisplays.find { it.id == selectedDisplayId }?.name ?: "Display"
                                    Text("Launch All on $targetName")
                                }
                                OutlinedButton(onClick = onClearQueue) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                
                Button(
                    onClick = onLaunchApp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Launch Application")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active Windows", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh List")
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (tasks.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("No active freeform windows", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                tasks.forEach { task ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(task.packageName.substringAfterLast("."), fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Task ID: ${task.taskId}", style = MaterialTheme.typography.labelSmall) },
                            leadingContent = { AppIcon(task.packageName, modifier = Modifier.size(40.dp)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isForceClose = FreeformOverlayService.shouldForceClose(context, task.packageName)
                                    val isBlacklisted = FreeformOverlayService.isBlacklisted(context, task.packageName)

                                    IconButton(onClick = { 
                                        FreeformOverlayService.toggleForceClose(context, task.packageName)
                                        onRefresh()
                                    }) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Toggle Force Close",
                                            tint = if (isForceClose) Color.Red else Color.Gray
                                        )
                                    }

                                    TextButton(onClick = { 
                                        if (isForceClose) {
                                            ShellExecutor.forceStopApp(task.packageName, task.taskId)
                                        } else {
                                            ShellExecutor.removeTask(task.taskId)
                                        }
                                        onRefresh() // Auto refresh after close
                                    }) { 
                                        Text(
                                            if (isForceClose) "Force Stop" else "Close", 
                                            color = MaterialTheme.colorScheme.error, 
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    }

                                    IconButton(onClick = { 
                                        FreeformOverlayService.toggleBlacklist(context, task.packageName)
                                        onRefresh()
                                    }) {
                                        Icon(if (isBlacklisted) Icons.Default.Block else Icons.Default.AddCircleOutline, null, 
                                            tint = if (isBlacklisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        
        if (tasks.any { it.isFreeform }) {
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val state = TaskManager.getCombinedTaskState()
                        val apps = state.tasks.filter { it.isFreeform }.mapNotNull { task ->
                            val bounds = state.boundsMap[task.taskId]?.bounds ?: return@mapNotNull null
                            WorkspaceApp(task.packageName, task.activityName, bounds)
                        }
                        if (apps.isNotEmpty()) {
                            val activeDisplay = selectedDisplayId
                            WorkspaceManager.setFavorite(context, WorkspaceGroup(apps, activeDisplay))
                            onRefreshWorkspaces()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Layout saved to Favorites!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Star, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Layout to Favorites")
            }
        }

        // Workspace Manager Section
        val favoriteWorkspace = WorkspaceManager.getFavorite(context)
        val historyWorkspaces = WorkspaceManager.getHistory(context)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Workspace Manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Save and organize layouts for quick snapping", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (favoriteWorkspace == null && historyWorkspaces.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No Saved Workspaces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Launch apps in freeform mode and arrange them. Then click 'Save Layout to Favorites' to store them here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Favorite Workspace Section
            if (favoriteWorkspace != null) {
                val group = favoriteWorkspace
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                    shape = MaterialTheme.shapes.extraLarge,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, "Favorite", tint = Color(0xFFFFC107))
                            Spacer(Modifier.width(8.dp))
                            Text("FAVORITE WORKSPACE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            group.apps.forEach { app ->
                                AppIcon(app.packageName, modifier = Modifier.size(32.dp))
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onEditGroup(group, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit")
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    WorkspaceManager.removeFavorite(context)
                                    onRefreshWorkspaces()
                                    Toast.makeText(context, "Favorite removed", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remove")
                            }
                        }
                    }
                }
            }
            
            // History Workspaces Section
            if (historyWorkspaces.isNotEmpty()) {
                Text("Workspace History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                historyWorkspaces.forEachIndexed { idx, group ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val label = if (group.displayId == 0) "Phone Workspace" else "External Display ${group.displayId}"
                                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.weight(1f))
                                val diffSec = (System.currentTimeMillis() - group.timestamp) / 1000
                                val timeStr = when {
                                    diffSec < 60 -> "Just Now"
                                    diffSec < 3600 -> "${diffSec / 60}m ago"
                                    else -> "${diffSec / 3600}h ago"
                                }
                                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.apps.forEach { app ->
                                    AppIcon(app.packageName, modifier = Modifier.size(28.dp))
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        WorkspaceManager.setFavorite(context, group)
                                        onRefreshWorkspaces()
                                        Toast.makeText(context, "Set as Favorite Workspace!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.StarBorder, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Make Favorite")
                                }
                                
                                Button(
                                    onClick = { onEditGroup(group, false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        val updatedHistory = historyWorkspaces.toMutableList()
                                        updatedHistory.removeAt(idx)
                                        WorkspaceManager.saveHistory(context, updatedHistory)
                                        onRefreshWorkspaces()
                                        Toast.makeText(context, "Workspace deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

data class AppInfo(val label: String, val packageName: String, val icon: ImageBitmap? = null, val isSystem: Boolean = false)
data class DisplayInfo(val id: Int, val name: String, val width: Int, val height: Int, val dpi: Int = 420, val isRounded: Boolean = true)

@Composable
fun StatusChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(
                    width = 120.coerceAtLeast(1), 
                    height = 120.coerceAtLeast(1)
                ).asImageBitmap()
                withContext(Dispatchers.Main) { icon = bitmap }
            } catch (e: Exception) {
                Log.e("AppIcon", "Failed to load icon for $packageName: ${e.message}")
            }
        }
    }
    
    if (icon != null) {
        Image(bitmap = icon!!, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape))
    }
}

@Composable
fun DisplayShapeIcon(display: DisplayInfo) {
    val maxSize = 32.dp
    val ratio = if (display.height > 0) display.width.toFloat() / display.height.toFloat() else 1f
    
    val (w, h) = if (ratio > 1f) {
        maxSize to (maxSize / ratio)
    } else {
        (maxSize * ratio) to maxSize
    }
    
    Box(
        modifier = Modifier
            .size(maxSize)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(w, h)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .border(
                    1.5.dp, 
                    MaterialTheme.colorScheme.primary, 
                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(ThemeManager.getThemeMode(context)) }
    var roundness by remember { mutableStateOf(ThemeManager.getRoundness(context)) }
    var opacity by remember { mutableStateOf(ThemeManager.getOpacity(context).toFloat()) }
    var borderWidth by remember { mutableStateOf(ThemeManager.getBorderWidth(context)) }
    var titleBarOpacity by remember { mutableStateOf(ThemeManager.getTitleBarOpacity(context).toFloat()) }
    
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)
        
        val isSystemDark = isSystemInDarkTheme()
        val isPreviewDark = when(mode) { 1 -> false; 2 -> true; else -> isSystemDark }
        WindowPreview(roundness, opacity, borderWidth, titleBarOpacity, isPreviewDark)
        
        Spacer(Modifier.height(16.dp))
        
        Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Auto", "Light", "Dark").forEachIndexed { index, label ->
                FilterChip(selected = mode == index, onClick = { mode = index; ThemeManager.setThemeMode(context, index) }, label = { Text(label) })
            }
        }
        
        Text("Window Roundness: ${roundness.toInt()}dp", style = MaterialTheme.typography.titleMedium)
        Slider(value = roundness, onValueChange = { roundness = it; ThemeManager.setRoundness(context, it) }, valueRange = 0f..40f)
        
        Text("Window Opacity: ${opacity.toInt()}/255", style = MaterialTheme.typography.titleMedium)
        Slider(value = opacity, onValueChange = { opacity = it; ThemeManager.setOpacity(context, it.toInt()) }, valueRange = 50f..255f)
        
        Text("Normal Title Bar Opacity: ${titleBarOpacity.toInt()}%", style = MaterialTheme.typography.titleMedium)
        Slider(value = titleBarOpacity, onValueChange = { titleBarOpacity = it; ThemeManager.setTitleBarOpacity(context, it.toInt()) }, valueRange = 30f..100f)
        
        Text("Border Width: ${borderWidth.toInt()}dp", style = MaterialTheme.typography.titleMedium)
        Slider(value = borderWidth, onValueChange = { borderWidth = it; ThemeManager.setBorderWidth(context, it) }, valueRange = 0f..12f)
        
        Spacer(Modifier.height(16.dp))
        
        var isDragTintEnabled by remember { mutableStateOf(ThemeManager.isDragTintEnabled(context)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Drag & Resize Tint Overlay", style = MaterialTheme.typography.titleMedium)
                Text("Show a semi-transparent colored tint overlay covering the window during gestures.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(checked = isDragTintEnabled, onCheckedChange = { 
                isDragTintEnabled = it
                ThemeManager.setDragTintEnabled(context, it)
            })
        }

        Spacer(Modifier.height(24.dp))
        
        // Pill Auto-Shrink & Scale Toggle
        var isPillAutoShrinkGlobalEnabled by remember { 
            mutableStateOf(prefs.getBoolean("pill_auto_shrink_global", true)) 
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pill Auto-Shrink & Scale", style = MaterialTheme.typography.titleMedium)
                Text("Automatically scale down the window title pill when inactive to save space.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(checked = isPillAutoShrinkGlobalEnabled, onCheckedChange = { 
                isPillAutoShrinkGlobalEnabled = it
                prefs.edit().putBoolean("pill_auto_shrink_global", it).apply()
            })
        }

        if (isPillAutoShrinkGlobalEnabled) {
            Spacer(Modifier.height(12.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    displayManager.displays.forEach { d ->
                        Text("Configure: ${d.name} (ID: ${d.displayId})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        
                        var shrinkEnabled by remember(d.displayId) { 
                            mutableStateOf(ThemeManager.getPillAutoShrink(context, d.displayId)) 
                        }
                        var scalePercent by remember(d.displayId) { 
                            mutableStateOf(ThemeManager.getPillInactiveScale(context, d.displayId).toFloat()) 
                        }
                        
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Enable Auto-Shrink", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = shrinkEnabled, onCheckedChange = {
                                shrinkEnabled = it
                                ThemeManager.setPillAutoShrink(context, d.displayId, it)
                            })
                        }
                        
                        if (shrinkEnabled) {
                            Spacer(Modifier.height(4.dp))
                            Text("Inactive Scale: ${scalePercent.toInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = scalePercent,
                                onValueChange = { scalePercent = it },
                                onValueChangeFinished = {
                                    ThemeManager.setPillInactiveScale(context, d.displayId, scalePercent.toInt())
                                },
                                valueRange = 30f..90f,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Shrink Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Choose styling for inactive/shrunk pill layout.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    
                    var shrinkStyle by remember { mutableStateOf(ThemeManager.getPillShrinkStyle(context)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Scale Transform", "Handle/Bar Resizing").forEachIndexed { index, label ->
                            FilterChip(
                                selected = shrinkStyle == index,
                                onClick = { 
                                    shrinkStyle = index
                                    ThemeManager.setPillShrinkStyle(context, index)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    PillPreviewCard(shrinkStyle)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        
        Text("Experimental", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                var showShadows by remember { mutableStateOf(ThemeManager.showShadows(context)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Window Shadows", style = MaterialTheme.typography.bodyLarge)
                        Text("Add depth with soft shadows (Performance impact).", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = showShadows, onCheckedChange = { 
                        showShadows = it
                        ThemeManager.setShowShadows(context, it)
                    })
                }
            }
        }
    }
}

@Composable
fun WindowPreview(roundness: Float, opacity: Float, borderWidth: Float, titleBarOpacity: Float, isDark: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Mock Window
            Column(
                modifier = Modifier
                    .size(200.dp, 120.dp)
                    .background(
                        color = if (isDark) Color(0xFF2D2D30).copy(alpha = opacity / 255f) 
                                else Color(0xFFF3F6FA).copy(alpha = opacity / 255f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = roundness.dp, 
                            topEnd = roundness.dp, 
                            bottomStart = roundness.dp, 
                            bottomEnd = roundness.dp
                        )
                    )
                    .border(
                        width = borderWidth.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = roundness.dp, 
                            topEnd = roundness.dp, 
                            bottomStart = roundness.dp, 
                            bottomEnd = roundness.dp
                        )
                    )
            ) {
                // Title Bar Mock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = titleBarOpacity / 100f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = roundness.dp, 
                                topEnd = roundness.dp
                            )
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(Color.White.copy(0.5f), androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.width(40.dp).height(4.dp).background(Color.White.copy(0.3f)))
                    }
                }
            }
            
            Text(
                "PREVIEW", 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            )
        }
    }
}

@Composable
fun SafeAreaScreen(padding: PaddingValues, displays: List<DisplayInfo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIdx by remember { mutableStateOf(0) }
    
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Safe Area", style = MaterialTheme.typography.headlineSmall)
        
        if (displays.size > 1) {
            Text("Select Display", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            ScrollableTabRow(
                selectedTabIndex = selectedIdx,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                displays.forEachIndexed { index, display ->
                    Tab(
                        selected = selectedIdx == index,
                        onClick = { selectedIdx = index },
                        text = { Text(display.name) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (displays.isEmpty()) {
            Text("No displays detected.")
        } else {
            val display = displays[selectedIdx]
            var dockPos by remember(selectedIdx) { mutableStateOf(ThemeManager.getDockPosition(context, display.id)) }
            var dockSize by remember(selectedIdx) { mutableStateOf(ThemeManager.getDockSize(context, display.id).toFloat()) }

            Text("Configuring: ${display.name} (ID: ${display.id})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            
            Text("Dock Position", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("None", "Top", "Bottom", "Left", "Right").forEachIndexed { index, label ->
                    FilterChip(selected = dockPos == index, onClick = { 
                        dockPos = index 
                        ThemeManager.setDockPosition(context, display.id, index)
                        // Show guide briefly
                        FreeformOverlayService.showDockGuide(display.id, index, dockSize.toInt())
                        // Hide after 1 sec
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            FreeformOverlayService.hideDockGuide()
                        }, 1500)
                    }, label = { Text(label) })
                }
            }
            
            if (dockPos > 0) {
                Text("Dock Size: ${dockSize.toInt()}px", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Slider(
                    value = dockSize, 
                    onValueChange = { 
                        dockSize = it
                        ThemeManager.setDockSize(context, display.id, it.toInt())
                        FreeformOverlayService.showDockGuide(display.id, dockPos, it.toInt())
                    },
                    onValueChangeFinished = {
                        FreeformOverlayService.hideDockGuide()
                    },
                    valueRange = 0f..500f
                )
                Text("Tip: Adjust slider to see the visual dock guide on the target display.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            Divider(Modifier.padding(vertical = 16.dp))
            
            Text("Display Density (DPI)", style = MaterialTheme.typography.titleMedium)
            var currentDensity by remember(selectedIdx) { 
                mutableStateOf(ThemeManager.getDensity(context, display.id, 420).toFloat()) 
            }
            
            Slider(
                value = currentDensity,
                onValueChange = { currentDensity = it },
                onValueChangeFinished = {
                    val densityVal = currentDensity.toInt()
                    ThemeManager.setDensity(context, display.id, densityVal)
                    ShellExecutor.executeCommand("wm density $densityVal -d ${display.id}")
                },
                valueRange = 160f..600f
            )
            Text("Current: ${currentDensity.toInt()} DPI", style = MaterialTheme.typography.bodyMedium)
            Text("Lower DPI = More content fits (Desktop mode).\nHigher DPI = Larger text and buttons.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            Button(
                onClick = { 
                    ShellExecutor.executeCommand("wm density reset -d ${display.id}")
                    // Refresh current density from shell
                    scope.launch(Dispatchers.IO) {
                        val out = ShellExecutor.exec("wm density -d ${display.id}")
                        val match = "Physical density: (\\d+)".toRegex().find(out)
                        val phys = match?.groupValues?.get(1)?.toIntOrNull() ?: 420
                        withContext(Dispatchers.Main) {
                            currentDensity = phys.toFloat()
                            ThemeManager.setDensity(context, display.id, phys)
                        }
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("Reset to Default")
            }
        }
    }
}

@Composable
fun AppSettingsScreen(padding: PaddingValues, displays: List<DisplayInfo>) {
    val context = LocalContext.current
    var launchMode by remember { mutableStateOf(ThemeManager.getAppLaunchDisplay(context)) }
    
    val prefs = context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE)
    var isBubbleModeEnabled by remember { mutableStateOf(prefs.getBoolean("bubble_mode", false)) }
    var isTabletModeEnabled by remember { mutableStateOf(ThemeManager.useTabletMode(context)) }
    
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("App Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Text("Active Shell Displays", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select which screens should have active Freeform Shell controls. Disabling a screen completely turns off overlays and title bars on that display, hiding it from target display picker list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                val activeDisplaysForShell = if (displays.isEmpty()) {
                    listOf(DisplayInfo(0, "Primary Display", 1080, 2400))
                } else {
                    displays
                }
                
                activeDisplaysForShell.forEachIndexed { idx, display ->
                    var isEnabled by remember(display.id) {
                        mutableStateOf(ThemeManager.isDisplayShellEnabled(context, display.id))
                    }
                    if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = display.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Display ID: ${display.id} • ${display.width}x${display.height}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                ThemeManager.setDisplayShellEnabled(context, display.id, it)
                                val intent = Intent(context, FreeformOverlayService::class.java).apply {
                                    putExtra("force_relayer", true)
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            var themeMode by remember { mutableStateOf(ThemeManager.getThemeMode(context)) }
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "Light", "Dark").forEachIndexed { index, label ->
                        FilterChip(
                            selected = themeMode == index, 
                            onClick = { 
                                themeMode = index
                                ThemeManager.setThemeMode(context, index) 
                            }, 
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("System Behavior", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Launch Screen Preference", style = MaterialTheme.typography.titleMedium)
                listOf("Phone Screen", "Secondary Screen", "Automatic").forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().clickable { 
                        launchMode = index
                        ThemeManager.setAppLaunchDisplay(context, index)
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = launchMode == index, onClick = { 
                            launchMode = index
                            ThemeManager.setAppLaunchDisplay(context, index)
                        })
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                var workspaceAutoSnap by remember { mutableStateOf(ThemeManager.getWorkspaceAutoSnap(context)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Workspace Auto-Snap", style = MaterialTheme.typography.bodyLarge)
                        Text("Auto-snap apps to saved positions when launching workspace. Turn off to just launch apps and snap manually.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = workspaceAutoSnap, onCheckedChange = { 
                        workspaceAutoSnap = it
                        ThemeManager.setWorkspaceAutoSnap(context, it)
                    })
                }

                if (workspaceAutoSnap) {
                    Spacer(Modifier.height(12.dp))
                    Text("Snapping Sensitivity per Display", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    
                    val activeDisplays = if (displays.isEmpty()) {
                        listOf(DisplayInfo(0, "Primary Display", 1080, 2400))
                    } else {
                        displays
                    }
                    
                    activeDisplays.forEach { display ->
                        var sensitivity by remember(display.id) { 
                            mutableStateOf(ThemeManager.getSnapSensitivity(context, display.id).toFloat()) 
                        }
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                text = "${display.name} (ID: ${display.id})", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Slider(
                                    value = sensitivity,
                                    onValueChange = {
                                        sensitivity = it
                                        ThemeManager.setSnapSensitivity(context, display.id, it.toInt())
                                        FreeformOverlayService.showSensitivityGuide(display.id, it.toInt())
                                    },
                                    onValueChangeFinished = {
                                        FreeformOverlayService.hideSensitivityGuide()
                                    },
                                    valueRange = 20f..150f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${sensitivity.toInt()} dp", 
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Tip: Adjust slider to see the snap trigger zone boundaries overlay on the display.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Experimental (May be unstable)", style = MaterialTheme.typography.titleMedium, color = Color.Red)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Tablet Mode Toggle
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tablet UI Mode", style = MaterialTheme.typography.bodyLarge)
                        Text("Adds 'Tablet UI' snap option to force desktop layouts.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = isTabletModeEnabled, onCheckedChange = { 
                        isTabletModeEnabled = it
                        ThemeManager.setUseTabletMode(context, it)
                    })
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                var usePillForSnapped by remember { mutableStateOf(ThemeManager.usePillForSnapped(context)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pill Mode for Snapped", style = MaterialTheme.typography.bodyLarge)
                        Text("Use floating pill for split/quarter windows to save space.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = usePillForSnapped, onCheckedChange = { 
                        usePillForSnapped = it
                        ThemeManager.setPillForSnapped(context, it)
                    })
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                var realtimeResize by remember { mutableStateOf(ThemeManager.realtimeResize(context)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Real-time Resize", style = MaterialTheme.typography.bodyLarge)
                        Text("Resize apps continuously as you drag (requires powerful device). Disable for smooth lag-free bounds preview.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = realtimeResize, onCheckedChange = { 
                        realtimeResize = it
                        ThemeManager.setRealtimeResize(context, it)
                    })
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                var instantResizeNoAnim by remember { mutableStateOf(ThemeManager.instantResizeNoAnim(context)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Instant Shell Resizing", style = MaterialTheme.typography.bodyLarge)
                        Text("Completely disables OS window resize transition animations for 100% instant and jitter-free workspace updates.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = instantResizeNoAnim, onCheckedChange = { 
                        instantResizeNoAnim = it
                        ThemeManager.setInstantResizeNoAnim(context, it)
                    })
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Bubble Mode Toggle
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bubble Minimize", style = MaterialTheme.typography.bodyLarge)
                        Text("Currently non-functional (Under development).", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = isBubbleModeEnabled, onCheckedChange = { 
                        isBubbleModeEnabled = it
                        prefs.edit().putBoolean("bubble_mode", it).apply()
                    })
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Paired Group Resizing Toggle
                var isPairedResizingGlobalEnabled by remember { 
                    mutableStateOf(ThemeManager.getPairedScalingGlobal(context)) 
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Paired Group Resizing", style = MaterialTheme.typography.bodyLarge)
                        Text("Scale paired split-screen groups together in perfect unison by resizing their outer edges/corners.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = isPairedResizingGlobalEnabled, onCheckedChange = { 
                        isPairedResizingGlobalEnabled = it
                        ThemeManager.setPairedScalingGlobal(context, it)
                    })
                }

                if (isPairedResizingGlobalEnabled) {
                    Spacer(Modifier.height(12.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                            displayManager.displays.forEach { d ->
                                Text("Configure: ${d.name} (ID: ${d.displayId})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                
                                var scalingEnabled by remember(d.displayId) { 
                                    mutableStateOf(ThemeManager.getPairedScaling(context, d.displayId)) 
                                }
                                
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Enable Group Resizing", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = scalingEnabled, onCheckedChange = {
                                        scalingEnabled = it
                                        ThemeManager.setPairedScaling(context, d.displayId, it)
                                    })
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                var isTiledSwapGlobalEnabled by remember { 
                    mutableStateOf(ThemeManager.getTiledSwapGlobal(context)) 
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tiled Layout Swap", style = MaterialTheme.typography.bodyLarge)
                        Text("Exchange positions of tiled snapped windows by tapping their bounds switcher representations.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = isTiledSwapGlobalEnabled, onCheckedChange = { 
                        isTiledSwapGlobalEnabled = it
                        ThemeManager.setTiledSwapGlobal(context, it)
                    })
                }

                if (isTiledSwapGlobalEnabled) {
                    Spacer(Modifier.height(12.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                            displayManager.displays.forEach { d ->
                                Text("Configure: ${d.name} (ID: ${d.displayId})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                
                                var swapEnabled by remember(d.displayId) { 
                                    mutableStateOf(ThemeManager.getTiledSwap(context, d.displayId)) 
                                }
                                
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Enable Layout Swap", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = swapEnabled, onCheckedChange = {
                                        swapEnabled = it
                                        ThemeManager.setTiledSwap(context, d.displayId, it)
                                    })
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                var isVisualHandlesGlobalEnabled by remember {
                    mutableStateOf(ThemeManager.getVisualCornerHandlesGlobal(context))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show Visual Corner Handles", style = MaterialTheme.typography.bodyLarge)
                        Text("Displays prominent rounded handles on window corners for easier touch input. Diagonal resizing itself is always active by dragging the window corners.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = isVisualHandlesGlobalEnabled, onCheckedChange = {
                        isVisualHandlesGlobalEnabled = it
                        ThemeManager.setVisualCornerHandlesGlobal(context, it)
                    })
                }

                if (isVisualHandlesGlobalEnabled) {
                    Spacer(Modifier.height(12.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                            displayManager.displays.forEach { d ->
                                Text("Configure: ${d.name} (ID: ${d.displayId})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                
                                var handlesEnabled by remember(d.displayId) { 
                                    mutableStateOf(ThemeManager.getVisualCornerHandles(context, d.displayId)) 
                                }
                                
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Enable Corner Handles", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = handlesEnabled, onCheckedChange = {
                                        handlesEnabled = it
                                        ThemeManager.setVisualCornerHandles(context, d.displayId, it)
                                    })
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                var hideOnLauncherActive by remember { mutableStateOf(ThemeManager.getHideOnLauncherActive(context)) }
                var selectedLauncherPackage by remember { mutableStateOf(ThemeManager.getDockLauncherPackage(context)) }
                
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Launcher & Dock Avoidance", style = MaterialTheme.typography.bodyLarge)
                        Text("Dims window title bars to 15% opacity when the selected launcher/dock start menu is open to preserve immersion.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = hideOnLauncherActive, onCheckedChange = { 
                        hideOnLauncherActive = it
                        ThemeManager.setHideOnLauncherActive(context, it)
                    })
                }

                if (hideOnLauncherActive) {
                    Spacer(Modifier.height(12.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Select Active Launcher / Dock", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))

                            // 1. Detect all home launchers and merge with common docks
                            val pm = context.packageManager
                            val detectedLaunchers = remember {
                                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                                val homeApps = pm.queryIntentActivities(intent, 0)
                                val list = homeApps.map {
                                    val label = it.activityInfo.loadLabel(pm).toString()
                                    val pkg = it.activityInfo.packageName
                                    Pair(label, pkg)
                                }.toMutableList()
                                
                                // Add farmerbb Taskbar
                                val taskbarPkg = "com.farmerbb.taskbar"
                                val isTaskbarInstalled = try { pm.getPackageInfo(taskbarPkg, 0); true } catch (e: Exception) { false }
                                if (isTaskbarInstalled) {
                                    list.add(Pair("Taskbar (Farmerbb)", taskbarPkg))
                                } else {
                                    list.add(Pair("Taskbar (Common Dock)", taskbarPkg))
                                }
                                list.distinctBy { it.second }
                            }

                            var showCustomLauncherPicker by remember { mutableStateOf(false) }
                            
                            // Find the currently selected launcher label
                            val currentLauncherLabel = detectedLaunchers.find { it.second == selectedLauncherPackage }?.first 
                                ?: if (selectedLauncherPackage.isNotEmpty()) selectedLauncherPackage else "None Selected"

                            Text("Active Bind: $currentLauncherLabel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))

                            // Show quick list of detected launchers
                            detectedLaunchers.forEach { launcher ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLauncherPackage = launcher.second
                                            ThemeManager.setDockLauncherPackage(context, launcher.second)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (selectedLauncherPackage == launcher.second),
                                        onClick = {
                                            selectedLauncherPackage = launcher.second
                                            ThemeManager.setDockLauncherPackage(context, launcher.second)
                                        }
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(launcher.first, style = MaterialTheme.typography.bodyMedium)
                                        Text(launcher.second, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showCustomLauncherPicker = true },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Search other apps...")
                            }

                            if (showCustomLauncherPicker) {
                                var customSearchQuery by remember { mutableStateOf("") }
                                var customApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
                                var filteredCustomApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
                                
                                LaunchedEffect(Unit) {
                                    withContext(Dispatchers.IO) {
                                        val apps = pm.getInstalledApplications(0)
                                            .map { app ->
                                                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                                                AppInfo(app.loadLabel(pm).toString(), app.packageName, null, isSystem)
                                            }
                                            .sortedBy { it.label }
                                        withContext(Dispatchers.Main) {
                                            customApps = apps
                                            filteredCustomApps = apps
                                        }
                                    }
                                }

                                AlertDialog(
                                    onDismissRequest = { showCustomLauncherPicker = false },
                                    title = { Text("Select Custom Launcher / Dock") },
                                    text = {
                                        Column {
                                            OutlinedTextField(
                                                value = customSearchQuery,
                                                onValueChange = { query ->
                                                    customSearchQuery = query
                                                    filteredCustomApps = customApps.filter { app ->
                                                        app.label.contains(query, ignoreCase = true) ||
                                                        app.packageName.contains(query, ignoreCase = true)
                                                    }
                                                },
                                                label = { Text("Search installed apps") },
                                                modifier = Modifier.fillMaxWidth(),
                                                leadingIcon = { Icon(Icons.Default.Search, null) }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LazyColumn(modifier = Modifier.height(300.dp)) {
                                                items(filteredCustomApps, key = { it.packageName }) { app ->
                                                    ListItem(
                                                        leadingContent = { AppIcon(app.packageName) },
                                                        headlineContent = { Text(app.label) },
                                                        supportingContent = { Text(app.packageName) },
                                                        modifier = Modifier.clickable {
                                                            selectedLauncherPackage = app.packageName
                                                            ThemeManager.setDockLauncherPackage(context, app.packageName)
                                                            showCustomLauncherPicker = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showCustomLauncherPicker = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compatibility Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CompatibilityScreen(padding: PaddingValues) {
    val context = LocalContext.current

    val fixStates = remember {
        CompatibilityManager.ALL_FIXES.associate { fix ->
            fix.id to mutableStateOf(
                ThemeManager.getCompFix(context, fix.id, fix.smartDefault())
            )
        }
    }

    val anyNonDefault = fixStates.entries.any { (id, state) ->
        val def = CompatibilityManager.ALL_FIXES.find { it.id == id }
        state.value != (def?.smartDefault?.invoke() ?: state.value)
    }

    var showResetConfirm by remember { mutableStateOf(false) }

    val sdkInt = Build.VERSION.SDK_INT
    val versionName = when {
        sdkInt >= 35 -> "Android 15+"
        sdkInt == 34 -> "Android 14"
        sdkInt == 33 -> "Android 13"
        sdkInt == 32 -> "Android 12L"
        sdkInt == 31 -> "Android 12"
        sdkInt == 30 -> "Android 11"
        sdkInt == 29 -> "Android 10"
        else         -> "Android (API $sdkInt)"
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Compatibility",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Toggle per-Android-version workarounds. Defaults are auto-selected for your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // Device version badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column {
                    Text(
                        "Your Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "$versionName  ·  API $sdkInt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Fix Cards
        CompatibilityManager.ALL_FIXES.forEach { fix ->
            val isRelevantForDevice = fix.smartDefault()
            val state = fixStates[fix.id] ?: return@forEach

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        !isRelevantForDevice -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        state.value          -> MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        else                 -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    }
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    fix.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isRelevantForDevice)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                if (isRelevantForDevice) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "ACTIVE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                fix.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (!isRelevantForDevice) 0.5f else 1f
                                )
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    fix.affectsVersionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (!isRelevantForDevice) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Not needed for $versionName — you can still force-enable it.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.value,
                            onCheckedChange = { newValue ->
                                state.value = newValue
                                ThemeManager.setCompFix(context, fix.id, newValue)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Reset to Defaults
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (anyNonDefault)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (anyNonDefault)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset All to Smart Defaults", fontWeight = FontWeight.SemiBold)
        }

        Text(
            "Resets every toggle to the recommended value for $versionName (API $sdkInt).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text("Reset Compatibility Fixes?") },
            text = {
                Text(
                    "All toggles will revert to their recommended defaults for $versionName (API $sdkInt). " +
                    "Any manual overrides you've set will be lost.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ThemeManager.resetCompFixes(context)
                        CompatibilityManager.ALL_FIXES.forEach { fix ->
                            fixStates[fix.id]?.value = fix.smartDefault()
                        }
                        showResetConfirm = false
                        Toast.makeText(context, "Compatibility fixes reset to defaults.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(padding: PaddingValues) {
    val context = LocalContext.current
    var allInstalled by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: User, 2: System
    val scope = rememberCoroutineScope()
    
    var refreshKey by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
                .map { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    AppInfo(app.loadLabel(pm).toString(), app.packageName, null, isSystem)
                }
            withContext(Dispatchers.Main) { allInstalled = apps }
        }
    }
    
    val displayList by remember(allInstalled, searchQuery, selectedFilter, refreshKey) {
        derivedStateOf {
            allInstalled.asSequence()
                .filter { app ->
                    // 1. Filter by search query
                    app.label.contains(searchQuery, true) || app.packageName.contains(searchQuery, true)
                }
                .filter { app ->
                    // 2. Filter by category
                    when (selectedFilter) {
                        1 -> !app.isSystem
                        2 -> app.isSystem
                        else -> true
                    }
                }
                .map { app ->
                    // Pre-calculate blacklisted state for efficient sorting
                    Pair(app, FreeformOverlayService.isBlacklisted(context, app.packageName))
                }
                .sortedWith(compareByDescending<Pair<AppInfo, Boolean>> { it.second }.thenBy { it.first.label.lowercase() })
                .toList()
        }
    }
    
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
        Text("App Blacklist", style = MaterialTheme.typography.headlineSmall)
        Text("Apps in this list will NOT have freeform overlays attached.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        Spacer(Modifier.height(12.dp))
        
        // Filter Tags
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "User", "System").forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedFilter == index,
                    onClick = { selectedFilter = index },
                    label = { Text(label) },
                    leadingIcon = if (selectedFilter == index) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } }
            } else null,
            shape = MaterialTheme.shapes.medium
        )
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(displayList, key = { it.first.packageName }) { (app, isBlacklisted) ->
                ListItem(
                    headlineContent = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label)
                            if (app.isSystem) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "SYSTEM",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelSmall) },
                    leadingContent = { AppIcon(app.packageName, modifier = Modifier.size(32.dp)) },
                    trailingContent = {
                        Switch(checked = isBlacklisted, onCheckedChange = { 
                            FreeformOverlayService.toggleBlacklist(context, app.packageName)
                            refreshKey++
                        })
                    }
                )
                Divider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
fun PillPreviewCard(shrinkStyle: Int) {
    var isShrunk by remember { mutableStateOf(false) }
    
    // Periodically toggle the shrink state to show the animation preview
    LaunchedEffect(shrinkStyle) {
        while (true) {
            delay(2000)
            isShrunk = !isShrunk
        }
    }
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Style Animation Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                // Style 0: Scale Transform (Old method)
                if (shrinkStyle == 0) {
                    val scale by animateFloatAsState(
                        targetValue = if (isShrunk) 0.7f else 1.0f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "scale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isShrunk) 0.4f else 1.0f,
                        animationSpec = tween(durationMillis = 400),
                        label = "alpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                alpha = alpha
                            )
                            .width(160.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp).background(Color.White.copy(0.7f), androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.width(60.dp).height(6.dp).background(Color.White.copy(0.5f)))
                        }
                    }
                } 
                // Style 1: Handle/Bar Resizing (New method)
                else {
                    val width by animateDpAsState(
                        targetValue = if (isShrunk) 60.dp else 160.dp,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "width"
                    )
                    val height by animateDpAsState(
                        targetValue = if (isShrunk) 8.dp else 40.dp,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "height"
                    )
                    val cornerRadius by animateDpAsState(
                        targetValue = if (isShrunk) 4.dp else 20.dp,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "corners"
                    )
                    
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(height)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius))
                            .padding(horizontal = if (isShrunk) 0.dp else 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Only show text/icons when expanded (i.e. width > 100.dp)
                        if (width > 100.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).background(Color.White.copy(0.7f), androidx.compose.foundation.shape.CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.width(60.dp).height(6.dp).background(Color.White.copy(0.5f)))
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isShrunk) "State: Shrunk (Inactive)" else "State: Expanded (Active/Hovered)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
