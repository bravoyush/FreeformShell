package com.example.freeformshell

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PhoneMirrorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
