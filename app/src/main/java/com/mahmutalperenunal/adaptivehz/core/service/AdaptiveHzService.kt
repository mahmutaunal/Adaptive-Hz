package com.mahmutalperenunal.adaptivehz.core.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzEngine
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.OtherStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.SamsungStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.XiaomiStrategy
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.shizuku.ShizukuInputManager
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController

/**
 * Accessibility bridge between the Android system and AdaptiveHzEngine.
 *
 * Responsibilities:
 * - Select the correct VendorStrategy at runtime.
 * - Forward only relevant interaction events to the engine.
 * - Forward events only while the app is in ADAPTIVE mode.
 * - Re-apply the selected Adaptive Hz mode when Battery Saver state changes.
 */
@SuppressLint("AccessibilityPolicy")
class AdaptiveHzService : AccessibilityService() {

    private lateinit var engine: AdaptiveHzEngine
    private var started = false

    private val heartbeatHandler by lazy { Handler(Looper.getMainLooper()) }

    private val shizukuInputManager by lazy { ShizukuInputManager() }

    private var lastShizukuRetryAt = 0L
    private var batterySaverReceiverRegistered = false

    /**
     * Listens for Battery Saver state changes while the accessibility service is active.
     *
     * This is intentionally registered dynamically instead of via manifest because
     * it only matters while AdaptiveHzService and the engine are running.
     */
    private val batterySaverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return
            if (!::engine.isInitialized) return

            val batterySaverOn = RefreshRateController.isBatterySaverOn(this@AdaptiveHzService)
            val keepActiveDuringBatterySaver =
                AdaptiveHzPrefs.shouldKeepActiveDuringBatterySaver(this@AdaptiveHzService)

            Log.d(
                TAG,
                "Battery Saver changed. enabled=$batterySaverOn, " +
                        "keepActive=$keepActiveDuringBatterySaver"
            )

            engine.reapplyCurrentMode(
                reason = "Battery Saver changed: enabled=$batterySaverOn"
            )
        }
    }

    /**
     * Periodically updates the runtime heartbeat state.
     */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            AdaptiveHzPrefs.markAccessibilityHeartbeat(applicationContext)
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    /**
     * Called when the accessibility service is fully connected.
     * Initializes the engine with the proper vendor strategy.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        val appContext = applicationContext

        AdaptiveHzPrefs.markAccessibilityHeartbeat(appContext)
        startHeartbeat()
        registerBatterySaverReceiver()

        // Selects vendor-specific refresh rate behavior.
        val strategy = when (DeviceVendorDetector.detect()) {
            DeviceVendor.SAMSUNG -> SamsungStrategy()
            DeviceVendor.XIAOMI -> XiaomiStrategy()
            else -> OtherStrategy()
        }

        engine = AdaptiveHzEngine(
            context = appContext,
            strategy = strategy,
            getGlobalMode = { AdaptiveHzPrefs.getCurrentMode(appContext) },
            shouldIgnorePackage = { pkg ->
                pkg == null || pkg == "com.android.systemui"
            },
            getAppProfileMode = { pkg ->
                AdaptiveHzPrefs.getAppRefreshProfileMode(appContext, pkg)
            },
            interactionSignalProvider = shizukuInputManager,
            tag = "AdaptiveHzEngine"
        )

        if (!started) {
            engine.start()
            shizukuInputManager.checkStatus()
            started = true
        }
    }

    /**
     * Entry point for Accessibility events.
     * Performs lightweight filtering before delegating to the engine.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val appContext = applicationContext

        AdaptiveHzPrefs.markAccessibilityHeartbeat(appContext)
        ensureShizukuReady()

        Log.d("AdaptiveHzRaw", "rawEvent=${event.eventType}")
        engine.onEvent(event)
    }

    /**
     * Triggered when the system temporarily interrupts accessibility feedback.
     */
    override fun onInterrupt() {
        AdaptiveHzPrefs.setAccessibilityConnected(applicationContext, false)
    }

    override fun onDestroy() {
        val appContext = applicationContext

        stopHeartbeat()
        heartbeatHandler.removeCallbacksAndMessages(null)
        unregisterBatterySaverReceiver()

        try {
            if (::engine.isInitialized) engine.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to stop engine", t)
        }

        try {
            shizukuInputManager.destroy()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to destroy Shizuku input manager", t)
        }

        try {
            AccessibilityHealthMonitor.cancelRecoveryNotification(appContext)
        } catch (_: Throwable) { }

        AdaptiveHzPrefs.markAccessibilityDisconnected(appContext)

        started = false
        super.onDestroy()
    }

    /**
     * Starts periodic accessibility health updates.
     */
    private fun startHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.post(heartbeatRunnable)
    }

    /**
     * Stops periodic accessibility health updates.
     */
    private fun stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    private fun registerBatterySaverReceiver() {
        if (batterySaverReceiverRegistered) return

        try {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            registerReceiver(batterySaverReceiver, filter)
            batterySaverReceiverRegistered = true

            Log.d(TAG, "Battery Saver receiver registered")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register Battery Saver receiver", t)
        }
    }

    private fun unregisterBatterySaverReceiver() {
        if (!batterySaverReceiverRegistered) return

        try {
            unregisterReceiver(batterySaverReceiver)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to unregister Battery Saver receiver", t)
        } finally {
            batterySaverReceiverRegistered = false
        }
    }

    private fun ensureShizukuReady() {
        val now = System.currentTimeMillis()

        if (shizukuInputManager.isAvailable()) return
        if (now - lastShizukuRetryAt < 5_000L) return

        lastShizukuRetryAt = now
        shizukuInputManager.checkStatus()
    }

    companion object {
        private const val TAG = "AdaptiveHzService"
        private const val HEARTBEAT_INTERVAL_MS = 2_500L
    }
}