package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.launch

@Composable
fun SingleMediaPlayerPage(
    session: MediaSessionData,
    displayId: Int,
    contentColor: Color,
    activeTileBg: Color,
    inactiveTileBg: Color
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    val appInfo = remember(session.packageName) {
        try { pm.getApplicationInfo(session.packageName, 0) } catch (e: Exception) { null }
    }
    val appLabel = remember(appInfo) {
        if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else session.packageName
    }
    
    val appIconBitmap = remember(session.packageName) {
        try {
            val drawable = pm.getApplicationIcon(session.packageName)
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    
    val dominantColor = remember(session.albumArt) {
        val art = session.albumArt
        if (art != null) {
            try {
                val onePixel = android.graphics.Bitmap.createScaledBitmap(art, 1, 1, true)
                val color = onePixel.getPixel(0, 0)
                onePixel.recycle()
                Color(color)
            } catch (e: Exception) {
                Color(0xFF2C2C2C)
            }
        } else {
            Color(0xFF2C2C2C)
        }
    }

    val isLightColor = remember(dominantColor) {
        val red = dominantColor.red
        val green = dominantColor.green
        val blue = dominantColor.blue
        val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
        luminance > 0.5f
    }

    val textColor = if (isLightColor) Color.Black else Color.White
    val secondaryTextColor = if (isLightColor) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.7f)
    val appBadgeBgColor = if (isLightColor) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.15f)
    val appBadgeTextColor = if (isLightColor) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.9f)
    val iconButtonBgColor = if (isLightColor) Color.Black else Color.White
    val iconButtonIconColor = if (isLightColor) Color.White else Color.Black
    val progressBarBgColor = if (isLightColor) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.25f)
    val progressBarActiveColor = if (isLightColor) Color.Black else Color.White

    val launchOptions = remember(displayId) {
        android.app.ActivityOptions.makeBasic().apply {
            launchDisplayId = displayId
            try {
                val method = this.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(this, 5) // 5 = WINDOWING_MODE_FREEFORM
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayer", "Failed to set launch windowing mode to freeform", e)
            }
        }.toBundle()
    }
    
    fun launchMusicApp() {
        try {
            val intent = pm.getLaunchIntentForPackage(session.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent, launchOptions)
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaPlayer", "Failed to launch music app", e)
        }
    }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isDraggingTimeline by remember { mutableStateOf(false) }
    var draggedPositionMs by remember { mutableStateOf(0L) }
    val controller = session.controller
    val isPlaying = session.isPlaying
    
    val scope = rememberCoroutineScope()
    
    val albumArtScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.04f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "AlbumArtScale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "PlayPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )
    
    LaunchedEffect(isPlaying, session.title) {
        while (true) {
            val state = controller.playbackState
            val metadata = controller.metadata
            if (state != null) {
                positionMs = state.position
            }
            if (metadata != null) {
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            }
            if (isPlaying) {
                delay(1000)
            } else {
                delay(2000)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(dominantColor)
    ) {
        if (session.albumArt != null) {
            Image(
                bitmap = session.albumArt.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = albumArtScale
                        scaleY = albumArtScale
                    }
                    .alpha(0.85f)
            )
        }
        
        // Edge/Vignette shading overlay using dark/colored radial shading
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isLightColor) Color.Black.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.45f)
                        ),
                        center = androidx.compose.ui.geometry.Offset(x = 350f, y = 150f),
                        radius = 500f
                    )
                )
        )
        
        // Shorter horizontal gradient text shield (transitions from solid dominantColor to transparent at 0.6f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to dominantColor,
                            0.2f to dominantColor.copy(alpha = 0.85f),
                            0.6f to dominantColor.copy(alpha = 0.0f)
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { launchMusicApp() }
                        .padding(4.dp)
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = appLabel,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = appLabel,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(appBadgeBgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (isPlaying) {
                        PlayingEqualizerAnimation(
                            color = appBadgeTextColor,
                            isPlaying = true,
                            modifier = Modifier.size(width = 10.dp, height = 12.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = appBadgeTextColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "This phone",
                        color = appBadgeTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = session.artist,
                        color = secondaryTextColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(Modifier.width(12.dp))
                
                Box(contentAlignment = Alignment.Center) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha
                                }
                                .background(iconButtonBgColor, CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(iconButtonBgColor)
                            .clickable {
                                if (isPlaying) controller.transportControls?.pause() else controller.transportControls?.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = iconButtonIconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            
            if (durationMs > 0) {
                val progressFraction = if (isDraggingTimeline) {
                    (draggedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(durationMs) {
                            detectTapGestures(
                                onPress = { offset ->
                                    isDraggingTimeline = true
                                    val width = size.width
                                    val fraction = (offset.x / width).coerceIn(0f, 1f)
                                    draggedPositionMs = (fraction * durationMs).toLong()
                                    controller.transportControls?.seekTo(draggedPositionMs)
                                    tryAwaitRelease()
                                    isDraggingTimeline = false
                                    positionMs = draggedPositionMs
                                }
                            )
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDraggingTimeline = true
                                    val width = size.width
                                    val fraction = (offset.x / width).coerceIn(0f, 1f)
                                    draggedPositionMs = (fraction * durationMs).toLong()
                                },
                                onDragEnd = {
                                    controller.transportControls?.seekTo(draggedPositionMs)
                                    isDraggingTimeline = false
                                    positionMs = draggedPositionMs
                                },
                                onDragCancel = {
                                    isDraggingTimeline = false
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val width = size.width
                                val deltaFraction = dragAmount / width
                                val deltaMs = (deltaFraction * durationMs).toLong()
                                draggedPositionMs = (draggedPositionMs + deltaMs).coerceIn(0L, durationMs)
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val widthDp = maxWidth
                    
                    // Background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(progressBarBgColor, RoundedCornerShape(2.dp))
                    )
                    
                    // Active track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(4.dp)
                            .background(progressBarActiveColor, RoundedCornerShape(2.dp))
                    )
                    
                    // Thumb: A vertical line/pill (like 4.dp wide, 12.dp high) as in Image 3
                    val thumbWidth = 4.dp
                    val maxThumbOffset = widthDp - thumbWidth
                    val thumbOffset = maxThumbOffset * progressFraction
                    
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset)
                            .size(width = thumbWidth, height = 12.dp)
                            .background(progressBarActiveColor, RoundedCornerShape(2.dp))
                    )
                }
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}

@Composable
fun PlayingEqualizerAnimation(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "EqAnim")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 15f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) height1 else 4f
        val h2 = if (isPlaying) height2 else 8f
        val h3 = if (isPlaying) height3 else 5f
        
        Box(Modifier.size(width = 2.dp, height = h1.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.size(width = 2.dp, height = h2.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.size(width = 2.dp, height = h3.dp).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartMediaPlayerCarousel(
    displayId: Int,
    contentColor: Color,
    activeTileBg: Color,
    inactiveTileBg: Color
) {
    val sessions = ActiveMediaState.activeSessions
    if (sessions.isEmpty()) return
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { sessions.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isScrollEnabled = remember(context) { ThemeManager.isScrollPageSwitchingEnabled(context) }
    
    val scrollModifier = if (isScrollEnabled && sessions.size > 1) {
        Modifier.pointerInput(sessions.size) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (deltaY != 0f) {
                            val targetPage = if (deltaY > 0f) {
                                (pagerState.currentPage + 1).coerceAtMost(sessions.size - 1)
                            } else {
                                (pagerState.currentPage - 1).coerceAtLeast(0)
                            }
                            if (targetPage != pagerState.currentPage) {
                                scope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(scrollModifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            if (page < sessions.size) {
                SingleMediaPlayerPage(
                    session = sessions[page],
                    displayId = displayId,
                    contentColor = contentColor,
                    activeTileBg = activeTileBg,
                    inactiveTileBg = inactiveTileBg
                )
            }
        }
        
        if (sessions.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(sessions.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / 60000) % 60
    return String.format("%02d:%02d", min, sec)
}
