package com.example.freeformshell

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import rikka.shizuku.Shizuku
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSettingsScreen(
    padding: PaddingValues,
    displays: List<DisplayInfo>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIdx by remember { mutableStateOf(0) }

    // --- State & Callbacks for Live Mirroring ---
    var isMirroring by remember { mutableStateOf(PhoneMirrorManager.isMirroring) }

    // Query physical metrics of primary display (Display 0) to align buffer size & aspect ratio dynamically
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
    val primaryDisplay = dm.getDisplay(0)
    val realMetrics = android.util.DisplayMetrics()
    primaryDisplay?.getRealMetrics(realMetrics)
    val primaryWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else 1080
    val primaryHeight = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else 2400
    val primaryAspectRatio = primaryWidth.toFloat() / primaryHeight.toFloat()

    DisposableEffect(Unit) {
        val cb = { state: Boolean -> isMirroring = state }
        PhoneMirrorManager.registerCallback(cb)
        onDispose { PhoneMirrorManager.unregisterCallback(cb) }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // --- Sleek Premium Header ---
        Text(
            text = "Desktop Environment Hub",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Configure programmatic Developer-less Desktop Mode, adjust display resolution snap bounds, and review connected monitors diagnostics natively.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))

        // --- SECTION: Interactive Phone Screen Mirroring ---
        Text(
            text = "Mirror (${MirrorShortcutHelper.getFriendlyDeviceName(Build.MODEL)})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mirror (${MirrorShortcutHelper.getFriendlyDeviceName(Build.MODEL)}) Window",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Pipes the primary device viewport directly into a draggable and resizable floating system overlay window. Fully interactive with gesture mappings and remote hardware keys.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time status display row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulse dot indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isMirroring) Color(0xFF34C759) else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMirroring) "Status: Mirror Window is Active" else "Status: Mirror Window is Closed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isMirroring) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isMirroring) {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.stopService(Intent(context, PhoneMirrorOverlayService::class.java))
                            } catch (e: Exception) {
                                Log.e("DesktopSettingsScreen", "Failed to stop PhoneMirrorOverlayService", e)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Close Floating Mirror Window", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (Shizuku.pingBinder()) {
                                try {
                                    val intent = Intent(context, PhoneMirrorOverlayService::class.java).apply {
                                        val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            try {
                                                context.display?.displayId ?: 0
                                            } catch (e: Exception) {
                                                0
                                            }
                                        } else {
                                            0
                                        }
                                        putExtra("EXTRA_DISPLAY_ID", displayId)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                } catch (e: Exception) {
                                    Log.e("DesktopSettingsScreen", "Failed to start PhoneMirrorOverlayService", e)
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Shizuku is not running! Please authorize and start Shizuku.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Dvr, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Mirror Window", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- Active Display Select tab row ---
        if (displays.size > 1) {
            Text(
                text = "Select Connected Display",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TvOff, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No connected monitors detected.", color = Color.Gray)
                }
            }
        } else {
            val display = displays.getOrNull(selectedIdx) ?: displays[0]
            var snapSensitivity by remember(display.id) { mutableStateOf(ThemeManager.getSnapSensitivity(context, display.id).toFloat()) }
            var dockSize by remember(display.id) {
                val saved = ThemeManager.getDockSize(context, display.id)
                mutableStateOf(if (saved == 0) 48f else saved.toFloat())
            }
            var dockPosition by remember(display.id) {
                mutableStateOf(ThemeManager.getDockPosition(context, display.id))
            }

            // --- Vector Monitor Canvas Bezel Representation ---
            Text(
                text = "Workspace Visualizer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
            
            MonitorView(
                display = display,
                snapSensitivity = snapSensitivity,
                dockPosition = dockPosition,
                dockSize = dockSize
            )

            Spacer(Modifier.height(24.dp))

            // --- Display Diagnostics Block ---
            Text(
                text = "Display Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            var physicalPort by remember(display.id) { mutableStateOf("SF Port parsing...") }

            LaunchedEffect(display.id) {
                withContext(Dispatchers.IO) {
                    val dumpsys = ShellExecutor.exec("dumpsys display")
                    val regex = "logicalId=${display.id}.*?physicalPort=(\\d+)".toRegex(RegexOption.IGNORE_CASE)
                    val match = regex.find(dumpsys.replace("\n", " "))
                    val port = match?.groupValues?.get(1) ?: run {
                        val regex2 = "displayId=${display.id}.*?port=(\\d+)".toRegex(RegexOption.IGNORE_CASE)
                        val match2 = regex2.find(dumpsys.replace("\n", " "))
                        match2?.groupValues?.get(1)
                    }

                    physicalPort = if (port != null) {
                        "SF Port $port"
                    } else {
                        if (display.id == 0) "SF Port 0 (Internal)" else "SF Port ${display.id + 1} (HDMI/DP)"
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DiagnosticItem(label = "Logical Display ID", value = display.id.toString(), icon = Icons.Default.Fingerprint)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticItem(label = "Physical Port Mapping", value = physicalPort, icon = Icons.Default.Cable)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticItem(label = "Active Grid Bounds", value = "${display.width} x ${display.height} px", icon = Icons.Default.Grid4x4)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticItem(label = "Display Pixel Density", value = "${display.dpi} DPI (Active: ${display.activeDpi} DPI)", icon = Icons.Default.Texture)
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Display Scoped Snap Sensitivity Slider ---
            Text(
                text = "Window Snapping Sensitivity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Adjust the boundary gap distance at which floating app windows automatically lock/snap to external screen edges.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Snap Range", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${snapSensitivity.toInt()} dp",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Slider(
                        value = snapSensitivity,
                        onValueChange = {
                            snapSensitivity = it
                            ThemeManager.setSnapSensitivity(context, display.id, it.toInt())
                            try {
                                FreeformOverlayService.showSensitivityGuide(display.id, it.toInt())
                            } catch (e: Exception) {
                                Log.e("DesktopSettingsScreen", "Failed to show sensitivity guide", e)
                            }
                        },
                        onValueChangeFinished = {
                            try {
                                FreeformOverlayService.hideSensitivityGuide()
                            } catch (e: Exception) {
                                Log.e("DesktopSettingsScreen", "Failed to hide sensitivity guide", e)
                            }
                        },
                        valueRange = 1f..250f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 dp (Tight)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("250 dp (Generous)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Display Scoped Dock Configuration ---
            Text(
                text = "System Dock Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Adjust the system panel dock positioning and safe-area margin bounds allocated per active display.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Position selector
                    Text(
                        text = "Dock Position",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val positions = listOf(
                            "None" to 0,
                            "Left" to 3,
                            "Top" to 1,
                            "Right" to 4,
                            "Bottom" to 2
                        )
                        positions.forEach { (label, value) ->
                            val selected = dockPosition == value
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    dockPosition = value
                                    ThemeManager.setDockPosition(context, display.id, value)
                                    try {
                                        FreeformOverlayService.showDockGuide(display.id, value, dockSize.toInt())
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            FreeformOverlayService.hideDockGuide()
                                        }, 1500)
                                    } catch (e: Exception) {
                                        Log.e("DesktopSettingsScreen", "Failed to show dock guide", e)
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dock Size & Margin Bounds", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${dockSize.toInt()} px",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Slider(
                        value = dockSize,
                        onValueChange = {
                            dockSize = it
                            ThemeManager.setDockSize(context, display.id, it.toInt())
                            try {
                                FreeformOverlayService.showDockGuide(display.id, dockPosition, it.toInt())
                            } catch (e: Exception) {
                                Log.e("DesktopSettingsScreen", "Failed to show dock guide", e)
                            }
                        },
                        onValueChangeFinished = {
                            try {
                                FreeformOverlayService.hideDockGuide()
                            } catch (e: Exception) {
                                Log.e("DesktopSettingsScreen", "Failed to hide dock guide", e)
                            }
                        },
                        valueRange = 0f..1000f,
                        enabled = dockPosition != 0 // Disable slider if Dock is set to None
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0 px (Compact)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("1000 px (Prominent)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

enum class WallpaperLoadStatus {
    LOADING,
    SUCCESS,
    FAILED
}

@Composable
fun MonitorView(
    display: DisplayInfo,
    snapSensitivity: Float,
    dockPosition: Int,
    dockSize: Float
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewBitmap by remember(display.id) { mutableStateOf<ImageBitmap?>(null) }
    var isScreenshotLoaded by remember(display.id) { mutableStateOf(false) }
    var triggerReload by remember { mutableStateOf(0) }
    var wallpaperLoadStatus by remember(display.id) { mutableStateOf(WallpaperLoadStatus.LOADING) }
    var pendingWallpaperUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            pendingWallpaperUri = uri
        }
    }

    if (pendingWallpaperUri != null) {
        AlertDialog(
            onDismissRequest = { pendingWallpaperUri = null },
            title = {
                Text(
                    text = "Apply Wallpaper Options",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = "Choose whether to update your Android system home screen wallpaper along with the local Desktop visualizer preview, or only update the visualizer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingWallpaperUri
                        pendingWallpaperUri = null
                        if (uri != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        val targetFile = File(context.filesDir, "custom_visualizer_wallpaper.png")
                                        if (targetFile.exists()) targetFile.delete()
                                        targetFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                        
                                        val bmp = BitmapFactory.decodeFile(targetFile.absolutePath)
                                        if (bmp != null) {
                                            try {
                                                val wm = WallpaperManager.getInstance(context)
                                                wm.setBitmap(bmp)
                                            } catch (se: Exception) {
                                                Log.e("DesktopSettingsScreen", "Failed to apply system wallpaper", se)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "System block: Applied only to visualizer", Toast.LENGTH_LONG).show()
                                                }
                                            }

                                            withContext(Dispatchers.Main) {
                                                previewBitmap = bmp.asImageBitmap()
                                                isScreenshotLoaded = true
                                                wallpaperLoadStatus = WallpaperLoadStatus.SUCCESS
                                                triggerReload++
                                                Toast.makeText(context, "Applied to System & Visualizer!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("DesktopSettingsScreen", "Failed to process wallpaper", e)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Wallpaper, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply to Both", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val uri = pendingWallpaperUri
                        pendingWallpaperUri = null
                        if (uri != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        val targetFile = File(context.filesDir, "custom_visualizer_wallpaper.png")
                                        if (targetFile.exists()) targetFile.delete()
                                        targetFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                        
                                        val bmp = BitmapFactory.decodeFile(targetFile.absolutePath)
                                        if (bmp != null) {
                                            withContext(Dispatchers.Main) {
                                                previewBitmap = bmp.asImageBitmap()
                                                isScreenshotLoaded = true
                                                wallpaperLoadStatus = WallpaperLoadStatus.SUCCESS
                                                triggerReload++
                                                Toast.makeText(context, "Applied to Visualizer Preview only", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("DesktopSettingsScreen", "Failed to process wallpaper", e)
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Visualizer Only", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    LaunchedEffect(display.id, triggerReload) {
        withContext(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                wallpaperLoadStatus = WallpaperLoadStatus.LOADING
            }

            // 0. Attempt to load persistent custom wallpaper chosen by the user
            val customWallpaperFile = File(context.filesDir, "custom_visualizer_wallpaper.png")
            if (customWallpaperFile.exists() && customWallpaperFile.length() > 0) {
                try {
                    val bmp = BitmapFactory.decodeFile(customWallpaperFile.absolutePath)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            previewBitmap = bmp.asImageBitmap()
                            isScreenshotLoaded = true
                            wallpaperLoadStatus = WallpaperLoadStatus.SUCCESS
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DesktopSettingsScreen", "Failed to decode custom wallpaper file", e)
                }
            }

            // 1. If no custom wallpaper, attempt to fetch actual system wallpaper via Shizuku
            if (!isScreenshotLoaded) {
                val wallpaperCopyFile = File(context.cacheDir, "system_wallpaper_copy.png")
                if (wallpaperCopyFile.exists()) wallpaperCopyFile.delete()
                
                val shizukuRes = ShellExecutor.executeCommandWithResult("cp /data/system/users/0/wallpaper ${wallpaperCopyFile.absolutePath}")
                if (shizukuRes.third == 0 && wallpaperCopyFile.exists() && wallpaperCopyFile.length() > 0) {
                    try {
                        val bmp = BitmapFactory.decodeFile(wallpaperCopyFile.absolutePath)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                previewBitmap = bmp.asImageBitmap()
                                isScreenshotLoaded = true
                                wallpaperLoadStatus = WallpaperLoadStatus.SUCCESS
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DesktopSettingsScreen", "Failed to decode system wallpaper copy via Shizuku", e)
                    }
                }
            }

            // 2. Fall back to standard WallpaperManager if Shizuku cp fails
            if (!isScreenshotLoaded) {
                try {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val drawable = wallpaperManager.drawable
                    val bmp = drawable?.toBitmap(width = 800, height = 480, config = Bitmap.Config.ARGB_8888)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            previewBitmap = bmp.asImageBitmap()
                            isScreenshotLoaded = true
                            wallpaperLoadStatus = WallpaperLoadStatus.SUCCESS
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DesktopSettingsScreen", "Failed to retrieve wallpaper fallback via WallpaperManager", e)
                }
            }
            
            // 3. Now attempt Screencap under Shizuku process to capture active workspace display
            if (!isScreenshotLoaded) {
                val cacheFile = File(context.cacheDir, "display_preview_${display.id}.png")
                if (cacheFile.exists()) cacheFile.delete()

                val res = ShellExecutor.executeCommandWithResult("screencap -d ${display.id} ${cacheFile.absolutePath}")
                if (res.third == 0 && cacheFile.exists() && cacheFile.length() > 0) {
                    try {
                        val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                previewBitmap = bmp.asImageBitmap()
                                isScreenshotLoaded = true
                                wallpaperLoadStatus = WallpaperLoadStatus.SUCCESS
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DesktopSettingsScreen", "Failed to decode screenshot bitmap", e)
                    }
                }
            }

            // If still not loaded, set status to FAILED
            if (!isScreenshotLoaded) {
                withContext(Dispatchers.Main) {
                    wallpaperLoadStatus = WallpaperLoadStatus.FAILED
                }
            }
        }
    }

    val isPhone = display.id == 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Beautiful Top Row above Visualizer to show Display label and Clean Edit button ---
        Row(
            modifier = Modifier
                .width(if (isPhone) 260.dp else 416.dp) // Avoid wrapping/clipping on narrow phone mockup
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPhone) Icons.Default.PhoneAndroid else Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPhone) "Built-in Screen" else "Secondary Display (${display.name})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (isScreenshotLoaded) {
                TextButton(
                    onClick = {
                        try {
                            imagePickerLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Log.e("DesktopSettingsScreen", "Failed to launch content picker", e)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isPhone) {
            // --- Portrait Phone Bezel Display Frame ---
            Box(
                modifier = Modifier
                    .height(350.dp)
                    .aspectRatio(9f / 19.5f)
                    .shadow(16.dp, shape = RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(6.dp, Color(0xFF2C2C2C), RoundedCornerShape(28.dp))
                    .border(8.dp, Color(0xFF121212), RoundedCornerShape(28.dp))
            ) {
                if (isScreenshotLoaded && previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!,
                        contentDescription = "Desktop Workspace Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DesktopWallpaperPlaceholder(isPhone = true)
                        
                        // Prominent "Set Custom Wallpaper" Button in Center
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable {
                                    try {
                                        imagePickerLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        Log.e("DesktopSettingsScreen", "Failed to launch content picker", e)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Set Custom Wallpaper",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Glossy reflections glass overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.2f))
                            )
                        )
                )

                // Snapping Visualizer Overlay
                SnappingOverlayView(snapSensitivity = snapSensitivity, isPhone = true)

                // Bottom Home Gesture Bar Mockup
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        } else {
            // --- Monitor Bezel Display Frame ---
            Box(
                modifier = Modifier
                    .height(260.dp) // Balanced height to match built-in screen visual weight
                    .aspectRatio(16f / 10f)
                    .shadow(16.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(6.dp, Color(0xFF2C2C2C), RoundedCornerShape(16.dp))
                    .border(8.dp, Color(0xFF121212), RoundedCornerShape(16.dp))
            ) {
                if (isScreenshotLoaded && previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!,
                        contentDescription = "Desktop Workspace Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DesktopWallpaperPlaceholder(isPhone = false)
                        
                        // Prominent "Set Custom Wallpaper" Button in Center
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable {
                                    try {
                                        imagePickerLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        Log.e("DesktopSettingsScreen", "Failed to launch content picker", e)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Set Custom Wallpaper",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Glossy reflections glass overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.2f))
                            )
                        )
                )

                // Snapping Visualizer Overlay
                SnappingOverlayView(snapSensitivity = snapSensitivity, isPhone = false)
            }

            // --- Monitor Stand Bevel ---
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(36.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF3E3E3E), Color(0xFF242424))
                        )
                    )
            )

            // --- Monitor Desktop Base ---
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(10.dp)
                    .shadow(4.dp, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF5E5E5E), Color(0xFF2C2C2C))
                        )
                    )
            )
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = wallpaperLoadStatus == WallpaperLoadStatus.FAILED) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wallpaper Auto-Fetch Failed",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "We couldn't retrieve your device wallpaper automatically due to Android API security locks. Please select and add your own wallpaper manually to view a realistic workspace preview.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try {
                                    imagePickerLauncher.launch("image/*")
                                } catch (e: Exception) {
                                    Log.e("DesktopSettingsScreen", "Failed to launch content picker", e)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select Custom Wallpaper",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopWallpaperPlaceholder(isPhone: Boolean) {
    val startColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val endColor = MaterialTheme.colorScheme.background
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(startColor, endColor)
                )
            )
    )
}

@Composable
fun SnappingOverlayView(snapSensitivity: Float, isPhone: Boolean) {
    val thickness = (snapSensitivity / 12f).dp
    val shape = if (isPhone) RoundedCornerShape(22.dp) else RoundedCornerShape(10.dp)
    val accentColor = MaterialTheme.colorScheme.primary
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp) // Inset slightly inside the screen bezel border
    ) {
        // 1. Semi-translucent primary-accented glowing edge bands
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = thickness,
                    color = accentColor.copy(alpha = 0.15f),
                    shape = shape
                )
        )
        
        // 2. Inner thin guideline threshold
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(thickness)
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.6f),
                    shape = shape
                )
        )

        // 3. Highlight corner-snapping zones
        val cornerShape = RoundedCornerShape(if (isPhone) 6.dp else 4.dp)
        
        // Top Left Corner
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(thickness)
                .background(accentColor.copy(alpha = 0.35f), cornerShape)
        )
        // Top Right Corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(thickness)
                .background(accentColor.copy(alpha = 0.35f), cornerShape)
        )
        // Bottom Left Corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(thickness)
                .background(accentColor.copy(alpha = 0.35f), cornerShape)
        )
        // Bottom Right Corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(thickness)
                .background(accentColor.copy(alpha = 0.35f), cornerShape)
        )
    }
}

@Composable
fun DockOverlayView(dockPosition: Int, dockSize: Float, isPhone: Boolean) {
    if (dockPosition == 0) return // None

    val accentColor = MaterialTheme.colorScheme.primary
    
    // Scale thickness of dock visually
    val visualThickness = (dockSize / 8f).dp
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp) // Inset inside the bezel slightly
    ) {
        val dockModifier = when (dockPosition) {
            1 -> Modifier // Top
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.55f)
                .height(visualThickness)
                .background(accentColor.copy(alpha = 0.85f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            
            2 -> Modifier // Bottom
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.55f)
                .height(visualThickness)
                .background(accentColor.copy(alpha = 0.85f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            
            3 -> Modifier // Left
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.55f)
                .width(visualThickness)
                .background(accentColor.copy(alpha = 0.85f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            
            4 -> Modifier // Right
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.55f)
                .width(visualThickness)
                .background(accentColor.copy(alpha = 0.85f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            
            else -> Modifier
        }

        // Draw Dock safe-area margin boundary guide line
        val marginGuideModifier = when (dockPosition) {
            1 -> Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = visualThickness)
                .height(1.dp)
                .background(accentColor.copy(alpha = 0.4f))
            
            2 -> Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = visualThickness)
                .height(1.dp)
                .background(accentColor.copy(alpha = 0.4f))
            
            3 -> Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = visualThickness)
                .width(1.dp)
                .background(accentColor.copy(alpha = 0.4f))
            
            4 -> Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = visualThickness)
                .width(1.dp)
                .background(accentColor.copy(alpha = 0.4f))
            
            else -> Modifier
        }

        // Draw the safe area guide line
        Box(modifier = marginGuideModifier)

        // Render the Dock bar with mock app icon dots inside!
        Box(
            modifier = dockModifier.padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            val isHorizontal = dockPosition == 1 || dockPosition == 2
            if (isHorizontal) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size((visualThickness * 0.5f).coerceAtLeast(3.dp))
                                .background(Color.White.copy(alpha = 0.9f), CircleShape)
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .size((visualThickness * 0.5f).coerceAtLeast(3.dp))
                                .background(Color.White.copy(alpha = 0.9f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
