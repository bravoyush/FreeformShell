package com.example.freeformshell

import android.widget.Toast
import android.content.Intent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Explicitly import some extended icons to ensure they are available
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Videocam

@Composable
fun ExpressiveLayout(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val context = LocalContext.current
    
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        
        // Adaptive thresholds based on active Freeform window boundaries!
        val isTablet = windowWidth >= 720.dp
        val isLandscape = windowWidth > windowHeight
        val isPhoneLandscape = isLandscape && !isTablet
        
        // Per-mode settings
        val hoverEnabled = if (isTablet) 
            ThemeManager.isSidebarHoverExpandTabletEnabled(context) 
        else 
            ThemeManager.isSidebarHoverExpandEnabled(context)
        
        val autoCollapse = if (isTablet)
            ThemeManager.isSidebarAutoCollapseTabletEnabled(context)
        else
            ThemeManager.isSidebarAutoCollapseEnabled(context)
            
        var isSidebarExpanded by remember { mutableStateOf(isTablet && !autoCollapse) }
        
        val sidebarWidth by animateDpAsState(
            targetValue = when {
                !isTablet && !isPhoneLandscape -> 0.dp // Hidden in portrait phone
                isSidebarExpanded -> 240.dp
                else -> 80.dp
            },
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "sidebarWidth"
        )
    
        // Only shift content on Large Tablets/Screens. 
        val sidebarShift by animateDpAsState(
            targetValue = if (isTablet) sidebarWidth else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "contentShift"
        )

        // Dynamic scaling factors for the dock based on active window height!
        val (dockHeight, dockBottom, iconSize, itemSpacing) = when {
            windowHeight >= 600.dp -> {
                listOf(64.dp, 16.dp, 48.dp, 8.dp)
            }
            windowHeight >= 450.dp -> {
                listOf(52.dp, 10.dp, 38.dp, 6.dp)
            }
            else -> {
                listOf(42.dp, 6.dp, 30.dp, 4.dp)
            }
        }
    
        Box(Modifier.fillMaxSize()) {
            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = sidebarShift)
            ) {
                content(PaddingValues(bottom = if (!isTablet && !isPhoneLandscape) dockHeight + dockBottom + 12.dp else 0.dp))
            }
    
            // Adaptive Sidebar (Either Shifting or Overlay)
            if (isTablet || isPhoneLandscape) {
                ExpressiveSidebar(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = onTabSelected,
                    onSearchClick = onSearchClick,
                    isExpanded = isSidebarExpanded,
                    onToggleExpand = { isSidebarExpanded = it },
                    isHoverEnabled = hoverEnabled,
                    width = sidebarWidth,
                    isOverlay = !isTablet // On non-tablet landscape, it behaves as an overlay
                )
            }
    
            // Floating Pill Dock (Portrait Phone)
            if (!isTablet && !isPhoneLandscape) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = dockBottom)
                ) {
                    ExpressivePillDock(selectedTabIndex, onTabSelected, onSearchClick = onSearchClick, iconSize = iconSize, itemSpacing = itemSpacing)
                }
            }
        }
    }
}

