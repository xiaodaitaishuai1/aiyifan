package com.aiyifan.app.core.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** Kept for the lifetime of the player, including while a floating window owns it. */
class ScreenOffPlaybackObserver(context: Context, private val pause: () -> Unit) {
    private val appContext = context.applicationContext
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) pause()
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (!canPlay(appContext)) pause()
    }

    fun release() = appContext.unregisterReceiver(receiver)

    companion object {
        fun canPlay(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }
}
