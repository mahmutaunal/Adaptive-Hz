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
    private val interactionIdleTimeoutMs: Long = 2000L
) {
    private val handler = Handler(Looper.getMainLooper())

    // Engine lifecycle flag to prevent processing events before start() is called
    private var started = false
    private var isHigh = false

    private var lastHighUptimeMs: Long = 0L
    private var lastBoostUptimeMs: Long = 0L

    private var isTouchInteracting: Boolean = false

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
        val heldTooLong = isHigh && (now - lastHighUptimeMs) >= interactionIdleTimeoutMs
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
        isTouchInteracting = false

        // Start safe at LOW
        applyLow(force = true)
        scheduleSafety()
        Log.d(tag, "Started: ${strategy.name}")
    }

    /** Stops the engine and cancels all scheduled work. */
    fun stop() {
        if (isHigh) applyLow(force = true)
        started = false
        isTouchInteracting = false
        handler.removeCallbacksAndMessages(null)
        Log.d(tag, "Stopped")
    }

    /**
     * Feed Accessibility events into the engine.
     * The service should pre-filter noisy event types and packages.
     */
    fun onEvent(event: AccessibilityEvent) {
        logEventDetails(event)

        if (!started) return
        if (!isEnabled()) return

        val pkg = event.packageName?.toString()
        if (shouldIgnorePackage(pkg)) return
        if (!canProcessForegroundInteraction(pkg)) return

        Log.d(tag, "EVENT ${eventTypeName(event.eventType)} pkg=$pkg isHigh=$isHigh")

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                isTouchInteracting = true
                requestHigh()
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                isTouchInteracting = false
                scheduleDrop(interactionIdleTimeoutMs)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                isTouchInteracting = true
                requestHigh()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (shouldBoostFromContentChange(event)) {
                    if (isHigh) {
                        scheduleDrop(interactionIdleTimeoutMs)
                    } else {
                        requestHigh()
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                onWindowStateChanged(event)
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> {
                // Explicitly ignore known noisy events.
            }

            else -> Unit
        }
    }

    private fun shouldBoostFromContentChange(e: AccessibilityEvent): Boolean {
        // Main interaction signal on many OneUI / HyperOS devices.
        // Keep this permissive so real touches are not missed.
        if (isRealScroll(e)) return true

        val changeTypes = e.contentChangeTypes
        if (changeTypes == 0) return true
        if ((changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0) return true
        if ((changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0) return true
        if ((changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) != 0) return true
        if ((changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) != 0) return true

        return false
    }

    private fun onWindowStateChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (shouldIgnorePackage(pkg)) return

        // Do not force a fresh boost if we are already LOW and idle.
        // But if the UI is already active, keep the HIGH window alive across screen/dialog transitions.
        if (isHigh) {
            scheduleDrop(interactionIdleTimeoutMs)
        }
    }

    /**
     * Best-effort filter to reduce false boosts.
     * Some apps emit TYPE_VIEW_SCROLLED during content updates without user touch.
     */
    private fun isRealScroll(e: AccessibilityEvent): Boolean {
        val deltaX = runCatching { e.scrollDeltaX }.getOrDefault(0)
        val deltaY = runCatching { e.scrollDeltaY }.getOrDefault(0)
        if (deltaX != 0 || deltaY != 0) return true

        val fromIndex = runCatching { e.fromIndex }.getOrDefault(-1)
        val toIndex = runCatching { e.toIndex }.getOrDefault(-1)
        if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) return true

        val scrollX = runCatching { e.scrollX }.getOrDefault(-1)
        val scrollY = runCatching { e.scrollY }.getOrDefault(-1)
        val maxScrollX = runCatching { e.maxScrollX }.getOrDefault(-1)
        val maxScrollY = runCatching { e.maxScrollY }.getOrDefault(-1)
        return (scrollX > 0 || scrollY > 0) || (maxScrollX > 0 || maxScrollY > 0)
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

    private fun requestHigh() {
        if (isHigh) {
            lastHighUptimeMs = SystemClock.uptimeMillis()
            scheduleDrop(interactionIdleTimeoutMs)
            scheduleSafety()
            return
        }

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
        handler.postDelayed(safetyRunnable, interactionIdleTimeoutMs)
    }

    private fun eventTypeName(type: Int): String {
        return when (type) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> "TOUCH_START"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> "TOUCH_END"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFICATION_STATE_CHANGED"
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> "ANNOUNCEMENT"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> "VIEW_ACCESSIBILITY_FOCUS_CLEARED"
            else -> type.toString()
        }
    }

    private fun logEventDetails(event: AccessibilityEvent) {
        val source = event.source

        val actions = try {
            source?.actionList
                ?.joinToString(prefix = "[", postfix = "]") { it.id.toString() }
                .orEmpty()
        } catch (_: Throwable) {
            "[]"
        }

        val msg = buildString {
            append("type=").append(eventTypeName(event.eventType))
            append(" pkg=").append(event.packageName)
            append(" cls=").append(event.className)
            append(" changeTypes=").append(event.contentChangeTypes)

            append(" from=").append(runCatching { event.fromIndex }.getOrDefault(-1))
            append(" to=").append(runCatching { event.toIndex }.getOrDefault(-1))

            append(" scrollX=").append(runCatching { event.scrollX }.getOrDefault(-1))
            append(" scrollY=").append(runCatching { event.scrollY }.getOrDefault(-1))
            append(" maxScrollX=").append(runCatching { event.maxScrollX }.getOrDefault(-1))
            append(" maxScrollY=").append(runCatching { event.maxScrollY }.getOrDefault(-1))

            append(" deltaX=").append(runCatching { event.scrollDeltaX }.getOrDefault(0))
            append(" deltaY=").append(runCatching { event.scrollDeltaY }.getOrDefault(0))

            append(" scrollable=").append(
                runCatching { source?.isScrollable ?: false }.getOrDefault(false)
            )

            append(" actions=").append(actions)
            append(" touchActive=").append(isTouchInteracting)
        }

        Log.d(tag, msg)
    }
}