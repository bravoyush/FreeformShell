package com.example.freeformshell

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSettingsScreen(
    padding: PaddingValues,
    onNavigateToTab: (Int) -> Unit
) {
    val context = LocalContext.current
    var desktopThemeStyle by remember { mutableStateOf(ThemeManager.getDesktopThemeStyle(context)) }
    var showStatusBar by remember { mutableStateOf(ThemeManager.showStatusBar(context)) }
    var showBatteryPercent by remember { mutableStateOf(ThemeManager.showBatteryPercent(context)) }
    var enableQuickSettings by remember { mutableStateOf(ThemeManager.isQuickSettingsEnabled(context)) }
    var statusBarHeight by remember { mutableStateOf(ThemeManager.getStatusBarHeight(context)) }
    var splitClickBento by remember { mutableStateOf(ThemeManager.isSplitClickBentoEnabled(context)) }
    var scrollPageSwitching by remember { mutableStateOf(ThemeManager.isScrollPageSwitchingEnabled(context)) }
    var notifPanelScale by remember { mutableStateOf(ThemeManager.getNotifPanelScale(context)) }
    var notifPanelHeight by remember { mutableStateOf(ThemeManager.getNotifPanelHeight(context)) }
    var notifPanelAnimDuration by remember { mutableStateOf(ThemeManager.getNotifPanelAnimDuration(context)) }
    var notifGroupingEnabled by remember { mutableStateOf(ThemeManager.isNotifGroupingEnabled(context)) }
    var livePreviewNotifHeight by remember { mutableStateOf(false) }
    var windowsNotifsEnabled by remember { mutableStateOf(ThemeManager.isWindowsNotifsEnabled(context)) }
    var windowsNotifsPosition by remember { mutableStateOf(ThemeManager.getWindowsNotifsPosition(context)) }

    var customAiAppPackage by remember { mutableStateOf(ThemeManager.getCustomAiAppPackage(context)) }
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<SettingsAppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(showAppSelectionDialog) {
        if (showAppSelectionDialog && installedApps.isEmpty()) {
            isLoadingApps = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    val resolved = pm.queryIntentActivities(mainIntent, 0)
                    val apps = resolved.map {
                        SettingsAppInfo(
                            packageName = it.activityInfo.packageName,
                            label = it.loadLabel(pm).toString()
                        )
                    }.distinctBy { it.packageName }.sortedBy { it.label }
                    installedApps = apps
                } catch (e: Exception) {
                    android.util.Log.e("LauncherSettings", "Error loading apps", e)
                } finally {
                    isLoadingApps = false
                }
            }
        }
    }

    val customAiAppLabel = remember(customAiAppPackage, installedApps) {
        if (customAiAppPackage.isEmpty()) {
            "Default Assistant (Gemini)"
        } else {
            val found = installedApps.find { it.packageName == customAiAppPackage }
            if (found != null) {
                found.label
            } else {
                try {
                    val pm = context.packageManager
                    val info = pm.getApplicationInfo(customAiAppPackage, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    customAiAppPackage
                }
            }
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(24.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Title
        Text(
            text = "Launcher Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Choose the system-wide desktop layout interface style and configure interactive status and overlay panels.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))

        // Card Selection for Style Theme
        Text(
            text = "Desktop Style Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Classic Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        desktopThemeStyle = 0
                        ThemeManager.setDesktopThemeStyle(context, 0)
                        try {
                            FreeformOverlayService.requestRefresh()
                        } catch (e: Exception) {}
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (desktopThemeStyle == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = if (desktopThemeStyle == 0) 2.dp else 1.dp,
                    color = if (desktopThemeStyle == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = if (desktopThemeStyle == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Classic Setup",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (desktopThemeStyle == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Original margins and standard dock positioning.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        minLines = 3
                    )
                }
            }

            // Aluminum OS Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        desktopThemeStyle = 1
                        ThemeManager.setDesktopThemeStyle(context, 1)
                        try {
                            FreeformOverlayService.requestRefresh()
                        } catch (e: Exception) {}
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (desktopThemeStyle == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = if (desktopThemeStyle == 1) 2.dp else 1.dp,
                    color = if (desktopThemeStyle == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (desktopThemeStyle == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Aluminum OS",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (desktopThemeStyle == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Android 17 desktop aesthetics with top status bar & quick settings bento panel.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        minLines = 3
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Dynamic Configuration
        if (desktopThemeStyle == 1) {
            Text(
                text = "Aluminum OS Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Top Status Bar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Render a flat translucent status bar overlay with real-time battery status, clock, and quick bento setting controls at the top.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = showStatusBar,
                            onCheckedChange = {
                                showStatusBar = it
                                ThemeManager.setShowStatusBar(context, it)
                                try {
                                    FreeformOverlayService.requestRefresh()
                                } catch (e: Exception) {}
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Battery Percentage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Display the exact battery percentage number inside the status bar battery capsule overlay.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = showBatteryPercent,
                            onCheckedChange = {
                                showBatteryPercent = it
                                ThemeManager.setShowBatteryPercent(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Quick Settings Panel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Allows tapping on the right side of the status bar overlay to open the Quick Settings bento dropdown.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = enableQuickSettings,
                            onCheckedChange = {
                                enableQuickSettings = it
                                ThemeManager.setQuickSettingsEnabled(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Split-Click Bento Tiles", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("When enabled, clicking the circular icon toggles Wi-Fi/Bluetooth power, while clicking the rest opens the list. When disabled, clicking anywhere opens the list.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = splitClickBento,
                            onCheckedChange = {
                                splitClickBento = it
                                ThemeManager.setSplitClickBentoEnabled(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mouse Scroll Page Navigation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("When enabled, scrolling the mouse wheel over the music tile or empty space in the quick settings grid will transition pages.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = scrollPageSwitching,
                            onCheckedChange = {
                                scrollPageSwitching = it
                                ThemeManager.setScrollPageSwitchingEnabled(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Top Status Bar Height", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Adjust the height of the top status bar overlay ($statusBarHeight dp).", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Slider(
                        value = statusBarHeight.toFloat(),
                        onValueChange = {
                            statusBarHeight = it.toInt()
                            ThemeManager.setStatusBarHeight(context, it.toInt())
                        },
                        valueRange = 28f..56f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Notification Panel Scale
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val scaleText = when (notifPanelScale) {
                                0.8f -> "Small (0.8x)"
                                1.0f -> "Normal (1.0x)"
                                1.2f -> "Medium (1.2x)"
                                1.4f -> "Large (1.4x)"
                                1.6f -> "Extra Large (1.6x)"
                                else -> "${String.format(java.util.Locale.US, "%.1f", notifPanelScale)}x"
                            }
                            Text("Notification Panel Scale", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Adjust scale of the notification & settings panel ($scaleText).", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Slider(
                        value = notifPanelScale,
                        onValueChange = {
                            notifPanelScale = it
                            ThemeManager.setNotifPanelScale(context, it)
                            try {
                                FreeformOverlayService.requestRefresh()
                            } catch (e: Exception) {}
                        },
                        valueRange = 0.8f..1.6f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Notification Panel Height
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification Panel Height", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Adjust the maximum vertical runway for the notification panel ($notifPanelHeight dp).", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Slider(
                        value = notifPanelHeight.toFloat(),
                        onValueChange = {
                            notifPanelHeight = it.toInt()
                            ThemeManager.setNotifPanelHeight(context, it.toInt())
                            try {
                                FreeformOverlayService.requestRefresh()
                                if (livePreviewNotifHeight) {
                                    context.sendBroadcast(android.content.Intent("com.example.freeformshell.OPEN_NOTIFS"))
                                }
                            } catch (e: Exception) {}
                        },
                        valueRange = 300f..600f,
                        steps = 30,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Height Preview", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Switch(
                            checked = livePreviewNotifHeight,
                            onCheckedChange = { 
                                livePreviewNotifHeight = it 
                                if (it) {
                                    context.sendBroadcast(android.content.Intent("com.example.freeformshell.OPEN_NOTIFS"))
                                }
                            },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Notification Animation Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Animation Speed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Control how fast notifications morph and expand ($notifPanelAnimDuration ms).", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Slider(
                        value = notifPanelAnimDuration.toFloat(),
                        onValueChange = {
                            notifPanelAnimDuration = it.toInt()
                            ThemeManager.setNotifPanelAnimDuration(context, it.toInt())
                        },
                        valueRange = 350f..1000f,
                        steps = 13,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Notification Grouping Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Group Notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("When enabled, notifications from the same app are bundled into a single expandable group.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = notifGroupingEnabled,
                            onCheckedChange = {
                                notifGroupingEnabled = it
                                ThemeManager.setNotifGroupingEnabled(context, it)
                                try {
                                    FreeformOverlayService.requestRefresh()
                                } catch (e: Exception) {}
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Windows-style Notification Popups", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Display a temporary Android Expressive style popup on incoming notifications.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = windowsNotifsEnabled,
                            onCheckedChange = {
                                windowsNotifsEnabled = it
                                ThemeManager.setWindowsNotifsEnabled(context, it)
                            }
                        )
                    }

                    if (windowsNotifsEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Popup Position", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Select where on the screen the notification popup should appear.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            var expandedPos by remember { mutableStateOf(false) }
                            val positionLabels = listOf(
                                "Bottom-Right",
                                "Bottom-Left",
                                "Top-Right",
                                "Top-Left",
                                "Top-Center",
                                "Bottom-Center"
                            )
                            Box {
                                Button(
                                    onClick = { expandedPos = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(positionLabels.getOrElse(windowsNotifsPosition) { "Bottom-Right" }, color = MaterialTheme.colorScheme.onSurface)
                                }
                                DropdownMenu(
                                    expanded = expandedPos,
                                    onDismissRequest = { expandedPos = false }
                                ) {
                                    positionLabels.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                windowsNotifsPosition = index
                                                ThemeManager.setWindowsNotifsPosition(context, index)
                                                expandedPos = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Test Popups", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Send a test notification to verify the slide-in popup works.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Button(
                                onClick = {
                                    val serviceActive = FreeformOverlayService.getInstance() != null
                                    val hasPermission = ThemeManager.isNotificationServiceEnabled(context)
                                    if (!serviceActive) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Freeform Overlay Service is not running. Please start it on the home screen.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } else if (!hasPermission) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Please grant Notification Access first to receive popups.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        try {
                                            val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("LauncherSettings", "Failed to open settings", e)
                                        }
                                    } else {
                                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                        val channelId = "freeform_test_channel"
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            val channel = android.app.NotificationChannel(
                                                channelId,
                                                "Freeform Shell Test Notifications",
                                                android.app.NotificationManager.IMPORTANCE_DEFAULT
                                            )
                                            notificationManager.createNotificationChannel(channel)
                                        }

                                        val intent = android.content.Intent(context, MainActivity::class.java)
                                        val pendingIntent = android.app.PendingIntent.getActivity(
                                            context, 0, intent,
                                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                        )

                                        val replyIntent = android.content.Intent(context, MainActivity::class.java)
                                        val replyPendingIntent = android.app.PendingIntent.getActivity(
                                            context, 1, replyIntent,
                                            android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                        )

                                        val remoteInput = androidx.core.app.RemoteInput.Builder("key_text_reply")
                                            .setLabel("Reply")
                                            .build()

                                        val replyAction = androidx.core.app.NotificationCompat.Action.Builder(
                                            android.R.drawable.ic_menu_send,
                                            "Reply",
                                            replyPendingIntent
                                        ).addRemoteInput(remoteInput)
                                         .build()

                                        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                                            .setContentTitle("Test Notification")
                                            .setContentText("This is a preview of the Android & Aluminum OS slide-in toast.")
                                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                                            .setContentIntent(pendingIntent)
                                            .setAutoCancel(true)
                                            .addAction(android.R.drawable.ic_menu_view, "Launch App", pendingIntent)
                                            .addAction(replyAction)
                                            .build()

                                        notificationManager.notify(12345, notification)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text("Send Test", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Top Bar Clock Settings Submenu Card
                    var showClockSettings by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClockSettings = !showClockSettings }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Top Bar Clock Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Configure clock format, AM/PM visibility, and date styles.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Icon(
                            imageVector = if (showClockSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showClockSettings) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // A. Clock Format
                                Text("Clock Format", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                var expandedFormat by remember { mutableStateOf(false) }
                                val formatLabels = listOf("12-Hour", "24-Hour", "System Default")
                                val currentFormat = ThemeManager.getClockFormat(context)
                                Box {
                                    Button(
                                        onClick = { expandedFormat = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text(formatLabels.getOrElse(currentFormat) { "System Default" }, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    DropdownMenu(
                                        expanded = expandedFormat,
                                        onDismissRequest = { expandedFormat = false }
                                    ) {
                                        formatLabels.forEachIndexed { index, label ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    ThemeManager.setClockFormat(context, index)
                                                    expandedFormat = false
                                                    try {
                                                        FreeformOverlayService.requestRefresh()
                                                    } catch (e: Exception) {}
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // B. Show AM/PM
                                val currentFormatSetting = ThemeManager.getClockFormat(context)
                                val is24HourActive = currentFormatSetting == 1 || (currentFormatSetting == 2 && android.text.format.DateFormat.is24HourFormat(context))
                                if (!is24HourActive) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Show AM/PM", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            Text("Toggle the AM/PM suffix for 12-hour format.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                        var showAmPm by remember { mutableStateOf(ThemeManager.getClockShowAmPm(context)) }
                                        Switch(
                                            checked = showAmPm,
                                            onCheckedChange = {
                                                showAmPm = it
                                                ThemeManager.setClockShowAmPm(context, it)
                                                try {
                                                    FreeformOverlayService.requestRefresh()
                                                } catch (e: Exception) {}
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // C. Date Style
                                Text("Date Display Style", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                var expandedDateStyle by remember { mutableStateOf(false) }
                                val dateStyleLabels = listOf("Hide Date", "Short (Numbers only, e.g. 6/6/2026)", "Medium (No day name, e.g. Jun 6)", "Long (Short day name, e.g. Sat, Jun 6)", "Full (Long day name, e.g. Saturday, June 6)")
                                val currentDateStyle = ThemeManager.getClockDateStyle(context)
                                Box {
                                    Button(
                                        onClick = { expandedDateStyle = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text(dateStyleLabels.getOrElse(currentDateStyle) { "Long (Short day name)" }, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    DropdownMenu(
                                        expanded = expandedDateStyle,
                                        onDismissRequest = { expandedDateStyle = false }
                                    ) {
                                        dateStyleLabels.forEachIndexed { index, label ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    ThemeManager.setClockDateStyle(context, index)
                                                    expandedDateStyle = false
                                                    try {
                                                        FreeformOverlayService.requestRefresh()
                                                    } catch (e: Exception) {}
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartButton,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Button App Launcher", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (customAiAppPackage.isEmpty()) "Default Assistant (Gemini)" else "Launches: $customAiAppLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Button(
                            onClick = { showAppSelectionDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Change", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ongoing Launcher Customization",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Future phases will introduce Centered Dock, App Drawer, minimal title bars, snap caret gesture bindings, and dynamic mouse pointer resizing options.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        } else {
            // Classic style active -> Show Link to Desktop Tab
            Text(
                text = "Classic Setup Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DesktopAccessDisabled,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Dock & Margins Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "To configure custom safe-area margins, dock sizes, positions, or run taskbar auto-detection, please go to the Desktop Settings page.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onNavigateToTab(9) }, // Index 9 is Desktop Settings
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Monitor, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Go to Desktop Settings")
                    }
                }
            }
        }
    }

    if (showAppSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showAppSelectionDialog = false },
            title = { Text("Select AI Assistant App") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search apps") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    
                    if (isLoadingApps) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxHeight(0.6f)) {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            customAiAppPackage = ""
                                            ThemeManager.setCustomAiAppPackage(context, "")
                                            showAppSelectionDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.Gray.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SmartButton,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Default Assistant", fontWeight = FontWeight.Bold)
                                        Text("Invoke standard Android voice assistant", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                filteredApps.forEach { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                customAiAppPackage = app.packageName
                                                ThemeManager.setCustomAiAppPackage(context, app.packageName)
                                                showAppSelectionDialog = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Android,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(app.label, fontWeight = FontWeight.Bold)
                                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppSelectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class SettingsAppInfo(val packageName: String, val label: String)
