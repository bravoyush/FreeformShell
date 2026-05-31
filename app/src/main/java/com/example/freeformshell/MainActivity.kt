package com.example.freeformshell

import android.content.Context
import android.content.Intent
import java.io.File
import androidx.compose.foundation.BorderStroke
import android.util.Log
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size

class MainActivity : ComponentActivity() {

    private val onPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dynamically update pinned dynamic Mirror shortcut if it exists on the home screen
        try {
            MirrorShortcutHelper.updatePinnedMirrorShortcutIfExist(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to update dynamic wallpaper shortcut", e)
        }
        
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
    var isDesktopModeEnabled by remember { mutableStateOf(ThemeManager.isForceDesktopModeEnabled(context)) }

    val activity = context as? ComponentActivity
    
    var hasCameraPermission by remember { 
        mutableStateOf(context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }
    var hasMicPermission by remember { 
        mutableStateOf(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            hasCameraPermission = context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
            hasMicPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            isShizukuAvailable = Shizuku.pingBinder()
            hasShizukuPermission = isShizukuAvailable && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            hasOverlayPermission = Settings.canDrawOverlays(context)
        }
    }
    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent
        if (intent != null && (intent.action == "ACTION_OPEN_CAPTURE_TAB" || intent.getBooleanExtra("open_capture_tab", false) == true)) {
            selectedTabIndex = 8
            intent.action = null
            intent.putExtra("open_capture_tab", false)
        }
    }

    var editingGroup by remember { mutableStateOf<WorkspaceGroup?>(null) }
    var isEditingFavorite by remember { mutableStateOf(false) }
    var refreshWorkspacesKey by remember { mutableStateOf(0) }

    var appUiStyle by remember { mutableStateOf(ThemeManager.getAppUiStyle(context)) }
    var appUiScale by remember { mutableStateOf(ThemeManager.getAppUiScale(context)) }
    var autoUiScalingEnabled by remember { mutableStateOf(ThemeManager.isAutoUiScalingEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isShizukuAvailable = Shizuku.pingBinder()
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasShizukuPermission = isShizukuAvailable && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                scope.launch(Dispatchers.IO) {
                    val displayDump = ShellExecutor.exec("dumpsys display")
                    val displayMap = LinkedHashMap<Int, DisplayInfo>()
                    
                    // Maps to track distinct physical and active densities/resolutions from dumpsys
                    val physicalMap = HashMap<Int, Int>()
                    val activeMap = HashMap<Int, Int>()
                    val physicalWidths = HashMap<Int, Int>()
                    val physicalHeights = HashMap<Int, Int>()
                    val activeWidths = HashMap<Int, Int>()
                    val activeHeights = HashMap<Int, Int>()
                    val infoMap = LinkedHashMap<Int, DisplayInfo>()
                    
                    // Tier 1: Match standard internal DisplayInfo blocks (Android 11 to 15+)
                    val infoRegex = """DisplayInfo\{"([^"]+)",\s+displayId\s+(\d+),.*?real\s+(\d+)\s*x\s*(\d+),.*?density\s+(\d+)""".toRegex()
                    displayDump.lineSequence().forEach { line ->
                        val match = infoRegex.find(line)
                        if (match != null) {
                            val id = match.groupValues[2].toInt()
                            val name = match.groupValues[1]
                            val w = match.groupValues[3].toInt()
                            val h = match.groupValues[4].toInt()
                            val density = match.groupValues[5].toInt()
                            
                            if (line.contains("mBaseDisplayInfo")) {
                                physicalMap[id] = density
                                physicalWidths[id] = w
                                physicalHeights[id] = h
                            } else if (line.contains("mOverrideDisplayInfo")) {
                                activeMap[id] = density
                                activeWidths[id] = w
                                activeHeights[id] = h
                            } else {
                                if (!physicalMap.containsKey(id)) {
                                    physicalMap[id] = density
                                    physicalWidths[id] = w
                                    physicalHeights[id] = h
                                }
                                if (!activeMap.containsKey(id)) {
                                    activeMap[id] = density
                                    activeWidths[id] = w
                                    activeHeights[id] = h
                                }
                            }
                            
                            infoMap[id] = DisplayInfo(
                                id = id,
                                name = name,
                                width = w,
                                height = h
                            )
                        }
                    }
                    
                    if (infoMap.isNotEmpty()) {
                        infoMap.forEach { (id, baseInfo) ->
                            val parsedPhys = physicalMap[id] ?: 0
                            val parsedActive = activeMap[id] ?: parsedPhys
                            val savedPhys = ThemeManager.getPhysicalDensity(context, id, 0)
                            
                            val finalPhys = if (parsedPhys > 0) {
                                parsedPhys
                            } else if (savedPhys > 0) {
                                savedPhys
                            } else {
                                if (parsedActive > 0) parsedActive else 420
                            }
                            
                            if (id != 0 && finalPhys > 0 && finalPhys != savedPhys) {
                                ThemeManager.setPhysicalDensity(context, id, finalPhys)
                            }
                            
                            val finalActive = if (parsedActive > 0) parsedActive else finalPhys
                            
                            // Width & Height overrides
                            val parsedPhysW = physicalWidths[id] ?: baseInfo.width
                            val parsedPhysH = physicalHeights[id] ?: baseInfo.height
                            val parsedActiveW = activeWidths[id] ?: parsedPhysW
                            val parsedActiveH = activeHeights[id] ?: parsedPhysH
                            
                            val savedPhysW = ThemeManager.getWidth(context, id, 0)
                            val savedPhysH = ThemeManager.getHeight(context, id, 0)
                            
                            val finalPhysW = if (parsedPhysW > 0) parsedPhysW else if (savedPhysW > 0) savedPhysW else baseInfo.width
                            val finalPhysH = if (parsedPhysH > 0) parsedPhysH else if (savedPhysH > 0) savedPhysH else baseInfo.height
                            
                            if (id != 0 && finalPhysW > 0 && finalPhysW != savedPhysW) {
                                ThemeManager.setWidth(context, id, finalPhysW)
                                ThemeManager.setHeight(context, id, finalPhysH)
                            }
                            
                            displayMap[id] = DisplayInfo(
                                id = id,
                                name = baseInfo.name,
                                width = finalPhysW,
                                height = finalPhysH,
                                dpi = finalPhys,
                                activeDpi = finalActive,
                                activeWidth = parsedActiveW,
                                activeHeight = parsedActiveH
                            )
                        }
                    }
                    
                    // Tier 2 Fallback: Match legacy single-line formatting
                    if (displayMap.isEmpty()) {
                        val displayRegex = """Display\s+#(\d+):\s+name="([^"]+)",.*?real\s+(\d+)x(\d+),.*?density\s+(\d+)""".toRegex()
                        displayRegex.findAll(displayDump).forEach { match ->
                            val id = match.groupValues[1].toInt()
                            val parsedDensity = match.groupValues[5].toInt()
                            val savedPhys = ThemeManager.getPhysicalDensity(context, id, 0)
                            
                            val finalPhys = if (id == 0) {
                                parsedDensity
                            } else if (savedPhys > 0) {
                                savedPhys
                            } else {
                                parsedDensity
                            }
                            
                            if (id != 0 && savedPhys == 0) {
                                ThemeManager.setPhysicalDensity(context, id, finalPhys)
                            }
                            
                            val parsedW = match.groupValues[3].toInt()
                            val parsedH = match.groupValues[4].toInt()
                            val savedW = ThemeManager.getWidth(context, id, 0)
                            val savedH = ThemeManager.getHeight(context, id, 0)
                            
                            val finalW = if (savedW > 0) savedW else parsedW
                            val finalH = if (savedH > 0) savedH else parsedH
                            
                            if (id != 0 && savedW == 0) {
                                ThemeManager.setWidth(context, id, finalW)
                                ThemeManager.setHeight(context, id, finalH)
                            }
                            
                            displayMap[id] = DisplayInfo(
                                id = id,
                                name = match.groupValues[2],
                                width = finalW,
                                height = finalH,
                                dpi = finalPhys,
                                activeDpi = parsedDensity,
                                activeWidth = parsedW,
                                activeHeight = parsedH
                            )
                        }
                    }
                    
                    // Tier 3 Fallback: Robust SDK DisplayManager with precise density metrics
                    if (displayMap.isEmpty()) {
                        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                        dm.displays.forEach { d ->
                            val metrics = android.util.DisplayMetrics()
                            d.getRealMetrics(metrics)
                            val parsedDensity = metrics.densityDpi
                            val savedPhys = ThemeManager.getPhysicalDensity(context, d.displayId, 0)
                            
                            val finalPhys = if (d.displayId == 0) {
                                parsedDensity
                            } else if (savedPhys > 0) {
                                savedPhys
                            } else {
                                parsedDensity
                            }
                            
                            if (d.displayId != 0 && savedPhys == 0) {
                                ThemeManager.setPhysicalDensity(context, d.displayId, finalPhys)
                            }
                            
                            val savedW = ThemeManager.getWidth(context, d.displayId, 0)
                            val savedH = ThemeManager.getHeight(context, d.displayId, 0)
                            val finalW = if (savedW > 0) savedW else metrics.widthPixels
                            val finalH = if (savedH > 0) savedH else metrics.heightPixels
                            
                            if (d.displayId != 0 && savedW == 0) {
                                ThemeManager.setWidth(context, d.displayId, finalW)
                                ThemeManager.setHeight(context, d.displayId, finalH)
                            }
                            
                            displayMap[d.displayId] = DisplayInfo(
                                id = d.displayId,
                                name = d.name ?: "Display ${d.displayId}",
                                width = finalW,
                                height = finalH,
                                dpi = finalPhys,
                                activeDpi = parsedDensity,
                                activeWidth = metrics.widthPixels,
                                activeHeight = metrics.heightPixels
                            )
                        }
                    }
                    withContext(Dispatchers.Main) { availableDisplays = displayMap.values.toList() }
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        
        val currentDensity = LocalDensity.current
        val customDensity = remember(currentDensity, windowWidth, windowHeight, appUiScale, autoUiScalingEnabled) {
            val baseScale = if (autoUiScalingEnabled) {
                when {
                    windowWidth < 450.dp || windowHeight < 450.dp -> 0.76f
                    windowWidth < 650.dp || windowHeight < 600.dp -> 0.86f
                    else -> 1f
                }
            } else {
                1f
            }
            val scaleFactor = baseScale * appUiScale
            Density(
                density = currentDensity.density * scaleFactor,
                fontScale = currentDensity.fontScale * scaleFactor
            )
        }
        
        CompositionLocalProvider(LocalDensity provides customDensity) {
            if (editingGroup != null) {
                WorkspaceSandboxEditor(
                    group = editingGroup!!,
                    isFavorite = isEditingFavorite,
                    onBack = { editingGroup = null },
                    onRefresh = { refreshWorkspacesKey++ }
                )
            } else {
                Scaffold(
                bottomBar = {
                    if (appUiStyle == 0) {
                        NavigationBar {
                            val showLabels = windowWidth >= 520.dp
                            NavigationBarItem(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, icon = { Icon(Icons.Default.Window, null) }, label = { Text("Windows") }, alwaysShowLabel = showLabels)
                            NavigationBarItem(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, icon = { Icon(Icons.Default.Palette, null) }, label = { Text("Customization") }, alwaysShowLabel = showLabels)
                            NavigationBarItem(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, icon = { Icon(Icons.Default.List, null) }, label = { Text("Task Manager") }, alwaysShowLabel = showLabels)
                            NavigationBarItem(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }, icon = { Icon(Icons.Default.Tv, null) }, label = { Text("Display") }, alwaysShowLabel = showLabels)
                            if (isDesktopModeEnabled) {
                                NavigationBarItem(selected = selectedTabIndex == 9, onClick = { selectedTabIndex = 9 }, icon = { Icon(Icons.Default.Monitor, null) }, label = { Text("Desktop") }, alwaysShowLabel = showLabels)
                            }
                            NavigationBarItem(selected = selectedTabIndex == 4, onClick = { selectedTabIndex = 4 }, icon = { Icon(Icons.Default.Block, null) }, label = { Text("Blacklist") }, alwaysShowLabel = showLabels)
                            NavigationBarItem(selected = selectedTabIndex == 8, onClick = { selectedTabIndex = 8 }, icon = { Icon(Icons.Default.Videocam, null) }, label = { Text("Capture") }, alwaysShowLabel = showLabels)
                            NavigationBarItem(selected = selectedTabIndex == 5, onClick = { selectedTabIndex = 5 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, alwaysShowLabel = showLabels)
                            NavigationBarItem(selected = selectedTabIndex == 10, onClick = { selectedTabIndex = 10 }, icon = { Icon(Icons.Default.Lock, null) }, label = { Text("Lock") }, alwaysShowLabel = showLabels)
                            NavigationBarItem(selected = selectedTabIndex == 6, onClick = { selectedTabIndex = 6 }, icon = { Icon(Icons.Default.Build, null) }, label = { Text("Compat") }, alwaysShowLabel = showLabels)
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTabIndex == 0 && appUiStyle == 0) {
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
                val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
                    when (selectedTabIndex) {
                        0 -> DashboardScreen(
                            padding = innerPadding,
                            availableDisplays = availableDisplays,
                            selectedDisplayId = selectedDisplayId,
                            onSelectDisplay = { selectedDisplayId = it },
                            isShizukuAvailable = isShizukuAvailable,
                            hasOverlayPermission = hasOverlayPermission,
                            launchQueue = launchQueue,
                            onLaunchApp = { showAppPicker = true },
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
                            onRefreshWorkspaces = { refreshWorkspacesKey++ },
                            onDesktopModeToggled = { isDesktopModeEnabled = it },
                            onShowPermissions = { showSettingsDialog = true },
                            onShowLogs = { showLogDialog = true }
                        )
                        1 -> CustomizationScreen(
                            padding = innerPadding,
                            appUiScale = appUiScale,
                            onAppUiScaleChange = { 
                                appUiScale = it
                                ThemeManager.setAppUiScale(context, it)
                            },
                            autoUiScalingEnabled = autoUiScalingEnabled,
                            onAutoUiScalingToggle = { 
                                autoUiScalingEnabled = it
                                ThemeManager.setAutoUiScalingEnabled(context, it)
                            }
                        )
                        2 -> TaskManagerScreen(
                            padding = innerPadding,
                            tasks = tasks,
                            isLoading = isLoading,
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
                            selectedDisplayId = selectedDisplayId,
                            onRefreshWorkspaces = { refreshWorkspacesKey++ }
                        )
                        3 -> DisplaySettingsScreen(innerPadding, availableDisplays)
                        9 -> DesktopSettingsScreen(innerPadding, availableDisplays)
                        4 -> BlacklistScreen(innerPadding)
                        5 -> AppSettingsScreen(innerPadding, availableDisplays)
                        6 -> CompatibilityScreen(innerPadding)
                        7 -> ExpressiveWorkspaceManagerScreen(
                            padding = innerPadding,
                            refreshKey = refreshWorkspacesKey,
                            onRefresh = { refreshWorkspacesKey++ },
                            onEditGroup = { group, isFav ->
                                editingGroup = group
                                isEditingFavorite = isFav
                            }
                        )
                        8 -> CaptureScreen(innerPadding, availableDisplays)
                        10 -> LockScreenSettingsScreen(innerPadding)
                    }
                }

                if (appUiStyle == 1) {
                    ExpressiveLayout(
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it },
                        content = content
                    )
                } else {
                    content(padding)
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

        if (showSettingsDialog) {
            AlertDialog(onDismissRequest = { showSettingsDialog = false }, title = { Text("Permissions & Settings") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        ListItem(headlineContent = { Text("Shizuku Service") }, trailingContent = { StatusChip(label = "Bind", color = if (isShizukuAvailable) Color.Green else Color.Red) })
                        ListItem(headlineContent = { Text("Shizuku Permission") }, trailingContent = { StatusChip(label = "Granted", color = if (hasShizukuPermission) Color.Green else Color.Red) }, modifier = Modifier.clickable { if (!hasShizukuPermission && isShizukuAvailable) onRequestShizukuPermission() })
                        ListItem(headlineContent = { Text("Display Overlays") }, trailingContent = { StatusChip(label = "Granted", color = if (hasOverlayPermission) Color.Green else Color.Red) }, modifier = Modifier.clickable { if (!hasOverlayPermission) onRequestOverlayPermission() })
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        ListItem(
                            headlineContent = { Text("Camera Access (Optional)") },
                            supportingContent = { Text("Used for Facecam PIP overlay support during display capture & broadcast.", style = MaterialTheme.typography.bodySmall, color = Color.Gray) },
                            trailingContent = { StatusChip(label = if (hasCameraPermission) "Granted" else "Request", color = if (hasCameraPermission) Color.Green else Color.Red) },
                            modifier = Modifier.clickable {
                                if (hasCameraPermission) {
                                    Toast.makeText(context, "Optional: To revoke/disable camera, please do so from system Settings.", Toast.LENGTH_LONG).show()
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Failed opening Details Settings", e)
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            }
                        )
                        
                        ListItem(
                            headlineContent = { Text("Microphone Access (Optional)") },
                            supportingContent = { Text("Used for mic voiceover audio synchronization during display recording.", style = MaterialTheme.typography.bodySmall, color = Color.Gray) },
                            trailingContent = { StatusChip(label = if (hasMicPermission) "Granted" else "Request", color = if (hasMicPermission) Color.Green else Color.Red) },
                            modifier = Modifier.clickable {
                                if (hasMicPermission) {
                                    Toast.makeText(context, "Optional: To revoke/disable microphone, please do so from system Settings.", Toast.LENGTH_LONG).show()
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Failed opening Details Settings", e)
                                    }
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Close") } }
            )
        }
    }
    }
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
    isShizukuAvailable: Boolean,
    hasOverlayPermission: Boolean,
    launchQueue: List<AppInfo>,
    onLaunchApp: () -> Unit,
    onClearQueue: () -> Unit,
    onLaunchQueue: () -> Unit,
    editingGroup: WorkspaceGroup?,
    onEditGroup: (WorkspaceGroup, Boolean) -> Unit,
    refreshWorkspacesKey: Int,
    onRefreshWorkspaces: () -> Unit,
    onDesktopModeToggled: (Boolean) -> Unit,
    onShowPermissions: () -> Unit = {},
    onShowLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var refreshTrigger by remember { mutableStateOf(0) }
    
    val enabledDisplays = remember(availableDisplays, refreshTrigger) {
        availableDisplays.filter { display ->
            ThemeManager.isDisplayShellEnabled(context, display.id)
        }.ifEmpty { availableDisplays }
    }
    
    LaunchedEffect(enabledDisplays) {
        if (enabledDisplays.none { it.id == selectedDisplayId }) {
            val fallbackId = enabledDisplays.firstOrNull()?.id ?: 0
            onSelectDisplay(fallbackId)
        }
    }
    
    val isExpressive = ThemeManager.getAppUiStyle(context) == 1

    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            }
            if (isExpressive) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onShowPermissions,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info, 
                            contentDescription = "Permissions Status", 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onShowLogs,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List, 
                            contentDescription = "System Console Logs", 
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(label = "Shizuku", color = if (isShizukuAvailable) Color(0xFF4CAF50) else Color(0xFFF44336))
            StatusChip(label = "Overlay", color = if (hasOverlayPermission) Color(0xFF4CAF50) else Color(0xFFF44336))
        }
 
        Spacer(modifier = Modifier.height(16.dp))

        // --- Big Premium Force Desktop Mode Toggle Button ---
        var isDesktopModeActive by remember { mutableStateOf(ThemeManager.isForceDesktopModeEnabled(context)) }
        var showDesktopWarning by remember { mutableStateOf(false) }

        Card(
            onClick = { showDesktopWarning = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .shadow(8.dp, shape = RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = if (isDesktopModeActive) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (isDesktopModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = if (isDesktopModeActive) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else 
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDesktopModeActive) Icons.Default.Monitor else Icons.Default.TvOff,
                        contentDescription = null,
                        tint = if (isDesktopModeActive) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDesktopModeActive) "Force Desktop Mode: ACTIVE" else "Force Desktop Mode: INACTIVE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDesktopModeActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isDesktopModeActive) 
                            "Secondary display environment is enabled. Tap to disable."
                        else 
                            "Enable desktop environment on external displays programmatically. Tap to activate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDesktopModeActive) 
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else 
                            Color.Gray
                    )
                }

                Spacer(Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isDesktopModeActive) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }

        if (showDesktopWarning) {
            val nextState = !isDesktopModeActive
            val value = if (nextState) 1 else 0
            val cmd = "settings put global force_desktop_mode_on_external_displays $value"
            AlertDialog(
                onDismissRequest = { showDesktopWarning = false },
                icon = { Icon(Icons.Default.Monitor, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
                title = {
                    Text(
                        text = if (nextState) "Enable Desktop Mode?" else "Disable Desktop Mode?",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "Runs:\n$cmd\n\nSome devices apply this immediately after replug or a few seconds. Others need a SystemUI restart or reboot.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Primary: apply only (no restart) — works on-the-fly on many devices
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showDesktopWarning = false
                                isDesktopModeActive = nextState
                                onDesktopModeToggled(nextState)
                                scope.launch(Dispatchers.IO) {
                                    val result = ShellExecutor.executeCommandWithResult(cmd)
                                    val msg = if (result.third == 0)
                                        "Desktop Mode ${if (nextState) "enabled" else "disabled"}. Replug display or wait a few seconds."
                                    else
                                        "Command failed (exit ${result.third}): ${result.second.take(120)}"
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Apply Only (No Restart)")
                        }

                        // Restart SystemUI after applying
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showDesktopWarning = false
                                isDesktopModeActive = nextState
                                onDesktopModeToggled(nextState)
                                scope.launch(Dispatchers.IO) {
                                    ShellExecutor.executeCommandWithResult(cmd)
                                    ShellExecutor.executeCommandWithResult("killall com.android.systemui")
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Apply + Restart SystemUI")
                        }

                        // Reboot after applying
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showDesktopWarning = false
                                isDesktopModeActive = nextState
                                onDesktopModeToggled(nextState)
                                scope.launch(Dispatchers.IO) {
                                    ShellExecutor.executeCommandWithResult(cmd)
                                    ShellExecutor.executeCommandWithResult("reboot")
                                }
                            }
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Apply + Reboot")
                        }

                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showDesktopWarning = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            )
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
                
                availableDisplays.forEach { display ->
                    val isOverlayEnabled = ThemeManager.isDisplayShellEnabled(context, display.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isOverlayEnabled) { onSelectDisplay(display.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDisplayId == display.id) && isOverlayEnabled,
                            onClick = { if (isOverlayEnabled) onSelectDisplay(display.id) },
                            enabled = isOverlayEnabled
                        )
                        Spacer(Modifier.width(8.dp))
                        DisplayShapeIcon(display, isOverlayEnabled)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = display.name, 
                                style = MaterialTheme.typography.bodyLarge, 
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOverlayEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "${display.width}x${display.height} (ID: ${display.id})", 
                                style = MaterialTheme.typography.labelMedium, 
                                color = if (isOverlayEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = isOverlayEnabled,
                            onCheckedChange = { checked ->
                                ThemeManager.setDisplayShellEnabled(context, display.id, checked)
                                val intent = Intent(context, FreeformOverlayService::class.java).apply {
                                    putExtra("force_relayer", true)
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                refreshTrigger++
                            }
                        )
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

        // Workspace Manager Section
        if (ThemeManager.getAppUiStyle(context) == 0) {
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
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TaskManagerScreen(
    padding: PaddingValues,
    tasks: List<AppTask>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    selectedDisplayId: Int,
    onRefreshWorkspaces: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Task Manager", 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Monitor and manage active freeform windows", 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh List")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator() 
            }
        } else if (tasks.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.extraLarge,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.List, 
                        null, 
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), 
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No Active Windows", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Launch apps in freeform mode and arrange them to see active tasks here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                tasks.forEach { task ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { 
                                Text(
                                    task.packageName.substringAfterLast("."), 
                                    fontWeight = FontWeight.Bold 
                                ) 
                            },
                            supportingContent = { 
                                Text(
                                    "Task ID: ${task.taskId} • Freeform: ${task.isFreeform}", 
                                    style = MaterialTheme.typography.labelSmall 
                                ) 
                            },
                            leadingContent = { 
                                AppIcon(task.packageName, modifier = Modifier.size(40.dp)) 
                            },
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
                                        Icon(
                                            if (isBlacklisted) Icons.Default.Block else Icons.Default.AddCircleOutline, 
                                            null, 
                                            tint = if (isBlacklisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                shape = MaterialTheme.shapes.extraLarge,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Workspace Layout", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Save the current placement of active freeform windows as a Favorite layout.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Button(
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
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "No active freeform window bounds found!", Toast.LENGTH_SHORT).show()
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
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    padding: PaddingValues,
    appUiScale: Float,
    onAppUiScaleChange: (Float) -> Unit,
    autoUiScalingEnabled: Boolean,
    onAutoUiScalingToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("freeform_settings", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(ThemeManager.getThemeMode(context)) }
    var roundness by remember { mutableStateOf(ThemeManager.getRoundness(context)) }
    var opacity by remember { mutableStateOf(ThemeManager.getOpacity(context).toFloat()) }
    var borderWidth by remember { mutableStateOf(ThemeManager.getBorderWidth(context)) }
    var titleBarOpacity by remember { mutableStateOf(ThemeManager.getTitleBarOpacity(context).toFloat()) }
    
    var appUiStyle by remember { mutableStateOf(ThemeManager.getAppUiStyle(context)) }

    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)
        
        val isSystemDark = isSystemInDarkTheme()
        val isPreviewDark = when(mode) { 1 -> false; 2 -> true; else -> isSystemDark }
        WindowPreview(roundness, opacity, borderWidth, titleBarOpacity, isPreviewDark)
        
        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(16.dp))
        
        val iconPacks = remember { IconPackManager.getInstalledIconPacks(context) }
        var selectedIconPack by remember { mutableStateOf(IconPackManager.getSelectedIconPack(context)) }
        var dropdownExpanded by remember { mutableStateOf(false) }
        val currentPackLabel = iconPacks.find { it.packageName == selectedIconPack }?.label ?: "System Default"
        
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Icon Pack", style = MaterialTheme.typography.titleMedium)
            Text("Select a custom icon pack to skin the window title bar app icons.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            Spacer(Modifier.height(8.dp))
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selected: $currentPackLabel", style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    iconPacks.forEach { pack ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (pack.icon != null) {
                                        val bitmap = remember(pack.packageName) {
                                            try {
                                                pack.icon.toBitmap(48, 48).asImageBitmap()
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        if (bitmap != null) {
                                            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(24.dp))
                                        } else {
                                            Icon(Icons.Default.Apps, null, modifier = Modifier.size(24.dp))
                                        }
                                    } else {
                                        Icon(Icons.Default.Apps, null, modifier = Modifier.size(24.dp))
                                    }
                                    Text(pack.label)
                                }
                            },
                            onClick = {
                                selectedIconPack = pack.packageName
                                IconPackManager.setSelectedIconPack(context, pack.packageName)
                                dropdownExpanded = false
                                Toast.makeText(context, "Applied ${pack.label}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
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
        
        Spacer(Modifier.height(24.dp))
        Text("Window Resizing Performance", style = MaterialTheme.typography.titleLarge)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Resizing Engine Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Choose the rendering strategy used during window resizing gestures.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                
                var activeStyle by remember { mutableStateOf(CompatibilityManager.getActiveResizeStyle(context)) }
                
                val styleOptions = listOf(
                    "Real-Time Scaling" to ResizeStyle.REAL_TIME_SCALING,
                    "Classic Tinted Overlay" to ResizeStyle.FUTURE_STYLE_A,
                    "App-Branded Matte Overlay" to ResizeStyle.FUTURE_STYLE_B
                )
                
                var dropdownExpanded by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentLabel = styleOptions.find { it.second == activeStyle }?.first ?: "Real-Time Scaling"
                        Text(currentLabel)
                    }
                    
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        styleOptions.forEach { (label, style) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    activeStyle = style
                                    CompatibilityManager.setActiveResizeStyle(context, style)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (appUiStyle == 1) {
            Text("Sidebar Behaviour", style = MaterialTheme.typography.titleLarge)
            SidebarHoverPreview()
            
            val configuration = LocalConfiguration.current
            val isTablet = configuration.screenWidthDp >= 900
            
            Text("Adaptive Configuration", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(if (isTablet) "Tablet Mode Active" else "Phone Mode Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    
                    // Phone Settings
                    Text("Phone Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    var hoverEnabled by remember { mutableStateOf(ThemeManager.isSidebarHoverExpandEnabled(context)) }
                    var autoCollapse by remember { mutableStateOf(ThemeManager.isSidebarAutoCollapseEnabled(context)) }
                    
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Hover Expand", Modifier.weight(1f))
                        Switch(checked = hoverEnabled, onCheckedChange = { 
                            hoverEnabled = it
                            ThemeManager.setSidebarHoverExpandEnabled(context, it)
                        })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-Collapse", Modifier.weight(1f))
                        Switch(checked = autoCollapse, onCheckedChange = { 
                            autoCollapse = it
                            ThemeManager.setSidebarAutoCollapseEnabled(context, it)
                        })
                    }
                    
                    Divider(Modifier.padding(vertical = 12.dp))
                    
                    // Tablet Settings
                    Text("Tablet Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    var hoverTabletEnabled by remember { mutableStateOf(ThemeManager.isSidebarHoverExpandTabletEnabled(context)) }
                    var autoCollapseTablet by remember { mutableStateOf(ThemeManager.isSidebarAutoCollapseTabletEnabled(context)) }
                    
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Hover Expand", Modifier.weight(1f))
                        Switch(checked = hoverTabletEnabled, onCheckedChange = { 
                            hoverTabletEnabled = it
                            ThemeManager.setSidebarHoverExpandTabletEnabled(context, it)
                        })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-Collapse", Modifier.weight(1f))
                        Switch(checked = autoCollapseTablet, onCheckedChange = { 
                            autoCollapseTablet = it
                            ThemeManager.setSidebarAutoCollapseTabletEnabled(context, it)
                        })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("App UI", style = MaterialTheme.typography.titleLarge)
        Text("Select the primary navigation layout for the shell application.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Classic", "Expressive").forEachIndexed { index, label ->
                FilterChip(
                    selected = appUiStyle == index, 
                    onClick = { 
                        appUiStyle = index
                        ThemeManager.setAppUiStyle(context, index)
                        // Trigger a full recompose of the MainScreen
                        (context as? MainActivity)?.recreate()
                    }, 
                    label = { Text(label) }
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))

        Text("Theme Mode", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Auto", "Light", "Dark").forEachIndexed { index, label ->
                FilterChip(selected = mode == index, onClick = { mode = index; ThemeManager.setThemeMode(context, index) }, label = { Text(label) })
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("App UI Scaling", style = MaterialTheme.typography.titleLarge)
        Text("Customize the size of fonts and controls for your physical screen size.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        Spacer(Modifier.height(12.dp))
        
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatic Scaling Mode", style = MaterialTheme.typography.titleMedium)
                Text("Scale layouts down in small/narrow Freeform windows to avoid cramming.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(
                checked = autoUiScalingEnabled, 
                onCheckedChange = onAutoUiScalingToggle
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        val scalePercent = (appUiScale * 100).toInt()
        Text("Custom UI Scale: ${scalePercent}%", style = MaterialTheme.typography.titleMedium)
        Text("Compensates for high physical DPI or small phone displays.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        Spacer(Modifier.height(4.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = appUiScale, 
                onValueChange = onAppUiScaleChange, 
                valueRange = 0.7f..1.3f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { onAppUiScaleChange(1.0f) },
                shape = CircleShape
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
fun SidebarHoverPreview() {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Automatic animation loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            isExpanded = true
            delay(2000)
            isExpanded = false
            delay(1000)
        }
    }
    
    val width by animateDpAsState(
        targetValue = if (isExpanded) 100.dp else 40.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "width"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Hover Expansion Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.CenterStart
            ) {
                // Main Content Mock
                Box(Modifier.fillMaxSize().padding(start = width + 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(12.dp).background(Color.Gray.copy(0.2f), CircleShape))
                        Box(Modifier.fillMaxWidth(0.7f).height(12.dp).background(Color.Gray.copy(0.2f), CircleShape))
                        Box(Modifier.fillMaxWidth(0.9f).height(12.dp).background(Color.Gray.copy(0.2f), CircleShape))
                    }
                }
                
                // Sidebar Mock
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(width)
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Menu, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(12.dp))
                        repeat(4) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Box(Modifier.size(12.dp).background(Color.White.copy(0.7f), CircleShape))
                                if (isExpanded) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.width(40.dp).height(4.dp).background(Color.White.copy(0.4f), CircleShape))
                                }
                            }
                        }
                    }
                }
                
                // Cursor Mock
                val cursorOffset by animateDpAsState(
                    targetValue = if (isExpanded) 15.dp else 60.dp,
                    animationSpec = tween(1000),
                    label = "cursor"
                )
                
                Icon(
                    Icons.Default.Navigation, 
                    null, 
                    tint = MaterialTheme.colorScheme.onSurface, 
                    modifier = Modifier
                        .size(20.dp)
                        .offset(x = cursorOffset, y = 10.dp)
                        .graphicsLayer(rotationZ = -45f)
                )
            }
            
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isExpanded) "Cursor Entered: Sidebar Expanded" else "Cursor Outside: Sidebar Collapsed",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
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
private fun SectionContainer(
    isExpressive: Boolean,
    title: String,
    content: @Composable () -> Unit
) {
    if (isExpressive) {
        Text(
            text = title, 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    } else {
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
        Divider(Modifier.padding(vertical = 16.dp))
    }
}

@Composable
fun DisplaySettingsScreen(padding: PaddingValues, displays: List<DisplayInfo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIdx by remember { mutableStateOf(0) }
    
    val isResolutionOverrideEnabled = CompatibilityManager.isResolutionOverrideEnabled(context)
    val isRefreshRateOverrideEnabled = CompatibilityManager.isRefreshRateOverrideEnabled(context)
    
    val isExpressive = ThemeManager.getAppUiStyle(context) == 1
    
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Display Settings Hub", 
            style = if (isExpressive) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, 
            fontWeight = FontWeight.ExtraBold, 
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text("Configure layout margin boundaries, density, resolutions, and refresh rates for your screen configurations.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        
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

            Text("Configuring: ${display.name} (ID: ${display.id})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            SectionContainer(isExpressive = isExpressive, title = "Dock Position & Margin Bounds") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("None", "Top", "Bottom", "Left", "Right").forEachIndexed { index, label ->
                        FilterChip(
                            selected = dockPos == index, 
                            onClick = { 
                                dockPos = index 
                                ThemeManager.setDockPosition(context, display.id, index)
                                FreeformOverlayService.showDockGuide(display.id, index, dockSize.toInt())
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    FreeformOverlayService.hideDockGuide()
                                }, 1500)
                            }, 
                            label = { Text(label) },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
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
                        valueRange = 0f..1000f
                    )
                    Text("Tip: Adjust slider to see the visual dock guide on the target display.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            // ─── Display Density (DPI) ───
            var physicalDensity by remember(selectedIdx) { mutableStateOf(display.dpi) }
            var activeDensity by remember(selectedIdx) { mutableStateOf(ThemeManager.getDensity(context, display.id, display.activeDpi)) }
            
            var showCustomDpiDialog by remember { mutableStateOf(false) }
            var showPhoneWarningDialog by remember { mutableStateOf(false) }
            var pendingDpiChange by remember { mutableStateOf<Int?>(null) }
            
            LaunchedEffect(selectedIdx) {
                withContext(Dispatchers.IO) {
                    var phys = display.dpi
                    var active = display.activeDpi
                    
                    if (display.id == 0) {
                        val out = ShellExecutor.exec("wm density")
                        val physMatch = "Physical density: (\\d+)".toRegex().find(out)
                        val overrideMatch = "Override density: (\\d+)".toRegex().find(out)
                        phys = physMatch?.groupValues?.get(1)?.toIntOrNull() ?: display.dpi
                        active = overrideMatch?.groupValues?.get(1)?.toIntOrNull() ?: phys
                    } else {
                        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                        val d = dm.getDisplay(display.id)
                        if (d != null) {
                            val metrics = android.util.DisplayMetrics()
                            d.getRealMetrics(metrics)
                            active = metrics.densityDpi
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        activeDensity = active
                        physicalDensity = phys
                    }
                }
            }
            
            val minDimension = minOf(display.width, display.height)
            val minDpiFromDp = (minDimension * 160) / 1000
            val maxDpiFromDp = (minDimension * 160) / 320
            val minAllowedDpi = maxOf(minDpiFromDp, (physicalDensity * 0.5).toInt()).coerceAtLeast(120)
            val maxAllowedDpi = minOf(maxDpiFromDp, (physicalDensity * 1.5).toInt()).coerceAtMost(800)
            
            fun triggerDpiChange(targetDpi: Int) {
                val currentActive = activeDensity
                scope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(context, FreeformOverlayService::class.java).apply {
                            action = "ACTION_DPI_CONFIRMATION"
                            putExtra("displayId", display.id)
                            putExtra("targetDpi", targetDpi)
                            putExtra("originalDpi", currentActive)
                        }
                        context.startService(intent)
                        activeDensity = targetDpi
                        ThemeManager.setDensity(context, display.id, targetDpi)
                    }
                }
            }
            
            fun initiateDpiChange(targetDpi: Int) {
                if (display.id == 0) {
                    pendingDpiChange = targetDpi
                    showPhoneWarningDialog = true
                } else {
                    triggerDpiChange(targetDpi)
                }
            }
            
            val defaultDpis = remember(physicalDensity) {
                mutableListOf(120, 160, 240, 320, 360, 480).apply {
                    if (!contains(physicalDensity)) add(physicalDensity)
                }.sorted()
            }
            
            SectionContainer(isExpressive = isExpressive, title = "Display Density (DPI)") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultDpis.forEach { dpi ->
                        val isSelected = activeDensity == dpi
                        val label = if (dpi == physicalDensity) "$dpi (Default)" else "$dpi"
                        
                        OutlinedButton(
                            onClick = { initiateDpiChange(dpi) },
                            shape = if (isExpressive) CircleShape else ButtonDefaults.outlinedShape,
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            }
                        ) {
                            Text(label)
                        }
                    }
                    
                    OutlinedButton(
                        onClick = { showCustomDpiDialog = true },
                        shape = if (isExpressive) CircleShape else ButtonDefaults.outlinedShape,
                        colors = ButtonDefaults.outlinedButtonColors(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Custom DPI")
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Text("Current: $activeDensity DPI (Physical: $physicalDensity DPI)", style = MaterialTheme.typography.bodyMedium)
                Text("Lower DPI = More content fits (Desktop mode).\nHigher DPI = Larger text and buttons.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                
                Button(
                    onClick = { initiateDpiChange(physicalDensity) },
                    modifier = Modifier.padding(top = 8.dp),
                    shape = if (isExpressive) CircleShape else ButtonDefaults.shape,
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text("Reset Density")
                }
            }
            
            // ─── Display Resolution (WxH) ───
            if (isResolutionOverrideEnabled) {
                var physicalW by remember(selectedIdx) { mutableStateOf(display.width) }
                var physicalH by remember(selectedIdx) { mutableStateOf(display.height) }
                
                var activeW by remember(selectedIdx) { mutableStateOf(ThemeManager.getWidth(context, display.id, display.activeWidth)) }
                var activeH by remember(selectedIdx) { mutableStateOf(ThemeManager.getHeight(context, display.id, display.activeHeight)) }
                
                var showCustomResDialog by remember { mutableStateOf(false) }
                var showPhoneResWarningDialog by remember { mutableStateOf(false) }
                var pendingResW by remember { mutableStateOf<Int?>(null) }
                var pendingResH by remember { mutableStateOf<Int?>(null) }
                
                LaunchedEffect(selectedIdx) {
                    withContext(Dispatchers.IO) {
                        var physW = display.width
                        var physH = display.height
                        var actW = display.activeWidth
                        var actH = display.activeHeight
                        
                        val out = ShellExecutor.exec("wm size -d ${display.id}")
                        val physMatch = "Physical size: (\\d+)x(\\d+)".toRegex().find(out)
                        val overrideMatch = "Override size: (\\d+)x(\\d+)".toRegex().find(out)
                        
                        if (physMatch != null) {
                            physW = physMatch.groupValues[1].toInt()
                            physH = physMatch.groupValues[2].toInt()
                        }
                        if (overrideMatch != null) {
                            actW = overrideMatch.groupValues[1].toInt()
                            actH = overrideMatch.groupValues[2].toInt()
                        } else {
                            actW = physW
                            actH = physH
                        }
                        
                        withContext(Dispatchers.Main) {
                            physicalW = physW
                            physicalH = physH
                            activeW = actW
                            activeH = actH
                        }
                    }
                }
                
                fun triggerResChange(targetW: Int, targetH: Int) {
                    val currentActiveW = activeW
                    val currentActiveH = activeH
                    scope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            val intent = Intent(context, FreeformOverlayService::class.java).apply {
                                action = "ACTION_RESOLUTION_CONFIRMATION"
                                    putExtra("displayId", display.id)
                                    putExtra("targetW", targetW)
                                    putExtra("targetH", targetH)
                                    putExtra("originalW", currentActiveW)
                                    putExtra("originalH", currentActiveH)
                            }
                            context.startService(intent)
                            activeW = targetW
                            activeH = targetH
                            ThemeManager.setWidth(context, display.id, targetW)
                            ThemeManager.setHeight(context, display.id, targetH)
                        }
                    }
                }
                
                fun initiateResChange(targetW: Int, targetH: Int) {
                    if (display.id == 0) {
                        pendingResW = targetW
                        pendingResH = targetH
                        showPhoneResWarningDialog = true
                    } else {
                        triggerResChange(targetW, targetH)
                    }
                }
                
                val presetPercents = listOf(100, 75, 60, 50)
                
                SectionContainer(isExpressive = isExpressive, title = "Display Resolution (Size)") {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetPercents.forEach { pct ->
                            val targetW = (physicalW * pct) / 100
                            val targetH = (physicalH * pct) / 100
                            val isSelected = activeW == targetW && activeH == targetH
                            val label = if (pct == 100) "100% (Physical)" else "$pct% (${targetW}x${targetH})"
                            
                            OutlinedButton(
                                onClick = { initiateResChange(targetW, targetH) },
                                shape = if (isExpressive) CircleShape else ButtonDefaults.outlinedShape,
                                colors = if (isSelected) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                },
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                }
                            ) {
                                Text(label)
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { showCustomResDialog = true },
                            shape = if (isExpressive) CircleShape else ButtonDefaults.outlinedShape,
                            colors = ButtonDefaults.outlinedButtonColors(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Custom Size")
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Text("Current: ${activeW}x${activeH} Pixels (Physical: ${physicalW}x${physicalH} Pixels)", style = MaterialTheme.typography.bodyMedium)
                    Text("Reducing resolution scales system assets, increasing overlay smoothness, and lowering frame generation latency.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    
                    Button(
                        onClick = { initiateResChange(physicalW, physicalH) },
                        modifier = Modifier.padding(top = 8.dp),
                        shape = if (isExpressive) CircleShape else ButtonDefaults.shape,
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("Reset Resolution")
                    }
                }
                
                if (showCustomResDialog) {
                    var customW by remember { mutableStateOf("") }
                    var customH by remember { mutableStateOf("") }
                    val parsedW = customW.toIntOrNull()
                    val parsedH = customH.toIntOrNull()
                    val isValid = parsedW != null && parsedH != null && parsedW in 480..4000 && parsedH in 480..4000
                    
                    AlertDialog(
                        onDismissRequest = { showCustomResDialog = false },
                        title = { Text("Custom Resolution Override") },
                        text = {
                            Column {
                                Text("Enter a custom resolution WxH for display ${display.name}:")
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customW,
                                    onValueChange = { customW = it.filter { char -> char.isDigit() } },
                                    label = { Text("Width (480px - 4000px)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    isError = customW.isNotEmpty() && (parsedW == null || parsedW !in 480..4000),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customH,
                                    onValueChange = { customH = it.filter { char -> char.isDigit() } },
                                    label = { Text("Height (480px - 4000px)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    isError = customH.isNotEmpty() && (parsedH == null || parsedH !in 480..4000),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (isValid && parsedW != null && parsedH != null) {
                                        showCustomResDialog = false
                                        initiateResChange(parsedW, parsedH)
                                    }
                                },
                                enabled = isValid
                            ) {
                                Text("Apply")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomResDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                
                if (showPhoneResWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showPhoneResWarningDialog = false },
                        title = { Text("⚠️ High Risk Resolution Change") },
                        text = {
                            Text("Changing resolution on your phone's primary display can disrupt touch coordinate mappings, corrupt navigation bar buttons, or make the screen interface completely unusable.\n\nAre you sure you want to change to ${pendingResW ?: 0}x${pendingResH ?: 0}?")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showPhoneResWarningDialog = false
                                    if (pendingResW != null && pendingResH != null) {
                                        triggerResChange(pendingResW!!, pendingResH!!)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Continue")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPhoneResWarningDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
            
            // ─── Display Refresh Rate (FPS) ───
            if (isRefreshRateOverrideEnabled) {
                var activeFps by remember(selectedIdx) { mutableStateOf(ThemeManager.getRefreshRate(context, display.id, 60)) }
                var supportedModes by remember(selectedIdx) { mutableStateOf<List<android.view.Display.Mode>>(emptyList()) }
                
                LaunchedEffect(selectedIdx) {
                    withContext(Dispatchers.IO) {
                        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                        val d = dm.getDisplay(display.id)
                        if (d != null) {
                            val modes = d.supportedModes.toList().sortedByDescending { it.refreshRate }
                            val currentFps = d.mode.refreshRate.toInt()
                            withContext(Dispatchers.Main) {
                                supportedModes = modes
                                activeFps = currentFps
                            }
                        }
                    }
                }
                
                fun triggerRefreshChange(targetModeId: Int, targetFps: Int) {
                    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val d = dm.getDisplay(display.id)
                    val currentModeId = d?.mode?.modeId ?: -1
                    val currentFps = activeFps
                    
                    scope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            val intent = Intent(context, FreeformOverlayService::class.java).apply {
                                action = "ACTION_REFRESH_RATE_CONFIRMATION"
                                putExtra("displayId", display.id)
                                putExtra("targetModeId", targetModeId)
                                putExtra("targetFps", targetFps)
                                putExtra("originalModeId", currentModeId)
                                putExtra("originalFps", currentFps)
                            }
                            context.startService(intent)
                            activeFps = targetFps
                            ThemeManager.setRefreshRate(context, display.id, targetFps)
                        }
                    }
                }
                
                val uniqueFpss = remember(supportedModes) {
                    supportedModes.map { it.refreshRate.toInt() }.distinct().sorted()
                }
                
                SectionContainer(isExpressive = isExpressive, title = "Display Refresh Rate (FPS)") {
                    if (uniqueFpss.isEmpty()) {
                        Text("Standard refresh rate controls not supported on this display context.")
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uniqueFpss.forEach { fps ->
                                val isSelected = activeFps == fps
                                val label = "$fps Hz"
                                
                                OutlinedButton(
                                    onClick = {
                                        val targetMode = supportedModes.find { it.refreshRate.toInt() == fps }
                                        if (targetMode != null) {
                                            triggerRefreshChange(targetMode.modeId, fps)
                                        }
                                    },
                                    shape = if (isExpressive) CircleShape else ButtonDefaults.outlinedShape,
                                    colors = if (isSelected) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    }
                                ) {
                                    Text(label)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Text("Current: $activeFps Hz", style = MaterialTheme.typography.bodyMedium)
                        Text("Higher refresh rates increase UI responsiveness and reduce animation stuttering. Supported hardware modes are loaded directly from your display.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            
            if (showCustomDpiDialog) {
                var customDpiInput by remember { mutableStateOf("") }
                var allowUnsafe by remember { mutableStateOf(false) }
                val activeMinDpi = if (allowUnsafe) 100 else minAllowedDpi
                val parsedDpi = customDpiInput.toIntOrNull()
                val isValid = parsedDpi != null && parsedDpi in activeMinDpi..maxAllowedDpi
                
                AlertDialog(
                    onDismissRequest = { showCustomDpiDialog = false },
                    title = { Text("Custom DPI Input") },
                    text = {
                        Column {
                            Text("Enter a custom density for display ${display.name}:")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customDpiInput,
                                onValueChange = { customDpiInput = it.filter { char -> char.isDigit() } },
                                label = { Text("DPI Value (${activeMinDpi} - ${maxAllowedDpi})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = customDpiInput.isNotEmpty() && !isValid,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (customDpiInput.isNotEmpty() && !isValid) {
                                Text(
                                    text = "DPI must be between $activeMinDpi and $maxAllowedDpi",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { allowUnsafe = !allowUnsafe }
                            ) {
                                Checkbox(
                                    checked = allowUnsafe,
                                    onCheckedChange = { allowUnsafe = it }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Allow Unsafe Extreme DPI (Min 100)", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (allowUnsafe) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "⚠️ Warning: Setting DPI below 200 makes screen elements extremely tiny and can make touch input very difficult.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (isValid && parsedDpi != null) {
                                    showCustomDpiDialog = false
                                    initiateDpiChange(parsedDpi)
                                }
                            },
                            enabled = isValid
                        ) {
                            Text("Apply")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomDpiDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            if (showPhoneWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showPhoneWarningDialog = false },
                    title = { Text("⚠️ High Risk Display Change") },
                    text = {
                        Text("Changing display density (DPI) on your phone's built-in display can cause UI glitches, system crashes, or soft-bricking if set to an incompatible value.\n\nAre you sure you want to continue with ${pendingDpiChange ?: 0} DPI?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showPhoneWarningDialog = false
                                pendingDpiChange?.let { triggerDpiChange(it) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPhoneWarningDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
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
    
    val isExpressive = ThemeManager.getAppUiStyle(context) == 1
    
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "App Settings", 
            style = if (isExpressive) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text("Configure global settings, theme attributes, and advanced system features.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))

        SectionContainer(isExpressive = isExpressive, title = "Appearance") {
            var themeMode by remember { mutableStateOf(ThemeManager.getThemeMode(context)) }
            Column {
                Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "Light", "Dark").forEachIndexed { index, label ->
                        FilterChip(
                            selected = themeMode == index, 
                            onClick = { 
                                themeMode = index
                                ThemeManager.setThemeMode(context, index) 
                            }, 
                            label = { Text(label) },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                    }
                }
            }
        }

        SectionContainer(isExpressive = isExpressive, title = "System Behavior") {
            Column {
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
                
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )
                
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
                                    valueRange = 1f..250f,
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

        SectionContainer(isExpressive = isExpressive, title = "Experimental (May be unstable)") {
            Column {
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
                
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )
                
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
                
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )
                
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
                
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )
                
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
                
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )
                
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
                
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )
                
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
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        shape = if (isExpressive) RoundedCornerShape(20.dp) else CardDefaults.shape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpressive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        ),
                        border = if (isExpressive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null
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

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )

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
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        shape = if (isExpressive) RoundedCornerShape(20.dp) else CardDefaults.shape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpressive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        ),
                        border = if (isExpressive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null
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

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )

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
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        shape = if (isExpressive) RoundedCornerShape(20.dp) else CardDefaults.shape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpressive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        ),
                        border = if (isExpressive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null
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

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color
                )

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
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        shape = if (isExpressive) RoundedCornerShape(20.dp) else CardDefaults.shape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpressive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        ),
                        border = if (isExpressive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null
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
                                modifier = Modifier.align(Alignment.End),
                                shape = if (isExpressive) CircleShape else ButtonDefaults.shape
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
                                        TextButton(
                                            onClick = { showCustomLauncherPicker = false },
                                            shape = if (isExpressive) CircleShape else ButtonDefaults.textShape
                                        ) {
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

data class DeviceInfoSpec(
    val name: String,
    val ram: String,
    val cpu: String,
    val storage: String
)

private fun getDeviceInfoSpec(context: Context): DeviceInfoSpec {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }} $model"
    }

    var ramText = "Unknown RAM"
    try {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        ramText = String.format(java.util.Locale.US, "%.1f GB RAM", totalRamGb)
    } catch (e: Exception) {
        Log.e("DeviceInfo", "Error getting RAM info", e)
    }

    val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM64"
    val cpuCores = Runtime.getRuntime().availableProcessors()
    val cpuText = "$cpuCores Cores @ $cpuAbi"

    var storageText = "Unknown Storage"
    try {
        val path = android.os.Environment.getDataDirectory()
        val stat = android.os.StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        val totalStorageGb = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
        val availableStorageGb = (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
        storageText = String.format(java.util.Locale.US, "%.1f GB free of %.1f GB", availableStorageGb, totalStorageGb)
    } catch (e: Exception) {
        Log.e("DeviceInfo", "Error getting storage info", e)
    }

    return DeviceInfoSpec(deviceName, ramText, cpuText, storageText)
}

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

    val isExpressive = ThemeManager.getAppUiStyle(context) == 1

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Compatibility",
            style = if (isExpressive) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
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
        val deviceInfo = remember { getDeviceInfoSpec(context) }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isExpressive) 0.15f else 0.7f)
            ),
            shape = if (isExpressive) RoundedCornerShape(28.dp) else MaterialTheme.shapes.extraLarge,
            border = if (isExpressive) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)) else null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            deviceInfo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "$versionName (Android ${Build.VERSION.RELEASE}) • API $sdkInt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Memory", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Text(deviceInfo.ram, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SettingsSuggest, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Processor", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Text(deviceInfo.cpu, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Internal Storage", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Text(deviceInfo.storage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        val leashMinEnabled = fixStates[CompatibilityManager.COMPAT_HYBRID_LEASH_MINIMIZATION]?.value == true
        if (leashMinEnabled) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "Android 12 Compatibility Note",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Compositor Leash Minimization is active. After starting the service, please click or interact with any background desktop windows once to initialize and show their custom title bars.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
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

fun getAbsolutePathFromDocumentTreeUri(context: Context, uri: Uri): String? {
    try {
        val documentId = android.provider.DocumentsContract.getTreeDocumentId(uri) ?: return null
        val parts = documentId.split(":")
        if (parts.size >= 2) {
            val type = parts[0]
            val path = parts[1]
            if ("primary".equals(type, ignoreCase = true)) {
                return android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + path
            } else {
                return "/storage/" + type + "/" + path
            }
        } else if (parts.size == 1) {
            if ("primary".equals(parts[0], ignoreCase = true)) {
                return android.os.Environment.getExternalStorageDirectory().absolutePath
            }
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to parse document tree URI to path", e)
    }
    return null
}

data class CaptureMedia(
    val file: File? = null,
    val uri: Uri? = null,
    val name: String,
    val isVideo: Boolean,
    val lastModified: Long,
    val size: Long
)

@Composable
fun CaptureScreen(
    padding: PaddingValues,
    availableDisplays: List<DisplayInfo>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val prefs = remember { context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE) }
    
    val defaultSavePath = remember { 
        ScreenRecordManager.getDefaultSaveDirectory(context).absolutePath
    }
    
    var selectedDisplayId by remember { mutableStateOf(0) }
    var resolution by remember { mutableStateOf(prefs.getString("pref_screenrecord_resolution", "Native") ?: "Native") }
    var bitrate by remember { mutableStateOf(prefs.getInt("pref_screenrecord_bitrate", 8)) }
    var recordMic by remember { mutableStateOf(prefs.getBoolean("pref_screenrecord_mic", false)) }
    var refreshGalleryKey by remember { mutableStateOf(0) }
    val saveUriStr = remember(refreshGalleryKey) { prefs.getString("pref_screenrecord_save_uri", "") ?: "" }
    
    var customSaveDir by remember { mutableStateOf(prefs.getString("pref_screenrecord_save_dir", defaultSavePath) ?: defaultSavePath) }
    var capturePreset by remember { mutableStateOf(prefs.getString("pref_capture_preset", "high") ?: "high") }
    var controllerStyle by remember { mutableStateOf(prefs.getString("pref_controller_style", "obsidian") ?: "obsidian") }
    
    // Keyboard shortcuts mapping
    var recMod by remember { mutableStateOf(prefs.getString("pref_shortcut_record_mod", "Ctrl+Alt") ?: "Ctrl+Alt") }
    var recKey by remember { mutableStateOf(prefs.getString("pref_shortcut_record_key", "R") ?: "R") }
    
    var capMod by remember { mutableStateOf(prefs.getString("pref_shortcut_screenshot_mod", "Ctrl+Alt") ?: "Ctrl+Alt") }
    var capKey by remember { mutableStateOf(prefs.getString("pref_shortcut_screenshot_key", "S") ?: "S") }
    
    var cropMod by remember { mutableStateOf(prefs.getString("pref_shortcut_crop_mod", "Ctrl+Alt") ?: "Ctrl+Alt") }
    var cropKey by remember { mutableStateOf(prefs.getString("pref_shortcut_crop_key", "C") ?: "C") }
    
    var isControllerActive by remember { mutableStateOf(false) }
    var galleryFiles by remember { mutableStateOf<List<CaptureMedia>>(emptyList()) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(context, FreeformOverlayService::class.java).apply {
                action = "ACTION_START_CAPTURE_CONTROL"
                putExtra("displayId", selectedDisplayId)
            }
            context.startService(intent)
            isControllerActive = true
        } else {
            Toast.makeText(context, "Camera permission is required for Facecam features.", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Notification permission request on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Notifications are disabled. You will not receive capture alerts.", Toast.LENGTH_SHORT).show()
            }
        }
        LaunchedEffect(Unit) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(permission)
            }
        }
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                prefs.edit().putString("pref_screenrecord_save_uri", uri.toString()).apply()
                
                val rawPath = getAbsolutePathFromDocumentTreeUri(context, uri) ?: defaultSavePath
                customSaveDir = rawPath
                prefs.edit().putString("pref_screenrecord_save_dir", rawPath).apply()
                refreshGalleryKey++
            } catch (e: Exception) {
                Log.e("CaptureScreen", "Failed to persist folder URI", e)
                Toast.makeText(context, "Error selecting folder: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(customSaveDir, saveUriStr, refreshGalleryKey) {
        withContext(Dispatchers.IO) {
            val list = mutableListOf<CaptureMedia>()
            if (saveUriStr.isNotEmpty()) {
                try {
                    val treeUri = Uri.parse(saveUriStr)
                    val treeDocumentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
                    val projection = arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        android.provider.DocumentsContract.Document.COLUMN_SIZE,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    )
                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val modIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        val sizeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                        val mimeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                        
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(idIndex)
                            val name = cursor.getString(nameIndex) ?: ""
                            val lastMod = if (modIndex != -1) cursor.getLong(modIndex) else 0L
                            val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                            val mime = cursor.getString(mimeIndex) ?: ""
                            
                            if (name.endsWith(".mp4") || name.endsWith(".png") || name.endsWith(".jpg") || 
                                mime.startsWith("video/") || mime.startsWith("image/")) {
                                
                                val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                list.add(CaptureMedia(
                                    uri = docUri,
                                    name = name,
                                    isVideo = name.endsWith(".mp4") || mime.startsWith("video/"),
                                    lastModified = lastMod,
                                    size = size
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CaptureScreen", "Error query SAF documents", e)
                }
            }
            
            // If SAF list is empty, try raw file access (only works for app-private or if granted MANAGE_EXTERNAL_STORAGE)
            if (list.isEmpty()) {
                val dir = File(customSaveDir)
                if (dir.exists()) {
                    dir.listFiles { _, name -> 
                        name.endsWith(".mp4") || name.endsWith(".png") || name.endsWith(".jpg")
                    }?.forEach { file ->
                        list.add(CaptureMedia(
                            file = file,
                            name = file.name,
                            isVideo = file.name.endsWith(".mp4"),
                            lastModified = file.lastModified(),
                            size = file.length()
                        ))
                    }
                }
            }
            galleryFiles = list.sortedByDescending { it.lastModified }
        }
    }
    
    val isExpressive = ThemeManager.getAppUiStyle(context) == 1
    val cardShape = if (isExpressive) RoundedCornerShape(28.dp) else MaterialTheme.shapes.extraLarge
    val cardColors = if (isExpressive) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f))
    } else {
        CardDefaults.elevatedCardColors()
    }
    val cardBorder = if (isExpressive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else null

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(24.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Capture & Broadcast", 
            style = if (isExpressive) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displaySmall, 
            fontWeight = FontWeight.ExtraBold, 
            color = MaterialTheme.colorScheme.primary
        )
        Text("Record Desktop environments, crop screenshot regions, and manage captures.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        
        Spacer(Modifier.height(24.dp))
        
        // Card 1: Monitor / Display Selector
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Target Display Physical Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Select which physical display monitor you want to capture.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                
                availableDisplays.forEach { d ->
                    val isSelected = selectedDisplayId == d.id
                    val borderAccent = if (isSelected) MaterialTheme.colorScheme.primary else if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                    
                    OutlinedCard(
                        onClick = { selectedDisplayId = d.id },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderAccent),
                        shape = if (isExpressive) RoundedCornerShape(20.dp) else MaterialTheme.shapes.medium
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tv, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontWeight = FontWeight.Bold)
                                Text("ID: ${d.id} • Resolution: ${d.width}x${d.height} • Active DPI: ${d.activeDpi}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            } else {
                                val intent = Intent(context, FreeformOverlayService::class.java).apply {
                                    action = "ACTION_START_CAPTURE_CONTROL"
                                    putExtra("displayId", selectedDisplayId)
                                }
                                context.startService(intent)
                                isControllerActive = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Show Controller")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, FreeformOverlayService::class.java).apply {
                                action = "ACTION_STOP_CAPTURE_CONTROL"
                            }
                            context.startService(intent)
                            isControllerActive = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Hide Controller")
                    }
                }
                
            }
        }
        
        // Card 2: Capture Parameters & Save Settings
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Video Quality Preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Choose between optimized presets or unlock advanced custom parameters.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(
                        "high" to "High (Native)",
                        "medium" to "Medium (1080p)",
                        "low_size" to "Low Size (720p)",
                        "advanced" to "Custom (Adv)"
                    ).forEach { (presetVal, label) ->
                        val isSel = capturePreset == presetVal
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                capturePreset = presetVal
                                prefs.edit().putString("pref_capture_preset", presetVal).apply()
                            },
                            label = { Text(label) },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                    }
                }
                
                if (capturePreset == "advanced") {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = if (isExpressive) RoundedCornerShape(20.dp) else CardDefaults.shape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpressive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = if (isExpressive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Advanced Capture Parameters", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            
                            // Resolution Selector
                            Text("Record Resolution Override:", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("Native", "1080p", "720p", "480p").forEach { r ->
                                    val isSel = resolution == r
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            resolution = r
                                            prefs.edit().putString("pref_screenrecord_resolution", r).apply()
                                        },
                                        label = { Text(r) },
                                        shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            // Bitrate slider
                            Text("Video Target Bitrate: $bitrate Mbps", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = bitrate.toFloat(),
                                onValueChange = {
                                    bitrate = it.toInt()
                                    prefs.edit().putInt("pref_screenrecord_bitrate", it.toInt()).apply()
                                },
                                valueRange = 1f..24f,
                                steps = 23
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Mic voiceover
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Microphone Audio Sync", fontWeight = FontWeight.Bold)
                        Text("Record microphone voiceover inside a synced M4A file alongside.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(
                        checked = recordMic,
                        onCheckedChange = {
                            recordMic = it
                            prefs.edit().putBoolean("pref_screenrecord_mic", it).apply()
                        }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                Divider(color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color)
                Spacer(Modifier.height(16.dp))
                
                // Save path customizer
                Text("Storage Destination Directory:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customSaveDir,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        supportingText = {
                            if (saveUriStr.isNotEmpty()) {
                                Text("Selected via Android Folder Picker", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Using Default Package-scoped Directory", color = Color.Gray)
                            }
                        }
                    )
                    
                    Button(
                        onClick = { directoryPickerLauncher.launch(null) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        shape = if (isExpressive) CircleShape else ButtonDefaults.shape
                    ) {
                        Icon(Icons.Default.FolderOpen, "Choose Folder")
                        Spacer(Modifier.width(4.dp))
                        Text("Choose")
                    }
                    
                    IconButton(onClick = {
                        customSaveDir = defaultSavePath
                        prefs.edit().putString("pref_screenrecord_save_dir", defaultSavePath).apply()
                        prefs.edit().putString("pref_screenrecord_save_uri", "").apply()
                        refreshGalleryKey++
                    }) {
                        Icon(Icons.Default.Refresh, "Reset")
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Divider(color = if (isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else DividerDefaults.color)
                Spacer(Modifier.height(16.dp))
                
                // Precise Capture ID Configuration System
                var captureIdMode by remember { mutableStateOf(prefs.getString("pref_capture_id_mode", "auto") ?: "auto") }
                var physicalOverrideId by remember { mutableStateOf(prefs.getString("pref_physical_display_id_override", "") ?: "") }
                
                var detectedPhysicalId by remember(selectedDisplayId) { mutableStateOf<String?>(null) }
                LaunchedEffect(selectedDisplayId) {
                    withContext(Dispatchers.IO) {
                        try {
                            val res = ScreenRecordManager.getPhysicalDisplayId(context, selectedDisplayId)
                            detectedPhysicalId = res?.removePrefix("local:")
                        } catch (e: Exception) {
                            detectedPhysicalId = null
                        }
                    }
                }
                
                Text("Display Capture ID Mode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Control whether capture commands should use automatic resolution, raw sequential logical display IDs, or 64-bit physical IDs.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "auto" to "Auto-Resolve",
                        "logical" to "Logical ID Only",
                        "physical" to "Physical ID Only"
                    ).forEach { (modeVal, label) ->
                        val isSelected = captureIdMode == modeVal
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                captureIdMode = modeVal
                                prefs.edit().putString("pref_capture_id_mode", modeVal).apply()
                            },
                            label = { Text(label) },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Show real-time diagnostics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = if (isExpressive) RoundedCornerShape(20.dp) else CardDefaults.shape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpressive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = if (isExpressive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Active Target Display Diagnostics", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("• Selected Logical Display ID: $selectedDisplayId", style = MaterialTheme.typography.bodySmall)
                        Text("• Auto-Detected Physical Display ID: ${detectedPhysicalId ?: "None (Primary Display or resolution failed)"}", style = MaterialTheme.typography.bodySmall)
                        
                        val activeCommandId = when (captureIdMode) {
                            "logical" -> selectedDisplayId.toString()
                            "physical" -> if (physicalOverrideId.isNotEmpty()) physicalOverrideId else (detectedPhysicalId ?: selectedDisplayId.toString())
                            else -> if (physicalOverrideId.isNotEmpty()) physicalOverrideId else (detectedPhysicalId ?: selectedDisplayId.toString())
                        }
                        Text("• Active ID passed to screencap/screenrecord: $activeCommandId", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text("Physical Display ID Override", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Enter your monitor's 64-bit physical display ID (e.g. 4621070409437748996) if auto-detection fails or captures the incorrect screen.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = physicalOverrideId,
                    onValueChange = {
                        physicalOverrideId = it
                        prefs.edit().putString("pref_physical_display_id_override", it.trim()).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter 64-bit physical display ID (e.g. 4621070409437748996)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Sandbox Validation Testing
                var testScreenshotFile by remember { mutableStateOf<File?>(null) }
                var testScreenshotBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var isTestCapturing by remember { mutableStateOf(false) }
                var showTestConfirmDialog by remember { mutableStateOf(false) }
                
                LaunchedEffect(testScreenshotFile) {
                    val file = testScreenshotFile
                    if (file != null && file.exists() && file.length() > 0) {
                        withContext(Dispatchers.IO) {
                            try {
                                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                                withContext(Dispatchers.Main) {
                                    testScreenshotBitmap = bmp
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed decoding test screenshot bitmap", e)
                                withContext(Dispatchers.Main) {
                                    testScreenshotBitmap = null
                                }
                            }
                        }
                    } else {
                        testScreenshotBitmap = null
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isTestCapturing = true
                            testScreenshotFile = null
                            testScreenshotBitmap = null
                            ScreenRecordManager.takeScreenshot(context, selectedDisplayId) { file ->
                                isTestCapturing = false
                                testScreenshotFile = file
                                showTestConfirmDialog = true
                            }
                        },
                        enabled = !isTestCapturing,
                        modifier = Modifier.weight(1f),
                        shape = if (isExpressive) CircleShape else ButtonDefaults.shape
                    ) {
                        if (isTestCapturing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Capturing Test...")
                        } else {
                            Icon(Icons.Default.CameraAlt, "Test Screenshot")
                            Spacer(Modifier.width(8.dp))
                            Text("Test Capture & Validate Screen")
                        }
                    }
                }
                
                if (showTestConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showTestConfirmDialog = false },
                        title = { Text("Verify Display Capture Output") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val bitmap = testScreenshotBitmap
                                if (bitmap != null) {
                                    Text("Please check the captured screen preview below. Does it display your external monitor or fell back to your phone display?", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(12.dp))
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color.LightGray)
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Test Capture Preview",
                                            modifier = Modifier.fillMaxWidth().height(180.dp).padding(4.dp)
                                        )
                                    }
                                } else {
                                    if (isTestCapturing || testScreenshotFile != null) {
                                        Text("Decoding preview image...", color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text("Screenshot failed! No file was created or the file size is 0 bytes.\n\nThis typically means that the display ID passed is invalid, or the system rejected the screencap option.", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showTestConfirmDialog = false },
                                shape = if (isExpressive) CircleShape else ButtonDefaults.shape
                            ) {
                                Text("Looks Correct! ✅")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = {
                                    prefs.edit().putString("pref_capture_id_mode", "physical").apply()
                                    captureIdMode = "physical"
                                    showTestConfirmDialog = false
                                    Toast.makeText(context, "Switched to 'Physical ID Only'. Please make sure your 64-bit ID is entered above.", Toast.LENGTH_LONG).show()
                                },
                                shape = if (isExpressive) CircleShape else ButtonDefaults.outlinedShape
                            ) {
                                Text("Incorrect (Phone Display) ❌", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
        
        // Card 2.5: Floating Controller Visual Theme Style
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Floating Controller Visual Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Choose a theme layout for the floating screen record controller pill overlay.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(
                        "obsidian" to "Obsidian (Classic)",
                        "liquid_glass" to "Liquid Glass (Premium)",
                        "solid_minimal" to "Minimal Solid (CPU Save)"
                    ).forEach { (styleVal, label) ->
                        val isSel = controllerStyle == styleVal
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                controllerStyle = styleVal
                                prefs.edit().putString("pref_controller_style", styleVal).apply()
                            },
                            label = { Text(label) },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                val description = when (controllerStyle) {
                    "liquid_glass" -> "Liquid Glass: Dynamic 3D glossy caustics, dual refracting light outlines, and deep concave button shadows."
                    "solid_minimal" -> "Minimal Solid: A flat solid Material design color scheme. Highly CPU efficient and clean, optimal for low-end devices."
                    else -> "Obsidian Glass: Classic deep obsidian semi-transparent layout with soft violet accent highlights."
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        // Card 3: Keyboard Shortcuts Config
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Hardware Keyboard Shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Configure global hotkeys inside freeform layouts or the main dashboard.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                
                ShortcutRow("Toggle Screen Recording", recMod, recKey, { m -> recMod = m; prefs.edit().putString("pref_shortcut_record_mod", m).apply() }, { k -> recKey = k; prefs.edit().putString("pref_shortcut_record_key", k).apply() })
                Spacer(Modifier.height(12.dp))
                ShortcutRow("Capture Full Screenshot", capMod, capKey, { m -> capMod = m; prefs.edit().putString("pref_shortcut_screenshot_mod", m).apply() }, { k -> capKey = k; prefs.edit().putString("pref_shortcut_screenshot_key", k).apply() })
                Spacer(Modifier.height(12.dp))
                ShortcutRow("Trigger Regional Crop", cropMod, cropKey, { m -> cropMod = m; prefs.edit().putString("pref_shortcut_crop_mod", m).apply() }, { k -> cropKey = k; prefs.edit().putString("pref_shortcut_crop_key", k).apply() })
            }
        }
        
        // Card 4: Media bento grid gallery
        Text("Media Gallery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
        
        if (galleryFiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = cardShape,
                colors = cardColors,
                border = cardBorder
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Camera, null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Gallery is Empty", fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("Your recordings and cropped screens will show up here.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                galleryFiles.chunked(2).forEach { rowFiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        rowFiles.forEach { item ->
                            Card(
                                modifier = Modifier.weight(1f).height(180.dp),
                                shape = if (isExpressive) RoundedCornerShape(20.dp) else MaterialTheme.shapes.large,
                                border = if (isExpressive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null,
                                colors = if (isExpressive) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)) else CardDefaults.cardColors()
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    ThumbnailImage(item)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(item.name.substringAfterLast("_"), color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                                val sizeMb = item.size / (1024f * 1024f)
                                                Text(String.format("%.2f MB", sizeMb), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                            }
                                            
                                            Row {
                                                IconButton(
                                                    onClick = { playOrViewMedia(context, item) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = { shareMedia(context, item) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                if (item.file != null) {
                                                                    ShellExecutor.executeCommand("rm \"${item.file.absolutePath}\"")
                                                                    if (item.name.endsWith(".mp4")) {
                                                                        val audioPath = item.file.absolutePath.replace(".mp4", ".m4a").replace("record_", "voiceover_")
                                                                        ShellExecutor.executeCommand("rm \"$audioPath\"")
                                                                    }
                                                                } else if (item.uri != null) {
                                                                    android.provider.DocumentsContract.deleteDocument(context.contentResolver, item.uri)
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("MainActivity", "Failed to delete document", e)
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                refreshGalleryKey++
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (rowFiles.size == 1) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutRow(
    label: String,
    modifierStr: String,
    keyChar: String,
    onModifierChange: (String) -> Unit,
    onKeyChange: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        
        var showModMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { showModMenu = true }, shape = CircleShape) {
                Text(modifierStr)
            }
            DropdownMenu(expanded = showModMenu, onDismissRequest = { showModMenu = false }) {
                listOf("Ctrl+Alt", "Ctrl+Shift", "Alt+Shift").forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = {
                        onModifierChange(m)
                        showModMenu = false
                    })
                }
            }
        }
        
        Spacer(Modifier.width(8.dp))
        
        var showKeyMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { showKeyMenu = true }, shape = CircleShape) {
                Text(keyChar)
            }
            DropdownMenu(expanded = showKeyMenu, onDismissRequest = { showKeyMenu = false }) {
                listOf("R", "S", "C", "A", "P", "F").forEach { k ->
                    DropdownMenuItem(text = { Text(k) }, onClick = {
                        onKeyChange(k)
                        showKeyMenu = false
                    })
                }
            }
        }
    }
}

@Composable
fun ThumbnailImage(item: CaptureMedia) {
    var bitmap by remember(item) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(item) {
        withContext(Dispatchers.IO) {
            try {
                if (item.isVideo) {
                    if (item.file != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bitmap = ThumbnailUtils.createVideoThumbnail(item.file, Size(512, 384), null)
                        } else {
                            bitmap = ThumbnailUtils.createVideoThumbnail(item.file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
                        }
                    } else if (item.uri != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bitmap = context.contentResolver.loadThumbnail(item.uri, Size(512, 384), null)
                        }
                    }
                } else {
                    if (item.file != null) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                        bitmap = android.graphics.BitmapFactory.decodeFile(item.file.absolutePath, opts)
                    } else if (item.uri != null) {
                        context.contentResolver.openInputStream(item.uri)?.use { 
                            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                            bitmap = android.graphics.BitmapFactory.decodeStream(it, null, opts)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Thumbnail generation failed", e)
            }
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(), 
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Icon(if (item.isVideo) Icons.Default.Videocam else Icons.Default.Image, null, tint = Color.LightGray, modifier = Modifier.size(36.dp))
        }
    }
}

private fun playOrViewMedia(context: Context, item: CaptureMedia) {
    try {
        val uri = if (item.uri != null) {
            item.uri
        } else if (item.file != null) {
            val authority = "${context.packageName}.fileprovider"
            androidx.core.content.FileProvider.getUriForFile(context, authority, item.file)
        } else return
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (item.isVideo) "video/mp4" else "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newRawUri(null, uri)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMedia(context: Context, item: CaptureMedia) {
    try {
        val uri = if (item.uri != null) {
            item.uri
        } else if (item.file != null) {
            val authority = "${context.packageName}.fileprovider"
            androidx.core.content.FileProvider.getUriForFile(context, authority, item.file)
        } else return
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/mp4" else "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(null, uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share Capture"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
