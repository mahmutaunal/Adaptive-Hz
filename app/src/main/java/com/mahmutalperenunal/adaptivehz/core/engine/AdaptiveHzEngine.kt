package com.mahmutalperenunal.adaptivehz.core.engine

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mahmutalperenunal.adaptivehz.BuildConfig
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.debug.DebugAccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.debug.DebugEventStore
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning
import com.mahmutalperenunal.adaptivehz.core.input.InteractionSignalProvider

/**
 * Engine that toggles the device between LOW (min Hz) and HIGH (max Hz)
 * based on user interaction signals coming from Accessibility events.
 */
class AdaptiveHzEngine(
    private val context: Context,
    private val strategy: VendorStrategy,
    private val getGlobalMode: () -> AdaptiveHzMode,
    private val shouldIgnorePackage: (String?) -> Boolean,
    private val getAppProfileMode: (String?) -> AppRefreshProfileMode = { AppRefreshProfileMode.DEFAULT },
    private val interactionSignalProvider: InteractionSignalProvider? = null,
    private val tag: String = "AdaptiveHzEngine",
    private val tuning: VendorTuning = strategy.tuning()
) {
    private val handler = Handler(Looper.getMainLooper())

    // Engine lifecycle flag to prevent processing events before start() is called
    private var started = false
    private var isHigh = false

    private var lastHighUptimeMs: Long = 0L
    private var lastCoalescedEventUptimeMs: Long = 0L

    private var isTouchInteracting: Boolean = false

    // Single idle timer: every boost resets it; when it fires we drop to LOW.
    private val dropRunnable = Runnable {
        if (!started) return@Runnable
        if (getGlobalMode() == AdaptiveHzMode.OFF) {
            applySystemControlled(force = true)
            return@Runnable
        }
        applyLow(force = false)
    }

    // Safety net: if HIGH is held too long (missing END/noisy events), force LOW.
    private val safetyRunnable = Runnable {
        if (!started) return@Runnable
        if (getGlobalMode() == AdaptiveHzMode.OFF) {
            applySystemControlled(force = true)
            return@Runnable
        }

        val now = SystemClock.uptimeMillis()
        val heldTooLong = isHigh && (now - lastHighUptimeMs) >= tuning.interactionIdleTimeoutMs
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
        isTouchInteracting = false

        when (getGlobalMode()) {
            AdaptiveHzMode.OFF -> applySystemControlled(force = true)
            else -> applyLow(force = true)
        }

        scheduleSafety()
        Log.d(tag, "Started: ${strategy.name}")
    }

    /** Stops the engine and cancels all scheduled work. */
    fun stop() {
        applySystemControlled(force = true)
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

        val globalMode = getGlobalMode()
        if (globalMode == AdaptiveHzMode.OFF) {
            applySystemControlled(force = true)
            return
        }

        val pkg = event.packageName?.toString()

        DebugEventStore.add(
            DebugAccessibilityEvent(
                timestamp = System.currentTimeMillis(),
                packageName = pkg.orEmpty(),
                eventType = eventTypeName(event.eventType),
                contentChangeTypes = event.contentChangeTypes,
                scrollDeltaX = runCatching { event.scrollDeltaX }.getOrDefault(0),
                scrollDeltaY = runCatching { event.scrollDeltaY }.getOrDefault(0)
            )
        )

        AdaptiveHzPrefs.updateDebugForegroundPackage(context, pkg)
        AdaptiveHzPrefs.updateDebugLastEvent(
            context = context,
            eventName = eventTypeName(event.eventType),
            packageName = pkg
        )

        if (shouldIgnorePackage(pkg)) return
        if (!canProcessForegroundInteraction(pkg)) return
        if (!handleModeDecisionBeforeEvent(pkg)) return

        Log.d(tag, "EVENT ${eventTypeName(event.eventType)} pkg=$pkg isHigh=$isHigh")

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                isTouchInteracting = true
                requestHigh()
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                isTouchInteracting = false
                scheduleDrop(tuning.interactionIdleTimeoutMs)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (tuning.allowScrollBoost) {
                    isTouchInteracting = true
                    requestHigh()
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!tuning.allowContentChangeBoost) return

                val hasRealInput = when {
                    interactionSignalProvider == null -> true
                    !interactionSignalProvider.isAvailable() -> true
                    else -> interactionSignalProvider.wasRecentlyTouched(500L)
                }

                if (hasRealInput && shouldBoostFromContentChange(event)) {
                    if (isHigh) {
                        scheduleDrop(tuning.interactionIdleTimeoutMs)
                    } else {
                        requestHigh()
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(tag, "WINDOW_CONTENT_CHANGED ignored. hasRealInput=$hasRealInput")
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

    /**
     * Resolves global mode and per-app overrides before processing an event.
     */
    private fun handleModeDecisionBeforeEvent(pkg: String?): Boolean {
        val globalMode = getGlobalMode()
        val appMode = getAppProfileMode(pkg)

        if (globalMode == AdaptiveHzMode.OFF) {
            applySystemControlled(force = true)
            return false
        }

        Log.d(tag, "Mode decision pkg=$pkg global=$globalMode app=$appMode")

        return when (appMode) {
            AppRefreshProfileMode.DEFAULT -> {
                when (globalMode) {
                    AdaptiveHzMode.ADAPTIVE -> true
                    AdaptiveHzMode.FORCE_MIN -> {
                        handler.removeCallbacks(dropRunnable)
                        handler.removeCallbacks(safetyRunnable)
                        applyLow(force = true)
                        false
                    }
                    AdaptiveHzMode.FORCE_MAX -> {
                        handler.removeCallbacks(dropRunnable)
                        handler.removeCallbacks(safetyRunnable)
                        applyHigh(force = true)
                        false
                    }
                }
            }

            AppRefreshProfileMode.SYSTEM_CONTROLLED -> {
                handler.removeCallbacks(dropRunnable)
                handler.removeCallbacks(safetyRunnable)
                applySystemControlled(force = true)
                false
            }

            AppRefreshProfileMode.FORCE_MIN -> {
                handler.removeCallbacks(dropRunnable)
                handler.removeCallbacks(safetyRunnable)
                applyLow(force = true)
                false
            }

            AppRefreshProfileMode.FORCE_MAX -> {
                handler.removeCallbacks(dropRunnable)
                handler.removeCallbacks(safetyRunnable)
                applyHigh(force = true)
                false
            }
        }
    }

    private fun applySystemControlled(force: Boolean) {
        if (!force && !isHigh) return

        handler.removeCallbacks(dropRunnable)
        handler.removeCallbacks(safetyRunnable)

        val w = strategy.desiredSystemControlled(context)

        if (w == null) {
            isHigh = false
            AdaptiveHzPrefs.updateDebugLastWrite(
                context = context,
                label = "SYSTEM_CONTROLLED no-op",
                success = true
            )
            Log.d(tag, "SYSTEM_CONTROLLED no-op")
            return
        }

        val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = context,
            label = "SYSTEM ${w.label}",
            success = ok
        )

        if (ok) {
            isHigh = false
            lastHighUptimeMs = 0L
            isTouchInteracting = false
            Log.d(tag, "SYSTEM_CONTROLLED (${w.label}) success")
        } else {
            Log.w(tag, "SYSTEM_CONTROLLED (${w.label}) failed")
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

    /**
     * Extends the current boost across window transitions when needed.
     */
    private fun onWindowStateChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (shouldIgnorePackage(pkg)) return

        // Do not force a fresh boost if we are already LOW and idle.
        // But if the UI is already active, keep the HIGH window alive across screen/dialog transitions.
        if (tuning.extendBoostOnWindowChange && isHigh) {
            scheduleDrop(tuning.interactionIdleTimeoutMs)
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

    /**
     * Requests a HIGH state while coalescing rapid duplicate signals.
     */
    private fun requestHigh() {
        if (shouldCoalesceBoost()) {
            if (isHigh) {
                lastHighUptimeMs = SystemClock.uptimeMillis()
                scheduleDrop(tuning.interactionIdleTimeoutMs)
                scheduleSafety()
            }
            return
        }

        if (isHigh) {
            lastHighUptimeMs = SystemClock.uptimeMillis()
            scheduleDrop(tuning.interactionIdleTimeoutMs)
            scheduleSafety()
            return
        }

        boostNow()
    }

    /**
     * Applies HIGH immediately for adaptive interaction boosts.
     */
    private fun boostNow() {
        if (RefreshRateController.isBatterySaverOn(context)) {
            Log.d(tag, "Battery saver active, forcing LOW")
            applyLow(force = true)
            return
        }

        val w = strategy.desiredHigh(context)
        val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = context,
            label = "HIGH ${w.label}",
            success = ok
        )

        if (ok) {
            isHigh = true
            lastHighUptimeMs = SystemClock.uptimeMillis()
            Log.d(tag, "HIGH (${w.label}) success")
            scheduleSafety()
        } else {
            Log.w(tag, "HIGH (${w.label}) failed")
        }

        scheduleDrop(tuning.interactionIdleTimeoutMs)
    }

    /**
     * Applies HIGH for explicit mode overrides.
     */
    private fun applyHigh(force: Boolean) {
        if (isHigh && !force) return

        if (RefreshRateController.isBatterySaverOn(context)) {
            Log.d(tag, "Battery saver active, forcing LOW")
            applyLow(force = true)
            return
        }

        val w = strategy.desiredHigh(context)
        val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = context,
            label = "HIGH ${w.label}",
            success = ok
        )

        if (ok) {
            isHigh = true
            lastHighUptimeMs = SystemClock.uptimeMillis()
            Log.d(tag, "HIGH (${w.label}) success")
            scheduleSafety()
        } else {
            Log.w(tag, "HIGH (${w.label}) failed")
        }
    }

    /** Resets the idle timer that drops the device back to LOW. */
    private fun scheduleDrop(delayMs: Long = tuning.interactionIdleTimeoutMs) {
        handler.removeCallbacks(dropRunnable)
        handler.postDelayed(dropRunnable, delayMs)
    }

    /**
     * Applies LOW and updates debug write state.
     */
    private fun applyLow(force: Boolean) {
        if (!isHigh && !force) return

        val w = strategy.desiredLow(context)
        val ok = RefreshRateController.writeSetting(context, w.key, w.intValue)

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = context,
            label = "LOW ${w.label}",
            success = ok
        )

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
        handler.postDelayed(safetyRunnable, tuning.interactionIdleTimeoutMs)
    }

    /**
     * Reduces redundant writes caused by bursty accessibility events.
     */
    private fun shouldCoalesceBoost(): Boolean {
        val now = SystemClock.uptimeMillis()

        val delta = now - lastCoalescedEventUptimeMs

        if (delta < tuning.eventCoalescingWindowMs) {
            Log.d(
                tag,
                "Coalesced boost request delta=${delta}ms"
            )
            return true
        }

        lastCoalescedEventUptimeMs = now
        return false
    }

    /**
     * Converts event constants into readable debug names.
     */
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

    /**
     * Logs raw event details for vendor-specific tuning and debugging.
     */
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