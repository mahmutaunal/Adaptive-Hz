package com.mahmutalperenunal.adaptivehz.core.engine

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
    private val interactionIdleTimeoutMs: Long = 3500L,
    private val contentIdleTimeoutMs: Long = 3500L,
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
        if (!canProcessForegroundInteraction(pkg)) return

        val now = SystemClock.uptimeMillis()

        Log.d(tag, "EVENT ${eventTypeName(event.eventType)} pkg=$pkg isHigh=$isHigh")

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                boostNowDebounced(now)
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                // Touch ended: schedule the standard idle drop window (3.5s by default).
                handler.removeCallbacks(dropRunnable)
                handler.postDelayed(dropRunnable, interactionIdleTimeoutMs)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                boostNowDebounced(now)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Scroll can be noisy on some ROMs; only treat it as interaction if it looks real.
                if (isRealScroll(event)) boostNowDebounced(now)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (shouldBoostFromContentChange(event, pkg)) {
                    if (isHigh) {
                        Log.d(tag, "Content change accepted -> extending HIGH hold")
                        scheduleDrop(contentIdleTimeoutMs)
                    } else {
                        boostNowDebounced(now)
                        scheduleDrop(contentIdleTimeoutMs)
                    }
                }
            }

            else -> Unit
        }
    }

    /**
     * Best-effort filter to reduce false boosts.
     * Some apps emit TYPE_VIEW_SCROLLED during content updates without user touch.
     */
    private fun isRealScroll(e: AccessibilityEvent): Boolean {
        val scrollDeltaDetected = runCatching { e.scrollDeltaY != 0 || e.scrollDeltaX != 0 }.getOrDefault(false)
        if (scrollDeltaDetected) return true

        val scrollX = runCatching { e.scrollX }.getOrDefault(0)
        val scrollY = runCatching { e.scrollY }.getOrDefault(0)
        if (scrollX != 0 || scrollY != 0) return true

        val fromIndex = runCatching { e.fromIndex }.getOrDefault(-1)
        val toIndex = runCatching { e.toIndex }.getOrDefault(-1)
        return fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex
    }

    /**
     * Only react while the device is actually interactive and unlocked.
     * This prevents false boosts from AOD / lock screen clock, notification, and location updates.
     */
    private fun canProcessForegroundInteraction(pkg: String?): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        val isInteractive = powerManager?.isInteractive ?: true
        val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false

        if (!isInteractive) return false
        if (isKeyguardLocked) {
            Log.d(tag, "Ignoring event while keyguard is locked. pkg=$pkg")
            return false
        }

        return true
    }

    /**
     * WINDOW_CONTENT_CHANGED is treated as a secondary fallback signal.
     * Keep it stricter than scroll/click so passive UI updates do not hold HIGH unnecessarily.
     */
    private fun shouldBoostFromContentChange(e: AccessibilityEvent, pkg: String?): Boolean {
        val hasMotionLikeSignal = isRealScroll(e)
        if (hasMotionLikeSignal) return true

        val changeTypes = e.contentChangeTypes
        val isMeaningfulChange =
            changeTypes == 0 ||
                (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0 ||
                (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0 ||
                (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) != 0

        if (!isMeaningfulChange) return false

        val className = e.className?.toString().orEmpty()
        val sourceText = e.text.joinToString(" ")

        val looksLikePassiveClockUpdate =
            pkg == "com.android.systemui" &&
                    className.contains("TextView", ignoreCase = true) &&
                    sourceText.length in 1..8

        return !looksLikePassiveClockUpdate
    }

    /** Rate-limit boosts to avoid spamming secure settings writes. */
    private fun boostNowDebounced(now: Long) {
        if ((now - lastBoostUptimeMs) < minBoostIntervalMs) {
            // Even if we skip to write, extend the idle window to feel responsive.
            scheduleDrop(interactionIdleTimeoutMs)
            return
        }
        lastBoostUptimeMs = now
        boostNow()
    }

    private fun boostNow() {
        if (RefreshRateController.isBatterySaverOn(context)) {
            Log.d(tag, "Battery saver active, forcing LOW")
            applyLow(force = true)
            return
        }

        val w = strategy.desiredHigh(context)
        val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)

        if (ok) {
            isHigh = true
            lastHighUptimeMs = SystemClock.uptimeMillis()
            Log.d(tag, "HIGH (${w.label}) success")
            scheduleSafety()
        } else {
            Log.w(tag, "HIGH (${w.label}) failed")
        }

        scheduleDrop(interactionIdleTimeoutMs)
    }

    /** Resets the idle timer that drops the device back to LOW. */
    private fun scheduleDrop(delayMs: Long = interactionIdleTimeoutMs) {
        handler.removeCallbacks(dropRunnable)
        handler.postDelayed(dropRunnable, delayMs)
    }

    private fun applyLow(force: Boolean) {
        if (!isHigh && !force) return
        val w = strategy.desiredLow(context)
        val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)
        if (ok) {
            isHigh = false
            Log.d(tag, "LOW (${w.label}) success")
        } else {
            Log.w(tag, "LOW (${w.label}) failed")
        }
    }

    /** Schedules the safety check that prevents staying on HIGH indefinitely. */
    private fun scheduleSafety() {
        handler.removeCallbacks(safetyRunnable)
        handler.postDelayed(safetyRunnable, maxHighHoldMs + 250L)
    }

    private fun eventTypeName(type: Int): String {
        return when (type) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> "TOUCH_START"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> "TOUCH_END"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            else -> type.toString()
        }
    }
}