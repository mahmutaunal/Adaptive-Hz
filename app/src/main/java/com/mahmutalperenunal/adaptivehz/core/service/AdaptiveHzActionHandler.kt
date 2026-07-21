package com.mahmutalperenunal.adaptivehz.core.service

import android.content.Context
import android.util.Log
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.RefreshRateApplyResult
import com.mahmutalperenunal.adaptivehz.core.engine.model.isOperationalSuccess
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.VendorStrategyProvider
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.widget.AdaptiveHzWidgetUpdater

// Coordinates user-triggered mode changes and refreshes related app surfaces.
object AdaptiveHzActionHandler {

    private const val TAG = "AdaptiveHzAction"

    // Refreshes widgets and the foreground notification after a mode change.
    private fun refreshSurfaces(context: Context) {
        val appContext = context.applicationContext

        runCatching {
            AdaptiveHzWidgetUpdater.refreshAll(appContext)
        }.onFailure {
            Log.w(TAG, "Widget refresh failed", it)
        }

        runCatching {
            StabilityForegroundService.refreshNotification(appContext)
        }.onFailure {
            Log.w(TAG, "Notification refresh failed", it)
        }
    }

    // Returns the currently persisted Adaptive Hz mode.
    fun getCurrentMode(context: Context): AdaptiveHzMode {
        return AdaptiveHzPrefs.getCurrentMode(context.applicationContext)
    }

    // Returns whether Adaptive Hz is currently enabled.
    fun isAppEnabled(context: Context): Boolean {
        return AdaptiveHzPrefs.isAppEnabled(context.applicationContext)
    }

    // Resolves the vendor strategy, applies the requested mode, and stores diagnostics.
    private fun applyRefreshMode(
        context: Context,
        mode: AdaptiveHzMode
    ): RefreshRateApplyResult {
        val appContext = context.applicationContext
        val strategy = VendorStrategyProvider.provide()

        // Map the selected mode to the vendor-specific settings write.
        val write = when (mode) {
            AdaptiveHzMode.OFF -> {
                strategy.desiredSystemControlled(appContext)
            }

            AdaptiveHzMode.ADAPTIVE -> {
                strategy.desiredLow(appContext)
            }

            AdaptiveHzMode.FORCE_MIN -> {
                strategy.desiredForceMinimum(appContext)
            }

            AdaptiveHzMode.FORCE_MAX -> {
                strategy.desiredForceMaximum(appContext)
            }
        }

        // Some strategies may not require a direct settings write for the selected mode.
        if (write == null) {
            Log.d(TAG, "No setting write required. mode=$mode strategy=${strategy.name}")
            return RefreshRateApplyResult.NoOperation
        }

        val result = RefreshRateController.applySetting(
            context = appContext,
            write = write
        )

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = appContext,
            label = "${mode.name}: ${write.label} / ${result::class.simpleName}",
            success = result.isOperationalSuccess
        )

        Log.d(
            TAG,
            "applyRefreshMode mode=$mode strategy=${strategy.name} result=$result"
        )

        return result
    }

    // Starts the foreground keep-alive service when the user has enabled it.
    private fun ensureKeepAliveIfNeeded(context: Context) {
        val appContext = context.applicationContext

        if (!AdaptiveHzPrefs.isKeepAliveEnabled(appContext)) return

        runCatching {
            StabilityForegroundService.start(appContext)
        }.onFailure {
            Log.w(TAG, "Unable to start keep-alive service", it)
        }
    }

    // Enables Adaptive Hz using the default adaptive mode.
    fun turnOn(context: Context): RefreshRateApplyResult {
        return setAdaptive(context)
    }

    // Disables Adaptive Hz and stops active recovery and keep-alive components.
    fun turnOff(context: Context): RefreshRateApplyResult {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(
            appContext,
            AdaptiveHzMode.OFF
        )

        val result = applyRefreshMode(
            appContext,
            AdaptiveHzMode.OFF
        )

        runCatching {
            AccessibilityHealthMonitor.cancelRecoveryNotification(appContext)
        }

        runCatching {
            StabilityForegroundService.stop(appContext)
        }

        refreshSurfaces(appContext)
        return result
    }

    // Disables Adaptive Hz from the notification without stopping the host service directly.
    fun turnOffForNotification(context: Context): RefreshRateApplyResult {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(
            appContext,
            AdaptiveHzMode.OFF
        )

        val result = applyRefreshMode(
            appContext,
            AdaptiveHzMode.OFF
        )

        runCatching {
            AccessibilityHealthMonitor.cancelRecoveryNotification(appContext)
        }

        refreshSurfaces(appContext)
        return result
    }

    // Enables vendor-aware adaptive refresh-rate behavior.
    fun setAdaptive(context: Context): RefreshRateApplyResult {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(
            appContext,
            AdaptiveHzMode.ADAPTIVE
        )

        val result = applyRefreshMode(
            appContext,
            AdaptiveHzMode.ADAPTIVE
        )

        ensureKeepAliveIfNeeded(appContext)
        refreshSurfaces(appContext)

        return result
    }

    // Forces the lowest supported refresh-rate mode.
    fun setMinimum(context: Context): RefreshRateApplyResult {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(
            appContext,
            AdaptiveHzMode.FORCE_MIN
        )

        val result = applyRefreshMode(
            appContext,
            AdaptiveHzMode.FORCE_MIN
        )

        ensureKeepAliveIfNeeded(appContext)
        refreshSurfaces(appContext)

        return result
    }

    // Forces the highest supported refresh-rate mode.
    fun setMaximum(context: Context): RefreshRateApplyResult {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(
            appContext,
            AdaptiveHzMode.FORCE_MAX
        )

        val result = applyRefreshMode(
            appContext,
            AdaptiveHzMode.FORCE_MAX
        )

        ensureKeepAliveIfNeeded(appContext)
        refreshSurfaces(appContext)

        return result
    }

    // Returns the mode shortcuts that should be shown in the notification.
    fun getAlternativeModesForNotification(
        context: Context
    ): List<AdaptiveHzMode> {
        return when (getCurrentMode(context.applicationContext)) {
            AdaptiveHzMode.OFF -> emptyList()

            AdaptiveHzMode.ADAPTIVE -> listOf(
                AdaptiveHzMode.FORCE_MIN,
                AdaptiveHzMode.FORCE_MAX
            )

            AdaptiveHzMode.FORCE_MIN -> listOf(
                AdaptiveHzMode.ADAPTIVE,
                AdaptiveHzMode.FORCE_MAX
            )

            AdaptiveHzMode.FORCE_MAX -> listOf(
                AdaptiveHzMode.ADAPTIVE,
                AdaptiveHzMode.FORCE_MIN
            )
        }
    }

    // Routes a mode request to the corresponding public action.
    fun applyMode(
        context: Context,
        mode: AdaptiveHzMode
    ): RefreshRateApplyResult {
        return when (mode) {
            AdaptiveHzMode.OFF -> turnOff(context)
            AdaptiveHzMode.ADAPTIVE -> setAdaptive(context)
            AdaptiveHzMode.FORCE_MIN -> setMinimum(context)
            AdaptiveHzMode.FORCE_MAX -> setMaximum(context)
        }
    }

    // Toggles Adaptive Hz between disabled and adaptive states.
    fun toggle(context: Context): RefreshRateApplyResult {
        return if (isAppEnabled(context.applicationContext)) {
            turnOff(context)
        } else {
            turnOn(context)
        }
    }
}