package com.example.freeformshell

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object NotificationIconCache {
    private val cache = LruCache<String, ImageBitmap>(150)
    private var cachedIconPack: String? = null

    fun getIcon(context: Context, key: String): ImageBitmap? {
        checkIconPack(context)
        return cache.get(key)
    }

    fun putIcon(context: Context, key: String, bitmap: ImageBitmap) {
        checkIconPack(context)
        cache.put(key, bitmap)
    }

    private fun checkIconPack(context: Context) {
        val currentPack = IconPackManager.getSelectedIconPack(context)
        if (cachedIconPack != currentPack) {
            cache.evictAll()
            cachedIconPack = currentPack
        }
    }

    fun clear() {
        cache.evictAll()
    }
}

object SystemAppIconCache {
    private val cache = LruCache<String, ImageBitmap>(50)
    fun get(packageName: String): ImageBitmap? = cache.get(packageName)
    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }
}

// --- HELPER FUNCTIONS ---

fun isSystemPackage(packageName: String): Boolean {
    return (packageName == "android" ||
            packageName == "com.android.systemui" ||
            packageName == "com.android.settings" ||
            packageName.startsWith("com.android."))
}

fun isConversationNotificationPanel(sbn: StatusBarNotification): Boolean {
    return sbn.notification.shortcutId != null || sbn.notification.extras.getString(Notification.EXTRA_CONVERSATION_TITLE) != null
}

fun isSilentOrOngoingNotificationPanel(sbn: StatusBarNotification): Boolean {
    return (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) ||
            (sbn.notification.priority <= Notification.PRIORITY_LOW)
}

fun formatElapsedTimeNotif(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60000 -> "now"
        diff < 3600000 -> "${diff / 60000}m"
        diff < 86400000 -> "${diff / 3600000}h"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

fun getAppLabel(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }
}

// --- COMPOSABLES ---

