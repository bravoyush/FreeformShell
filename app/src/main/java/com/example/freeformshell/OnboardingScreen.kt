package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    isReopen: Boolean = false,
    onOpenShizukuGuide: (() -> Unit)? = null,
    onOpenTaskbarGuide: (() -> Unit)? = null,
    onOpenDevOptionsGuide: (() -> Unit)? = null,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 8 })
    var showLocalShizukuGuide by remember { mutableStateOf(false) }
    var showLocalTaskbarGuide by remember { mutableStateOf(false) }
    var showLocalDevOptionsGuide by remember { mutableStateOf(false) }

    val handleShizukuGuide = {
        if (onOpenShizukuGuide != null) {
            onOpenShizukuGuide()
        } else {
            showLocalShizukuGuide = true
        }
    }

    val handleTaskbarGuide = {
        if (onOpenTaskbarGuide != null) {
            onOpenTaskbarGuide()
        } else {
            showLocalTaskbarGuide = true
        }
    }

    val handleDevOptionsGuide = {
        if (onOpenDevOptionsGuide != null) {
            onOpenDevOptionsGuide()
        } else {
            showLocalDevOptionsGuide = true
        }
    }
    
    val steps = listOf(
        OnboardingStep.Welcome,
        OnboardingStep.DevOptions,
        OnboardingStep.Shizuku,
        OnboardingStep.Overlay,
        OnboardingStep.Notifications,
        OnboardingStep.LauncherSelection,
        OnboardingStep.Features,
        OnboardingStep.Ready
    )

    var showShizukuWarning by remember { mutableStateOf(false) }
    var autoAdvanceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Decorative background elements
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header with progress indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(8) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(if (isSelected) 28.dp else 6.dp, label = "indicator")
                    
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false
            ) { page ->
                OnboardingStepContent(
                    step = steps[page],
                    onOpenShizukuGuide = handleShizukuGuide,
                    onOpenTaskbarGuide = handleTaskbarGuide,
                    onOpenDevOptionsGuide = handleDevOptionsGuide,
                    showShizukuWarning = showShizukuWarning
                )
            }

            // Footer navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            autoAdvanceJob?.cancel()
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.width(64.dp))
                }

                if (pagerState.currentPage < 7) {
                    val isStepComplete = when (steps[pagerState.currentPage]) {
                        OnboardingStep.Shizuku -> {
                            var isAvailable by remember { mutableStateOf(Shizuku.pingBinder()) }
                            var hasPerm by remember { mutableStateOf(isAvailable && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) }
                            
                            LaunchedEffect(Unit) {
                                while (true) {
                                    isAvailable = Shizuku.pingBinder()
                                    hasPerm = isAvailable && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    kotlinx.coroutines.delay(1000)
                                }
                            }
                            hasPerm
                        }
                        OnboardingStep.Overlay -> Settings.canDrawOverlays(context)
                        else -> true
                    }

                    Button(
                        onClick = {
                            val currentStep = steps[pagerState.currentPage]
                            if (currentStep == OnboardingStep.Shizuku && !isStepComplete) {
                                if (!showShizukuWarning) {
                                    showShizukuWarning = true
                                    Toast.makeText(context, "Warning: Shizuku is required for full multi-window functionality.", Toast.LENGTH_SHORT).show()
                                    autoAdvanceJob?.cancel()
                                    autoAdvanceJob = scope.launch {
                                        kotlinx.coroutines.delay(2500)
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                } else {
                                    autoAdvanceJob?.cancel()
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            } else {
                                autoAdvanceJob?.cancel()
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        enabled = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Continue")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            if (!isReopen) ThemeManager.setSetupComplete(context, true)
                            onComplete()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        val text = if (isReopen) "Close Guide" else "Start Freeform Shell"
                        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        if (showLocalShizukuGuide) {
            Dialog(
                onDismissRequest = { showLocalShizukuGuide = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShizukuGuideScreen(onClose = { showLocalShizukuGuide = false })
                }
            }
        }

        if (showLocalTaskbarGuide) {
            Dialog(
                onDismissRequest = { showLocalTaskbarGuide = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskbarGuideScreen(onClose = { showLocalTaskbarGuide = false })
                }
            }
        }

        if (showLocalDevOptionsGuide) {
            Dialog(
                onDismissRequest = { showLocalDevOptionsGuide = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DeveloperOptionsGuideScreen(onClose = { showLocalDevOptionsGuide = false })
                }
            }
        }
    }
}

sealed class OnboardingStep {
    object Welcome : OnboardingStep()
    object DevOptions : OnboardingStep()
    object Shizuku : OnboardingStep()
    object Overlay : OnboardingStep()
    object Notifications : OnboardingStep()
    object LauncherSelection : OnboardingStep()
    object Features : OnboardingStep()
    object Ready : OnboardingStep()
}

@Composable
fun OnboardingStepContent(
    step: OnboardingStep,
    onOpenShizukuGuide: () -> Unit = {},
    onOpenTaskbarGuide: () -> Unit = {},
    onOpenDevOptionsGuide: () -> Unit = {},
    showShizukuWarning: Boolean = false
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            OnboardingStep.Welcome -> {
                OnboardingIllustration(Icons.Default.RocketLaunch, MaterialTheme.colorScheme.primary)
                Text(
                    "Welcome to FreeformShell",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Turn your Android device into a powerful multi-window desktop workstation.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            OnboardingStep.DevOptions -> {
                OnboardingIllustration(Icons.Default.Build, MaterialTheme.colorScheme.secondary)
                Text(
                    "Enable Developer Options",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Required for multi-window freeform support and external desktop mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recommended Settings:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("1. Enable 'Enable freeform windows'", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("2. Enable 'Force desktop mode'", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("3. Enable 'Wireless Debugging' (for Shizuku)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Open Dev Options", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open About Phone", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Card(
                    onClick = onOpenDevOptionsGuide,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Developer Options Guide", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Interactive step-by-step walkthrough", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OnboardingStep.Shizuku -> {
                OnboardingIllustration(Icons.Default.Shield, Color(0xFF4CAF50))
                Text(
                    "Enable Shizuku",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "FreeformShell uses Shizuku to manage windows and system tasks with zero latency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                
                var isShizukuRunning by remember { mutableStateOf(Shizuku.pingBinder()) }
                var hasShizukuPerm by remember { mutableStateOf(isShizukuRunning && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) }

                LaunchedEffect(Unit) {
                    while (true) {
                        isShizukuRunning = Shizuku.pingBinder()
                        hasShizukuPerm = isShizukuRunning && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                        kotlinx.coroutines.delay(1000)
                    }
                }

                if (!isShizukuRunning) {
                    Button(
                        onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage("rikka.app.shizuku")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text("Open Shizuku App")
                    }
                } else if (!hasShizukuPerm) {
                    Button(
                        onClick = { Shizuku.requestPermission(0) }
                    ) {
                        Text("Grant Shizuku Permission")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("Shizuku is ready!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(
                    onClick = onOpenShizukuGuide,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shizuku Setup Guide", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Interactive step-by-step walkthrough", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (showShizukuWarning && !hasShizukuPerm) {
                    Spacer(Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Warning: Shizuku is required for full multi-window functionality. Continuing in 2.5s...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
            OnboardingStep.Overlay -> {
                OnboardingIllustration(Icons.Default.Layers, Color(0xFF2196F3))
                Text(
                    "Overlay Permission",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Required to show the Aluminum status bar, window title bars, and desktop dock.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                
                var hasOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
                LaunchedEffect(Unit) {
                    while(true) {
                        hasOverlayPerm = Settings.canDrawOverlays(context)
                        kotlinx.coroutines.delay(1000)
                    }
                }

                if (!hasOverlayPerm) {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Grant Overlay Permission")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("Overlay permission granted!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
            OnboardingStep.Notifications -> {
                OnboardingIllustration(Icons.Default.Notifications, Color(0xFFFF9800))
                Text(
                    "Notification Access",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Optional. Allows you to see and interact with notifications directly from the status bar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                
                var hasNotifPerm by remember { mutableStateOf(ThemeManager.isNotificationServiceEnabled(context)) }
                LaunchedEffect(Unit) {
                    while(true) {
                        hasNotifPerm = ThemeManager.isNotificationServiceEnabled(context)
                        kotlinx.coroutines.delay(1000)
                    }
                }

                if (!hasNotifPerm) {
                    Button(
                        onClick = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    ) {
                        Text("Grant Notification Access")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("Notification access enabled!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
            OnboardingStep.LauncherSelection -> {
                LauncherAnimatedHero()
                Spacer(Modifier.height(16.dp))
                Text(
                    "3rd-Party Launcher",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "We recommend using a 3rd-party launcher for Desktop Mode on external displays, as some stock phone launchers do not support launching apps in freeform windows on secondary screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Taskbar Card
                    Card(
                        onClick = onOpenTaskbarGuide,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppIcon("com.farmerbb.taskbar", modifier = Modifier.size(26.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Taskbar (by Farmerbb)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Classic Windows start menu & taskbar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // SmartDock Card
                    Card(
                        onClick = {
                            Toast.makeText(context, "Guide coming soon! Currently, guide is available for Taskbar Launcher.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppIcon("cu.axel.smartdock", modifier = Modifier.size(26.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SmartDock / Youki Dex", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                Text("Modern desktop dock environment", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("SOON", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Footer Info Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Note: Currently, interactive setup guide is available for Taskbar launcher.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            }
            OnboardingStep.Features -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Pro Tips", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    
                    FeatureTip("Status Bar", "Tap the top edge to expand Quick Settings and Notifications.", Icons.Default.VerticalAlignTop)
                    FeatureTip("Window Snapping", "Drag windows to the edges to split-screen or maximize.", Icons.Default.GridView)
                    FeatureTip("App Dock", "Launch apps instantly from the Aluminum dock.", Icons.Default.ViewStream)
                }
            }
            OnboardingStep.Ready -> {
                OnboardingIllustration(Icons.Default.Celebration, MaterialTheme.colorScheme.primary)
                Text(
                    "You're all set!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "FreeformShell is configured and ready to transform your mobile experience.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun OnboardingIllustration(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = color.copy(alpha = 0.1f),
            shape = CircleShape
        ) {}
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = color
        )
    }
}

@Composable
fun FeatureTip(title: String, desc: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LauncherAnimatedHero() {
    val items = remember {
        listOf(
            "com.farmerbb.taskbar" to "Taskbar",
            "cu.axel.smartdock" to "SmartDock",
            "com.youki.dex" to "Youki Dex"
        )
    }
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2500)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f, targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
            label = "Glow"
        )
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer(alpha = glowAlpha)
                .background(
                    brush = Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // Static circle base with shadow (never re-scales)
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Only the icon inside animates over the static circle
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.7f, animationSpec = tween(500)))
                            .togetherWith(fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 1.15f, animationSpec = tween(400)))
                    },
                    label = "LauncherIconTransition"
                ) { targetIndex ->
                    val (pkg, name) = items[targetIndex]
                    AppIcon(pkg, modifier = Modifier.size(50.dp))
                }
            }
        }
    }
}
