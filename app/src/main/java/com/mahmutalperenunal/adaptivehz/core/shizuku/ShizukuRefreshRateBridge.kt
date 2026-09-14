package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.os.Binder
import android.util.Log

/**
 * Process-local access point for the already-bound Shizuku user service.
 *
 * The remote implementation runs as shell. Samsung values use session-scoped display tokens;
 * HyperOS 3 uses an exact-state-restoring secure-setting lease. Generic min/peak access remains
 * only for snapshots left by older app builds. Existing vendor strategies never depend on this.
 */
object ShizukuRefreshRateBridge {
    private const val TAG = "ShizukuRefreshBridge"

    @Volatile
    private var service: IInputMonitorService? = null

    @Volatile
    private var samsungTokenSupport: Boolean? = null

    @Volatile
    private var activeSamsungTokenLimits: Pair<Int, Int>? = null

    private val tokenStateLock = Any()
    private val displaySessionClientToken = Binder()

    internal fun attach(remote: IInputMonitorService) {
        synchronized(tokenStateLock) {
            service = remote
            samsungTokenSupport = null
            activeSamsungTokenLimits = null
        }
        runCatching { remote.registerDisplaySessionClient(displaySessionClientToken) }
            .onFailure { Log.w(TAG, "Unable to register display-session lifecycle", it) }
    }

    internal fun detach(remote: IInputMonitorService?) {
        synchronized(tokenStateLock) {
            if (remote == null || service?.asBinder() == remote.asBinder()) {
                service = null
                samsungTokenSupport = null
                activeSamsungTokenLimits = null
            }
        }
    }

    fun isAvailable(): Boolean = service?.asBinder()?.isBinderAlive == true

    fun read(key: String): String? {
        val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return null
        return runCatching { remote.readRefreshRateSetting(key) }
            .onFailure { Log.w(TAG, "Unable to read system/$key through Shizuku", it) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun write(key: String, value: String?): Boolean {
        val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return false
        return runCatching {
            if (value == null) {
                remote.deleteRefreshRateSetting(key)
            } else {
                remote.writeRefreshRateSetting(key, value)
            }
        }.onFailure {
            Log.w(TAG, "Unable to write system/$key through Shizuku", it)
        }.getOrDefault(false)
    }

    fun readSamsungRefreshRateMode(): Int? {
        val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return null
        return runCatching { remote.readSamsungRefreshRateMode() }
            .onFailure { Log.w(TAG, "Unable to read Samsung refresh mode through Shizuku", it) }
            .getOrNull()
            ?.takeIf { it in 0..2 }
    }

    fun writeSamsungRefreshRateMode(value: Int): Boolean {
        if (value !in 0..2) return false
        val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return false
        return runCatching { remote.writeSamsungRefreshRateMode(value) }
            .onFailure { Log.w(TAG, "Unable to write Samsung refresh mode through Shizuku", it) }
            .getOrDefault(false)
    }

    fun inspectSamsungRefreshRateTokenApi(): String? {
        val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return null
        return runCatching { remote.inspectSamsungRefreshRateTokenApi() }
            .onFailure { Log.w(TAG, "Unable to inspect Samsung refresh-rate token API", it) }
            .getOrNull()
    }

    fun supportsSamsungRefreshRateTokens(): Boolean {
        samsungTokenSupport?.let { return it }
        val supported = SamsungRefreshRateTokenContract.isCandidateReport(
            inspectSamsungRefreshRateTokenApi()
        )
        samsungTokenSupport = supported
        return supported
    }

    fun applySamsungRefreshRateTokenLimits(minRefreshRate: Int, maxRefreshRate: Int): Boolean {
        return synchronized(tokenStateLock) {
            val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return@synchronized false
            val requested = minRefreshRate to maxRefreshRate

            runCatching {
                remote.applySamsungRefreshRateTokenLimits(minRefreshRate, maxRefreshRate)
            }.onFailure {
                Log.w(TAG, "Unable to apply Samsung refresh-rate token limits", it)
            }.getOrDefault(false).also { applied ->
                activeSamsungTokenLimits = requested.takeIf { applied }
            }
        }
    }

    fun releaseSamsungRefreshRateTokenLimits(): Boolean {
        return synchronized(tokenStateLock) {
            val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return@synchronized true
            runCatching { remote.releaseSamsungRefreshRateTokenLimits() }
                .onFailure { Log.w(TAG, "Unable to release Samsung refresh-rate tokens", it) }
                .getOrDefault(false)
                .also { released ->
                    if (released) activeSamsungTokenLimits = null
                }
        }
    }

    fun applyHyperOsRefreshRateSetting(key: String, refreshRate: Int): Boolean {
        if (key !in HyperOsRefreshRateSettingKeys.allowed || refreshRate <= 0) return false
        return synchronized(tokenStateLock) {
            val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return@synchronized false
            runCatching { remote.applyHyperOsRefreshRateSetting(key, refreshRate) }
                .onFailure { Log.w(TAG, "Unable to apply HyperOS secure/$key", it) }
                .getOrDefault(false)
        }
    }

    fun releaseHyperOsRefreshRateSetting(): Boolean {
        return synchronized(tokenStateLock) {
            val remote = service?.takeIf { it.asBinder().isBinderAlive } ?: return@synchronized true
            runCatching { remote.releaseHyperOsRefreshRateSetting() }
                .onFailure { Log.w(TAG, "Unable to restore HyperOS refresh-rate setting", it) }
                .getOrDefault(false)
        }
    }

}

private object HyperOsRefreshRateSettingKeys {
    val allowed = setOf("user_refresh_rate", "miui_refresh_rate")
}
