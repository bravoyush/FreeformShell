package com.example.freeformshell

import android.content.Context
import java.io.File
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    var desktopLockEnabled by remember { mutableStateOf(ThemeManager.isDesktopLockEnabled(context)) }
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
    var keyboardAllowed by remember { mutableStateOf(ThemeManager.isLockKeyboardAllowed(context)) }
    var musicControlStyle by remember { mutableStateOf(ThemeManager.getLockMusicControlStyle(context)) }
    var lockScreenDisplayTarget by remember { mutableStateOf(ThemeManager.getLockScreenDisplayTarget(context)) }

    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val destFile = File(context.filesDir, "lock_wallpaper.jpg")
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                wallpaperTheme = 1
                ThemeManager.setLockWallpaperTheme(context, 1)
                Toast.makeText(context, "Custom lock screen wallpaper updated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("LockScreenSettingsScreen", "Failed saving custom wallpaper", e)
                Toast.makeText(context, "Failed to load custom image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Dialog state for PIN config
    var showPinDialog by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf(ThemeManager.getDesktopLockPin(context)) }
    var tempPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configure Desktop PIN") },
            text = {
                Column {
                    Text(
                        text = "Set a secure 4-digit PIN to unlock your external widescreen desktop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                tempPin = it
                                pinError = ""
                            }
                        },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError.isNotEmpty()) {
                        Text(
                            text = pinError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempPin.length == 4) {
                            ThemeManager.setDesktopLockPin(context, tempPin)
                            pinValue = tempPin
                            showPinDialog = false
                        } else {
                            pinError = "PIN must be exactly 4 digits."
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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

        // Security & Keyguard Sync Controls
        LockSectionContainer(isExpressive = isExpressive, title = "Security & Keyguard Sync") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Desktop Lock Screen Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Desktop Lock Screen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Secures secondary and virtual displays automatically when the smartphone locks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = desktopLockEnabled,
                        onCheckedChange = {
                            desktopLockEnabled = it
                            ThemeManager.setDesktopLockEnabled(context, it)
                        }
                    )
                }

                if (desktopLockEnabled) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Lock Screen Target Displays Selector
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Lock Screen Target Displays",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select which screens should show the custom lock dashboard when locked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val options = listOf(
                                "Desktop Only (Secondary)",
                                "Desktop & Handset (Both)",
                                "Handset Only (Primary)"
                            )
                            options.forEachIndexed { index, name ->
                                FilterChip(
                                    selected = lockScreenDisplayTarget == index,
                                    onClick = {
                                        lockScreenDisplayTarget = index
                                        ThemeManager.setLockScreenDisplayTarget(context, index)
                                    },
                                    label = { Text(name) },
                                    shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Change Desktop PIN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Setup Desktop Lock PIN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Active PIN: ${pinValue.map { "*" }.joinToString("")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Button(
                            onClick = {
                                tempPin = ""
                                pinError = ""
                                showPinDialog = true
                            },
                            shape = if (isExpressive) CircleShape else ButtonDefaults.shape
                        ) {
                            Text("Configure PIN")
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

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

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // Allow Keyboard on Lock Screen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow Keyboard on Lock Screen",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Enables hardware keyboards to input passcode digits natively, or blocks keypresses entirely for secure visual PIN entry only.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = keyboardAllowed,
                                onCheckedChange = {
                                    keyboardAllowed = it
                                    ThemeManager.setLockKeyboardAllowed(context, it)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (desktopLockEnabled) {
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
                        "Gradient Backdrop",
                        "Custom Image (Click to pick)",
                        "Obsidian OLED Black"
                    )
                    themes.forEachIndexed { index, name ->
                        FilterChip(
                            selected = wallpaperTheme == index,
                            onClick = {
                                if (index == 1) {
                                    try {
                                        wallpaperLauncher.launch("image/*")
                                    } catch (e: java.lang.Exception) {
                                        Log.e("LockScreenSettingsScreen", "Failed to launch image picker", e)
                                    }
                                } else {
                                    wallpaperTheme = index
                                    ThemeManager.setLockWallpaperTheme(context, index)
                                }
                            },
                            label = { Text(name) },
                            shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                        )
                    }
                }
            }

            // Background Glass Blur Radius
            if (wallpaperTheme != 2) { // Hide blur if OLED Black is chosen
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
                        "Thin Stacked",
                        "Google Pixel Style",
                        "Nothing OS Style",
                        "Elegant Analog",
                        "None"
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
                            "Full Notification View"
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

            // Lock Screen Music Controller
            LockSectionContainer(isExpressive = isExpressive, title = "Lock Screen Music Controller") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose visual integration style when music is playing:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val musicStyles = listOf(
                            "Below Clock",
                            "Above Notifications (Side Split)",
                            "Backdrop Art + Visualizer"
                        )
                        musicStyles.forEachIndexed { index, name ->
                            FilterChip(
                                selected = musicControlStyle == index,
                                onClick = {
                                    musicControlStyle = index
                                    ThemeManager.setLockMusicControlStyle(context, index)
                                },
                                label = { Text(name) },
                                shape = if (isExpressive) CircleShape else FilterChipDefaults.shape
                            )
                        }
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
