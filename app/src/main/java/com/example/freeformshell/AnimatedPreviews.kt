package com.example.freeformshell

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoopingAutoHideTitleBarPreviewCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "titleBarLoop")

    // Loop cycle: 0f -> 1f (3.5 seconds loop)
    // 0f..0.25f: Idle collapsed handle
    // 0.25f..0.75f: Mouse moves in & title bar expands
    // 0.75f..1.0f: Mouse exits & title bar collapses back up
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "titleProgress"
    )

    val isExpanded = progress in 0.25f..0.75f
    
    val titleOffsetY by animateDpAsState(
        targetValue = if (!isExpanded) (-28).dp else 0.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "titleOffsetY"
    )

    val mouseOffsetY by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 70.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "mouseOffsetY"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (!isExpanded) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "contentAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Animation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Auto-Hide Title Bar Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Live looping preview showing hover expansion & collapse",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Interactive Looping Mockup Window Container - Squarish Desktop Shape!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp, 155.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                ) {
                    // Title Bar Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .offset(y = titleOffsetY)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Mockup Application",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha * 0.4f))
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha * 0.8f))
                                )
                            }
                        }

                        // Thin handle indicator when collapsed
                        if (!isExpanded) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp)
                                    .width(36.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                            )
                        }
                    }

                    // Animated Mouse Cursor Graphic
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = mouseOffsetY)
                            .size(20.dp)
                            .rotate(-45f)
                    )
                }
            }
        }
    }
}

@Composable
fun AppUiStylePreview(selectedStyle: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            if (selectedStyle == 0) {
                // CLASSIC UI PREVIEW: Traditional Sidebar Navigation Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(12.dp))
                ) {
                    // Left Navigation Rail / Sidebar
                    Column(
                        modifier = Modifier
                            .width(70.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.height(8.dp))
                        repeat(3) {
                            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(0.3f), CircleShape))
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Main Content Canvas
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.width(40.dp).height(3.dp).background(Color.Gray.copy(0.4f)))
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), RoundedCornerShape(6.dp)))
                    }
                }
                
                Text(
                    "CLASSIC: Navigation Rail Layout", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    fontWeight = FontWeight.Bold
                )
            } else {
                // EXPRESSIVE UI PREVIEW: Modern Floating Pill Dock + Glassmorphic Floating Containers
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Floating Glass Container Mockup
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(90.dp)
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.6f), RoundedCornerShape(14.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(Modifier.width(36.dp).height(3.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(0.5f)))
                        }
                    }

                    // Floating Bottom Pill Dock
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) {
                                Box(Modifier.size(8.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                }
                
                Text(
                    "EXPRESSIVE: Floating Pill Dock & Glass Containers", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
