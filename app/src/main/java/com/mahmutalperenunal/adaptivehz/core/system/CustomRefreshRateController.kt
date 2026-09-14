package com.mahmutalperenunal.adaptivehz.core.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.shizuku.ShizukuRefreshRateBridge
import kotlin.math.abs
import kotlin.math.roundToInt

data class RefreshRateCapabilities(
    val supportedRates: List<Int>,
    val customRateSelectionSupported: Boolean
)

sealed interface CustomRefreshRateResult {
    data object Applied : CustomRefreshRateResult
    data object NoOperation : CustomRefreshRateResult
    data object Unsupported : CustomRefreshRateResult
    data object InvalidRate : CustomRefreshRateResult
    data class Failed(val rollbackSucceeded: Boolean) : CustomRefreshRateResult
}

val CustomRefreshRateResult.isSuccess: Boolean
    get() = this is CustomRefreshRateResult.Applied ||
        this is CustomRefreshRateResult.NoOperation

/** Optional fixed-rate layer; vendor strategies remain the fallback path. */
object CustomRefreshRateController {
    private const val TAG = "CustomRefreshRate"
    private const val FLOAT_TOLERANCE = 0.01f

    @Synchronized
    fun resolveCapabilities(context: Context): RefreshRateCapabilities {
        val appContext = context.applicationContext
        val observation = runCatching {
            val display = resolveDefaultDisplay(context)
                ?: throw IllegalStateException("Default display is unavailable")
            CapabilityObservation(
                identity = buildCapabilityIdentity(display),
                liveRates = normalizeSupportedRates(
                    display.supportedModes
                        .filter {
                            it.physicalWidth == display.mode.physicalWidth &&
                                it.physicalHeight == display.mode.physicalHeight
                        }
                        .map { it.refreshRate }
                )
            )
        }.onFailure {
            Log.w(TAG, "Unable to discover physical display modes", it)
        }.getOrNull()

        if (observation == null) {
            return RefreshRateCapabilities(
                supportedRates = emptyList(),
                customRateSelectionSupported = false
            )
        }

        val cachedRates = AdaptiveHzPrefs.getCachedRefreshRateCapabilities(
            appContext,
            observation.identity
        )?.supportedRates.orEmpty()
        val rates = selectStableSupportedRates(observation.liveRates, cachedRates)

        if (rates.size > 1 && rates != cachedRates) {
            AdaptiveHzPrefs.saveCachedRefreshRateCapabilities(
                appContext,
                observation.identity,
                rates
            )
        }

        Log.d(
            TAG,
            "Capabilities live=${observation.liveRates}, cached=$cachedRates, selected=$rates"
        )

        val vendor = DeviceVendorDetector.detect()
        val hyperOsMajor = if (vendor == DeviceVendor.XIAOMI) {
            RefreshRateController.detectHyperOsVersion().majorVersion
        } else {
            null
        }
        return RefreshRateCapabilities(
            supportedRates = rates,
            customRateSelectionSupported = isCustomSelectionSupported(
                vendor = vendor,
                hyperOsMajor = hyperOsMajor,
                supportedRateCount = rates.size
            )
        )
    }

    private data class CapabilityObservation(
        val identity: String,
        val liveRates: List<Int>
    )

    private fun buildCapabilityIdentity(display: Display): String {
        val mode = display.mode
        return listOf(
            Build.FINGERPRINT,
            display.displayId.toString(),
            mode.physicalWidth.toString(),
            mode.physicalHeight.toString()
        ).joinToString("|")
    }

