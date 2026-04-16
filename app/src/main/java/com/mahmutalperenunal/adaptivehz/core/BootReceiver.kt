package com.mahmutalperenunal.adaptivehz.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController

/**
 * Restores the last known app mode after device reboot.
 *
 * - OFF: leaves the system as-is.
 * - ADAPTIVE: starts from minimum refresh rate so AdaptiveHzService can raise it on interaction.
 * - FORCE_MIN: reapplies minimum refresh rate.
 * - FORCE_MAX: reapplies maximum refresh rate.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val currentMode = AdaptiveHzPrefs.getCurrentMode(context)

        when (currentMode) {
            AdaptiveHzMode.OFF -> {
                // No restore needed when the app was left disabled.
            }

            AdaptiveHzMode.ADAPTIVE,
            AdaptiveHzMode.FORCE_MIN -> {
                try {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try { RefreshRateController.applyForceMinimum(context) } catch (_: Throwable) {}
                    }, 1500L)
                } catch (_: Throwable) {
                    // Fallback: no delay
                    try { RefreshRateController.applyForceMinimum(context) } catch (_: Throwable) {}
                }
            }

            AdaptiveHzMode.FORCE_MAX -> {
                try {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try { RefreshRateController.applyForceMaximum(context) } catch (_: Throwable) {}
                    }, 1500L)
                } catch (_: Throwable) {
                    // Fallback: no delay
                    try { RefreshRateController.applyForceMaximum(context) } catch (_: Throwable) {}
                }
            }
        }

        val keepAlive = AdaptiveHzPrefs.isKeepAliveEnabled(context)
        if (keepAlive) {
            try { StabilityForegroundService.start(context) } catch (_: Throwable) {}
        }
    }
}