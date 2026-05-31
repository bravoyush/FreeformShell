package com.example.freeformshell

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import rikka.shizuku.Shizuku
import java.io.InputStream

object PhoneMirrorManager {
    private const val TAG = "PhoneMirrorManager"

    @Volatile
    var isMirroring = false
        private set

    @Volatile
    var isPausedForRecording = false

    private var appContext: Context? = null
    private var activeSurface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private var mirrorProcess: Process? = null
    private var activeDecoder: MediaCodec? = null
    private var streamThread: Thread? = null
    private var drainingThread: Thread? = null

    // A list of callbacks to notify state changes to UI
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val reconnectRunnable = Runnable {
        val ctx = appContext
        val surf = activeSurface
        if (ctx != null && surf != null && !isPausedForRecording) {
            Log.d(TAG, "Attempting auto-reconnection of phone mirror stream...")
            cleanupMirroringResources()
            startMirroringSequence(ctx, surf, surfaceWidth, surfaceHeight)
        }
    }

    private fun scheduleReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, 2000)
    }

    fun triggerManualReconnect() {
        Log.d(TAG, "Manual reconnect triggered.")
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.post(reconnectRunnable)
    }

    fun registerCallback(cb: (Boolean) -> Unit) {
        synchronized(callbacks) {
            callbacks.add(cb)
            // Deliver initial state on main thread
            val current = isMirroring
            mainHandler.post { cb(current) }
        }
    }

    fun unregisterCallback(cb: (Boolean) -> Unit) {
        synchronized(callbacks) {
            callbacks.remove(cb)
        }
    }

    private fun notifyStateChanged() {
        val state = isMirroring
        val snapshot: List<(Boolean) -> Unit>
        synchronized(callbacks) {
            snapshot = callbacks.toList()
        }
        mainHandler.post {
            snapshot.forEach { it(state) }
        }
    }

    /**
     * Start the mirroring stream directly using a local screenrecord and MediaCodec pipeline.
     */
    fun startMirroring(context: Context, surface: Surface, containerWidth: Int, containerHeight: Int) {
        if (isMirroring) {
            Log.w(TAG, "Mirroring is already running, stopping existing stream first.")
            stopMirroring()
        }

        appContext = context.applicationContext
        activeSurface = surface
        surfaceWidth = containerWidth
        surfaceHeight = containerHeight
        isPausedForRecording = false

        mainHandler.removeCallbacks(reconnectRunnable)
        startMirroringSequence(context, surface, containerWidth, containerHeight)
    }

    private fun startMirroringSequence(context: Context, surface: Surface, containerWidth: Int, containerHeight: Int) {
        Log.d(TAG, "Starting native Shizuku + MediaCodec screen mirror stream.")

        try {
            // Get physical dimensions of display 0 (Internal Screen) explicitly
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val primaryDisplay = dm.getDisplay(0)
            val realMetrics = android.util.DisplayMetrics()
            primaryDisplay?.getRealMetrics(realMetrics) ?: run {
                val resMetrics = context.resources.displayMetrics
                realMetrics.widthPixels = resMetrics.widthPixels
                realMetrics.heightPixels = resMetrics.heightPixels
                realMetrics.densityDpi = resMetrics.densityDpi
            }
            val displayWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else 1080
            val displayHeight = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else 2400

            Log.d(TAG, "Configuring stream: original=${displayWidth}x$displayHeight")

            // Initialize MediaCodec decoder for H.264 / AVC
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, displayWidth, displayHeight)
            // Request low latency if supported
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            
            val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            decoder.configure(format, surface, null, 0)
            decoder.start()
            activeDecoder = decoder

            isMirroring = true
            notifyStateChanged()

            // Spawn the privileged screenrecord process and parse stream
            startStreamThread()

            // Spawn the decoder draining thread
            startDrainingThread()

            Log.d(TAG, "Native Screen Mirroring successfully initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting native mirror pipeline", e)
            stopMirroring()
        }
    }

    private fun startStreamThread() {
        streamThread = Thread {
            var inputStream: InputStream? = null
            try {
                if (!Shizuku.pingBinder()) {
                    Log.e(TAG, "Shizuku not running, aborting screen stream.")
                    stopMirroring()
                    return@Thread
                }

                val command = "nice -n -10 screenrecord --output-format=h264 --bit-rate 4000000 -"
                Log.d(TAG, "Executing: $command")

                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val newProcessMethod = shizukuClass.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }

                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", command),
                    null,
                    null
                ) as Process
                mirrorProcess = process
                inputStream = process.inputStream

                val streamBuffer = ByteArray(2 * 1024 * 1024) // 2MB read/assembly buffer
                var bufferLength = 0

                while (isMirroring) {
                    val spaceLeft = streamBuffer.size - bufferLength
                    if (spaceLeft <= 0) {
                        Log.w(TAG, "Stream buffer overflow, resetting to prevent lockup.")
                        bufferLength = 0
                        continue
                    }

                    val bytesRead = inputStream.read(streamBuffer, bufferLength, spaceLeft)
                    if (bytesRead < 0) {
                        Log.d(TAG, "Process stream reached EOF.")
                        break
                    }
                    if (bytesRead == 0) continue

                    bufferLength += bytesRead

                    // Scan buffer for Annex B NAL unit start codes: 0x00000001 (4 bytes) or 0x000001 (3 bytes)
                    var i = 0
                    var lastStartCodePos = -1

                    while (i <= bufferLength - 4) {
                        if (streamBuffer[i] == 0.toByte() &&
                            streamBuffer[i + 1] == 0.toByte() &&
                            streamBuffer[i + 2] == 0.toByte() &&
                            streamBuffer[i + 3] == 1.toByte()
                        ) {
                            if (lastStartCodePos != -1) {
                                val nalSize = i - lastStartCodePos
                                val nalUnit = ByteArray(nalSize)
                                System.arraycopy(streamBuffer, lastStartCodePos, nalUnit, 0, nalSize)
                                queueNalUnitToCodec(nalUnit)
                            }
                            lastStartCodePos = i
                            i += 4
                        } else if (streamBuffer[i] == 0.toByte() &&
                            streamBuffer[i + 1] == 0.toByte() &&
                            streamBuffer[i + 2] == 1.toByte()
                        ) {
                            if (lastStartCodePos != -1) {
                                val nalSize = i - lastStartCodePos
                                val nalUnit = ByteArray(nalSize)
                                System.arraycopy(streamBuffer, lastStartCodePos, nalUnit, 0, nalSize)
                                queueNalUnitToCodec(nalUnit)
                            }
                            lastStartCodePos = i
                            i += 3
                        } else {
                            i++
                        }
                    }

                    // Shift remaining unparsed bytes to front of the buffer
                    if (lastStartCodePos != -1) {
                        val remainingLength = bufferLength - lastStartCodePos
                        System.arraycopy(streamBuffer, lastStartCodePos, streamBuffer, 0, remainingLength)
                        bufferLength = remainingLength
                    }
                }
            } catch (e: Exception) {
                if (isMirroring) {
                    Log.e(TAG, "Exception in screenrecord reading thread", e)
                }
            } finally {
                try {
                    inputStream?.close()
                } catch (e: Exception) {}
                
                if (isMirroring) {
                    Log.d(TAG, "Stream disconnected unexpectedly, triggering reconnect.")
                    isMirroring = false
                    notifyStateChanged()
                    
                    if (!isPausedForRecording) {
                        scheduleReconnect()
                    }
                }
            }
        }.apply {
            name = "PhoneMirror-StreamReader"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun queueNalUnitToCodec(nalUnit: ByteArray) {
        val codec = activeDecoder ?: return
        try {
            val inputBufferIndex = codec.dequeueInputBuffer(5000) // 5ms timeout
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(nalUnit)
                val presentationTimeUs = System.nanoTime() / 1000
                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    nalUnit.size,
                    presentationTimeUs,
                    0
                )
            }
        } catch (e: Exception) {
            if (isMirroring) {
                Log.e(TAG, "Error queuing NAL unit to MediaCodec", e)
            }
        }
    }

    private fun startDrainingThread() {
        drainingThread = Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isMirroring) {
                val codec = activeDecoder ?: break
                try {
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 5000) // 5ms timeout
                    if (outputBufferIndex >= 0) {
                        // Render directly to the surface
                        codec.releaseOutputBuffer(outputBufferIndex, true)
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "MediaCodec output format changed: ${codec.outputFormat}")
                    }
                } catch (e: Exception) {
                    if (isMirroring) {
                        Log.e(TAG, "Error dequeuing output from MediaCodec", e)
                    }
                    break
                }
            }
        }.apply {
            name = "PhoneMirror-DecoderDrainer"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun cleanupMirroringResources() {
        // Terminate screenrecord process
        try {
            mirrorProcess?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying screenrecord process", e)
        }
        mirrorProcess = null

        // Stop and release decoder
        try {
            activeDecoder?.stop()
        } catch (e: Exception) {}
        try {
            activeDecoder?.release()
        } catch (e: Exception) {}
        activeDecoder = null

        // Join threads safely
        try {
            streamThread?.interrupt()
        } catch (e: Exception) {}
        streamThread = null

        try {
            drainingThread?.interrupt()
        } catch (e: Exception) {}
        drainingThread = null
    }

    /**
     * Clean up and release decoding pipeline and screenrecord processes safely.
     */
    fun stopMirroring() {
        if (!isMirroring && activeDecoder == null && mirrorProcess == null && activeSurface == null) {
            return
        }

        Log.d(TAG, "Stopping screen mirror pipeline and cleaning up resources.")
        isMirroring = false
        mainHandler.removeCallbacks(reconnectRunnable)
        
        cleanupMirroringResources()

        activeSurface = null
        appContext = null
        notifyStateChanged()
        Log.d(TAG, "Screen mirror pipeline successfully terminated.")
    }

    fun pauseMirroring() {
        if (!isMirroring) return
        Log.d(TAG, "Pausing mirror stream for active recording session.")
        isMirroring = false
        mainHandler.removeCallbacks(reconnectRunnable)
        cleanupMirroringResources()
        notifyStateChanged()
    }

    /**
     * Translates local UI coordinates from the mirroring container back to the default display physical scale,
     * then executes an input tap injection command via the persistent Shell.
     */
    fun translateAndInjectTap(context: Context, localX: Float, localY: Float, viewWidth: Float, viewHeight: Float) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val primaryDisplay = dm.getDisplay(0)
        val realMetrics = android.util.DisplayMetrics()
        primaryDisplay?.getRealMetrics(realMetrics) ?: run {
            val resMetrics = context.resources.displayMetrics
            realMetrics.widthPixels = resMetrics.widthPixels
            realMetrics.heightPixels = resMetrics.heightPixels
        }
        val displayWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else 1080
        val displayHeight = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else 2400

        if (viewWidth <= 0 || viewHeight <= 0) return

        val targetX = ((localX / viewWidth) * displayWidth).coerceIn(0f, displayWidth.toFloat()).toInt()
        val targetY = ((localY / viewHeight) * displayHeight).coerceIn(0f, displayHeight.toFloat()).toInt()

        Log.v(TAG, "Touch map: ($localX, $localY) on (${viewWidth}x${viewHeight}) -> ($targetX, $targetY) physical")
        ShellExecutor.injectTap(targetX, targetY)
    }

    /**
     * Translates local start/end drag events into high-performance physical swipes.
     */
    fun translateAndInjectSwipe(
        context: Context,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        viewWidth: Float,
        viewHeight: Float,
        durationMs: Int = 200
    ) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val primaryDisplay = dm.getDisplay(0)
        val realMetrics = android.util.DisplayMetrics()
        primaryDisplay?.getRealMetrics(realMetrics) ?: run {
            val resMetrics = context.resources.displayMetrics
            realMetrics.widthPixels = resMetrics.widthPixels
            realMetrics.heightPixels = resMetrics.heightPixels
        }
        val displayWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else 1080
        val displayHeight = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else 2400

        if (viewWidth <= 0 || viewHeight <= 0) return

        val targetX1 = ((startX / viewWidth) * displayWidth).coerceIn(0f, displayWidth.toFloat()).toInt()
        val targetY1 = ((startY / viewHeight) * displayHeight).coerceIn(0f, displayHeight.toFloat()).toInt()
        val targetX2 = ((endX / viewWidth) * displayWidth).coerceIn(0f, displayWidth.toFloat()).toInt()
        val targetY2 = ((endY / viewHeight) * displayHeight).coerceIn(0f, displayHeight.toFloat()).toInt()

        Log.v(TAG, "Swipe map: ($startX,$startY) to ($endX,$endY) -> ($targetX1,$targetY1) to ($targetX2,$targetY2)")
        ShellExecutor.injectSwipe(targetX1, targetY1, targetX2, targetY2, durationMs)
    }

    /**
     * Forwards privileged keystroke inputs to simulate hardware events natively.
     */
    fun injectKeyEvent(keyCode: Int) {
        Log.d(TAG, "Injecting system keycode: $keyCode")
        ShellExecutor.injectKeyEvent(keyCode)
    }
}
