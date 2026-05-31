package com.example.freeformshell

import android.content.Context
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
    
    // Installed applications list for package selector dialog
    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingApps by remember { mutableStateOf(false) }

    // Widescreen container dimensions (Simulated Screen bounds)
    // External display default: 1920 x 1080 or phone default 1080 x 2400
    val screenWidth = if (group.displayId == 0) 1080 else 1920
    val screenHeight = if (group.displayId == 0) 2400 else 1080
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
                    // Append a default window (centered, 50% width/height)
                    val w = screenWidth / 2
                    val h = screenHeight / 2
                    val left = (screenWidth - w) / 2
                    val top = (screenHeight - h) / 2
                    val newApp = WorkspaceApp(
                        packageName = "com.android.settings",
                        component = null,
                        bounds = Rect(left, top, left + w, top + h),
                        isSnapped = false
                    )
                    apps = (apps + newApp).toMutableList()
                    selectedIndex = apps.size - 1
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
                text = if (group.displayId == 0) "Phone Screen Grid (1080 x 2400)" else "Widescreen Monitor Grid (1920 x 1080)",
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
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedIndex = index }
                                .pointerInput(index) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        
                                        // Translate dragging in DPs back into physical screen pixel offsets
                                        val dx = (dragAmount.x / scale).toInt()
                                        val dy = (dragAmount.y / scale).toInt()
                                        
                                        val newLeft = (appBounds.left + dx).coerceIn(0, screenWidth - appBounds.width())
                                        val newTop = (appBounds.top + dy).coerceIn(0, screenHeight - appBounds.height())
                                        val newRight = newLeft + appBounds.width()
                                        val newBottom = newTop + appBounds.height()
                                        
                                        val updatedBounds = Rect(newLeft, newTop, newRight, newBottom)
                                        apps[index] = apps[index].copy(bounds = updatedBounds)
                                        apps = apps.toMutableList()
                                        selectedIndex = index
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AppIcon(app.packageName, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = app.packageName.substringAfterLast("."),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Dynamic Resize handle in bottom-right corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .pointerInput(index) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            
                                            val dw = (dragAmount.x / scale).toInt()
                                            val dh = (dragAmount.y / scale).toInt()
                                            
                                            val minSize = (120 * scale).toInt()
                                            val newRight = (appBounds.right + dw).coerceIn(appBounds.left + minSize, screenWidth)
                                            val newBottom = (appBounds.bottom + dh).coerceIn(appBounds.top + minSize, screenHeight)
                                            
                                            val updatedBounds = Rect(appBounds.left, appBounds.top, newRight, newBottom)
                                            apps[index] = apps[index].copy(bounds = updatedBounds)
                                            apps = apps.toMutableList()
                                            selectedIndex = index
                                        }
                                    },
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(8.dp)
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(bottomEnd = 2.dp))
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
                                FilterChip(
                                    selected = app.bounds == rect,
                                    onClick = {
                                        apps[selectedIndex] = app.copy(bounds = rect, isSnapped = true)
                                        apps = apps.toMutableList()
                                    },
                                    label = { Text(name) },
                                    shape = CircleShape
                                )
                            }
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
                                                if (selectedIndex != -1 && selectedIndex < apps.size) {
                                                    apps[selectedIndex] = apps[selectedIndex].copy(
                                                        packageName = appInfo.packageName,
                                                        component = null
                                                    )
                                                    apps = apps.toMutableList()
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
