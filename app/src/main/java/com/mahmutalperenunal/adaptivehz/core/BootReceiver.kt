package com.mahmutalperenunal.adaptivehz.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * When the device restarts, if the user has left adaptive mode enabled,
 * it initially sets the screen to 60 Hz. Subsequently, based on touch/scroll input,
 * it continues to handle transitions via AdaptiveHzService.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("adaptive_hz_prefs", Context.MODE_PRIVATE)
            val dynamicEnabled = prefs.getBoolean("dynamic_enabled", false)

            if (dynamicEnabled) {
                // If the user has left adaptive mode ON: start at minimum Hz
                RefreshRateController.applyForceMinimum(context)
            }

            val keepAlive = prefs.getBoolean("keep_alive_enabled", false)
            if (keepAlive) {
                StabilityForegroundService.start(context)
            }
        }
    }
}