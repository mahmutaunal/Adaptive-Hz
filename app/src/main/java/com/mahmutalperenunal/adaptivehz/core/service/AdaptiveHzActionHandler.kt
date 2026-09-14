package com.mahmutalperenunal.adaptivehz.core.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.RefreshRateApplyResult
import com.mahmutalperenunal.adaptivehz.core.engine.model.isOperationalSuccess
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.VendorStrategyProvider
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.system.CustomRefreshRateController
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateOperationCoordinator
import com.mahmutalperenunal.adaptivehz.core.system.isSuccess
import com.mahmutalperenunal.adaptivehz.widget.AdaptiveHzWidgetUpdater
import java.util.concurrent.Executors

// Coordinates user-triggered mode changes and refreshes related app surfaces.
object AdaptiveHzActionHandler {

    private const val TAG = "AdaptiveHzAction"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val modeCommandExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AdaptiveHzModeCommand").apply { isDaemon = true }
        }
    }
    private val modeCommandDispatcher by lazy {
        LatestCommandDispatcher(
            workerExecutor = modeCommandExecutor,
            callbackExecutor = { command -> mainHandler.post(command) }
        )
    }

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

        // Keep the existing mode-to-vendor mapping intact for the fallback path.
        val write = when (mode) {
            AdaptiveHzMode.OFF -> strategy.desiredSystemControlled(appContext)
            AdaptiveHzMode.ADAPTIVE -> strategy.desiredLow(appContext)
            AdaptiveHzMode.FORCE_MIN -> strategy.desiredForceMinimum(appContext)
            AdaptiveHzMode.FORCE_MAX -> strategy.desiredForceMaximum(appContext)
        }

        val customRate = when (mode) {
            // Adaptive custom rates are interaction targets only. LOW must keep using
            // the unchanged vendor strategy so the first touch is never rendered at
            // a previously configured custom minimum.
            AdaptiveHzMode.ADAPTIVE -> null
            AdaptiveHzMode.FORCE_MIN -> AdaptiveHzPrefs.getGlobalCustomMinimumRate(appContext)
            AdaptiveHzMode.FORCE_MAX -> AdaptiveHzPrefs.getGlobalCustomMaximumRate(appContext)
            AdaptiveHzMode.OFF -> null
        }
        var customFallbackRequired = false

        if (customRate != null) {
            val customResult = CustomRefreshRateController.applyFixedRate(appContext, customRate)
            if (customResult.isSuccess) {
                AdaptiveHzPrefs.updateDebugLastWrite(
                    context = appContext,
                    label = "${mode.name}: custom ${customRate}Hz / ${customResult::class.simpleName}",
                    success = true
                )
                return RefreshRateApplyResult.NoOperation
            }
            customFallbackRequired = true
            Log.w(TAG, "Custom ${customRate}Hz unavailable; falling back to ${strategy.name}")
        }

        // Custom overrides must be removed before returning to a vendor strategy.
        if (!CustomRefreshRateController.restoreOriginal(appContext)) {
            Log.e(TAG, "Unable to restore original min/peak settings before legacy write")
            return write?.let {
                RefreshRateApplyResult.Failure(
                    requestedWrite = it,
                    throwable = IllegalStateException("Custom refresh-rate restore failed")
                )
            } ?: RefreshRateApplyResult.NoOperation
        }

        // Some strategies may not require a direct settings write for the selected mode.
        if (write == null) {
            Log.d(TAG, "No setting write required. mode=$mode strategy=${strategy.name}")
            return if (customFallbackRequired && customRate != null) {
                RefreshRateApplyResult.CustomFallbackApplied(null, customRate)
            } else {
                RefreshRateApplyResult.NoOperation
            }
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

        return if (customFallbackRequired && customRate != null && result.isOperationalSuccess) {
            RefreshRateApplyResult.CustomFallbackApplied(write, customRate)
        } else {
            result
        }
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

    private fun requestMode(
        context: Context,
        mode: AdaptiveHzMode,
        stopKeepAliveWhenOff: Boolean,
        onComplete: ((RefreshRateApplyResult) -> Unit)?,
        onSettled: (() -> Unit)?
    ) {
        val appContext = context.applicationContext
        val requestedAt = SystemClock.elapsedRealtime()
        AdaptiveHzPrefs.syncLegacyStateFromMode(
            appContext,
            mode
        )

        modeCommandDispatcher.submit(
            operation = {
                val operationStartedAt = SystemClock.elapsedRealtime()
                RefreshRateOperationCoordinator.run {
                    if (AdaptiveHzPrefs.getCurrentMode(appContext) != mode) {
                        RefreshRateApplyResult.NoOperation
                    } else {
                        applyRefreshMode(appContext, mode)
                    }
                }.also {
                    Log.d(
                        TAG,
                        "Mode timing mode=$mode queueMs=${operationStartedAt - requestedAt} " +
                            "applyMs=${SystemClock.elapsedRealtime() - operationStartedAt}"
                    )
                }
            },
            onComplete = { result ->
                finishLatestModeRequest(appContext, mode, stopKeepAliveWhenOff)
                onComplete?.invoke(result)
            },
            onError = { error ->
                Log.e(TAG, "Unexpected asynchronous mode failure: $mode", error)
                finishLatestModeRequest(appContext, mode, stopKeepAliveWhenOff)
                onComplete?.invoke(
                    RefreshRateApplyResult.Failure(
                        requestedWrite = null,
                        throwable = error
                    )
                )
            },
            onSettled = { onSettled?.invoke() }
        )
    }

    private fun finishLatestModeRequest(
        context: Context,
        mode: AdaptiveHzMode,
        stopKeepAliveWhenOff: Boolean
    ) {
        if (mode == AdaptiveHzMode.OFF) {
            runCatching {
                AccessibilityHealthMonitor.cancelRecoveryNotification(context)
            }
            if (stopKeepAliveWhenOff) {
                runCatching { StabilityForegroundService.stop(context) }
            }
        } else {
            ensureKeepAliveIfNeeded(context)
        }

        refreshSurfaces(context)
    }

    fun turnOnAsync(
        context: Context,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        setAdaptiveAsync(context, onComplete, onSettled)
    }

    fun turnOffAsync(
        context: Context,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        requestMode(
            context = context,
            mode = AdaptiveHzMode.OFF,
            stopKeepAliveWhenOff = true,
            onComplete = onComplete,
            onSettled = onSettled
        )
    }

    fun turnOffForNotificationAsync(
        context: Context,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        requestMode(
            context = context,
            mode = AdaptiveHzMode.OFF,
            stopKeepAliveWhenOff = false,
            onComplete = onComplete,
            onSettled = onSettled
        )
    }

    fun setAdaptiveAsync(
        context: Context,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        requestMode(context, AdaptiveHzMode.ADAPTIVE, false, onComplete, onSettled)
    }

    fun setMinimumAsync(
        context: Context,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        requestMode(context, AdaptiveHzMode.FORCE_MIN, false, onComplete, onSettled)
    }

    fun setMaximumAsync(
        context: Context,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        requestMode(context, AdaptiveHzMode.FORCE_MAX, false, onComplete, onSettled)
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
    fun applyModeAsync(
        context: Context,
        mode: AdaptiveHzMode,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        return when (mode) {
            AdaptiveHzMode.OFF -> turnOffAsync(context, onComplete, onSettled)
            AdaptiveHzMode.ADAPTIVE -> setAdaptiveAsync(context, onComplete, onSettled)
            AdaptiveHzMode.FORCE_MIN -> setMinimumAsync(context, onComplete, onSettled)
            AdaptiveHzMode.FORCE_MAX -> setMaximumAsync(context, onComplete, onSettled)
        }
    }

    // Toggles Adaptive Hz between disabled and adaptive states.
    fun toggleAsync(
        context: Context,
        onComplete: ((RefreshRateApplyResult) -> Unit)? = null,
        onSettled: (() -> Unit)? = null
    ) {
        if (isAppEnabled(context.applicationContext)) {
            turnOffAsync(context, onComplete, onSettled)
        } else {
            turnOnAsync(context, onComplete, onSettled)
        }
    }
}
