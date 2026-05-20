package com.mahmutalperenunal.adaptivehz.core.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzEngine
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.OtherStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.SamsungStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.XiaomiStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import com.mahmutalperenunal.adaptivehz.core.shizuku.ShizukuInputManager

/**
 * Accessibility bridge between the Android system and AdaptiveHzEngine.
 *
 * Responsibilities:
 * - Select the correct VendorStrategy at runtime.
 * - Forward only relevant interaction events to the engine.
 * - Forward events only while the app is in ADAPTIVE mode.
 */
@SuppressLint("AccessibilityPolicy")
class AdaptiveHzService : AccessibilityService() {

    private lateinit var engine: AdaptiveHzEngine
    private var started = false

    private val heartbeatHandler by lazy { Handler(Looper.getMainLooper()) }

    private val shizukuInputManager by lazy { ShizukuInputManager() }

    /**
     * Periodically updates the runtime heartbeat state.
     */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            AdaptiveHzPrefs.markAccessibilityHeartbeat(this@AdaptiveHzService)
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    /**
     * Called when the accessibility service is fully connected.
     * Initializes the engine with the proper vendor strategy.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AdaptiveHzService", "Service connected")

        AdaptiveHzPrefs.markAccessibilityHeartbeat(this)
        startHeartbeat()

        // Selects vendor-specific refresh rate behavior.
        val strategy = when (DeviceVendorDetector.detect()) {
            DeviceVendor.SAMSUNG -> SamsungStrategy()
            DeviceVendor.XIAOMI -> XiaomiStrategy()
            else -> OtherStrategy()
        }

        engine = AdaptiveHzEngine(
            context = this,
            strategy = strategy,
            getGlobalMode = { AdaptiveHzPrefs.getCurrentMode(this) },
            shouldIgnorePackage = { pkg ->
                pkg == null || pkg == "com.android.systemui"
            },
            getAppProfileMode = { pkg ->
                AdaptiveHzPrefs.getAppRefreshProfileMode(this, pkg)
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

        AdaptiveHzPrefs.markAccessibilityHeartbeat(this)

        Log.d("AdaptiveHzRaw", "rawEvent=${event.eventType}")
        engine.onEvent(event)
    }

    /**
     * Triggered when the system temporarily interrupts accessibility feedback.
     */
    override fun onInterrupt() {
        AdaptiveHzPrefs.setAccessibilityConnected(this, false)
    }

    override fun onDestroy() {
        stopHeartbeat()

        AdaptiveHzPrefs.markAccessibilityDisconnected(this)
        shizukuInputManager.destroy()

        try {
            if (::engine.isInitialized) engine.stop()
        } catch (_: Throwable) { }

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

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 2_500L
    }
}