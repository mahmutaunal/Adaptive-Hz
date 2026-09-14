package com.mahmutalperenunal.adaptivehz.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzActionHandler
import com.mahmutalperenunal.adaptivehz.core.service.StabilityForegroundService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        val pendingResult = goAsync()
        runCatching {
            recoveryExecutor.execute {
                try {
                    when (intent?.action) {
                        Intent.ACTION_MY_PACKAGE_REPLACED -> handlePackageReplaced(appContext)
                        Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(appContext)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }.onFailure {
            pendingResult.finish()
            Log.e(TAG, "Unable to schedule boot recovery", it)
        }
    }

    // Restores monitoring components after the app package is updated.
    private fun handlePackageReplaced(context: Context) {
        recoverLegacyCustomOverrides(context, "package update")

        // Force the next health check to revalidate the accessibility connection.
        AdaptiveHzPrefs.markAccessibilityDisconnected(context)

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            runCatching {
                StabilityForegroundService.start(context)
            }.onFailure {
                Log.w(TAG, "Unable to start foreground service after update", it)
            }
        }

        recoveryExecutor.schedule(
            {
                AccessibilityHealthMonitor.check(context, "package_replaced")
            },
            PACKAGE_REPLACED_HEALTH_DELAY_MS,
            TimeUnit.MILLISECONDS
        )
    }

    // Restores the persisted mode and background services after device startup.
    private fun handleBootCompleted(context: Context) {
        recoverLegacyCustomOverrides(context, "device boot")

        // Reapply the last active mode after Android finishes booting.
        val currentMode = AdaptiveHzPrefs.getCurrentMode(context)

        if (currentMode != AdaptiveHzMode.OFF) {
            recoveryExecutor.schedule(
                {
                    val latestMode = AdaptiveHzPrefs.getCurrentMode(context)
                    if (latestMode == AdaptiveHzMode.OFF) return@schedule

                    runCatching {
                        AdaptiveHzActionHandler.applyModeAsync(
                            context = context,
                            mode = latestMode
                        )
                    }.onFailure {
                        Log.e(
                            TAG,
                            "Unable to restore mode after boot: $latestMode",
                            it
                        )
                    }
                },
                MODE_RESTORE_DELAY_MS,
                TimeUnit.MILLISECONDS
            )
        }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            runCatching {
                StabilityForegroundService.start(context)
            }.onFailure {
                Log.w(TAG, "Unable to start keep-alive service after boot", it)
            }
        }

        recoveryExecutor.schedule(
            {
                AccessibilityHealthMonitor.check(context, "boot_completed")
            },
            BOOT_HEALTH_DELAY_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun recoverLegacyCustomOverrides(context: Context, reason: String) {
        runCatching {
            RefreshRateOperationCoordinator.run {
                CustomRefreshRateController.recoverPersistentOverridesAfterRestart(context)
            }
        }.onSuccess { restored ->
            if (!restored) Log.e(TAG, "Legacy custom refresh-rate recovery failed after $reason")
        }.onFailure { error ->
            Log.e(TAG, "Legacy custom refresh-rate recovery crashed after $reason", error)
        }
    }

    // Delays allow Android services and settings providers to become available.
    companion object {
        private const val TAG = "BootReceiver"

        private const val MODE_RESTORE_DELAY_MS = 1_500L
        private const val PACKAGE_REPLACED_HEALTH_DELAY_MS = 5_000L
        private const val BOOT_HEALTH_DELAY_MS = 8_000L

        private val recoveryExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "AdaptiveHzBootRecovery").apply { isDaemon = true }
        }
    }
}
