package com.example.freeformshell

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build.VERSION.SDK_INT
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
fun DeveloperOptionsGuideScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val totalPages = 6
    val pagerState = rememberPagerState(pageCount = { totalPages })
    
    var currentPage by remember { mutableIntStateOf(0) }
    
    // Sync pagerState with currentPage for desktop/mobile consistency
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
                    Icon(Icons.Default.Close, contentDescription = "Close Guide")
                }
                Text(
                    "Developer Options Setup",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(48.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isDesktop) {
                    // Desktop Two-Pane Layout
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Pane: Text & Controls (40%)
                        Column(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight()
                                .padding(start = 48.dp, end = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            DevGuideTextContent(currentPage)
                            Spacer(Modifier.height(24.dp))
                            DevGuideActionControls(currentPage)
                        }
                        // Right Pane: Media (60%)
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DevGuideMediaContent(currentPage, isDesktop = true)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DevGuideTextContent(page)
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                DevGuideMediaContent(page, isDesktop = false)
                            }
                            Spacer(Modifier.height(12.dp))
                            DevGuideActionControls(page)
                        }
                    }
                }
            }

            // Bottom controls with Modern Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                val progress by animateFloatAsState(
                    targetValue = (currentPage + 1).toFloat() / totalPages.toFloat(),
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
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
                
                Spacer(Modifier.height(20.dp))
                
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
                            if (currentPage < totalPages - 1) {
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
                            if (currentPage < totalPages - 1) "Next Step" else "Finish Guide",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DevGuideTextContent(page: Int) {
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
                0 -> "Unlock Developer Options" to "Open About Phone and tap Build Number 7 times"
                1 -> "Open Developer Settings" to "Navigate to System -> Developer Options"
                2 -> "Enable Freeform Windows" to "Scroll to Apps section and enable 'Enable freeform windows'"
                3 -> "Enable Force Desktop Mode" to "Scroll to Hardware section and enable 'Force desktop mode'"
                4 -> "Wireless Debugging (Optional)" to "Enable Wireless Debugging for Shizuku pairing"
                5 -> "Setup Complete!" to "Developer options are configured for FreeformShell"
                else -> "" to ""
            }
            
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DevGuideActionControls(page: Int) {
    val context = LocalContext.current

    AnimatedContent(
        targetState = page,
        label = "ActionTransition"
    ) { targetPage ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (targetPage) {
                0 -> {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open About Phone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                1, 2, 3, 4 -> {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Developer Options", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                5 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("All required settings configured!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DevGuideMediaContent(page: Int, isDesktop: Boolean) {
    val mediaRes = when (page) {
        0 -> "dev_guide_step_1_unlock.png"
        1 -> "dev_guide_step_2_open.mp4"
        2 -> "dev_guide_step_3_freeform.mp4"
        3 -> "dev_guide_step_4_desktop_mode.mp4"
        4 -> "system_wireless_debugging.png"
        5 -> "dev_guide_step_4_desktop_mode.mp4"
        else -> ""
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (mediaRes.isNotEmpty()) {
            DevGuideMedia(mediaName = mediaRes)
        }
    }
}

@Composable
fun DevGuideMedia(mediaName: String) {
    val context = LocalContext.current
    val isVideo = mediaName.endsWith(".mp4")
    
    val targetAspectRatio = 9f / 21f
    
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
            .fillMaxHeight(0.95f)
            .aspectRatio(targetAspectRatio),
        color = Color.Black
    ) {
        if (resId != 0) {
            if (isVideo) {
                DevVideoPlayer(resId)
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
fun DevVideoPlayer(resId: Int) {
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
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