@Composable
fun ExpressiveSidebar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit = {},
    isExpanded: Boolean,
    onToggleExpand: (Boolean) -> Unit,
    isHoverEnabled: Boolean,
    width: Dp,
    isOverlay: Boolean
) {
    val context = LocalContext.current
    val surfaceColor by animateColorAsState(
        targetValue = if (isExpanded) 
            MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp) 
        else 
            MaterialTheme.colorScheme.primary,
        label = "sidebarColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .padding(vertical = 16.dp, horizontal = 12.dp)
            .pointerInput(isHoverEnabled) {
                if (isHoverEnabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Enter) onToggleExpand(true)
                            if (event.type == PointerEventType.Exit) onToggleExpand(false)
                        }
                    }
                }
            },
        color = surfaceColor,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = if (isOverlay) 24.dp else (if (isExpanded) 12.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToggleExpand(!isExpanded) }) {
                    Icon(
                        if (isExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                        null,
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search Settings",
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            val navItems = buildList {
                add(SidebarItemData(0, Icons.Default.Dashboard, "Overview Hub"))
                add(SidebarItemData(7, Icons.Default.Workspaces, "Layouts"))
                add(SidebarItemData(1, Icons.Default.Palette, "Customization"))
                if (ThemeManager.isModuleWidgetEnabled(context)) {
                    add(SidebarItemData(2, Icons.Default.List, "Task Manager"))
                }
                add(SidebarItemData(3, Icons.Default.Tv, "Display"))
                if (ThemeManager.isForceDesktopModeEnabled(context)) {
                    add(SidebarItemData(9, Icons.Default.Monitor, "Desktop"))
                }
                add(SidebarItemData(11, Icons.Default.RocketLaunch, "Top Bar & Panel"))
                add(SidebarItemData(4, Icons.Default.Block, "App Rules"))
                if (ThemeManager.isModuleCaptureEnabled(context)) {
                    add(SidebarItemData(8, Icons.Default.Videocam, "Capture"))
                }
                add(SidebarItemData(5, Icons.Default.Settings, "Settings"))
                if (ThemeManager.isModuleLockEnabled(context)) {
                    add(SidebarItemData(10, Icons.Default.Lock, "Lock Screen"))
                }
                add(SidebarItemData(12, Icons.Default.HelpCenter, "Guides"))
                add(SidebarItemData(6, Icons.Default.Build, "Compat"))
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                navItems.forEach { item ->
                    val isSelected = selectedTabIndex == item.index
                    ExpressiveSidebarItem(
                        selected = isSelected,
                        onClick = { onTabSelected(item.index) },
                        icon = item.icon,
                        label = item.label,
                        isExpanded = isExpanded
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ExpressiveSidebarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    isExpanded: Boolean
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            if (isExpanded) MaterialTheme.colorScheme.secondaryContainer 
            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
        } else Color.Transparent,
        label = "itemBg"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isExpanded) {
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer 
            else MaterialTheme.colorScheme.onSurfaceVariant
        } else MaterialTheme.colorScheme.onPrimary,
        label = "itemContent"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick, // Using Surface's built-in onClick for better ripple isolation
        color = containerColor,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
        ) {
            if (isExpanded) Spacer(Modifier.width(16.dp))

            Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.dp))
            
            if (isExpanded) {
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExpressivePillDock(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit = {},
    iconSize: Dp = 48.dp,
    itemSpacing: Dp = 8.dp
) {
    val context = LocalContext.current
    val navItems = buildList {
        add(0 to Icons.Default.Dashboard)
        add(7 to Icons.Default.Workspaces)
        add(1 to Icons.Default.Palette)
        if (ThemeManager.isModuleWidgetEnabled(context)) {
            add(2 to Icons.Default.List)
        }
        add(3 to Icons.Default.Tv)
        if (ThemeManager.isForceDesktopModeEnabled(context)) {
            add(9 to Icons.Default.Monitor)
        }
        add(11 to Icons.Default.RocketLaunch)
        add(4 to Icons.Default.Block)
        if (ThemeManager.isModuleCaptureEnabled(context)) {
            add(8 to Icons.Default.Videocam)
        }
        add(5 to Icons.Default.Settings)
        if (ThemeManager.isModuleLockEnabled(context)) {
            add(10 to Icons.Default.Lock)
        }
        add(6 to Icons.Default.Build)
    }

    val rowPaddingH = iconSize * 0.25f
    val rowPaddingV = iconSize * 0.16f

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
        shape = CircleShape,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = rowPaddingH, vertical = rowPaddingV)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), CircleShape)
                    .clickable { onSearchClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(iconSize * 0.5f)
                )
            }

            navItems.forEach { (index, icon) ->
                val isSelected = selectedTabIndex == index
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, 
                            CircleShape
                        )
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize * 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveWorkspaceManagerScreen(
    padding: PaddingValues,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onEditGroup: (WorkspaceGroup, Boolean) -> Unit
) {
    val context = LocalContext.current
    val favoriteWorkspace = remember(refreshKey) { WorkspaceManager.getFavorite(context) }
    val historyWorkspaces = remember(refreshKey) { WorkspaceManager.getHistory(context) }
    var showCreateDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(24.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Workspace Manager", 
            style = MaterialTheme.typography.displaySmall, 
            fontWeight = FontWeight.ExtraBold, 
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Save and organize layouts for quick snapping", 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // ➕ CREATE NEW LAYOUT ACTION CARD
        ElevatedCard(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(18.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Create Custom Layout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Build a new multi-window layout preset from scratch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showCreateDialog) {
            var selectedDisplayTarget by remember { mutableIntStateOf(0) }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create Custom Layout Preset", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Select target display grid layout:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedDisplayTarget == 0,
                                onClick = { selectedDisplayTarget = 0 },
                                label = { Text("Phone Screen (1080x2400)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedDisplayTarget != 0,
                                onClick = { selectedDisplayTarget = 1 },
                                label = { Text("Widescreen (1920x1080)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCreateDialog = false
                            val newGroup = WorkspaceGroup(
                                apps = emptyList(),
                                displayId = selectedDisplayTarget,
                                timestamp = System.currentTimeMillis()
                            )
                            onEditGroup(newGroup, false)
                        }
                    ) {
                        Text("Open Layout Editor")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                }
            )
        }
        
        if (favoriteWorkspace == null && historyWorkspaces.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Saved Workspaces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Launch apps and click 'Save Layout' to see them here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            if (favoriteWorkspace != null) {
                Text("Favorite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                WorkspaceCard(favoriteWorkspace, true, onEditGroup, onRefresh)
                Spacer(Modifier.height(24.dp))
            }
            
            if (historyWorkspaces.isNotEmpty()) {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                historyWorkspaces.forEach { group ->
                    WorkspaceCard(group, false, onEditGroup, onRefresh)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun WorkspaceCard(
    group: WorkspaceGroup, 
    isFavorite: Boolean,
    onEdit: (WorkspaceGroup, Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = if (isFavorite) 
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
        else 
            CardDefaults.elevatedCardColors()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Content Column (Metadata & Controls)
            Column(modifier = Modifier.weight(1.3f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.History, 
                        null, 
                        tint = if (isFavorite) Color(0xFFFFC107) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (group.displayId == 0) "Phone Workspace" else "External Display ${group.displayId}", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.apps.take(4).forEach { app ->
                        AppIcon(app.packageName, modifier = Modifier.size(28.dp))
                    }
                    if (group.apps.size > 4) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${group.apps.size - 4}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Direct premium Launch button
                    Button(
                        onClick = {
                            try {
                                val groupJson = WorkspaceManager.groupToJson(group).toString()
                                val serviceIntent = Intent(context, FreeformOverlayService::class.java).apply {
                                    action = "ACTION_LAUNCH_WORKSPACE"
                                    putExtra("EXTRA_GROUP_JSON", groupJson)
                                    putExtra("EXTRA_TARGET_DISPLAY", group.displayId)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                                Toast.makeText(context, "Restoring Saved Workspace...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to restore workspace: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Launch", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onEdit(group, isFavorite) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        shape = CircleShape,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit", fontSize = 12.sp)
                    }

                    if (!isFavorite) {
                        FilledTonalIconButton(
                            onClick = { WorkspaceManager.setFavorite(context, group); onRefresh() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.StarBorder, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            try {
                                WorkspaceShortcutHelper.pinWorkspaceShortcut(context, group)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to pin shortcut: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.AddHome, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            if (isFavorite) WorkspaceManager.removeFavorite(context)
                            else {
                                val history = WorkspaceManager.getHistory(context).toMutableList()
                                history.removeAll { it.timestamp == group.timestamp }
                                WorkspaceManager.saveHistory(context, history)
                            }
                            onRefresh()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Right Bento Column (Mini visualizer)
            val isPortrait = group.displayId == 0
            val miniW = if (isPortrait) 64.dp else 120.dp
            val miniH = if (isPortrait) 120.dp else 68.dp
            
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(132.dp)
                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(miniW, miniH)
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    val screenW = if (isPortrait) 1080f else 1920f
                    val screenH = if (isPortrait) 2400f else 1080f
                    val scaleX = miniW.value / screenW
                    val scaleY = miniH.value / screenH

                    group.apps.forEach { app ->
                        val appL = (app.bounds.left * scaleX).dp
                        val appT = (app.bounds.top * scaleY).dp
                        val appW = ((app.bounds.right - app.bounds.left) * scaleX).dp
                        val appH = ((app.bounds.bottom - app.bounds.top) * scaleY).dp

                        Box(
                            modifier = Modifier
                                .offset(appL, appT)
                                .size(appW, appH)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

private data class SidebarItemData(val index: Int, val icon: ImageVector, val label: String)

// ── Search & Smart Quick Settings Components ─────────────────────────────────

data class SearchableSettingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val tabIndex: Int,
    val icon: ImageVector,
    val actionText: String,
    val keywords: String = "",
    val onExecute: (() -> Unit)? = null
)

@Composable
fun SettingsSearchDialog(
    onDismiss: () -> Unit,
    onNavigateToTab: ((Int, String) -> Unit)? = null
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val allSettings = remember(context) {
        val staticItems = listOf(
            // --- TAB 0: OVERVIEW HUB ---
            SearchableSettingItem("force_desktop", "Force Desktop Mode", "Enable desktop environment on external displays programmatically", "Overview Hub", 0, Icons.Default.Tv, "Go to Overview", "desktop mode external display monitor secondary shell") {
                val current = ThemeManager.isForceDesktopModeEnabled(context)
                ThemeManager.setForceDesktopModeEnabled(!current)
            },
            SearchableSettingItem("target_display", "Target Display Selection", "Select display ID to launch overlays or desktop shell", "Overview Hub", 0, Icons.Default.Monitor, "Go to Overview", "display id screen target monitor shell"),
            SearchableSettingItem("launch_app", "Launch Controls & App Queue", "Select and queue applications to launch in freeform mode", "Overview Hub", 0, Icons.Default.PlayArrow, "Go to Overview", "launch app queue start open freeform window"),
            SearchableSettingItem("shizuku_status", "Shizuku Service Status", "Check if Shizuku binder service is active and authorized", "Overview Hub", 0, Icons.Default.Info, "Go to Overview", "shizuku adb wireless debugging permission binder status"),
            SearchableSettingItem("console_logs", "System Console Logs", "View live logcat and window management debug logs", "Overview Hub", 0, Icons.Default.List, "Go to Overview", "logcat logs console debug window management"),

            // --- TAB 1: CUSTOMIZATION ---
            SearchableSettingItem("theme_mode", "Theme Mode (Auto / Light / Dark)", "Switch app color scheme between System Auto, Light, and Dark", "Customization", 1, Icons.Default.Palette, "Open Customization", "theme dark light auto color scheme mode") {
                val current = ThemeManager.getThemeMode(context)
                ThemeManager.setThemeMode(context, (current + 1) % 3)
            },
            SearchableSettingItem("corner_roundness", "Window Corner Roundness", "Adjust corner border radius of freeform windows (0dp - 40dp)", "Customization → Geometry", 1, Icons.Default.RoundedCorner, "Open Customization", "corner roundness radius curvature curved border geometry"),
            SearchableSettingItem("window_opacity", "Window Transparency & Opacity", "Adjust background opacity level of freeform app windows", "Customization → Geometry", 1, Icons.Default.Opacity, "Open Customization", "opacity transparency alpha translucent clear background window"),
            SearchableSettingItem("border_width", "Window Border Stroke Width", "Customize accent border line thickness around active windows", "Customization → Geometry", 1, Icons.Default.LineStyle, "Open Customization", "border width stroke line thickness border_width outline"),
            SearchableSettingItem("drag_tint", "Drag & Resize Tint Overlay", "Show accent tint feedback while dragging or resizing windows", "Customization → Geometry", 1, Icons.Default.Build, "Open Customization", "drag tint resize feedback color active movement") {
                val current = ThemeManager.isDragTintEnabled(context)
                ThemeManager.setDragTintEnabled(context, !current)
            },
            SearchableSettingItem("header_mode", "Header Mode (Floating Pill vs Title Bar)", "Switch between compact macOS-style floating pill and full bar", "Customization → Title Bar", 1, Icons.Default.ViewDay, "Open Customization", "header pill titlebar macos floating bar top strip") {
                val current = ThemeManager.usePillForSnapped(context)
                ThemeManager.setPillForSnapped(context, !current)
            },
            SearchableSettingItem("icon_pack", "Icon Pack Selection", "Apply custom third-party icon packs to title bars and docks", "Customization → Title Bar", 1, Icons.Default.Category, "Open Customization", "icon pack icons launcher app custom icons"),
            SearchableSettingItem("titlebar_opacity", "Title Bar Opacity", "Control transparency of the window top title bar", "Customization → Title Bar", 1, Icons.Default.Opacity, "Open Customization", "titlebar opacity header transparency alpha top bar"),
            SearchableSettingItem("auto_hide_bar", "Auto-Hide Title Bar", "Collapse title bar into thin handle strip when window is idle", "Customization → Title Bar", 1, Icons.Default.Animation, "Open Customization", "auto hide titlebar collapse vanish disappear unfocus idle handle strip") {
                val current = ThemeManager.autoHideTitleBar(context)
                ThemeManager.setAutoHideTitleBar(context, !current)
            },
            SearchableSettingItem("pill_shrink", "Pill Auto-Shrink & Auto-Scale", "Automatically shrink floating pill dock when idle", "Customization → Title Bar", 1, Icons.Default.Compress, "Open Customization", "pill shrink auto scale dock idle compact mini"),
            SearchableSettingItem("pill_shrink_style", "Pill Shrink Animation Style", "Toggle between physical resize handle and scale transform animations", "Customization → Title Bar", 1, Icons.Default.Tune, "Open Customization", "pill shrink animation style transform scale handle"),
            SearchableSettingItem("app_ui_style", "App UI Style (Classic vs Expressive)", "Switch between Classic console UI and Expressive sidebar design", "Customization → Layout", 1, Icons.Default.Style, "Open Customization", "app ui style classic expressive sidebar layout design theme"),
            SearchableSettingItem("sidebar_hover", "Sidebar Hover Expansion", "Expand navigation sidebar automatically when hovering over icons", "Customization → Layout", 1, Icons.Default.MenuOpen, "Open Customization", "sidebar hover expand mouse pointer drawer layout"),
            SearchableSettingItem("sidebar_collapse", "Sidebar Auto-Collapse", "Automatically collapse navigation drawer when moving pointer away", "Customization → Layout", 1, Icons.Default.ChevronLeft, "Open Customization", "sidebar collapse auto drawer hide mouse pointer"),
            SearchableSettingItem("auto_scale", "Automatic App UI Scaling", "Scale down layout automatically in narrow Freeform windows", "Customization → Scaling", 1, Icons.Default.AspectRatio, "Open Customization", "auto scale app ui scaling narrow small window size responsive") {
                val current = ThemeManager.isAutoUiScalingEnabled(context)
                ThemeManager.setAutoUiScalingEnabled(context, !current)
            },
            SearchableSettingItem("custom_scale", "Custom UI Scale Factor", "Adjust fine UI scale multiplier (0.7x to 1.3x)", "Customization → Scaling", 1, Icons.Default.Straighten, "Open Customization", "custom scale factor multiplier ui zoom size ratio"),
            SearchableSettingItem("resize_engine", "Resizing Engine Style", "Switch between Real-Time, Classic Tinted, and Matte Overlay resize engines", "Customization → Engine", 1, Icons.Default.Speed, "Open Customization", "resize engine realtime tinted matte performance GPU scaling style"),

            // --- TAB 2: TASK MANAGER ---
            SearchableSettingItem("running_tasks", "Running Overlays & Tasks", "View and manage active freeform window overlays", "Task Manager", 2, Icons.Default.ListAlt, "Open Tasks", "running tasks overlays active windows task manager process"),
            SearchableSettingItem("kill_task", "Close & Kill Overlays", "Force terminate active window task overlays", "Task Manager", 2, Icons.Default.Close, "Open Tasks", "close kill force stop terminate overlay task window"),
            SearchableSettingItem("focus_task", "Bring Window to Front", "Switch window focus and bring selected overlay to top", "Task Manager", 2, Icons.Default.FlipToFront, "Open Tasks", "focus bring to front z-order top active window stack"),

            // --- TAB 3: DISPLAY ---
            SearchableSettingItem("display_resolution", "Display Resolution & Density DPI", "Configure custom virtual resolution and DPI for secondary displays", "Display", 3, Icons.Default.Tv, "Open Display", "display resolution density dpi override virtual screen WxH height width"),
            SearchableSettingItem("display_orientation", "Display Rotation & Orientation", "Lock or rotate external display orientation (Landscape / Portrait)", "Display", 3, Icons.Default.ScreenRotation, "Open Display", "display orientation rotation landscape portrait rotate lock screen"),

            // --- TAB 4: BLACKLIST & RULES ---
            SearchableSettingItem("app_rules", "Per-App Freeform Rules", "Configure default window size, aspect ratio, and launch flags per app", "App Rules", 4, Icons.Default.Rule, "Open Rules", "app rules per-app window size aspect ratio launch flags defaults"),
            SearchableSettingItem("app_blacklist", "App Exclusion Blacklist", "Blacklist specific apps from opening in freeform windows", "App Rules", 4, Icons.Default.Block, "Open Rules", "app blacklist exclusion exclude disable freeform overlay block"),

            // --- TAB 5: APP SETTINGS ---
            SearchableSettingItem("app_settings", "General App Settings & Preferences", "Configure system permissions, overlays, and defaults", "Settings", 5, Icons.Default.Settings, "Open Settings", "general app settings preferences permissions overlays defaults system"),

            // --- TAB 6: COMPATIBILITY ---
            SearchableSettingItem("compat_rules", "Compatibility Engine & Rules", "Android 11 to 15+ OS compatibility guidelines and fixes", "Compatibility", 6, Icons.Default.Build, "Open Compatibility", "compatibility engine rules android version fixes workarounds os"),

            // --- TAB 7: WORKSPACES & LAYOUTS ---
            SearchableSettingItem("snap_layouts", "Window Snap Previews & Tiling", "View interactive previews of multi-window tiling grids", "Workspaces", 7, Icons.Default.Dashboard, "Open Workspaces", "snap layouts preview tiling grid multi window split split-screen"),
            SearchableSettingItem("workspace_presets", "Workspace Presets", "Load and switch between saved multi-window workspace setups", "Workspaces", 7, Icons.Default.Workspaces, "Open Workspaces", "workspace presets load saved layout setups multi window split"),
            SearchableSettingItem("save_layout", "Save Current Workspace", "Save active freeform window positions as a custom layout preset", "Workspaces", 7, Icons.Default.Save, "Open Workspaces", "save current workspace layout preset store snapshot windows"),

            // --- TAB 8: CAPTURE ---
            SearchableSettingItem("screen_recorder", "Screen Recorder & Snipping Tool", "Record video or capture screenshots of freeform windows and desktop", "Capture", 8, Icons.Default.Videocam, "Open Capture", "screen recorder screenshot capture video snipping tool record desktop window"),
            SearchableSettingItem("audio_source", "Recorder Audio Source", "Choose between System Internal Audio or Microphone input for recording", "Capture", 8, Icons.Default.Mic, "Open Capture", "recorder audio source internal system mic microphone sound recording"),

            // --- TAB 9: DESKTOP ---
            SearchableSettingItem("desktop_wallpaper", "Desktop Wallpaper & Background", "Choose custom desktop wallpaper images or solid colors", "Desktop", 9, Icons.Default.Wallpaper, "Open Desktop", "desktop wallpaper background image picture solid color screen"),
            SearchableSettingItem("desktop_shortcuts", "Desktop App Shortcuts", "Pin frequently used app icons and widgets directly to desktop grid", "Desktop", 9, Icons.Default.GridOn, "Open Desktop", "desktop shortcuts pinned app icons widgets grid home screen"),

            // --- TAB 12: GUIDES ---
            SearchableSettingItem("guide_center", "Guide Center & Tutorials", "Detailed guides for Shizuku, Dock, and Multi-window", "Guides", 12, Icons.Default.HelpCenter, "Open Guides", "guide center tutorials help documentation setup how-to"),
            SearchableSettingItem("shizuku_guide", "Shizuku Setup Guide", "Detailed steps for wireless debugging and ADB pairing", "Guides", 12, Icons.Default.Shield, "Open Guides", "shizuku guide setup wireless debugging adb pairing tutorial help"),
            SearchableSettingItem("quick_start", "Launch Quick Start Wizard", "Run the initial setup and permission onboarding again", "Guides", 12, Icons.Default.Bolt, "Open Guides", "launch quick start wizard onboarding initial setup permission step-by-step")
        )

        // DYNAMIC INDEXING: Automatically register ALL fixes from CompatibilityManager.ALL_FIXES!
        val dynamicCompatItems = CompatibilityManager.ALL_FIXES.map { fix ->
            val customKeywords = when (fix.id) {
                "hide_overlays_on_launcher_focused" -> "taskbar start menu launcher focus hide titlebar disappear reappear vanish focus collapse"
                "compat_precise_touch_region" -> "precise touch region cutout cutout carve center touchable borders tapjacking pass-through"
                "compat_hybrid_leash_minimization" -> "compositor leash minimization focus guard background desktop click android 12"
                "compat_border_fade_on_resize" -> "border fade resize WMS sync opacity transition desync"
                "compat_minimize_button" -> "minimize button titlebar header window close shrink"
                "compat_isolated_titlebar_linking" -> "isolated titlebar surface linking reparent surfacecontrol latency smooth"
                "strict_wms_sync" -> "strict WMS sync window manager synchronization bounce rubber-banding"
                "hidden_api_bypass" -> "LSPosed hidden api bypass reflection non-sdk classloader"
                "finger_defer_focus" -> "touch defer focus finger lift drag inputdispatcher action_cancel touch"
                "hover_phantom_exit_check" -> "hover phantom exit position check pointer mouse stylus"
                "android12_visibility_bypass" -> "android 12 task visibility bypass false misreported"
                "compat_resolution_override" -> "resolution override customization WxH display virtual screen"
                "compat_refresh_rate_override" -> "refresh rate override customization Hz display smooth preferredDisplayModeId"
                else -> "compatibility fix workaround rule android"
            }
            SearchableSettingItem(
                id = fix.id,
                title = fix.label,
                subtitle = fix.description,
                category = "Compatibility",
                tabIndex = 6,
                icon = Icons.Default.Build,
                actionText = "Open Compatibility",
                keywords = "compat fix workaround android ${fix.affectsVersionLabel} ${fix.id} $customKeywords"
            )
        }

        staticItems + dynamicCompatItems
    }

    val filtered = remember(searchQuery, allSettings) {
        if (searchQuery.isBlank()) allSettings else {
            val queryTokens = searchQuery.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            allSettings.filter { item ->
                val searchableText = "${item.title} ${item.subtitle} ${item.category} ${item.keywords}".lowercase()
                queryTokens.all { token -> searchableText.contains(token) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Search Shell Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search (e.g. opacity, compatibility, dpi)...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))

                // Results List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { item ->
                        ElevatedCard(
                            onClick = {
                                item.onExecute?.invoke()
                                onNavigateToTab?.invoke(item.tabIndex, item.id)
                                Toast.makeText(context, "Navigated to ${item.category}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = item.category,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartQuickSettingsSection(onNavigateToTab: (Int) -> Unit) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }
    
    val topKeys = remember(refreshTrigger) {
        ThemeManager.getTopFrequentSettings(context, listOf("theme_mode", "auto_hide", "desktop_mode", "pill_mode"))
    }
    
    val autoHide = remember(refreshTrigger) { ThemeManager.autoHideTitleBar(context) }
    val usePill = remember(refreshTrigger) { ThemeManager.usePillForSnapped(context) }
    val isDesktop = remember(refreshTrigger) { ThemeManager.isForceDesktopModeEnabled(context) }
    val autoScale = remember(refreshTrigger) { ThemeManager.isAutoUiScalingEnabled(context) }
    val themeMode = remember(refreshTrigger) { ThemeManager.getThemeMode(context) }
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Quick Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Grid of Top 4 Frequent Settings
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                topKeys.take(2).forEach { key ->
                    QuickControlTile(
                        key = key,
                        autoHide = autoHide,
                        usePill = usePill,
                        isDesktop = isDesktop,
                        autoScale = autoScale,
                        themeMode = themeMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        refreshTrigger++
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                topKeys.drop(2).take(2).forEach { key ->
                    QuickControlTile(
                        key = key,
                        autoHide = autoHide,
                        usePill = usePill,
                        isDesktop = isDesktop,
                        autoScale = autoScale,
                        themeMode = themeMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        refreshTrigger++
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickControlTile(
    key: String,
    autoHide: Boolean,
    usePill: Boolean,
    isDesktop: Boolean,
    autoScale: Boolean,
    themeMode: Int,
    modifier: Modifier = Modifier,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    
    val label = when(key) {
        "theme_mode" -> "Theme: ${listOf("Auto", "Light", "Dark")[themeMode]}"
        "auto_hide" -> "Auto-Hide: ${if (autoHide) "ON" else "OFF"}"
        "desktop_mode" -> "Desktop: ${if (isDesktop) "ON" else "OFF"}"
        "pill_mode" -> "Pill Mode: ${if (usePill) "ON" else "OFF"}"
        "auto_scale" -> "Auto-Scale: ${if (autoScale) "ON" else "OFF"}"
        else -> "Drag Tint"
    }
    
    val icon = when(key) {
        "theme_mode" -> Icons.Default.Palette
        "auto_hide" -> Icons.Default.Animation
        "desktop_mode" -> Icons.Default.Tv
        "pill_mode" -> Icons.Default.Dashboard
        "auto_scale" -> Icons.Default.AspectRatio
        else -> Icons.Default.Build
    }
    
    val isActive = when(key) {
        "theme_mode" -> themeMode != 0
        "auto_hide" -> autoHide
        "desktop_mode" -> isDesktop
        "pill_mode" -> usePill
        "auto_scale" -> autoScale
        else -> ThemeManager.isDragTintEnabled(context)
    }

    FilterChip(
        selected = isActive,
        onClick = {
            when(key) {
                "theme_mode" -> {
                    val next = (themeMode + 1) % 3
                    ThemeManager.setThemeMode(context, next)
                    ThemeManager.recordSettingUsed(context, "theme_mode")
                }
                "auto_hide" -> {
                    ThemeManager.setAutoHideTitleBar(context, !autoHide)
                    ThemeManager.recordSettingUsed(context, "auto_hide")
                }
                "desktop_mode" -> {
                    ThemeManager.setForceDesktopModeEnabled(!isDesktop)
                    ThemeManager.recordSettingUsed(context, "desktop_mode")
                }
                "pill_mode" -> {
                    ThemeManager.setPillForSnapped(context, !usePill)
                    ThemeManager.recordSettingUsed(context, "pill_mode")
                }
                "auto_scale" -> {
                    ThemeManager.setAutoUiScalingEnabled(context, !autoScale)
                    ThemeManager.recordSettingUsed(context, "auto_scale")
                }
                else -> {
                    val current = ThemeManager.isDragTintEnabled(context)
                    ThemeManager.setDragTintEnabled(context, !current)
                    ThemeManager.recordSettingUsed(context, "drag_tint")
                }
            }
            onChanged()
        },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.height(34.dp)
    )
}
