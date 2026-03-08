package com.mahmutalperenunal.adaptivehz.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzEngine
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
 * - Respect the user’s dynamic_enabled preference.
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
            isEnabled = { isDynamicEnabled() },
            shouldIgnorePackage = { pkg ->
                pkg == null || pkg == packageName || pkg == "com.android.systemui"
            },
            tag = "AdaptiveHzEngine",
            idleTimeoutMs = 3500L,
            maxHighHoldMs = 8000L
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
        if (!isDynamicEnabled()) return
        if (!shouldForward(event.eventType)) return
        engine.onEvent(event)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try { if (::engine.isInitialized) engine.stop() } catch (_: Throwable) {}
        started = false
        super.onDestroy()
    }

    /**
     * Filters event types to reduce noise and prevent false boosts.
     */
    private fun shouldForward(t: Int): Boolean {
        return when (t) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> true
            else -> false
        }
    }

    /** Returns whether adaptive mode is currently enabled by the user. */
    private fun isDynamicEnabled(): Boolean {
        val prefs = getSharedPreferences("adaptive_hz_prefs", MODE_PRIVATE)
        return prefs.getBoolean("dynamic_enabled", false)
    }
}