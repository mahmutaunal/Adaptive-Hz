package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Shizuku-backed privileged service responsible for monitoring low-level touchscreen input events.
 */
class InputMonitorUserService : IInputMonitorService.Stub() {

    private val monitoring = AtomicBoolean(false)
    private val refreshRateTokenController = SamsungRefreshRateTokenController()
    private val refreshRateSessionLock = Any()
    private val samsungModeLease = SamsungRefreshRateModeLease(
        readMode = ::readSamsungRefreshRateModeOrNull,
        writeMode = ::writeSamsungRefreshRateModeDirect
    )
    private val hyperOsSettingLease = HyperOsRefreshRateSettingLease(
        managedKeys = listOf(KEY_XIAOMI_USER_REFRESH_RATE, KEY_XIAOMI_REFRESH_RATE),
        read = ::readHyperOsRefreshRateSetting,
        write = ::writeHyperOsRefreshRateSetting
    )

    @Volatile
    private var monitorProcess: Process? = null

    @Volatile
    private var monitorThread: Thread? = null

    @Volatile
    private var lastMoveCallbackAt = 0L

    @Volatile
    private var clientBinder: IBinder? = null

    private val clientDeathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "Client process died; releasing custom display session")
        stopMonitoring()
        releasePrivilegedDisplaySessions()
    }

    override fun listInputDevices(): String {
        return executeDirectCommand("/system/bin/getevent", "-lp")
            .takeIf { it.success }
            ?.output
            .orEmpty()
    }

    override fun registerDisplaySessionClient(client: IBinder?) {
        if (client != null) registerClientDeath(client)
    }

    override fun readRefreshRateSetting(key: String?): String {
        val safeKey = key?.takeIf(::isAllowedRefreshRateKey) ?: return ""
        val result = executeDirectCommand("/system/bin/settings", "get", "system", safeKey)
        if (!result.success) return ""

        return result.output.trim().takeUnless { it == "null" }.orEmpty()
    }

    override fun writeRefreshRateSetting(key: String?, value: String?): Boolean {
        val safeKey = key?.takeIf(::isAllowedRefreshRateKey) ?: return false
        val safeValue = value
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.toString()
            ?: return false

        val result = executeDirectCommand(
            "/system/bin/settings",
            "put",
            "system",
            safeKey,
            safeValue
        )
        if (!result.success) return false

        return readRefreshRateSetting(safeKey).toFloatOrNull() == safeValue.toFloatOrNull()
    }

    override fun deleteRefreshRateSetting(key: String?): Boolean {
        val safeKey = key?.takeIf(::isAllowedRefreshRateKey) ?: return false
        val result = executeDirectCommand("/system/bin/settings", "delete", "system", safeKey)
        return result.success && readRefreshRateSetting(safeKey).isEmpty()
    }

    override fun readSamsungRefreshRateMode(): Int {
        return readSamsungRefreshRateModeOrNull() ?: SAMSUNG_MODE_UNAVAILABLE
    }

    private fun readSamsungRefreshRateModeOrNull(): Int? {
        val result = executeDirectCommand(
            "/system/bin/settings",
            "get",
            "secure",
            KEY_SAMSUNG_REFRESH_RATE_MODE
        )
        if (!result.success) return null
        return result.output.trim().toIntOrNull()?.takeIf { it in SAMSUNG_REFRESH_RATE_MODES }
    }

    override fun writeSamsungRefreshRateMode(value: Int): Boolean {
        return synchronized(refreshRateSessionLock) {
            writeSamsungRefreshRateModeDirect(value)
        }
    }

    private fun writeSamsungRefreshRateModeDirect(value: Int): Boolean {
        if (value !in SAMSUNG_REFRESH_RATE_MODES) return false
        val result = executeDirectCommand(
            "/system/bin/settings",
            "put",
            "secure",
            KEY_SAMSUNG_REFRESH_RATE_MODE,
            value.toString()
        )
        return result.success && readSamsungRefreshRateModeOrNull() == value
    }

    override fun inspectSamsungRefreshRateTokenApi(): String {
        return refreshRateTokenController.inspectApi()
    }

    override fun applySamsungRefreshRateTokenLimits(
        minRefreshRate: Int,
        maxRefreshRate: Int
    ): Boolean {
        return synchronized(refreshRateSessionLock) {
            if (!samsungModeLease.prepareHighRange()) {
                Log.w(TAG, "Samsung high refresh-rate range could not be prepared")
                return@synchronized false
            }

            if (refreshRateTokenController.applyLimits(minRefreshRate, maxRefreshRate)) {
                return@synchronized true
            }

            val tokensReleased = refreshRateTokenController.releaseLimits()
            val modeRestored = samsungModeLease.restoreOriginal()
            if (!tokensReleased || !modeRestored) {
                Log.e(
                    TAG,
                    "Custom session rollback failed: tokens=$tokensReleased mode=$modeRestored"
                )
            }
            false
        }
    }

    override fun releaseSamsungRefreshRateTokenLimits(): Boolean {
        val released = synchronized(refreshRateSessionLock) {
            val tokensReleased = refreshRateTokenController.releaseLimits()
            val modeRestored = samsungModeLease.restoreOriginal()
            tokensReleased && modeRestored
        }
        if (released) return true

        // A failed explicit release must never leave a long-lived display constraint. Killing
        // this non-daemon UserService triggers Binder death cleanup in DisplayManagerService.
        Log.e(TAG, "Token cleanup failed; terminating UserService for Binder death cleanup")
        exitProcess(0)
    }

    override fun applyHyperOsRefreshRateSetting(key: String?, refreshRate: Int): Boolean {
        val safeKey = key?.takeIf(::isAllowedHyperOsRefreshRateKey) ?: return false
        if (refreshRate !in MIN_PHYSICAL_REFRESH_RATE..MAX_PHYSICAL_REFRESH_RATE) return false
        return synchronized(refreshRateSessionLock) {
            hyperOsSettingLease.apply(safeKey, refreshRate)
        }
    }

    override fun releaseHyperOsRefreshRateSetting(): Boolean {
        return synchronized(refreshRateSessionLock) {
            hyperOsSettingLease.restoreOriginal()
        }.also { restored ->
            if (!restored) {
                // Unlike Samsung Binder tokens, terminating cannot restore a persistent setting.
                // Keep the process and snapshot alive so a later cleanup attempt can retry.
                Log.e(TAG, "HyperOS secure-setting lease could not be restored")
            }
        }
    }

    private fun readHyperOsRefreshRateSetting(
        key: String
    ): HyperOsRefreshRateSettingLease.SettingState? {
        if (!isAllowedHyperOsRefreshRateKey(key)) return null
        val result = executeDirectCommand("/system/bin/settings", "get", "secure", key)
        if (!result.success) return null
        val raw = result.output.trim().takeUnless { it == "null" }
        if (raw != null && raw.toIntOrNull()?.takeIf { it > 0 } == null) return null
        return HyperOsRefreshRateSettingLease.SettingState(raw)
    }

    private fun writeHyperOsRefreshRateSetting(key: String, value: String?): Boolean {
        if (!isAllowedHyperOsRefreshRateKey(key)) return false
        val result = if (value == null) {
            executeDirectCommand("/system/bin/settings", "delete", "secure", key)
        } else {
            val safeValue = value.toIntOrNull()
                ?.takeIf { it in MIN_PHYSICAL_REFRESH_RATE..MAX_PHYSICAL_REFRESH_RATE }
                ?: return false
            executeDirectCommand(
                "/system/bin/settings",
                "put",
                "secure",
                key,
                safeValue.toString()
            )
        }
        return result.success
    }

    private fun releasePrivilegedDisplaySessions(): Boolean {
        // Restore persistent state first. Samsung tokens are process-scoped and still disappear
        // if their explicit release has to terminate this UserService.
        val hyperOsRestored = releaseHyperOsRefreshRateSetting()
        val samsungRestored = releaseSamsungRefreshRateTokenLimits()
        return hyperOsRestored && samsungRestored
    }

    /**
     * Starts streaming raw input events from the selected touchscreen device node.
     */
    override fun startMonitoring(
        devicePath: String?,
        callback: IInputEventCallback?
    ) {
        if (devicePath.isNullOrBlank() || callback == null) return

        registerClientDeath(callback.asBinder())

        if (!monitoring.compareAndSet(false, true)) {
            Log.d(TAG, "startMonitoring skipped: already monitoring")
            return
        }

        monitorThread = Thread {
            var process: Process? = null

            try {
                Log.d(TAG, "Starting input monitor: $devicePath")

                process = ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "exec /system/bin/getevent -lt $devicePath"
                )
                    .redirectErrorStream(true)
                    .start()

                monitorProcess = process

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (monitoring.get()) {
                        val line = reader.readLine() ?: break
                        handleInputLine(line, callback)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Input monitor failed", t)
            } finally {
                monitoring.set(false)
                monitorProcess = null

                process?.destroySafely()

                Log.d(TAG, "Input monitor stopped")
            }
        }.apply {
            name = "AdaptiveHzInputMonitor"
            isDaemon = false
            start()
        }
    }

    /**
     * Stops the active getevent process and releases monitoring resources.
     */
    override fun stopMonitoring() {
        Log.d(TAG, "stopMonitoring")

        monitoring.set(false)
        unregisterClientDeath()

        val process = monitorProcess
        monitorProcess = null

        process?.destroySafely()

        val thread = monitorThread
        monitorThread = null

        if (thread != null && thread != Thread.currentThread()) {
            runCatching {
                thread.join(MONITOR_THREAD_JOIN_TIMEOUT_MS)
            }.onFailure {
                Log.e(TAG, "Failed to join monitor thread", it)
            }
        }
    }

    private fun registerClientDeath(binder: IBinder) {
        if (clientBinder === binder) return
        unregisterClientDeath()
        runCatching {
            binder.linkToDeath(clientDeathRecipient, 0)
            clientBinder = binder
        }.onFailure {
            Log.w(TAG, "Unable to observe client process lifetime", it)
        }
    }

    private fun unregisterClientDeath() {
        val binder = clientBinder ?: return
        clientBinder = null
        runCatching { binder.unlinkToDeath(clientDeathRecipient, 0) }
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        stopMonitoring()
        if (releasePrivilegedDisplaySessions()) {
            exitProcess(0)
        }
    }

    private fun handleInputLine(
        line: String,
        callback: IInputEventCallback
    ) {
        when {
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                safeCallback { callback.onTouchDown() }
            }

            line.contains("BTN_TOUCH") && line.contains("UP") -> {
                safeCallback { callback.onTouchUp() }
            }

            line.contains("ABS_MT_TRACKING_ID") &&
                    line.contains("ffffffff", ignoreCase = true) -> {
                safeCallback { callback.onTouchUp() }
            }

            line.contains("ABS_MT_POSITION_X") ||
                    line.contains("ABS_MT_POSITION_Y") -> {
                val now = System.currentTimeMillis()

                if (now - lastMoveCallbackAt >= MOVE_CALLBACK_THROTTLE_MS) {
                    lastMoveCallbackAt = now
                    safeCallback { callback.onTouchMove() }
                }
            }
        }
    }

    private inline fun safeCallback(block: () -> Unit) {
        try {
            block()
        } catch (_: RemoteException) {
            stopMonitoring()
            releasePrivilegedDisplaySessions()
        } catch (t: Throwable) {
            Log.e(TAG, "Callback failed", t)
        }
    }

    private fun Process.destroySafely() {
        runCatching {
            outputStream?.close()
        }

        runCatching {
            inputStream?.close()
        }

        runCatching {
            errorStream?.close()
        }

        runCatching {
            destroy()
        }

        runCatching {
            if (!waitFor(PROCESS_DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                destroyForcibly()
            }
        }
    }

    private data class DirectCommandResult(
        val success: Boolean,
        val output: String
    )

    private fun executeDirectCommand(vararg command: String): DirectCommandResult {
        var process: Process? = null
        return try {
            process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { reader ->
                reader.readText().take(MAX_COMMAND_OUTPUT_CHARS)
            }
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroySafely()
                DirectCommandResult(false, output)
            } else {
                DirectCommandResult(process.exitValue() == 0, output)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Direct settings command failed", t)
            DirectCommandResult(false, "")
        } finally {
            process?.destroySafely()
        }
    }

    private fun isAllowedRefreshRateKey(key: String): Boolean {
        return key == KEY_MIN_REFRESH_RATE || key == KEY_PEAK_REFRESH_RATE
    }

    private fun isAllowedHyperOsRefreshRateKey(key: String): Boolean {
        return key == KEY_XIAOMI_USER_REFRESH_RATE || key == KEY_XIAOMI_REFRESH_RATE
    }

    companion object {
        private const val TAG = "InputMonitorService"

        private const val MOVE_CALLBACK_THROTTLE_MS = 75L

        private const val COMMAND_TIMEOUT_MS = 3_000L
        private const val MONITOR_THREAD_JOIN_TIMEOUT_MS = 1_000L
        private const val PROCESS_DESTROY_TIMEOUT_MS = 500L

        private const val MAX_COMMAND_OUTPUT_CHARS = 8_000

        private const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"
        private const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"
        private const val KEY_SAMSUNG_REFRESH_RATE_MODE = "refresh_rate_mode"
        private const val KEY_XIAOMI_USER_REFRESH_RATE = "user_refresh_rate"
        private const val KEY_XIAOMI_REFRESH_RATE = "miui_refresh_rate"
        private const val MIN_PHYSICAL_REFRESH_RATE = 1
        private const val MAX_PHYSICAL_REFRESH_RATE = 1_000
        private const val SAMSUNG_MODE_UNAVAILABLE = -1
        private val SAMSUNG_REFRESH_RATE_MODES = 0..2
    }
}
