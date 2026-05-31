package com.example.freeformshell

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.Intent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import android.os.Build
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import android.media.MediaScannerConnection
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory

object ScreenRecordManager {
    private const val TAG = "ScreenRecordManager"
    
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private var currentAudioFile: File? = null
    
    private var mediaRecorder: MediaRecorder? = null
    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    
    // Status callbacks
    interface RecordStatusCallback {
        fun onStart()
        fun onTick(durationSec: Int)
        fun onStop(videoFile: File?, audioFile: File?)
        fun onError(error: String)
    }
    
    private var activeCallback: RecordStatusCallback? = null
    private var tickCount = 0
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            tickCount++
            activeCallback?.onTick(tickCount)
            handler.postDelayed(this, 1000)
        }
    }

    fun isRecordingActive(): Boolean = isRecording
    fun getRecordingDuration(): Int = tickCount
    fun getCurrentVideoFile(): File? = currentRecordingFile
    
    @JvmStatic
    fun getDefaultSaveDirectory(context: Context): File {
        // 1. Try Pictures/Screenshots/FreeformShell
        try {
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val screenshotsDir = File(picturesDir, "Screenshots")
            val freeformDir = File(screenshotsDir, "FreeformShell")
            if (!freeformDir.exists()) {
                freeformDir.mkdirs()
            }
            if (freeformDir.exists() && freeformDir.canWrite()) {
                return freeformDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Pictures/Screenshots/FreeformShell", e)
        }

        // 2. Try DCIM/Screenshots/FreeformShell (common for Samsung, Sony, etc.)
        try {
            val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
            val screenshotsDir = File(dcimDir, "Screenshots")
            val freeformDir = File(screenshotsDir, "FreeformShell")
            if (!freeformDir.exists()) {
                freeformDir.mkdirs()
            }
            if (freeformDir.exists() && freeformDir.canWrite()) {
                return freeformDir
            }
        } catch (e: Exception) {}

        // 3. Try Pictures/FreeformShell
        try {
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val freeformDir = File(picturesDir, "FreeformShell")
            if (!freeformDir.exists()) {
                freeformDir.mkdirs()
            }
            if (freeformDir.exists() && freeformDir.canWrite()) {
                return freeformDir
            }
        } catch (e: Exception) {}

        // 4. Fallback to app's own External Files Dir
        val externalFiles = context.getExternalFilesDir("Captures")
        if (externalFiles != null) {
            return externalFiles
        }

        // 5. Hard fallback
        return File(context.filesDir, "Captures").apply { if (!exists()) mkdirs() }
    }

    @JvmStatic
    fun exportFileToUserStorage(context: Context, localFile: File): File? {
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        val saveUriStr = prefs.getString("pref_screenrecord_save_uri", "") ?: ""
        val customSaveDir = prefs.getString("pref_screenrecord_save_dir", "") ?: ""
        
        var exportSuccessful = false

        // 1. If custom SAF Uri is selected
        if (saveUriStr.isNotEmpty()) {
            try {
                val treeUri = android.net.Uri.parse(saveUriStr)
                val resolver = context.contentResolver
                val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocumentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                
                val newDocUri = android.provider.DocumentsContract.createDocument(
                    resolver,
                    parentDocumentUri,
                    "image/png",
                    localFile.name
                )
                
                if (newDocUri != null) {
                    resolver.openOutputStream(newDocUri)?.use { outStream ->
                        java.io.FileInputStream(localFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    Log.d(TAG, "Successfully exported file via SAF tree to: $newDocUri")
                    exportSuccessful = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export file via SAF Tree Uri using DocumentsContract", e)
            }
        }
        
        // 2. Default public storage using MediaStore (Pictures/Screenshots)
        if (!exportSuccessful) {
            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, localFile.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                
                val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { outStream ->
                        java.io.FileInputStream(localFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }
                    Log.d(TAG, "Successfully exported file via MediaStore: $imageUri")
                    exportSuccessful = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export file via MediaStore to Pictures/Screenshots", e)
            }
        }
        
        // 3. Fallback: try direct file copy to custom directory if writeable
        if (!exportSuccessful && customSaveDir.isNotEmpty()) {
            try {
                val destDir = File(customSaveDir)
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                val destFile = File(destDir, localFile.name)
                java.io.FileOutputStream(destFile).use { outStream ->
                    java.io.FileInputStream(localFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                Log.d(TAG, "Successfully copied file to custom directory: ${destFile.absolutePath}")
                return destFile
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy to custom directory direct path", e)
            }
        }
        
        // If the user set a custom save path but we failed to export to it due to permission/Scoped Storage issues
        if (!exportSuccessful && (saveUriStr.isNotEmpty() || customSaveDir.isNotEmpty())) {
            handler.post {
                Toast.makeText(context, "Folder permission issue: Saved to App Captures", Toast.LENGTH_LONG).show()
            }
        }
        
        return localFile
    }

    @JvmStatic
    fun startRecording(context: Context, displayId: Int, callback: RecordStatusCallback) {
        if (isRecording) {
            callback.onError("Recording is already active")
            return
        }
        
        if (displayId == 0) {
            if (PhoneMirrorManager.isMirroring) {
                Log.d(TAG, "Screen mirror active on Display 0, pausing mirror stream to prevent screenrecord encoder conflicts.")
                PhoneMirrorManager.isPausedForRecording = true
                PhoneMirrorManager.pauseMirroring()
            }
        }
        
        appContext = context.applicationContext
        
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        val mode = prefs.getString("pref_capture_id_mode", "auto") ?: "auto"
        val overrideId = prefs.getString("pref_physical_display_id_override", "") ?: ""
        val preset = prefs.getString("pref_capture_preset", "high") ?: "high"
        
        val physicalId = getPhysicalDisplayId(context, displayId)
        val isVirtual = displayId > 0 && physicalId == null
        
        val screenrecordId = when (mode) {
            "logical" -> displayId.toString()
            "physical" -> {
                if (overrideId.isNotEmpty()) overrideId.trim()
                else physicalId?.removePrefix("local:") ?: displayId.toString()
            }
            else -> { // "auto"
                if (overrideId.isNotEmpty()) overrideId.trim()
                else physicalId?.removePrefix("local:") ?: displayId.toString()
            }
        }
        
        val resPreset = prefs.getString("pref_screenrecord_resolution", "Native") ?: "Native"
        val bitrateMbps = prefs.getInt("pref_screenrecord_bitrate", 8)
        val recordMic = prefs.getBoolean("pref_screenrecord_mic", false)
        val customSaveDir = prefs.getString("pref_screenrecord_save_dir", "") ?: ""
        
        // Resolve target folder
        val saveDir = if (customSaveDir.isNotEmpty()) {
            File(customSaveDir)
        } else {
            getDefaultSaveDirectory(context)
        }
        
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(saveDir, "record_${timestamp}_display${displayId}.mp4")
        val audioFile = File(saveDir, "voiceover_${timestamp}_display${displayId}.m4a")
        
        currentRecordingFile = videoFile
        currentAudioFile = if (recordMic) audioFile else null
        activeCallback = callback
        tickCount = 0
        
        Thread {
            try {
                // Defensive: clean any active screenrecords first
                ShellExecutor.executeCommand("pkill -2 screenrecord")
                Thread.sleep(100)
                
                // Construct size string if not Native
                val sizeArg = when (if (preset == "advanced") resPreset else preset) {
                    "medium" -> " --size 1920x1080"
                    "1080p" -> " --size 1920x1080"
                    "low_size" -> " --size 1280x720"
                    "720p" -> " --size 1280x720"
                    "480p" -> " --size 854x480"
                    else -> "" // Native
                }
                
                val finalBitrateMbps = when (preset) {
                    "high" -> 12
                    "medium" -> 6
                    "low_size" -> 2
                    else -> bitrateMbps
                }
                val bitRateArg = " --bit-rate ${finalBitrateMbps * 1000000}"
                
                // Android 14 wants the numeric ID without 'local:' prefix for --display-id
                val cleanPhysicalId = screenrecordId.removePrefix("local:")
                
                val useArg = (mode == "logical" && displayId != 0) || 
                             (mode == "physical" && cleanPhysicalId.isNotEmpty()) ||
                             (mode == "auto" && displayId != 0)
                
                val displayArg = if (useArg) " --display-id $cleanPhysicalId" else ""
                val outputFilePath = videoFile.absolutePath
                
                // Prepend 'nice -n -10' to elevate the Linux thread scheduling priority
                // This guarantees dedicated CPU core cycles for real-time video encoding, completely eliminating lag!
                val command = "nice -n -10 screenrecord$sizeArg$bitRateArg$displayArg \"$outputFilePath\""
                
                Log.i(TAG, "Starting screenrecord command [Preset=$preset, Mode=$mode]: $command")
                
                // Execute screenrecord directly via sh -c to ensure environment variables are correct
                // and it doesn't get blocked by Shizuku's process wrapper peculiarities for long-running binaries
                val fullCommand = "sh -c '$command'"
                
                // Initialize Microphone capture if checked
                if (recordMic) {
                    handler.post {
                        try {
                            mediaRecorder = MediaRecorder().apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFile(audioFile.absolutePath)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                setAudioEncodingBitRate(128000)
                                setAudioSamplingRate(44100)
                                prepare()
                                start()
                            }
                            Log.i(TAG, "MediaRecorder started for mic voiceover successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start MediaRecorder for microphone recording", e)
                        }
                    }
                }
                
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                
                handler.post {
                    if (isVirtual) {
                        Toast.makeText(context, "Recording virtual display from Primary Screen fallback", Toast.LENGTH_LONG).show()
                    }
                    callback.onStart()
                    handler.post(tickRunnable)
                }
                
                // Start screenrecord asynchronously in Shizuku persistent shell block
                val result = ShellExecutor.executeCommandWithResult(fullCommand)
                Log.i(TAG, "Screenrecord exited with result code ${result.third}. Output: ${result.first}, Error: ${result.second}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in screenrecord thread execution", e)
                handler.post {
                    isRecording = false
                    callback.onError("Shell execution error: ${e.message}")
                }
            }
        }.start()
    }
    
    @JvmStatic
    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        handler.removeCallbacks(tickRunnable)
        
        Thread {
            try {
                // Signal 2 (SIGINT) stops screenrecord cleanly and flushes MP4 frames!
                val stopRes = ShellExecutor.executeCommandWithResult("pkill -2 screenrecord")
                Log.d(TAG, "pkill screenrecord triggered: $stopRes")
                
                // Stop mic recorder if active
                handler.post {
                    try {
                        mediaRecorder?.apply {
                            stop()
                            release()
                        }
                        mediaRecorder = null
                        Log.d(TAG, "MediaRecorder mic audio stopped cleanly")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed stopping mic recorder", e)
                    }
                    
                    val recordFile = currentRecordingFile
                    if (recordFile != null && recordFile.exists() && recordFile.length() > 0) {
                        appContext?.let { ctx ->
                            sendCaptureNotification(ctx, recordFile, true)
                        }
                    }
                    
                    activeCallback?.onStop(currentRecordingFile, currentAudioFile)
                    activeCallback = null
                    
                    if (PhoneMirrorManager.isPausedForRecording) {
                        Log.d(TAG, "Resuming phone mirror stream after active screen recording session completed.")
                        PhoneMirrorManager.isPausedForRecording = false
                        PhoneMirrorManager.triggerManualReconnect()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleanly stopping recorder", e)
                handler.post {
                    activeCallback?.onError("Failed stopping screenrecord process cleanly: ${e.message}")
                    activeCallback = null
                }
            }
        }.start()
    }
    
    @JvmStatic
    fun takeScreenshot(context: Context, displayId: Int, onComplete: (File?) -> Unit) {
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        val mode = prefs.getString("pref_capture_id_mode", "auto") ?: "auto"
        val overrideId = prefs.getString("pref_physical_display_id_override", "") ?: ""
        
        val physicalId = getPhysicalDisplayId(context, displayId)
        val isVirtual = displayId > 0 && physicalId == null
        
        // Resolve the precise display ID to use for screencap -d based on selected mode
        val screencapId = when (mode) {
            "logical" -> displayId.toString()
            "physical" -> {
                if (overrideId.isNotEmpty()) overrideId.trim()
                else physicalId?.removePrefix("local:") ?: displayId.toString()
            }
            else -> { // "auto"
                if (overrideId.isNotEmpty()) overrideId.trim()
                else physicalId?.removePrefix("local:") ?: displayId.toString()
            }
        }
        
        val customSaveDir = prefs.getString("pref_screenrecord_save_dir", "") ?: ""
        
        val saveDir = if (customSaveDir.isNotEmpty()) {
            File(customSaveDir)
        } else {
            getDefaultSaveDirectory(context)
        }
        
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val screenshotFile = File(saveDir, "screenshot_${timestamp}_display${displayId}.png")
        
        Thread {
            try {
                // If logical mode and displayId is 0, or physical mode has empty, or auto mode resolves to 0
                val useArg = (mode == "logical" && displayId != 0) || 
                             (mode == "physical" && screencapId.isNotEmpty()) ||
                             (mode == "auto" && displayId != 0)
                
                val displayArg = if (useArg) " -d $screencapId" else ""
                val command = "screencap$displayArg \"${screenshotFile.absolutePath}\""
                Log.i(TAG, "Running screencap command [Mode=$mode]: $command")
                val result = ShellExecutor.executeCommandWithResult(command)
                
                handler.post {
                    if (screenshotFile.exists() && screenshotFile.length() > 0) {
                        if (isVirtual && mode == "auto") {
                            Toast.makeText(context, "Virtual display captured from Primary Screen fallback", Toast.LENGTH_SHORT).show()
                        }
                        sendCaptureNotification(context, screenshotFile, false)
                        onComplete(screenshotFile)
                    } else {
                        Log.e(TAG, "Screencap failed [Mode=$mode]. Output: ${result.first}, Error: ${result.second}")
                        // Fallback only if mode is "auto" to prevent silent capturing of phone display in manual physical/logical modes!
                        if (mode == "auto" && displayId != 0 && screencapId != displayId.toString()) {
                            takeScreenshotLegacyFallback(context, displayId, screenshotFile, onComplete)
                        } else {
                            onComplete(null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing screencap", e)
                handler.post { onComplete(null) }
            }
        }.start()
    }

    @JvmStatic
    fun takeScreenshotToTempFile(context: Context, displayId: Int, onComplete: (File?) -> Unit) {
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        val mode = prefs.getString("pref_capture_id_mode", "auto") ?: "auto"
        val overrideId = prefs.getString("pref_physical_display_id_override", "") ?: ""
        
        val physicalId = getPhysicalDisplayId(context, displayId)
        val isVirtual = displayId > 0 && physicalId == null
        
        val screencapId = when (mode) {
            "logical" -> displayId.toString()
            "physical" -> {
                if (overrideId.isNotEmpty()) overrideId.trim()
                else physicalId?.removePrefix("local:") ?: displayId.toString()
            }
            else -> { // "auto"
                if (overrideId.isNotEmpty()) overrideId.trim()
                else physicalId?.removePrefix("local:") ?: displayId.toString()
            }
        }
        
        // IMPORTANT: screencap runs as the ADB shell user which CANNOT write to cacheDir (/data/data/.../cache).
        // Use externalFilesDir which is accessible to both our app and the shell user, yet not indexed by the gallery.
        val tempDir = context.getExternalFilesDir(null) ?: context.filesDir
        if (!tempDir.exists()) tempDir.mkdirs()
        val tempFile = File(tempDir, "temp_screencap_${System.currentTimeMillis()}.png")
        
        Thread {
            try {
                val useArg = (mode == "logical" && displayId != 0) || 
                             (mode == "physical" && screencapId.isNotEmpty()) ||
                             (mode == "auto" && displayId != 0)
                
                val displayArg = if (useArg) " -d $screencapId" else ""
                val command = "screencap$displayArg \"${tempFile.absolutePath}\""
                Log.i(TAG, "Running screencap command to temp file [Mode=$mode]: $command")
                val result = ShellExecutor.executeCommandWithResult(command)
                
                handler.post {
                    if (tempFile.exists() && tempFile.length() > 0) {
                        onComplete(tempFile)
                    } else {
                        Log.e(TAG, "Temp screencap failed [Mode=$mode]. Output: ${result.first}, Error: ${result.second}")
                        if (mode == "auto" && displayId != 0 && screencapId != displayId.toString()) {
                            Thread {
                                try {
                                    val fallbackCommand = "screencap -d $displayId \"${tempFile.absolutePath}\""
                                    ShellExecutor.executeCommand(fallbackCommand)
                                    handler.post {
                                        if (tempFile.exists() && tempFile.length() > 0) {
                                            onComplete(tempFile)
                                        } else {
                                            onComplete(null)
                                        }
                                    }
                                } catch (e: Exception) {
                                    handler.post { onComplete(null) }
                                }
                            }.start()
                        } else {
                            onComplete(null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing temp screencap", e)
                handler.post { onComplete(null) }
            }
        }.start()
    }

    private fun takeScreenshotLegacyFallback(context: Context, displayId: Int, file: File, onComplete: (File?) -> Unit) {
        Thread {
            try {
                val command = "screencap -d $displayId \"${file.absolutePath}\""
                ShellExecutor.executeCommand(command)
                handler.post {
                    if (file.exists() && file.length() > 0) {
                        sendCaptureNotification(context, file, false)
                        onComplete(file)
                    } else {
                        onComplete(null)
                    }
                }
            } catch (e: Exception) {
                handler.post { onComplete(null) }
            }
        }.start()
    }

    @JvmStatic
    fun sendCaptureNotification(context: Context, file: File, isVideo: Boolean) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(if (isVideo) "video/mp4" else "image/png")
        ) { path, uri ->
            Log.d(TAG, "Media scan complete: $path -> $uri")
            
            val authority = "${context.packageName}.fileprovider"
            val fallbackUri = try {
                FileProvider.getUriForFile(context, authority, file)
            } catch (e: Exception) {
                null
            }
            
            val fileUri = uri ?: fallbackUri ?: return@scanFile
            
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, if (isVideo) "video/mp4" else "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = android.content.ClipData.newRawUri(null, fileUri)
            }
            val viewPendingIntent = PendingIntent.getActivity(
                context,
                file.hashCode(),
                viewIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val openLocIntent = Intent(context, Class.forName("com.example.freeformshell.MainActivity")).apply {
                action = "ACTION_OPEN_CAPTURE_TAB"
                putExtra("open_capture_tab", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openLocPendingIntent = PendingIntent.getActivity(
                context,
                file.hashCode() + 1,
                openLocIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (isVideo) "video/mp4" else "image/png"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri(null, fileUri)
            }
            val sharePendingIntent = PendingIntent.getActivity(
                context,
                file.hashCode() + 2,
                Intent.createChooser(shareIntent, "Share Capture"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val title = if (isVideo) "Screen Recording Saved" else "Screenshot Saved"
            val text = "Captured ${file.name.substringAfterLast("_")}"

            var previewBitmap: Bitmap? = null
            try {
                if (isVideo) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        previewBitmap = ThumbnailUtils.createVideoThumbnail(file, Size(1024, 768), null)
                    } else {
                        @Suppress("DEPRECATION")
                        previewBitmap = ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
                    }
                } else {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
                    options.inJustDecodeBounds = false
                    previewBitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification thumbnail", e)
            }
            
            val channelId = "freeform_capture_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Screen Captures & Recordings",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when screen recording or screenshot is completed"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(if (isVideo) android.R.drawable.ic_menu_slideshow else android.R.drawable.ic_menu_camera)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(viewPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .addAction(if (isVideo) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_view, if (isVideo) "Play" else "View", viewPendingIntent)
                .addAction(android.R.drawable.ic_menu_search, "Location", openLocPendingIntent)
                .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)

            if (previewBitmap != null) {
                builder.setLargeIcon(previewBitmap)
                builder.setStyle(NotificationCompat.BigPictureStyle()
                    .bigPicture(previewBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(text))
            }
                
            notificationManager.notify(file.hashCode(), builder.build())
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @JvmStatic
    fun getPhysicalDisplayId(context: Context, displayId: Int): String? {
        if (displayId == 0) return null 
        
        // Prioritize User-Specified Physical Display ID Override!
        val prefs = context.getSharedPreferences("freeform_capture_settings", Context.MODE_PRIVATE)
        val overrideId = prefs.getString("pref_physical_display_id_override", "") ?: ""
        if (overrideId.isNotEmpty()) {
            val cleanOverride = overrideId.trim()
            val resolvedId = if (cleanOverride.startsWith("local:")) cleanOverride else "local:$cleanOverride"
            Log.i(TAG, "[OVERRIDE] Bypassing automated mapping and using custom physical display ID override: $resolvedId")
            return resolvedId
        }
        
        Log.i(TAG, "Resolving physical display ID for logical displayId=$displayId")
        
        // Layer 0: Pure Native Android Frame Display UniqueID Reflection (Highly robust, zero shell delay!)
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            val display = dm?.getDisplay(displayId)
            if (display != null) {
                // Display.getUniqueId() returns the exact physical display address string (e.g. "local:4621070409437748996")
                val getUniqueIdMethod = display.javaClass.getMethod("getUniqueId")
                val rawUniqueId = getUniqueIdMethod.invoke(display) as? String
                if (!rawUniqueId.isNullOrEmpty()) {
                    Log.i(TAG, "[Layer 0-Native] Successfully resolved logical displayId=$displayId to native uniqueId=$rawUniqueId")
                    return rawUniqueId
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Native Display.getUniqueId() reflection failed", e)
        }
        
        try {
            val displayDump = ShellExecutor.exec("dumpsys display")
            if (displayDump.trim().isEmpty()) {
                Log.e(TAG, "dumpsys display returned completely empty output! Shizuku might be unauthorized or inactive.")
            }
            
            
            // Layer 0: Direct Regex Block Matching (Matches modern dumpsys display DisplayInfo/DisplayDevice layouts)
            val layer0InfoRegex = """DisplayInfo\{[^}]*?\b(?:mDisplayId|displayId|logicalId)\s*[=:\s]\s*$displayId\b[^}]*?\b(?:mUniqueId|uniqueId)\s*[=:\s]\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
            val layer0InfoMatch = layer0InfoRegex.find(displayDump)
            if (layer0InfoMatch != null) {
                val physId = layer0InfoMatch.groupValues[1]
                Log.i(TAG, "[Layer 0-Info] Resolved logical displayId=$displayId to physicalId=$physId")
                return physId
            }
            
            val layer0DeviceRegex = """DisplayDevice\{[^}]*?\b(?:mDisplayId|displayId|logicalId)\s*[=:\s]\s*$displayId\b[^}]*?\b(?:mUniqueId|uniqueId)\s*[=:\s]\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
            val layer0DeviceMatch = layer0DeviceRegex.find(displayDump)
            if (layer0DeviceMatch != null) {
                val physId = layer0DeviceMatch.groupValues[1]
                Log.i(TAG, "[Layer 0-Device] Resolved logical displayId=$displayId to physicalId=$physId")
                return physId
            }

            // Layer 1: Block-Based dumpsys display Parsing
            // We split the display dump into distinct device/display sections
            val blocks = displayDump.split(Regex("DisplayDevice\\{|Display Device:|Display\\s+\\d+:"))
            val targetIdRegex = """\b(?:mDisplayId|displayId|logicalId|mCurrentLayerStack|layerStack)\s*[=:\s]\s*$displayId\b""".toRegex()
            
            for (block in blocks) {
                if (targetIdRegex.containsMatchIn(block)) {
                    // This block matches our logical display ID!
                    // Extract any "local:<id>" or "local:0x..." string
                    val localMatch = """local:(\d+)""".toRegex().find(block)
                    if (localMatch != null) {
                        val physId = localMatch.groupValues[1]
                        Log.i(TAG, "[Layer 1] Resolved logical displayId=$displayId to physicalId=$physId")
                        return "local:$physId"
                    }
                    
                    // Fallback search for uniqueId or mUniqueId inside the same block
                    val uniqueMatch = """uniqueId="?([^"\s,]+)"?""".toRegex().find(block)
                    if (uniqueMatch != null) {
                        val rawUnique = uniqueMatch.groupValues[1]
                        if (rawUnique.startsWith("local:")) {
                            Log.i(TAG, "[Layer 1-Unique] Resolved logical displayId=$displayId to physicalId=$rawUnique")
                            return rawUnique
                        }
                    }
                }
            }
            
            // Layer 2: Direct dumpsys SurfaceFlinger extraction
            val sfDump = ShellExecutor.exec("dumpsys SurfaceFlinger --display-id")
            // SurfaceFlinger output formats:
            // "Display 21691504607621632 (HWC display 0): port=0 displayName="Panel""
            // "Display 1 (HWC display 0): ID=4621070409437748996, type=Internal..."
            val sfIds = mutableListOf<String>()
            val sfLines = sfDump.split("\n")
            for (line in sfLines) {
                // Find long 64-bit IDs right after 'Display '
                val match64 = """Display\s+(\d{10,})\b""".toRegex().find(line)
                if (match64 != null) {
                    sfIds.add(match64.groupValues[1])
                    continue
                }
                // Try extracting 'ID=...' format
                val matchId = """ID=(\d+)\b""".toRegex().find(line)
                if (matchId != null) {
                    sfIds.add(matchId.groupValues[1])
                }
            }
            Log.d(TAG, "[Layer 2] Discovered physical display IDs in SurfaceFlinger: $sfIds")
            
            // Layer 3: Eliminate-Primary Heuristic (2-display setups)
            // If displayId > 0 (external screen) and we have exactly 2 physical displays active
            if (displayId > 0 && sfIds.size == 2) {
                // Find primary display ID by checking which one is port 0 or HWC display 0
                var primaryPhysId: String? = null
                for (line in sfLines) {
                    if (line.contains("HWC display 0") || line.contains("port=0") || line.contains("displayName=\"Panel\"") || line.contains("type=Internal")) {
                        for (id in sfIds) {
                            if (line.contains(id)) {
                                primaryPhysId = id
                                break
                            }
                        }
                    }
                }
                
                if (primaryPhysId != null) {
                    val externalPhysId = sfIds.firstOrNull { it != primaryPhysId }
                    if (externalPhysId != null) {
                        Log.i(TAG, "[Layer 3] Eliminate-primary resolved displayId=$displayId to physicalId=$externalPhysId (primary=$primaryPhysId)")
                        return "local:$externalPhysId"
                    }
                } else {
                    // Fallback to second ID in the list if we can't determine primary
                    val secondId = sfIds.getOrNull(1)
                    if (secondId != null) {
                        Log.i(TAG, "[Layer 3-Fallback] Resolved displayId=$displayId to second physical ID: $secondId")
                        return "local:$secondId"
                    }
                }
            }
            
            // Layer 4: Global dumpsys display fallbacks
            // Check for modern DisplayStates section or mDisplayId mapping globally
            val deviceMatch = """DisplayDevice\{.*?mDisplayId=($displayId),.*?mUniqueId=local:(\d+)""".toRegex(RegexOption.DOT_MATCHES_ALL).find(displayDump)
            if (deviceMatch != null) return "local:" + deviceMatch.groupValues[2]
            
            val displayMatch = """Display\s+$displayId:.*?mUniqueId=local:(\d+)""".toRegex(RegexOption.DOT_MATCHES_ALL).find(displayDump)
            if (displayMatch != null) return "local:" + displayMatch.groupValues[1]
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in 4-layer physical display resolver", e)
        }
        
        Log.w(TAG, "[Layer 4-Legacy] Failed to resolve displayId=$displayId to physical ID, falling back to logical displayId string")
        return null
    }
}
