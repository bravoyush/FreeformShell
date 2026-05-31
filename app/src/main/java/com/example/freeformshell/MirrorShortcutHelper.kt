package com.example.freeformshell

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.*
import android.graphics.drawable.Icon
import android.app.WallpaperManager
import android.os.Build
import android.util.Log

object MirrorShortcutHelper {
    private const val TAG = "MirrorShortcutHelper"
    const val SHORTCUT_ID = "phone_mirror_premium_shortcut"

    /**
     * Converts a cryptic device model code to its beautiful retail/friendly name.
     * E.g. Sony SOG06 / XQ-CT54 -> "Xperia 1 Mark IV"
     */
    fun getFriendlyDeviceName(model: String): String {
        val cleanModel = model.trim().uppercase(java.util.Locale.US)
        
        // Sony Xperia 1 Series
        if (cleanModel == "SOG06" || cleanModel == "XQ-CT54" || cleanModel == "XQ-CT72" || cleanModel.contains("SO-51C")) {
            return "Xperia 1 Mark IV"
        }
        if (cleanModel == "SOG01" || cleanModel == "XQ-AT51" || cleanModel == "XQ-AT52" || cleanModel.contains("SO-51A")) {
            return "Xperia 1"
        }
        if (cleanModel == "SOG03" || cleanModel == "XQ-BC52" || cleanModel == "XQ-BC72" || cleanModel.contains("SO-51B")) {
            return "Xperia 1 Mark III"
        }
        if (cleanModel == "SOG10" || cleanModel == "XQ-DQ54" || cleanModel == "XQ-DQ72" || cleanModel.contains("SO-51D")) {
            return "Xperia 1 Mark V"
        }
        
        // Sony Xperia 5 Series
        if (cleanModel == "SOG02" || cleanModel == "XQ-AS52" || cleanModel.contains("SO-52A")) {
            return "Xperia 5 II"
        }
        if (cleanModel == "SOG05" || cleanModel == "XQ-BS52" || cleanModel.contains("SO-52B")) {
            return "Xperia 5 III"
        }
        if (cleanModel == "SOG09" || cleanModel == "XQ-CQ54" || cleanModel.contains("SO-52C")) {
            return "Xperia 5 IV"
        }
        if (cleanModel == "SOG12" || cleanModel == "XQ-DE54" || cleanModel.contains("SO-52D")) {
            return "Xperia 5 Mark V"
        }
        
        // Other common model identifiers (Samsung/Google/Xiaomi)
        if (cleanModel.startsWith("SM-G")) {
            return "Galaxy Phone (${model})"
        }
        
        // General Brand Prefix Stripping for readability
        var friendly = model
        val brandPrefixes = listOf("Sony ", "Samsung ", "Google ", "Xiaomi ", "OnePlus ", "Oppo ", "Realme ", "Motorola ", "Asus ")
        for (prefix in brandPrefixes) {
            if (friendly.startsWith(prefix, ignoreCase = true)) {
                friendly = friendly.substring(prefix.length)
            }
        }
        
        return friendly
    }

    /**
     * Generates a stunning, pixel-perfect 512x512 bitmap icon representing a phone.
     * Extracts the user's actual active system wallpaper, renders it inside the screen boundary,
     * and layers realistic bezels, reflections, punch-hole cameras, and subtle depth shadows.
     */
    fun generatePhoneWallpaperIcon(context: Context): Bitmap {
        val size = 512
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        canvas.drawColor(Color.TRANSPARENT)
        
        // Retrieve system wallpaper
        val wallpaperManager = WallpaperManager.getInstance(context)
        val wallpaperDrawable = try {
            wallpaperManager.drawable
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve system wallpaper drawable, using fallback gradient", e)
            null
        }
        
        // 1. Sleek Phone Body Frame
        val border = 40f
        val r = RectF(border, border, size - border, size - border)
        val cornerRadius = 60f
        
        // Frame Shadow/Glow (Obsidian Slate look)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0)
            setShadowLayer(24f, 0f, 12f, Color.BLACK)
        }
        canvas.drawRoundRect(r, cornerRadius, cornerRadius, shadowPaint)
        
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18171E") // Sleek deep metallic phone frame
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(r, cornerRadius, cornerRadius, framePaint)
        
        // 2. Bezel & Screen setup
        val bezel = 20f
        val screenRect = RectF(r.left + bezel, r.top + bezel, r.right - bezel, r.bottom - bezel)
        val screenCornerRadius = cornerRadius - bezel * 0.5f
        
