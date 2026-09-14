package com.mahmutalperenunal.adaptivehz.core.engine

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mahmutalperenunal.adaptivehz.BuildConfig
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.debug.DebugAccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.debug.DebugEventStore
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppliedRefreshState
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppliedRefreshStateTracker
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.RefreshRateApplyResult
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.CustomRateSelectionResolver
import com.mahmutalperenunal.adaptivehz.core.engine.model.CustomRateSelectionState
import com.mahmutalperenunal.adaptivehz.core.engine.model.EffectiveRefreshPolicy
import com.mahmutalperenunal.adaptivehz.core.engine.model.EffectiveRefreshPolicyResolver
import com.mahmutalperenunal.adaptivehz.core.engine.model.RefreshTarget
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.system.CustomRefreshRateController
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateOperationCoordinator
import com.mahmutalperenunal.adaptivehz.core.system.isSuccess
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning
import com.mahmutalperenunal.adaptivehz.core.engine.model.isOperationalSuccess
import com.mahmutalperenunal.adaptivehz.core.input.InteractionSignalProvider

/**
 * Engine that toggles the device between LOW (min Hz) and HIGH (max Hz)
 * based on user interaction signals coming from Accessibility events.
 */
class AdaptiveHzEngine(
    context: Context,
    private val strategy: VendorStrategy,
    private val getGlobalMode: () -> AdaptiveHzMode,
    private val shouldIgnorePackage: (String?) -> Boolean,
    private val getAppProfileMode: (String?) -> AppRefreshProfileMode = { AppRefreshProfileMode.DEFAULT },
    private val interactionSignalProvider: InteractionSignalProvider? = null,
    private val getInteractionDropDelayMs: () -> Long = {
        AdaptiveHzPrefs.getInteractionDropDelayMs(context.applicationContext)
    },
    private val tag: String = "AdaptiveHzEngine",
    private val tuning: VendorTuning = strategy.tuning()
    ) {
    private val appContext = context.applicationContext
    private val workerThread = HandlerThread("AdaptiveHzRefreshEngine").apply { start() }
    private val handler = Handler(workerThread.looper)
    private val appliedStateTracker = AppliedRefreshStateTracker()

    // Engine lifecycle flag to prevent processing events before start() is called
    @Volatile
    private var started = false
    private var isHigh = false

    private var lastHighUptimeMs: Long = 0L
    private var isTouchInteracting: Boolean = false
    private var activePackageName: String? = null

    // Single idle timer: every boost resets it; when it fires we drop to LOW.
    private val dropRunnable = Runnable {
        if (!started) return@Runnable
        when (resolveEffectivePolicy(activePackageName)) {
            EffectiveRefreshPolicy.SYSTEM_CONTROLLED -> {
                applySystemControlled(force = false)
                return@Runnable
            }
            EffectiveRefreshPolicy.FORCE_MINIMUM -> {
                applyForceMinimum(force = false)
                return@Runnable
            }
            EffectiveRefreshPolicy.FORCE_MAXIMUM -> {
                applyForceMaximum(force = false)
                return@Runnable
            }
            EffectiveRefreshPolicy.ADAPTIVE -> Unit
        }
        if (isPhysicalTouchActive()) {
            scheduleDrop(ACTIVE_TOUCH_RECHECK_MS)
            return@Runnable
        }
        applyLow(force = false)
    }

    // Safety net: if HIGH is held too long (missing END/noisy events), force LOW.
    private val safetyRunnable = Runnable {
        if (!started) return@Runnable
        when (resolveEffectivePolicy(activePackageName)) {
            EffectiveRefreshPolicy.SYSTEM_CONTROLLED -> {
                applySystemControlled(force = false)
                return@Runnable
            }
            EffectiveRefreshPolicy.FORCE_MINIMUM -> {
                applyForceMinimum(force = false)
                return@Runnable
            }
            EffectiveRefreshPolicy.FORCE_MAXIMUM -> {
                applyForceMaximum(force = false)
                return@Runnable
            }
            EffectiveRefreshPolicy.ADAPTIVE -> Unit
        }

        val now = SystemClock.uptimeMillis()
        val safetyTimeoutMs = effectiveDropDelayMs() + tuning.interactionIdleTimeoutMs
        val heldTooLong = isHigh && (now - lastHighUptimeMs) >= safetyTimeoutMs
        if (heldTooLong) {
            if (isPhysicalTouchActive()) {
                scheduleSafety()
                return@Runnable
            }
            Log.w(tag, "Safety drop -> LOW")
            applyLow(force = true)
        }
    }

    /** Starts the engine in a safe LOW state. */
    fun start() {
        if (started) return
        started = true
        handler.post {
            if (!started) return@post
            isHigh = false
            lastHighUptimeMs = 0L
            isTouchInteracting = false
            appliedStateTracker.invalidate()

            when (resolveEffectivePolicy(activePackageName)) {
                EffectiveRefreshPolicy.SYSTEM_CONTROLLED -> applySystemControlled(force = true)
                EffectiveRefreshPolicy.ADAPTIVE -> {
                    applyLow(force = true)
                    scheduleSafety()
                }
                EffectiveRefreshPolicy.FORCE_MINIMUM -> applyForceMinimum(force = true)
                EffectiveRefreshPolicy.FORCE_MAXIMUM -> applyForceMaximum(force = true)
            }
            Log.d(tag, "Started on ${Thread.currentThread().name}: ${strategy.name}")
        }
    }

    /** Stops the engine and cancels all scheduled work. */
    fun stop(restoreSystemControlled: Boolean = false) {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        handler.post {
            isTouchInteracting = false
            if (restoreSystemControlled) applySystemControlled(force = true)
            appliedStateTracker.invalidate()
            Log.d(tag, "Stopped. restoreSystemControlled=$restoreSystemControlled")
            workerThread.quitSafely()
        }
    }

    private fun applyWrite(
        write: SettingWrite,
        policy: RefreshRateController.RefreshWritePolicy =
            RefreshRateController.RefreshWritePolicy.NORMAL,
        genericRefreshRateHz: Int = write.intValue
    ): RefreshRateApplyResult {
        return RefreshRateController.applySetting(
            context = appContext,
            write = write,
            policy = policy,
            genericRefreshRateHz = genericRefreshRateHz
        )
    }

    /**
     * Re-applies the currently selected Adaptive Hz mode.
     *
     * Useful when system-level conditions change, such as Battery Saver being toggled.
     */
    fun reapplyCurrentMode(reason: String) {
        if (!started) return
        handler.post {
            if (!started) return@post
            Log.d(tag, "Re-applying current mode. reason=$reason")

            handler.removeCallbacks(dropRunnable)
            handler.removeCallbacks(safetyRunnable)

            when (resolveEffectivePolicy(activePackageName)) {
                EffectiveRefreshPolicy.SYSTEM_CONTROLLED -> applySystemControlled(force = true)
                EffectiveRefreshPolicy.ADAPTIVE -> {
                    applyLow(force = true)
                    scheduleSafety()
                }
                EffectiveRefreshPolicy.FORCE_MINIMUM -> applyForceMinimum(force = true)
                EffectiveRefreshPolicy.FORCE_MAXIMUM -> applyForceMaximum(force = true)
            }
        }
    }

    /**
     * Feed Accessibility events into the engine.
     * The service should pre-filter noisy event types and packages.
     */
    fun onEvent(event: AccessibilityEvent) {
        if (!started) return
        val eventSnapshot = snapshotEvent(event)
        handler.post {
            processEvent(eventSnapshot)
        }
    }

    private fun processEvent(event: EngineAccessibilityEvent) {
        logEventDetails(event)

        if (!started) return

        val globalMode = getGlobalMode()
        if (globalMode == AdaptiveHzMode.OFF) {
            applySystemControlled(force = false)
            return
        }

        val pkg = event.packageName

        DebugEventStore.add(
            DebugAccessibilityEvent(
                timestamp = System.currentTimeMillis(),
                packageName = pkg.orEmpty(),
                eventType = eventTypeName(event.eventType),
                contentChangeTypes = event.contentChangeTypes,
                scrollDeltaX = event.scrollDeltaX,
                scrollDeltaY = event.scrollDeltaY
            )
        )

        AdaptiveHzPrefs.updateDebugForegroundPackage(appContext, pkg)
        AdaptiveHzPrefs.updateDebugLastEvent(
            context = appContext,
            eventName = eventTypeName(event.eventType),
            packageName = pkg
        )

        if (shouldIgnorePackage(pkg)) return
        if (!canProcessForegroundInteraction(pkg)) return
        val packageChanged = activePackageName != pkg
        activePackageName = pkg
        if (!handleModeDecisionBeforeEvent(pkg, packageChanged)) return

        Log.d(tag, "EVENT ${eventTypeName(event.eventType)} pkg=$pkg isHigh=$isHigh")

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                isTouchInteracting = true
                requestHigh()
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                isTouchInteracting = false
                scheduleDrop(getInteractionDropDelayMs())
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (tuning.allowScrollBoost) {
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
                        scheduleDrop(getInteractionDropDelayMs())
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
     * Low-level Shizuku input callbacks are the authoritative interaction signal on devices
     * where an app does not emit a useful AccessibilityEvent for every gesture.
     */
    fun onRawTouchDown() {
        handler.post { handleRawTouchSignal(isActive = true) }
    }

    fun onRawTouchMove() {
        handler.post { handleRawTouchSignal(isActive = true) }
    }

    fun onRawTouchUp() {
        handler.post { handleRawTouchSignal(isActive = false) }
    }

    private fun handleRawTouchSignal(isActive: Boolean) {
        if (!started) return

        isTouchInteracting = isActive
        if (!isActive) {
            if (isHigh && usesAdaptiveInteraction(activePackageName)) {
                scheduleDrop(getInteractionDropDelayMs())
            }
            return
        }

        if (!canProcessForegroundInteraction(activePackageName)) return
        if (!handleModeDecisionBeforeEvent(activePackageName, packageChanged = false)) return
        requestHigh()
    }

    /**
     * Resolves global mode and per-app overrides before processing an event.
     */
    private fun handleModeDecisionBeforeEvent(
        pkg: String?,
        packageChanged: Boolean
    ): Boolean {
        val globalMode = getGlobalMode()
        val appMode = getAppProfileMode(pkg)

        if (globalMode == AdaptiveHzMode.OFF) {
            applySystemControlled(force = true)
            return false
        }

        Log.d(tag, "Mode decision pkg=$pkg global=$globalMode app=$appMode")

        return when (EffectiveRefreshPolicyResolver.resolve(globalMode, appMode)) {
            EffectiveRefreshPolicy.ADAPTIVE -> {
                if (packageChanged) {
                    handler.removeCallbacks(dropRunnable)
                    handler.removeCallbacks(safetyRunnable)
                    applyLow(force = false)
                }
                true
            }
            EffectiveRefreshPolicy.SYSTEM_CONTROLLED -> {
                handler.removeCallbacks(dropRunnable)
                handler.removeCallbacks(safetyRunnable)
                applySystemControlled(force = false)
                false
            }
            EffectiveRefreshPolicy.FORCE_MINIMUM -> {
                handler.removeCallbacks(dropRunnable)
                handler.removeCallbacks(safetyRunnable)
                applyForceMinimum(force = false)
                false
            }
            EffectiveRefreshPolicy.FORCE_MAXIMUM -> {
                handler.removeCallbacks(dropRunnable)
                handler.removeCallbacks(safetyRunnable)
                applyForceMaximum(force = false)
                false
            }
        }
    }

    private fun applySystemControlled(force: Boolean) {
        val target = appliedState(RefreshTarget.SYSTEM_CONTROLLED, customRateHz = null)
        if (!appliedStateTracker.shouldApply(target, force)) return

        RefreshRateOperationCoordinator.run {
            if (started && !isTargetCurrent(target)) return@run
            applySystemControlledLocked(target)
        }
    }

    private fun applySystemControlledLocked(target: AppliedRefreshState) {

        handler.removeCallbacks(dropRunnable)
        handler.removeCallbacks(safetyRunnable)

        if (!CustomRefreshRateController.restoreOriginal(appContext)) {
            Log.w(tag, "Unable to restore custom refresh-rate snapshot before SYSTEM_CONTROLLED")
            return
        }

        val w = strategy.desiredSystemControlled(appContext)

        if (w == null) {
            isHigh = false
            AdaptiveHzPrefs.updateDebugLastWrite(
                context = appContext,
                label = "SYSTEM_CONTROLLED no-op",
                success = true
            )
            Log.d(tag, "SYSTEM_CONTROLLED no-op")
            appliedStateTracker.markApplied(target)
            return
        }

        val result = applyWrite(w)
        val ok = result.isOperationalSuccess

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = appContext,
            label = "SYSTEM ${w.label} / ${result::class.simpleName}",
            success = ok
        )

        if (ok) {
            isHigh = false
            lastHighUptimeMs = 0L
            isTouchInteracting = false
            appliedStateTracker.markApplied(target)
            Log.d(tag, "SYSTEM_CONTROLLED (${w.label}) success")
        } else {
            Log.w(tag, "SYSTEM_CONTROLLED (${w.label}) failed")
        }
    }

    private fun shouldBoostFromContentChange(e: EngineAccessibilityEvent): Boolean {
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
    private fun onWindowStateChanged(event: EngineAccessibilityEvent) {
        val pkg = event.packageName
        if (shouldIgnorePackage(pkg)) return

        // Do not force a fresh boost if we are already LOW and idle.
        // But if the UI is already active, keep the HIGH window alive across screen/dialog transitions.
        if (tuning.extendBoostOnWindowChange && isHigh) {
            scheduleDrop(getInteractionDropDelayMs())
        }
    }

    /**
     * Best-effort filter to reduce false boosts.
     * Some apps emit TYPE_VIEW_SCROLLED during content updates without user touch.
     */
    private fun isRealScroll(e: EngineAccessibilityEvent): Boolean {
        val deltaX = e.scrollDeltaX
        val deltaY = e.scrollDeltaY
        if (deltaX != 0 || deltaY != 0) return true

        val fromIndex = e.fromIndex
        val toIndex = e.toIndex
        if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) return true

        val scrollX = e.scrollX
        val scrollY = e.scrollY
        val maxScrollX = e.maxScrollX
        val maxScrollY = e.maxScrollY
        return (scrollX > 0 || scrollY > 0) || (maxScrollX > 0 || maxScrollY > 0)
    }

    /**
     * Only react while the device is actually interactive and unlocked.
     * This prevents false boosts from AOD / lock screen clock, notification, and location updates.
     */
    private fun canProcessForegroundInteraction(pkg: String?): Boolean {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

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
        val target = appliedState(
            RefreshTarget.ADAPTIVE_HIGH,
            resolveCustomHighRate(activePackageName)
        )
        if (isHigh && appliedStateTracker.isApplied(target)) {
            lastHighUptimeMs = SystemClock.uptimeMillis()
            scheduleDrop(getInteractionDropDelayMs())
            scheduleSafety()
            return
        }

        // A first signal must never be coalesced while LOW. Missing that edge leaves the
        // complete gesture at the configured minimum rate.
        boostNow()
    }

    private fun usesAdaptiveInteraction(packageName: String?): Boolean {
        return resolveEffectivePolicy(packageName) == EffectiveRefreshPolicy.ADAPTIVE
    }

    private fun resolveEffectivePolicy(packageName: String?): EffectiveRefreshPolicy {
        return EffectiveRefreshPolicyResolver.resolve(
            globalMode = getGlobalMode(),
            appMode = getAppProfileMode(packageName)
        )
    }

    private fun isPhysicalTouchActive(): Boolean {
        val provider = interactionSignalProvider
        return if (provider != null && provider.isAvailable()) {
            provider.isTouchActive()
        } else {
            isTouchInteracting
        }
    }

    /**
     * Applies HIGH immediately for adaptive interaction boosts.
     */
    private fun boostNow() {
        if (shouldRespectBatterySaverRefreshLimit()) {
            Log.d(tag, "Battery saver active and override disabled, forcing LOW")
            applyLow(force = true)
            return
        }

        val w = strategy.desiredHigh(appContext)
        val customRate = resolveCustomHighRate(activePackageName)
        val target = appliedState(RefreshTarget.ADAPTIVE_HIGH, customRate)
        val ok = applyCustomOrLegacy(target, customRate) { writeHighRefreshSetting(w) }

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = appContext,
            label = customRate?.let { "HIGH custom ${it}Hz" } ?: "HIGH ${w.label}",
            success = ok
        )

        if (ok) {
            isHigh = true
            lastHighUptimeMs = SystemClock.uptimeMillis()
            appliedStateTracker.markApplied(target)
            Log.d(tag, "HIGH (${w.label}) success")
            scheduleSafety()
        } else {
            Log.w(tag, "HIGH (${w.label}) failed")
        }

        scheduleDrop(getInteractionDropDelayMs())
    }

    /**
     * Applies a persistent vendor-specific minimum refresh-rate mode.
     *
     * This is intentionally separate from adaptive LOW because some vendors
     * use a special policy value for persistent minimum. HyperOS 1 uses
     * user_refresh_rate=0 while adaptive LOW continues to use the physical Hz.
     */
    private fun applyForceMinimum(force: Boolean) {
        val w = strategy.desiredForceMinimum(appContext)
        val customRate = resolveCustomMinimumRate(activePackageName)
        val target = appliedState(RefreshTarget.FORCE_MINIMUM, customRate)
        if (!appliedStateTracker.shouldApply(target, force)) return
        val ok = applyCustomOrLegacy(target, customRate) { writeLowRefreshSetting(w) }

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = appContext,
            label = customRate?.let { "FORCE_MIN custom ${it}Hz" } ?: "FORCE_MIN ${w.label}",
            success = ok
        )

        if (ok) {
            isHigh = false
            appliedStateTracker.markApplied(target)
            handler.removeCallbacks(dropRunnable)
            handler.removeCallbacks(safetyRunnable)
            Log.d(tag, "FORCE_MIN (${w.label}) success")
        } else {
            Log.w(tag, "FORCE_MIN (${w.label}) failed")
        }
    }

    /**
     * Applies a persistent vendor-specific maximum refresh-rate mode.
     *
     * This is intentionally separate from adaptive HIGH:
     *
     * - Adaptive HIGH uses the physical maximum Hz.
     * - Some vendors use a special policy value for persistent maximum.
     *
     * HyperOS 1 is one such case and uses user_refresh_rate=1.
     */
    private fun applyForceMaximum(force: Boolean) {
        if (shouldRespectBatterySaverRefreshLimit()) {
            Log.d(tag, "Battery saver active and override disabled, forcing LOW")
            applyLow(force = true)
            return
        }

        val w = strategy.desiredForceMaximum(appContext)
        val customRate = resolveCustomMaximumRate(activePackageName)
        val target = appliedState(RefreshTarget.FORCE_MAXIMUM, customRate)
        if (!appliedStateTracker.shouldApply(target, force)) return
        val ok = applyCustomOrLegacy(target, customRate) { writeHighRefreshSetting(w) }

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = appContext,
            label = customRate?.let { "FORCE_MAX custom ${it}Hz" } ?: "FORCE_MAX ${w.label}",
            success = ok
        )

        if (ok) {
            isHigh = true
            lastHighUptimeMs = SystemClock.uptimeMillis()
            appliedStateTracker.markApplied(target)

            // Persistent maximum mode must not be followed by an adaptive drop.
            handler.removeCallbacks(dropRunnable)
            handler.removeCallbacks(safetyRunnable)

            Log.d(tag, "FORCE_MAX (${w.label}) success")
        } else {
            Log.w(tag, "FORCE_MAX (${w.label}) failed")
        }
    }

    /** Resets the idle timer that drops the device back to LOW. */
    private fun scheduleDrop(delayMs: Long) {
        handler.removeCallbacks(dropRunnable)
        handler.postDelayed(dropRunnable, delayMs)
    }

    /**
     * Applies LOW and updates debug write state.
     */
    private fun applyLow(force: Boolean) {
        val w = strategy.desiredLow(appContext)
        val customRate = resolveCustomLowRate(activePackageName)
        val target = appliedState(RefreshTarget.ADAPTIVE_LOW, customRate)
        if (!appliedStateTracker.shouldApply(target, force)) return
        val ok = applyCustomOrLegacy(target, customRate) { writeLowRefreshSetting(w) }

        AdaptiveHzPrefs.updateDebugLastWrite(
            context = appContext,
            label = customRate?.let { "LOW custom ${it}Hz" } ?: "LOW ${w.label}",
            success = ok
        )

        if (ok) {
            isHigh = false
            appliedStateTracker.markApplied(target)
            Log.d(tag, "LOW (${w.label}) success")
        } else {
            Log.w(tag, "LOW (${w.label}) failed")
        }
    }

    private fun writeHighRefreshSetting(
        write: SettingWrite
    ): Boolean {
        if (!CustomRefreshRateController.restoreOriginal(appContext)) return false
        val policy = if (shouldUseBatterySaverOverrideWrites()) {
            RefreshRateController.RefreshWritePolicy.BATTERY_SAVER_OVERRIDE_HIGH
        } else {
            RefreshRateController.RefreshWritePolicy.NORMAL
        }

        val (_, maxHz) = RefreshRateController.resolveDisplayMinMax(appContext)

        val result = applyWrite(
            write = write,
            policy = policy,
            genericRefreshRateHz = maxHz
        )

        Log.d(
            tag,
            "High write result=${result::class.simpleName} write=${write.label}"
        )

        return result.isOperationalSuccess
    }

    private fun writeLowRefreshSetting(
        write: SettingWrite
    ): Boolean {
        if (!CustomRefreshRateController.restoreOriginal(appContext)) return false
        val policy = if (shouldUseBatterySaverOverrideWrites()) {
            RefreshRateController.RefreshWritePolicy.BATTERY_SAVER_OVERRIDE_LOW
        } else {
            RefreshRateController.RefreshWritePolicy.NORMAL
        }

        val result = applyWrite(
            write = write,
            policy = policy,
            genericRefreshRateHz = write.intValue
        )

        Log.d(
            tag,
            "Low write result=${result::class.simpleName} write=${write.label}"
        )

        return result.isOperationalSuccess
    }

    private fun applyCustomOrLegacy(
        target: AppliedRefreshState,
        customRate: Int?,
        legacyWrite: () -> Boolean
    ): Boolean {
        return RefreshRateOperationCoordinator.run {
            if (!isTargetCurrent(target)) return@run false

            val startedAt = SystemClock.elapsedRealtime()
            if (customRate == null) {
                return@run legacyWrite().also {
                    logTransitionLatency("legacy", startedAt)
                }
            }

            val result = CustomRefreshRateController.applyFixedRate(appContext, customRate)
            if (result.isSuccess) {
                logTransitionLatency("custom-${customRate}Hz", startedAt)
                return@run true
            }

            Log.w(tag, "Custom ${customRate}Hz rejected (${result::class.simpleName}); using legacy")
            if (!CustomRefreshRateController.restoreOriginal(appContext)) return@run false
            legacyWrite().also {
                logTransitionLatency("custom-fallback-${customRate}Hz", startedAt)
            }
        }
    }

    private fun logTransitionLatency(path: String, startedAt: Long) {
        Log.d(
            tag,
            "Refresh transition path=$path durationMs=${SystemClock.elapsedRealtime() - startedAt}"
        )
    }

    private fun resolveCustomLowRate(packageName: String?): Int? {
        return CustomRateSelectionResolver.low(selectionState(packageName))
    }

    private fun resolveCustomHighRate(packageName: String?): Int? {
        return CustomRateSelectionResolver.high(selectionState(packageName))
    }

    private fun resolveCustomMinimumRate(packageName: String?): Int? {
        return CustomRateSelectionResolver.forceMinimum(selectionState(packageName))
    }

    private fun resolveCustomMaximumRate(packageName: String?): Int? {
        return CustomRateSelectionResolver.forceMaximum(selectionState(packageName))
    }

    private fun selectionState(packageName: String?): CustomRateSelectionState {
        return CustomRateSelectionState(
            globalMode = getGlobalMode(),
            appMode = getAppProfileMode(packageName),
            globalMinimum = AdaptiveHzPrefs.getGlobalCustomMinimumRate(appContext),
            globalMaximum = AdaptiveHzPrefs.getGlobalCustomMaximumRate(appContext),
            globalAdaptiveTarget = AdaptiveHzPrefs.getGlobalAdaptiveTargetRate(appContext),
            appMinimum = AdaptiveHzPrefs.getAppCustomMinimumRate(appContext, packageName),
            appMaximum = AdaptiveHzPrefs.getAppCustomMaximumRate(appContext, packageName)
        )
    }

    private fun appliedState(target: RefreshTarget, customRateHz: Int?): AppliedRefreshState {
        return AppliedRefreshState(
            target = target,
            packageName = activePackageName,
            globalMode = getGlobalMode(),
            appMode = getAppProfileMode(activePackageName),
            customRateHz = customRateHz
        )
    }

    private fun isTargetCurrent(expected: AppliedRefreshState): Boolean {
        if (!started) return false
        val currentCustomRate = when (expected.target) {
            RefreshTarget.SYSTEM_CONTROLLED -> null
            RefreshTarget.ADAPTIVE_LOW -> resolveCustomLowRate(activePackageName)
            RefreshTarget.ADAPTIVE_HIGH -> resolveCustomHighRate(activePackageName)
            RefreshTarget.FORCE_MINIMUM -> resolveCustomMinimumRate(activePackageName)
            RefreshTarget.FORCE_MAXIMUM -> resolveCustomMaximumRate(activePackageName)
        }
        return appliedState(expected.target, currentCustomRate) == expected
    }

    private fun shouldUseBatterySaverOverrideWrites(): Boolean {
        return RefreshRateController.isBatterySaverOn(appContext) &&
                AdaptiveHzPrefs.shouldKeepActiveDuringBatterySaver(appContext)
    }

    /** Schedules the safety check that prevents staying on HIGH indefinitely. */
    private fun scheduleSafety() {
        handler.removeCallbacks(safetyRunnable)
        val safetyDelayMs = effectiveDropDelayMs() + tuning.interactionIdleTimeoutMs
        handler.postDelayed(safetyRunnable, safetyDelayMs)
    }

    companion object {
        private const val ACTIVE_TOUCH_RECHECK_MS = 100L
    }

    private fun effectiveDropDelayMs(): Long {
        return getInteractionDropDelayMs()
    }

    private fun shouldRespectBatterySaverRefreshLimit(): Boolean {
        val batterySaverOn = RefreshRateController.isBatterySaverOn(appContext)

        if (!batterySaverOn) return false

        val keepActiveDuringBatterySaver =
            AdaptiveHzPrefs.shouldKeepActiveDuringBatterySaver(appContext)

        return !keepActiveDuringBatterySaver
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
    private fun logEventDetails(event: EngineAccessibilityEvent) {
        val msg = buildString {
            append("type=").append(eventTypeName(event.eventType))
            append(" pkg=").append(event.packageName)
            append(" cls=").append(event.className)
            append(" changeTypes=").append(event.contentChangeTypes)

            append(" from=").append(event.fromIndex)
            append(" to=").append(event.toIndex)

            append(" scrollX=").append(event.scrollX)
            append(" scrollY=").append(event.scrollY)
            append(" maxScrollX=").append(event.maxScrollX)
            append(" maxScrollY=").append(event.maxScrollY)

            append(" deltaX=").append(event.scrollDeltaX)
            append(" deltaY=").append(event.scrollDeltaY)

            append(" touchActive=").append(isTouchInteracting)
        }

        Log.d(tag, msg)
    }

    /** Copies framework-owned event data before AccessibilityService returns and recycles it. */
    private fun snapshotEvent(event: AccessibilityEvent): EngineAccessibilityEvent {
        return EngineAccessibilityEvent(
            eventType = event.eventType,
            packageName = event.packageName?.toString(),
            className = event.className?.toString(),
            contentChangeTypes = event.contentChangeTypes,
            fromIndex = runCatching { event.fromIndex }.getOrDefault(-1),
            toIndex = runCatching { event.toIndex }.getOrDefault(-1),
            scrollX = runCatching { event.scrollX }.getOrDefault(-1),
            scrollY = runCatching { event.scrollY }.getOrDefault(-1),
            maxScrollX = runCatching { event.maxScrollX }.getOrDefault(-1),
            maxScrollY = runCatching { event.maxScrollY }.getOrDefault(-1),
            scrollDeltaX = runCatching { event.scrollDeltaX }.getOrDefault(0),
            scrollDeltaY = runCatching { event.scrollDeltaY }.getOrDefault(0)
        )
    }

    private data class EngineAccessibilityEvent(
        val eventType: Int,
        val packageName: String?,
        val className: String?,
        val contentChangeTypes: Int,
        val fromIndex: Int,
        val toIndex: Int,
        val scrollX: Int,
        val scrollY: Int,
        val maxScrollX: Int,
        val maxScrollY: Int,
        val scrollDeltaX: Int,
        val scrollDeltaY: Int
    )
}