@Composable
fun NotificationSubPanel(
    onBack: () -> Unit,
    cardBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    inactiveTileBg: Color,
    onDismissDropdown: () -> Unit,
    launchOptions: Bundle,
    onInputActiveChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allNotifications = ActiveNotificationState.notifications
    val isGroupingEnabled = ThemeManager.isNotifGroupingEnabled(context)
    val panelHeight = ThemeManager.getNotifPanelHeight(context).dp

    var isConversationsCollapsed by remember { mutableStateOf(false) }
    var isAlertsCollapsed by remember { mutableStateOf(false) }
    var isSilentCollapsed by remember { mutableStateOf(false) }

    var expandedNotifications by remember { mutableStateOf(setOf<String>()) }
    var expandedBundles by remember { mutableStateOf(setOf<String>()) }

    var replyingNotificationKey by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(replyingNotificationKey) {
        onInputActiveChanged(replyingNotificationKey != null)
    }

    val conversations = remember(allNotifications) { allNotifications.filter { isConversationNotificationPanel(it) } }
    val alerts = remember(allNotifications) { allNotifications.filter { !isConversationNotificationPanel(it) && !isSilentOrOngoingNotificationPanel(it) } }
    val silent = remember(allNotifications) { allNotifications.filter { !isConversationNotificationPanel(it) && isSilentOrOngoingNotificationPanel(it) } }

    val groupedConversations = remember(conversations, isGroupingEnabled) {
        if (isGroupingEnabled) conversations.groupBy { it.packageName }
        else conversations.associateBy { it.key }.mapValues { listOf(it.value) }
    }
    val groupedAlerts = remember(alerts, isGroupingEnabled) {
        if (isGroupingEnabled) alerts.groupBy { it.packageName }
        else alerts.associateBy { it.key }.mapValues { listOf(it.value) }
    }
    val groupedSilent = remember(silent, isGroupingEnabled) {
        if (isGroupingEnabled) silent.groupBy { it.packageName }
        else silent.associateBy { it.key }.mapValues { listOf(it.value) }
    }

    var isClearing by remember { mutableStateOf(false) }
    val clearWobble by animateFloatAsState(
        targetValue = if (isClearing) 12f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "wobble"
    )

    Box(modifier = Modifier.fillMaxWidth().height(panelHeight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Quick Settings",
                        tint = contentColor,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .padding(8.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Notifications",
                        color = contentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val hasClearable = remember(allNotifications) { allNotifications.any { it.isClearable } }
                if (hasClearable) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear All",
                        tint = contentColor,
                        modifier = Modifier
                            .graphicsLayer { rotationZ = clearWobble }
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch {
                                    isClearing = true
                                    delay(400)
                                    LockScreenNotificationListener.instance?.cancelAllNotifications()
                                    isClearing = false
                                }
                            }
                            .padding(8.dp)
                    )
                }
            }

            if (allNotifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.25f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "All caught up!",
                            color = contentColor.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "No notifications",
                            color = contentColor.copy(alpha = 0.3f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    if (conversations.isNotEmpty()) {
                        item {
                            NotificationHeader("Conversations", contentColor, isConversationsCollapsed) {
                                isConversationsCollapsed = !isConversationsCollapsed
                            }
                        }
                        if (!isConversationsCollapsed) {
                            val conversationEntries = groupedConversations.entries.toList()
                            conversationEntries.forEachIndexed { index, entry ->
                                renderGroupedNotifications(
                                    group = entry,
                                    isFirstInSection = index == 0,
                                    isLastInSection = index == conversationEntries.size - 1,
                                    expandedBundles = expandedBundles,
                                    onToggleBundle = { key ->
                                        expandedBundles = if (expandedBundles.contains(key)) expandedBundles - key else expandedBundles + key
                                    },
                                    expandedNotifications = expandedNotifications,
                                    onToggleNotification = { key ->
                                        expandedNotifications = if (expandedNotifications.contains(key)) expandedNotifications - key else expandedNotifications + key
                                    },
                                    replyingNotificationKey = replyingNotificationKey,
                                    onToggleReply = { key -> replyingNotificationKey = key },
                                    replyText = replyText,
                                    onReplyTextChanged = { replyText = it },
                                    inactiveTileBg = inactiveTileBg,
                                    contentColor = contentColor,
                                    activeTileBg = activeTileBg,
                                    cardBg = cardBg,
                                    context = context,
                                    onDismissDropdown = onDismissDropdown,
                                    launchOptions = launchOptions
                                )
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    if (alerts.isNotEmpty()) {
                        item {
                            NotificationHeader("Alerts", contentColor, isAlertsCollapsed) {
                                isAlertsCollapsed = !isAlertsCollapsed
                            }
                        }
                        if (!isAlertsCollapsed) {
                            val alertEntries = groupedAlerts.entries.toList()
                            alertEntries.forEachIndexed { index, entry ->
                                renderGroupedNotifications(
                                    group = entry,
                                    isFirstInSection = index == 0,
                                    isLastInSection = index == alertEntries.size - 1,
                                    expandedBundles = expandedBundles,
                                    onToggleBundle = { key ->
                                        expandedBundles = if (expandedBundles.contains(key)) expandedBundles - key else expandedBundles + key
                                    },
                                    expandedNotifications = expandedNotifications,
                                    onToggleNotification = { key ->
                                        expandedNotifications = if (expandedNotifications.contains(key)) expandedNotifications - key else expandedNotifications + key
                                    },
                                    replyingNotificationKey = replyingNotificationKey,
                                    onToggleReply = { key -> replyingNotificationKey = key },
                                    replyText = replyText,
                                    onReplyTextChanged = { replyText = it },
                                    inactiveTileBg = inactiveTileBg,
                                    contentColor = contentColor,
                                    activeTileBg = activeTileBg,
                                    cardBg = cardBg,
                                    context = context,
                                    onDismissDropdown = onDismissDropdown,
                                    launchOptions = launchOptions
                                )
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    if (silent.isNotEmpty()) {
                        item {
                            NotificationHeader("Silent / Ongoing", contentColor, isSilentCollapsed) {
                                isSilentCollapsed = !isSilentCollapsed
                            }
                        }
                        if (!isSilentCollapsed) {
                            val silentEntries = groupedSilent.entries.toList()
                            silentEntries.forEachIndexed { index, entry ->
                                renderGroupedNotifications(
                                    group = entry,
                                    isFirstInSection = index == 0,
                                    isLastInSection = index == silentEntries.size - 1,
                                    expandedBundles = expandedBundles,
                                    onToggleBundle = { key ->
                                        expandedBundles = if (expandedBundles.contains(key)) expandedBundles - key else expandedBundles + key
                                    },
                                    expandedNotifications = expandedNotifications,
                                    onToggleNotification = { key ->
                                        expandedNotifications = if (expandedNotifications.contains(key)) expandedNotifications - key else expandedNotifications + key
                                    },
                                    replyingNotificationKey = replyingNotificationKey,
                                    onToggleReply = { key -> replyingNotificationKey = key },
                                    replyText = replyText,
                                    onReplyTextChanged = { replyText = it },
                                    inactiveTileBg = inactiveTileBg,
                                    contentColor = contentColor,
                                    activeTileBg = activeTileBg,
                                    cardBg = cardBg,
                                    context = context,
                                    onDismissDropdown = onDismissDropdown,
                                    launchOptions = launchOptions
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationAppChip(packageName: String, size: Dp, context: Context) {
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            val cached = SystemAppIconCache.get(packageName)
            if (cached != null) {
                iconBitmap = cached
                return@withContext
            }
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(pm.getApplicationInfo(packageName, 0))
                val bmp = drawable.toBitmap(48, 48).asImageBitmap()
                SystemAppIconCache.put(packageName, bmp)
                iconBitmap = bmp
            } catch (_: Exception) {}
        }
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    }
}

@Composable
fun NotificationHeader(
    title: String,
    color: Color,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = color.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Icon(
            imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = if (isCollapsed) "Expand Section" else "Collapse Section",
            tint = color.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun BundleHeaderCard(
    sbn: StatusBarNotification,
    packageName: String,
    appLabel: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    inactiveTileBg: Color,
    contentColor: Color,
    context: Context,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    var isHovered by remember { mutableStateOf(false) }
    val isLight = contentColor == Color.Black
    val hoverOverlay = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) hoverOverlay.compositeOver(inactiveTileBg) else inactiveTileBg
        ),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clip(shape)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp, bottom = 9.dp, start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.padding(top = 2.dp)) {
                Box(modifier = Modifier.size(24.dp)) {
                    NotificationIconResolver(
                        context = context,
                        sbn = sbn,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            if (isExpanded) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = appLabel,
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " • $count notifications",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appLabel,
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$count notifications",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.08f))
                    .clickable { onClick() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

 @Composable
    fun NotificationCard(
        sbn: StatusBarNotification,
        appLabel: String,
        isExpanded: Boolean,
        onToggleExpand: () -> Unit,
        isReplying: Boolean,
        onToggleReply: () -> Unit,
        replyText: String,
        onReplyTextChanged: (String) -> Unit,
        inactiveTileBg: Color,
        contentColor: Color,
        activeTileBg: Color,
        cardBg: Color,
        context: Context,
        onDismissDropdown: () -> Unit,
        launchOptions: Bundle,
        shape: Shape = RoundedCornerShape(24.dp)
    ) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = bigText?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val timeString = remember(sbn.postTime) { formatElapsedTimeNotif(sbn.postTime) }

        val largeIcon = sbn.notification.getLargeIcon()
        val displayTitle = title.ifEmpty { appLabel }

        val isMediaCard = remember(sbn) {
            sbn.notification.category == Notification.CATEGORY_TRANSPORT ||
                    sbn.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                    sbn.notification.actions?.any { it.title.isNullOrBlank() } == true
        }
val animScale = remember(context) { ThemeManager.getAnimationSpeedScale(context) }
        val animDurationMs = remember(animScale) { (280 * animScale).toInt().coerceIn(100, 2000) }
        val expandTweenSpec = remember(animDurationMs) {
            tween<androidx.compose.ui.unit.IntSize>(animDurationMs, 0, FastOutSlowInEasing)
        }

        var collapsedBitmap by remember(sbn.key) { mutableStateOf<ImageBitmap?>(null) }
        var expandedMediaBitmap by remember(sbn.key) { mutableStateOf<ImageBitmap?>(null) }
        var scaled720pBitmap by remember(sbn.key) { mutableStateOf<ImageBitmap?>(null) }
        var actionBitmaps by remember(sbn.key) { mutableStateOf<Map<Int, ImageBitmap?>>(emptyMap()) }

        LaunchedEffect(sbn.key, sbn.postTime, isExpanded) {
            withContext(Dispatchers.IO) {
                try {
                    // A. Extract fallback & low-res 128px thumbnail for collapsed view
                    val bigPic = try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
                        }
                    } catch (_: Exception) { null }

                    val rawLargeIcon = try {
                        largeIcon?.loadDrawable(context)?.toBitmap()
                    } catch (_: Exception) { null }

                    val fallbackBitmap = bigPic ?: rawLargeIcon
                    if (fallbackBitmap != null) {
                        collapsedBitmap = Bitmap.createScaledBitmap(fallbackBitmap, 128, 128, true).asImageBitmap()
                    } else {
                        collapsedBitmap = null
                    }

                    if (bigPic != null) {
                        val maxDim = 720
                        val w = bigPic.width
                        val h = bigPic.height
                        val scaled = if (w > maxDim || h > maxDim) {
                            val scale = maxDim.toFloat() / kotlin.math.max(w, h)
                            Bitmap.createScaledBitmap(bigPic, (w * scale).toInt(), (h * scale).toInt(), true)
                        } else {
                            bigPic
                        }
                        scaled720pBitmap = scaled.asImageBitmap()
                    } else {
                        scaled720pBitmap = null
                    }

                    // B. Extract high-res media artwork via MediaSession ONLY when expanded
                    if (isExpanded) {
                        var highResBitmap: Bitmap? = null

                        val sessionToken = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                        }

                        if (sessionToken != null) {
                            var controller: MediaController? = null
                            try {
                                controller = MediaController(context, sessionToken)
                                val metadata = controller.metadata
                                if (metadata != null) {
                                    highResBitmap = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                                        ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                                }
                            } catch (e: Exception) {
                                Log.e("NotificationCard", "Failed to get bitmap from MediaSession", e)
                            } finally {
                                controller = null
                            }
                        }

                        val sourceForExpanded = highResBitmap ?: fallbackBitmap

                        if (sourceForExpanded != null) {
                            val maxDim = 1080
                            val w = sourceForExpanded.width
                            val h = sourceForExpanded.height
                            val scaled = if (w > maxDim || h > maxDim) {
                                val scale = maxDim.toFloat() / kotlin.math.max(w, h)
                                Bitmap.createScaledBitmap(sourceForExpanded, (w * scale).toInt(), (h * scale).toInt(), true)
                            } else {
                                sourceForExpanded
                            }
                            expandedMediaBitmap = scaled.asImageBitmap()
                        } else {
                            expandedMediaBitmap = null
                        }

                        // C. Extract action button icons asynchronously off UI thread
                        val actions = sbn.notification.actions
                        if (!actions.isNullOrEmpty()) {
                            val loadedActionBitmaps = mutableMapOf<Int, ImageBitmap?>()
                            val isSystemNotif = isSystemPackage(sbn.packageName)
                            actions.forEachIndexed { index, action ->
                                if (!isSystemNotif) {
                                    try {
                                        val icon = action.getIcon()
                                        if (icon != null) {
                                            val pkgContext = context.createPackageContext(sbn.packageName, 0)
                                            loadedActionBitmaps[index] = icon.loadDrawable(pkgContext)?.toBitmap()?.asImageBitmap()
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            actionBitmaps = loadedActionBitmaps
                        } else {
                            actionBitmaps = emptyMap()
                        }
                    } else {
                        actionBitmaps = emptyMap()
                    }
                } catch (e: Exception) {
                    Log.e("NotificationCard", "Error extracting icons in background", e)
                }
            }
        }

        var isHovered by remember { mutableStateOf(false) }
        val isLight = contentColor == Color.Black
        val hoverOverlay = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)

        var swipeOffsetX by remember { mutableFloatStateOf(0f) }
        val swipeThreshold = 300f
        val animatedOffsetX by animateFloatAsState(
            targetValue = swipeOffsetX,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "swipe_offset"
        )

        val focusRequester = remember { FocusRequester() }
        val scope = rememberCoroutineScope()
        LaunchedEffect(isReplying) {
            if (isReplying) {
                delay(150)
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {}
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isHovered) hoverOverlay.compositeOver(inactiveTileBg) else inactiveTileBg
            ),
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffsetX
                    alpha = (1f - (kotlin.math.abs(animatedOffsetX) / swipeThreshold)).coerceIn(0f, 1f)
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Enter -> isHovered = true
                                PointerEventType.Exit -> isHovered = false
                            }
                        }
                    }
                }
                .pointerInput(sbn.key) {
                    if (!sbn.isClearable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(swipeOffsetX) > (swipeThreshold * 0.6f)) {
                                LockScreenNotificationListener.instance?.cancelNotification(sbn.key)
                            } else {
                                swipeOffsetX = 0f
                            }
                        },
                        onDragCancel = { swipeOffsetX = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-swipeThreshold * 1.2f, swipeThreshold * 1.2f)
                    }
                }
                .clip(shape)
                .clickable {
                    scope.launch {
                        try {
                            delay(100)
                            val displayId = launchOptions.getInt("launch_display_id", 0)
                            val dm = context.resources.displayMetrics
                            val screenW = dm.widthPixels
                            val screenH = dm.heightPixels

                            val w = screenW / 2
                            val h = screenH / 2
                            val l = (screenW - w) / 2
                            val t = (screenH - h) / 2

                            FreeformOverlayService.setIntendedBounds(sbn.packageName, android.graphics.Rect(l, t, l + w, t + h), displayId)
                            sbn.notification.contentIntent?.send(context, 0, null, null, null, null, launchOptions)
                            onDismissDropdown()
                        } catch (e: Exception) {
                            Log.e("NotificationCard", "Error sending contentIntent", e)
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (isExpanded) 12.dp else 14.dp,
                        bottom = if (isExpanded && isMediaCard) 18.dp else if (isExpanded) 18.dp else 14.dp,
                        start = 14.dp,
                        end = 14.dp
                    )
            ) {
                // --- TOP HEADER ROW ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.padding(top = 2.dp)) {
                        Box(modifier = Modifier.size(24.dp)) {
                            NotificationIconResolver(
                                context = context,
                                sbn = sbn,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Line 1: Header Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AnimatedContent(
                                targetState = isExpanded,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(animDurationMs, easing = FastOutSlowInEasing))
                                        .togetherWith(fadeOut(animationSpec = tween(animDurationMs, easing = FastOutSlowInEasing)))
                                },
                                label = "header_text_swap",
                                modifier = Modifier.weight(1f, fill = false)
                            ) { expanded ->
                                Text(
                                    text = if (expanded) appLabel else displayTitle,
                                    color = if (expanded) contentColor.copy(alpha = 0.6f) else contentColor,
                                    fontSize = if (expanded) 12.sp else 14.sp,
                                    fontWeight = if (expanded) FontWeight.Normal else FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = " • $timeString",
                                color = contentColor.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        // Content Block
                        AnimatedVisibility(
                            visible = isExpanded && displayTitle.isNotEmpty(),
                            enter = fadeIn(animationSpec = tween(durationMillis = animDurationMs, easing = FastOutSlowInEasing)) + expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = expandTweenSpec
                            ),
                            exit = fadeOut(animationSpec = tween(durationMillis = (animDurationMs * 0.8f).toInt(), easing = FastOutSlowInEasing)) + shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = expandTweenSpec
                            )
                        ) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = displayTitle,
                                    color = contentColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = text,
                                color = contentColor.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = if (isExpanded) 6 else 1,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // Right slot: Thumbnail (Uses collapsed 128px bitmap) & Chevron Behavior
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val colBmp = collapsedBitmap
                        if (colBmp != null) {
                            val hasBigPicture = scaled720pBitmap != null

                            val thumbnailAlpha by animateFloatAsState(
                                targetValue = if (isExpanded && (isMediaCard || hasBigPicture)) 0f else 1f,
                                animationSpec = tween(durationMillis = 180),
                                label = "thumb_alpha"
                            )

                            val thumbnailSize by animateDpAsState(
                                targetValue = if (!isMediaCard && isExpanded && !hasBigPicture) 48.dp else 36.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "thumb_size"
                            )

                            Spacer(Modifier.width(8.dp))
                            Image(
                                bitmap = colBmp,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .graphicsLayer { alpha = thumbnailAlpha }
                                    .size(thumbnailSize)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }

                        val isExpandable = remember(sbn) {
                            sbn.notification.actions?.isNotEmpty() == true ||
                                    largeIcon != null || extras.getCharSequence(Notification.EXTRA_BIG_TEXT) != null || text.length > 50
                        }
                        if (isExpandable) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.08f))
                                    .clickable { onToggleExpand() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle",
                                    tint = contentColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // --- FULL WIDTH EXPANDED BODY ---
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = expandTweenSpec
                    ) + fadeIn(animationSpec = tween(durationMillis = animDurationMs, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = expandTweenSpec
                    ) + fadeOut(animationSpec = tween(durationMillis = (animDurationMs * 0.8f).toInt(), easing = FastOutSlowInEasing))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        // Expanded Media Thumbnail Slot (Crossfades between track cover changes)
                        if (isMediaCard) {
                            Spacer(Modifier.height(10.dp))
                            val trackStateKey = remember(expandedMediaBitmap, displayTitle, text) {
                                "${sbn.key}_${displayTitle}_${text}"
                            }
                            Crossfade(
                                targetState = expandedMediaBitmap to trackStateKey,
                                animationSpec = tween(durationMillis = 350),
                                label = "media_artwork_crossfade"
                            ) { (currentBitmap, _) ->
                                if (currentBitmap != null) {
                                    Image(
                                        bitmap = currentBitmap,
                                        contentDescription = "Media Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                }
                            }
                        } else {
                            val bigPicBmp = scaled720pBitmap
                            if (bigPicBmp != null) {
                                // Regular Big Picture (Non-media)
                                Spacer(Modifier.height(12.dp))
                                Image(
                                    bitmap = bigPicBmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }

                        // Actions / Player Buttons
                        val actions = sbn.notification.actions
                        if (!actions.isNullOrEmpty()) {
                            val isMedia = isMediaCard
                            val useCompact = actions.size >= 3 && !isMedia

                            Spacer(Modifier.height(if (isMedia) 10.dp else 8.dp))

                            Row(
                                horizontalArrangement = if (isMedia) Arrangement.SpaceEvenly else Arrangement.spacedBy(if (useCompact) 4.dp else 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (isMedia) 8.dp else 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                actions.forEachIndexed { index, action ->
                                    val hasRemoteInput = action.remoteInputs?.any { it.allowFreeFormInput } == true
                                    val actionBitmap = actionBitmaps[index]

                                    val buttonModifier = if (isMedia) {
                                        Modifier.weight(1f, fill = false)
                                    } else {
                                        Modifier.weight(1f, fill = true)
                                    }

                                    Box(
                                        modifier = buttonModifier
                                            .clip(if (isMedia) CircleShape else RoundedCornerShape(12.dp))
                                            .background(if (isMedia) contentColor.copy(alpha = 0.05f) else contentColor.copy(alpha = 0.08f))
                                            .clickable {
                                                if (hasRemoteInput) {
                                                    onToggleReply()
                                                } else {
                                                    try {
                                                        action.actionIntent?.send(context, 0, null, null, null, null, launchOptions)
                                                        if (!isMedia) {
                                                            if (sbn.isClearable) {
                                                                LockScreenNotificationListener.instance?.cancelNotification(sbn.key)
                                                            }
                                                            onDismissDropdown()
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("NotificationCard", "Error sending action intent", e)
                                                    }
                                                }
                                            }
                                            .padding(vertical = 6.dp, horizontal = if (isMedia) 10.dp else 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isMedia) {
                                            if (actionBitmap != null) {
                                                Image(
                                                    bitmap = actionBitmap,
                                                    contentDescription = action.title?.toString(),
                                                    colorFilter = ColorFilter.tint(contentColor.copy(alpha = 0.85f)),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                if (actionBitmap != null) {
                                                    Image(
                                                        bitmap = actionBitmap,
                                                        contentDescription = null,
                                                        colorFilter = ColorFilter.tint(if (hasRemoteInput) activeTileBg else contentColor.copy(alpha = 0.85f)),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                } else if (hasRemoteInput) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                                        contentDescription = null,
                                                        tint = activeTileBg,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }

                                                if (!action.title.isNullOrBlank()) {
                                                    Text(
                                                        text = action.title.toString(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (hasRemoteInput) activeTileBg else contentColor.copy(alpha = 0.9f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(if (isMedia) 4.dp else 8.dp))
                        }

                        // Reply slot
                        if (isReplying) {
                            val replyAction = sbn.notification.actions?.firstOrNull { action ->
                                action.remoteInputs?.any { it.allowFreeFormInput } == true
                            }
                            val remoteInput = replyAction?.remoteInputs?.firstOrNull { it.allowFreeFormInput }
                            if (remoteInput != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(cardBg)
                                        .border(0.5.dp, activeTileBg.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = replyText,
                                        onValueChange = onReplyTextChanged,
                                        singleLine = true,
                                        cursorBrush = SolidColor(activeTileBg),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            color = contentColor
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester),
                                        decorationBox = { inner ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (replyText.isEmpty()) {
                                                    Text(
                                                        "Reply...",
                                                        fontSize = 12.sp,
                                                        color = contentColor.copy(alpha = 0.4f)
                                                    )
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (replyText.isNotBlank()) activeTileBg
                                                else contentColor.copy(alpha = 0.1f)
                                            )
                                            .clickable {
                                                if (replyText.isBlank()) return@clickable
                                                val textToSend = replyText
                                                scope.launch(Dispatchers.IO) {
                                                    val bundle = Bundle().apply {
                                                        putCharSequence(remoteInput.resultKey, textToSend)
                                                    }
                                                    val intent = Intent()
                                                    android.app.RemoteInput.addResultsToIntent(
                                                        replyAction.remoteInputs, intent, bundle
                                                    )
                                                    try {
                                                        replyAction.actionIntent?.send(context, 0, intent, null, null, null, launchOptions)
                                                        LockScreenNotificationListener.instance?.cancelNotification(sbn.key)
                                                    } catch (e: Exception) {
                                                        Log.e("NotificationCard", "Failed to reply", e)
                                                    }
                                                }
                                                onReplyTextChanged("")
                                                onToggleReply()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = if (replyText.isNotBlank()) Color.White else contentColor.copy(alpha = 0.4f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun NotificationIconResolver(
    context: Context,
    sbn: StatusBarNotification,
    modifier: Modifier = Modifier
) {
    var iconBitmap by remember(sbn.key) { mutableStateOf<ImageBitmap?>(null) }
    var isAvatar by remember(sbn.key) { mutableStateOf(false) }

    LaunchedEffect(sbn.key) {
        withContext(Dispatchers.IO) {
            val cacheKey = sbn.key
            val isConv = isConversationNotificationPanel(sbn)

            val cached = NotificationIconCache.getIcon(context, cacheKey)
            if (cached != null) {
                iconBitmap = cached
                isAvatar = (isConv && sbn.notification.getLargeIcon() != null) || !isSystemPackage(sbn.packageName)
                return@withContext
            }

            if (isConv) {
                try {
                    val largeIcon = sbn.notification.getLargeIcon()
                    if (largeIcon != null) {
                        val drawable = largeIcon.loadDrawable(context)
                        if (drawable != null) {
                            val bmp = drawable.toBitmap(96, 96).asImageBitmap()
                            NotificationIconCache.putIcon(context, cacheKey, bmp)
                            iconBitmap = bmp
                            isAvatar = true
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationIcon", "Failed to load large icon", e)
                }
            }

            if (isSystemPackage(sbn.packageName)) {
                try {
                    val smallIcon = sbn.notification.smallIcon
                    if (smallIcon != null) {
                        val drawable = smallIcon.loadDrawable(context)
                        if (drawable != null) {
                            val bmp = drawable.toBitmap(96, 96).asImageBitmap()
                            NotificationIconCache.putIcon(context, cacheKey, bmp)
                            iconBitmap = bmp
                            isAvatar = false
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationIcon", "Failed to load system small icon", e)
                }
            }

            val appIconKey = sbn.packageName + "_appicon"
            val cachedAppIcon = NotificationIconCache.getIcon(context, appIconKey)
            if (cachedAppIcon != null) {
                iconBitmap = cachedAppIcon
                isAvatar = true
                return@withContext
            }

            try {
                val iconPackDrawable = IconPackManager.getIconFromPack(context, sbn.packageName)
                if (iconPackDrawable != null) {
                    val bmp = iconPackDrawable.toBitmap(96, 96).asImageBitmap()
                    NotificationIconCache.putIcon(context, appIconKey, bmp)
                    iconBitmap = bmp
                    isAvatar = true
                    return@withContext
                }
            } catch (e: Exception) {
                Log.e("NotificationIcon", "Failed to load icon from pack", e)
            }

            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                val drawable = pm.getApplicationIcon(appInfo)
                val bmp = drawable.toBitmap(96, 96).asImageBitmap()
                NotificationIconCache.putIcon(context, appIconKey, bmp)
                iconBitmap = bmp
                isAvatar = true
                return@withContext
            } catch (e: Exception) {
                Log.e("NotificationIcon", "Failed to load package icon fallback", e)
            }

            try {
                val smallIcon = sbn.notification.smallIcon
                if (smallIcon != null) {
                    val drawable = smallIcon.loadDrawable(context)
                    if (drawable != null) {
                        val bmp = drawable.toBitmap(96, 96).asImageBitmap()
                        NotificationIconCache.putIcon(context, cacheKey, bmp)
                        iconBitmap = bmp
                        isAvatar = false
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationIcon", "Failed to load final small icon fallback", e)
            }
        }
    }

    val appColor = remember(sbn.notification.color) {
        if (sbn.notification.color != 0) Color(sbn.notification.color)
        else Color(0xFF5F6368)
    }

    if (iconBitmap != null) {
        if (isAvatar) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = "Notification Icon",
                modifier = modifier.clip(CircleShape)
            )
        } else {
            Box(
                modifier = modifier
                    .background(appColor, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = "Notification Icon",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        val firstLetter = sbn.packageName.take(1).uppercase()
        Box(
            modifier = modifier.background(appColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLetter,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun LazyListScope.renderGroupedNotifications(
    group: Map.Entry<String, List<StatusBarNotification>>,
    isFirstInSection: Boolean,
    isLastInSection: Boolean,
    expandedBundles: Set<String>,
    onToggleBundle: (String) -> Unit,
    expandedNotifications: Set<String>,
    onToggleNotification: (String) -> Unit,
    replyingNotificationKey: String?,
    onToggleReply: (String?) -> Unit,
    replyText: String,
    onReplyTextChanged: (String) -> Unit,
    inactiveTileBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    cardBg: Color,
    context: Context,
    onDismissDropdown: () -> Unit,
    launchOptions: Bundle
) {
    val packageName = group.key
    val sbnList = group.value
    val appLabel = getAppLabel(context, packageName)

    val outerRadius = 24.dp
    val innerRadius = 4.dp

    val topRadius = if (isFirstInSection) outerRadius else innerRadius
    val bottomRadius = if (isLastInSection) outerRadius else innerRadius
    val mainShape = RoundedCornerShape(
        topStart = topRadius, topEnd = topRadius,
        bottomStart = bottomRadius, bottomEnd = bottomRadius
    )

    if (sbnList.size == 1) {
        item(key = sbnList[0].key) {
            NotificationCard(
                sbn = sbnList[0],
                appLabel = appLabel,
                isExpanded = expandedNotifications.contains(sbnList[0].key),
                onToggleExpand = { onToggleNotification(sbnList[0].key) },
                isReplying = replyingNotificationKey == sbnList[0].key,
                onToggleReply = { onToggleReply(if (replyingNotificationKey == sbnList[0].key) null else sbnList[0].key) },
                replyText = replyText,
                onReplyTextChanged = onReplyTextChanged,
                inactiveTileBg = inactiveTileBg,
                contentColor = contentColor,
                activeTileBg = activeTileBg,
                cardBg = cardBg,
                context = context,
                onDismissDropdown = onDismissDropdown,
                launchOptions = launchOptions,
                shape = mainShape
            )
        }
    } else {
        val bundleKey = packageName + "_bundle"
        val isBundleExpanded = expandedBundles.contains(bundleKey)

        item(key = bundleKey) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val headerShape = if (isBundleExpanded) {
                    RoundedCornerShape(topStart = topRadius, topEnd = topRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
                } else {
                    mainShape
                }

                BundleHeaderCard(
                    sbn = sbnList[0],
                    packageName = packageName,
                    appLabel = appLabel,
                    count = sbnList.size,
                    isExpanded = isBundleExpanded,
                    onClick = { onToggleBundle(bundleKey) },
                    inactiveTileBg = inactiveTileBg,
                    contentColor = contentColor,
                    context = context,
                    shape = headerShape
                )

                val animDuration = remember { ThemeManager.getNotifPanelAnimDuration(context) }
                AnimatedVisibility(
                    visible = isBundleExpanded,
                    enter = expandVertically(animationSpec = tween(animDuration)) + fadeIn(animationSpec = tween(animDuration)),
                    exit = shrinkVertically(animationSpec = tween(animDuration)) + fadeOut(animationSpec = tween(animDuration))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        sbnList.forEachIndexed { index, sbn ->
                            val isLastInBundle = index == sbnList.size - 1
                            val childBottomRadius = if (isLastInBundle) bottomRadius else innerRadius

                            NotificationCard(
                                sbn = sbn,
                                appLabel = appLabel,
                                isExpanded = expandedNotifications.contains(sbn.key),
                                onToggleExpand = { onToggleNotification(sbn.key) },
                                isReplying = replyingNotificationKey == sbn.key,
                                onToggleReply = { onToggleReply(if (replyingNotificationKey == sbn.key) null else sbn.key) },
                                replyText = replyText,
                                onReplyTextChanged = onReplyTextChanged,
                                inactiveTileBg = inactiveTileBg.copy(alpha = 0.7f),
                                contentColor = contentColor,
                                activeTileBg = activeTileBg,
                                cardBg = cardBg,
                                context = context,
                                onDismissDropdown = onDismissDropdown,
                                launchOptions = launchOptions,
                                shape = RoundedCornerShape(
                                    topStart = innerRadius, topEnd = innerRadius,
                                    bottomStart = childBottomRadius, bottomEnd = childBottomRadius
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}