        canvas.save()
        val screenPath = Path().apply {
            addRoundRect(screenRect, screenCornerRadius, screenCornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(screenPath)
        
        // 3. Render wallpaper inside
        if (wallpaperDrawable != null) {
            val wRatio = screenRect.width() / wallpaperDrawable.intrinsicWidth.toFloat()
            val hRatio = screenRect.height() / wallpaperDrawable.intrinsicHeight.toFloat()
            val scale = Math.max(wRatio, hRatio)
            
            val drawW = wallpaperDrawable.intrinsicWidth * scale
            val drawH = wallpaperDrawable.intrinsicHeight * scale
            
            val left = screenRect.centerX() - drawW / 2f
            val top = screenRect.centerY() - drawH / 2f
            
            wallpaperDrawable.setBounds(left.toInt(), top.toInt(), (left + drawW).toInt(), (top + drawH).toInt())
            wallpaperDrawable.draw(canvas)
        } else {
            // Draw a breathtaking fallback deep space violet gradient
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, screenRect.top, 0f, screenRect.bottom,
                    Color.parseColor("#1D0A35"), Color.parseColor("#09081C"), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(screenRect, fallbackPaint)
        }
        
        canvas.restore()
        
        // 4. Glossy screen glass shine overlay
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                screenRect.left, screenRect.top, screenRect.right, screenRect.bottom,
                intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(15, 255, 255, 255)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.clipPath(screenPath)
        canvas.drawRect(screenRect, shinePaint)
        canvas.restore()
        
        // 5. Bezel Outline Highlights
        val bezelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3C3B43")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(screenRect, screenCornerRadius, screenCornerRadius, bezelStrokePaint)
        
        // 6. Camera Punch Hole
        val punchHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val punchHoleCenterY = screenRect.top + 28f
        canvas.drawCircle(screenRect.centerX(), punchHoleCenterY, 11f, punchHolePaint)
        
        // Camera lens blue coating reflect
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 0, 110, 240)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(screenRect.centerX() - 3f, punchHoleCenterY - 3f, 4.5f, lensPaint)
        
        return bitmap
    }

    /**
     * Programmatically requests pinning a beautiful, dynamic, custom-drawn Mirror Shortcut on the home screen.
     */
    fun pinPremiumMirrorShortcut(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
            if (!shortcutManager.isRequestPinShortcutSupported()) {
                Log.w(TAG, "Requesting pinned shortcuts is not supported by the active launcher.")
                return
            }

            val deviceName = getFriendlyDeviceName(Build.MODEL)
            val label = "Mirror ($deviceName)"
            val iconBitmap = generatePhoneWallpaperIcon(context)

            // Dynamic launch intent for the Mirror activity
            val intent = Intent(context, PhoneMirrorActivity::class.java).apply {
                action = Intent.ACTION_MAIN
            }

            val shortcutInfo = ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel(label)
                .setLongLabel("Phone Mirroring ($deviceName)")
                .setIcon(Icon.createWithBitmap(iconBitmap))
                .setIntent(intent)
                .build()

            shortcutManager.requestPinShortcut(shortcutInfo, null)
            Log.d(TAG, "Requested to pin premium mirror shortcut: $label")
        } catch (e: Exception) {
            Log.e(TAG, "Failed pinning mirror shortcut", e)
        }
    }

    /**
     * Refreshes/updates the dynamic mirror shortcut icon and text if it already exists on the home screen.
     * Invoked on app fresh launches and startup to keep the wallpaper perfectly in sync.
     */
    fun updatePinnedMirrorShortcutIfExist(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
            val deviceName = getFriendlyDeviceName(Build.MODEL)
            val label = "Mirror ($deviceName)"
            
            // Build fresh dynamic icon based on current active wallpaper
            val iconBitmap = generatePhoneWallpaperIcon(context)

            val intent = Intent(context, PhoneMirrorActivity::class.java).apply {
                action = Intent.ACTION_MAIN
            }

            val updatedShortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel(label)
                .setLongLabel("Phone Mirroring ($deviceName)")
                .setIcon(Icon.createWithBitmap(iconBitmap))
                .setIntent(intent)
                .build()

            shortcutManager.updateShortcuts(listOf(updatedShortcut))
            Log.d(TAG, "Premium mirror home screen shortcut dynamic layout updated successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic shortcut update failed (possibly not supported or not pinned yet)", e)
        }
    }
}
