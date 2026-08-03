package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.widget.Toast

import androidx.compose.animation.core.Spring
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset

// Custom vector sparkle icon representing system AI sparkles
val SparkleIcon: ImageVector = ImageVector.Builder(
    name = "Sparkle",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 3f)
        quadTo(12f, 12f, 21f, 12f)
        quadTo(12f, 12f, 12f, 21f)
        quadTo(12f, 12f, 3f, 12f)
        quadTo(12f, 12f, 12f, 3f)
        close()
    }
}.build()

val GoldLightningBolt: ImageVector = ImageVector.Builder(
    name = "GoldLightningBolt",
    defaultWidth = 10.dp,
    defaultHeight = 14.dp,
    viewportWidth = 10f,
    viewportHeight = 14f
).apply {
    path(fill = SolidColor(Color(0xFFFFCC00))) {
        moveTo(6f, 0f)
        lineTo(1f, 8f)
        lineTo(5f, 8f)
        lineTo(4f, 14f)
        lineTo(9f, 6f)
        lineTo(5f, 6f)
        close()
    }
}.build()

// Custom ethernet indicator icon <->
val EthernetIcon: ImageVector = ImageVector.Builder(
    name = "Ethernet",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(8f, 7f)
        lineTo(4f, 12f)
        lineTo(8f, 17f)
    }
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round
    ) {
        moveTo(10f, 12f)
        lineTo(14f, 12f)
    }
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16f, 7f)
        lineTo(20f, 12f)
        lineTo(16f, 17f)
    }
}.build()

data class QSAvailableTile(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val customIcon: ImageBitmap? = null
)

data class ActiveAppInfo(
    val packageName: String,
    val label: String,
    val runningDuration: String
)

data class BluetoothDeviceModel(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val device: android.bluetooth.BluetoothDevice
)

enum class WifiConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    FAILED
}

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val flags: String,
    val netId: Int? = null,
    val isSaved: Boolean = false,
    val connectionState: WifiConnectionState = WifiConnectionState.IDLE,
    val securityType: String = "OPEN",
    val frequency: Int = 0,
    val bandLabel: String = "",
    val generationLabel: String = "",
    val ipAddress: String = ""
)

const val MAX_QS_SUBPANEL_HEIGHT_DP = 520

