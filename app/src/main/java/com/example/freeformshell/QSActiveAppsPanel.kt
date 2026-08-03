package com.example.freeformshell

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ActiveAppsPill(
    activeCount: Int,
    contentColor: Color,
    inactiveTileBg: Color,
    onClick: () -> Unit
) {
    if (activeCount > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(inactiveTileBg)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$activeCount app${if (activeCount > 1) "s are" else " is"} active",
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "View Active Apps",
                tint = contentColor.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ActiveAppsSubPanel(
    activeApps: List<ActiveAppInfo>,
    onStopApp: (String) -> Unit,
    onBack: () -> Unit,
    cardBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    inactiveTileBg: Color,
    context: Context
) {
    Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                text = "Active apps",
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "These apps are active and running, even when you're not using them. This improves their functionality, but it may also affect battery life.",
            color = contentColor.copy(alpha = 0.6f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(activeApps) { app ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = inactiveTileBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            var appIconDrawable by remember(app.packageName) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
                            LaunchedEffect(app.packageName) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val pm = context.packageManager
                                        val appInfo = pm.getApplicationInfo(app.packageName, 0)
                                        appIconDrawable = pm.getApplicationIcon(appInfo)
                                    } catch (e: Exception) {}
                                }
                            }

                            if (appIconDrawable != null) {
                                val bitmap = remember(appIconDrawable) { appIconDrawable!!.toBitmap(96, 96).asImageBitmap() }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(contentColor.copy(alpha = 0.1f), CircleShape)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = app.label,
                                    color = contentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = app.runningDuration,
                                    color = contentColor.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = { onStopApp(app.packageName) },
                            colors = ButtonDefaults.buttonColors(containerColor = activeTileBg),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                "Stop",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getActiveAppsNatively(context: Context): List<ActiveAppInfo> {
    val activeList = mutableListOf<ActiveAppInfo>()
    try {
        if (rikka.shizuku.Shizuku.pingBinder()) {
            val amBinder = rikka.shizuku.SystemServiceHelper.getSystemService("activity")
            val shizukuBinder = rikka.shizuku.ShizukuBinderWrapper(amBinder)
            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
            val activityManager = asInterfaceMethod.invoke(null, shizukuBinder)
            
            val getServicesMethod = activityManager.javaClass.getMethod("getServices", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val rawServices = getServicesMethod.invoke(activityManager, 100, 0) as List<*>
            
            val pm = context.packageManager
            val groupedByPackage = mutableMapOf<String, Long>()
            
            for (serviceObj in rawServices) {
                if (serviceObj == null) continue
                val serviceInfo = serviceObj as android.app.ActivityManager.RunningServiceInfo
                if (serviceInfo.foreground) {
                    val pkg = serviceInfo.service.packageName
                    if (pkg == context.packageName || pkg == "android" || pkg == "com.android.systemui") continue
                    
                    val currentEarliest = groupedByPackage[pkg]
                    if (currentEarliest == null || serviceInfo.activeSince < currentEarliest) {
                        groupedByPackage[pkg] = serviceInfo.activeSince
                    }
                }
            }
            
            val now = android.os.SystemClock.elapsedRealtime()
            for ((pkg, activeSince) in groupedByPackage) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val durationMs = now - activeSince
                    val durationStr = formatActiveAppDuration(durationMs)
                    activeList.add(ActiveAppInfo(packageName = pkg, label = label, runningDuration = durationStr))
                } catch (e: Exception) {}
            }
        } else {
            // Fallback: Query via dumpsys shell command
            val out = ShellExecutor.executeCommand("dumpsys activity services")
            val pm = context.packageManager
            val lines = out.lines()
            var inSection = false
            var currentPkg = ""
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Active foreground apps - user")) {
                    inSection = true
                    continue
                }
                
                if (inSection && line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("\t")) {
                    inSection = false
                }
                
                if (inSection) {
                    if (trimmed.startsWith("#")) {
                        val parts = trimmed.split(":")
                        if (parts.size >= 2) {
                            val pkg = parts[1].trim()
                            if (pkg.isNotEmpty() && pkg != context.packageName && pkg != "android" && pkg != "com.android.systemui") {
                                currentPkg = pkg
                            } else {
                                currentPkg = ""
                            }
                        }
                    } else if (currentPkg.isNotEmpty() && trimmed.startsWith("mStartTime=")) {
                        val parts = trimmed.split(" ")
                        val startPart = parts.firstOrNull { it.startsWith("mStartTime=") }
                        if (startPart != null) {
                            var durationRaw = startPart.substringAfter("mStartTime=")
                            if (durationRaw.startsWith("-")) {
                                durationRaw = durationRaw.substring(1)
                            }
                            val cleanDuration = formatDumpsysDuration(durationRaw)
                            try {
                                val appInfo = pm.getApplicationInfo(currentPkg, 0)
                                val label = pm.getApplicationLabel(appInfo).toString()
                                activeList.add(ActiveAppInfo(packageName = currentPkg, label = label, runningDuration = cleanDuration))
                            } catch (e: Exception) {}
                        }
                        currentPkg = ""
                    }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("ActiveApps", "Error querying active services natively", e)
    }
    return activeList
}

fun formatDumpsysDuration(raw: String): String {
    val duration = raw
    val dIndex = duration.indexOf('d')
    val hIndex = duration.indexOf('h')
    val mIndex = duration.indexOf('m')
    
    val dPart = if (dIndex >= 0) duration.substring(0, dIndex + 1) else ""
    val hPart = if (hIndex >= 0) {
        val start = if (dIndex >= 0) dIndex + 1 else 0
        duration.substring(start, hIndex + 1)
    } else ""
    val mPart = if (mIndex >= 0) {
        val start = if (hIndex >= 0) hIndex + 1 else if (dIndex >= 0) dIndex + 1 else 0
        duration.substring(start, mIndex + 1)
    } else ""
    
    val partsStr = listOf(dPart, hPart, mPart).filter { it.isNotEmpty() }.joinToString(" ")
    return if (partsStr.isEmpty()) "0m" else partsStr
}
