package com.example.freeformshell

import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShizukuGuideScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 9 })
    
    var currentPage by remember { mutableIntStateOf(0) }
    
    // Sync pagerState with our manual currentPage for desktop/mobile consistency
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) {
            pagerState.animateScrollToPage(currentPage)
        }
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val isDesktop = maxWidth > 700.dp
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null)
                }
                Text("Shizuku Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(48.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isDesktop) {
                    // Desktop Two-Pane Layout
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Pane: Text & Controls (40%)
                        Column(
                            modifier = Modifier.weight(0.4f).fillMaxHeight().padding(start = 48.dp, end = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            GuideTextContent(currentPage)
                            Spacer(Modifier.height(24.dp))
                            ShizukuActionControls(currentPage)
                        }
                        // Right Pane: Media (60%)
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight().padding(24.dp), contentAlignment = Alignment.Center) {
                            GuideMediaContent(currentPage, isDesktop = true)
                        }
                    }
                } else {
                    // Mobile Layout
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false
                    ) { page ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GuideTextContent(page)
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                GuideMediaContent(page, isDesktop = false)
                            }
                            Spacer(Modifier.height(12.dp))
                            ShizukuActionControls(page)
                        }
                    }
                }
            }

            // Bottom controls with Modern Progress Bar
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
                val progress by animateFloatAsState(
                    targetValue = (currentPage + 1).toFloat() / 9f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "Progress"
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                ),
                                shape = CircleShape
                            )
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPage > 0) {
                        TextButton(onClick = { currentPage-- }) {
                            Text("Back", fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Spacer(Modifier.width(64.dp))
                    }

                    Button(
                        onClick = {
                            if (currentPage < 8) {
                                currentPage++
                            } else {
                                onClose()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            if (currentPage < 8) "Next Step" else "Finish Guide",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GuideTextContent(page: Int) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (fadeIn(animationSpec = tween(500)) + 
             slideInHorizontally(animationSpec = tween(500)) { it * direction }
            ).togetherWith(
                fadeOut(animationSpec = tween(400)) + 
                slideOutHorizontally(animationSpec = tween(400)) { -it * direction }
            )
        },
        label = "TextTransition"
    ) { targetPage ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (title, subtitle) = when (targetPage) {
                0 -> "Status Check" to "Let's see if Shizuku is ready"
                1 -> "Wireless Debugging" to "Tap 'Pairing' in the Shizuku app"
                2 -> "Start Pairing" to "Follow the internal instructions"
                3 -> "Pair Device" to "Tap 'Pair device with pairing code'"
                4 -> "Enter Pairing Code" to "Input the code from the popup"
                5 -> "Confirming Service" to "Look for the success notification"
                6 -> "Pairing Successful" to "You can now start the daemon"
                7 -> "Authorized Apps" to "Manage which apps can use Shizuku"
                8 -> "Grant Permission" to "Toggle Freeform Shell to allowed"
                else -> "" to ""
            }
            
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ShizukuActionControls(page: Int) {
    val context = LocalContext.current
    val shizukuPackage = "moe.shizuku.privileged.api"

    AnimatedContent(
        targetState = page,
        label = "ActionTransition"
    ) { targetPage ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (targetPage) {
                0 -> {
                    val isRunning = remember {
                        try { rikka.shizuku.Shizuku.pingBinder() } catch (e: Exception) { false }
                    }
                    val isInstalled = remember {
                        try {
                            context.packageManager.getPackageInfo(shizukuPackage, 0)
                            true
                        } catch (e: Exception) {
                            context.packageManager.getLaunchIntentForPackage(shizukuPackage) != null
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).background(color = if (isRunning) Color(0xFF4CAF50) else if (isInstalled) Color(0xFFFF9800) else Color(0xFFF44336), shape = CircleShape))
                                    Spacer(Modifier.width(10.dp))
                                    Text(text = if (isRunning) "SERVICE ACTIVE" else if (isInstalled) "DAEMON INACTIVE" else "NOT INSTALLED", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isRunning) Color(0xFF4CAF50) else if (isInstalled) Color(0xFFFF9800) else Color(0xFFF44336))
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(text = if (isRunning) "Everything is set! You can proceed or close." else if (isInstalled) "Shizuku is installed but inactive." else "Required for advanced window management.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (!isInstalled) {
                            Button(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=rikka.app.shizuku"))) },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Install Shizuku", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val intent = context.packageManager.getLaunchIntentForPackage(shizukuPackage)
                                    if (intent != null) context.startActivity(intent)
                                    else {
                                        try {
                                            val launchIntent = Intent(Intent.ACTION_MAIN)
                                            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                                            launchIntent.setPackage(shizukuPackage)
                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(launchIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Please open Shizuku manually.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Launch Shizuku", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                1, 3 -> {
                    Button(onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        context.startActivity(intent)
                    }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text(if (targetPage == 1) "Open Developer Options" else "Open Wireless Debugging")
                    }
                }
                6 -> {
                    Button(onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage(shizukuPackage)
                        if (intent != null) context.startActivity(intent)
                    }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("Launch Shizuku")
                    }
                }
                7 -> {
                    Button(onClick = {
                        val authIntent = Intent("rikka.shizuku.intent.action.OPEN_AUTHORIZATION_MANAGER")
                        authIntent.setPackage(shizukuPackage)
                        authIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(authIntent)
                        } catch (e: Exception) {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(shizukuPackage)
                            if (launchIntent != null) context.startActivity(launchIntent)
                        }
                    }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("Open Shizuku Manager")
                    }
                }
            }
        }
    }
}

@Composable
fun GuideMediaContent(page: Int, isDesktop: Boolean) {
    val shizukuPackage = "moe.shizuku.privileged.api"
    
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (isDesktop) {
                (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.95f, animationSpec = tween(600)))
                .togetherWith(fadeOut(animationSpec = tween(400)))
            } else {
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(animationSpec = tween(500)) + 
                 scaleIn(initialScale = 0.85f, animationSpec = tween(500)) +
                 slideInHorizontally(animationSpec = tween(500)) { it * direction }
                ).togetherWith(
                    fadeOut(animationSpec = tween(400)) + 
                    scaleOut(targetScale = 1.1f, animationSpec = tween(400)) +
                    slideOutHorizontally(animationSpec = tween(400)) { -it * direction }
                )
            }
        },
        label = "MediaTransition"
    ) { targetPage ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (targetPage) {
                0 -> {
                    // Hero Section
                    Box(contentAlignment = Alignment.Center) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.2f, targetValue = 0.5f,
                            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse)
                        )
                        Box(modifier = Modifier.size(240.dp).graphicsLayer(alpha = glowAlpha).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary, Color.Transparent)), shape = CircleShape))
                        Surface(modifier = Modifier.size(160.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIcon(shizukuPackage, modifier = Modifier.size(100.dp))
                            }
                        }
                    }
                }
                1 -> {
                    Box(contentAlignment = Alignment.Center) {
                        GuideImage(imageResName = "shizuku_wireless_start")
                        PulseRing(modifier = Modifier.offset(x = (-30).dp, y = (-50).dp)) 
                    }
                }
                2 -> {
                    Box(contentAlignment = Alignment.Center) {
                        GuideImage(imageResName = "shizuku_pairing_guide")
                        FingerTapAnimation(modifier = Modifier.offset(y = 140.dp))
                    }
                }
                3 -> {
                    Box(contentAlignment = Alignment.Center) {
                        GuideImage(imageResName = "system_wireless_debugging")
                        PulseRing(modifier = Modifier.offset(y = 140.dp))
                    }
                }
                4 -> GuideImage(imageResName = "system_pairing_dialog")
                5 -> GuideImage(imageResName = "notif_pairing_service")
                6 -> GuideImage(imageResName = "notif_pairing_success")
                7 -> {
                    Box(contentAlignment = Alignment.Center) {
                        GuideImage(imageResName = "shizuku_main_running")
                        PulseRing(modifier = Modifier.offset(y = (-65).dp))
                    }
                }
                8 -> {
                    Box(contentAlignment = Alignment.Center) {
                        GuideImage(imageResName = "shizuku_auth_list")
                        FingerTapAnimation(modifier = Modifier.offset(x = 90.dp, y = 30.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GuideImage(imageResName: String) {
    val context = LocalContext.current
    val resId = remember(imageResName) {
        val cleanName = imageResName.substringBeforeLast(".").trim()
        context.resources.getIdentifier(cleanName, "drawable", context.packageName).let {
            if (it == 0) context.resources.getIdentifier(cleanName, "raw", context.packageName)
            else it
        }
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    if (resId != 0) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxHeight(0.95f)
                .fillMaxWidth(0.9f),
            color = Color.Black
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resId)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .fillMaxWidth(0.7f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Missing: $imageResName", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PulseRing(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart)
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart)
    )

    Box(
        modifier = modifier
            .size(70.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
    )
}

@Composable
fun FingerTapAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val translateY by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    Icon(
        imageVector = Icons.Default.TouchApp,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(56.dp)
            .offset(y = translateY.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(12.dp, CircleShape)
    )
}
