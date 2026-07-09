package com.mahmutalperenunal.adaptivehz.core.service

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.widget.AdaptiveHzWidgetUpdater

/**
 * Shared action layer used by both UI and notification actions.
 *
 * This prevents business logic duplication between HomeScreen and
 * StabilityForegroundService.
 */
object AdaptiveHzActionHandler {

    private fun refreshSurfaces(context: Context) {
        val appContext = context.applicationContext

        try {
            AdaptiveHzWidgetUpdater.refreshAll(appContext)
        } catch (_: Throwable) { }

        try {
            StabilityForegroundService.refreshNotification(appContext)
        } catch (_: Throwable) { }
    }

    fun getCurrentMode(context: Context): AdaptiveHzMode {
        return AdaptiveHzPrefs.getCurrentMode(context.applicationContext)
    }

    fun isAppEnabled(context: Context): Boolean {
        return AdaptiveHzPrefs.isAppEnabled(context.applicationContext)
    }

    /**
     * Mirrors the app's existing "On" behavior:
     * - app becomes enabled
     * - mode becomes ADAPTIVE
     * - refresh starts from minimum
     * - keep-alive service starts only if the user enabled it
     */
    fun turnOn(context: Context) {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(appContext, AdaptiveHzMode.ADAPTIVE)

        try {
            RefreshRateController.applyForceMinimum(appContext)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(appContext)) {
            try {
                StabilityForegroundService.start(appContext)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(appContext)
    }

    /**
     * Mirrors the app's existing "Off" behavior:
     * - adaptive/manual behavior disabled
     * - system default/adaptive restored
     * - keep-alive notification service stopped
     */
    fun turnOff(context: Context) {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(appContext, AdaptiveHzMode.OFF)

        try {
            RefreshRateController.resetToSystemDefault(appContext)
        } catch (_: Throwable) { }

        try {
            AccessibilityHealthMonitor.cancelRecoveryNotification(appContext)
        } catch (_: Throwable) { }

        try {
            StabilityForegroundService.stop(appContext)
        } catch (_: Throwable) { }

        refreshSurfaces(appContext)
    }

    /**
     * Soft off used by notification actions.
     *
     * Unlike the hard off flow, this does not stop the foreground service
     * immediately. The notification can remain visible for a short grace period.
     */
    fun turnOffForNotification(context: Context) {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(appContext, AdaptiveHzMode.OFF)

        try {
            RefreshRateController.resetToSystemDefault(appContext)
        } catch (_: Throwable) { }

        try {
            AccessibilityHealthMonitor.cancelRecoveryNotification(appContext)
        } catch (_: Throwable) { }

        refreshSurfaces(appContext)
    }

    /**
     * Enables adaptive mode while keeping the app enabled.
     */
    fun setAdaptive(context: Context) {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(appContext, AdaptiveHzMode.ADAPTIVE)

        try {
            RefreshRateController.applyForceMinimum(appContext)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(appContext)) {
            try {
                StabilityForegroundService.start(appContext)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(appContext)
    }

    /**
     * Locks refresh rate to minimum while keeping the app enabled.
     */
    fun setMinimum(context: Context) {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(appContext, AdaptiveHzMode.FORCE_MIN)

        try {
            RefreshRateController.applyForceMinimum(appContext)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(appContext)) {
            try {
                StabilityForegroundService.start(appContext)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(appContext)
    }

    /**
     * Locks refresh rate to maximum while keeping the app enabled.
     */
    fun setMaximum(context: Context) {
        val appContext = context.applicationContext

        AdaptiveHzPrefs.syncLegacyStateFromMode(appContext, AdaptiveHzMode.FORCE_MAX)

        try {
            RefreshRateController.applyForceMaximum(appContext)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(appContext)) {
            try {
                StabilityForegroundService.start(appContext)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(appContext)
    }

    /**
     * Used by notification layer to determine which two alternative actions
     * should be shown together with the Off action while the app is enabled.
     */
    fun getAlternativeModesForNotification(context: Context): List<AdaptiveHzMode> {
        val appContext = context.applicationContext
        return when (getCurrentMode(appContext)) {
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

    /**
     * Small helper so callers can route a mode selection without duplicating logic.
     */
    fun applyMode(context: Context, mode: AdaptiveHzMode) {
        val appContext = context.applicationContext
        when (mode) {
            AdaptiveHzMode.OFF -> turnOff(appContext)
            AdaptiveHzMode.ADAPTIVE -> setAdaptive(appContext)
            AdaptiveHzMode.FORCE_MIN -> setMinimum(appContext)
            AdaptiveHzMode.FORCE_MAX -> setMaximum(appContext)
        }
    }

    fun toggle(context: Context) {
        val appContext = context.applicationContext
        if (isAppEnabled(appContext)) {
            turnOff(appContext)
        } else {
            turnOn(appContext)
        }
    }
}