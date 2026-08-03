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
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap

object WorkspaceShortcutHelper {
    private const val TAG = "WorkspaceShortcutHelper"

    /**
     * Generates a stunning, pixel-perfect 512x512 bitmap icon representing a desktop workspace.
     * Renders a mockup monitor or phone bezel, retrieves the system wallpaper as screen background,
     * and layers realistic semi-translucent mini-app windows displaying their respective app icons.
     */
    fun generateWorkspaceIcon(context: Context, group: WorkspaceGroup): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val isPortrait = group.displayId == 0

        // 1. Outer Bezel Dimensions
        val border = 44f
        val r = RectF(border, border, size - border, size - border)
        val cornerRadius = 40f

        // Frame Shadow / Ambient Glow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            setShadowLayer(20f, 0f, 10f, Color.BLACK)
        }
        canvas.drawRoundRect(r, cornerRadius, cornerRadius, shadowPaint)

        // Sleek Metallic Dark Frame Bezel
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1A22")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(r, cornerRadius, cornerRadius, framePaint)

        // 2. Bezel & Screen clipping setup
        val bezelThickness = 16f
        val screenRect = RectF(r.left + bezelThickness, r.top + bezelThickness, r.right - bezelThickness, r.bottom - bezelThickness)
        val screenCornerRadius = cornerRadius - bezelThickness * 0.5f

        canvas.save()
        val screenPath = Path().apply {
            addRoundRect(screenRect, screenCornerRadius, screenCornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(screenPath)

        // 3. Render User System Wallpaper inside the Visualizer
        val wallpaperManager = WallpaperManager.getInstance(context)
        val wallpaperDrawable = try {
            wallpaperManager.drawable
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve system wallpaper for shortcut icon, using fallback", e)
            null
        }

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
            // Radiant fallback gradient
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, screenRect.top, 0f, screenRect.bottom,
                    Color.parseColor("#120E2E"), Color.parseColor("#260E45"), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(screenRect, fallbackPaint)
        }

        // Draw a dark overlay over the wallpaper so app blocks pop out beautifully
        val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 18, 17, 24)
            style = Paint.Style.FILL
        }
        canvas.drawRect(screenRect, scrimPaint)

        // 4. Render Miniature Application Windows with App Icons!
        val screenW = if (isPortrait) 1080f else 1920f
        val screenH = if (isPortrait) 2400f else 1080f
        val scaleX = screenRect.width() / screenW
        val scaleY = screenRect.height() / screenH

        val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 32, 30, 48) // Frosted dark glass window body
            style = Paint.Style.FILL
        }

        val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 110, 85, 235) // Deep violet border highlight
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val pm = context.packageManager

        group.apps.forEach { app ->
            val winL = screenRect.left + app.bounds.left * scaleX
            val winT = screenRect.top + app.bounds.top * scaleY
            val winR = screenRect.left + app.bounds.right * scaleX
            val winB = screenRect.top + app.bounds.bottom * scaleY

            val cardRect = RectF(winL, winT, winR, winB)
            val cardR = 10f

            // Draw window card
            canvas.drawRoundRect(cardRect, cardR, cardR, cardFillPaint)
            canvas.drawRoundRect(cardRect, cardR, cardR, cardStrokePaint)

            // Draw small title bar accent for each card
            val titleH = Math.min(22f, cardRect.height() * 0.2f)
            val titleBarRect = RectF(cardRect.left, cardRect.top, cardRect.right, cardRect.top + titleH)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(100, 110, 85, 235)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(titleBarRect, cardR, cardR, titlePaint)
            // Cover bottom round corners of the title accent
            canvas.drawRect(cardRect.left, cardRect.top + titleH / 2f, cardRect.right, cardRect.top + titleH, titlePaint)

            // Try to draw the actual application launcher icon inside the miniature block
            var drewIcon = false
            val maxIconSize = Math.min(cardRect.width() * 0.5f, cardRect.height() * 0.5f)
            if (maxIconSize >= 20f) {
                try {
                    val appIcon = pm.getApplicationIcon(app.packageName)
                    val iconSize = maxIconSize.toInt()
                    val bmp = appIcon.toBitmap(width = iconSize, height = iconSize, config = Bitmap.Config.ARGB_8888)
                    if (bmp != null) {
                        val iconL = cardRect.centerX() - iconSize / 2f
                        val iconT = cardRect.centerY() - iconSize / 2f + titleH / 2f
                        canvas.drawBitmap(bmp, iconL, iconT, null)
                        drewIcon = true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Could not fetch app icon for miniature visualization block: ${app.packageName}")
                }
            }

            if (!drewIcon) {
                // Fallback: draw first letter of package label
                val letter = try {
                    val label = pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
                    if (label.isNotEmpty()) label.substring(0, 1).uppercase() else "?"
                } catch (e: Exception) {
                    app.packageName.substringAfterLast(".").take(1).uppercase()
                }
                
                val fontMetrics = textPaint.fontMetrics
                val baseline = cardRect.centerY() + titleH / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(letter, cardRect.centerX(), baseline, textPaint)
            }
        }

        canvas.restore()

        // 5. Glossy glass shine highlight overlay
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                screenRect.left, screenRect.top, screenRect.right, screenRect.bottom,
                intArrayOf(Color.argb(45, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(10, 255, 255, 255)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.clipPath(screenPath)
        canvas.drawRect(screenRect, shinePaint)
        canvas.restore()

        // 6. Bezel Outline highlights
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#343242")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(screenRect, screenCornerRadius, screenCornerRadius, strokePaint)

        return bitmap
    }

    /**
     * Programmatically pins a custom-drawn Workspace dynamic shortcut on the home screen.
     */
    fun pinWorkspaceShortcut(context: Context, group: WorkspaceGroup) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, "Shortcuts require Android 8.0+ (Oreo)", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
            if (!shortcutManager.isRequestPinShortcutSupported()) {
                Toast.makeText(context, "Your launcher does not support home screen shortcut pinning.", Toast.LENGTH_LONG).show()
                return
            }

            val pm = context.packageManager
            val appNames = group.apps.map {
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString()
                } catch (e: Exception) {
                    it.packageName.substringAfterLast(".")
                }
            }

            val dynamicLabel = if (appNames.isNotEmpty()) {
                val base = appNames.joinToString(", ")
                if (base.length > 25) base.substring(0, 22) + "..." else base
            } else {
                "Workspace"
            }

            val shortcutId = "workspace_shortcut_${group.timestamp}"
            val iconBitmap = generateWorkspaceIcon(context, group)

            // Setup Intent pointing to the translucent zero-UI WorkspaceShortcutActivity helper
            val intent = Intent(context, WorkspaceShortcutActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra("EXTRA_GROUP_JSON", WorkspaceManager.groupToJson(group).toString())
                putExtra("EXTRA_GROUP_TIMESTAMP", group.timestamp)
            }

            val shortcutInfo = ShortcutInfo.Builder(context, shortcutId)
                .setShortLabel(dynamicLabel)
                .setLongLabel("Relaunch Workspace ($dynamicLabel)")
                .setIcon(Icon.createWithBitmap(iconBitmap))
                .setIntent(intent)
                .build()

            shortcutManager.requestPinShortcut(shortcutInfo, null)
            Toast.makeText(context, "Pinning shortcut: '$dynamicLabel'...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Dynamic shortcut requested: $shortcutId ($dynamicLabel)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed pinning workspace shortcut", e)
            Toast.makeText(context, "Failed to pin shortcut: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
