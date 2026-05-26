package com.example.freeformshell

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val label: String, val packageName: String, val icon: ImageBitmap? = null, val isSystem: Boolean = false)
data class DisplayInfo(val id: Int, val name: String, val width: Int, val height: Int, val dpi: Int = 420, val activeDpi: Int = 420, val activeWidth: Int = width, val activeHeight: Int = height, val isRounded: Boolean = true)

@Composable
fun StatusChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(
                    width = 120.coerceAtLeast(1), 
                    height = 120.coerceAtLeast(1)
                ).asImageBitmap()
                withContext(Dispatchers.Main) { icon = bitmap }
            } catch (e: Exception) {
                Log.e("AppIcon", "Failed to load icon for $packageName: ${e.message}")
            }
        }
    }
    
    if (icon != null) {
        Image(bitmap = icon!!, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape))
    }
}

@Composable
fun DisplayShapeIcon(display: DisplayInfo, isEnabled: Boolean = true) {
    val maxSize = 32.dp
    val ratio = if (display.height > 0) display.width.toFloat() / display.height.toFloat() else 1f
    
    val (w, h) = if (ratio > 1f) {
        maxSize to (maxSize / ratio)
    } else {
        (maxSize * ratio) to maxSize
    }
    
    val colorAlpha = if (isEnabled) 1f else 0.4f
    val bgAlpha = if (isEnabled) 0.2f else 0.08f
    
    Box(
        modifier = Modifier
            .size(maxSize)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(w, h)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha),
                    RoundedCornerShape(4.dp)
                )
                .border(
                    1.5.dp, 
                    MaterialTheme.colorScheme.primary.copy(alpha = colorAlpha), 
                    RoundedCornerShape(4.dp)
                )
        )
    }
}
