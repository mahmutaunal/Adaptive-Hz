package com.mahmutalperenunal.adaptivehz

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * A service that monitors touch/scroll-like interactions across the system,
 * running at 90 Hz during interaction and 60 Hz when idle.
 *
 * This service must be manually enabled in Accessibility settings.
 */
@SuppressLint("AccessibilityPolicy")
class AdaptiveHzService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val downgradeRunnable = Runnable {
        // If there is no interaction for a while, return to 60 Hz
        Log.d("AdaptiveHzService", "Idle: forcing 60 Hz")
        RefreshRateController.applyForce60(this)
        isHigh = false
    }

    // Are we currently in 90 mode? Flag to reduce unnecessary repetition.
    private var isHigh = false

    // How long should we wait before reducing it to 60 if there is no touch/scroll? (ms)
    private val idleTimeoutMs = 300L // 0.3 seconds

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                bumpToHigh()
            }
        }
    }

    private fun bumpToHigh() {
        // Pull back to 90 before every interaction
        if (!isHigh) {
            Log.d("AdaptiveHzService", "Interaction: forcing 90 Hz")
            RefreshRateController.applyForce90(this)
            isHigh = true
        }

        // Cancel the last downgrade timer and set up a new one
        handler.removeCallbacks(downgradeRunnable)
        handler.postDelayed(downgradeRunnable, idleTimeoutMs)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AdaptiveHzService", "Service connected")

        // When the service starts, if the adaptive flag is enabled, start at 60 Hz
        if (isDynamicEnabled()) {
            RefreshRateController.applyForce60(this)
            isHigh = false
        }
    }

    // Is adaptive mode active?
    private fun isDynamicEnabled(): Boolean {
        val prefs = getSharedPreferences("adaptive_hz_prefs", MODE_PRIVATE)
        return prefs.getBoolean("dynamic_enabled", false)
    }
}