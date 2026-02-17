package com.mahmutalperenunal.adaptivehz.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * A service that monitors touch/scroll-like interactions across the system,
 * running at Maximum Hz during interaction and Minimum Hz when idle.
 *
 * This service must be manually enabled in Accessibility settings.
 */
@SuppressLint("AccessibilityPolicy")
class AdaptiveHzService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val downgradeRunnable = Runnable {
        // If there is no interaction for a while, return to Minimum Hz
        Log.d("AdaptiveHzService", "Idle: forcing Minimum Hz")
        RefreshRateController.applyForceMinimum(this)
        isHigh = false
    }

    // Are we currently in Maximum Hz mode? Flag to reduce unnecessary repetition.
    private var isHigh = false

    // How long should we wait before reducing it to 60 if there is no touch/scroll? (ms)
    private val idleTimeoutMs = 3500L // 3.5 seconds

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isDynamicEnabled()) return

        // Ignore events coming from our own app UI to prevent self-trigger loops
        val pkg = event.packageName?.toString()
        if (pkg == packageName) return

        when (event.eventType) {
            // Most important: ensure we boost to Maximum as early as possible on the first touch.
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                bumpToHigh(force = true)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            // Extra pre-boost signals that often arrive very early when UI starts updating.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                bumpToHigh(force = false)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                bumpToHigh(force = false)
            }
            // Touch interaction finished; treat this as a final interaction signal
            // so we keep Maximum Hz for a short while before the idle timeout kicks in.
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                bumpToHigh(force = false)
            }
        }
    }

    private fun bumpToHigh(force: Boolean) {
        // Pull back to Maximum before every interaction.
        // `force=true` is used on the very first touch to reduce perceived delay.
        if (!isHigh || force) {
            Log.d("AdaptiveHzService", "Interaction: forcing Maximum Hz")
            RefreshRateController.applyForceMaximum(this)
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

        // When the service starts, if the adaptive flag is enabled, start at Minimum Hz
        if (isDynamicEnabled()) {
            RefreshRateController.applyForceMinimum(this)
            isHigh = false
        }
    }

    // Is adaptive mode active?
    private fun isDynamicEnabled(): Boolean {
        val prefs = getSharedPreferences("adaptive_hz_prefs", MODE_PRIVATE)
        return prefs.getBoolean("dynamic_enabled", false)
    }
}