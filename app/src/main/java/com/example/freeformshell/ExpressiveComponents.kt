package com.example.freeformshell

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
    
        val bottomPadding = if (!isTablet && !isPhoneLandscape) (dockHeight + dockBottom) else 0.dp
    
        Box(Modifier.fillMaxSize()) {
            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = sidebarShift)
            ) {
                content(PaddingValues(bottom = bottomPadding))
            }
    
            // Adaptive Sidebar (Either Shifting or Overlay)
            if (isTablet || isPhoneLandscape) {
                ExpressiveSidebar(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = onTabSelected,
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
                    ExpressivePillDock(selectedTabIndex, onTabSelected, iconSize = iconSize, itemSpacing = itemSpacing)
                }
            }
        }
    }
}

@Composable
fun ExpressiveSidebar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
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
            IconButton(
                onClick = { onToggleExpand(!isExpanded) },
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                    null,
                    tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                )
            }

            val navItems = buildList {
                add(SidebarItemData(0, Icons.Default.Dashboard, "Windows"))
                add(SidebarItemData(7, Icons.Default.Workspaces, "Layouts"))
                add(SidebarItemData(1, Icons.Default.Palette, "Customization"))
                add(SidebarItemData(2, Icons.Default.List, "Tasks"))
                add(SidebarItemData(3, Icons.Default.Tv, "Display"))
                if (ThemeManager.isForceDesktopModeEnabled(context)) {
                    add(SidebarItemData(9, Icons.Default.Monitor, "Desktop"))
                }
                add(SidebarItemData(4, Icons.Default.Block, "Blacklist"))
                add(SidebarItemData(8, Icons.Default.Videocam, "Capture"))
                add(SidebarItemData(5, Icons.Default.Settings, "Settings"))
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
    iconSize: Dp = 48.dp,
    itemSpacing: Dp = 8.dp
) {
    val context = LocalContext.current
    val navItems = buildList {
        add(0 to Icons.Default.Dashboard)
        add(7 to Icons.Default.Workspaces)
        add(1 to Icons.Default.Palette)
        add(2 to Icons.Default.List)
        add(3 to Icons.Default.Tv)
        if (ThemeManager.isForceDesktopModeEnabled(context)) {
            add(9 to Icons.Default.Monitor)
        }
        add(4 to Icons.Default.Block)
        add(8 to Icons.Default.Videocam)
        add(5 to Icons.Default.Settings)
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
            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
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
        Column(modifier = Modifier.padding(16.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.apps.take(6).forEach { app ->
                    AppIcon(app.packageName, modifier = Modifier.size(32.dp))
                }
                if (group.apps.size > 6) {
                    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                        Text("+${group.apps.size - 6}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEdit(group, isFavorite) }, modifier = Modifier.weight(1f), shape = CircleShape) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
                if (!isFavorite) {
                    FilledTonalIconButton(onClick = { WorkspaceManager.setFavorite(context, group); onRefresh() }) {
                        Icon(Icons.Default.StarBorder, null)
                    }
                }
                IconButton(onClick = {
                    if (isFavorite) WorkspaceManager.removeFavorite(context)
                    else {
                        val history = WorkspaceManager.getHistory(context).toMutableList()
                        history.removeAll { it.timestamp == group.timestamp }
                        WorkspaceManager.saveHistory(context, history)
                    }
                    onRefresh()
                }) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private data class SidebarItemData(val index: Int, val icon: ImageVector, val label: String)
