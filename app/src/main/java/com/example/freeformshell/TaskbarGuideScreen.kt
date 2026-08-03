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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskbarGuideScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 11 })
    
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
                Text("Taskbar Setup Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                            TaskbarGuideTextContent(currentPage)
                            Spacer(Modifier.height(24.dp))
                            TaskbarActionControls(currentPage)
                        }
                        // Right Pane: Media (60%)
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight().padding(24.dp), contentAlignment = Alignment.Center) {
                            TaskbarGuideMediaContent(currentPage, isDesktop = true)
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
                            TaskbarGuideTextContent(page)
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                TaskbarGuideMediaContent(page, isDesktop = false)
                            }
                            Spacer(Modifier.height(12.dp))
                            TaskbarActionControls(page)
                        }
                    }
                }
            }

            // Bottom controls with Modern Progress Bar
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
                val progress by animateFloatAsState(
                    targetValue = (currentPage + 1).toFloat() / 11f,
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
                                    colors = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
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
                            if (currentPage < 10) {
                                currentPage++
                            } else {
                                onClose()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            if (currentPage < 10) "Next Step" else "Finish Guide",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskbarGuideTextContent(page: Int) {
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
                0 -> "Welcome to Taskbar" to "Classic desktop UI for external displays"
                1 -> "Set as Default" to "Make Taskbar your system launcher"
                2 -> "Desktop Mode" to "Configure the secondary display behavior"
                3 -> "Primary Launcher" to "Keep your phone's home screen intact"
                4 -> "Freeform Mode" to "Enable the main Freeform toggle"
                5 -> "Window Behavior" to "Set how apps open in desktop mode"
                6 -> "Taskbar Position" to "Choose where the bar sits on your screen"
                7 -> "General Settings" to "Recommended tweaks for the best experience"
                8 -> "Navigation" to "Add system buttons to the taskbar"
                9 -> "Live Positioning" to "How Taskbar moves on your monitor"
                10 -> "Display Bounds" to "Prevent windows scaling outside taskbar"
                else -> "" to ""
            }
            
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TaskbarActionControls(page: Int) {
    val context = LocalContext.current
    val taskbarPackage = "com.farmerbb.taskbar"

    AnimatedContent(
        targetState = page,
        label = "ActionTransition"
    ) { targetPage ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (targetPage) {
                0 -> {
                    val isInstalled = remember { 
                        try { context.packageManager.getPackageInfo(taskbarPackage, 0); true } 
                        catch (e: Exception) { context.packageManager.getLaunchIntentForPackage(taskbarPackage) != null }
                    }
                    if (!isInstalled) {
                        Button(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$taskbarPackage"))) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Install Taskbar", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Button(
                            onClick = {
                                val intent = context.packageManager.getLaunchIntentForPackage(taskbarPackage)
                                if (intent != null) context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Launch Taskbar", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                1 -> {
                    Button(onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                        context.startActivity(intent)
                    }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("Open Home Settings")
                    }
                }
                3 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "Crucial: Select your phone's launcher as 'Primary Launcher' so Taskbar can redirect to it for normal use.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                10 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "Tip: Use these settings to keep app windows within the Taskbar area.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskbarGuideMediaContent(page: Int, isDesktop: Boolean) {
    val taskbarPackage = "com.farmerbb.taskbar"
    
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
                        Box(modifier = Modifier.size(240.dp).graphicsLayer(alpha = glowAlpha).background(brush = Brush.radialGradient(listOf(MaterialTheme.colorScheme.tertiary, Color.Transparent)), shape = CircleShape))
                        Surface(modifier = Modifier.size(160.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIcon(taskbarPackage, modifier = Modifier.size(100.dp))
                            }
                        }
                    }
                }
                1 -> GuideMedia(mediaName = "setting_taskbar_as_default_launcher.mp4")
                2 -> GuideMedia(mediaName = "enabling_desktopmode_taskbar.mp4")
                3 -> GuideMedia(mediaName = "setting_primary_launcher_in_taskbarapp.mp4")
                4 -> GuideMedia(mediaName = "enabling_freeform_for_taskbar.gif")
                5 -> GuideMedia(mediaName = "freeform_window_setting_in_taskbar_app.mp4")
                6 -> GuideMedia(mediaName = "taskbar_general_settings_tab_set_taskbar_potion_on_display.mp4")
                7 -> GuideMedia(mediaName = "taskbar_recommend_general_tab_setting.png")
                8 -> GuideMedia(mediaName = "taskbarhome_to_advance_tab_adding_navigation_button_recommended.mp4")
                9 -> GuideMedia(mediaName = "taskbar_live_position.mp4")
                10 -> GuideMedia(mediaName = "freeform_bounds_fix.mp4")
            }
        }
    }
}

@Composable
fun GuideMedia(mediaName: String) {
    val context = LocalContext.current
    val isVideo = mediaName.endsWith(".mp4")
    
    val isLandscape = mediaName == "taskbar_live_position.mp4" || mediaName == "freeform_bounds_fix.mp4"
    val targetAspectRatio = if (isLandscape) 16f / 9f else 9f / 21f
    
    val resId = remember(mediaName) {
        val cleanName = mediaName.substringBeforeLast(".").trim()
        context.resources.getIdentifier(cleanName, "raw", context.packageName).let {
            if (it == 0) context.resources.getIdentifier(cleanName, "drawable", context.packageName)
            else it
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier
            .then(if (isLandscape) Modifier.fillMaxWidth(0.95f) else Modifier.fillMaxHeight(0.95f))
            .aspectRatio(targetAspectRatio),
        color = Color.Black
    ) {
        if (resId != 0) {
            if (isVideo) {
                VideoPlayer(resId, isLandscape)
            } else {
                val imageLoader = remember {
                    ImageLoader.Builder(context).components {
                        if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                    }.build()
                }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(resId).crossfade(true).build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Media Missing:\n$mediaName", color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun VideoPlayer(resId: Int, isLandscape: Boolean = false) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$resId")
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(resId) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = if (isLandscape) AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH else AspectRatioFrameLayout.RESIZE_MODE_FILL
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
