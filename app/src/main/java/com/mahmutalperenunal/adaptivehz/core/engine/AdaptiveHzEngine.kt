package com.mahmutalperenunal.adaptivehz.core.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.engine.strategy.VendorStrategy

/**
 * Engine that toggles the device between LOW (min Hz) and HIGH (max Hz)
 * based on user interaction signals coming from Accessibility events.
 */
class AdaptiveHzEngine(
    private val context: Context,
    private val strategy: VendorStrategy,
    private val isEnabled: () -> Boolean,
    private val shouldIgnorePackage: (String?) -> Boolean,
    private val tag: String = "AdaptiveHzEngine",

    // Core UX timing (keep these stable across devices)
    private val idleTimeoutMs: Long = 3500L,
    private val maxHighHoldMs: Long = 4000L,

    // Spam/noise protection (tune carefully)
    private val minBoostIntervalMs: Long = 90L
) {
    private val handler = Handler(Looper.getMainLooper())

    private var started = false
    private var isHigh = false

    private var lastHighUptimeMs: Long = 0L
    private var lastBoostUptimeMs: Long = 0L

    // Single idle timer: every boost resets it; when it fires we drop to LOW.
    private val dropRunnable = Runnable {
        if (!started) return@Runnable
        if (!isEnabled()) return@Runnable
        applyLow(force = false)
    }

    // Safety net: if HIGH is held too long (missing END/noisy events), force LOW.
    private val safetyRunnable = Runnable {
        if (!started) return@Runnable
        if (!isEnabled()) return@Runnable

        val now = SystemClock.uptimeMillis()
        val heldTooLong = isHigh && (now - lastHighUptimeMs) >= maxHighHoldMs
        if (heldTooLong) {
            Log.w(tag, "Safety drop -> LOW")
            applyLow(force = true)
        }
    }

    /** Starts the engine in a safe LOW state. */
    fun start() {
        if (started) return
        started = true
        isHigh = false
        lastHighUptimeMs = 0L
        lastBoostUptimeMs = 0L

        // Start safe at LOW
        applyLow(force = true)
        scheduleSafety()
        Log.d(tag, "Started: ${strategy.name}")
    }

    /** Stops the engine and cancels all scheduled work. */
    fun stop() {
        started = false
        handler.removeCallbacksAndMessages(null)
        Log.d(tag, "Stopped")
    }

    /**
     * Feed Accessibility events into the engine.
     * The service should pre-filter noisy event types and packages.
     */
    fun onEvent(event: AccessibilityEvent) {
        if (!started) return
        if (!isEnabled()) return

        val pkg = event.packageName?.toString()
        if (shouldIgnorePackage(pkg)) return

        val now = SystemClock.uptimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                boostNowDebounced(now)
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                // Touch ended: schedule the standard idle drop window (3.5s by default).
                handler.postDelayed(dropRunnable, idleTimeoutMs)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                boostNowDebounced(now)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Scroll can be noisy on some ROMs; only treat it as interaction if it looks real.
                if (isRealScroll(event)) boostNowDebounced(now)
            }

            else -> Unit
        }
    }

    /**
     * Best-effort filter to reduce false boosts.
     * Some apps emit TYPE_VIEW_SCROLLED during content updates without user touch.
     */
    private fun isRealScroll(e: AccessibilityEvent): Boolean {
        val scrollX = runCatching { e.scrollX }.getOrDefault(0)
        val scrollY = runCatching { e.scrollY }.getOrDefault(0)

        if (scrollX != 0 || scrollY != 0) return true

        val itemCount = runCatching { e.itemCount }.getOrDefault(0)
        val fromIndex = runCatching { e.fromIndex }.getOrDefault(-1)
        val toIndex = runCatching { e.toIndex }.getOrDefault(-1)

        val hasIndexInfo = itemCount > 0 && fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex
        return hasIndexInfo
    }

    /** Rate-limit boosts to avoid spamming secure settings writes. */
    private fun boostNowDebounced(now: Long) {
        if ((now - lastBoostUptimeMs) < minBoostIntervalMs) {
            // Even if we skip to write, extend the idle window to feel responsive.
            scheduleDrop()
            return
        }
        lastBoostUptimeMs = now
        boostNow()
    }

    private fun boostNow() {
        if (!isHigh) {
            val w = strategy.desiredHigh(context)
            val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)
            isHigh = ok
            if (ok) {
                lastHighUptimeMs = SystemClock.uptimeMillis()
                Log.d(tag, "HIGH (${w.label})")
                scheduleSafety()
            } else {
                Log.w(tag, "HIGH write failed (${w.label})")
            }
        }
        scheduleDrop()
    }

    /** Resets the idle timer that drops the device back to LOW. */
    private fun scheduleDrop() {
        handler.removeCallbacks(dropRunnable)
        handler.postDelayed(dropRunnable, idleTimeoutMs)
    }

    private fun applyLow(force: Boolean) {
        if (!isHigh && !force) return
        val w = strategy.desiredLow(context)
        val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)
        if (ok) {
            isHigh = false
            Log.d(tag, "LOW (${w.label})")
        } else {
            Log.w(tag, "LOW write failed (${w.label})")
        }
    }

    /** Schedules the safety check that prevents staying on HIGH indefinitely. */
    private fun scheduleSafety() {
        handler.removeCallbacks(safetyRunnable)
        handler.postDelayed(safetyRunnable, maxHighHoldMs + 250L)
    }
}