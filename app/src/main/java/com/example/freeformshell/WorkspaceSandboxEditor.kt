package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSandboxEditor(
    group: WorkspaceGroup,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isExpressive = ThemeManager.getAppUiStyle(context) == 1

    // Mutable list copy of apps to track sandbox changes
    var apps by remember { mutableStateOf(group.apps.toMutableList()) }
    var selectedIndex by remember { mutableStateOf(-1) }
    
    // Snapping guidelines states
    var activeSnapLeftLine by remember { mutableStateOf<Float?>(null) }
    var activeSnapTopLine by remember { mutableStateOf<Float?>(null) }
    var activeSnapRightLine by remember { mutableStateOf<Float?>(null) }
    var activeSnapBottomLine by remember { mutableStateOf<Float?>(null) }
    
    // Installed applications list for package selector dialog
    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingApps by remember { mutableStateOf(false) }

    // Real display dimensions (Dynamic Screen bounds)
    val realDisplayBounds = remember(group.displayId) {
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = dm.getDisplay(group.displayId) ?: dm.getDisplay(0)
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            val w = if (metrics.widthPixels > 0) metrics.widthPixels else (if (group.displayId == 0) 1080 else 1920)
            val h = if (metrics.heightPixels > 0) metrics.heightPixels else (if (group.displayId == 0) 2400 else 1080)
            android.graphics.Point(w, h)
        } catch (e: Exception) {
            android.graphics.Point(if (group.displayId == 0) 1080 else 1920, if (group.displayId == 0) 2400 else 1080)
        }
    }
    val screenWidth = realDisplayBounds.x
    val screenHeight = realDisplayBounds.y
    val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

    // Load installed apps when app picker needs to show
    LaunchedEffect(showAppPicker) {
        if (showAppPicker && installedApps.isEmpty()) {
            isLoadingApps = true
            scope.launch(Dispatchers.IO) {
                val pm = context.packageManager
                val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .sortedBy { it.loadLabel(pm).toString() }
                withContext(Dispatchers.Main) {
                    installedApps = list
                    isLoadingApps = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspace Sandbox Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Direct Launch / Test Button
                    IconButton(
                        onClick = {
                            try {
                                val updatedGroup = WorkspaceGroup(apps, group.displayId, group.timestamp)
                                val groupJson = WorkspaceManager.groupToJson(updatedGroup).toString()
                                val serviceIntent = Intent(context, FreeformOverlayService::class.java).apply {
                                    action = "ACTION_LAUNCH_WORKSPACE"
                                    putExtra("EXTRA_GROUP_JSON", groupJson)
                                    putExtra("EXTRA_TARGET_DISPLAY", updatedGroup.displayId)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                                Toast.makeText(context, "Launching Saved Workspace...", Toast.LENGTH_SHORT).show()
                                onBack()
                                (context as? android.app.Activity)?.moveTaskToBack(true)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to restore workspace: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, "Launch Workspace", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Pin dynamically to Home Screen launcher
                    IconButton(
                        onClick = {
                            try {
                                val updatedGroup = WorkspaceGroup(apps, group.displayId, group.timestamp)
                                WorkspaceShortcutHelper.pinWorkspaceShortcut(context, updatedGroup)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to pin shortcut: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.AddHome, "Pin to Home Screen")
                    }

                    // Save Button
                    Button(
                        onClick = {
                            // Save updated group back to database
                            val updatedGroup = WorkspaceGroup(apps, group.displayId, group.timestamp)
                            if (isFavorite) {
                                WorkspaceManager.setFavorite(context, updatedGroup)
                            } else {
                                val history = WorkspaceManager.getHistory(context).toMutableList()
                                val index = history.indexOfFirst { it.timestamp == group.timestamp }
                                if (index != -1) {
                                    history[index] = updatedGroup
                                } else {
                                    history.add(0, updatedGroup)
                                }
                                WorkspaceManager.saveHistory(context, history)
                            }
                            Toast.makeText(context, "Workspace saved successfully!", Toast.LENGTH_SHORT).show()
                            onRefresh()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        },
        floatingActionButton = {
            // Add App FAB
            FloatingActionButton(
                onClick = {
                    selectedIndex = -1
                    showAppPicker = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, "Add App Block")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (group.displayId == 0) "Phone Screen Grid ($screenWidth x $screenHeight)" else "Display Grid ($screenWidth x $screenHeight)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Drag, resize, or tap window cards inside the visual canvas sandbox to edit your layout profile.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Graphical Sandbox Canvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val canvasMaxWidth = maxWidth
                val canvasMaxHeight = maxHeight
                
                // Calculate scale preserving aspect ratio
                val scale = remember(canvasMaxWidth, canvasMaxHeight, screenAspectRatio) {
                    val scaleW = canvasMaxWidth.value / screenWidth
                    val scaleH = canvasMaxHeight.value / screenHeight
                    kotlin.math.min(scaleW, scaleH)
                }
                
                val visualW = (screenWidth * scale).dp
                val visualH = (screenHeight * scale).dp

                Box(
                    modifier = Modifier
                        .size(visualW, visualH)
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    // Draw grid helper lines
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)).align(Alignment.Center))
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.05f)).align(Alignment.Center))

                    // Visual Snap Guides (glowing lines)
                    activeSnapLeftLine?.let { x ->
                        Box(modifier = Modifier.offset((x * scale).dp, 0.dp).fillMaxHeight().width(1.5.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                    }
                    activeSnapRightLine?.let { x ->
                        Box(modifier = Modifier.offset((x * scale).dp, 0.dp).fillMaxHeight().width(1.5.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                    }
                    activeSnapTopLine?.let { y ->
                        Box(modifier = Modifier.offset(0.dp, (y * scale).dp).fillMaxWidth().height(1.5.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                    }
                    activeSnapBottomLine?.let { y ->
                        Box(modifier = Modifier.offset(0.dp, (y * scale).dp).fillMaxWidth().height(1.5.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                    }

                    // Remember updated state holders to avoid stale lambda captures inside pointerInput
                    val currentAppsState = rememberUpdatedState(apps)
                    val currentScaleState = rememberUpdatedState(scale)
                    val screenWidthState = rememberUpdatedState(screenWidth)
                    val screenHeightState = rememberUpdatedState(screenHeight)

                    // Draw each application block
                    apps.forEachIndexed { index, app ->
                        val isSelected = selectedIndex == index
                        val appBounds = app.bounds

                        // Convert screen bounds to scaled DP values
                        val boxL = (appBounds.left * scale).dp
                        val boxT = (appBounds.top * scale).dp
                        val boxW = ((appBounds.right - appBounds.left) * scale).dp
                        val boxH = ((appBounds.bottom - appBounds.top) * scale).dp

                        Box(
                            modifier = Modifier
                                .offset(boxL, boxT)
                                .size(boxW, boxH)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            // 1. Sleek Title Bar (EXCLUSIVE DRAG & SNAP HANDLE)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                    .pointerInput(index) {
                                        detectDragGestures(
                                            onDragStart = { selectedIndex = index }, // Selecting block when drag starts
                                            onDragEnd = {
                                                activeSnapLeftLine = null
                                                activeSnapRightLine = null
                                                activeSnapTopLine = null
                                                activeSnapBottomLine = null
                                            },
                                            onDragCancel = {
                                                activeSnapLeftLine = null
                                                activeSnapRightLine = null
                                                activeSnapTopLine = null
                                                activeSnapBottomLine = null
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                
                                                val curApps = currentAppsState.value
                                                if (index >= 0 && index < curApps.size) {
                                                    val curApp = curApps[index]
                                                    val curBounds = curApp.bounds
                                                    val curScale = currentScaleState.value
                                                    val curScrWidth = screenWidthState.value
                                                    val curScrHeight = screenHeightState.value
                                                    
                                                    val dx = (dragAmount.x / curScale).toInt()
                                                    val dy = (dragAmount.y / curScale).toInt()
                                                    
                                                    val snapThreshold = (24 * curScale).toInt().coerceAtLeast(16)
                                                    
                                                    var finalLeft = curBounds.left + dx
                                                    var snapLeft: Float? = null
                                                    var snapRight: Float? = null
                                                    
                                                    if (Math.abs(finalLeft) < snapThreshold) {
                                                        finalLeft = 0
                                                        snapLeft = 0f
                                                    } else if (Math.abs((finalLeft + curBounds.width()) - curScrWidth) < snapThreshold) {
                                                        finalLeft = curScrWidth - curBounds.width()
                                                        snapRight = curScrWidth.toFloat()
                                                    } else if (Math.abs(finalLeft - (curScrWidth - curBounds.width()) / 2) < snapThreshold) {
                                                        finalLeft = (curScrWidth - curBounds.width()) / 2
                                                        snapLeft = curScrWidth / 2f
                                                    }
                                                    
                                                    var finalTop = curBounds.top + dy
                                                    var snapTop: Float? = null
                                                    var snapBottom: Float? = null
                                                    
                                                    if (Math.abs(finalTop) < snapThreshold) {
                                                        finalTop = 0
                                                        snapTop = 0f
                                                    } else if (Math.abs((finalTop + curBounds.height()) - curScrHeight) < snapThreshold) {
                                                        finalTop = curScrHeight - curBounds.height()
                                                        snapBottom = curScrHeight.toFloat()
                                                    } else if (Math.abs(finalTop - (curScrHeight - curBounds.height()) / 2) < snapThreshold) {
                                                        finalTop = (curScrHeight - curBounds.height()) / 2
                                                        snapTop = curScrHeight / 2f
                                                    }
                                                    
                                                    finalLeft = finalLeft.coerceIn(0, curScrWidth - curBounds.width())
                                                    finalTop = finalTop.coerceIn(0, curScrHeight - curBounds.height())
                                                    
                                                    activeSnapLeftLine = snapLeft
                                                    activeSnapRightLine = snapRight
                                                    activeSnapTopLine = snapTop
                                                    activeSnapBottomLine = snapBottom
                                                    
                                                    val updatedBounds = Rect(finalLeft, finalTop, finalLeft + curBounds.width(), finalTop + curBounds.height())
                                                    val newList = curApps.toMutableList()
                                                    newList[index] = newList[index].copy(bounds = updatedBounds)
                                                    apps = newList
                                                    selectedIndex = index
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Text(
                                    text = app.packageName.substringAfterLast("."),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp)
                                )
                            }

                            // 2. Card Body Content (App Launcher Icon) - Clicking selects the window card
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 24.dp) // Leave space for title bar
                                    .padding(6.dp)
                                    .clickable { selectedIndex = index },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AppIcon(app.packageName, modifier = Modifier.size(24.dp))
                            }

                            // 3. Diagonal Grip Resize Handle (EXCLUSIVE CORNER SCALE HANDLE)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp) // Touch bounds
                                    .pointerInput(index) {
                                        detectDragGestures(
                                            onDragStart = { selectedIndex = index },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                
                                                val curApps = currentAppsState.value
                                                if (index >= 0 && index < curApps.size) {
                                                    val curApp = curApps[index]
                                                    val curBounds = curApp.bounds
                                                    val curScale = currentScaleState.value
                                                    val curScrWidth = screenWidthState.value
                                                    val curScrHeight = screenHeightState.value
                                                    
                                                    val dw = (dragAmount.x / curScale).toInt()
                                                    val dh = (dragAmount.y / curScale).toInt()
                                                    
                                                    val minSize = 150
                                                    val newRight = (curBounds.right + dw).coerceIn(curBounds.left + minSize, curScrWidth)
                                                    val newBottom = (curBounds.bottom + dh).coerceIn(curBounds.top + minSize, curScrHeight)
                                                    
                                                    val updatedBounds = Rect(curBounds.left, curBounds.top, newRight, newBottom)
                                                    val newList = curApps.toMutableList()
                                                    newList[index] = newList[index].copy(bounds = updatedBounds)
                                                    apps = newList
                                                    selectedIndex = index
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Resize",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(2.dp)
                                        .graphicsLayer(rotationZ = -45f) // Rotated diagonal line/filter grip
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sandbox App Editor Panel
            if (selectedIndex != -1 && selectedIndex < apps.size) {
                val app = apps[selectedIndex]
                
                // Reactive numeric states mapped to selected block coordinates
                val leftStr = remember(selectedIndex, app.bounds.left) { mutableStateOf(app.bounds.left.toString()) }
                val topStr = remember(selectedIndex, app.bounds.top) { mutableStateOf(app.bounds.top.toString()) }
                val widthStr = remember(selectedIndex, app.bounds.width()) { mutableStateOf(app.bounds.width().toString()) }
                val heightStr = remember(selectedIndex, app.bounds.height()) { mutableStateOf(app.bounds.height().toString()) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(app.packageName, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Selected Block #${selectedIndex + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row {
                                FilledTonalIconButton(onClick = { showAppPicker = true }) {
                                    Icon(Icons.Default.Search, "Change App")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        apps = apps.toMutableList().apply { removeAt(selectedIndex) }
                                        selectedIndex = -1
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, "Delete Block", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Snapping Quick Presets
                        Text("Quick Layout Snaps", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val snaps = listOf(
                                "Fullscreen" to Rect(0, 0, screenWidth, screenHeight),
                                "Left Half" to Rect(0, 0, screenWidth / 2, screenHeight),
                                "Right Half" to Rect(screenWidth / 2, 0, screenWidth, screenHeight),
                                "Top Half" to Rect(0, 0, screenWidth, screenHeight / 2),
                                "Bottom Half" to Rect(0, screenHeight / 2, screenWidth, screenHeight),
                                "Top-Left" to Rect(0, 0, screenWidth / 2, screenHeight / 2),
                                "Top-Right" to Rect(screenWidth / 2, 0, screenWidth, screenHeight / 2),
                                "Bottom-Left" to Rect(0, screenHeight / 2, screenWidth / 2, screenHeight),
                                "Bottom-Right" to Rect(screenWidth / 2, screenHeight / 2, screenWidth, screenHeight)
                            )
                            snaps.forEach { (name, rect) ->
                                val isSelectedSnap = kotlin.math.abs(app.bounds.left - rect.left) < 10 &&
                                                     kotlin.math.abs(app.bounds.top - rect.top) < 10 &&
                                                     kotlin.math.abs(app.bounds.right - rect.right) < 10 &&
                                                     kotlin.math.abs(app.bounds.bottom - rect.bottom) < 10
                                FilterChip(
                                    selected = isSelectedSnap,
                                    onClick = {
                                        apps = apps.mapIndexed { i, a ->
                                            if (i == selectedIndex) a.copy(bounds = rect, isSnapped = true) else a
                                        }.toMutableList()
                                        leftStr.value = rect.left.toString()
                                        topStr.value = rect.top.toString()
                                        widthStr.value = rect.width().toString()
                                        heightStr.value = rect.height().toString()
                                    },
                                    label = { Text(name) },
                                    shape = CircleShape
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Precise Numerical Coordinate Editor Fields
                        Text("Coordinates & Size (px)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = leftStr.value,
                                onValueChange = {
                                    leftStr.value = it
                                    val v = it.toIntOrNull()
                                    if (v != null) {
                                        val newL = v.coerceIn(0, screenWidth)
                                        val newW = app.bounds.width()
                                        val newBounds = Rect(newL, app.bounds.top, (newL + newW).coerceAtMost(screenWidth), app.bounds.bottom)
                                        apps = apps.mapIndexed { i, a -> if (i == selectedIndex) a.copy(bounds = newBounds, isSnapped = false) else a }.toMutableList()
                                    }
                                },
                                label = { Text("Left (X)", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = topStr.value,
                                onValueChange = {
                                    topStr.value = it
                                    val v = it.toIntOrNull()
                                    if (v != null) {
                                        val newT = v.coerceIn(0, screenHeight)
                                        val newH = app.bounds.height()
                                        val newBounds = Rect(app.bounds.left, newT, app.bounds.right, (newT + newH).coerceAtMost(screenHeight))
                                        apps = apps.mapIndexed { i, a -> if (i == selectedIndex) a.copy(bounds = newBounds, isSnapped = false) else a }.toMutableList()
                                    }
                                },
                                label = { Text("Top (Y)", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = widthStr.value,
                                onValueChange = {
                                    widthStr.value = it
                                    val w = it.toIntOrNull()
                                    if (w != null && w > 0) {
                                        val maxW = screenWidth - app.bounds.left
                                        val finalW = w.coerceIn(120, if (maxW > 120) maxW else screenWidth)
                                        val newBounds = Rect(app.bounds.left, app.bounds.top, app.bounds.left + finalW, app.bounds.bottom)
                                        apps = apps.mapIndexed { i, a -> if (i == selectedIndex) a.copy(bounds = newBounds, isSnapped = false) else a }.toMutableList()
                                    }
                                },
                                label = { Text("Width", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = heightStr.value,
                                onValueChange = {
                                    heightStr.value = it
                                    val h = it.toIntOrNull()
                                    if (h != null && h > 0) {
                                        val maxH = screenHeight - app.bounds.top
                                        val finalH = h.coerceIn(120, if (maxH > 120) maxH else screenHeight)
                                        val newBounds = Rect(app.bounds.left, app.bounds.top, app.bounds.right, app.bounds.top + finalH)
                                        apps = apps.mapIndexed { i, a -> if (i == selectedIndex) a.copy(bounds = newBounds, isSnapped = false) else a }.toMutableList()
                                    }
                                },
                                label = { Text("Height", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select a window block inside the canvas to edit details or snap coordinates", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
    }

    // App Picker Search / Selector Dialog
    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Select Application", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search package...") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    if (isLoadingApps) {
                        Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val filtered = installedApps.filter {
                            it.packageName.lowercase().contains(searchQuery.lowercase()) ||
                                    context.packageManager.getApplicationLabel(it).toString().lowercase().contains(searchQuery.lowercase())
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                            LazyColumn {
                                items(filtered) { appInfo ->
                                    val appLabel = context.packageManager.getApplicationLabel(appInfo).toString()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val comp = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)?.component?.flattenToShortString()
                                                if (selectedIndex != -1 && selectedIndex < apps.size) {
                                                    apps = apps.mapIndexed { i, a ->
                                                        if (i == selectedIndex) a.copy(packageName = appInfo.packageName, component = comp) else a
                                                    }.toMutableList()
                                                } else {
                                                    val w = screenWidth / 2
                                                    val h = screenHeight / 2
                                                    val left = (screenWidth - w) / 2
                                                    val top = (screenHeight - h) / 2
                                                    val newApp = WorkspaceApp(
                                                        packageName = appInfo.packageName,
                                                        component = comp,
                                                        bounds = Rect(left, top, left + w, top + h),
                                                        isSnapped = false
                                                    )
                                                    apps = (apps + newApp).toMutableList()
                                                    selectedIndex = apps.size - 1
                                                }
                                                showAppPicker = false
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(appInfo.packageName, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(appInfo.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
