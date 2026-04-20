package com.mahmutalperenunal.adaptivehz.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzEngine
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.OtherStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.SamsungStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.XiaomiStrategy
import com.mahmutalperenunal.adaptivehz.core.system.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.system.DeviceVendorDetector


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

    /**
     * Called when the accessibility service is fully connected.
     * Initializes the engine with the proper vendor strategy.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AdaptiveHzService", "Service connected")

        val strategy = when (DeviceVendorDetector.detect()) {
            DeviceVendor.SAMSUNG -> SamsungStrategy()
            DeviceVendor.XIAOMI -> XiaomiStrategy()
            else -> OtherStrategy()
        }

        engine = AdaptiveHzEngine(
            context = this,
            strategy = strategy,
            isEnabled = { isAdaptiveModeEnabled() },
            shouldIgnorePackage = { pkg ->
                pkg == null || pkg == packageName || pkg == "com.android.systemui"
            },
            tag = "AdaptiveHzEngine"
        )

        if (!started) {
            engine.start()
            started = true
        }
    }

    /**
     * Entry point for Accessibility events.
     * Performs lightweight filtering before delegating to the engine.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isAdaptiveModeEnabled()) return
        Log.d("AdaptiveHzRaw", "rawEvent=${event.eventType}")
        engine.onEvent(event)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try { if (::engine.isInitialized) engine.stop() } catch (_: Throwable) {}
        started = false
        super.onDestroy()
    }

    /** Returns whether the app is currently in adaptive mode. */
    private fun isAdaptiveModeEnabled(): Boolean {
        return AdaptiveHzPrefs.getCurrentMode(this) == AdaptiveHzMode.ADAPTIVE
    }
}