val LocalTileSize = staticCompositionLocalOf { Pair(2, 1) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickSettingTile(
    icon: ImageVector,
    label: String,
    statusText: String,
    isActive: Boolean,
    isDark: Boolean,
    w: Int = 2,
    h: Int = 1,
    customIcon: ImageBitmap? = null,
    onLongClick: (() -> Unit)? = null,
    onIconClick: (() -> Unit)? = null,
    isEditMode: Boolean = false,
    onClick: () -> Unit
) {
    val localSize = LocalTileSize.current
    val finalW = if (w == 2 && localSize.first != 2) localSize.first else w
    val finalH = if (h == 1 && localSize.second != 1) localSize.second else h

    val containerBg = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val statusColor = contentColor.copy(alpha = 0.7f)
    val iconBg = if (isActive) contentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    
    val is1x1 = finalW == 1 && finalH == 1
    val targetCornerRadius = if (isActive) 16.dp else 30.dp
    val animatedCornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "TileCornerAnimation"
    )
    val tileShape = RoundedCornerShape(animatedCornerRadius)
    
    val clickModifier = if (isEditMode) {
        Modifier
    } else {
        Modifier.combinedClickable(
            onClick = { onClick() },
            onLongClick = onLongClick
        )
    }

    val innerCardModifier = Modifier.fillMaxSize()
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = innerCardModifier
                .clip(tileShape)
                .then(clickModifier),
            colors = CardDefaults.cardColors(containerColor = containerBg),
            shape = tileShape
        ) {
            if (is1x1) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.65f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (isActive) iconBg else Color.Transparent)
                            .then(if (onIconClick != null && !isEditMode) Modifier.clickable { onIconClick() } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customIcon != null) {
                            Image(
                                bitmap = customIcon,
                                contentDescription = label,
                                modifier = Modifier.fillMaxSize(0.5f)
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.fillMaxSize(0.5f)
                            )
                        }
                    }
                }
            } else if (finalW == 2 && finalH == 2) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(iconBg)
                            .then(if (onIconClick != null && !isEditMode) Modifier.clickable { onIconClick() } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customIcon != null) {
                            Image(
                                bitmap = customIcon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val config = androidx.compose.ui.platform.LocalConfiguration.current
                val isPhoneScreen = config.screenWidthDp < 480
                
                val rowPadding = if (isPhoneScreen) 6.dp else 8.dp
                val iconBoxSize = if (isPhoneScreen) 30.dp else 36.dp
                val iconInnerSize = if (isPhoneScreen) 15.dp else 18.dp
                val textSpacerWidth = if (isPhoneScreen) 6.dp else 10.dp
                val labelSize = if (isPhoneScreen) 12.sp else 13.sp
                val statusSize = if (isPhoneScreen) 10.sp else 11.sp

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = rowPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconClickModifier = if (onIconClick != null && !isEditMode) {
                        Modifier.clickable { onIconClick() }
                    } else {
                        Modifier
                    }
                    Box(
                        modifier = Modifier
                            .size(iconBoxSize)
                            .clip(CircleShape)
                            .background(iconBg)
                            .then(iconClickModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customIcon != null) {
                            Image(
                                bitmap = customIcon,
                                contentDescription = label,
                                modifier = Modifier.size(iconInnerSize)
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(iconInnerSize)
                            )
                        }
                    }
                    Spacer(Modifier.width(textSpacerWidth))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = labelSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = statusSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryPill(
    batteryPercent: Int,
    isCharging: Boolean,
    isPowerSaveMode: Boolean,
    contentColor: Color,
    showPercent: Boolean = true,
    scale: Float = 1f
) {
    val fillFraction = (batteryPercent / 100f).coerceIn(0f, 1f)
    
    val isLightMode = contentColor != Color.White && contentColor.red < 0.5f
    val unfilledBgColor = if (isLightMode) contentColor.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.24f)
    val normalFillColor = if (isLightMode) contentColor else Color.White

    val fillColor = when {
        isCharging -> Color(0xFF34C759) // Green
        batteryPercent <= 20 -> Color(0xFFFF3B30) // Red
        batteryPercent <= 40 || isPowerSaveMode -> Color(0xFFFFCC00) // Yellow
        else -> normalFillColor
    }
    
    val tipColor = if (batteryPercent >= 100) fillColor else unfilledBgColor
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)
    ) {
        Box(
            modifier = Modifier
                .width((30 * scale).dp)
                .height((14 * scale).dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val tipWidth = (1.5 * scale).dp.toPx()
                val tipHeight = (5 * scale).dp.toPx()
                val bodyWidth = width - tipWidth - (1 * scale).dp.toPx()
                
                val bodyPath = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = androidx.compose.ui.geometry.Rect(0f, 0f, bodyWidth, height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius((3 * scale).dp.toPx())
                        )
                    )
                }
                
                drawPath(
                    path = bodyPath,
                    color = unfilledBgColor
                )
                
                if (fillFraction > 0f) {
                    val fillWidth = bodyWidth * fillFraction
                    clipPath(bodyPath) {
                        drawRect(
                            color = fillColor,
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(fillWidth, height)
                        )
                    }
                }
                
                val tipTop = (height - tipHeight) / 2f
                val tipLeft = bodyWidth + (1 * scale).dp.toPx()
                val tipPath = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = androidx.compose.ui.geometry.Rect(
                                left = tipLeft,
                                top = tipTop,
                                right = tipLeft + tipWidth,
                                bottom = tipTop + tipHeight
                            ),
                            topRight = androidx.compose.ui.geometry.CornerRadius((1.2 * scale).dp.toPx()),
                            bottomRight = androidx.compose.ui.geometry.CornerRadius((1.2 * scale).dp.toPx())
                        )
                    )
                }
                drawPath(
                    path = tipPath,
                    color = tipColor
                )
            }
            
            if (showPercent) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val strokeWidthPx = with(density) { (1.5 * scale).dp.toPx() }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-0.5 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$batteryPercent",
                        color = Color.Black,
                        fontSize = (9 * scale).sp,
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            ),
                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidthPx,
                                join = StrokeJoin.Round
                            )
                        ),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$batteryPercent",
                        color = Color.White,
                        fontSize = (9 * scale).sp,
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        if (isCharging) {
            Icon(
                imageVector = GoldLightningBolt,
                contentDescription = "Charging",
                tint = contentColor,
                modifier = Modifier.size(11.dp)
            )
        } else if (isPowerSaveMode) {
            Text(
                text = "+",
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AluminumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    iconStart: ImageVector,
    iconEnd: ImageVector? = null,
    activeContentColor: Color,
    inactiveContentColor: Color
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        val rangeMin = valueRange.start
        val rangeMax = valueRange.endInclusive
        val fraction = if (rangeMax > rangeMin) {
            ((value - rangeMin) / (rangeMax - rangeMin)).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        val onDrag: (Offset) -> Unit = { offset ->
            val newFraction = (offset.x / widthPx).coerceIn(0f, 1f)
            val newValue = rangeMin + newFraction * (rangeMax - rangeMin)
            onValueChange(newValue)
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            onDrag(offset)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(change.position)
                    }
                }
        ) {
            val thumbWidth = 4.dp
            val gap = 4.dp
            
            val thumbWidthPx = with(density) { thumbWidth.toPx() }
            val gapPx = with(density) { gap.toPx() }
            
            val thumbCenterX = fraction * widthPx
            
            val activeWidthPx = (thumbCenterX - thumbWidthPx / 2f - gapPx).coerceAtLeast(0f)
            val activeWidth = with(density) { activeWidthPx.toDp() }
            
            val inactiveStartPx = (thumbCenterX + thumbWidthPx / 2f + gapPx).coerceAtMost(widthPx)
            val inactiveWidthPx = (widthPx - inactiveStartPx).coerceAtLeast(0f)
            val inactiveWidth = with(density) { inactiveWidthPx.toDp() }
            
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeWidthPx > 0f) {
                    Box(
                        modifier = Modifier
                            .width(activeWidth)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 22.dp,
                                    bottomStart = 22.dp,
                                    topEnd = 0.dp,
                                    bottomEnd = 0.dp
                                )
                            )
                            .background(activeColor)
                    )
                } else {
                    Spacer(modifier = Modifier.width(activeWidth))
                }
                
                if (activeWidthPx > 0f && thumbCenterX - thumbWidthPx / 2f > 0f) {
                    Spacer(modifier = Modifier.width(gap))
                }
                
                Box(
                    modifier = Modifier
                        .width(thumbWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(thumbColor)
                )
                
                if (inactiveWidthPx > 0f && thumbCenterX + thumbWidthPx / 2f < widthPx) {
                    Spacer(modifier = Modifier.width(gap))
                }
                
                if (inactiveWidthPx > 0f) {
                    Box(
                        modifier = Modifier
                            .width(inactiveWidth)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 0.dp,
                                    bottomStart = 0.dp,
                                    topEnd = 22.dp,
                                    bottomEnd = 22.dp
                                )
                            )
                            .background(inactiveColor)
                    )
                } else {
                    Spacer(modifier = Modifier.width(inactiveWidth))
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = iconStart,
                    contentDescription = null,
                    tint = if (fraction > 0.12f) activeContentColor else inactiveContentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (iconEnd != null) {
                    Icon(
                        imageVector = iconEnd,
                        contentDescription = null,
                        tint = if (fraction > 0.88f) activeContentColor else inactiveContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

fun getBuiltInTiles(): List<QSAvailableTile> {
    return listOf(
        QSAvailableTile("wifi", "Wi-Fi", Icons.Default.Wifi),
        QSAvailableTile("bluetooth", "Bluetooth", Icons.Default.Bluetooth),
        QSAvailableTile("modes", "Modes", Icons.Default.VolumeUp),
        QSAvailableTile("flashlight", "Flashlight", Icons.Default.LightMode),
        QSAvailableTile("autorotate", "Auto-rotate", Icons.Default.ScreenRotation),
        QSAvailableTile("screenrecord", "Screen record", Icons.Default.FiberManualRecord),
        QSAvailableTile("screenshot", "Screenshot", Icons.Default.CameraAlt),
        QSAvailableTile("dnd", "Do Not Disturb", Icons.Default.NotificationsOff),
        QSAvailableTile("hotspot", "Hotspot", Icons.Default.WifiTethering),
        QSAvailableTile("cast", "Cast", Icons.Default.Cast),
        QSAvailableTile("airplane", "Airplane mode", Icons.Default.Flight),
        QSAvailableTile("location", "Location", Icons.Default.Place),
        QSAvailableTile("darktheme", "Dark theme", Icons.Default.DarkMode),
        QSAvailableTile("extradim", "Extra dim", Icons.Default.BrightnessMedium),
        QSAvailableTile("nfc", "NFC", Icons.Default.CreditCard),
        QSAvailableTile("stamina", "Stamina mode", Icons.Default.BatterySaver)
    )
}

fun formatElapsedTime(postTime: Long): String {
    val diff = System.currentTimeMillis() - postTime
    if (diff < 60000) return "now"
    val mins = diff / 60000
    if (mins < 60) return "${mins}m"
    val hours = mins / 60
    if (hours < 24) return "${hours}h"
    val days = hours / 24
    return "${days}d"
}

fun formatActiveAppDuration(durationMs: Long): String {
    val durationSec = durationMs / 1000
    if (durationSec < 60) return "now"
    val mins = durationSec / 60
    if (mins < 60) return "${mins} min"
    val hours = mins / 60
    if (hours < 24) return "${hours} hr"
    val days = hours / 24
    return "${days} d"
}

fun isConversationNotification(sbn: android.service.notification.StatusBarNotification): Boolean {
    val notification = sbn.notification
    val category = notification.category
    val isMessageCategory = category == android.app.Notification.CATEGORY_MESSAGE ||
                           category == android.app.Notification.CATEGORY_SOCIAL ||
                           category == android.app.Notification.CATEGORY_CALL
                           
    val template = notification.extras.getString(android.app.Notification.EXTRA_TEMPLATE) ?: ""
    val isMessagingStyle = template.contains("MessagingStyle") || template.contains("ConversationStyle")
    
    val hasReply = notification.actions?.any { action ->
        action.remoteInputs?.any { it.allowFreeFormInput } == true
    } == true
    
    return isMessageCategory || isMessagingStyle || hasReply
}

fun isSilentOrOngoingNotification(sbn: android.service.notification.StatusBarNotification): Boolean {
    val flags = sbn.notification.flags
    val isOngoing = (flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0 ||
                    (flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0
    return isOngoing
}

@Composable
fun RenderQSGridTile(
    tileKey: String,
    isDark: Boolean,
    isWifiOn: Boolean,
    wifiSsid: String,
    isBtOn: Boolean,
    btDeviceName: String,
    currentRingerMode: Int,
    audioManager: AudioManager,
    isFlashlightOn: Boolean,
    toggleFlashlight: () -> Unit,
    isAutoRotateOn: Boolean,
    toggleAutoRotate: () -> Unit,
    isRecording: Boolean,
    recordingDuration: Int,
    toggleScreenRecord: () -> Unit,
    isDndOn: Boolean,
    toggleDnd: () -> Unit,
    isHotspotOn: Boolean,
    toggleHotspot: () -> Unit,
    isAirplaneModeOn: Boolean,
    toggleAirplaneMode: () -> Unit,
    isLocationOn: Boolean,
    toggleLocation: () -> Unit,
    isExtraDimOn: Boolean,
    toggleExtraDim: () -> Unit,
    isWirelessDebugOn: Boolean,
    toggleWirelessDebug: () -> Unit,
    isNfcOn: Boolean,
    toggleNfc: () -> Unit,
    thirdPartyTiles: List<QSAvailableTile>,
    onDismiss: () -> Unit,
    launchOptions: android.os.Bundle,
    onShowWifiListDetail: () -> Unit,
    onShowBtListDetail: () -> Unit,
    displayId: Int,
    isEditMode: Boolean = false
) {
    val context = LocalContext.current
    val isSplitClickBentoEnabled = remember { ThemeManager.isSplitClickBentoEnabled(context) }
    
    when (tileKey) {
        "wifi" -> {
            QuickSettingTile(
                icon = Icons.Default.Wifi,
                label = "Wi-Fi",
                statusText = if (isWifiOn) (if (wifiSsid.isNotEmpty()) wifiSsid else "Connected") else "Off",
                isActive = isWifiOn,
                isDark = isDark,
                isEditMode = isEditMode,
                onLongClick = {
                    onDismiss()
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                    } catch (e: Exception) {}
                },
                onIconClick = if (isSplitClickBentoEnabled) {
                    {
                        val newState = !isWifiOn
                        Thread {
                            val cmd = if (newState) "svc wifi enable" else "svc wifi disable"
                            ShellExecutor.executeCommand(cmd)
                        }.start()
                    }
                } else {
                    null
                }
            ) {
                onShowWifiListDetail()
            }
        }
        "bluetooth" -> {
            QuickSettingTile(
                icon = Icons.Default.Bluetooth,
                label = "Bluetooth",
                statusText = if (isBtOn) (if (btDeviceName.isNotEmpty()) btDeviceName else "On") else "Off",
                isActive = isBtOn,
                isDark = isDark,
                isEditMode = isEditMode,
                onLongClick = {
                    onDismiss()
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                    } catch (e: Exception) {}
                },
                onIconClick = if (isSplitClickBentoEnabled) {
                    {
                        val newState = !isBtOn
                        Thread {
                            val cmd = if (newState) "cmd bluetooth_manager enable" else "cmd bluetooth_manager disable"
                            ShellExecutor.executeCommand(cmd)
                        }.start()
                    }
                } else {
                    null
                }
            ) {
                onShowBtListDetail()
            }
        }
        "modes" -> {
            val isModesActive = currentRingerMode == AudioManager.RINGER_MODE_SILENT || currentRingerMode == AudioManager.RINGER_MODE_VIBRATE
            val modesStatusText = when (currentRingerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Silent"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                else -> "Sound"
            }
            val modesIcon = when (currentRingerMode) {
                AudioManager.RINGER_MODE_SILENT -> Icons.Default.VolumeOff
                AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
                else -> Icons.Default.VolumeUp
            }
            QuickSettingTile(
                icon = modesIcon,
                label = "Modes",
                statusText = modesStatusText,
                isActive = isModesActive,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                val nextMode = when (currentRingerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                try {
                    audioManager.ringerMode = nextMode
                } catch (e: Exception) {
                    Log.e("AluminumQS", "Failed to set ringerMode directly, falling back to shell", e)
                    Thread {
                        try {
                            val ringerVal = when (nextMode) {
                                AudioManager.RINGER_MODE_SILENT -> 0
                                AudioManager.RINGER_MODE_VIBRATE -> 1
                                else -> 2
                            }
                            ShellExecutor.executeCommand("settings put global mode_ringer $ringerVal")
                            val zenVal = if (nextMode == AudioManager.RINGER_MODE_SILENT) 1 else 0
                            ShellExecutor.executeCommand("settings put global zen_mode $zenVal")
                        } catch (ex: Exception) {
                            Log.e("AluminumQS", "Shell ringerMode fallback failed", ex)
                        }
                    }.start()
                }
            }
        }
        "flashlight" -> {
            QuickSettingTile(
                icon = Icons.Default.LightMode,
                label = "Flashlight",
                statusText = if (isFlashlightOn) "On" else "Off",
                isActive = isFlashlightOn,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                toggleFlashlight()
            }
        }
        "autorotate" -> {
            QuickSettingTile(
                icon = Icons.Default.ScreenRotation,
                label = "Auto-rotate",
                statusText = if (isAutoRotateOn) "On" else "Off",
                isActive = isAutoRotateOn,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                toggleAutoRotate()
            }
        }
        "screenrecord" -> {
            QuickSettingTile(
                icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                label = "Screen record",
                statusText = if (isRecording) "${recordingDuration}s" else "Off",
                isActive = isRecording,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                toggleScreenRecord()
            }
        }
        "screenshot" -> {
            QuickSettingTile(
                icon = Icons.Default.CameraAlt,
                label = "Screenshot",
                statusText = "Capture",
                isActive = false,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                onDismiss()
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.postDelayed({
                    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val display = displayManager.getDisplay(displayId) ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    val displayContext = context.createDisplayContext(display)
                    val metrics = displayContext.resources.displayMetrics
                    val displayInfo = DisplayInfo(
                        id = displayId,
                        name = "",
                        width = metrics.widthPixels,
                        height = metrics.heightPixels
                    )
                    val snipOverlay = SnippingOverlay(context, displayId, displayInfo) { croppedFile ->
                        if (croppedFile != null) {
                            Toast.makeText(context, "Capture saved: ${croppedFile.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    snipOverlay.show()
                }, 500)
            }
        }
        "dnd" -> {
            QuickSettingTile(
                icon = Icons.Default.NotificationsOff,
                label = "Do Not Disturb",
                statusText = if (isDndOn) "On" else "Off",
                isActive = isDndOn,
                isDark = isDark,
                isEditMode = isEditMode,
                onLongClick = {
                    onDismiss()
                    try {
                        val intent = Intent("android.settings.ZEN_MODE_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                    } catch (e: Exception) {}
                }
            ) {
                toggleDnd()
            }
        }
        "hotspot" -> {
            QuickSettingTile(
                icon = Icons.Default.WifiTethering,
                label = "Hotspot",
                statusText = if (isHotspotOn) "On" else "Off",
                isActive = isHotspotOn,
                isDark = isDark,
                isEditMode = isEditMode,
                onLongClick = {
                    onDismiss()
                    val intent = Intent().apply {
                        setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent, launchOptions)
                    } catch (e: Exception) {
                        val fallback = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallback, launchOptions)
                    }
                }
            ) {
                toggleHotspot()
            }
        }
        "cast" -> {
            QuickSettingTile(
                icon = Icons.Default.Cast,
                label = "Cast",
                statusText = "Off",
                isActive = false,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                onDismiss()
                try {
                    val intent = Intent("android.settings.CAST_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent, launchOptions)
                } catch (e: Exception) {}
            }
        }
        "quickshare" -> {
            QuickSettingTile(
                icon = Icons.Default.Share,
                label = "Quick Share",
                statusText = "Tap to open",
                isActive = false,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                onDismiss()
                val intentActions = listOf(
                    {
                        val intent = Intent("android.settings.NEARBY_SHARING_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    },
                    {
                        val intent = Intent("com.google.android.gms.settings.NEARBY_SHARING_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    },
                    {
                        val intent = Intent().apply {
                            setClassName("com.google.android.gms", "com.google.android.gms.nearby.sharing.settings.NearbySharingSettingsActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    },
                    {
                        val intent = Intent().apply {
                            setClassName("com.samsung.android.app.sharelive", "com.samsung.android.app.sharelive.presentation.setting.QuickShareSettingActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    },
                    {
                        val intent = Intent("com.samsung.android.app.sharelive.action.SHARE_SETTING").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    },
                    {
                        val intent = Intent("com.google.android.gms.SHARE_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    },
                    {
                        val intent = Intent("android.settings.CONNECTED_DEVICE_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    },
                    {
                        val intent = Intent("com.google.android.gms.settings.GOOGLE_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                        true
                    }
                )
                var success = false
                for (action in intentActions) {
                    try {
                        if (action()) {
                            success = true
                            break
                        }
                    } catch (e: Exception) {}
                }
                if (!success) {
                    Toast.makeText(context, "Quick Share settings not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
        "airplane" -> {
            QuickSettingTile(
                icon = Icons.Default.Flight,
                label = "Airplane mode",
                statusText = if (isAirplaneModeOn) "On" else "Off",
                isActive = isAirplaneModeOn,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                toggleAirplaneMode()
            }
        }
        "location" -> {
            QuickSettingTile(
                icon = Icons.Default.Place,
                label = "Location",
                statusText = if (isLocationOn) "On" else "Off",
                isActive = isLocationOn,
                isDark = isDark,
                isEditMode = isEditMode,
                onLongClick = {
                    onDismiss()
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                    } catch (e: Exception) {}
                }
            ) {
                toggleLocation()
            }
        }
        "darktheme" -> {
            QuickSettingTile(
                icon = Icons.Default.DarkMode,
                label = "Dark theme",
                statusText = if (isDark) "On" else "Off",
                isActive = isDark,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                val newMode = if (isDark) 1 else 2
                Thread {
                    val cmd = if (isDark) "cmd uimode night no" else "cmd uimode night yes"
                    ShellExecutor.executeCommand(cmd)
                }.start()
                ThemeManager.setThemeMode(context, newMode)
                try {
                    FreeformOverlayService.requestRefresh()
                } catch (e: Exception) {}
            }
        }
        "extradim" -> {
            QuickSettingTile(
                icon = Icons.Default.BrightnessMedium,
                label = "Extra dim",
                statusText = if (isExtraDimOn) "On" else "Off",
                isActive = isExtraDimOn,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                toggleExtraDim()
            }
        }
        "wirelessdebugging" -> {
            QuickSettingTile(
                icon = Icons.Default.Build,
                label = "Wireless debugging",
                statusText = if (isWirelessDebugOn) "On" else "Off",
                isActive = isWirelessDebugOn,
                isDark = isDark,
                isEditMode = isEditMode,
                onLongClick = {
                    onDismiss()
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                    } catch (e: Exception) {}
                }
            ) {
                toggleWirelessDebug()
            }
        }
        "nfc" -> {
            QuickSettingTile(
                icon = Icons.Default.CreditCard,
                label = "NFC",
                statusText = if (isNfcOn) "On" else "Off",
                isActive = isNfcOn,
                isDark = isDark,
                isEditMode = isEditMode,
                onLongClick = {
                    onDismiss()
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_NFC_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent, launchOptions)
                    } catch (e: Exception) {}
                }
            ) {
                toggleNfc()
            }
        }
        "stamina" -> {
            var isSaverOn by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val out = ShellExecutor.executeCommand("settings get global low_power").trim()
                            isSaverOn = out == "1"
                        } catch (e: Exception) {}
                    }
                    kotlinx.coroutines.delay(3000)
                }
            }
            QuickSettingTile(
                icon = Icons.Default.BatterySaver,
                label = "Stamina mode",
                statusText = if (isSaverOn) "On" else "Off",
                isActive = isSaverOn,
                isDark = isDark,
                isEditMode = isEditMode
            ) {
                val nextState = !isSaverOn
                isSaverOn = nextState
                Thread {
                    val valStr = if (nextState) "1" else "0"
                    ShellExecutor.executeCommand("settings put global low_power $valStr")
                }.start()
            }
        }
        else -> {
            val found = thirdPartyTiles.firstOrNull { it.key == tileKey }
            if (found != null) {
                QuickSettingTile(
                    icon = Icons.Default.Settings,
                    customIcon = found.customIcon,
                    label = found.label,
                    statusText = "Tap to trigger",
                    isActive = false,
                    isDark = isDark,
                    isEditMode = isEditMode
                ) {
                    Thread {
                        ShellExecutor.executeCommand("cmd statusbar click-tile $tileKey")
                    }.start()
                }
            } else {
                QuickSettingTile(
                    icon = Icons.Default.Settings,
                    label = tileKey.replaceFirstChar { it.uppercase() },
                    statusText = "Not available",
                    isActive = false,
                    isDark = isDark,
                    isEditMode = isEditMode
                ) {}
            }
        }
    }
}

@Composable
fun MusicTileItem(
    cardBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    inactiveTileBg: Color,
    isDark: Boolean,
    w: Int = 4,
    h: Int = 1
) {
    // Legacy compatibility stub
}
