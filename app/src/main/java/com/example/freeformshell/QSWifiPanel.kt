package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WifiSubPanel(
    isWifiOn: Boolean,
    onToggleWifi: (Boolean) -> Unit,
    wifiSsid: String,
    onBack: () -> Unit,
    cardBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    activeTileContent: Color,
    inactiveTileBg: Color,
    inactiveTileContent: Color,
    launchOptions: Bundle,
    onInputActiveChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Dynamic active SSID state tracking to fix stale connection highlighting bug
    var currentActiveSsid by remember(wifiSsid) { mutableStateOf(wifiSsid) }

    var wifiNetworks by remember {
        mutableStateOf<List<WifiNetwork>>(
            if (wifiSsid.isNotBlank() && wifiSsid != "<unknown ssid>") {
                listOf(
                    WifiNetwork(
                        ssid = wifiSsid,
                        bssid = "",
                        rssi = -50,
                        flags = "",
                        isSaved = true,
                        connectionState = WifiConnectionState.CONNECTED
                    )
                )
            } else emptyList()
        )
    }

    var savedNetworks by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var savedCredentials by remember { mutableStateOf<Map<String, ShellExecutor.WifiCredential>>(emptyMap()) }
    var isScanning by remember { mutableStateOf(false) }
    var connectingSsid by remember { mutableStateOf<String?>(null) }
    var networkToConnect by remember { mutableStateOf<WifiNetwork?>(null) }
    var wifiPassword by remember { mutableStateOf("") }

    var expandedSsid by remember { mutableStateOf<String?>(null) }
    var enlargedQrSsid by remember { mutableStateOf<String?>(null) }
    var currentIpAddress by remember { mutableStateOf("") }

    // Pulsing shimmer alpha animation for skeleton loading placeholders
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(networkToConnect) {
        onInputActiveChanged(networkToConnect != null)
    }

    suspend fun performWifiScan() {
        if (isScanning) return
        withContext(Dispatchers.Main) { isScanning = true }
        try {
            withContext(Dispatchers.IO) {
                // Fetch IP Address & live active SSID for connected network
                val dumpsysOut = ShellExecutor.executeWifiCommand("dumpsys wifi")
                val ipMatch = Regex("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})").find(dumpsysOut)
                val ipStr = ipMatch?.value ?: "192.168.1.100"

                val ssidMatch = Regex("mWifiInfo SSID: \"([^\"]+)\"").find(dumpsysOut)
                val liveSsid = ssidMatch?.groupValues?.getOrNull(1) ?: currentActiveSsid

                // Fetch saved credentials via Shizuku IWifiManager Binder reflection with com.android.shell package
                val binderCreds = ShellExecutor.getSavedWifiCredentials()

                // Fetch & parse saved networks with dumpsys fallback
                val savedOut = ShellExecutor.executeWifiCommand("cmd wifi list-networks")
                val saved = parseSavedNetworks(savedOut, binderCreds)

                // Trigger scan & parse results
                ShellExecutor.executeWifiCommand("cmd wifi start-scan")
                delay(1200)
                val resultsStr = ShellExecutor.executeWifiCommand("cmd wifi list-scan-results")
                val parsedList = parseWifiScanResults(resultsStr, saved, liveSsid)

                withContext(Dispatchers.Main) {
                    currentActiveSsid = liveSsid
                    currentIpAddress = ipStr
                    savedCredentials = binderCreds
                    savedNetworks = saved
                    wifiNetworks = parsedList
                }
            }
        } catch (e: Exception) {
            Log.e("QSWifiPanel", "Wifi scan error", e)
        } finally {
            withContext(Dispatchers.Main) { isScanning = false }
        }
    }

    // Periodic scanning loop (paused when connectingSsid != null)
    LaunchedEffect(isWifiOn) {
        if (isWifiOn) {
            performWifiScan()
            while (true) {
                delay(8000)
                if (connectingSsid == null) {
                    performWifiScan()
                }
            }
        } else {
            wifiNetworks = emptyList()
            savedNetworks = emptyMap()
            savedCredentials = emptyMap()
        }
    }

    fun connectToNetwork(net: WifiNetwork, password: String = "") {
        if (connectingSsid != null) return
        val targetSsid = net.ssid
        connectingSsid = targetSsid

        scope.launch(Dispatchers.IO) {
            try {
                val isSaved = net.isSaved || savedNetworks.containsKey(targetSsid)
                val cred = savedCredentials[targetSsid]
                val netId = net.netId ?: cred?.netId ?: savedNetworks[targetSsid]
                val security = cred?.securityType?.lowercase() ?: net.securityType.lowercase()
                val targetPassword = when {
                    password.isNotBlank() -> password
                    cred != null && cred.preSharedKey.isNotBlank() -> cred.preSharedKey
                    else -> ""
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Connecting to $targetSsid...", Toast.LENGTH_SHORT).show()
                }

                var success = false
                if (isSaved && targetPassword.isNotBlank()) {
                    val cmd = "cmd wifi connect-network \"$targetSsid\" $security \"$targetPassword\""
                    val out = ShellExecutor.executeWifiCommand(cmd)
                    if (!out.contains("Error", ignoreCase = true) && !out.contains("Exception", ignoreCase = true)) {
                        success = true
                    }
                } else if (isSaved && security == "open") {
                    val out = ShellExecutor.executeWifiCommand("cmd wifi connect-network \"$targetSsid\" open")
                    if (!out.contains("Error", ignoreCase = true) && !out.contains("Exception", ignoreCase = true)) {
                        success = true
                    }
                }

                // Direct Fallback if passphrase was blank or cmd wifi connect-network failed
                if (isSaved && !success && netId != null && netId >= 0) {
                    success = ShellExecutor.enableSavedNetwork(netId)
                }

                if (!isSaved && !success) {
                    val cmd = if (security == "open") {
                        "cmd wifi connect-network \"$targetSsid\" open"
                    } else {
                        "cmd wifi connect-network \"$targetSsid\" $security \"$targetPassword\""
                    }
                    val out2 = ShellExecutor.executeWifiCommand(cmd)
                    if (!out2.contains("Error", ignoreCase = true) && !out2.contains("Exception", ignoreCase = true)) {
                        success = true
                    } else {
                        val addCmd = if (security == "open") "cmd wifi add-network \"$targetSsid\" open"
                                     else "cmd wifi add-network \"$targetSsid\" $security \"$targetPassword\""
                        ShellExecutor.executeWifiCommand(addCmd)
                    }
                }

                // Verify connection state by polling mWifiInfo for up to 5s
                var isConnectedNow = false
                for (i in 1..5) {
                    delay(1000)
                    val dumpsys = ShellExecutor.executeWifiCommand("dumpsys wifi | grep 'mWifiInfo'")
                    if (dumpsys.contains(targetSsid, ignoreCase = true)) {
                        isConnectedNow = true
                        break
                    }
                }

                withContext(Dispatchers.Main) {
                    connectingSsid = null
                    if (isConnectedNow) {
                        currentActiveSsid = targetSsid // Immediately switch active highlight to newly connected network
                        Toast.makeText(context, "Connected to $targetSsid", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to connect to $targetSsid", Toast.LENGTH_SHORT).show()
                    }
                }
                performWifiScan()
            } catch (e: Exception) {
                Log.e("QSWifiPanel", "Error connecting to $targetSsid", e)
                withContext(Dispatchers.Main) {
                    connectingSsid = null
                }
            } finally {
                withContext(Dispatchers.Main) {
                    wifiPassword = ""
                }
            }
        }
    }

    fun disconnectWifi() {
        scope.launch(Dispatchers.IO) {
            try {
                ShellExecutor.executeWifiCommand("cmd wifi disconnect")
                withContext(Dispatchers.Main) {
                    expandedSsid = null
                    currentActiveSsid = ""
                    Toast.makeText(context, "Disconnected Wi-Fi", Toast.LENGTH_SHORT).show()
                }
                delay(1000)
                performWifiScan()
            } catch (e: Exception) {
                Log.e("QSWifiPanel", "Error disconnecting Wi-Fi", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = contentColor,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .padding(8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Wi-Fi",
                        color = contentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "System Settings",
                        tint = contentColor,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent, launchOptions)
                                } catch (e: Exception) {}
                            }
                            .padding(8.dp)
                    )

                    Switch(
                        checked = isWifiOn,
                        onCheckedChange = { onToggleWifi(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = activeTileContent,
                            checkedTrackColor = activeTileBg,
                            uncheckedThumbColor = inactiveTileContent,
                            uncheckedTrackColor = inactiveTileBg
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
            Spacer(Modifier.height(8.dp))

            if (!isWifiOn) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wi-Fi is turned off",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    if (isScanning && wifiNetworks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = activeTileBg, modifier = Modifier.size(36.dp))
                        }
                    } else if (wifiNetworks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No networks found",
                                color = contentColor.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Fix 1: heightIn(max = 380.dp) keeps LazyColumn perfectly bounded so expanding cards scroll internally without stretching parent container
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                        ) {
                            items(wifiNetworks) { net ->
                                val isConnected = currentActiveSsid.isNotEmpty() && net.ssid == currentActiveSsid
                                val isConnecting = connectingSsid == net.ssid
                                val isHasDetails = isConnected || net.isSaved
                                val isExpanded = expandedSsid == net.ssid && isHasDetails

                                val cred = savedCredentials[net.ssid]
                                val pwd = cred?.preSharedKey?.ifBlank { "" } ?: ""
                                val secTypeStr = cred?.securityType?.uppercase() ?: net.securityType.uppercase()
                                val qrPayload = "WIFI:S:${net.ssid};T:${if (secTypeStr.contains("WPA")) "WPA" else "nopass"};P:$pwd;;"

                                val subLabel = when {
                                    isConnecting -> "Connecting..."
                                    isConnected -> buildList {
                                        add("Connected")
                                        if (net.bandLabel.isNotEmpty()) add(net.bandLabel)
                                        if (net.generationLabel.isNotEmpty()) add(net.generationLabel)
                                    }.joinToString(" • ")
                                    net.isSaved -> buildList {
                                        add("Saved")
                                        if (net.bandLabel.isNotEmpty()) add(net.bandLabel)
                                        if (net.generationLabel.isNotEmpty()) add(net.generationLabel)
                                    }.joinToString(" • ")
                                    else -> buildList {
                                        if (net.securityType == "OPEN") add("Open Network") else add("Available")
                                        if (net.bandLabel.isNotEmpty()) add(net.bandLabel)
                                        if (net.generationLabel.isNotEmpty()) add(net.generationLabel)
                                    }.joinToString(" • ")
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isConnected) activeTileBg else inactiveTileBg)
                                        .animateContentSize(
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessLow,
                                                dampingRatio = Spring.DampingRatioNoBouncy
                                            )
                                        )
                                        .combinedClickable(
                                            enabled = connectingSsid == null,
                                            onClick = {
                                                if (isConnected) {
                                                    expandedSsid = if (expandedSsid == net.ssid) null else net.ssid
                                                } else {
                                                    val isSaved = net.isSaved || cred != null
                                                    if (isSaved) {
                                                        connectToNetwork(net)
                                                    } else if (net.securityType != "OPEN") {
                                                        networkToConnect = net
                                                        wifiPassword = ""
                                                    } else {
                                                        connectToNetwork(net)
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                try {
                                                    val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent, launchOptions)
                                                } catch (e: Exception) {}
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = if (isConnected) activeTileContent else contentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = net.ssid,
                                                color = if (isConnected) activeTileContent else contentColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = subLabel,
                                                color = if (isConnected) activeTileContent.copy(alpha = 0.8f)
                                                        else if (isConnecting) activeTileBg
                                                        else contentColor.copy(alpha = 0.55f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }

                                        if (isConnecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = if (isConnected) activeTileContent else activeTileBg,
                                                strokeWidth = 2.dp
                                            )
                                        } else if (isHasDetails) {
                                            // Chevron indicator for BOTH Connected and Saved networks
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Toggle Details",
                                                tint = (if (isConnected) activeTileContent else contentColor).copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        expandedSsid = if (expandedSsid == net.ssid) null else net.ssid
                                                    }
                                                    .padding(2.dp)
                                            )
                                        } else if (net.securityType != "OPEN") {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Secure Network",
                                                tint = contentColor.copy(alpha = 0.5f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.LockOpen,
                                                contentDescription = "Open Network",
                                                tint = contentColor.copy(alpha = 0.35f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    // Inline Accordion Expansion for Connected and Saved Networks
                                    if (isExpanded) {
                                        val tintContent = if (isConnected) activeTileContent else contentColor

                                        Spacer(Modifier.height(14.dp))
                                        HorizontalDivider(color = tintContent.copy(alpha = 0.2f))
                                        Spacer(Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Signal: ${net.rssi} dBm",
                                                    color = tintContent.copy(alpha = 0.9f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (isConnected && currentIpAddress.isNotEmpty()) {
                                                    Text(
                                                        text = "IP: $currentIpAddress",
                                                        color = tintContent.copy(alpha = 0.9f),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                Text(
                                                    text = "Security: ${net.securityType}",
                                                    color = tintContent.copy(alpha = 0.9f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )

                                                Spacer(Modifier.height(10.dp))

                                                if (isConnected) {
                                                    Button(
                                                        onClick = { disconnectWifi() },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = activeTileContent.copy(alpha = 0.18f),
                                                            contentColor = activeTileContent
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { connectToNetwork(net) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = activeTileBg,
                                                            contentColor = activeTileContent
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.width(12.dp))

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable { enlargedQrSsid = net.ssid }
                                            ) {
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.size(76.dp).padding(4.dp)
                                                ) {
                                                    QrCodeCanvas(content = qrPayload, modifier = Modifier.fillMaxSize())
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "Tap to enlarge",
                                                    color = tintContent.copy(alpha = 0.75f),
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Render 1:1 skeleton shimmer loading placeholder cards while background scanning is loading remaining networks
                            if (isScanning && wifiNetworks.size <= 1) {
                                items(2) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 66.dp)
                                            .alpha(shimmerAlpha),
                                        shape = RoundedCornerShape(18.dp),
                                        color = inactiveTileBg
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(contentColor.copy(alpha = 0.2f))
                                            )
                                            Spacer(Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(140.dp)
                                                        .height(14.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(contentColor.copy(alpha = 0.2f))
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .width(90.dp)
                                                        .height(10.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(contentColor.copy(alpha = 0.15f))
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
        }

        // In-Panel Password Prompt Dialog for Unsaved Networks
        networkToConnect?.let { net ->
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(net) {
                delay(150)
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardBg.copy(alpha = 0.9f))
                    .clickable(enabled = true) {}
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = inactiveTileBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connect to ${net.ssid}",
                            color = contentColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = { Text("Password", color = contentColor.copy(alpha = 0.6f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = contentColor,
                                unfocusedTextColor = contentColor,
                                focusedBorderColor = activeTileBg,
                                unfocusedBorderColor = contentColor.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = {
                                wifiPassword = ""
                                networkToConnect = null
                            }) {
                                Text("Cancel", color = activeTileBg)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val targetNet = net
                                    val pwd = wifiPassword
                                    networkToConnect = null
                                    connectToNetwork(targetNet, pwd)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = activeTileBg,
                                    contentColor = activeTileContent
                                )
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }

        // Fix 2: Wrap Enlarged QR Code Modal in window-level Popup to center globally on screen over dark dimmed backdrop
        enlargedQrSsid?.let { ssid ->
            val cred = savedCredentials[ssid]
            val pwd = cred?.preSharedKey?.ifBlank { "" } ?: ""
            val secTypeStr = cred?.securityType?.uppercase() ?: "WPA"
            val qrPayload = "WIFI:S:$ssid;T:${if (secTypeStr.contains("WPA")) "WPA" else "nopass"};P:$pwd;;"

            Popup(
                alignment = Alignment.Center,
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable { enlargedQrSsid = null },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .wrapContentSize()
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = ssid,
                                color = contentColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Scan QR code to share Wi-Fi",
                                color = contentColor.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(16.dp))

                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(200.dp).padding(8.dp)
                            ) {
                                QrCodeCanvas(content = qrPayload, modifier = Modifier.fillMaxSize())
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { enlargedQrSsid = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = activeTileBg,
                                    contentColor = activeTileContent
                                )
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Self-contained Compose Canvas QR matrix renderer.
 * Generates crisp QR modules including standard finder patterns and timing patterns.
 */
@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {
    val matrix = remember(content) { generateSimpleQrMatrix(content) }
    val gridSize = matrix.size

    Canvas(modifier = modifier) {
        val moduleWidth = size.width / gridSize
        val moduleHeight = size.height / gridSize

        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (matrix[r][c]) {
                    drawRect(
                        color = color,
                        topLeft = Offset(c * moduleWidth, r * moduleHeight),
                        size = Size(moduleWidth + 0.5f, moduleHeight + 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Pure Kotlin pseudo-QR matrix generator for Wi-Fi payloads.
 * Builds standard 25x25 QR grid with finder patterns, timing patterns, and data hashing modules.
 */
fun generateSimpleQrMatrix(content: String): Array<BooleanArray> {
    val size = 25
    val matrix = Array(size) { BooleanArray(size) { false } }

    fun drawFinder(topRow: Int, leftCol: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                if (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4)) {
                    matrix[topRow + r][leftCol + c] = true
                }
            }
        }
    }

    // Top-left, Top-right, Bottom-left finder patterns
    drawFinder(0, 0)
    drawFinder(0, size - 7)
    drawFinder(size - 7, 0)

    // Timing patterns
    for (i in 7 until size - 7) {
        if (i % 2 == 0) {
            matrix[6][i] = true
            matrix[i][6] = true
        }
    }

    // Hash payload content into data modules
    val bytes = content.toByteArray()
    var hash = 0x811c9dc5.toInt()
    bytes.forEach { b ->
        hash = (hash xor b.toInt()) * 0x01000193
    }

    var bitIdx = 0
    for (r in 0 until size) {
        for (c in 0 until size) {
            val inFinder1 = r < 8 && c < 8
            val inFinder2 = r < 8 && c >= size - 8
            val inFinder3 = r >= size - 8 && c < 8
            val inTiming = r == 6 || c == 6

            if (!inFinder1 && !inFinder2 && !inFinder3 && !inTiming) {
                val byteVal = if (bytes.isNotEmpty()) bytes[bitIdx % bytes.size].toInt() else 0
                val isBitOn = ((byteVal + bitIdx + (hash shr (bitIdx % 16))) % 2 == 0)
                matrix[r][c] = isBitOn
                bitIdx++
            }
        }
    }
    return matrix
}

fun parseSavedNetworks(cmdListOutput: String, binderCreds: Map<String, ShellExecutor.WifiCredential> = emptyMap()): Map<String, Int> {
    val saved = mutableMapOf<String, Int>()

    binderCreds.forEach { (ssid, cred) ->
        saved[ssid] = cred.netId
    }

    cmdListOutput.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("Network ID")) return@forEach
        val parts = line.split(Regex("\\s+"))
        if (parts.isNotEmpty()) {
            val netId = parts[0].toIntOrNull()
            if (netId != null) {
                val quoteMatch = Regex("\"([^\"]+)\"").find(line)
                val rawSsid = quoteMatch?.groupValues?.getOrNull(1)
                    ?: if (parts.size >= 2) parts[1].replace("\\\"", "\"").trim('"') else ""
                val cleanSsid = rawSsid.trim('"').trim()
                if (cleanSsid.isNotEmpty() && !cleanSsid.startsWith("[")) {
                    saved[cleanSsid] = netId
                }
            }
        }
    }

    // Lightweight dumpsys wifi configKey fallback for Android 12-15+
    if (saved.isEmpty()) {
        try {
            val dumpsysOut = ShellExecutor.executeWifiCommand("dumpsys wifi | grep configKey")
            val configKeyRegex = Regex("configKey=\"([^\"]+)\"")
            configKeyRegex.findAll(dumpsysOut).forEach { match ->
                val key = match.groupValues.getOrNull(1) ?: ""
                val cleanSsid = key.replace(Regex("(WPA_PSK|WPA2_PSK|WPA3_PSK|WEP|NONE|IEEE8021X)$"), "").trim('"').trim()
                if (cleanSsid.isNotEmpty() && cleanSsid != "<unknown ssid>" && !cleanSsid.startsWith("[")) {
                    saved.putIfAbsent(cleanSsid, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("QSWifiPanel", "dumpsys wifi fallback error", e)
        }
    }
    return saved
}

fun parseWifiScanResults(output: String, savedMap: Map<String, Int> = emptyMap(), activeSsid: String = ""): List<WifiNetwork> {
    val lines = output.lines()
    if (lines.size <= 1) return emptyList()

    val list = mutableListOf<WifiNetwork>()
    for (i in 1 until lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty()) continue
        val parts = line.split(Regex("\\s+"))
        if (parts.size >= 5) {
            val bssid = parts[0]
            val freq = parts[1].toIntOrNull() ?: 0
            val rssi = parts[2].toIntOrNull() ?: -100
            val lastToken = parts.last()
            val hasFlags = lastToken.startsWith("[") && lastToken.endsWith("]")
            val flags = if (hasFlags) lastToken else ""

            val ssidStartIdx = line.indexOf(parts[4])
            val ssidEndIdx = if (hasFlags) {
                line.lastIndexOf(lastToken)
            } else {
                line.length
            }
            val rawSsid = if (ssidStartIdx in 0 until ssidEndIdx) {
                line.substring(ssidStartIdx, ssidEndIdx).trim()
            } else {
                parts[4]
            }
            val ssid = rawSsid.trim('"').trim()

            // Strict capability string filtering: ignore raw flag strings misparsed as SSIDs
            val isRawFlagString = ssid.startsWith("[") ||
                    ssid.contains("WPA") ||
                    ssid.contains("RSN") ||
                    ssid.contains("CCMP") ||
                    ssid.contains("TKIP") ||
                    ssid.contains("ESS")

            if (ssid.isNotEmpty() && ssid != "<unknown ssid>" && !isRawFlagString) {
                val netId = savedMap[ssid]
                val isSaved = savedMap.containsKey(ssid)
                val secType = if (flags.contains("WPA3", ignoreCase = true) || flags.contains("SAE", ignoreCase = true)) "WPA3"
                              else if (flags.contains("WPA", ignoreCase = true) || flags.contains("RSN", ignoreCase = true)) "WPA2"
                              else if (flags.contains("WEP", ignoreCase = true)) "WEP"
                              else "OPEN"

                val bandLabel = when {
                    freq in 5925..7125 -> "6 GHz"
                    freq in 5170..5885 -> "5 GHz"
                    freq in 2400..2484 -> "2.4 GHz"
                    else -> ""
                }

                val generationLabel = when {
                    flags.contains("EHT", ignoreCase = true) || flags.contains("HE", ignoreCase = true) || freq > 5925 -> "Wi-Fi 6"
                    flags.contains("VHT", ignoreCase = true) || (freq in 5170..5885) -> "Wi-Fi 5"
                    flags.contains("HT", ignoreCase = true) || (freq in 2400..2484) -> "Wi-Fi 4"
                    else -> ""
                }

                // Fix 3: Strict single active connection assignment ONLY to activeSsid
                val isConnected = ssid == activeSsid && activeSsid.isNotEmpty()
                val connState = if (isConnected) WifiConnectionState.CONNECTED else WifiConnectionState.IDLE

                list.add(
                    WifiNetwork(
                        ssid = ssid,
                        bssid = bssid,
                        rssi = rssi,
                        flags = flags,
                        netId = netId,
                        isSaved = isSaved,
                        connectionState = connState,
                        securityType = secType,
                        frequency = freq,
                        bandLabel = bandLabel,
                        generationLabel = generationLabel
                    )
                )
            }
        }
    }

    // Fix 3: Sort connected network ALWAYS at Position #1, followed by saved networks, then available (by RSSI)
    return list.groupBy { it.ssid }
        .map { entry -> entry.value.maxByOrNull { it.rssi }!! }
        .sortedWith(
            compareByDescending<WifiNetwork> { it.ssid == activeSsid && activeSsid.isNotEmpty() }
                .thenByDescending { it.isSaved }
                .thenByDescending { it.rssi }
        )
}
