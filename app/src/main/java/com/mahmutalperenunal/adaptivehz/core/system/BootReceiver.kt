package com.mahmutalperenunal.adaptivehz.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzActionHandler
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

    // Routes supported system broadcasts to their corresponding recovery flow.
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())

        when (intent?.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                handlePackageReplaced(appContext, handler)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                handleBootCompleted(appContext, handler)
            }
        }
    }

    // Restores monitoring components after the app package is updated.
    private fun handlePackageReplaced(
        context: Context,
        handler: Handler
    ) {
        // Force the next health check to revalidate the accessibility connection.
        AdaptiveHzPrefs.markAccessibilityDisconnected(context)

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            runCatching {
                StabilityForegroundService.start(context)
            }.onFailure {
                Log.w(TAG, "Unable to start foreground service after update", it)
            }
        }

        handler.postDelayed(
            {
                AccessibilityHealthMonitor.check(
                    context = context,
                    reason = "package_replaced"
                )
            },
            PACKAGE_REPLACED_HEALTH_DELAY_MS
        )
    }

    // Restores the persisted mode and background services after device startup.
    private fun handleBootCompleted(
        context: Context,
        handler: Handler
    ) {
        // Reapply the last active mode after Android finishes booting.
        val currentMode = AdaptiveHzPrefs.getCurrentMode(context)

        if (currentMode != AdaptiveHzMode.OFF) {
            handler.postDelayed(
                {
                    runCatching {
                        AdaptiveHzActionHandler.applyMode(
                            context = context,
                            mode = currentMode
                        )
                    }.onFailure {
                        Log.e(
                            TAG,
                            "Unable to restore mode after boot: $currentMode",
                            it
                        )
                    }
                },
                MODE_RESTORE_DELAY_MS
            )
        }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            runCatching {
                StabilityForegroundService.start(context)
            }.onFailure {
                Log.w(TAG, "Unable to start keep-alive service after boot", it)
            }
        }

        handler.postDelayed(
            {
                AccessibilityHealthMonitor.check(
                    context = context,
                    reason = "boot_completed"
                )
            },
            BOOT_HEALTH_DELAY_MS
        )
    }

    // Delays allow Android services and settings providers to become available.
    companion object {
        private const val TAG = "BootReceiver"

        private const val MODE_RESTORE_DELAY_MS = 1_500L
        private const val PACKAGE_REPLACED_HEALTH_DELAY_MS = 5_000L
        private const val BOOT_HEALTH_DELAY_MS = 8_000L
    }
}