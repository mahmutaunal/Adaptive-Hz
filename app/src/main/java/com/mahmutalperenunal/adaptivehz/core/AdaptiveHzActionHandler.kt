package com.mahmutalperenunal.adaptivehz.core

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzMode
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
        try {
            AdaptiveHzWidgetUpdater.refreshAll(context)
        } catch (_: Throwable) { }

        try {
            StabilityForegroundService.refreshNotification(context)
        } catch (_: Throwable) { }
    }

    fun getCurrentMode(context: Context): AdaptiveHzMode {
        return AdaptiveHzPrefs.getCurrentMode(context)
    }

    fun isAppEnabled(context: Context): Boolean {
        return AdaptiveHzPrefs.isAppEnabled(context)
    }

    /**
     * Mirrors the app's existing "On" behavior:
     * - app becomes enabled
     * - mode becomes ADAPTIVE
     * - refresh starts from minimum
     * - keep-alive service starts only if the user enabled it
     */
    fun turnOn(context: Context) {
        AdaptiveHzPrefs.syncLegacyStateFromMode(context, AdaptiveHzMode.ADAPTIVE)

        try {
            RefreshRateController.applyForceMinimum(context)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            try {
                StabilityForegroundService.start(context)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(context)
    }

    /**
     * Mirrors the app's existing "Off" behavior:
     * - adaptive/manual behavior disabled
     * - system default/adaptive restored
     * - keep-alive notification service stopped
     */
    fun turnOff(context: Context) {
        AdaptiveHzPrefs.syncLegacyStateFromMode(context, AdaptiveHzMode.OFF)

        try {
            RefreshRateController.resetToSystemDefault(context)
        } catch (_: Throwable) { }

        try {
            StabilityForegroundService.stop(context)
        } catch (_: Throwable) { }

        refreshSurfaces(context)
    }

    /**
     * Soft off used by notification actions.
     *
     * Unlike the hard off flow, this does not stop the foreground service
     * immediately. The notification can remain visible for a short grace period.
     */
    fun turnOffForNotification(context: Context) {
        AdaptiveHzPrefs.syncLegacyStateFromMode(context, AdaptiveHzMode.OFF)

        try {
            RefreshRateController.resetToSystemDefault(context)
        } catch (_: Throwable) { }

        refreshSurfaces(context)
    }

    /**
     * Enables adaptive mode while keeping the app enabled.
     */
    fun setAdaptive(context: Context) {
        AdaptiveHzPrefs.syncLegacyStateFromMode(context, AdaptiveHzMode.ADAPTIVE)

        try {
            RefreshRateController.applyForceMinimum(context)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            try {
                StabilityForegroundService.start(context)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(context)
    }

    /**
     * Locks refresh rate to minimum while keeping the app enabled.
     */
    fun setMinimum(context: Context) {
        AdaptiveHzPrefs.syncLegacyStateFromMode(context, AdaptiveHzMode.FORCE_MIN)

        try {
            RefreshRateController.applyForceMinimum(context)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            try {
                StabilityForegroundService.start(context)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(context)
    }

    /**
     * Locks refresh rate to maximum while keeping the app enabled.
     */
    fun setMaximum(context: Context) {
        AdaptiveHzPrefs.syncLegacyStateFromMode(context, AdaptiveHzMode.FORCE_MAX)

        try {
            RefreshRateController.applyForceMaximum(context)
        } catch (_: Throwable) { }

        if (AdaptiveHzPrefs.isKeepAliveEnabled(context)) {
            try {
                StabilityForegroundService.start(context)
            } catch (_: Throwable) { }
        }

        refreshSurfaces(context)
    }

    /**
     * Used by notification layer to determine which two alternative actions
     * should be shown together with the Off action while the app is enabled.
     */
    fun getAlternativeModesForNotification(context: Context): List<AdaptiveHzMode> {
        return when (getCurrentMode(context)) {
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
        when (mode) {
            AdaptiveHzMode.OFF -> turnOff(context)
            AdaptiveHzMode.ADAPTIVE -> setAdaptive(context)
            AdaptiveHzMode.FORCE_MIN -> setMinimum(context)
            AdaptiveHzMode.FORCE_MAX -> setMaximum(context)
        }
    }

    fun toggle(context: Context) {
        if (isAppEnabled(context)) {
            turnOff(context)
        } else {
            turnOn(context)
        }
    }
}