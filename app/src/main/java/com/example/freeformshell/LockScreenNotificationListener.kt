package com.example.freeformshell

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ActiveNotificationState {
    var notifications by mutableStateOf<List<StatusBarNotification>>(emptyList())
}

data class MediaSessionData(
    val packageName: String,
    val title: String,
    val artist: String,
    val albumArt: android.graphics.Bitmap?,
    val isPlaying: Boolean,
    val controller: MediaController
)

object ActiveMediaState {
    var isPlaying by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var albumArt by mutableStateOf<android.graphics.Bitmap?>(null)
    var packageName by mutableStateOf("")
    var activeController by mutableStateOf<MediaController?>(null)
    
    var activeSessions by mutableStateOf<List<MediaSessionData>>(emptyList())
}

class LockScreenNotificationListener : NotificationListenerService() {

    companion object {
        const val TAG = "LockScreenNotificationListener"
        var instance: LockScreenNotificationListener? = null
    }

    private lateinit var mediaSessionManager: MediaSessionManager
    private val handler = Handler(Looper.getMainLooper())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        Log.d(TAG, "Active sessions changed: ${controllers?.size ?: 0}")
        updateMediaController(controllers)
    }

    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var currentControllers: List<MediaController> = emptyList()

    private fun createCallbackForController(controller: MediaController): MediaController.Callback {
        return object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                handler.post {
                    updateActiveSessionsList()
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                handler.post {
                    updateActiveSessionsList()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Listener Connected")
        
        // Refresh notifications
        try {
            val active = activeNotifications
            handler.post {
                ActiveNotificationState.notifications = active?.toList() ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active notifications", e)
        }

        // Register Media Session listener
        try {
            val cn = ComponentName(this, LockScreenNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, cn)
            updateMediaController(mediaSessionManager.getActiveSessions(cn))
        } catch (e: Exception) {
            Log.e(TAG, "Error registering active sessions listener", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.d(TAG, "Listener Disconnected")
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {}
        unregisterActiveController()
        NotificationToastOverlay.dismissAll()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        Log.d(TAG, "onNotificationPosted: pkg=${sbn.packageName}, key=${sbn.key}")
        handler.post {
            val currentList = ActiveNotificationState.notifications.toMutableList()
            // Remove existing version of same key if present
            currentList.removeAll { it.key == sbn.key }
            // Add at the beginning of the list for newest-first order
            currentList.add(0, sbn)
            ActiveNotificationState.notifications = currentList

            val enabled = ThemeManager.isWindowsNotifsEnabled(this)
            Log.d(TAG, "onNotificationPosted: isWindowsNotifsEnabled=$enabled")
            if (enabled) {
                NotificationToastOverlay.showToast(this, sbn)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        Log.d(TAG, "onNotificationRemoved: pkg=${sbn.packageName}, key=${sbn.key}")
        handler.post {
            val currentList = ActiveNotificationState.notifications.toMutableList()
            currentList.removeAll { it.key == sbn.key }
            ActiveNotificationState.notifications = currentList

            val enabled = ThemeManager.isWindowsNotifsEnabled(this)
            Log.d(TAG, "onNotificationRemoved: isWindowsNotifsEnabled=$enabled")
            if (enabled) {
                NotificationToastOverlay.dismissToast(sbn.key)
            }
        }
    }

    private fun updateMediaController(controllers: List<MediaController>?) {
        handler.post {
            unregisterActiveController()
            val list = controllers ?: emptyList()
            currentControllers = list
            
            for (ctrl in list) {
                val cb = createCallbackForController(ctrl)
                try {
                    ctrl.registerCallback(cb)
                    controllerCallbacks[ctrl] = cb
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register callback for ${ctrl.packageName}", e)
                }
            }
            updateActiveSessionsList()
        }
    }

    private fun updateActiveSessionsList() {
        val sessionDataList = currentControllers.map { ctrl ->
            val metadata = ctrl.metadata
            val state = ctrl.playbackState
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Track"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            MediaSessionData(
                packageName = ctrl.packageName,
                title = title,
                artist = artist,
                albumArt = albumArt,
                isPlaying = isPlaying,
                controller = ctrl
            )
        }
        
        ActiveMediaState.activeSessions = sessionDataList

        val primary = sessionDataList.firstOrNull { it.isPlaying } ?: sessionDataList.firstOrNull()
        if (primary != null) {
            ActiveMediaState.activeController = primary.controller
            ActiveMediaState.packageName = primary.packageName
            ActiveMediaState.isPlaying = primary.isPlaying
            ActiveMediaState.title = primary.title
            ActiveMediaState.artist = primary.artist
            ActiveMediaState.albumArt = primary.albumArt
        } else {
            ActiveMediaState.activeController = null
            ActiveMediaState.packageName = ""
            ActiveMediaState.isPlaying = false
            ActiveMediaState.title = ""
            ActiveMediaState.artist = ""
            ActiveMediaState.albumArt = null
        }
    }

    private fun unregisterActiveController() {
        for ((ctrl, cb) in controllerCallbacks) {
            try {
                ctrl.unregisterCallback(cb)
            } catch (e: Exception) {}
        }
        controllerCallbacks.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {}
        unregisterActiveController()
        NotificationToastOverlay.dismissAll()
    }
}
