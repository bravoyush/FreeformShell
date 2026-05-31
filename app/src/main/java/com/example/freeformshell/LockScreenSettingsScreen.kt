package com.example.freeformshell

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreenSettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val isExpressive = ThemeManager.getAppUiStyle(context) == 1

    var wallpaperTheme by remember { mutableStateOf(ThemeManager.getLockWallpaperTheme(context)) }
    var blurRadius by remember { mutableStateOf(ThemeManager.getLockBlurRadius(context)) }
    var clockPreset by remember { mutableStateOf(ThemeManager.getLockClockPreset(context)) }
    var lockSyncDismiss by remember { mutableStateOf(ThemeManager.isLockSyncDismissEnabled(context)) }
    var keypadScrambler by remember { mutableStateOf(ThemeManager.isLockKeypadScramblerEnabled(context)) }
    var keyboardBypass by remember { mutableStateOf(ThemeManager.isLockKeyboardBypassEnabled(context)) }
    var notificationsMode by remember { mutableStateOf(ThemeManager.getLockNotificationsMode(context)) }
    var aodEnabled by remember { mutableStateOf(ThemeManager.isLockAodEnabled(context)) }
    var weatherEnabled by remember { mutableStateOf(ThemeManager.isLockWeatherWidgetEnabled(context)) }
    var calendarEnabled by remember { mutableStateOf(ThemeManager.isLockCalendarWidgetEnabled(context)) }

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Title
        Text(
            text = "Desktop Lock Screen",
            style = if (isExpressive) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Secure your external monitor. Prevent touch leakage when your primary device is locked, and style your Pixel-inspired secondary keyguard.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        // Backdrop Theme Selection
        LockSectionContainer(isExpressive = isExpressive, title = "Backdrop Wallpaper Theme") {
            Text(
                text = "Choose the canvas styling for the lock screen:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val themes = listOf(
                    "System Wallpaper (Refracted Glass)",
                    "Obsidian Black (OLED Saver)",
                    "Liquid Glass Gloss"
                )
                themes.forEachIndexed { index, name ->
                    FilterChip(
                        selected = wallpaperTheme == index,
                        onClick = {
                            wallpaperTheme = index
                            ThemeManager.setLockWallpaperTheme(context, index)
                        },
                        label = { Text(name) },
                        shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                    )
                }
            }
        }

        // Background Glass Blur Radius
        if (wallpaperTheme != 1) { // Hide blur if Obsidian Black OLED Saver is chosen
            LockSectionContainer(isExpressive = isExpressive, title = "Gaussian Glass Blur Radius") {
                Text(
                    text = "Blur depth: ${blurRadius.toInt()}dp",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = blurRadius,
                    onValueChange = {
                        blurRadius = it
                        ThemeManager.setLockBlurRadius(context, it)
                    },
                    valueRange = 0f..100f
                )
                Text(
                    text = "Controls the depth of dynamic backdrop blending. High values increase readability, lower values preserve background colors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        // Clock Layout Presets
        LockSectionContainer(isExpressive = isExpressive, title = "Clock Layout Presets") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val clocks = listOf(
                    "Pixel Stacked Numeric",
                    "Elegant Analog",
                    "Minimalist Text Banner"
                )
                clocks.forEachIndexed { index, name ->
                    FilterChip(
                        selected = clockPreset == index,
                        onClick = {
                            clockPreset = index
                            ThemeManager.setLockClockPreset(context, index)
                        },
                        label = { Text(name) },
                        shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                    )
                }
            }
        }

        // Security & Keyguard Sync Controls
        LockSectionContainer(isExpressive = isExpressive, title = "Security & Keyguard Sync") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Handset Lock Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Handset Lock-Sync (Auto-Dismiss)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatically dismiss the desktop lock screen when you unlock your primary smartphone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = lockSyncDismiss,
                        onCheckedChange = {
                            lockSyncDismiss = it
                            ThemeManager.setLockSyncDismissEnabled(context, it)
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Keyboard Bypass (Read-Only Mode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keyboard Bypass (Read-Only Mode)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Turns the lock screen into a clean information center banner without numerical input capabilities, keeping screens secure but completely locked until handset authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = keyboardBypass,
                        onCheckedChange = {
                            keyboardBypass = it
                            ThemeManager.setLockKeyboardBypassEnabled(context, it)
                        }
                    )
                }

                if (!keyboardBypass) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Shoulder-Surfing Scrambler
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shoulder-Surfing Scrambler",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dynamically randomizes and scrambles keypad button placements to hide your input trace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = keypadScrambler,
                            onCheckedChange = {
                                keypadScrambler = it
                                ThemeManager.setLockKeypadScramblerEnabled(context, it)
                            }
                        )
                    }
                }
            }
        }

        // Widgets & Notification Previews
        LockSectionContainer(isExpressive = isExpressive, title = "Widgets & Notification Previews") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Notifications Mode
                Text("Notification Preview Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        "Hide Notifications",
                        "App Icons Only",
                        "Full Content Cards"
                    )
                    modes.forEachIndexed { index, name ->
                        FilterChip(
                            selected = notificationsMode == index,
                            onClick = {
                                notificationsMode = index
                                ThemeManager.setLockNotificationsMode(context, index)
                            },
                            label = { Text(name) },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Always-On Display (AOD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always-On Display (AOD)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dims secondary displays and renders a low-power clock overlay during lock states.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = aodEnabled,
                        onCheckedChange = {
                            aodEnabled = it
                            ThemeManager.setLockAodEnabled(context, it)
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Weather Feed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Weather & Calendar Widgets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Embed active weather forecasts and event schedules directly on your lock dashboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = weatherEnabled,
                            onClick = {
                                weatherEnabled = !weatherEnabled
                                ThemeManager.setLockWeatherWidgetEnabled(context, weatherEnabled)
                            },
                            label = { Text("Weather") },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                        FilterChip(
                            selected = calendarEnabled,
                            onClick = {
                                calendarEnabled = !calendarEnabled
                                ThemeManager.setLockCalendarWidgetEnabled(context, calendarEnabled)
                            },
                            label = { Text("Calendar") },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LockSectionContainer(
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
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
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