    /**
     * Resolves the primary display without requiring a visual Context.
     *
     * Activity contexts are display-associated, while Application, Service and receiver
     * contexts are not. DisplayManager provides the same default display safely for every
     * entry point used by the refresh-rate engine.
     */
    private fun resolveDefaultDisplay(context: Context): Display? {
        val displayManager = context.applicationContext
            .getSystemService(DisplayManager::class.java)

        return displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    internal fun normalizeSupportedRates(reportedRates: List<Float>): List<Int> {
        return reportedRates
            .map { it.roundToInt() }
            .filter { it > 0 }
            .distinct()
            .sorted()
    }

    /**
     * Samsung may temporarily expose only the modes allowed by the active refresh policy.
     * A same-display cached superset remains the physical capability list; a live list that
     * introduces a new rate is treated as a genuine capability change and replaces it.
     */
    internal fun selectStableSupportedRates(
        liveRates: List<Int>,
        cachedRates: List<Int>
    ): List<Int> {
        val live = liveRates.filter { it > 0 }.distinct().sorted()
        val cached = cachedRates.filter { it > 0 }.distinct().sorted()
        if (cached.size <= 1) return live

        val liveIsTemporarySubset = live.all { it in cached } && live.size < cached.size
        return if (liveIsTemporarySubset) cached else live
    }

    internal fun isCustomSelectionSupported(
        vendor: DeviceVendor,
        hyperOsMajor: Int?,
        supportedRateCount: Int
    ): Boolean {
        if (supportedRateCount <= 1) return false
        return vendor == DeviceVendor.SAMSUNG ||
            (vendor == DeviceVendor.XIAOMI && hyperOsMajor == 3)
    }

    @Synchronized
    fun applyFixedRate(context: Context, refreshRateHz: Int): CustomRefreshRateResult {
        val appContext = context.applicationContext
        val capabilities = resolveCapabilities(appContext)
        if (!capabilities.customRateSelectionSupported) {
            return CustomRefreshRateResult.Unsupported
        }
        if (refreshRateHz !in capabilities.supportedRates) {
            return CustomRefreshRateResult.InvalidRate
        }

        // Recover settings left by older Adaptive Hz versions before acquiring safe tokens.
        if (!recoverLegacyPersistentOverrides(appContext)) {
            return CustomRefreshRateResult.Failed(rollbackSucceeded = false)
        }

        return when (DeviceVendorDetector.detect()) {
            DeviceVendor.SAMSUNG -> applySamsungFixedRate(refreshRateHz)
            DeviceVendor.XIAOMI -> applyHyperOsFixedRate(
                context = appContext,
                refreshRateHz = refreshRateHz,
                supportedRates = capabilities.supportedRates
            )
            DeviceVendor.OTHER -> CustomRefreshRateResult.Unsupported
        }
    }

    private fun applySamsungFixedRate(refreshRateHz: Int): CustomRefreshRateResult {
        if (!ShizukuRefreshRateBridge.supportsSamsungRefreshRateTokens()) {
            return CustomRefreshRateResult.Unsupported
        }
        return if (ShizukuRefreshRateBridge.applySamsungRefreshRateTokenLimits(
                minRefreshRate = refreshRateHz,
                maxRefreshRate = refreshRateHz
            )) {
            CustomRefreshRateResult.Applied
        } else {
            val rollbackSucceeded = ShizukuRefreshRateBridge.releaseSamsungRefreshRateTokenLimits()
            CustomRefreshRateResult.Failed(rollbackSucceeded)
        }
    }

    private fun applyHyperOsFixedRate(
        context: Context,
        refreshRateHz: Int,
        supportedRates: List<Int>
    ): CustomRefreshRateResult {
        if (RefreshRateController.detectHyperOsVersion().majorVersion != 3 ||
            !ShizukuRefreshRateBridge.isAvailable()) {
            return CustomRefreshRateResult.Unsupported
        }

        val display = resolveDefaultDisplay(context) ?: return CustomRefreshRateResult.Unsupported
        val identity = buildCapabilityIdentity(display)
        val activeHz = display.mode.refreshRate.roundToInt()
        val snapshot = AdaptiveHzPrefs.getHyperOsCustomSettingSnapshot(context)
        val cachedKey = AdaptiveHzPrefs.getVerifiedHyperOsCustomRoute(context, identity)
            ?.takeIf { it in HyperOsCustomRefreshRateContract.allowedKeys }

        val allCandidates = listOf(
            HyperOsCustomRefreshRateContract.Candidate(
                HyperOsCustomRefreshRateContract.USER_REFRESH_RATE,
                readSecureInt(context, HyperOsCustomRefreshRateContract.USER_REFRESH_RATE)
            ),
            HyperOsCustomRefreshRateContract.Candidate(
                HyperOsCustomRefreshRateContract.MIUI_REFRESH_RATE,
                readSecureInt(context, HyperOsCustomRefreshRateContract.MIUI_REFRESH_RATE)
            )
        )
        val candidates = HyperOsCustomRefreshRateContract.orderCandidates(
            candidates = allCandidates,
            activeRefreshRate = activeHz,
            activeLeaseKey = snapshot?.key ?: cachedKey
        )

        for (candidate in candidates) {
            if (snapshot != null && snapshot.key != candidate.key) {
                if (!releaseAndRecoverHyperOsOverride(context)) continue
            }

            val original = Settings.Secure.getString(context.contentResolver, candidate.key)
            val companionKey = HyperOsCustomRefreshRateContract.allowedKeys
                .first { it != candidate.key }
            val companionOriginal = Settings.Secure.getString(
                context.contentResolver,
                companionKey
            )
            if (!AdaptiveHzPrefs.saveHyperOsCustomSettingSnapshot(
                    context,
                    candidate.key,
                    original,
                    companionKey,
                    companionOriginal
                )) {
                continue
            }

            val routeAlreadyVerified = candidate.key == cachedKey
            val probeRate = if (!routeAlreadyVerified && isActiveRate(display, refreshRateHz)) {
                supportedRates
                    .filter { it != refreshRateHz }
                    .minByOrNull { abs(it - refreshRateHz) }
            } else {
                null
            }

            val probeSucceeded = probeRate == null || (
                ShizukuRefreshRateBridge.applyHyperOsRefreshRateSetting(candidate.key, probeRate) &&
                    waitForActiveRate(context, probeRate, DISCOVERY_VERIFY_TIMEOUT_MS)
                )
            val targetSucceeded = probeSucceeded &&
                ShizukuRefreshRateBridge.applyHyperOsRefreshRateSetting(
                    candidate.key,
                    refreshRateHz
                ) &&
                waitForActiveRate(context, refreshRateHz, APPLY_VERIFY_TIMEOUT_MS)

            if (targetSucceeded) {
                AdaptiveHzPrefs.saveVerifiedHyperOsCustomRoute(context, identity, candidate.key)
                Log.i(TAG, "Verified HyperOS 3 custom route secure/${candidate.key}")
                return CustomRefreshRateResult.Applied
            }

            Log.w(TAG, "HyperOS candidate did not change physical mode: secure/${candidate.key}")
            if (!releaseAndRecoverHyperOsOverride(context)) {
                return CustomRefreshRateResult.Failed(rollbackSucceeded = false)
            }
            if (candidate.key == cachedKey) {
                AdaptiveHzPrefs.clearVerifiedHyperOsCustomRoute(context)
            }
        }

        return CustomRefreshRateResult.Unsupported
    }

    @Synchronized
    fun restoreOriginal(context: Context): Boolean {
        val appContext = context.applicationContext
        val hyperOsRestored = if (
            DeviceVendorDetector.detect() == DeviceVendor.XIAOMI ||
            AdaptiveHzPrefs.getHyperOsCustomSettingSnapshot(appContext) != null
        ) {
            releaseAndRecoverHyperOsOverride(appContext)
        } else {
            true
        }
        if (!hyperOsRestored) {
            Log.e(TAG, "Unable to restore HyperOS custom refresh-rate setting")
            return false
        }
        val tokensReleased = ShizukuRefreshRateBridge.releaseSamsungRefreshRateTokenLimits()
        if (!tokensReleased) {
            Log.e(TAG, "Unable to release session-scoped refresh-rate tokens")
            return false
        }

        return recoverLegacyPersistentOverrides(appContext)
    }

    /** Restores persistent safety snapshots during boot and package replacement. */
    @Synchronized
    fun recoverPersistentOverridesAfterRestart(context: Context): Boolean {
        val appContext = context.applicationContext
        return recoverHyperOsPersistentOverride(appContext) &&
            recoverLegacyPersistentOverrides(appContext)
    }

    private fun releaseAndRecoverHyperOsOverride(context: Context): Boolean {
        val firstRemoteRestore = ShizukuRefreshRateBridge.releaseHyperOsRefreshRateSetting()
        val persistentRestored = recoverHyperOsPersistentOverride(context)
        val remoteRestored = firstRemoteRestore ||
            ShizukuRefreshRateBridge.releaseHyperOsRefreshRateSetting()
        return remoteRestored && persistentRestored
    }

    private fun recoverHyperOsPersistentOverride(context: Context): Boolean {
        val snapshot = AdaptiveHzPrefs.getHyperOsCustomSettingSnapshot(context) ?: return true
        if (snapshot.key !in HyperOsCustomRefreshRateContract.allowedKeys ||
            snapshot.companionKey !in HyperOsCustomRefreshRateContract.allowedKeys ||
            snapshot.key == snapshot.companionKey) return false

        val restored = runCatching {
            val resolver = context.contentResolver
            repeat(HYPEROS_RESTORE_ATTEMPTS) {
                val companionWritten = Settings.Secure.putString(
                    resolver,
                    snapshot.companionKey,
                    snapshot.companionOriginalValue
                )
                val selectedWritten = Settings.Secure.putString(
                    resolver,
                    snapshot.key,
                    snapshot.originalValue
                )
                val valuesRestored = companionWritten && selectedWritten &&
                    equivalent(
                        Settings.Secure.getString(resolver, snapshot.companionKey),
                        snapshot.companionOriginalValue
                    ) && equivalent(
                        Settings.Secure.getString(resolver, snapshot.key),
                        snapshot.originalValue
                    )
                if (valuesRestored) return@runCatching true
            }
            false
        }.onFailure {
            Log.e(TAG, "Direct HyperOS recovery failed for secure/${snapshot.key}", it)
        }.getOrDefault(false)

        if (restored) AdaptiveHzPrefs.clearHyperOsCustomSettingSnapshot(context)
        return restored
    }

    private fun readSecureInt(context: Context, key: String): Int? {
        return Settings.Secure.getString(context.contentResolver, key)?.toIntOrNull()
    }

    private fun isActiveRate(display: Display, targetHz: Int): Boolean {
        return abs(display.mode.refreshRate - targetHz) <= DISPLAY_RATE_TOLERANCE
    }

    private fun waitForActiveRate(context: Context, targetHz: Int, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            val display = resolveDefaultDisplay(context)
            if (display != null && isActiveRate(display, targetHz)) return true
            SystemClock.sleep(DISPLAY_VERIFY_INTERVAL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    /** Restores only persistent snapshots produced by pre-token Adaptive Hz versions. */
    @Synchronized
    fun recoverLegacyPersistentOverrides(context: Context): Boolean {
        val snapshot = AdaptiveHzPrefs.getOriginalRefreshRateSettings(context) ?: return true
        val restored = restoreState(
            context,
            RefreshRateState(snapshot.minValue, snapshot.peakValue)
        )
        if (restored) AdaptiveHzPrefs.clearOriginalRefreshRateSettings(context)
        return restored
    }

    private data class RefreshRateState(val minValue: String?, val peakValue: String?)

    private fun readCurrentState(context: Context): RefreshRateState {
        val resolver = context.contentResolver
        return RefreshRateState(
            minValue = Settings.System.getString(
                resolver,
                RefreshRateController.KEY_MIN_REFRESH_RATE
            ),
            peakValue = Settings.System.getString(
                resolver,
                RefreshRateController.KEY_PEAK_REFRESH_RATE
            )
        )
    }

    private fun restoreState(context: Context, state: RefreshRateState): Boolean {
        val current = readCurrentState(context)
        if (equivalent(current.minValue, state.minValue) &&
            equivalent(current.peakValue, state.peakValue)) {
            return true
        }
        val peakWritten = write(
            context,
            RefreshRateController.KEY_PEAK_REFRESH_RATE,
            state.peakValue
        )
        val minWritten = write(
            context,
            RefreshRateController.KEY_MIN_REFRESH_RATE,
            state.minValue
        )
        if (!peakWritten || !minWritten) return false
        val actual = readCurrentState(context)
        return equivalent(actual.minValue, state.minValue) &&
            equivalent(actual.peakValue, state.peakValue)
    }

    private fun write(context: Context, key: String, value: String?): Boolean {
        if (ShizukuRefreshRateBridge.write(key, value)) return true

        val directWrite = runCatching {
            Settings.System.putString(context.contentResolver, key, value)
        }.onFailure {
            Log.w(TAG, "Direct settings write failed: system/$key; trying root transport", it)
        }.getOrDefault(false)

        if (directWrite) return true

        return RootManager.writeRefreshRateSetting(key, value).also { rootSucceeded ->
            if (!rootSucceeded) {
                Log.e(TAG, "All settings transports rejected system/$key")
            }
        }
    }

    private fun equivalent(first: String?, second: String?): Boolean {
        if (first == second) return true
        val firstFloat = first?.toFloatOrNull() ?: return false
        val secondFloat = second?.toFloatOrNull() ?: return false
        return abs(firstFloat - secondFloat) <= FLOAT_TOLERANCE
    }

    private const val DISPLAY_RATE_TOLERANCE = 0.75f
    private const val DISPLAY_VERIFY_INTERVAL_MS = 40L
    private const val APPLY_VERIFY_TIMEOUT_MS = 800L
    private const val DISCOVERY_VERIFY_TIMEOUT_MS = 1_200L
    private const val HYPEROS_RESTORE_ATTEMPTS = 3
}
