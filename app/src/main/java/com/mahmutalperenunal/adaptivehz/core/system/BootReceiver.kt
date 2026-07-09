package com.mahmutalperenunal.adaptivehz.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.service.StabilityForegroundService

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
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())

        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AdaptiveHzPrefs.markAccessibilityDisconnected(appContext)

            val keepAlive = AdaptiveHzPrefs.isKeepAliveEnabled(appContext)
            if (keepAlive) {
                try { StabilityForegroundService.start(appContext) } catch (_: Throwable) {}
            }

            try {
                mainHandler.postDelayed({
                    AccessibilityHealthMonitor.check(
                        context = appContext,
                        reason = "package_replaced"
                    )
                }, 5_000L)
            } catch (_: Throwable) {
                AccessibilityHealthMonitor.check(
                    context = appContext,
                    reason = "package_replaced"
                )
            }

            return
        }

        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val currentMode = AdaptiveHzPrefs.getCurrentMode(appContext)

        when (currentMode) {
            AdaptiveHzMode.OFF -> {
                // No restore needed when the app was left disabled.
            }

            AdaptiveHzMode.ADAPTIVE,
            AdaptiveHzMode.FORCE_MIN -> {
                try {
                    mainHandler.postDelayed({
                        try { RefreshRateController.applyForceMinimum(appContext) } catch (_: Throwable) {}
                    }, 1500L)
                } catch (_: Throwable) {
                    // Fallback: no delay
                    try { RefreshRateController.applyForceMinimum(appContext) } catch (_: Throwable) {}
                }
            }

            AdaptiveHzMode.FORCE_MAX -> {
                try {
                    mainHandler.postDelayed({
                        try { RefreshRateController.applyForceMaximum(appContext) } catch (_: Throwable) {}
                    }, 1500L)
                } catch (_: Throwable) {
                    // Fallback: no delay
                    try { RefreshRateController.applyForceMaximum(appContext) } catch (_: Throwable) {}
                }
            }
        }

        val keepAlive = AdaptiveHzPrefs.isKeepAliveEnabled(appContext)
        if (keepAlive) {
            try { StabilityForegroundService.start(appContext) } catch (_: Throwable) {}
        }

        try {
            mainHandler.postDelayed({
                AccessibilityHealthMonitor.check(
                    context = appContext,
                    reason = "boot_completed"
                )
            }, 8_000L)
        } catch (_: Throwable) {
            AccessibilityHealthMonitor.check(
                context = appContext,
                reason = "boot_completed"
            )
        }
    }
}